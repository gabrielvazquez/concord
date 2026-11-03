package com.gabrielvazquez.concord.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val bg = Color(0xFF000000)
    val panel = Color(0xFF0A0A0B)
    val card = Color(0xFF131315)
    val card2 = Color(0xFF1A1A1D)
    val line = Color(0xFF202024)
    val line2 = Color(0xFF2A2A2F)
    val tx = Color(0xFFFAFAFA)
    val tx2 = Color(0xFFA1A1AA)
    val tx3 = Color(0xFF71717A)
    val green = Color(0xFF3DDC84)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFFF4D5E)
    val gray = Color(0xFF52525B)
    val spotify = Color(0xFF1ED760)
}

@Composable
fun ConcordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Color.Black, secondary = C.tx2, onSecondary = Color.Black,
            background = C.bg, onBackground = C.tx, surface = C.panel, onSurface = C.tx, surfaceVariant = C.card, onSurfaceVariant = C.tx2,
            surfaceContainer = C.panel, surfaceContainerHigh = C.card, surfaceContainerHighest = C.card2, surfaceContainerLow = C.panel,
            outline = C.line2, outlineVariant = C.line, error = C.red, primaryContainer = C.card2, onPrimaryContainer = C.tx,
        ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides C.tx) { content() }
    }
}

fun statusColor(st: String): Color = when (st) { "online" -> C.green; "idle" -> C.amber; "dnd" -> C.red; else -> C.gray }

fun parseColor(s: String?, fb: Color = Color(0xFF6366F1)): Color = try {
    if (s == null || !s.startsWith("#")) fb else {
        val h = s.removePrefix("#")
        val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h.take(6)
        Color(0xFF000000 or full.toLong(16))
    }
} catch (_: Exception) { fb }
