package com.gabrielvazquez.concord

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mantém a chamada viva com a tela bloqueada ou com outro app aberto, cuida da saída de áudio
 * (alto-falante, fone, Bluetooth) e mostra a notificação fixa com Mutar e Sair.
 */
class CallService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var devCb: AudioDeviceCallback? = null

    companion object {
        var running = false; private set
        private var title = "Em chamada"
        private var muted = false
        private var projection = false
        var speakerChoice: Boolean? = null
        var speakerOn by mutableStateOf(true)

        fun start(c: Context, t: String, m: Boolean) {
            title = t; muted = m
            if (running) { update(c, t, m); return }
            try { c.startForegroundService(Intent(c, CallService::class.java)) } catch (_: Exception) {}
        }
        fun update(c: Context, t: String, m: Boolean) {
            title = t; muted = m
            if (running) c.getSystemService(NotificationManager::class.java).notify(Notifier.ID_ONGOING, build(c))
        }
        fun stop(c: Context) { projection = false; speakerChoice = null; if (running) c.stopService(Intent(c, CallService::class.java)) }
        fun enableProjection(c: Context) { projection = true; c.startForegroundService(Intent(c, CallService::class.java)) }
        fun disableProjection(c: Context) { if (!projection) return; projection = false; if (running) c.startForegroundService(Intent(c, CallService::class.java)) }

        fun setSpeaker(c: Context, on: Boolean) { speakerChoice = on; route(c) }

        private fun build(c: Context): Notification {
            val pMute = PendingIntent.getBroadcast(c, 11, Intent(c, Action::class.java).setAction("mute"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val pLeave = PendingIntent.getBroadcast(c, 12, Intent(c, Action::class.java).setAction("leave"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(c, Notifier.CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat).setColor(Color.WHITE)
                .setContentTitle(title)
                .setContentText(if (projection) "Transmitindo a tela" else if (muted) "Microfone mutado" else "Voz conectada")
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(Notifier.open(c, "voice", 10))
                .addAction(Notification.Action.Builder(null, if (muted) "Desmutar" else "Mutar", pMute).build())
                .addAction(Notification.Action.Builder(null, "Sair", pLeave).build())
                .build()
        }

        private fun headset(am: AudioManager): Boolean = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }

        /** Sem fone: alto-falante (como no Discord). Com fone ou Bluetooth: vai para ele. */
        fun route(c: Context) {
            val am = c.getSystemService(AudioManager::class.java)
            val hs = headset(am)
            val speaker = speakerChoice ?: !hs
            speakerOn = speaker
            if (Build.VERSION.SDK_INT >= 31) {
                val list = am.availableCommunicationDevices
                val pick = if (speaker) list.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                else list.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                    ?: list.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (pick != null) am.setCommunicationDevice(pick) else am.clearCommunicationDevice()
            } else {
                am.isSpeakerphoneOn = speaker
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(this)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                var type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                if (projection) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(Notifier.ID_ONGOING, n, type)
            } else startForeground(Notifier.ID_ONGOING, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!running) {
            running = true
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "concord:call").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi = wm.createWifiLock(if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF, "concord:call").apply { setReferenceCounted(false); acquire() }
            val am = getSystemService(AudioManager::class.java)
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            route(this)
            devCb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>?) { route(this@CallService) }
                override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>?) { route(this@CallService) }
            }
            am.registerAudioDeviceCallback(devCb, null)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        val am = getSystemService(AudioManager::class.java)
        devCb?.let { am.unregisterAudioDeviceCallback(it) }
        if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice() else am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
        wake?.let { if (it.isHeld) it.release() }
        wifi?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Botões da notificação fixa */
    class Action : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Concord.isReady()) return
            val v = Concord.I.voice
            when (i.action) { "mute" -> v.toggleMute(); "leave" -> v.leave() }
        }
    }
}
