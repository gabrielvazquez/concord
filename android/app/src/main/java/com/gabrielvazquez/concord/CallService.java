package com.gabrielvazquez.concord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Mantém a chamada viva com a tela bloqueada ou com outro app aberto (serviço em primeiro plano
 * do tipo microfone), cuida do áudio (alto-falante/fone/Bluetooth) e mostra a notificação fixa
 * com Mutar e Sair.
 */
public class CallService extends Service {
    static final String CH = "em_chamada";
    static final int NOTIF_ID = 77;
    static boolean running = false, speakerOn = true;
    static String title = "Em chamada";
    static boolean muted = false;
    static Boolean speakerChoice = null;   // null = automático (alto-falante sem fone)

    PowerManager.WakeLock wake;
    WifiManager.WifiLock wifi;
    AudioDeviceCallback devCb;

    static void createChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CH, "Em chamada", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Mostra que você está numa sala de voz");
        ch.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static void start(Context c, String t, boolean m) {
        title = t == null || t.isEmpty() ? "Em chamada" : t; muted = m;
        if (running) { c.getSystemService(NotificationManager.class).notify(NOTIF_ID, build(c)); return; }
        try { c.startForegroundService(new Intent(c, CallService.class)); } catch (Exception ignored) {}
    }

    static void stop(Context c) { if (running) c.stopService(new Intent(c, CallService.class)); }

    static Notification build(Context c) {
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pMute = PendingIntent.getBroadcast(c, 2, new Intent(c, Action.class).setAction("mute"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pLeave = PendingIntent.getBroadcast(c, 3, new Intent(c, Action.class).setAction("leave"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(Color.WHITE)
                .setContentTitle(title)
                .setContentText(muted ? "Microfone mutado" : "Voz conectada")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(pOpen)
                .addAction(new Notification.Action.Builder(null, muted ? "Desmutar" : "Mutar", pMute).build())
                .addAction(new Notification.Action.Builder(null, "Sair", pLeave).build())
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = build(this);
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIF_ID, n);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        if (!running) {
            running = true;
            PowerManager pm = getSystemService(PowerManager.class);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "concord:call");
            wake.setReferenceCounted(false); wake.acquire(6 * 60 * 60 * 1000L);
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifi = wm.createWifiLock(Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF, "concord:call");
                wifi.setReferenceCounted(false); wifi.acquire();
            }
            startAudio();
        }
        return START_NOT_STICKY;
    }

    void startAudio() {
        AudioManager am = getSystemService(AudioManager.class);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        route(this);
        devCb = new AudioDeviceCallback() {
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d) { route(CallService.this); }
            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) { route(CallService.this); }
        };
        am.registerAudioDeviceCallback(devCb, null);
    }

    static boolean headset(AudioManager am) {
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_USB_HEADSET
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET)) return true;
        }
        return false;
    }

    /** Alto-falante sem fone (como no Discord); com fone ou Bluetooth, vai para ele. */
    static void route(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        boolean hs = headset(am);
        boolean speaker = speakerChoice != null ? speakerChoice : !hs;
        speakerOn = speaker;
        if (Build.VERSION.SDK_INT >= 31) {
            AudioDeviceInfo pick = null;
            for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                int t = d.getType();
                if (speaker && t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { pick = d; break; }
                if (!speaker && hs && (t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_BLE_HEADSET)) { pick = d; break; }
                if (!speaker && !hs && t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) pick = d;
            }
            if (pick != null) am.setCommunicationDevice(pick); else am.clearCommunicationDevice();
        } else {
            am.setSpeakerphoneOn(speaker);
        }
    }

    static void setSpeaker(Context c, boolean on) { speakerChoice = on; if (running) route(c); }

    @Override
    public void onDestroy() {
        running = false; speakerChoice = null;
        AudioManager am = getSystemService(AudioManager.class);
        if (devCb != null) am.unregisterAudioDeviceCallback(devCb);
        if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice(); else am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_NORMAL);
        if (wake != null && wake.isHeld()) wake.release();
        if (wifi != null && wifi.isHeld()) wifi.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    /** Botões da notificação fixa */
    public static class Action extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            MainActivity a = MainActivity.instance;
            if (a != null) a.js("window.__concordNative && window.__concordNative(" + org.json.JSONObject.quote(i.getAction()) + ")");
        }
    }
}
