package com.gabrielvazquez.concord

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gabrielvazquez.concord.ui.AccountsScreen
import com.gabrielvazquez.concord.ui.C
import com.gabrielvazquez.concord.ui.ConcordTheme
import com.gabrielvazquez.concord.ui.LoginScreen
import com.gabrielvazquez.concord.ui.Logo
import com.gabrielvazquez.concord.ui.MainScreen
import com.gabrielvazquez.concord.ui.NewProfileScreen
import com.gabrielvazquez.concord.ui.Ui

class MainActivity : ComponentActivity() {
    private val c get() = Concord.I
    private var pendingPerm: (() -> Unit)? = null
    private var pendingInvite: String? = null

    private val askPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val run = pendingPerm; pendingPerm = null
        if (ok) run?.invoke() else c.say("Permissão negada. Libere nas configurações do Android.")
    }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val askScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) c.voice.startScreen(data)
    }

    private fun withPerm(p: String, run: () -> Unit) {
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) run() else { pendingPerm = run; askPerm.launch(p) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        c.askMic = { run -> withPerm(Manifest.permission.RECORD_AUDIO, run) }
        if (c.screen == Screen.Loading) c.boot()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        handle(intent)
        setContent { ConcordTheme { Root() } }
    }

    @Composable
    private fun Root() {
        when (val s = c.screen) {
            Screen.Loading -> Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) { Logo(84.dp) }
            Screen.Accounts -> AccountsScreen()
            is Screen.Login -> LoginScreen(s)
            Screen.NewProfile -> NewProfileScreen()
            Screen.Main -> {
                LaunchedEffect(Unit) { pendingInvite?.let { c.joinByCode(it); pendingInvite = null } }
                MainScreen(
                    onAskMic = { run -> withPerm(Manifest.permission.RECORD_AUDIO, run) },
                    onScreenShare = { withPerm(Manifest.permission.RECORD_AUDIO) { askScreen.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) } },
                    onAskCam = { run -> withPerm(Manifest.permission.CAMERA, run) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        i ?: return
        i.data?.fragment?.takeIf { it.isNotBlank() }?.let { code -> if (c.screen == Screen.Main) c.joinByCode(code) else pendingInvite = code }
        val act = i.getStringExtra("concord_action") ?: return
        i.removeExtra("concord_action")
        val kind = act.substringBefore(":"); val id = act.substringAfter(":", "")
        when (kind) {
            "open" -> when {
                id.startsWith("dm-") -> c.dms.find { it.id == id.removePrefix("dm-") }?.let { c.openDm(it) }
                id.startsWith("ch-") -> c.channels.find { it.id == id.removePrefix("ch-") }?.let { c.openText(it) }
            }
            "voice" -> c.voice.channel?.let { c.view = View.Voice(it) }
            "call-accept" -> withPerm(Manifest.permission.RECORD_AUDIO) { c.answer(true) }
            "call-decline" -> c.answer(false)
            "call-open" -> {}
        }
    }

    override fun onStart() { super.onStart(); c.onForeground(true) }
    override fun onStop() { super.onStop(); c.onForeground(false) }

    /** Saiu do app durante uma transmissão: continua assistindo numa janelinha (PiP) */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (c.voice.channel != null && c.voice.videos.isNotEmpty()) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Ui.pip = isInPictureInPictureMode
    }
}
