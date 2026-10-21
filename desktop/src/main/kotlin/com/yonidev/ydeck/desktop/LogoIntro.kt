package com.yonidev.ydeck.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The logo sting, "thinking" (store/brand/logo), played once when the app opens: on the night ground an indigo arc
 * spins like an agent at work, slows, opens and turns white as it settles into the mark, and the amber dot lands in
 * its opening; "YDeck" rises under it with the line. A click skips.
 */
@Composable
fun LogoIntro(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < END) t = (withFrameNanos { it } - start) / 1e9f
        done()
    }
    val measurer = rememberTextMeasurer()
    Box(
        Modifier.fillMaxSize().background(NIGHT)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRing(t)
            drawWords(t, measurer)
        }
    }
}

/** The line has settled (2.7 + 0.5) and holds a moment before the window fades in. */
private const val END = 3.4f

private val NIGHT = Color(0xFF0B0B0D)
private val PAPER = Color(0xFFF4F4F5)
private val INDIGO = Color(0xFF3D35E0)
private val AMBER = Color(0xFFF5A524)
private val MUTED = Color(0xFF8B8B93)

private fun seg(t: Float, a: Float, d: Float) = ((t - a) / d).coerceIn(0f, 1f)
private fun p3out(p: Float) = 1f - (1f - p).pow(3)
private fun inOut(p: Float) = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f
private fun backOut(p: Float): Float { val s = 1.7f; val q = p - 1f; return q * q * ((s + 1f) * q + s) + 1f }

/** The mark's geometry (store/brand/logo/make.cjs): radius 36 of 100, stroke 11, the opening from -70° to -10°. */
private class Ring(size: Size) {
    val r = min(size.width, size.height) * 0.085f
    val stroke = r * 11f / 36f
    val cx = size.width / 2f
    val cy = size.height * 0.42f
}

private fun DrawScope.drawRing(t: Float) {
    val g = Ring(size)
    // Thinking: the arc spins three turns, fast, and slows to a stop exactly where the mark's ring starts.
    val spin = p3out(seg(t, 0.15f, 1.75f))
    val from = -10f - 1080f * (1f - spin)
    val sweep = 70f + 230f * inOut(seg(t, 0.95f, 0.85f))
    val color = lerp(INDIGO, PAPER, inOut(seg(t, 1.25f, 0.6f)))
    drawArc(color, from, sweep, useCenter = false, topLeft = Offset(g.cx - g.r, g.cy - g.r), size = Size(g.r * 2, g.r * 2),
        alpha = seg(t, 0f, 0.18f), style = Stroke(g.stroke, cap = StrokeCap.Round))
    // It stops, and the amber dot lands in the opening.
    val land = seg(t, 1.85f, 0.34f)
    if (land > 0f) {
        val a = Math.toRadians(-40.0)
        drawCircle(AMBER, (g.r * 7.5f / 36f * backOut(land)).coerceAtLeast(0f), Offset(g.cx + g.r * cos(a).toFloat(), g.cy + g.r * sin(a).toFloat()))
    }
}

/** "YDeck" rises under the ring, then the line fades in under it. */
private fun DrawScope.drawWords(t: Float, measurer: TextMeasurer) {
    val g = Ring(size)
    val nk = p3out(seg(t, 2.35f, 0.55f))
    if (nk <= 0f) return
    val nameSize = g.r * 0.95f
    val name = measurer.measure("YDeck", TextStyle(color = PAPER, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = nameSize.toSp(), letterSpacing = (-0.02).em))
    val top = g.cy + g.r + g.stroke + nameSize * 0.9f
    clipRect(0f, top, size.width, top + name.size.height) {
        translate(g.cx - name.size.width / 2f, top + (1f - nk) * name.size.height) { drawText(name) }
    }
    val lk = p3out(seg(t, 2.7f, 0.5f))
    if (lk <= 0f) return
    val line = measurer.measure(tr("Every project. One screen.", "כל הפרויקטים. מסך אחד."),
        TextStyle(color = MUTED.copy(alpha = lk), fontFamily = PlexSans, fontSize = (nameSize * 0.42f).toSp()))
    translate(g.cx - line.size.width / 2f, top + name.size.height + nameSize * 0.35f + (1f - lk) * 6f) { drawText(line) }
}
