package com.yonidev.ydeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yonidev.ydeck.link.Memory
import kotlin.math.cos
import kotlin.math.sin

/** YDeck's mark, drawn (store/brand/logo/make.cjs): a ring open at the top right, the amber dot in the opening. */
@Composable
fun Mark(size: Dp, ring: Color = LocalDeck.current.fg, dot: Color = LocalDeck.current.open) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension * 0.36f
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawArc(ring, -10f, 300f, useCenter = false, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            style = Stroke(r * 11f / 36f, cap = StrokeCap.Round))
        val a = Math.toRadians(-40.0)
        drawCircle(dot, r * 7.5f / 36f, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
    }
}

/** A project's state as one dot: amber when a next step waits, green when nothing is left open. */
@Composable
fun StateDot(memory: Memory?, size: Dp = 8.dp) {
    val c = LocalDeck.current
    val color = when {
        memory == null -> return
        memory.open -> c.open
        else -> c.closed
    }
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** "עכשיו", "לפני 5 דקות", "אתמול"… */
fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
    val min = ((now - ms) / 60_000).coerceAtLeast(0)
    val h = min / 60
    val d = h / 24
    return when {
        min < 2 -> "עכשיו"
        min < 60 -> "לפני $min דקות"
        h < 2 -> "לפני שעה"
        h < 24 -> "לפני $h שעות"
        d < 2 -> "אתמול"
        d < 7 -> "לפני $d ימים"
        d < 14 -> "לפני שבוע"
        else -> "לפני ${d / 7} שבועות"
    }
}

/** Hebrew text that opens with an English word ("YDeck שם את…") still reads right to left; English stays left to right. */
fun dirOf(text: String): androidx.compose.ui.text.style.TextDirection =
    if (text.any { it in '\u0590'..'\u05FF' }) androidx.compose.ui.text.style.TextDirection.Rtl else androidx.compose.ui.text.style.TextDirection.Ltr
