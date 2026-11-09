package com.gabrielvazquez.concord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.Profile

/* Editor de aparência do perfil, com as mesmas opções do site/exe e as travas por título */
private data class Opt(val k: String, val l: String, val t: String = "")
private val O_DECO = listOf(Opt("", "Nenhuma"), Opt("ring", "Anel"), Opt("rainbow", "Arco-íris"), Opt("fire", "Fogo"), Opt("ice", "Gelo"), Opt("neon", "Neon"), Opt("gold", "Ouro"),
    Opt("galaxy", "Galáxia"), Opt("electric", "Elétrico"), Opt("pulse", "Radar"), Opt("glitch", "Glitch"), Opt("halo", "Auréola"), Opt("crown", "Coroa"), Opt("orbit", "Órbita"),
    Opt("sparkle", "Brilhos"), Opt("hearts", "Corações"), Opt("rgb", "RGB"), Opt("cyber", "Cyber"), Opt("sun", "Sol"), Opt("moon", "Lua"), Opt("star", "Estrela"), Opt("double", "Anel duplo"),
    Opt("aura", "Aura"), Opt("toxic", "Tóxico"), Opt("glints", "Pontos de luz"), Opt("bronze", "Anel de bronze", "moderador"), Opt("silverhalo", "Halo prateado", "adm"), Opt("frost", "Cristais de gelo", "adm"),
    Opt("goldflame", "Chama dourada", "diretor"), Opt("starorbit", "Órbita de estrelas", "diretor"), Opt("crystal", "Halo de cristal", "presidente"), Opt("amecrown", "Coroa de ametista", "ceo"),
    Opt("vortex", "Vórtice roxo", "ceo"), Opt("aurorahalo", "Coroa de aurora", "fundador"))
private val O_SHAPE = listOf(Opt("", "Círculo"), Opt("squircle", "Arredondado"), Opt("square", "Quadrado"), Opt("hex", "Hexágono"), Opt("diamond", "Losango"))
private val O_NFX = listOf(Opt("", "Nenhum"), Opt("glow", "Brilho"), Opt("shimmer", "Cintilar"), Opt("neon", "Neon"), Opt("rainbow", "Arco-íris"), Opt("glitch", "Glitch"), Opt("outline", "Contorno"),
    Opt("soft", "Suave"), Opt("emboss", "Relevo", "moderador"), Opt("ice", "Gelo", "adm"), Opt("chrome", "Cromado", "adm"), Opt("liquidgold", "Ouro líquido", "diretor"), Opt("neonblink", "Neon piscando", "diretor"),
    Opt("holo", "Holográfico", "presidente"), Opt("glitchp", "Glitch roxo", "ceo"), Opt("amethyst", "Ametista", "ceo"), Opt("aurora", "Aurora", "fundador"))
private val O_FONT = listOf(Opt("", "Padrão"), Opt("Space Grotesk", "Space Grotesk"), Opt("Playfair Display", "Playfair"), Opt("Bebas Neue", "Bebas Neue"), Opt("Pacifico", "Pacifico"),
    Opt("Press Start 2P", "Pixel"), Opt("JetBrains Mono", "Mono"), Opt("Orbitron", "Orbitron"), Opt("Caveat", "Caveat"), Opt("Bungee", "Bungee"), Opt("Righteous", "Righteous"),
    Opt("Permanent Marker", "Marcador"), Opt("Lobster", "Lobster"), Opt("Audiowide", "Audiowide"), Opt("Silkscreen", "Silkscreen"), Opt("Creepster", "Terror"), Opt("Dancing Script", "Cursiva"),
    Opt("Rubik Glitch", "Glitch"), Opt("Bangers", "Quadrinhos"), Opt("Russo One", "Russo"), Opt("Comfortaa", "Comfortaa"), Opt("Kanit", "Kanit"), Opt("Major Mono Display", "Major Mono"),
    Opt("Cinzel", "Cinzel", "moderador"), Opt("Unbounded", "Unbounded", "adm"), Opt("Syncopate", "Syncopate", "diretor"), Opt("Michroma", "Michroma", "diretor"), Opt("Bodoni Moda", "Bodoni", "presidente"),
    Opt("Cinzel Decorative", "Cinzel Deco", "ceo"), Opt("Tilt Neon", "Tilt Neon", "ceo"), Opt("Megrim", "Megrim", "fundador"), Opt("Bungee Shade", "Bungee Shade", "fundador"), Opt("Monoton", "Monoton", "fundador"))
private val O_PLATE = listOf(Opt("", "Nenhuma")) + listOf("aurora" to "Aurora", "sunset" to "Pôr do sol", "ocean" to "Oceano", "cyber" to "Cyber", "gold" to "Ouro", "galaxy" to "Galáxia", "matrix" to "Matrix",
    "sakura" to "Sakura", "lava" to "Lava", "mono" to "Monocromo", "neon" to "Neon", "candy" to "Doce", "forest" to "Floresta", "ice" to "Gelo", "toxic" to "Tóxico", "royal" to "Real", "retro" to "Retrô", "mint" to "Menta").map { Opt(it.first, it.second) }
private val O_BAN = listOf(Opt("", "Nenhum")) + listOf("aurora" to "Aurora", "sunset" to "Pôr do sol", "ocean" to "Oceano", "nebula" to "Nebulosa", "lava" to "Lava", "forest" to "Floresta", "cyber" to "Cyber",
    "waves" to "Ondas", "stripes" to "Listras", "dots" to "Bolinhas", "grid" to "Grade neon", "candy" to "Doce", "midnight" to "Meia-noite", "gold" to "Ouro", "ice" to "Gelo", "toxic" to "Tóxico", "sakura" to "Sakura").map { Opt(it.first, it.second) } +
    listOf(Opt("mist", "Névoa"), Opt("embers", "Brasas", "moderador"), Opt("ripples", "Ondas d’água", "adm"), Opt("sunrays", "Raios de sol", "diretor"), Opt("storm", "Tempestade", "diretor"),
        Opt("galaxyspin", "Galáxia girando", "presidente"), Opt("petalfall", "Chuva de pétalas", "presidente"), Opt("violetsmoke", "Fumaça violeta", "ceo"), Opt("prism", "Prisma", "fundador"), Opt("northern", "Aurora boreal", "fundador"))
private val O_PB = listOf(Opt("", "Nenhuma"), Opt("silk", "Seda"), Opt("bronze", "Bronze", "moderador"), Opt("silver", "Prata viva", "adm"), Opt("goldline", "Ouro vivo", "diretor"), Opt("neon", "Neon", "diretor"),
    Opt("platinum", "Platina", "presidente"), Opt("prism", "Prisma", "presidente"), Opt("emerald", "Esmeralda", "ceo"), Opt("pulse", "Pulso", "ceo"), Opt("aurora", "Aurora", "fundador"), Opt("cosmic", "Cósmica", "fundador"))
private val COLORS = listOf("#ffffff", "#f43f5e", "#f97316", "#f59e0b", "#facc15", "#84cc16", "#22c55e", "#10b981", "#14b8a6", "#06b6d4", "#3b82f6", "#6366f1", "#8b5cf6", "#a855f7", "#d946ef", "#ec4899")
private val TITLE_NAME = mapOf("moderador" to "MOD", "adm" to "ADMIN", "diretor" to "DIRECTOR", "presidente" to "PRESIDENT", "ceo" to "CEO", "fundador" to "FOUNDER")
private val RANKS = mapOf("" to 0, "moderador" to 2, "adm" to 3, "diretor" to 4, "presidente" to 5, "ceo" to 6, "fundador" to 7)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onClose: () -> Unit) {
    val c = Concord.I
    val me0 = c.me ?: return
    var d by remember { mutableStateOf(me0) }
    var tab by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val myRank = rankOf(me0.id)
    val ok = { t: String -> myRank >= (RANKS[t] ?: 0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Text("Aparência do perfil", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(if (busy) "Salvando…" else "Salvar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(enabled = !busy) { busy = true; c.saveLook(d) { busy = false; onClose() } }.padding(horizontal = 16.dp, vertical = 9.dp))
            }
            // pré-visualização: cartão + como fica na lista
            Column(Modifier.padding(horizontal = 14.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardBrush(d) ?: Brush.verticalGradient(listOf(C.panel, C.panel))).border(1.dp, C.line, RoundedCornerShape(18.dp))) {
                    BannerBox(d, Modifier.fillMaxWidth().height(70.dp))
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FancyAvatar(d, d.id, 58.dp, "online"); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { StyledNameP(d, d.name, 20.sp, FontWeight.ExtraBold); TitleBadges(c.titlesOf(d.id)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().memberCard(d).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FancyAvatar(d, d.id, 36.dp, "online"); Spacer(Modifier.width(10.dp)); StyledNameP(d, d.name, 14.5.sp, FontWeight.SemiBold)
                }
            }
            val tabs = listOf("Nome", "Avatar", "Cartão", "Premium")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { i, l -> Text(l, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (tab == i) Color.Black else C.tx2,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (tab == i) Color.White else Color.Transparent).border(1.dp, if (tab == i) Color.White else C.line2, RoundedCornerShape(20.dp)).clickable { tab = i }.padding(horizontal = 14.dp, vertical = 8.dp)) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (tab) {
                    0 -> {
                        Group("Cor do nome") {
                            Swatches(d.nameColor) { d = d.copy(nameColor = it, nameColor2 = if (it == null) null else d.nameColor2) }
                            Spacer(Modifier.height(8.dp)); Text("Segunda cor (gradiente)", color = C.tx3, fontSize = 12.sp); Spacer(Modifier.height(6.dp))
                            Swatches(d.nameColor2) { d = d.copy(nameColor2 = it, nameColor = d.nameColor ?: if (it != null) "#ffffff" else null) }
                        }
                        Group("Efeito") { Chips(O_NFX, d.nameEffect, ok) { d = d.copy(nameEffect = it, nameColor = d.nameColor ?: if (it != null && it != "shimmer" && it != "rainbow") "#ffffff" else null) } }
                        Group("Fonte") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                O_FONT.forEach { o -> val can = ok(o.t); val on = (d.nameFont ?: "") == o.k
                                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.card2 else C.card).border(1.dp, if (on) Color.White else C.line2, RoundedCornerShape(12.dp)).clickable(enabled = can) { d = d.copy(nameFont = o.k.ifEmpty { null }) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(o.l, fontFamily = Fonts.get(o.k), fontSize = 14.sp, color = if (can) C.tx else C.tx3)
                                            LockTag(o.t, can)
                                        }
                                    }
                                }
                            }
                        }
                        Group("Placa de nome") { Chips(O_PLATE, d.plate, ok) { d = d.copy(plate = it) } }
                    }
                    1 -> {
                        Group("Moldura") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                O_DECO.forEach { o -> val can = ok(o.t); val on = (d.decoration ?: "") == o.k
                                    Column(Modifier.width(96.dp).clip(RoundedCornerShape(14.dp)).background(if (on) C.card2 else C.card).border(1.dp, if (on) Color.White else C.line2, RoundedCornerShape(14.dp)).clickable(enabled = can) { d = d.copy(decoration = o.k.ifEmpty { null }) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        FancyAvatar(d.copy(decoration = o.k), d.id, 44.dp)
                                        Spacer(Modifier.height(6.dp)); Text(o.l, fontSize = 11.5.sp, color = if (can) C.tx2 else C.tx3, maxLines = 1)
                                        if (o.t.isNotEmpty()) LockTag(o.t, can)
                                    }
                                }
                            }
                        }
                        Group("Formato") { Chips(O_SHAPE, d.shape, ok) { d = d.copy(shape = it) } }
                    }
                    2 -> {
                        Group("Banner animado") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                O_BAN.forEach { o -> val can = ok(o.t); val on = (d.bannerAnim ?: "") == o.k
                                    Column(Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.card2 else C.card).border(1.dp, if (on) Color.White else C.line2, RoundedCornerShape(12.dp)).clickable(enabled = can) { d = d.copy(bannerAnim = o.k.ifEmpty { null }) }.padding(6.dp)) {
                                        BannerBox(d.copy(banner = null, bannerAnim = o.k.ifEmpty { null }), Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(Modifier.height(5.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text(o.l, fontSize = 11.5.sp, color = if (can) C.tx2 else C.tx3, maxLines = 1, modifier = Modifier.weight(1f, fill = false)); LockTag(o.t, can) }
                                    }
                                }
                            }
                        }
                        Group("Cores do cartão") {
                            Text("Principal", color = C.tx3, fontSize = 12.sp); Spacer(Modifier.height(6.dp))
                            Swatches(d.themePrimary, dark = true) { d = d.copy(themePrimary = it) }
                            Spacer(Modifier.height(8.dp)); Text("Destaque", color = C.tx3, fontSize = 12.sp); Spacer(Modifier.height(6.dp))
                            Swatches(d.themeAccent, dark = true) { d = d.copy(themeAccent = it) }
                        }
                    }
                    else -> {
                        Group("Borda do mini card") { Chips(O_PB, d.premBorder, ok) { d = d.copy(premBorder = it) } }
                        Group("Status animado") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Anel do status com as cores do seu título", modifier = Modifier.weight(1f), fontSize = 14.sp)
                                Switch(d.premRing, { d = d.copy(premRing = it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(10.dp)); content()
    }
}

@Composable
private fun LockTag(t: String, can: Boolean) {
    if (t.isEmpty()) return
    Row(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(5.dp)).background(if (can) Color(0x22FFFFFF) else Color(0x14FFFFFF)).padding(horizontal = 4.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!can) { Icon(Icons.Default.Lock, null, tint = C.tx3, modifier = Modifier.size(9.dp)); Spacer(Modifier.width(2.dp)) }
        Text(TITLE_NAME[t] ?: t, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = if (can) C.tx2 else C.tx3, letterSpacing = 0.6.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(opts: List<Opt>, cur: String?, ok: (String) -> Boolean, pick: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opts.forEach { o -> val can = ok(o.t); val on = (cur ?: "") == o.k
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Color.Transparent).border(1.dp, if (on) Color.White else C.line2, RoundedCornerShape(16.dp)).clickable(enabled = can) { pick(o.k.ifEmpty { null }) }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o.l, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else if (can) C.tx2 else C.tx3)
                LockTag(o.t, can)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(cur: String?, dark: Boolean = false, pick: (String?) -> Unit) {
    val list = if (dark) listOf("#18181b", "#1e1b4b", "#312e81", "#4c1d95", "#581c87", "#701a75", "#831843", "#7f1d1d", "#7c2d12", "#78350f", "#14532d", "#064e3b", "#134e4a", "#0c4a6e", "#1e3a8a", "#0a0a0b") else COLORS
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, if (cur == null) Color.White else C.line2, CircleShape).clickable { pick(null) }, contentAlignment = Alignment.Center) { Text("—", color = C.tx3) }
        list.forEach { h -> val on = cur.equals(h, true)
            Box(Modifier.size(32.dp).clip(CircleShape).background(parseColor(h)).border(if (on) 2.5.dp else 1.dp, if (on) Color.White else C.line2, CircleShape).clickable { pick(h) })
        }
    }
}

@Suppress("unused") private fun Profile.k() = id
