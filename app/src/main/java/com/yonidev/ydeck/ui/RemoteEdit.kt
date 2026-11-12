package com.yonidev.ydeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.link.Note

/*
 * The phone as a remote: changing YDeck's data from here, where it shows (design "א · ישר במקום"). Every change goes
 * to the computer as a [Note] and shows here at once (see Overlay); nothing here runs anything.
 */

/** One line of text to ask for: what it's for, a hint, what it starts with, and what happens with it. */
data class TextAsk(val title: String, val hint: String, val initial: String = "", val onSend: (String) -> Unit)

/** One line of text, typed: a step, an idea, a renamed step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSheet(a: TextAsk, onDismiss: () -> Unit) {
    val c = LocalDeck.current
    var text by remember { mutableStateOf(a.initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(a.title, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Field(text, a.hint) { text = it }
            SendButton("שמירה", text.isNotBlank() && text.trim() != a.initial.trim()) { a.onSend(text.trim()); onDismiss() }
        }
    }
}

/** A small menu by what was pressed: its actions, the last one red when [danger]. */
@Composable
fun ItemMenu(open: Boolean, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>, danger: Boolean = false) {
    val c = LocalDeck.current
    DropdownMenu(open, onDismiss, containerColor = c.surface2) {
        items.forEachIndexed { i, (label, act) ->
            DropdownMenuItem(
                text = { Text(label, color = if (danger && i == items.lastIndex) Color(0xFFF87171) else c.fg, fontSize = 15.sp) },
                onClick = { onDismiss(); act() },
            )
        }
    }
}

/** The colours a project can take from here: the computer's own few, and black. */
val SWATCHES = listOf("#5A52F5", "#1A6FDB", "#0EA5E9", "#10B981", "#F59E0B", "#EF4444", "#EC4899", "#8B5CF6", "#0B0B0C", "#6E6E73")

private fun hex(color: Int) = String.format("#%06X", color and 0xFFFFFF)

/**
 * A project's details, edited here: the name, the line under it and its colour (saved together), its links and its
 * values to copy (each added or removed at once).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(p: Project, onDismiss: () -> Unit, edit: (kind: String, text: String, args: List<String>) -> Unit) {
    val c = LocalDeck.current
    var name by remember { mutableStateOf(p.name) }
    var kind by remember { mutableStateOf(p.kind) }
    var color by remember { mutableStateOf(hex(p.color)) }
    var linkLabel by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var copyLabel by remember { mutableStateOf("") }
    var copyValue by remember { mutableStateOf("") }
    val changed = name.isNotBlank() && (name.trim() != p.name || kind.trim() != p.kind || color != hex(p.color))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("פרטי הפרויקט", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Label("שם")
            Field(name, "שם הפרויקט") { name = it }
            Label("מה זה")
            Field(kind, "למשל: אפליקציה + אתר") { kind = it }
            Label("צבע")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SWATCHES.forEach { s ->
                    val on = s.equals(color, ignoreCase = true)
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(s)))
                        .then(if (on) Modifier.border(2.dp, c.fg, CircleShape) else Modifier.border(1.dp, c.line, CircleShape)).clickable { color = s })
                }
            }
            SendButton("שמירת הפרטים", changed) {
                if (name.trim() != p.name) edit(Note.NAME, name.trim(), emptyList())
                if (kind.trim() != p.kind) edit(Note.KIND, kind.trim(), emptyList())
                if (color != hex(p.color)) edit(Note.COLOR, color, emptyList())
                onDismiss()
            }

            Hairline()
            Label("קישורים")
            p.actions.forEach { a ->
                Removable(a.label, a.url) { edit(Note.LINK_DROP, a.url, emptyList()) }
            }
            Field(linkLabel, "שם הקישור (לא חובה)") { linkLabel = it }
            Field(linkUrl, "https://…", ltr = true) { linkUrl = it }
            val url = linkUrl.trim().let { if (it.isNotEmpty() && !it.contains("://")) "https://$it" else it }
            SendButton("הוספת קישור", url.startsWith("http") && url.length > 10, soft = true) {
                edit(Note.LINK_ADD, url, listOf("link", linkLabel.trim())); linkLabel = ""; linkUrl = ""
            }

            Hairline()
            Label("ערכים להעתקה")
            p.copies.forEach { item -> Removable(item.label, item.value) { edit(Note.COPY_DROP, item.value, listOf(item.label)) } }
            Field(copyLabel, "שם (למשל: Package)") { copyLabel = it }
            Field(copyValue, "הערך", ltr = true) { copyValue = it }
            SendButton("הוספת ערך", copyValue.isNotBlank(), soft = true) {
                edit(Note.COPY_ADD, copyValue.trim(), listOf(copyLabel.trim())); copyLabel = ""; copyValue = ""
            }
        }
    }
}

/** A new project, made here: a name and a colour. Its folder is connected on the computer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectSheet(onDismiss: () -> Unit, onCreate: (name: String, color: String) -> Unit) {
    val c = LocalDeck.current
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(SWATCHES.first()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("פרויקט חדש", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Field(name, "שם הפרויקט") { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SWATCHES.forEach { s ->
                    val on = s == color
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(s)))
                        .then(if (on) Modifier.border(2.dp, c.fg, CircleShape) else Modifier.border(1.dp, c.line, CircleShape)).clickable { color = s })
                }
            }
            Text("נוצר ב־YDeck במחשב. את התיקייה שלו מחברים שם.", color = c.muted, fontSize = 13.sp)
            SendButton("יצירה", name.isNotBlank()) { onCreate(name.trim(), color); onDismiss() }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = LocalDeck.current.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(LocalDeck.current.line))

@Composable
private fun Field(value: String, hint: String, ltr: Boolean = false, onChange: (String) -> Unit) {
    val c = LocalDeck.current
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 13.dp)) {
        val style = TextStyle(color = c.fg, fontSize = 16.sp, fontFamily = PlexSans, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content)
        if (value.isEmpty()) Text(hint, style = style.copy(color = c.faint))
        BasicTextField(value, { onChange(it.take(Note.MAX).replace("\n", " ")) }, textStyle = style, cursorBrush = SolidColor(c.fg), singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

/** Something listed with a way to take it out: its name, what it holds, and ×. */
@Composable
private fun Removable(title: String, detail: String, onRemove: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (title.isNotBlank()) Text(title, color = c.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = c.muted, fontSize = 12.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Ltr))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Text("×", color = c.muted, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SendButton(text: String, enabled: Boolean, soft: Boolean = false, onClick: () -> Unit) {
    val c = LocalDeck.current
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = if (soft) c.surface2 else c.fg, contentColor = if (soft) c.fg else c.bg,
            disabledContainerColor = c.surface2, disabledContentColor = c.faint),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(50.dp),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}
