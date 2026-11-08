package com.gabrielvazquez.concord.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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

/** selo de título com a pedra de cada nível (CEO = diamante, com brilho passando) */
private val TITLE_STYLE = mapOf(
    "ceo" to Triple("CEO", "Diamante", listOf(0xFFF0FDFF, 0xFFA5F3FC, 0xFFC4B5FD, 0xFFF5D0FE, 0xFFBAE6FD)),
    "presidente" to Triple("PRESIDENTE", "Platina", listOf(0xFFF8FAFC, 0xFFCBD5E1, 0xFFE0E7FF, 0xFF94A3B8)),
    "diretor" to Triple("DIRETOR", "Ouro", listOf(0xFFFEF9C3, 0xFFFCD34D, 0xFFF59E0B, 0xFFFDE68A)),
    "adm" to Triple("ADM", "Prata", listOf(0xFFFAFAFA, 0xFFD4D4D8, 0xFFA1A1AA, 0xFFE4E4E7)),
    "moderador" to Triple("MOD", "Bronze", listOf(0xFFFED7AA, 0xFFFB923C, 0xFFC2410C)),
    "fundador" to Triple("FUNDADOR", "Esmeralda", listOf(0xFFD1FAE5, 0xFF6EE7B7, 0xFF10B981, 0xFFA7F3D0)),
)
@Composable
fun TitleBadge(key: String?, big: Boolean = false) {
    val st = TITLE_STYLE[key] ?: return
    val shine = if (key == "ceo" || key == "presidente") {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "tb")
        t.animateFloat(-1f, 2f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(if (key == "ceo") 2600 else 4000), androidx.compose.animation.core.RepeatMode.Restart), label = "s").value
    } else -1f
    val h = if (big) 26.dp else 18.dp
    Row(Modifier.padding(start = if (big) 0.dp else 6.dp).height(h).clip(RoundedCornerShape(if (big) 8.dp else 6.dp))
        .background(androidx.compose.ui.graphics.Brush.linearGradient(st.third.map { Color(it) }))
        .drawWithContent { drawContent(); if (shine > -1f) { val x = size.width * shine
            drawRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .7f), Color.Transparent), start = Offset(x - 30f, 0f), end = Offset(x + 30f, size.height))) } }
        .padding(horizontal = if (big) 10.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("◆", color = Color.White, fontSize = if (big) 13.sp else 10.sp)
        Spacer(Modifier.width(4.dp))
        Text(st.first, color = Color(0xFF0B0B0E), fontSize = if (big) 12.sp else 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp)
        if (big) { Spacer(Modifier.width(6.dp)); Text(st.second, color = Color(0xB30B0B0E), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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
