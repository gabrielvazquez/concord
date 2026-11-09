package com.gabrielvazquez.concord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gabrielvazquez.concord.CallService
import com.gabrielvazquez.concord.Channel
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.voice.RemoteVideo
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoView(track: VideoTrack, modifier: Modifier = Modifier, mirror: Boolean = false, fill: Boolean = false) {
    val c = Concord.I
    val ctx = LocalContext.current
    val view = remember(track) {
        SurfaceViewRenderer(ctx).apply {
            init(c.voice.egl.eglBaseContext, null)
            setScalingType(if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true); setMirror(mirror)
        }
    }
    DisposableEffect(track) {
        try { track.addSink(view) } catch (_: Exception) {}
        onDispose { try { track.removeSink(view) } catch (_: Exception) {}; view.release() }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

private data class Tile(val key: String, val uid: String?, val name: String, val color: String?, val muted: Boolean, val deaf: Boolean)

@Composable
fun VoicePane(ch: Channel, onAskMic: (() -> Unit) -> Unit, onScreenShare: () -> Unit, onAskCam: (() -> Unit) -> Unit) {
    val c = Concord.I
    val v = c.voice
    if (v.channel?.id != ch.id) {
        // ainda não está nesta sala
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = C.tx3, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(12.dp))
                Text(ch.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp))
                val users = c.voiceUsers(ch.id)
                Text(if (users.isEmpty()) "Ninguém na sala ainda." else users.joinToString(", ") { c.name(it.uid, it.name) } + if (users.size > 1) " estão aqui" else " está aqui", color = C.tx2)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.width(240.dp)) { PrimaryButton(if (v.channel != null) "Trocar para esta sala" else "Entrar na sala") { onAskMic { v.leave(); c.openVoice(ch) } } }
            }
        }
        return
    }
    val tiles = buildList {
        add(Tile(c.sid, c.meId, c.name(c.meId), c.me?.color, v.muted, v.deaf))
        v.meta.forEach { (k, m) -> if (!m.optBoolean("tv")) add(Tile(k, m.optString("uid").ifEmpty { null }, c.name(m.optString("uid"), m.optString("name")), m.optString("color"), m.optBoolean("muted"), m.optBoolean("deaf"))) }
    }
    val screens = v.videos.filter { v.isScreen(it) }
    val focus = Ui.focusVideo?.let { f -> v.videos.find { it.track.id() == f } } ?: screens.firstOrNull()
    val srv = if (ch.dmId == null) c.servers.find { it.id == ch.serverId } else null
    val stage = stageOf(srv)
    val bar = barOf(srv)
    Column(Modifier.fillMaxSize().then(if (stage != null) Modifier.background(stage.bg) else Modifier)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (focus != null) VideoCard(focus, big = true)
                LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(tiles, key = { it.key }) { t ->
                        val cam = if (t.key == c.sid) null else v.videos.find { it.key == t.key && !v.isScreen(it) }
                        ParticipantTile(t, cam, mine = t.key == c.sid, stage = stage)
                    }
                    items(v.videos.filter { it != focus && v.isScreen(it) }, key = { "s-" + it.track.id() }) { VideoCard(it, big = false) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            ControlBar(bar) {
                Ctl(bar, if (v.muted || v.deaf) Icons.Default.MicOff else Icons.Default.Mic, "Microfone", off = v.muted || v.deaf) { v.toggleMute() }
                Ctl(bar, if (v.deaf) Icons.Default.HeadsetOff else Icons.Default.Headset, "Áudio", off = v.deaf) { v.toggleDeaf() }
                Ctl(bar, if (CallService.speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Hearing, "Alto-falante", active = CallService.speakerOn) { CallService.setSpeaker(c.app, !CallService.speakerOn) }
                Ctl(bar, if (v.camOn) Icons.Default.Videocam else Icons.Default.VideocamOff, "Câmera", active = v.camOn) { if (v.camOn) v.toggleCam() else onAskCam { v.toggleCam() } }
                Ctl(bar, if (v.screenOn) Icons.Default.StopScreenShare else Icons.Default.ScreenShare, "Tela", active = v.screenOn) { if (v.screenOn) { v.stopScreen(); c.trackServer() } else onScreenShare() }
                Ctl(bar, Icons.Default.Tv, "Enviar para a TV", active = c.tvsInRoom() > 0) { Ui.tvOpen = true }
                Box(Modifier.height(42.dp).width(54.dp).clip(bar?.shape?.takeIf { it != CircleShape } ?: RoundedCornerShape(21.dp)).background(C.red).clickable { v.leave() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.CallEnd, "Sair", tint = Color.White) }
            }
        }
    }
}

@Composable
fun TvDialog() {
    val c = Concord.I
    var code by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.lastTvCode()) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val on = c.tvsInRoom() > 0
    androidx.compose.material3.AlertDialog(onDismissRequest = { Ui.tvOpen = false }, containerColor = C.panel,
        title = { Text("Enviar para a TV") },
        text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Text("Na TV, abra o app CONCORD TV ou o site gabrielvazquez.github.io/concord/tv. O som continua aqui.", color = C.tx2, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            val ctx = LocalContext.current
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(enabled = !busy) {
                val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
                com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, opts).startScan()
                    .addOnSuccessListener { b -> val k = tvCodeFrom(b.rawValue); if (k == null) c.say("Esse QR code não é de uma TV do CONCORD.") else { code = k; busy = true; c.pairTv(k) { ok -> busy = false; if (ok) { Ui.tvOpen = false; c.say("TV conectada. O som continua aqui.") } else c.say("Não achei a TV. Confira se ela está na tela do código.") } } }
                    .addOnFailureListener { c.say("Não consegui abrir o leitor. Digite o código.") }
            }.padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, null, tint = Color.Black, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text("Ler o QR code da TV", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("ou digite o código", color = C.tx3, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            field(code, { code = it.filter(Char::isDigit).take(6) }, "Código da TV", kb = androidx.compose.ui.text.input.KeyboardType.Number)
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.HorizontalDivider(color = C.line)
            Spacer(Modifier.height(14.dp))
            androidx.compose.runtime.LaunchedEffect(Unit) { if (c.tvLink == null) c.loadTvLink(create = true) }
            Text("Vincular a TV à sua conta", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Digite este código uma vez na TV, em \"Vincular à conta\". Depois ela abre sozinha sempre que você entrar numa sala de voz.", color = C.tx2, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(c.tvLink?.chunked(3)?.joinToString("  ") ?: "· · ·", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Gerar outro código", color = C.tx3, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable { c.loadTvLink(regen = true) }.padding(8.dp))
        } },
        confirmButton = { androidx.compose.material3.TextButton(enabled = code.length == 6 && !busy, onClick = { busy = true; c.pairTv(code) { ok -> busy = false; if (ok) { Ui.tvOpen = false; c.say("TV conectada. O som continua aqui.") } else c.say("Não achei essa TV. Confira o código.") } }) { Text(if (busy) "Conectando…" else "Conectar", color = Color.White) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { if (on) c.pairTv(c.lastTvCode(), unpair = true) { c.say("TV desconectada.") }; Ui.tvOpen = false }) { Text(if (on) "Desconectar TV" else "Cancelar", color = C.tx2) } })
}

/** barra da chamada em cápsula, igual ao exe; servidores CEO/Founder trocam o estilo */
@Composable
private fun ControlBar(bar: BarStyle?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = when { bar?.shape is androidx.compose.foundation.shape.CutCornerShape -> androidx.compose.foundation.shape.CutCornerShape(12.dp); bar?.shape == RoundedCornerShape(0.dp) -> RoundedCornerShape(0.dp); else -> RoundedCornerShape(30.dp) }
    val ang = spin(bar?.spinMs ?: 0)
    val base = Modifier.clip(shape)
    val m = when {
        bar == null -> base.background(C.card).border(1.dp, C.line, shape)
        bar.bar == null -> Modifier
        else -> {
            val bb = bar.barBorder
            val borderBrush = if (bb == null) androidx.compose.ui.graphics.SolidColor(Color.Transparent) else if (bar.spinMs > 0) androidx.compose.ui.graphics.Brush.sweepGradient(rotateCols(bb, ang)) else androidx.compose.ui.graphics.Brush.linearGradient(bb)
            base.background(bar.bar).border(if (bar.spinMs > 0 || bb?.size ?: 0 > 2) 1.5.dp else 1.dp, borderBrush, shape)
        }
    }
    Row(m.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(if (bar == BARS["split"] || bar == BARS["minimal"]) 10.dp else 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
private fun rotateCols(c: List<Color>, deg: Float): List<Color> {
    val base = c.dropLast(1); val n = base.size; if (n < 2) return c
    val f = deg / 360f * n; val i = f.toInt() % n; val fr = f - f.toInt()
    return (0..n).map { k -> androidx.compose.ui.graphics.lerp(base[(k + i) % n], base[(k + i + 1) % n], fr) }
}

@Composable
private fun Ctl(bar: BarStyle?, icon: ImageVector, label: String, off: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    val shape = bar?.shape ?: CircleShape
    val bg = if (active) Color.White else if (off) Color(0x33FF4D5E) else bar?.btn ?: C.card2
    val bd = if (active) Color.White else if (off) Color(0x664A1F25) else bar?.btnBorder ?: C.line2
    Box(Modifier.size(40.dp).clip(shape).background(bg).border(1.dp, bd, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (active) Color.Black else if (off) C.red else bar?.tint ?: C.tx, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ParticipantTile(t: Tile, cam: RemoteVideo?, mine: Boolean, stage: StageTheme?) {
    val c = Concord.I
    val v = c.voice
    val speaking = v.speaking[t.key] == true
    val p = t.uid?.let { c.profiles[it] }
    val shape = RoundedCornerShape(stage?.radius ?: 16.dp)
    val spk = stage?.speak ?: C.green
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(shape).background(stage?.tile ?: parseColor(p?.bannerColor ?: p?.themePrimary, C.card))
        .border(if (speaking) 2.5.dp else 1.dp, if (speaking) spk else stage?.tileBorder ?: C.line, shape)
        .clickable { t.uid?.let { Ui.profile = it } }) {
        if (stage == null && cam == null && !(mine && v.camTrack != null) && !p?.banner.isNullOrEmpty()) {
            coil.compose.AsyncImage(p!!.banner, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        }
        val myCam = if (mine) v.camTrack else null
        when {
            myCam != null -> VideoView(myCam, Modifier.fillMaxSize(), mirror = true, fill = true)
            cam != null -> VideoView(cam.track, Modifier.fillMaxSize(), fill = true)
            else -> Box(Modifier.align(Alignment.Center)) { Avatar(t.uid, 64.dp, fallbackName = t.name, fallbackColor = t.color, speaking = speaking) }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(if (stage?.radius == 0.dp) 0.dp else 999.dp)).then(if (stage != null) Modifier.background(stage.label) else Modifier.background(Color(0xCC000000))).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (mine) "${t.name} (você)" else t.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = stage?.labelText ?: Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.muted || t.deaf) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.MicOff, null, tint = C.red, modifier = Modifier.size(13.dp)) }
            if (t.deaf) { Spacer(Modifier.width(3.dp)); Icon(Icons.Default.HeadsetOff, null, tint = C.red, modifier = Modifier.size(13.dp)) }
        }
        if (mine && v.camOn) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color(0xAA000000)).clickable { v.flipCam() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Cameraswitch, "Trocar câmera", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun VideoCard(rv: RemoteVideo, big: Boolean) {
    val c = Concord.I
    val uid = c.voice.meta[rv.key]?.optString("uid")
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, C.line, RoundedCornerShape(16.dp))
        .clickable { Ui.focusVideo = if (big) null else rv.track.id() }) {
        VideoView(rv.track, Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(C.red).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("AO VIVO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
        Text(c.name(uid), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 8.dp, vertical = 3.dp))
        if (big && Ui.focusVideo != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color(0xAA000000)).clickable { Ui.focusVideo = null }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CloseFullscreen, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** Picture-in-picture: só a transmissão (ou quem fala) numa janelinha */
@Composable
fun PipView() {
    val c = Concord.I
    val v = c.voice
    val vid = v.videos.firstOrNull { v.isScreen(it) } ?: v.videos.firstOrNull()
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (vid != null) VideoView(vid.track, Modifier.fillMaxSize())
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(28.dp); Spacer(Modifier.height(6.dp))
            Text(v.channel?.name ?: "CONCORD", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (v.muted) "Mutado" else "${v.meta.size + 1} na sala", fontSize = 11.sp, color = C.tx2)
        }
    }
}
