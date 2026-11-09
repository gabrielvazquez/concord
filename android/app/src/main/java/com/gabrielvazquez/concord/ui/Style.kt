@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.gabrielvazquez.concord.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.Profile
import com.gabrielvazquez.concord.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/* ====================================================================
   Visual igual ao do site/exe: fontes do nome, cores e efeitos, molduras,
   placas, bordas premium, anel de status, temas do servidor e da chamada.
   ==================================================================== */

fun hex(vararg c: Long) = c.map { Color(it) }

/** Fontes do Google baixadas na hora (ficam guardadas no aparelho) */
object Fonts {
    val fams = mutableStateMapOf<String, FontFamily>()
    private val loading = HashSet<String>()
    private val http by lazy { okhttp3.OkHttpClient() }
    fun get(name: String?): FontFamily? {
        if (name.isNullOrBlank()) return null
        fams[name]?.let { return it }
        if (loading.add(name)) Concord.I.scope.launch {
            val fam = withContext(Dispatchers.IO) {
                try {
                    val dir = File(Concord.I.app.filesDir, "fonts").apply { mkdirs() }
                    val f = File(dir, name.replace(Regex("[^A-Za-z0-9]"), "_") + ".ttf")
                    if (!f.exists() || f.length() < 1000) {
                        val css = http.newCall(okhttp3.Request.Builder().url("https://fonts.googleapis.com/css2?family=" + name.replace(' ', '+')).build()).execute().use { it.body?.string() ?: "" }
                        val url = Regex("""url\((https://[^)]+)\)""").find(css)?.groupValues?.get(1) ?: return@withContext null
                        val bytes = http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { it.body?.bytes() } ?: return@withContext null
                        f.writeBytes(bytes)
                    }
                    FontFamily(Font(f))
                } catch (_: Exception) { null }
            }
            if (fam != null) fams[name] = fam
        }
        return null
    }
}

/* ---------- nome com cor, gradiente e efeito ---------- */
private val NAME_FX_COLORS = mapOf(
    "rainbow" to hex(0xFFF43F5E, 0xFFF59E0B, 0xFF22C55E, 0xFF06B6D4, 0xFF8B5CF6, 0xFFF43F5E),
    "aurora" to hex(0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, 0xFFF472B6, 0xFF34D399),
    "holo" to hex(0xFFA5F3FC, 0xFFF0ABFC, 0xFFFDE68A, 0xFFA7F3D0, 0xFFA5F3FC),
    "liquidgold" to hex(0xFF92400E, 0xFFFDE68A, 0xFFF59E0B, 0xFFFFF7D6, 0xFF92400E),
    "amethyst" to hex(0xFF7C3AED, 0xFFE9D5FF, 0xFFA78BFA, 0xFF7C3AED),
    "chrome" to hex(0xFF71717A, 0xFFFFFFFF, 0xFFA1A1AA, 0xFFF4F4F5, 0xFF71717A),
    "ice" to hex(0xFFE0F2FE, 0xFF7DD3FC, 0xFFFFFFFF, 0xFF38BDF8),
    "glitchp" to hex(0xFFA78BFA, 0xFF22D3EE, 0xFFF472B6, 0xFFA78BFA),
)
private val GLOW_FX = setOf("glow", "neon", "neonblink", "soft", "glitch", "glitchp", "emboss", "outline")

@Composable
fun StyledName(uid: String?, text: String, size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Bold, dim: Boolean = false, fallback: Color = C.tx, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val p = uid?.let { Concord.I.profiles[it] }
    StyledNameP(p, text, size, weight, dim, fallback, modifier, maxLines)
}

@Composable
fun StyledNameP(p: Profile?, text: String, size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Bold, dim: Boolean = false, fallback: Color = C.tx, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val font = Fonts.get(p?.nameFont)
    val c1 = p?.nameColor?.let { parseColor(it, fallback) } ?: fallback
    val c2 = p?.nameColor2?.let { parseColor(it, c1) }
    val fx = p?.nameEffect
    val animated = fx == "shimmer" || fx == "rainbow" || NAME_FX_COLORS.containsKey(fx)
    val t = if (animated && !dim) {
        val tr = rememberInfiniteTransition(label = "nm")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(if (fx == "rainbow") 3000 else 2600, easing = LinearEasing)), label = "nmt").value
    } else 0f
    val w = 400f
    val brush: Brush? = when {
        dim -> null
        fx == "shimmer" -> Brush.linearGradient(listOf(c1.copy(alpha = .75f), Color.White, c1.copy(alpha = .75f)), start = Offset(-w + 2 * w * t, 0f), end = Offset(2 * w * t, 0f), tileMode = TileMode.Mirror)
        NAME_FX_COLORS.containsKey(fx) -> Brush.linearGradient(NAME_FX_COLORS[fx]!!, start = Offset(-w * t, 0f), end = Offset(w - w * t, 0f), tileMode = TileMode.Mirror)
        c2 != null -> Brush.linearGradient(listOf(c1, c2))
        else -> null
    }
    val shadow = if (!dim && fx in GLOW_FX) Shadow(if (fx == "neon" || fx == "neonblink") c1 else c1.copy(alpha = .6f), Offset.Zero, if (fx == "neon" || fx == "neonblink") 18f else 10f) else null
    val style = TextStyle(fontSize = size, fontWeight = weight, fontFamily = font, shadow = shadow, color = if (dim) C.tx3 else if (brush == null) c1 else Color.Unspecified)
    Text(text, style = if (brush != null) style.copy(brush = brush) else style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/* ---------- molduras do avatar ---------- */
private class Deco(val colors: List<Color>, val ms: Int = 4000, val width: Float = 0.085f, val pulse: Boolean = false)
private val DECOS = mapOf(
    "ring" to Deco(hex(0xFFFFFFFF, 0xFFA1A1AA, 0xFFFFFFFF), 0),
    "rainbow" to Deco(hex(0xFFF43F5E, 0xFFF59E0B, 0xFF22C55E, 0xFF06B6D4, 0xFF8B5CF6, 0xFFF43F5E), 3000),
    "fire" to Deco(hex(0xFFFACC15, 0xFFF97316, 0xFFEF4444, 0xFFF97316, 0xFFFACC15), 1800),
    "ice" to Deco(hex(0xFFFFFFFF, 0xFFBAE6FD, 0xFF38BDF8, 0xFFBAE6FD, 0xFFFFFFFF), 5000),
    "neon" to Deco(hex(0xFF22D3EE, 0xFFE879F9, 0xFF22D3EE), 2500),
    "gold" to Deco(hex(0xFF78350F, 0xFFFDE68A, 0xFFB45309, 0xFFFFF7D6, 0xFF78350F), 4500),
    "galaxy" to Deco(hex(0xFF312E81, 0xFFA855F7, 0xFFEC4899, 0xFF6366F1, 0xFF312E81), 4000),
    "electric" to Deco(hex(0xFFFACC15, 0xFFFFFFFF, 0xFF60A5FA, 0xFFFACC15), 900),
    "pulse" to Deco(hex(0xFF22C55E, 0xFF22C55E), 0, pulse = true),
    "glitch" to Deco(hex(0xFF22D3EE, 0xFFF43F5E, 0xFF22D3EE), 700),
    "halo" to Deco(hex(0xFFFFFFFF, 0xFFFDE68A, 0xFFFFFFFF), 6000),
    "crown" to Deco(hex(0xFFFDE68A, 0xFFB45309, 0xFFFDE68A), 5000),
    "rgb" to Deco(hex(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFF0000), 1500),
    "cyber" to Deco(hex(0xFFE879F9, 0xFF22D3EE, 0xFFE879F9), 2000),
    "aura" to Deco(hex(0xFFA78BFA, 0xFF34D399, 0xFFA78BFA), 3500, pulse = true),
    "toxic" to Deco(hex(0xFF84CC16, 0xFF1A2E05, 0xFF84CC16), 2500),
    "orbit" to Deco(hex(0xFFFFFFFF, 0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF), 2500),
    "sparkle" to Deco(hex(0xFFFDE68A, 0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF, 0xFFFDE68A), 3000),
    "hearts" to Deco(hex(0xFFF472B6, 0xFFFB7185, 0xFFF472B6), 3000),
    "sun" to Deco(hex(0xFFFDE047, 0xFFF97316, 0xFFFDE047), 6000),
    "moon" to Deco(hex(0xFFE0E7FF, 0xFF6366F1, 0xFFE0E7FF), 7000),
    "star" to Deco(hex(0xFFFDE68A, 0xFFFFFFFF, 0xFFFDE68A), 4000),
    "double" to Deco(hex(0xFFFFFFFF, 0xFF71717A, 0xFFFFFFFF), 0, 0.12f),
    "glints" to Deco(hex(0xFFFFFFFF, 0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF), 5000),
    "bronze" to Deco(hex(0xFF9A5B2A, 0xFFF0B47A, 0xFF7C3F17, 0xFFF0B47A, 0xFF9A5B2A), 6000),
    "silverhalo" to Deco(hex(0xFF71717A, 0xFFF4F4F5, 0xFFA1A1AA, 0xFFF4F4F5, 0xFF71717A), 4500),
    "frost" to Deco(hex(0xFFE0F2FE, 0xFF7DD3FC, 0xFFFFFFFF, 0xFF38BDF8, 0xFFE0F2FE), 4000),
    "goldflame" to Deco(hex(0xFFFFF7D6, 0xFFFDE68A, 0xFFF59E0B, 0xFFB45309, 0xFFFFF7D6), 1600),
    "starorbit" to Deco(hex(0xFFFDE68A, 0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF, 0xFFFDE68A), 2200),
    "crystal" to Deco(hex(0xFFE0E7FF, 0xFFFFFFFF, 0xFFC7D2FE, 0xFF94A3B8, 0xFFE0E7FF), 3000),
    "amecrown" to Deco(hex(0xFF7C3AED, 0xFFE9D5FF, 0xFFA78BFA, 0xFF7C3AED), 2400),
    "vortex" to Deco(hex(0xFF4C1D95, 0xFFA78BFA, 0x004C1D95, 0xFFA78BFA, 0xFF4C1D95), 1200),
    "aurorahalo" to Deco(hex(0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, 0xFFF472B6, 0xFF34D399), 2000),
)

fun avatarShape(k: String?): Shape = when (k) {
    "squircle" -> RoundedCornerShape(30)
    "square" -> RoundedCornerShape(18)
    "hex" -> GenericShape { s, _ -> val w = s.width; val h = s.height; moveTo(w * .25f, 0f); lineTo(w * .75f, 0f); lineTo(w, h / 2); lineTo(w * .75f, h); lineTo(w * .25f, h); lineTo(0f, h / 2); close() }
    "diamond" -> GenericShape { s, _ -> moveTo(s.width / 2, 0f); lineTo(s.width, s.height / 2); lineTo(s.width / 2, s.height); lineTo(0f, s.height / 2); close() }
    else -> CircleShape
}

/** cores do anel premium do status, por título */
private val STATUS_RING = mapOf(
    "fundador" to hex(0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, 0xFF22D3EE, 0xFF34D399),
    "ceo" to hex(0xFF6EE7B7, 0xFF065F46, 0xFFCCFBF1, 0xFF065F46, 0xFF6EE7B7),
    "presidente" to hex(0xFFE0E7FF, 0xFF94A3B8, 0xFFC7D2FE, 0xFF94A3B8, 0xFFE0E7FF),
    "diretor" to hex(0xFFFDE68A, 0xFFB45309, 0xFFFFF7D6, 0xFFB45309, 0xFFFDE68A),
    "adm" to hex(0xFFF4F4F5, 0xFF71717A, 0xFFD4D4D8, 0xFF71717A, 0xFFF4F4F5),
    "moderador" to hex(0xFFF0B47A, 0xFF9A5B2A, 0xFFFFE1C2, 0xFF9A5B2A, 0xFFF0B47A),
)

@Composable
fun spin(ms: Int): Float {
    if (ms <= 0) return 0f
    val t = rememberInfiniteTransition(label = "spin")
    return t.animateFloat(0f, 360f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "a").value
}

/** Avatar completo: foto, formato, moldura animada e status (com anel premium do título) */
@Composable
fun FancyAvatar(p: Profile?, uid: String?, size: Dp, status: String? = null, fallbackName: String? = null, fallbackColor: String? = null, speaking: Boolean = false, deco: Boolean = true) {
    val name = p?.name ?: fallbackName ?: "?"
    val d = if (deco) DECOS[p?.decoration] else null
    val shape = avatarShape(p?.shape)
    val ang = spin(d?.ms ?: 0)
    val pulse = if (d?.pulse == true) { val t = rememberInfiniteTransition(label = "pl"); t.animateFloat(.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p").value } else 1f
    Box(Modifier.size(size)) {
        val inner = if (d != null) size * 0.84f else size
        Box(Modifier.size(inner).align(Alignment.Center).then(if (speaking) Modifier.border(2.dp, C.green, shape) else Modifier).clip(shape).background(parseColor(p?.color ?: fallbackColor)), contentAlignment = Alignment.Center) {
            val url = p?.avatar
            if (!url.isNullOrEmpty()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (inner.value * 0.42f).sp)
        }
        if (d != null) Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.width * d.width
            rotate(ang) { drawCircle(Brush.sweepGradient(d.colors), radius = this.size.width / 2 - sw / 2, style = Stroke(sw), alpha = pulse) }
        }
        if (status != null) StatusDot(uid, status, size, p?.premRing != false)
    }
}

@Composable
private fun BoxScope.StatusDot(uid: String?, status: String, size: Dp, ringOn: Boolean) {
    val titles = uid?.let { Concord.I.titlesOf(it) } ?: emptyList()
    val ring = if (ringOn) STATUS_RING[titles.firstOrNull()] else null
    val d = (size.value * (if (ring != null) 0.38f else 0.32f)).coerceAtLeast(if (ring != null) 11f else 9f).dp
    val ang = spin(if (ring != null) 2600 else 0)
    Box(Modifier.size(d).align(Alignment.BottomEnd).offset(1.dp, 1.dp).clip(CircleShape).background(C.panel), contentAlignment = Alignment.Center) {
        if (ring != null) Canvas(Modifier.fillMaxSize()) { rotate(ang) { drawCircle(Brush.sweepGradient(ring), radius = this.size.width / 2 - 1.5f, style = Stroke(this.size.width * .22f)) } }
        Box(Modifier.size(d * (if (ring != null) 0.48f else 0.66f)).clip(CircleShape).background(statusColor(status)))
    }
}

/* ---------- placa do nome e borda premium (lista de membros) ---------- */
val PLATES = mapOf(
    "aurora" to hex(0xFF0E7490, 0xFF6D28D9, 0xFF065F46), "sunset" to hex(0xFF9A3412, 0xFFBE185D, 0xFF7C2D12), "ocean" to hex(0xFF1E3A8A, 0xFF0E7490, 0xFF1E40AF),
    "cyber" to hex(0xFF701A75, 0xFF0891B2, 0xFF701A75), "gold" to hex(0xFF78350F, 0xFFB45309, 0xFF422006), "galaxy" to hex(0xFF1E1B4B, 0xFF581C87, 0xFF1E1B4B),
    "matrix" to hex(0xFF052E16, 0xFF14532D), "sakura" to hex(0xFF831843, 0xFFF9A8D4, 0xFF9D174D), "lava" to hex(0xFF7F1D1D, 0xFFEA580C, 0xFF450A0A),
    "mono" to hex(0xFF27272A, 0xFF52525B, 0xFF18181B), "neon" to hex(0xFFBE185D, 0xFF7C3AED, 0xFF0891B2), "candy" to hex(0xFFF9A8D4, 0xFF93C5FD, 0xFFFBCFE8),
    "forest" to hex(0xFF14532D, 0xFF3F6212, 0xFF052E16), "ice" to hex(0xFF0C4A6E, 0xFFBAE6FD, 0xFF0369A1), "toxic" to hex(0xFF1A2E05, 0xFF65A30D, 0xFF1A2E05),
    "royal" to hex(0xFF312E81, 0xFFA16207, 0xFF4C1D95), "retro" to hex(0xFFF97316, 0xFFDB2777, 0xFF7C3AED), "mint" to hex(0xFF064E3B, 0xFF5EEAD4, 0xFF065F46),
)
private class PBorder(val colors: List<Color>, val ms: Int, val fill: List<Color>, val title: String)
private val PBORDERS = mapOf(
    "silk" to PBorder(hex(0x30FFFFFF, 0x08FFFFFF, 0x30FFFFFF), 0, hex(0x00000000, 0x00000000), ""),
    "bronze" to PBorder(hex(0xFF9A5B2A, 0xFFF0B47A, 0xFF7C3F17, 0xFFF0B47A, 0xFF9A5B2A), 0, hex(0x149A5B2A, 0x00000000), "moderador"),
    "silver" to PBorder(hex(0xFF71717A, 0xFFF4F4F5, 0xFFA1A1AA, 0xFFF4F4F5, 0xFF71717A), 6000, hex(0x0DF4F4F5, 0x00000000), "adm"),
    "goldline" to PBorder(hex(0xFF78350F, 0xFFFDE68A, 0xFFB45309, 0xFFFFF7D6, 0xFF78350F), 5000, hex(0x14F59E0B, 0x00000000), "diretor"),
    "neon" to PBorder(hex(0xFF22D3EE, 0xFFE879F9, 0xFF22D3EE), 0, hex(0x1022D3EE, 0x10E879F9), "diretor"),
    "platinum" to PBorder(hex(0xFF94A3B8, 0xFFFFFFFF, 0xFFC7D2FE, 0xFFE2E8F0, 0xFF94A3B8), 4000, hex(0x12C7D2FE, 0x00000000), "presidente"),
    "prism" to PBorder(hex(0xFFF43F5E, 0xFFF59E0B, 0xFF22C55E, 0xFF06B6D4, 0xFF8B5CF6, 0xFFF43F5E), 3400, hex(0x128B5CF6, 0x0C06B6D4), "presidente"),
    "emerald" to PBorder(hex(0xFF065F46, 0xFF6EE7B7, 0xFFCCFBF1, 0xFF10B981, 0xFF065F46), 3000, hex(0x1F10B981, 0x080D9488), "ceo"),
    "pulse" to PBorder(hex(0xFF10B981, 0xFFA78BFA, 0xFF10B981), 0, hex(0x1410B981, 0x14A78BFA), "ceo"),
    "aurora" to PBorder(hex(0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, 0xFFF472B6, 0xFF34D399), 2600, hex(0x1734D399, 0x1022D3EE, 0x17A78BFA), "fundador"),
    "cosmic" to PBorder(hex(0xFF1E1B4B, 0xFFA78BFA, 0xFFFFFFFF, 0xFF22D3EE, 0xFF1E1B4B), 2200, hex(0x55312E81, 0xFF0B0B1A), "fundador"),
)
private val RANK = mapOf("" to 0, "moderador" to 2, "adm" to 3, "diretor" to 4, "presidente" to 5, "ceo" to 6, "fundador" to 7)
fun rankOf(uid: String?): Int { val c = Concord.I; if (uid != null && c.admins.contains(uid)) return 9; return c.titlesOf(uid).maxOfOrNull { RANK[it] ?: 0 } ?: 0 }

/** fundo do cartão na lista: placa animada + borda premium (se o título libera) */
@Composable
fun Modifier.memberCard(p: Profile?, shape: Shape = RoundedCornerShape(12.dp)): Modifier {
    val plate = PLATES[p?.plate]
    val pb = PBORDERS[p?.premBorder]?.takeIf { rankOf(p?.id) >= (RANK[it.title] ?: 0) }
    val ang = spin(pb?.ms ?: 0)
    var m = this.clip(shape)
    if (plate != null) m = m.background(Brush.linearGradient(plate.map { it.copy(alpha = .55f) }))
    if (pb != null) {
        m = m.background(Brush.horizontalGradient(pb.fill))
        m = m.drawWithContent {
            drawContent()
            val stroke = 1.6.dp.toPx()
            val brush = if (pb.ms > 0) Brush.sweepGradient(pb.colors, center = Offset(size.width / 2, size.height / 2)) else Brush.linearGradient(pb.colors)
            if (pb.ms > 0) {
                // gira as cores: desenha a borda com o gradiente girado em volta do centro
                val r = 12.dp.toPx()
                drawRoundRect(Brush.sweepGradient(pb.colors.rotateBy(ang), Offset(size.width / 2, size.height / 2)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
            } else drawRoundRect(brush, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
        }
    }
    return m
}
/** "gira" um gradiente de varredura trocando as paradas de cor (o sweepGradient não tem rotação própria) */
private fun List<Color>.rotateBy(deg: Float): List<Color> {
    if (size < 2) return this
    val base = dropLast(1); val n = base.size; val f = (deg / 360f) * n
    val i = f.toInt() % n; val frac = f - f.toInt()
    return (0..n).map { k -> androidx.compose.ui.graphics.lerp(base[(k + i) % n], base[(k + i + 1) % n], frac) }
}

/* ---------- banner animado e fundo do cartão de perfil ---------- */
val BANNERS = mapOf(
    "aurora" to hex(0xFF0E7490, 0xFF6D28D9, 0xFF059669), "sunset" to hex(0xFFF97316, 0xFFDB2777, 0xFF7C3AED), "ocean" to hex(0xFF0C4A6E, 0xFF0891B2, 0xFF1E3A8A),
    "nebula" to hex(0xFF1E1B4B, 0xFFEC4899, 0xFF22D3EE), "lava" to hex(0xFF450A0A, 0xFFDC2626, 0xFFF97316), "forest" to hex(0xFF052E16, 0xFF15803D, 0xFF3F6212),
    "cyber" to hex(0xFF701A75, 0xFF0E7490, 0xFFA21CAF), "waves" to hex(0xFF082F49, 0xFF0284C7, 0xFF0EA5E9), "stripes" to hex(0xFF18181B, 0xFF27272A),
    "dots" to hex(0xFF1E1B4B, 0xFF3B0764), "grid" to hex(0xFF2E1065, 0xFFE879F9), "candy" to hex(0xFFF9A8D4, 0xFFA5B4FC, 0xFF99F6E4),
    "midnight" to hex(0xFF020617, 0xFF1E1B4B), "gold" to hex(0xFF78350F, 0xFFF59E0B, 0xFFFEF3C7, 0xFFB45309), "ice" to hex(0xFFE0F2FE, 0xFF38BDF8, 0xFF0C4A6E),
    "toxic" to hex(0xFF052E16, 0xFF84CC16, 0xFF14532D), "sakura" to hex(0xFF831843, 0xFFF9A8D4, 0xFF9D174D), "mist" to hex(0xFF27272A, 0xFF71717A, 0xFF18181B),
    "embers" to hex(0xFF1C0A00, 0xFFF97316, 0xFF450A0A), "ripples" to hex(0xFF0C4A6E, 0xFF082F49), "sunrays" to hex(0xFFF59E0B, 0xFF7C2D12),
    "storm" to hex(0xFF334155, 0xFF0F172A), "galaxyspin" to hex(0xFF1E1B4B, 0xFF7C3AED, 0xFFDB2777), "petalfall" to hex(0xFF500724, 0xFFF9A8D4),
    "violetsmoke" to hex(0xFF2E1065, 0xFF9333EA, 0xFFC026D3), "prism" to hex(0xFFF43F5E, 0xFFF59E0B, 0xFF84CC16, 0xFF06B6D4, 0xFF8B5CF6), "northern" to hex(0xFF020617, 0xFF34D399, 0xFFA78BFA),
)
@Composable
fun BannerBox(p: Profile?, modifier: Modifier) {
    val anim = BANNERS[p?.bannerAnim]
    val flow = if (anim != null) { val t = rememberInfiniteTransition(label = "bn"); t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse), label = "b").value } else 0f
    Box(modifier.background(parseColor(p?.bannerColor ?: p?.themePrimary ?: p?.color, C.card2))) {
        if (!p?.banner.isNullOrEmpty()) AsyncImage(p!!.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (anim != null) Box(Modifier.fillMaxSize().background(Brush.linearGradient(anim, start = Offset(-600f * flow, 0f), end = Offset(900f - 600f * flow, 400f), tileMode = TileMode.Mirror)))
    }
}
fun cardBrush(p: Profile?): Brush? {
    val a = p?.themePrimary?.let { parseColor(it) } ?: return null
    val b = p.themeAccent?.let { parseColor(it) } ?: C.panel
    return Brush.verticalGradient(listOf(a, b))
}

/* ====================================================================
   Servidor: tipo (anel no ícone), tema premium, cabeçalho e fundo do chat
   ==================================================================== */
private val TIER_RING = mapOf(
    "gold" to Pair(hex(0xFF78350F, 0xFFFDE68A, 0xFFB45309, 0xFF78350F), 0), "platinum" to Pair(hex(0xFF94A3B8, 0xFFFFFFFF, 0xFFC7D2FE, 0xFF94A3B8), 0),
    "diamond" to Pair(hex(0xFF065F46, 0xFF6EE7B7, 0xFFCCFBF1, 0xFF10B981, 0xFF065F46), 4000), "aurora" to Pair(hex(0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, 0xFFF472B6, 0xFF34D399), 3000),
)
@Composable
fun TierRing(tier: String?, modifier: Modifier = Modifier) {
    val r = TIER_RING[tier] ?: return
    val ang = spin(r.second)
    Canvas(modifier) { rotate(ang) { drawRoundRect(Brush.sweepGradient(r.first), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .34f), style = Stroke(2.dp.toPx())) } }
}
/** tema premium do servidor (Emerald/Aurora): cor de destaque */
val SRV_THEME = mapOf("obsidian" to 0xFFE4E4E7, "emerald" to 0xFF10B981, "amethyst" to 0xFFA78BFA, "gold" to 0xFFFBBF24, "glacier" to 0xFF7DD3FC,
    "crimson" to 0xFFF43F5E, "aurora" to 0xFF5EEAD4, "midnight" to 0xFF818CF8, "sakura" to 0xFFF9A8D4, "toxic" to 0xFFA3E635)
fun srvAccent(s: Server?): Color? {
    if (s == null) return null
    if (s.premium) SRV_THEME[s.theme]?.let { return Color(it) }
    return if (s.applyAccent) s.accent?.let { parseColor(it) } else null
}
private val SRV_BGS = mapOf("aurora" to hex(0x2210B981, 0x226366F1), "nebula" to hex(0x2AA855F7, 0x1FEC4899), "sunset" to hex(0x14F97316, 0x22EF4444),
    "ocean" to hex(0x1C0EA5E9, 0x1C0369A1), "forest" to hex(0x2216A34A, 0x00000000), "ember" to hex(0x22F97316, 0x18DC2626), "mono" to hex(0x08FFFFFF, 0x00000000), "grid" to hex(0x06FFFFFF, 0x06FFFFFF))
fun chatBrush(s: Server?): Brush? {
    val v = s?.themeBg ?: return null
    val cols = SRV_BGS[v] ?: if (v.startsWith("#")) parseColor(v).let { listOf(it.copy(alpha = .2f), it.copy(alpha = .1f)) } else return null
    return Brush.linearGradient(listOf(cols[0], Color.Transparent, cols.getOrElse(1) { Color.Transparent }))
}

/* ---------- tela da chamada (12 temas) ---------- */
class StageTheme(val bg: Brush, val tile: Color, val tileBorder: Color, val speak: Color, val label: Brush, val labelText: Color = Color.White, val radius: Dp = 16.dp)
private fun sb(vararg c: Long) = Brush.verticalGradient(c.map { Color(it) })
private fun lb(vararg c: Long) = Brush.horizontalGradient(c.map { Color(it) })
val STAGES = mapOf(
    "nebula" to StageTheme(Brush.radialGradient(hex(0xFF2A1240, 0xFF07040D), radius = 1400f), Color(0xCC120B1C), Color(0x33A855F7), Color(0xFFE879F9), lb(0xCC2A1240, 0xCC2A1240)),
    "aurora" to StageTheme(Brush.linearGradient(hex(0xFF03080A, 0xFF062A22, 0xFF0B1E33, 0xFF1B1035, 0xFF03080A)), Color(0x99061212), Color(0x3334D399), Color(0xFF34D399), lb(0xCC04201A, 0xCC04201A)),
    "emerald" to StageTheme(Brush.linearGradient(hex(0xFF022C22, 0xFF064E3B, 0xFF022C22, 0xFF0F766E)), Color(0xCC06281F), Color(0x446EE7B7), Color(0xFF6EE7B7), lb(0xFF065F46, 0xFF0D9488)),
    "cyber" to StageTheme(sb(0xFF0B0216, 0xFF1A0530, 0xFF2B0A3D, 0xFF0B0216), Color(0xCC0E0420), Color(0x55FF2BD6), Color(0xFF22D3EE), lb(0xFFFF2BD6, 0xFFFF2BD6), Color.Black, 6.dp),
    "ocean" to StageTheme(sb(0xFF0A2A44, 0xFF03121F, 0xFF020A14), Color(0xCC04192B), Color(0x3338BDF8), Color(0xFF38BDF8), lb(0xCC0C4A6E, 0xCC0C4A6E)),
    "sunset" to StageTheme(sb(0xFF1A0612, 0xFF3B0D1F, 0xFF7C2D12, 0xFFF59E0B), Color(0xCC1D0812), Color(0x44FB923C), Color(0xFFFB923C), lb(0xFFBE185D, 0xFFEA580C)),
    "royal" to StageTheme(Brush.radialGradient(hex(0xFF3B0764, 0xFF12021F), radius = 1200f), Color(0xCC1A0630), Color(0x66FBBF24), Color(0xFFFDE68A), lb(0xFF78350F, 0xFFB45309), Color(0xFFFFF7D6)),
    "carbon" to StageTheme(sb(0xFF1A1A1A, 0xFF131313, 0xFF1D1D1D), Color(0xDD0B0B0B), Color(0x55EF4444), Color(0xFFEF4444), lb(0xFFEF4444, 0xFFEF4444), Color.White, 10.dp),
    "sakura" to StageTheme(sb(0xFF2A0E1E, 0xFF14070E, 0xFF1F0B17), Color(0xCC240C1B), Color(0x44F9A8D4), Color(0xFFF9A8D4), lb(0xCC831843, 0xCC831843)),
    "void" to StageTheme(Brush.radialGradient(hex(0xFF0D0D1F, 0xFF000000), radius = 1200f), Color(0xCC05050A), Color(0x1FFFFFFF), Color(0xFFC7D2FE), lb(0x14FFFFFF, 0x14FFFFFF)),
    "arcade" to StageTheme(sb(0xFF05010A, 0xFF0A0414), Color(0xFF0A0414), Color(0xFFFACC15), Color(0xFF22C55E), lb(0xFFFACC15, 0xFFFACC15), Color.Black, 0.dp),
)
fun stageOf(s: Server?): StageTheme? = if (s?.premium == true) STAGES[s.stage] else null

/* ---------- barra da chamada (11 estilos) ---------- */
class BarStyle(val bar: Brush?, val barBorder: List<Color>?, val btn: Color, val btnBorder: Color, val tint: Color, val shape: Shape = CircleShape, val spinMs: Int = 0, val glow: Color? = null)
val BARS = mapOf(
    "glass" to BarStyle(Brush.verticalGradient(hex(0x1FFFFFFF, 0x0AFFFFFF)), hex(0x30FFFFFF, 0x30FFFFFF), Color(0x14FFFFFF), Color(0x24FFFFFF), C.tx),
    "neon" to BarStyle(Brush.verticalGradient(hex(0xFF06030C, 0xFF06030C)), hex(0xFF22D3EE, 0xFFE879F9, 0xFF22D3EE), Color.Transparent, Color(0x88E879F9), Color(0xFFF5D0FE), glow = Color(0x5522D3EE)),
    "dock" to BarStyle(Brush.verticalGradient(hex(0xCC18181B, 0xCC18181B)), hex(0xFF2A2A2F, 0xFF2A2A2F), C.card2, C.line2, C.tx, RoundedCornerShape(14.dp)),
    "gold" to BarStyle(Brush.verticalGradient(hex(0xFF0B0905, 0xFF0B0905)), hex(0xFF78350F, 0xFFFDE68A, 0xFFB45309, 0xFFFFF7D6, 0xFF78350F), Color(0xFF1A1307), Color(0x66B45309), Color(0xFFFDE68A)),
    "emerald" to BarStyle(Brush.verticalGradient(hex(0xFF03140F, 0xFF03140F)), hex(0xFF065F46, 0xFF6EE7B7, 0xFFCCFBF1, 0xFF10B981, 0xFF065F46), Color(0xFF053B2D), Color(0x446EE7B7), Color(0xFFD1FAE5), spinMs = 4000, glow = Color(0x2610B981)),
    "aurora" to BarStyle(Brush.verticalGradient(hex(0xFF07070C, 0xFF07070C)), hex(0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, 0xFFF472B6, 0xFF34D399), Color(0x0AFFFFFF), Color(0x1AFFFFFF), C.tx, spinMs = 3000, glow = Color(0x2EA78BFA)),
    "minimal" to BarStyle(null, null, Color.Transparent, Color.Transparent, C.tx),
    "split" to BarStyle(null, null, C.card, C.line, C.tx, RoundedCornerShape(18.dp)),
    "hud" to BarStyle(Brush.verticalGradient(hex(0xFF0B0B0B, 0xFF0B0B0B)), hex(0xAAF97316, 0xAAF97316), Color(0xFF1A1A1A), Color(0x55F97316), Color(0xFFFED7AA), androidx.compose.foundation.shape.CutCornerShape(8.dp)),
    "retro" to BarStyle(Brush.verticalGradient(hex(0xFF1E1B4B, 0xFF1E1B4B)), hex(0xFFFFFFFF, 0xFFFFFFFF), Color(0xFF312E81), Color.White, Color.White, RoundedCornerShape(0.dp)),
)
fun barOf(s: Server?): BarStyle? = if (s?.premium == true) BARS[s.bar] else null

/** brilho que passa no banner do servidor (efeitos do cabeçalho) */
@Composable
fun HeadFx(fx: String?, modifier: Modifier) {
    val cols = when (fx) {
        "shine" -> hex(0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF)
        "aurora" -> hex(0x0034D399, 0x3334D399, 0x2B22D3EE, 0x33A78BFA, 0x00A78BFA)
        "emerald" -> hex(0x0010B981, 0x3310B981, 0x0010B981)
        "glitch" -> hex(0x0022D3EE, 0x5522D3EE, 0x00F472B6, 0x55F472B6, 0x00F472B6)
        "particles" -> hex(0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF)
        else -> return
    }
    val t = rememberInfiniteTransition(label = "hf").animateFloat(-1f, 2f, infiniteRepeatable(tween(if (fx == "glitch") 1600 else 4500, easing = LinearEasing)), label = "x").value
    Box(modifier.drawWithContent { val x = size.width * t; drawRect(Brush.linearGradient(cols, start = Offset(x - size.width * .5f, 0f), end = Offset(x + size.width * .5f, size.height))) })
}


/* ---------- moldura do ícone do servidor (cor + estilo; as animadas pedem servidor especial) ---------- */
private val TIER_N = mapOf("gold" to 1, "platinum" to 2, "diamond" to 3, "aurora" to 4)
private val RING_NEED = mapOf("gold" to "gold", "fire" to "gold", "platinum" to "platinum", "prism" to "platinum", "ice" to "platinum",
    "emerald" to "diamond", "neon" to "diamond", "rainbow" to "diamond", "aurora" to "aurora", "cosmic" to "aurora")
private val RING_COLS = mapOf(
    "gold" to Pair(hex(0xFF78350F, 0xFFFDE68A, 0xFFB45309, 0xFFFFF7D6, 0xFF78350F), 4000), "fire" to Pair(hex(0xFFFACC15, 0xFFF97316, 0xFFEF4444, 0xFFF97316, 0xFFFACC15), 1600),
    "platinum" to Pair(hex(0xFF94A3B8, 0xFFFFFFFF, 0xFFC7D2FE, 0xFFE2E8F0, 0xFF94A3B8), 4000), "prism" to Pair(hex(0xFFF43F5E, 0xFFF59E0B, 0xFF22C55E, 0xFF06B6D4, 0xFF8B5CF6, 0xFFF43F5E), 3000),
    "ice" to Pair(hex(0xFFFFFFFF, 0xFFBAE6FD, 0xFF38BDF8, 0xFFBAE6FD, 0xFFFFFFFF), 5000), "emerald" to Pair(hex(0xFF065F46, 0xFF6EE7B7, 0xFFCCFBF1, 0xFF10B981, 0xFF065F46), 3000),
    "neon" to Pair(hex(0xFF22D3EE, 0xFFE879F9, 0xFF22D3EE), 0), "rainbow" to Pair(hex(0xFFF43F5E, 0xFFF59E0B, 0xFF22C55E, 0xFF06B6D4, 0xFF8B5CF6, 0xFFF43F5E), 2000),
    "aurora" to Pair(hex(0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, 0xFFF472B6, 0xFF34D399), 2600), "cosmic" to Pair(hex(0xFF1E1B4B, 0xFFA78BFA, 0xFFFFFFFF, 0xFF22D3EE, 0xFF1E1B4B), 2200),
)
fun ringOk(s: Server): Boolean { val need = RING_NEED[s.ring] ?: return s.ring != null; return (TIER_N[s.tier] ?: 0) >= (TIER_N[need] ?: 0) }

@Composable
fun ServerRing(s: Server, modifier: Modifier = Modifier) {
    if (s.ring == null || !ringOk(s)) { TierRing(s.tier, modifier); return }
    val c1 = parseColor(s.ringColor, Color.White); val c2 = parseColor(s.ring2, Color(0xFFA855F7))
    val anim = RING_COLS[s.ring]
    val ang = spin(anim?.second ?: 0)
    val pulse = if (s.ring == "pulse") rememberInfiniteTransition(label = "rp").animateFloat(0f, 1f, infiniteRepeatable(tween(1800)), label = "p").value else -1f
    Canvas(modifier) {
        val sw = (if (s.ring == "double") 4.dp else 2.5.dp).toPx()
        val cr = androidx.compose.ui.geometry.CornerRadius(size.width * .34f)
        val brush: Brush = when {
            anim != null -> Brush.sweepGradient(anim.first.rotateCols(ang))
            s.ring == "duo" -> Brush.linearGradient(listOf(c1, c2))
            else -> androidx.compose.ui.graphics.SolidColor(c1)
        }
        if (s.ring == "glow" || s.ring == "neon" || s.ring == "emerald" || s.ring == "aurora") drawRoundRect((if (anim != null) anim.first[1] else c1).copy(alpha = .35f), cornerRadius = cr, style = Stroke(sw * 3.2f))
        if (pulse >= 0f) drawRoundRect(c1.copy(alpha = (1f - pulse) * .6f), cornerRadius = cr, style = Stroke(sw + 10.dp.toPx() * pulse))
        val effect = if (s.ring == "dashed") androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 7f)) else null
        drawRoundRect(brush, cornerRadius = cr, style = Stroke(sw, pathEffect = effect))
        if (s.ring == "double") drawRoundRect(C.bg, topLeft = Offset(sw / 3, sw / 3), size = androidx.compose.ui.geometry.Size(size.width - sw / 1.5f, size.height - sw / 1.5f), cornerRadius = cr, style = Stroke(sw / 3))
    }
}
private fun List<Color>.rotateCols(deg: Float): List<Color> {
    val base = dropLast(1); val n = base.size; if (n < 2) return this
    val f = deg / 360f * n; val i = f.toInt() % n; val fr = f - f.toInt()
    return (0..n).map { k -> androidx.compose.ui.graphics.lerp(base[(k + i) % n], base[(k + i + 1) % n], fr) }
}

/** imagem com enquadramento (zoom/posição) e GIF parado ou animado */
@Composable
fun FitImage(url: String, fit: Triple<Float, Float, Float>?, animate: Boolean, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val (z, x, y) = fit ?: Triple(1f, 50f, 50f)
    val req = remember(url, animate) {
        coil.request.ImageRequest.Builder(ctx).data(url).apply { if (!animate) decoderFactory(coil.decode.BitmapFactoryDecoder.Factory()) }.build()
    }
    AsyncImage(req, null, contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(x / 50f - 1f, y / 50f - 1f),
        modifier = modifier.graphicsLayerCompat(z, x / 100f, y / 100f))
}
private fun Modifier.graphicsLayerCompat(z: Float, ox: Float, oy: Float) = if (z <= 1.001f) this else this.clipToBounds().then(Modifier.graphicsLayer(scaleX = z, scaleY = z, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(ox, oy)))
