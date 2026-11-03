package com.gabrielvazquez.concord.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
