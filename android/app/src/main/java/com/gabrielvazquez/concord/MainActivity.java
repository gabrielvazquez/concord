package com.gabrielvazquez.concord;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * CONCORD para Android: o app é o próprio site (sempre atualizado) dentro de um WebView
 * otimizado, mais o que só um app nativo tem: chamada em segundo plano, notificações,
 * botão voltar, áudio no alto-falante e escolha de fotos.
 */
public class MainActivity extends Activity {
    static final String APP_URL = "https://gabrielvazquez.github.io/concord/";
    static final String ORIGIN = "https://gabrielvazquez.github.io";
    static final String UPDATE_URL = "https://github.com/gabrielvazquez/concord/releases/download/android-latest/version.json";
    static final String APK_URL = "https://github.com/gabrielvazquez/concord/releases/download/android-latest/CONCORD.apk";
    static final String CH_MSG = "mensagens", CH_CALL = "chamadas";

    static MainActivity instance;
    WebView web;
    FrameLayout root;
    View fullView;
    WebChromeClient.CustomViewCallback fullCallback;
    ValueCallback<Uri[]> fileCallback;
    PermissionRequest pendingPerm;
    boolean inCall = false, visible = false, offline = false;
    final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        instance = this;
        getWindow().setBackgroundDrawableResource(R.color.black);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        createChannels();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setOffscreenPreRaster(false);
        s.setUserAgentString(s.getUserAgentString() + " ConcordAndroid/" + BuildConfigCompat.version(this));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        WebView.setWebContentsDebuggingEnabled(false);

        web.addJavascriptInterface(new Bridge(), "concordAndroid");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        if (saved != null) web.restoreState(saved);
        if (saved == null || web.getUrl() == null) web.loadUrl(urlFromIntent(getIntent()));

        // Volta sozinho quando a internet voltar
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { ui.post(() -> { if (offline) { offline = false; web.loadUrl(APP_URL); } }); }
        });

        askNotificationPermission();
    }

    String urlFromIntent(Intent i) {
        Uri d = i != null ? i.getData() : null;
        if (d != null && "gabrielvazquez.github.io".equals(d.getHost())) return d.toString();
        return APP_URL;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        Uri d = i.getData();
        if (d != null && d.getFragment() != null && !d.getFragment().isEmpty()) {
            // link de convite com o app já aberto
            js("window.__concordInvite && window.__concordInvite(" + JSONObject.quote(d.getFragment()) + ")");
        }
        String act = i.getStringExtra("concord_action");
        if (act != null) js("window.__concordNative && window.__concordNative(" + JSONObject.quote(act) + ")");
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        web.onResume();
        cancelAllNotifications();
        js("window.__concordNative && window.__concordNative('resume')");
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        // Fora de chamada, a página para de desenhar (economiza bateria); em chamada continua tudo rodando.
        if (!inCall) web.onPause();
        js("window.__concordNative && window.__concordNative('pause')");
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        CallService.stop(this);
        web.destroy();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (fullView != null) { exitFullscreen(); return; }
        web.evaluateJavascript("(window.__concordBack && window.__concordBack()) ? 1 : 0", r -> {
            if (!"1".equals(r)) moveTaskToBack(true);
        });
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) js("window.__concordNative && window.__concordNative('trim')");
    }

    void js(String code) { ui.post(() -> { if (web != null) web.evaluateJavascript(code, null); }); }

    /* ---------------- Navegação ---------------- */
    class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
            Uri u = req.getUrl();
            String url = u.toString();
            if (url.startsWith(ORIGIN + "/concord")) return false;
            if (url.startsWith("file:///android_asset/")) return false;
            openExternal(url);
            return true;
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            if (req.isForMainFrame()) { offline = true; v.loadUrl("file:///android_asset/offline.html?url=" + Uri.encode(APP_URL)); }
        }
    }

    /* ---------------- Câmera/microfone, fotos, tela cheia ---------------- */
    class Chrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(PermissionRequest req) {
            ui.post(() -> {
                String origin = req.getOrigin() != null ? req.getOrigin().toString() : "";
                if (!origin.startsWith(ORIGIN)) { req.deny(); return; }
                List<String> need = new ArrayList<>();
                for (String r : req.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && !has(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO);
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && !has(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
                }
                if (need.isEmpty()) { req.grant(req.getResources()); return; }
                pendingPerm = req;
                requestPermissions(need.toArray(new String[0]), 11);
            });
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            try { startActivityForResult(Intent.createChooser(i, "Escolher imagem"), 21); }
            catch (ActivityNotFoundException e) { fileCallback = null; return false; }
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback cb) {
            if (fullView != null) { cb.onCustomViewHidden(); return; }
            fullView = view; fullCallback = cb;
            root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            immersive(true);
        }

        @Override public void onHideCustomView() { exitFullscreen(); }

        @Override public android.graphics.Bitmap getDefaultVideoPoster() { return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888); }
    }

    void exitFullscreen() {
        if (fullView == null) return;
        root.removeView(fullView); fullView = null;
        if (fullCallback != null) fullCallback.onCustomViewHidden();
        fullCallback = null;
        immersive(false);
    }

    void immersive(boolean on) {
        WindowInsetsController c = Build.VERSION.SDK_INT >= 30 ? getWindow().getInsetsController() : null;
        if (c != null) {
            if (on) { c.hide(WindowInsets.Type.systemBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
            else c.show(WindowInsets.Type.systemBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(on ? (View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) : 0);
        }
    }

    boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 11 && pendingPerm != null) {
            List<String> ok = new ArrayList<>();
            for (String r : pendingPerm.getResources()) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && has(Manifest.permission.RECORD_AUDIO)) ok.add(r);
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && has(Manifest.permission.CAMERA)) ok.add(r);
            }
            if (ok.isEmpty()) pendingPerm.deny(); else pendingPerm.grant(ok.toArray(new String[0]));
            pendingPerm = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == 21 && fileCallback != null) {
            Uri[] r = null;
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null && data.getClipData().getItemCount() > 0) r = new Uri[]{ data.getClipData().getItemAt(0).getUri() };
                else if (data.getData() != null) r = new Uri[]{ data.getData() };
            }
            fileCallback.onReceiveValue(r);
            fileCallback = null;
        }
    }

    void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) {}
    }

    /* ---------------- Notificações ---------------- */
    void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel m = new NotificationChannel(CH_MSG, "Mensagens", NotificationManager.IMPORTANCE_HIGH);
        m.setDescription("Mensagens e menções");
        m.enableVibration(true);
        NotificationChannel c = new NotificationChannel(CH_CALL, "Chamadas recebidas", NotificationManager.IMPORTANCE_HIGH);
        c.setDescription("Quando alguém te liga");
        c.enableVibration(true);
        c.setVibrationPattern(new long[]{0, 600, 400, 600, 400, 600});
        c.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        nm.createNotificationChannel(m);
        nm.createNotificationChannel(c);
        CallService.createChannel(this);
    }

    void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS))
            requestPermissions(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, 12);
    }

    void cancelAllNotifications() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications())
            if (n.getId() != CallService.NOTIF_ID) nm.cancel(n.getTag(), n.getId());
    }

    PendingIntent openIntent(String action, int req) {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (action != null) i.putExtra("concord_action", action);
        return PendingIntent.getActivity(this, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void showNotification(String title, String body, String tag, String kind) {
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) return;
        boolean call = "call".equals(kind);
        Notification.Builder b = new Notification.Builder(this, call ? CH_CALL : CH_MSG)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(Color.WHITE)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(openIntent(call ? "call-open:" + tag : "open:" + tag, tag.hashCode()))
                .setCategory(call ? Notification.CATEGORY_CALL : Notification.CATEGORY_MESSAGE);
        if (call) {
            b.setOngoing(true).setTimeoutAfter(40000)
             .setFullScreenIntent(openIntent("call-open:" + tag, tag.hashCode() + 1), true)
             .addAction(new Notification.Action.Builder(null, "Recusar", openIntent("call-decline:" + tag, tag.hashCode() + 2)).build())
             .addAction(new Notification.Action.Builder(null, "Atender", openIntent("call-accept:" + tag, tag.hashCode() + 3)).build());
        }
        getSystemService(NotificationManager.class).notify(tag, call ? 2 : 1, b.build());
    }

    /* ---------------- Ponte com o site ---------------- */
    class Bridge {
        @JavascriptInterface public String version() { return BuildConfigCompat.version(MainActivity.this); }
        @JavascriptInterface public int versionCode() { return BuildConfigCompat.code(MainActivity.this); }
        @JavascriptInterface public boolean isVisible() { return visible; }

        @JavascriptInterface public void callStart(String title, boolean muted) {
            ui.post(() -> { inCall = true; CallService.start(MainActivity.this, title, muted); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
        }
        @JavascriptInterface public void callUpdate(String title, boolean muted) { ui.post(() -> { if (inCall) CallService.start(MainActivity.this, title, muted); }); }
        @JavascriptInterface public void callEnd() {
            ui.post(() -> { inCall = false; CallService.stop(MainActivity.this); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); if (!visible) web.onPause(); });
        }
        @JavascriptInterface public void setSpeaker(boolean on) { ui.post(() -> CallService.setSpeaker(MainActivity.this, on)); }
        @JavascriptInterface public boolean speakerOn() { return CallService.speakerOn; }

        @JavascriptInterface public void notify(String title, String body, String tag, String kind) {
            ui.post(() -> showNotification(title, body, tag == null || tag.isEmpty() ? "concord" : tag, kind));
        }
        @JavascriptInterface public void clearNotification(String tag) {
            ui.post(() -> { NotificationManager nm = getSystemService(NotificationManager.class); nm.cancel(tag, 1); nm.cancel(tag, 2); });
        }
        @JavascriptInterface public void vibrate(int ms) {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(Math.max(10, Math.min(ms, 800)), VibrationEffect.DEFAULT_AMPLITUDE));
        }
        @JavascriptInterface public void openExternal(String url) { if (url != null && url.startsWith("https://")) ui.post(() -> MainActivity.this.openExternal(url)); }
        @JavascriptInterface public void downloadUpdate() { ui.post(() -> MainActivity.this.openExternal(APK_URL)); }

        @JavascriptInterface public void checkUpdate() {
            new Thread(() -> {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(UPDATE_URL + "?t=" + System.currentTimeMillis()).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(true);
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) { String l; while ((l = r.readLine()) != null) sb.append(l); }
                    JSONObject o = new JSONObject(sb.toString());
                    int latest = o.optInt("versionCode", 0);
                    js("window.__concordNative && window.__concordNative('update', " + JSONObject.quote(o.toString()) + ", " + (latest > BuildConfigCompat.code(MainActivity.this)) + ")");
                } catch (Exception ignored) {}
            }).start();
        }
    }
}
