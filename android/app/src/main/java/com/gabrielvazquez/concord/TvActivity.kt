package com.gabrielvazquez.concord

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielvazquez.concord.net.RtChannel
import com.gabrielvazquez.concord.ui.AvatarRaw
import com.gabrielvazquez.concord.ui.C
import com.gabrielvazquez.concord.ui.ConcordTheme
import com.gabrielvazquez.concord.ui.Logo
import com.gabrielvazquez.concord.ui.QrCode
import com.gabrielvazquez.concord.ui.tvLink
import com.gabrielvazquez.concord.ui.VideoView
import kotlinx.coroutines.delay
import org.json.JSONObject

/** CONCORD TV: mostra um código, recebe o pareamento do celular/PC e assiste a sala em tela cheia (som opcional). */
class TvActivity : ComponentActivity() {
    private val c get() = Concord.I
    private var pairRt: RtChannel? = null
    private var code by mutableStateOf("")
    private var online by mutableStateOf(false)
    private var room by mutableStateOf<String?>(null)
    private var owner by mutableStateOf("")
    private var sel by mutableIntStateOf(0)
    private var osd by mutableStateOf<String?>(null)
    private var osdAt by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("concord", 0)
        code = prefs.getString("tv_my_code", null) ?: (100000 + (Math.random() * 900000).toInt()).toString().also { prefs.edit().putString("tv_my_code", it).apply() }
        c.rt.allowAnon = true
        c.rt.onStatus = { online = it; c.connected = it }
        c.rt.connect()
        listen()
        Updater.check(c, force = true)
        setContent { ConcordTheme { Screen() } }
    }

    private fun listen() {
        val r = c.rt.channel("dc-tv-$code", null)
        pairRt = r
        r.onJoined = { online = true }
        r.on("pair") { p ->
            val roomId = p.optString("room"); if (roomId.isEmpty()) return@on
            owner = p.optString("name", "alguém")
            r.send("paired", JSONObject().put("ok", true).put("room", roomId))
            c.voice.joinAsTv(Channel(roomId, "", p.optString("roomName", "Sala de voz"), "voice", 0, null, null, null, false, 0, 0), p.optString("uid").ifEmpty { null }, owner)
            room = p.optString("roomName", "Sala de voz"); sel = 0
            show("Conectada à sala de $owner · OK liga o som na TV")
        }
        r.on("unpair") { stopWatching() }
    }

    private fun stopWatching() { c.voice.leave(); room = null }
    private fun show(s: String) { osd = s; osdAt = System.currentTimeMillis() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (room == null) {
            if (Updater.available != null && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) { Updater.install(this, c.scope) { show(it) }; return true }
            return super.onKeyDown(keyCode, event)
        }
        val screens = c.voice.videos.filter { c.voice.isScreen(it) }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> { if (screens.size > 1) { sel = (sel + 1) % screens.size; show("Transmissão ${sel + 1} de ${screens.size}") }; return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (screens.size > 1) { sel = (sel - 1 + screens.size) % screens.size; show("Transmissão ${sel + 1} de ${screens.size}") }; return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { c.voice.toggleTvSound(); show(if (c.voice.tvSound) "Som na TV ligado" else "Som só no celular/PC"); return true }
            KeyEvent.KEYCODE_BACK -> { stopWatching(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { if (isFinishing) { c.voice.leave(); pairRt?.leave() }; super.onDestroy() }

    @Composable
    private fun Screen() {
        LaunchedEffect(osdAt) { if (osd != null) { delay(4500); osd = null } }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (room == null) PairScreen() else Watch()
            osd?.let {
                Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart).padding(40.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xCC000000)).padding(horizontal = 22.dp, vertical = 12.dp))
            }
        }
    }

    @Composable
    private fun PairScreen() {
        Column(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF18181C), Color.Black), radius = 1400f, center = androidx.compose.ui.geometry.Offset(960f, -100f))),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { Logo(40.dp); Spacer(Modifier.width(14.dp)); Text("C O N C O R D   T V", color = C.tx2, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(250.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp)) { QrCode(tvLink(code), Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(14.dp))
                    Text("Celular: aponte a câmera", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(70.dp))
                Box(Modifier.width(1.dp).height(220.dp).background(C.line2))
                Spacer(Modifier.width(70.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(code.chunked(3).joinToString("  "), fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Text("PC: na sala de voz, clique em \"Enviar para a TV\"", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("e digite este código", color = C.tx2, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (online) C.green else C.amber)); Spacer(Modifier.width(10.dp))
                Text(if (online) "Pronta para conectar" else "Conectando…", color = C.tx2, fontSize = 18.sp)
            }
            Updater.available?.let { ver ->
                Spacer(Modifier.height(30.dp))
                val p = Updater.progress
                Text(if (p < 0f) "Nova versão $ver disponível · aperte OK para atualizar" else if (p > 1f) "Instalando… confirme na tela" else "Baixando atualização… ${(p * 100).toInt()}%",
                    color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(Color.White).padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
    }

    @Composable
    private fun Watch() {
        val v = c.voice
        val screens = v.videos.filter { v.isScreen(it) }
        val cams = v.videos.filter { !v.isScreen(it) }
        when {
            screens.isNotEmpty() -> {
                val cur = screens[sel.coerceIn(0, screens.size - 1)]
                Box(Modifier.fillMaxSize()) {
                    VideoView(cur.track, Modifier.fillMaxSize())
                    val nm = v.meta[cur.key]?.optString("name") ?: ""
                    Row(Modifier.align(Alignment.TopStart).padding(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("AO VIVO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(C.red).padding(horizontal = 6.dp, vertical = 1.dp))
                        Spacer(Modifier.width(10.dp)); Text("Tela de $nm", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            cams.isNotEmpty() -> LazyVerticalGrid(GridCells.Fixed(if (cams.size > 1) 2 else 1), Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(cams, key = { it.track.id() }) { cv ->
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(C.card)) {
                        VideoView(cv.track, Modifier.fillMaxSize(), fill = true)
                        Text(v.meta[cv.key]?.optString("name") ?: "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(room ?: "", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    v.meta.values.filter { !it.optBoolean("tv") }.forEach { m ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AvatarRaw(null, m.optString("name"), m.optString("color"), 96.dp, speaking = false); Spacer(Modifier.height(10.dp))
                            Text(m.optString("name"), color = C.tx2, fontSize = 18.sp)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp)); Text("Aguardando alguém transmitir…", color = C.tx3, fontSize = 18.sp)
            }
        }
    }
}
