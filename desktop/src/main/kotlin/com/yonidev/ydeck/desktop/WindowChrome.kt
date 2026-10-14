package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Window

/*
 * The app window's own frame: no Windows title bar, but a strip in the window's own colours (the sidebar's on its
 * side, the page's on the other) that drags the window and maximises it on a double click, YDeck's own minimise,
 * maximise and close, edges to resize from, and Windows 11's rounded corners.
 */

const val TITLE_BAR_HEIGHT = 38

/** A frameless window: rounded corners (Windows 11) and a maximise that stops at the taskbar. */
fun dressFrameless(window: java.awt.Frame) {
    runCatching { window.maximizedBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    runCatching {
        val h = WinDef.HWND(Native.getComponentPointer(window))
        // DWMWA_WINDOW_CORNER_PREFERENCE = 33, DWMWCP_ROUND = 2
        Dwm.lib?.DwmSetWindowAttribute(h, 33, IntByReference(2), 4)
    }
}

private object Dwm {
    interface Lib : com.sun.jna.Library { fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attr: Int, value: IntByReference, size: Int): Int }
    val lib: Lib? by lazy { runCatching { Native.load("dwmapi", Lib::class.java) }.getOrNull() }
}

/**
 * The strip along the top: [sidebar] wide in the sidebar's colour (none: one colour throughout, [fill]), drag to move,
 * double click to maximise, and the three window buttons at its end.
 */
@Composable
fun TitleBar(window: Window, state: WindowState, sidebar: Dp?, fill: Color, sidebarFill: Color, ink: Color, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(TITLE_BAR_HEIGHT.dp)) {
        Row(Modifier.fillMaxSize()) {
            if (sidebar != null) Box(Modifier.width(sidebar).fillMaxHeight().background(sidebarFill))
            Box(Modifier.weight(1f).fillMaxHeight().background(fill))
        }
        Box(Modifier.fillMaxSize().dragsWindow(window, state))
        // The buttons sit where Windows puts them: at the end, whatever the language.
        Row(Modifier.align(Alignment.TopEnd).fillMaxHeight()) {
            CaptionButton(ink, onClick = { state.isMinimized = true }) { c ->
                drawLine(c, Offset(size.width * 0.38f, size.height / 2), Offset(size.width * 0.62f, size.height / 2), strokeWidth = 1.2.dp.toPx())
            }
            CaptionButton(ink, onClick = { state.placement = if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized }) { c ->
                val s = size.height * 0.26f
                val stroke = Stroke(1.2.dp.toPx())
                val x = size.width / 2 - s / 2; val y = size.height / 2 - s / 2
                if (state.placement == WindowPlacement.Maximized) {
                    // restore: a square, and the top and right edges of another behind it
                    val k = s * 0.24f
                    val w = 1.2.dp.toPx()
                    drawRect(c, Offset(x - k / 2, y + k / 2), Size(s, s), style = stroke)
                    drawLine(c, Offset(x + k / 2, y - k / 2), Offset(x + s + k / 2, y - k / 2), strokeWidth = w)
                    drawLine(c, Offset(x + s + k / 2, y - k / 2), Offset(x + s + k / 2, y + s - k / 2), strokeWidth = w)
                } else drawRect(c, Offset(x, y), Size(s, s), style = stroke)
            }
            CaptionButton(ink, close = true, onClick = onClose) { c ->
                val s = size.height * 0.24f
                val cx = size.width / 2; val cy = size.height / 2
                drawLine(c, Offset(cx - s / 2, cy - s / 2), Offset(cx + s / 2, cy + s / 2), strokeWidth = 1.2.dp.toPx())
                drawLine(c, Offset(cx + s / 2, cy - s / 2), Offset(cx - s / 2, cy + s / 2), strokeWidth = 1.2.dp.toPx())
            }
        }
    }
}

@Composable
private fun CaptionButton(ink: Color, close: Boolean = false, onClick: () -> Unit, glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val bg = when {
        !hovered -> Color.Transparent
        close -> Color(0xFFC42B1C)
        else -> ink.copy(alpha = 0.08f)
    }
    Box(Modifier.width(46.dp).fillMaxHeight().background(bg).hoverable(hover).clickable(interactionSource = hover, indication = null, onClick = onClick)) {
        // Window glyphs don't mirror in right-to-left.
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Canvas(Modifier.fillMaxSize()) { glyph(if (hovered && close) Color.White else ink.copy(alpha = 0.82f)) }
        }
    }
}

/** Drags the window with the pointer; a double click maximises or restores it; a drag out of maximised restores it. */
private fun Modifier.dragsWindow(window: Window, state: WindowState): Modifier = pointerInput(Unit) {
    var lastUp = 0L
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent()
            if (down.changes.none { it.pressed }) continue
            val now = System.currentTimeMillis()
            if (now - lastUp < 350) {
                state.placement = if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                lastUp = 0
                while (awaitPointerEvent().changes.any { it.pressed }) { }
                continue
            }
            val start = MouseInfo.getPointerInfo()?.location ?: continue
            var origin = window.location
            var moved = false
            while (true) {
                val e = awaitPointerEvent()
                if (e.changes.none { it.pressed }) break
                val p = MouseInfo.getPointerInfo()?.location ?: continue
                if (!moved && (kotlin.math.abs(p.x - start.x) + kotlin.math.abs(p.y - start.y)) < 4) continue
                if (!moved && state.placement == WindowPlacement.Maximized) {
                    // Out of maximised: the window comes back to its size under the pointer, at the same relative spot.
                    val ratio = (start.x - window.x).toFloat() / window.width.coerceAtLeast(1)
                    state.placement = WindowPlacement.Floating
                    origin = java.awt.Point((p.x - window.width * ratio).toInt(), p.y - (start.y - window.y))
                }
                moved = true
                window.setLocation(origin.x + (p.x - start.x), origin.y + (p.y - start.y))
            }
            lastUp = if (moved) 0 else System.currentTimeMillis()
        }
    }
}

/** Edges and corners to resize a frameless window from (not while it's maximised). */
@Composable
fun BoxScope.ResizeEdges(window: Window, state: WindowState, thickness: Dp = 5.dp) {
    if (state.placement == WindowPlacement.Maximized) return
    val t = thickness
    @Composable fun edge(align: Alignment, w: Dp?, h: Dp?, cursor: Int, dx: Int, dy: Int) {
        Box(
            Modifier.align(align).then(if (w != null) Modifier.width(w) else Modifier.fillMaxWidth()).then(if (h != null) Modifier.height(h) else Modifier.fillMaxHeight())
                .pointerHoverIcon(PointerIcon(Cursor(cursor)))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            if (down.changes.none { it.pressed }) continue
                            val start = MouseInfo.getPointerInfo()?.location ?: continue
                            val b0 = Rectangle(window.bounds)
                            val min = window.minimumSize
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.changes.none { it.pressed }) break
                                val p = MouseInfo.getPointerInfo()?.location ?: continue
                                val ddx = p.x - start.x; val ddy = p.y - start.y
                                var x = b0.x; var y = b0.y; var w = b0.width; var h = b0.height
                                if (dx > 0) w = (b0.width + ddx).coerceAtLeast(min.width)
                                if (dx < 0) { w = (b0.width - ddx).coerceAtLeast(min.width); x = b0.x + b0.width - w }
                                if (dy > 0) h = (b0.height + ddy).coerceAtLeast(min.height)
                                if (dy < 0) { h = (b0.height - ddy).coerceAtLeast(min.height); y = b0.y + b0.height - h }
                                window.setBounds(x, y, w, h)
                            }
                        }
                    }
                },
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        edge(Alignment.CenterStart, t, null, Cursor.W_RESIZE_CURSOR, -1, 0)
        edge(Alignment.CenterEnd, t, null, Cursor.E_RESIZE_CURSOR, 1, 0)
        edge(Alignment.TopCenter, null, t, Cursor.N_RESIZE_CURSOR, 0, -1)
        edge(Alignment.BottomCenter, null, t, Cursor.S_RESIZE_CURSOR, 0, 1)
        edge(Alignment.TopStart, t * 2, t * 2, Cursor.NW_RESIZE_CURSOR, -1, -1)
        edge(Alignment.TopEnd, t * 2, t * 2, Cursor.NE_RESIZE_CURSOR, 1, -1)
        edge(Alignment.BottomStart, t * 2, t * 2, Cursor.SW_RESIZE_CURSOR, -1, 1)
        edge(Alignment.BottomEnd, t * 2, t * 2, Cursor.SE_RESIZE_CURSOR, 1, 1)
    }
}
