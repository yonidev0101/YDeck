package com.yonidev.ydeck.desktop

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition

/*
 * Every right-click menu (a widget's, the strip's, a link's, a text field's copy and paste) drawn like the app and like
 * the tray's menu (TrayMenu.kt), not in Compose's default grey box. Given to everything by DeckTheme.
 */
class DeckContextMenu(private val colors: DeckColors) : ContextMenuRepresentation {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status as? ContextMenuState.Status.Open ?: return
        val close = { state.status = ContextMenuState.Status.Closed }
        Popup(
            properties = PopupProperties(focusable = true),
            onDismissRequest = close,
            popupPositionProvider = rememberPopupPositionProviderAtPosition(positionPx = status.rect.center),
            onKeyEvent = { if (it.key == Key.Escape) { close(); true } else false },
        ) {
            val shape = RoundedCornerShape(12.dp)
            Column(Modifier.padding(8.dp).shadow(8.dp, shape).clip(shape).background(colors.surface).border(1.dp, colors.line, shape)
                .padding(5.dp).width(IntrinsicSize.Max).widthIn(min = 170.dp)) {
                items().forEach { item -> Row(item.label) { close(); item.onClick() } }
            }
        }
    }

    @Composable
    private fun Row(label: String, onClick: () -> Unit) {
        val hover = remember { MutableInteractionSource() }
        val hovered by hover.collectIsHoveredAsState()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
            .background(if (hovered) colors.surface2 else colors.surface).hoverable(hover).clickable(onClick = onClick)
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(said(label), color = colors.fg, fontSize = 14.sp, fontFamily = PlexSans, maxLines = 1)
        }
    }

    /** A text field's own items come in English; in Hebrew they're said in Hebrew. */
    private fun said(label: String): String = when (label) {
        "Copy" -> tr("Copy", "העתקה")
        "Paste" -> tr("Paste", "הדבקה")
        "Cut" -> tr("Cut", "גזירה")
        "Select All", "Select all" -> tr("Select all", "בחירת הכול")
        else -> label
    }
}
