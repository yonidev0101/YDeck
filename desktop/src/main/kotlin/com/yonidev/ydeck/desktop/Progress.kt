package com.yonidev.ydeck.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * The mark is the progress, as on the phone (app/.../ui/Progress.kt): YDeck's ring, open at the top right with the amber
 * dot in the opening, fills with what was done out of done + left. When nothing is left it reaches the dot, and the dot
 * turns green. On first show it draws itself from nothing to where the project stands, like the mark loading.
 */

/** Done and done + left, as the lists say them; null when the project keeps no lists. */
fun Pulse.progress(): Pair<Int, Int>? {
    val total = done.size + left.size
    return if (total == 0) null else done.size to total
}

/** The expo-out the brand moves with: things land, they don't float. */
private val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
private fun animatedShare(done: Int, total: Int, animate: Boolean): Float {
    val target = if (total <= 0) 0f else done.toFloat() / total
    val a = remember { Animatable(if (animate) 0f else target) }
    LaunchedEffect(target) { if (animate) a.animateTo(target, tween(1100, easing = ExpoOut)) else a.snapTo(target) }
    return a.value
}

/** The ring, the share drawn in [fill] from the ring's start, and the dot. Geometry as YDeck's mark: a 300° arc from -10°. */
private fun DrawScope.ring(share: Float, r: Float, stroke: Float, dotR: Float, track: Color, fill: Color, dot: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val box = Size(r * 2, r * 2)
    val tl = Offset(c.x - r, c.y - r)
    drawArc(track, -10f, 300f, useCenter = false, topLeft = tl, size = box, style = Stroke(stroke, cap = StrokeCap.Round))
    if (share > 0.004f) drawArc(fill, -10f, 300f * share.coerceAtMost(1f), useCenter = false, topLeft = tl, size = box, style = Stroke(stroke, cap = StrokeCap.Round))
    val a = Math.toRadians(-40.0)
    drawCircle(dot, dotR, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
}

/** The project page's mark: the ring at [size], [content] (the project's logo, or a number) in its middle. */
@Composable
fun ProgressMark(done: Int, total: Int, size: Dp, animate: Boolean = true, content: @Composable () -> Unit = {}) {
    val c = LocalDeck.current
    val share = animatedShare(done, total, animate)
    val finished = total > 0 && done >= total && share >= 0.995f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension * 0.36f
            ring(share, r, r * 11f / 36f, r * 7.5f / 36f, c.line, c.fg, if (finished) CLEAN_DOT else CHANGED_DOT)
        }
        content()
    }
}

/** Now's and the sidebar's: a thin ring around the project's logo. */
@Composable
fun RingedLogo(p: Project, done: Int, total: Int, size: Dp = 44.dp) {
    val c = LocalDeck.current
    val share = animatedShare(done, total, true)
    val finished = done >= total && share >= 0.995f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 2.2.dp.toPx()
            val dotR = 3.dp.toPx()
            ring(share, this.size.minDimension / 2 - dotR, stroke, dotR, c.line, c.fg, if (finished) CLEAN_DOT else CHANGED_DOT)
        }
        Logo(p, size * 0.66f)
    }
}
