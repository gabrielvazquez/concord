package com.gabrielvazquez.concord.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gabrielvazquez.concord.Channel
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.STATUS_LABEL
import com.gabrielvazquez.concord.Server
import com.gabrielvazquez.concord.View
import kotlinx.coroutines.launch

/** Estado das janelas sobrepostas (perfil, membros, configurações...) */
object Ui {
    var profile by mutableStateOf<String?>(null)
    var membersOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var joinOpen by mutableStateOf(false)
    var statusOpen by mutableStateOf(false)
    var newDmOpen by mutableStateOf(false)
    var pip by mutableStateOf(false)
    var focusVideo by mutableStateOf<String?>(null)
    var tvOpen by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onAskMic: (() -> Unit) -> Unit, onScreenShare: () -> Unit, onAskCam: (() -> Unit) -> Unit) {
    val c = Concord.I
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(c.toast) { c.toast?.let { snack.showSnackbar(it); c.toast = null } }
    LaunchedEffect(Unit) { if (c.view == null) drawer.open() }

    if (Ui.pip) { PipView(); return }

    val act = LocalContext.current as? android.app.Activity
    BackHandler(enabled = drawer.isClosed) { scope.launch { drawer.open() } }
    BackHandler(enabled = drawer.isOpen) { act?.moveTaskToBack(true) }

    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = true, scrimColor = Color(0xAA000000), drawerContent = {
        ModalDrawerSheet(drawerContainerColor = C.bg, drawerShape = RoundedCornerShape(0.dp, 20.dp, 20.dp, 0.dp), modifier = Modifier.width(340.dp)) {
            DrawerContent(close = { scope.launch { drawer.close() } }, onAskMic = onAskMic)
        }
    }) {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                TopBar(onMenu = { scope.launch { drawer.open() } })
                HorizontalDivider(color = C.line)
                UpdateBanner()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val v = c.view) {
                        is View.Text -> ChatPane(v.ch.id, "#" + v.ch.name, readonly = v.ch.readonly && !c.isAdmin)
                        is View.DmChat -> ChatPane("dm-" + v.dm.id, "@" + c.name(v.dm.other(c.meId!!)), readonly = false)
                        is View.Voice -> VoicePane(v.ch, onAskMic, onScreenShare, onAskCam)
                        is View.Home -> HomePane()
                        is View.Friends -> FriendsPane()
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Logo(56.dp, C.tx3); Spacer(Modifier.height(16.dp))
                                if (c.servers.isEmpty()) { Text("Você ainda não está em nenhum servidor", color = C.tx2); TextButton(onClick = { Ui.joinOpen = true }) { Text("Entrar com código") } }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp))
        }
    }

    if (Ui.membersOpen) MembersSheet()
    Ui.profile?.let { ProfileSheet(it) }
    if (Ui.settingsOpen) SettingsScreen()
    if (Ui.joinOpen) JoinDialog()
    if (Ui.statusOpen) StatusSheet()
    if (Ui.newDmOpen) NewDmSheet()
    if (Ui.tvOpen) TvDialog()
    c.incoming?.let { IncomingCallDialog() }
}

@Composable
fun UpdateBanner() {
    val ver = com.gabrielvazquez.concord.Updater.available ?: return
    val act = LocalContext.current as? android.app.Activity ?: return
    val c = Concord.I
    val p = com.gabrielvazquez.concord.Updater.progress
    Row(Modifier.fillMaxWidth().padding(10.dp, 8.dp).clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.line2, RoundedCornerShape(14.dp))
        .clickable(enabled = p < 0f) { com.gabrielvazquez.concord.Updater.install(act, c.scope) { c.say(it) } }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (p < 0f) "Nova versão disponível" else if (p > 1f) "Instalando…" else "Baixando atualização… ${(p * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(if (p < 0f) "Versão $ver · toque para atualizar" else "Confirme \"Atualizar\" quando o Android pedir", color = C.tx3, fontSize = 12.sp)
            if (p in 0f..1f) androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = Color.White, trackColor = C.line2)
        }
        if (p < 0f) Text("Atualizar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun TopBar(onMenu: () -> Unit) {
    val c = Concord.I
    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Menu", tint = C.tx2) }
        val v = c.view
        when (v) {
            is View.Text -> { Icon(Icons.Default.Tag, null, tint = C.tx3, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) { Text(v.ch.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    v.ch.topic?.let { Text(it, color = C.tx3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
            is View.DmChat -> { val o = v.dm.other(c.meId!!); Box(Modifier.clickable { Ui.profile = o }) { Avatar(o, 30.dp, c.statusOf(o)) }; Spacer(Modifier.width(10.dp))
                Text(c.name(o), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { c.startCall(v.dm) }) { Icon(Icons.Default.Call, "Ligar", tint = C.tx2) } }
            is View.Voice -> { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = C.tx3, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Text(v.ch.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.voice.state, color = if (c.voice.state == "Voz conectada") C.green else C.amber, fontSize = 12.sp) } }
            is View.Friends -> Text("Amigos", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
            is View.Home -> Text("Mensagens diretas", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
            null -> Text(c.server?.name ?: "CONCORD", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
        }
        if (!c.connected) Icon(Icons.Default.SignalCellularAlt, "Reconectando", tint = C.amber, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
        if (c.server != null && !c.home) IconButton(onClick = { Ui.membersOpen = true }) { Icon(Icons.Default.People, "Membros", tint = C.tx2) }
    }
}

/* ===================== Gaveta: servidores + canais ===================== */
@Composable
private fun DrawerContent(close: () -> Unit, onAskMic: (() -> Unit) -> Unit) {
    val c = Concord.I
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // trilho de servidores
        Column(Modifier.width(72.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val unreadDm = c.dms.count { c.unread.contains(it.id) }
            RailButton(selected = c.home, badge = unreadDm, onClick = { c.openHome() }) { Box(Modifier.semantics { contentDescription = "Mensagens diretas" }) { Logo(28.dp) } }
            HorizontalDivider(Modifier.width(28.dp), color = C.line2)
            c.servers.forEach { s -> Box { RailButton(selected = !c.home && c.server?.id == s.id, onClick = { c.selectServer(s) }) { ServerIcon(s) }; TierRing(s.tier, Modifier.size(50.dp)) } }
            RailButton(selected = false, onClick = { Ui.joinOpen = true }) { Icon(Icons.Default.Add, "Entrar em servidor", tint = C.tx2) }
            Spacer(Modifier.weight(1f))
            RailButton(selected = false, onClick = { c.logout() }) { Icon(Icons.AutoMirrored.Filled.Logout, "Trocar de conta", tint = C.tx3, modifier = Modifier.size(20.dp)) }
        }
        // lista
        Column(Modifier.weight(1f).fillMaxHeight().padding(end = 6.dp).clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)).background(C.panel)) {
            if (c.home) DmList(close) else ChannelList(close, onAskMic)
            VoiceCard()
            UserBar()
        }
    }
}

@Composable
private fun RailButton(selected: Boolean, badge: Int = 0, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(50.dp)) {
        Box(Modifier.size(48.dp).align(Alignment.Center).clip(RoundedCornerShape(if (selected) 16.dp else 24.dp)).background(if (selected) C.card2 else C.card)
            .border(1.dp, if (selected) C.tx3 else C.line, RoundedCornerShape(if (selected) 16.dp else 24.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
        if (badge > 0) Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(C.red), contentAlignment = Alignment.Center) { Text("$badge", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ServerIcon(s: Server) {
    if (!s.iconUrl.isNullOrEmpty()) AsyncImage(s.iconUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
    else Text(s.icon ?: s.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

@Composable
private fun ColumnScopeHeader(title: String, sub: String? = null, banner: String? = null, accent: String? = null, srv: Server? = null) {
    val has = banner != null || accent != null
    val head = if (srv?.premium == true) srv.head else null
    val tierTint = when (srv?.tier) { "gold" -> Color(0x22F59E0B); "platinum" -> Color(0x22E0E7FF); "diamond" -> Color(0x3310B981); "aurora" -> Color(0x33A78BFA); else -> null }
    Box(Modifier.fillMaxWidth().height(if (!has) 64.dp else if (head == "tall") 150.dp else if (head == "center") 130.dp else 110.dp).clip(RoundedCornerShape(topStart = 18.dp))) {
        if (banner != null) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (accent != null) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(parseColor(accent), C.panel))))
        else if (tierTint != null) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(tierTint, Color.Transparent))))
        if (srv?.premium == true) HeadFx(srv.headFx, Modifier.fillMaxSize())
        if (has) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, if (head == "glass") Color(0x990A0A0B) else Color(0xEE0A0A0B)))))
        val inner = @Composable {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, color = C.tx2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        when {
            head == "center" && has -> Column(Modifier.align(Alignment.BottomCenter).padding(16.dp, 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { inner() }
            head == "glass" && has -> Column(Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x8C0A0A0B)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(14.dp)).padding(10.dp, 8.dp)) { inner() }
            else -> Column(Modifier.align(Alignment.BottomStart).padding(16.dp, 12.dp)) { inner() }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChannelList(close: () -> Unit, onAskMic: (() -> Unit) -> Unit) {
    val c = Concord.I
    val s = c.server
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    if (s == null) { Box(Modifier.weight(1f)) {}; return }
    ColumnScopeHeader(s.name, s.description, s.bannerUrl, s.accent, s)
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(Icons.Default.ContentCopy, s.code) { clip.setText(AnnotatedString(c.inviteLink())); c.say("Link de convite copiado.") }
        Chip(Icons.Default.Share, "Convidar") {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Entra no ${s.name} no CONCORD: ${c.inviteLink()}"), "Convidar"))
        }
    }
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
        val groups = linkedMapOf<String, List<Channel>>()
        groups["Canais de texto"] = c.channels.filter { !it.isVoice && it.category == null }
        groups["Canais de voz"] = c.channels.filter { it.isVoice && it.category == null }
        c.channels.mapNotNull { it.category }.distinct().forEach { cat -> groups[cat] = c.channels.filter { it.category == cat }.sortedBy { if (it.isVoice) 1 else 0 } }
        groups.forEach { (title, list) ->
            if (list.isEmpty()) return@forEach
            item(key = "h-$title") { Text(title.uppercase(), color = C.tx3, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(10.dp, 14.dp, 10.dp, 6.dp)) }
            items(list, key = { it.id }) { ch ->
                val sel = (c.view as? View.Text)?.ch?.id == ch.id || (c.view as? View.Voice)?.ch?.id == ch.id
                val users = if (ch.isVoice) c.voiceUsers(ch.id) else emptyList()
                val acc = srvAccent(s)
                Column {
                    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) (acc?.copy(alpha = .16f) ?: C.card2) else Color.Transparent)
                        .clickable { if (ch.isVoice) onAskMic { c.openVoice(ch) } else c.openText(ch); close() }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (ch.icon != null) Text(ch.icon, fontSize = 16.sp, modifier = Modifier.width(22.dp))
                        else Icon(if (ch.isVoice) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Tag, null, tint = C.tx3, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        val unread = c.unread.contains(ch.id)
                        Text(ch.name, color = if (sel || unread) C.tx else C.tx2, fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ch.readonly) Icon(Icons.Default.Lock, null, tint = C.tx3, modifier = Modifier.size(14.dp))
                        if (ch.isVoice && (users.isNotEmpty() || ch.userLimit > 0)) Text("${users.size}${if (ch.userLimit > 0) "/${ch.userLimit}" else ""}", color = C.tx3, fontSize = 12.sp)
                        if (unread) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(Color.White))
                    }
                    users.forEach { u ->
                        Row(Modifier.fillMaxWidth().padding(start = 34.dp, top = 2.dp, bottom = 2.dp).clickable { Ui.profile = u.uid }, verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.uid, 26.dp, fallbackName = u.name, fallbackColor = u.color, speaking = c.voice.channel?.id == ch.id && (c.voice.speaking[u.key] == true || (u.uid == c.meId && c.voice.speaking[c.sid] == true)))
                            Spacer(Modifier.width(8.dp)); StyledName(u.uid, c.name(u.uid, u.name), 13.5.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (u.screen) Text("AO VIVO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(C.red).padding(4.dp, 1.dp))
                            if (u.muted || u.deaf) Icon(Icons.Default.MicOff, "Mutado", tint = C.red, modifier = Modifier.padding(start = 4.dp).size(17.dp))
                            if (u.deaf) Icon(Icons.Default.HeadsetOff, "Não está ouvindo", tint = C.red, modifier = Modifier.padding(start = 4.dp).size(17.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.line2, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.tx2, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text(text, color = C.tx2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DmList(close: () -> Unit) {
    val c = Concord.I
    ColumnScopeHeader("Mensagens diretas")
    val pend = c.pendingIn().size
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(52.dp).clip(RoundedCornerShape(12.dp)).background(if (c.view is View.Friends) C.card2 else Color.Transparent).clickable { c.view = View.Friends; close() }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(C.card2), contentAlignment = Alignment.Center) { Icon(Icons.Default.People, null, tint = C.tx, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(10.dp)); Text("Amigos", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (pend > 0) Text("$pend", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(C.red).padding(horizontal = 7.dp, vertical = 1.dp))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Chip(Icons.Default.Add, "Nova conversa") { Ui.newDmOpen = true } }
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
        items(c.dms, key = { it.id }) { d ->
            val o = d.other(c.meId!!)
            val sel = (c.view as? View.DmChat)?.dm?.id == d.id
            Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) C.card2 else Color.Transparent).clickable { c.openDm(d); close() }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(o, 36.dp, c.statusOf(o).takeIf { it != "offline" }); Spacer(Modifier.width(10.dp))
                StyledName(o, c.name(o), 15.sp, if (c.unread.contains(d.id)) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (c.voice.channel?.dmId == d.id) Icon(Icons.Default.Call, null, tint = C.green, modifier = Modifier.size(16.dp))
                if (c.unread.contains(d.id)) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(Color.White))
            }
        }
        if (c.dms.isEmpty()) item { Text("Nenhuma conversa ainda.", color = C.tx3, modifier = Modifier.padding(12.dp)) }
    }
}

@Composable
private fun VoiceCard() {
    val c = Concord.I
    val v = c.voice
    val ch = v.channel ?: return
    val ok = v.state == "Voz conectada"
    Column(Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (ch.dmId != null) c.home = true else c.servers.find { it.id == ch.serverId }?.let { if (c.server?.id != it.id) c.selectServer(it) }; c.view = View.Voice(ch) }) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, C.line2, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.SignalCellularAlt, null, tint = if (ok) C.green else C.amber, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) C.green else C.amber)); Spacer(Modifier.width(6.dp)); Text(v.state, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Text(v.title(), color = C.tx2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VcBtn(if (v.muted || v.deaf) Icons.Default.MicOff else Icons.Default.Mic, "Microfone", v.muted || v.deaf) { v.toggleMute() }
            VcBtn(if (v.deaf) Icons.Default.HeadsetOff else Icons.Default.Headset, "Áudio", v.deaf) { v.toggleDeaf() }
            VcBtn(Icons.AutoMirrored.Filled.VolumeUp, "Abrir sala", false) { if (ch.dmId != null) c.home = true; c.view = View.Voice(ch) }
            VcBtn(Icons.Default.CallEnd, "Sair", false, red = true) { v.leave() }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.VcBtn(icon: ImageVector, label: String, off: Boolean, red: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (red) C.red else if (off) Color(0x22FF4D5E) else C.card2).border(1.dp, if (off) Color(0x55FF4D5E) else C.line2, RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (red) Color.White else if (off) C.red else C.tx, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun UserBar() {
    val c = Concord.I
    val me = c.me ?: return
    Row(Modifier.padding(8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable { Ui.statusOpen = true }, verticalAlignment = Alignment.CenterVertically) {
            Avatar(me.id, 36.dp, c.myStatus()); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { StyledName(me.id, c.name(me.id), 14.sp); Text(me.shownStatus ?: STATUS_LABEL[c.myStatus()] ?: "", color = C.tx3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (c.voice.channel != null) {
            IconButton(onClick = { c.voice.toggleMute() }) { Icon(if (c.voice.muted || c.voice.deaf) Icons.Default.MicOff else Icons.Default.Mic, "Microfone", tint = if (c.voice.muted || c.voice.deaf) C.red else C.tx2) }
            IconButton(onClick = { c.voice.toggleDeaf() }) { Icon(if (c.voice.deaf) Icons.Default.HeadsetOff else Icons.Default.Headset, "Áudio", tint = if (c.voice.deaf) C.red else C.tx2) }
        }
        IconButton(onClick = { Ui.settingsOpen = true }) { Icon(Icons.Default.Settings, "Configurações", tint = C.tx2) }
    }
}

/* ===================== Membros (igual ao painel direito do exe) ===================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersSheet() {
    val c = Concord.I
    ModalBottomSheet(onDismissRequest = { Ui.membersOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.panel) {
        val onIds = c.online.map { it.uid }.toSet()
        val offline = c.members.filter { it !in onIds }
        val me = c.me
        val live = c.online.firstOrNull { it.screen && it.voice != null }
        val busiest = c.channels.filter { it.isVoice }.map { it to c.voiceUsers(it.id) }.maxByOrNull { it.second.size }?.takeIf { it.second.isNotEmpty() }
        val featCh = live?.let { l -> c.channels.find { it.id == l.voice } } ?: busiest?.first
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (me != null) item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(16.dp)).clickable { Ui.membersOpen = false; Ui.statusOpen = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me.id, 42.dp, c.myStatus()); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { StyledName(me.id, c.name(me.id), 15.sp); Text(me.shownStatus ?: STATUS_LABEL[c.myStatus()] ?: "", color = C.tx3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Icon(androidx.compose.material.icons.Icons.Default.Settings, null, tint = C.tx3, modifier = Modifier.size(18.dp).clickable { Ui.membersOpen = false; Ui.settingsOpen = true })
                }
            }
            if (featCh != null) item {
                val users = c.voiceUsers(featCh.id)
                Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1C1C20), Color(0xFF111113)))).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, C.line2, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Icon(if (live != null) Icons.Default.SignalCellularAlt else Icons.AutoMirrored.Filled.VolumeUp, null, tint = C.tx2, modifier = Modifier.size(15.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column { Text(if (live != null) "Transmissão ao vivo" else "Sala de voz", color = C.tx2, fontSize = 12.sp); Text(featCh.name, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                    Text(if (live != null) "${c.name(live.uid, live.name)} está transmitindo a tela" else "${users.size} ${if (users.size > 1) "pessoas conversando" else "pessoa na sala"}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f)) { users.take(6).forEachIndexed { i, u -> Box(Modifier.padding(start = if (i == 0) 0.dp else 0.dp).layout { m, cs -> val pl = m.measure(cs); layout(pl.width - if (i == 0) 0 else 8.dp.roundToPx(), pl.height) { pl.place(if (i == 0) 0 else -8.dp.roundToPx(), 0) } }) { Avatar(u.uid, 26.dp, fallbackName = u.name, fallbackColor = u.color) } } }
                        if (c.voice.channel?.id != featCh.id) Text(if (live != null) "Assistir" else "Entrar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { Ui.membersOpen = false; c.openVoice(featCh) }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            item { Text("ONLINE — ${c.online.size}", color = C.tx3, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(8.dp, 16.dp, 8.dp, 6.dp)) }
            items(c.online, key = { "on-" + it.uid }) { o ->
                val ch = o.voice?.let { v -> c.channels.find { it.id == v } }
                MemberRow(o.uid, c.statusOf(o.uid), sub = ch?.let { (if (o.screen) "Transmitindo" else it.name) }, live = o.screen, since = if (ch != null && o.since > 0) o.since else 0L, act = o.act)
            }
            if (offline.isNotEmpty()) item { Text("OFFLINE — ${offline.size}", color = C.tx3, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(8.dp, 16.dp, 8.dp, 6.dp)) }
            items(offline, key = { "off-$it" }) { MemberRow(it, null, dim = true) }
        }
    }
}

@Composable
private fun MemberRow(uid: String, status: String?, sub: String? = null, live: Boolean = false, since: Long = 0L, act: com.gabrielvazquez.concord.Activity? = null, dim: Boolean = false) {
    val c = Concord.I
    val p = c.profiles[uid]
    Row(Modifier.fillMaxWidth().memberCard(if (dim) null else p).clickable { Ui.profile = uid }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.then(if (dim) Modifier.alphaDim() else Modifier)) { Avatar(uid, 38.dp, status) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledName(uid, c.name(uid), 14.5.sp, FontWeight.SemiBold, dim = dim, modifier = Modifier.weight(1f, fill = false))
                TitleBadges(c.titlesOf(uid))
            }
            val small = @Composable { icon: ImageVector?, tint: Color, text: String ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(text, color = C.tx3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (since > 0) { val m = ((System.currentTimeMillis() - since) / 60000).coerceAtLeast(0); Spacer(Modifier.width(6.dp)); Text(if (m < 1) "agora" else if (m < 60) "$m min" else "${m / 60} h", color = C.tx3, fontSize = 11.sp) }
                }
            }
            when {
                sub != null -> small(if (live) Icons.Default.SignalCellularAlt else Icons.AutoMirrored.Filled.VolumeUp, if (live) C.red else C.tx3, sub)
                act?.game != null -> small(Icons.Default.SportsEsports, C.tx3, "Jogando ${act.game}")
                act?.musicTitle != null -> small(Icons.Default.MusicNote, C.spotify, "Ouvindo ${act.musicTitle}")
                p?.shownStatus != null -> small(null, C.tx3, p.shownStatus!!)
            }
        }
    }
}
private fun Modifier.alphaDim() = this.then(Modifier.graphicsLayer(alpha = .45f))

/* ===================== Perfil ===================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(uid: String) {
    val c = Concord.I
    val p = c.profiles[uid]
    LaunchedEffect(uid) { if (p == null) c.loadProfiles(listOf(uid)) }
    ModalBottomSheet(onDismissRequest = { Ui.profile = null }, containerColor = C.panel, dragHandle = null) {
        val bg = cardBrush(p)
        Column(Modifier.fillMaxWidth().then(if (bg != null) Modifier.background(bg) else Modifier).padding(bottom = 28.dp)) {
            BannerBox(p, Modifier.fillMaxWidth().height(130.dp))
            Box(Modifier.padding(start = 20.dp).padding(top = 0.dp)) {
                Box(Modifier.padding(top = 0.dp).offsetUp().clip(avatarShape(p?.shape)).background(C.panel).padding(5.dp)) { Avatar(uid, 92.dp, c.statusOf(uid)) }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledName(uid, c.name(uid), 22.sp, FontWeight.ExtraBold)
                }
                if (c.titlesOf(uid).isNotEmpty()) { Spacer(Modifier.height(6.dp)); TitleBadges(c.titlesOf(uid), big = true) }
                Text(listOfNotNull(p?.username?.let { "@$it" } ?: p?.name, p?.pronouns).joinToString(" · "), color = C.tx2, fontSize = 14.sp)
                p?.shownStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, color = C.tx) }
                val act = c.online.find { it.uid == uid }?.act
                Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (bg != null) Color(0x55000000) else C.card).border(1.dp, if (bg != null) Color(0x1FFFFFFF) else Color.Transparent, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    act?.game?.let { g -> ActivityRow(Icons.Default.SportsEsports, "JOGANDO AGORA", g, if (act.gameSince > 0) sinceLabel(act.gameSince) else null, C.tx) }
                    act?.musicTitle?.let { m -> ActivityRow(Icons.Default.MusicNote, "OUVINDO NO SPOTIFY", m, act.musicArtist, C.spotify) }
                    p?.bio?.let { Text("SOBRE MIM", color = C.tx3, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(it, color = C.tx2, fontSize = 14.sp) }
                    Text("MEMBRO DO CONCORD DESDE", color = C.tx3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(p?.createdAt?.take(10)?.split("-")?.reversed()?.joinToString("/") ?: "—", color = C.tx2, fontSize = 14.sp)
                }
                if (uid != c.meId) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { PrimaryButton("Mensagem") { Ui.profile = null; c.openDmWith(uid) } }
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).clickable { Ui.profile = null; c.openDmWith(uid, call = true) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, "Ligar", tint = C.tx) }
                    }
                } else { Spacer(Modifier.height(14.dp)); PrimaryButton("Editar perfil") { Ui.profile = null; Ui.settingsOpen = true } }
            }
        }
    }
}

private fun Modifier.offsetUp() = this.layout { m, cs ->
    val pl = m.measure(cs); layout(pl.width, pl.height - 48.dp.roundToPx()) { pl.place(0, -48.dp.roundToPx()) }
}

@Composable
private fun ActivityRow(icon: ImageVector, label: String, title: String, sub: String?, tint: Color) {
    Column {
        Text(label, color = C.tx3, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(C.card2), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.width(10.dp))
            Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); sub?.let { Text(it, color = C.tx3, fontSize = 12.sp, maxLines = 1) } }
        }
    }
}

/* ===================== Status (tipos + status personalizado, igual ao exe) ===================== */
private val CST_EMOJI = listOf("🎮","🎧","💻","📚","🍕","☕","😴","🔥","✨","💜","💚","🎬","⚽","🏋️","🚗","✈️","🎉","🤒","🤫","🛠️","🎵","👀","🌙","💎","👑","🧠","🍿","🏖️","📺","🕹️")
private val CST_PRESETS = listOf("🎮" to "Jogando", "📚" to "Estudando", "💻" to "Trabalhando", "🎬" to "Vendo filme", "😴" to "Dormindo", "🍕" to "Comendo", "🚗" to "Na rua", "🤫" to "Não chama")
private val CST_UNTIL = listOf(30 to "30 min", 60 to "1 hora", 240 to "4 horas", -1 to "Hoje", 0 to "Não limpar")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StatusSheet() {
    val c = Concord.I
    val me = c.me
    var custom by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(me?.let { if (it.shownStatus != null) it.statusEmoji else null } ?: "") }
    var text by remember { mutableStateOf(me?.let { if (it.shownStatus != null) it.statusText else null } ?: "") }
    var until by remember { mutableStateOf(0) }
    ModalBottomSheet(onDismissRequest = { Ui.statusOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.panel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            if (!custom) {
                val cur = me?.shownStatus
                if (cur != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).border(1.dp, C.line2, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(cur, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Status personalizado", color = C.tx3, fontSize = 11.sp) }
                    IconButton(onClick = { custom = true }) { Icon(androidx.compose.material.icons.Icons.Default.Settings, "Editar", tint = C.tx2) }
                    TextButton(onClick = { c.setCustomStatus(null, null, 0); Ui.statusOpen = false }) { Text("Limpar", color = C.tx2) }
                } else Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, C.line2, RoundedCornerShape(12.dp)).clickable { custom = true }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🙂", fontSize = 18.sp); Spacer(Modifier.width(12.dp)); Text("Definir status personalizado", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                val curSt = me?.presence ?: "online"
                STATUS_LABEL.forEach { (k, l) ->
                    Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(if (curSt == k) C.card2 else Color.Transparent).clickable { c.setStatus(k); Ui.statusOpen = false }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor(k))); Spacer(Modifier.width(14.dp)); Text(l, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = C.line)
                Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).clickable { Ui.statusOpen = false; Ui.settingsOpen = true }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = C.tx2); Spacer(Modifier.width(14.dp)); Text("Editar perfil", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("Status personalizado", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp); Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(14.dp)).clickable { emoji = "" }, contentAlignment = Alignment.Center) { Text(emoji.ifEmpty { "🙂" }, fontSize = 24.sp) }
                    Spacer(Modifier.width(10.dp)); Box(Modifier.weight(1f)) { field(text, { text = it.take(80) }, "O que você está fazendo?") }
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CST_PRESETS.forEach { (e, t) -> Text("$e $t", fontSize = 13.sp, color = C.tx2, modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, C.line2, RoundedCornerShape(16.dp)).clickable { emoji = e; text = t }.padding(horizontal = 11.dp, vertical = 6.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CST_EMOJI.forEach { e -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (e == emoji) C.card2 else Color.Transparent).clickable { emoji = if (emoji == e) "" else e }, contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) } }
                }
                Spacer(Modifier.height(12.dp))
                Text("LIMPAR DEPOIS DE", color = C.tx3, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CST_UNTIL.forEach { (m, l) -> Text(l, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (until == m) Color.Black else C.tx2, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (until == m) Color.White else Color.Transparent).border(1.dp, if (until == m) Color.White else C.line2, RoundedCornerShape(16.dp)).clickable { until = m }.padding(horizontal = 12.dp, vertical = 6.dp)) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.line2, RoundedCornerShape(14.dp)).clickable { c.setCustomStatus(null, null, 0); Ui.statusOpen = false }, contentAlignment = Alignment.Center) { Text("Limpar", fontWeight = FontWeight.Bold) }
                    Box(Modifier.weight(1f)) { PrimaryButton("Salvar") { c.setCustomStatus(text, emoji, until); Ui.statusOpen = false } }
                }
            }
        }
    }
}

/* ===================== Diálogos ===================== */
@Composable
private fun JoinDialog() {
    val c = Concord.I
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { Ui.joinOpen = false }, containerColor = C.panel,
        title = { Text(if (create) "Criar servidor" else "Entrar em servidor") },
        text = { Column {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.card)) {
                listOf(false to "Entrar com código", true to "Criar").forEach { (v, l) -> Text(l, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center, color = if (create == v) Color.Black else C.tx2,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (create == v) Color.White else Color.Transparent).clickable { create = v; code = "" }.padding(vertical = 9.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            if (create) { Text("Dê um nome. Depois é só mandar o código para a galera.", color = C.tx2); Spacer(Modifier.height(12.dp)); field(code, { code = it.take(40) }, "Nome do servidor") }
            else { Text("Cole o código ou o link de convite.", color = C.tx2); Spacer(Modifier.height(12.dp)); field(code, { code = it.substringAfterLast('#').uppercase().take(24) }, "Código") }
        } },
        confirmButton = { TextButton(enabled = (if (create) code.isNotBlank() else code.length >= 3) && !busy, onClick = { busy = true; if (create) c.createServer(code) { busy = false; if (it) Ui.joinOpen = false } else c.joinByCode(code) { busy = false; if (it) Ui.joinOpen = false } }) { Text(if (busy) "Aguarde…" else if (create) "Criar" else "Entrar", color = Color.White) } },
        dismissButton = { TextButton(onClick = { Ui.joinOpen = false }) { Text("Cancelar", color = C.tx2) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDmSheet() {
    val c = Concord.I
    ModalBottomSheet(onDismissRequest = { Ui.newDmOpen = false }, containerColor = C.panel) {
        LazyColumn(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            item { Text("Nova conversa", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(8.dp)) }
            items(c.profiles.values.filter { it.id != c.meId }.sortedBy { it.name.lowercase() }, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Ui.newDmOpen = false; c.openDmWith(p.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.id, 36.dp); Spacer(Modifier.width(12.dp)); Text(c.name(p.id), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun IncomingCallDialog() {
    val c = Concord.I
    val inc = c.incoming ?: return
    AlertDialog(onDismissRequest = {}, containerColor = C.panel,
        icon = { Avatar(inc.from, 72.dp) },
        title = { Text(c.name(inc.from), fontWeight = FontWeight.ExtraBold) },
        text = { Text("Chamada de voz", color = C.tx2) },
        confirmButton = { TextButton(onClick = { c.answer(true) }) { Icon(Icons.Default.Call, null, tint = C.green); Spacer(Modifier.width(6.dp)); Text("Atender", color = C.green, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { c.answer(false) }) { Icon(Icons.Default.CallEnd, null, tint = C.red); Spacer(Modifier.width(6.dp)); Text("Recusar", color = C.red) } })
}

@Composable
private fun FriendsPane() {
    val c = Concord.I
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.gabrielvazquez.concord.Profile>>(emptyList()) }
    LaunchedEffect(q) { kotlinx.coroutines.delay(300); results = c.searchUsers(q) }
    LaunchedEffect(Unit) { c.loadFriends() }
    val me = c.me
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Adicionar amigo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Procure pelo @ ou pelo nome. O seu é @${me?.username ?: "…"}.", color = C.tx2, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            field(q, { q = it.take(30) }, "@nome ou nome")
            Spacer(Modifier.height(6.dp))
        }
        if (q.trim().removePrefix("@").length >= 2) {
            if (results.isEmpty()) item { Text("Ninguém com esse nome.", color = C.tx3, modifier = Modifier.padding(10.dp)) }
            items(results, key = { "r-" + it.id }) { p -> FriendRow(p.id) }
            item { HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 10.dp)) }
        }
        val pin = c.friends.filter { it.status == "pending" && it.requester != c.meId }
        val fr = c.friends.filter { it.status == "accepted" }
        val pout = c.friends.filter { it.status == "pending" && it.requester == c.meId }
        listOf("Pedidos recebidos" to pin, "Amigos" to fr, "Pedidos enviados" to pout).forEach { (t, l) ->
            if (l.isNotEmpty()) {
                item { Text("${t.uppercase()} — ${l.size}", color = C.tx3, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                items(l, key = { t + it.other(c.meId!!) }) { f -> FriendRow(f.other(c.meId!!)) }
            }
        }
        if (c.friends.isEmpty() && q.isBlank()) item { Text("Você ainda não adicionou ninguém. Procure um amigo acima.", color = C.tx3, modifier = Modifier.padding(vertical = 18.dp)) }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun FriendRow(uid: String) {
    val c = Concord.I
    val p = c.profiles[uid]
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Ui.profile = uid }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(uid, 42.dp, c.statusOf(uid).takeIf { it != "offline" }); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            StyledName(uid, c.name(uid), 15.sp, FontWeight.SemiBold)
            p?.username?.let { Text("@$it", color = C.tx3, fontSize = 12.sp) }
        }
        when (if (uid == c.meId) "me" else c.friendState(uid)) {
            "me" -> Text("Você", color = C.tx3, fontSize = 12.sp)
            "friend" -> {
                IconButton(onClick = { c.openDmWith(uid) }) { Icon(Icons.Default.Chat, "Mensagem", tint = C.tx2) }
                IconButton(onClick = { c.openDmWith(uid, call = true) }) { Icon(Icons.Default.Call, "Ligar", tint = C.tx2) }
            }
            "received" -> {
                Text("Aceitar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { c.friendRespond(uid, true) }.padding(horizontal = 12.dp, vertical = 7.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recusar", color = C.tx, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, C.line2, RoundedCornerShape(10.dp)).clickable { c.friendRespond(uid, false) }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
            "sent" -> Text("Cancelar pedido", color = C.tx2, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, C.line2, RoundedCornerShape(10.dp)).clickable { c.friendRespond(uid, false) }.padding(horizontal = 10.dp, vertical = 6.dp))
            else -> Text("Adicionar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { c.friendRequest(uid) }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun HomePane() {
    val c = Concord.I
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Chat, null, tint = C.tx3, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(12.dp))
            Text(if (c.dms.isEmpty()) "Nenhuma conversa ainda" else "Escolha uma conversa", color = C.tx2)
            TextButton(onClick = { Ui.newDmOpen = true }) { Text("Nova conversa", color = Color.White) }
            TextButton(onClick = { c.view = View.Friends }) { Text("Procurar amigos", color = C.tx2) }
        }
    }
}
