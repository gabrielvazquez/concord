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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
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
    private var roomId: String? = null
    // TV vinculada a uma conta: segue a pessoa nas salas de voz
    private var linkCode by mutableStateOf<String?>(null)
    private var linkName by mutableStateOf<String?>(null)
    private var linkInput by mutableStateOf("")
    private var linkRt: RtChannel? = null
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
        linkCode = prefs.getString("tv_link", null); linkName = prefs.getString("tv_link_name", null)
        linkCode?.let { listenLink(it) }
        Updater.check(c, force = true)
        setContent { ConcordTheme { Screen() } }
    }

    private fun listen() {
        val r = c.rt.channel("dc-tv-$code", null)
        pairRt = r
        r.onJoined = { online = true }
        attach(r, linked = false)
    }

    private fun listenLink(k: String) {
        linkRt?.leave()
        val r = c.rt.channel("dc-tvlink-$k", null)
        linkRt = r
        r.onJoined = { r.send("hello", JSONObject()) }
        r.on("linked") { p -> saveLinkName(p.optString("name")) }
        attach(r, linked = true)
    }

    private fun saveLinkName(n: String) { if (n.isEmpty()) return; linkName = n; getSharedPreferences("concord", 0).edit().putString("tv_link_name", n).apply() }

    private fun link() {
        val k = linkInput.filter(Char::isDigit); if (k.length != 6) return
        getSharedPreferences("concord", 0).edit().putString("tv_link", k).remove("tv_link_name").apply()
        linkCode = k; linkName = null; linkInput = ""
        listenLink(k); show("TV vinculada. Ela abre sozinha quando a conta entrar numa sala de voz.")
    }

    private fun unlink() {
        linkRt?.leave(); linkRt = null; linkCode = null; linkName = null
        getSharedPreferences("concord", 0).edit().remove("tv_link").remove("tv_link_name").apply()
        show("TV desvinculada.")
    }

    private fun attach(r: RtChannel, linked: Boolean) {
        r.on("pair") { p ->
            val rid = p.optString("room"); if (rid.isEmpty()) return@on
            owner = p.optString("name", "alguém")
            if (linked) saveLinkName(owner)
            r.send("paired", JSONObject().put("ok", true).put("room", rid))
            if (roomId == rid && room != null) return@on
            if (room != null) c.voice.leave()
            c.voice.joinAsTv(Channel(rid, "", p.optString("roomName", "Sala de voz"), "voice", 0, null, null, null, false, 0, 0), p.optString("uid").ifEmpty { null }, owner)
            room = p.optString("roomName", "Sala de voz"); roomId = rid; sel = 0
            show("Conectada à sala de $owner · OK liga o som na TV")
        }
        r.on("unpair") { p -> if (!linked || p.optString("room") == roomId) stopWatching() }
    }

    private fun stopWatching() { c.voice.leave(); room = null; roomId = null }
    private fun show(s: String) { osd = s; osdAt = System.currentTimeMillis() }

    // OK do controle na tela do código: se há atualização e não se está digitando, atualiza (o Compose às vezes engolia a tecla)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || event.keyCode == KeyEvent.KEYCODE_BUTTON_A
        val typing = currentFocus is android.widget.EditText
        if (ok && room == null && Updater.available != null && Updater.progress < 0f && linkInput.isEmpty() && !typing) {
            if (event.action == KeyEvent.ACTION_UP) Updater.install(this, c.scope) { show(it) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

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

    override fun onDestroy() { if (isFinishing) { c.voice.leave(); pairRt?.leave(); linkRt?.leave() }; super.onDestroy() }

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
            Row(verticalAlignment = Alignment.CenterVertically) { Logo(32.dp); Spacer(Modifier.width(12.dp)); Text("C O N C O R D   T V", color = C.tx2, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(170.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp)) { QrCode(tvLink(code), Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(14.dp))
                    Text("Celular: aponte a câmera", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(70.dp))
                Box(Modifier.width(1.dp).height(150.dp).background(C.line2))
                Spacer(Modifier.width(70.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(code.chunked(3).joinToString("  "), fontSize = 80.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Text("PC: na sala de voz, clique em \"Enviar para a TV\"", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("e digite este código", color = C.tx2, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (online) C.green else C.amber)); Spacer(Modifier.width(10.dp))
                Text(if (online) "Pronta para conectar" else "Conectando…", color = C.tx2, fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (linkCode == null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ou vincule à sua conta:", color = C.tx2, fontSize = 18.sp); Spacer(Modifier.width(16.dp))
                androidx.compose.material3.OutlinedTextField(value = linkInput, onValueChange = { v -> linkInput = v.filter(Char::isDigit).take(6); if (linkInput.length == 6) link() },
                    singleLine = true, placeholder = { Text("código da conta", color = C.tx3) }, modifier = Modifier.width(260.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { link() }))
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vinculada à conta de ${linkName ?: "…"} · abre sozinha quando entrar numa sala", color = C.tx2, fontSize = 18.sp); Spacer(Modifier.width(16.dp))
                androidx.compose.material3.OutlinedButton(onClick = { unlink() }) { Text("Desvincular", color = Color.White) }
            }
            Updater.available?.let { ver ->
                Spacer(Modifier.height(30.dp))
                val p = Updater.progress
                // botão de verdade: recebe o foco do controle e o OK clica nele
                val fr = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
                var focused by androidx.compose.runtime.remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(ver) { kotlinx.coroutines.delay(300); try { fr.requestFocus() } catch (_: Exception) {} }
                Text(if (p < 0f) "Nova versão $ver disponível · aperte OK para atualizar" else if (p > 1f) "Instalando… confirme na tela" else "Baixando atualização… ${(p * 100).toInt()}%",
                    color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.focusRequester(fr).onFocusChanged { focused = it.isFocused }
                        .border(if (focused) 3.dp else 0.dp, if (focused) C.green else Color.Transparent, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp)).background(Color.White)
                        .clickable { Updater.install(this@TvActivity, c.scope) { show(it) } }
                        .padding(horizontal = 24.dp, vertical = 12.dp))
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
