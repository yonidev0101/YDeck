package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.Point
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.imageio.ImageIO

/*
 * YDeck by the clock. Compose's Tray draws its menu with AWT's PopupMenu, which looks like Windows 98; here the icon is
 * AWT's, and its menu is a window of YDeck's own, drawn like the app.
 */
object AppTray {
    /** The icon: a click opens YDeck; a right click asks for the menu, where the pointer is. Null without a tray. */
    fun icon(onOpen: () -> Unit, onMenu: (Point) -> Unit): TrayIcon? = runCatching {
        if (!SystemTray.isSupported()) return null
        val image = AppTray::class.java.classLoader.getResourceAsStream("icons/app.png")!!.use { ImageIO.read(it) }
        TrayIcon(image, "YDeck").apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) java.awt.MouseInfo.getPointerInfo()?.location?.let(onMenu)
                    else if (e.button == MouseEvent.BUTTON1) onOpen()
                }
            })
            SystemTray.getSystemTray().add(this)
        }
    }.getOrNull()

    fun remove(icon: TrayIcon?) { if (icon != null) runCatching { SystemTray.getSystemTray().remove(icon) } }
}

class TrayItem(val glyph: Glyph, val text: String, val hint: String? = null, val onClick: () -> Unit)

private const val ITEM = 40f
private const val MENU_WIDTH = 272f
private const val SHADOW = 12f

/** The tray's menu, above the pointer by the clock; it closes when it loses focus, or with a choice. */
@Composable
fun ApplicationScope.TrayMenuWindow(at: Point, groups: List<List<TrayItem>>, dark: Boolean, onClose: () -> Unit) {
    val h = groups.sumOf { it.size } * ITEM + (groups.size - 1) * 9f + 12f + SHADOW * 2
    val w = MENU_WIDTH + SHADOW * 2
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val x = (at.x - w + SHADOW).coerceIn(bounds.x.toFloat(), bounds.x + bounds.width - w)
    val y = (at.y - h + SHADOW).coerceIn(bounds.y.toFloat(), bounds.y + bounds.height - h)
    val state = rememberWindowState(position = WindowPosition(x.dp, y.dp), size = DpSize(w.dp, h.dp))
    Window(onCloseRequest = onClose, state = state, undecorated = true, transparent = true, resizable = false, alwaysOnTop = true,
        title = "YDeck", icon = androidx.compose.ui.res.painterResource("icons/app.png")) {
        DisposableEffect(Unit) {
            val l = object : WindowAdapter() { override fun windowLostFocus(e: WindowEvent?) = onClose() }
            window.addWindowFocusListener(l)
            window.toFront(); window.requestFocus()
            onDispose { window.removeWindowFocusListener(l) }
        }
        DeckTheme(dark) { TrayMenu(groups) { onClose(); it.onClick() } }
    }
}

/** The menu itself, in its shadow (drawn also for the renders). */
@Composable
fun TrayMenu(groups: List<List<TrayItem>>, onPick: (TrayItem) -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxSize().padding(SHADOW.dp).shadow(8.dp, shape).clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(6.dp)) {
        groups.forEachIndexed { i, group ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).height(1.dp).background(c.line))
            group.forEach { item -> TrayRow(item) { onPick(item) } }
        }
    }
}

@Composable
private fun TrayRow(item: TrayItem, onClick: () -> Unit) {
    val c = LocalDeck.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().height(ITEM.dp).clip(RoundedCornerShape(9.dp)).background(if (hovered) c.surface2 else c.surface)
        .hoverable(hover).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(item.glyph, 16.dp, if (hovered) c.fg else c.muted)
        Spacer(Modifier.width(12.dp))
        Text(item.text, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f), maxLines = 1)
        item.hint?.let { Text(it, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1) }
    }
}
