package com.gabrielvazquez.concord.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gabrielvazquez.concord.Concord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Símbolo do CONCORD (dois anéis) */
@Composable
fun Logo(size: Dp = 32.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 32f
        val st = Stroke(width = 2f * s)
        drawCircle(color, radius = 7f * s, center = Offset(12.5f * s, 16f * s), style = st)
        drawCircle(color, radius = 7f * s, center = Offset(19.5f * s, 16f * s), style = st)
    }
}

/** QR code desenhado na hora (preto no branco, para qualquer câmera ler) */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val m = androidx.compose.runtime.remember(text) {
        com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 0, com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M))
    }
    Canvas(modifier) {
        val n = m.width; val s = size.width / n
        for (y in 0 until n) for (x in 0 until n) if (m[x, y]) drawRect(Color.Black, Offset(x * s, y * s), androidx.compose.ui.geometry.Size(s + 0.6f, s + 0.6f))
    }
}

/** link que o QR da TV abre: o celular conecta na TV com ele */
fun tvLink(code: String) = "https://gabrielvazquez.github.io/concord/?tv=$code"
fun tvCodeFrom(raw: String?): String? = raw?.let { Regex("""(?:tv=)?(\d{6})""").find(it)?.groupValues?.get(1) }

/** selo de título: metais lisos com ícone próprio; só o CEO é diamante (fundo escuro, borda furta-cor e brilho) */
private class TitleStyle(val label: String, val tier: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val top: Long, val bottom: Long, val ink: Long)
private val TITLE_STYLE = mapOf(
    "ceo" to TitleStyle("CEO", "Diamond", androidx.compose.material.icons.Icons.Default.Diamond, 0xFF120A24, 0xFF0B1A1C, 0xFFF5F3FF),
    "fundador" to TitleStyle("FOUNDER", "Aurora", androidx.compose.material.icons.Icons.Default.WorkspacePremium, 0xFF06060B, 0xFF0A0F14, 0xFFFFFFFF),
    "presidente" to TitleStyle("PRESIDENT", "Platinum", androidx.compose.material.icons.Icons.Default.MilitaryTech, 0xFFF8FAFC, 0xFFCBD5E1, 0xFF1E293B),
    "diretor" to TitleStyle("DIRECTOR", "Gold", androidx.compose.material.icons.Icons.Default.Star, 0xFFFDE68A, 0xFFF2A20C, 0xFF3A2400),
    "adm" to TitleStyle("ADMIN", "Silver", androidx.compose.material.icons.Icons.Default.Shield, 0xFFF4F4F5, 0xFFB4B4BB, 0xFF18181B),
    "moderador" to TitleStyle("MOD", "Bronze", androidx.compose.material.icons.Icons.Default.Gavel, 0xFFF9B47C, 0xFFC0571D, 0xFF2A1203),
)
/** vários títulos: um só = selo completo; mais de um = selos mini (só o ícone); no perfil, todos completos */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TitleBadges(keys: List<String>, big: Boolean = false) {
    if (keys.isEmpty()) return
    if (big) { androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) { keys.forEach { TitleBadge(it, big = true) } }; return }
    if (keys.size == 1) { TitleBadge(keys[0]); return }
    Row(Modifier.padding(start = 6.dp)) { keys.forEachIndexed { i, k -> if (i > 0) Spacer(Modifier.width(3.dp)); TitleBadge(k, mini = true) } }
}

@Composable
fun TitleBadge(key: String?, big: Boolean = false, mini: Boolean = false) {
    val st = TITLE_STYLE[key] ?: return
    val ceo = key == "ceo" || key == "fundador"
    val prism = if (key == "fundador") listOf(Color(0xFF34D399), Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFFF472B6))
        else listOf(Color(0xFFA855F7), Color(0xFF2DD4BF), Color(0xFF7C3AED), Color(0xFF34D399), Color(0xFFC084FC))
    // quanto mais alto o cargo, mais rápido o brilho passa (Founder fica parado)
    val speed = mapOf("fundador" to 1900, "ceo" to 2400, "presidente" to 3400, "diretor" to 5000, "adm" to 8000, "moderador" to 11000)[key]
    val anim = if (speed != null) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "tb")
        t.animateFloat(-1f, 2.2f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(speed), androidx.compose.animation.core.RepeatMode.Restart), label = "s").value
    } else -9f
    val shineA = when (key) { "fundador" -> .5f; "ceo" -> .45f; "presidente" -> .8f; "diretor" -> .7f; "adm" -> .55f; else -> .35f }
    val shape = RoundedCornerShape(if (big) 8.dp else 6.dp)
    Row(Modifier.padding(start = if (big || mini) 0.dp else 6.dp).height(if (big) 26.dp else 18.dp).then(if (mini) Modifier.width(if (ceo) 20.dp else 18.dp) else Modifier).clip(shape)
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(st.top), Color(st.bottom))))
        .then(if (ceo) Modifier.border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(prism), shape) else Modifier)
        .drawWithContent { drawContent(); if (anim in -1f..1.2f) { val x = size.width * anim
            drawRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = shineA), Color.Transparent), start = Offset(x - 30f, 0f), end = Offset(x + 30f, size.height))) } }
        .padding(horizontal = if (mini) 0.dp else if (big) 10.dp else 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        androidx.compose.material3.Icon(st.icon, null, tint = if (key == "fundador") Color(0xFF67E8F9) else if (ceo) Color(0xFFA78BFA) else Color(st.ink), modifier = Modifier.size(if (big) 15.dp else 11.dp))
        if (mini) return@Row
        Spacer(Modifier.width(5.dp))
        Text(st.label, color = Color(st.ink), fontSize = if (big) 11.5.sp else 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = if (big) 2.sp else 1.5.sp)
        if (big) { Spacer(Modifier.width(7.dp)); Text(st.tier, color = if (key == "fundador") Color(0xFFA7F3D0) else if (ceo) Color(0xFF5EEAD4) else Color(st.ink).copy(alpha = .7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun Avatar(uid: String?, size: Dp, status: String? = null, fallbackName: String? = null, fallbackColor: String? = null, speaking: Boolean = false) {
    val c = Concord.I
    val p = uid?.let { c.profiles[it] }
    val name = p?.name ?: fallbackName ?: "?"
    AvatarRaw(p?.avatar, name, p?.color ?: fallbackColor, size, status, speaking)
}

@Composable
fun AvatarRaw(url: String?, name: String, color: String?, size: Dp, status: String? = null, speaking: Boolean = false) {
    Box(Modifier.size(size)) {
        val ring = if (speaking) Modifier.border(2.dp, C.green, CircleShape) else Modifier
        Box(Modifier.size(size).then(ring).clip(CircleShape).background(parseColor(color)), contentAlignment = Alignment.Center) {
            if (!url.isNullOrEmpty()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
            else Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
        if (status != null) {
            val d = (size.value * 0.32f).coerceAtLeast(9f).dp
            Box(Modifier.size(d).align(Alignment.BottomEnd).offset(1.dp, 1.dp).clip(CircleShape).background(C.panel), contentAlignment = Alignment.Center) {
                Box(Modifier.size(d * 0.66f).clip(CircleShape).background(statusColor(status)))
            }
        }
    }
}

/** Formatação de texto igual à do site: **negrito**, *itálico*, __sublinhado__, ~~riscado~~, `código`, ||spoiler||, @menção e links */
fun rich(text: String, myName: String?): AnnotatedString = buildAnnotatedString {
    val rx = Regex("(\\*\\*[^*]+\\*\\*)|(__[^_]+__)|(~~[^~]+~~)|(`[^`]+`)|(\\|\\|[^|]+\\|\\|)|(\\*[^*\\s][^*]*\\*)|(https?://\\S+)|(@[\\wÀ-ÿ.-]{2,32})")
    var i = 0
    for (m in rx.findAll(text)) {
        if (m.range.first > i) append(text.substring(i, m.range.first))
        val t = m.value
        when {
            t.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(t.drop(2).dropLast(2)) }
            t.startsWith("__") -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(t.drop(2).dropLast(2)) }
            t.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(t.drop(2).dropLast(2)) }
            t.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = C.card2, fontSize = 13.sp)) { append(" " + t.drop(1).dropLast(1) + " ") }
            t.startsWith("||") -> withStyle(SpanStyle(background = C.line2, color = C.line2)) { append(t.drop(2).dropLast(2)) }
            t.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.drop(1).dropLast(1)) }
            t.startsWith("http") -> { pushStringAnnotation("url", t); withStyle(SpanStyle(color = Color(0xFF8AB4F8), textDecoration = TextDecoration.Underline)) { append(t) }; pop() }
            t.startsWith("@") -> {
                val me = myName != null && t.drop(1).equals(myName, true)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White, background = if (me) Color(0x55FBBF24) else Color(0x33FFFFFF))) { append(t) }
            }
            else -> append(t)
        }
        i = m.range.last + 1
    }
    if (i < text.length) append(text.substring(i))
}

fun isImageUrl(s: String): Boolean {
    val t = s.trim()
    return !t.contains(' ') && t.startsWith("https://") && (Regex("\\.(png|jpe?g|gif|webp)(\\?.*)?$", RegexOption.IGNORE_CASE).containsMatchIn(t) || t.contains("/storage/v1/object/public/concord/") && t.contains("/img-"))
}

private val fmtTime = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
private val fmtDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

fun timeLabel(t: Long): String {
    val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = t }
    val same = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yest = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    return when { same -> "Hoje às " + fmtTime.format(Date(t)); yest -> "Ontem às " + fmtTime.format(Date(t)); else -> fmtDate.format(Date(t)) + " " + fmtTime.format(Date(t)) }
}
fun hhmm(t: Long): String = fmtTime.format(Date(t))
fun dayKey(t: Long): String = fmtDate.format(Date(t))

fun sinceLabel(t: Long): String {
    val m = ((System.currentTimeMillis() - t) / 60000).coerceAtLeast(0)
    return if (m < 1) "agora" else if (m < 60) "há $m min" else "há ${m / 60} h${if (m % 60 > 0) " ${m % 60} min" else ""}"
}
