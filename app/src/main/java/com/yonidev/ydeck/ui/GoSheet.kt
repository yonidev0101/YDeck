package com.yonidev.ydeck.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Before "Let's go" goes to the computer: a note for Claude on the suggestion (explain it first, change something),
 * and where it runs: on the project's page (YDeck runs the conversation, seen here too), or in a new Claude Code
 * window on the computer, with Remote Control if picked so it shows in the Claude app. The last pick is kept.
 */

/** A suggestion waiting for its "Let's go": [project]'s id, [text] as it would go to Claude. */
data class Going(val project: String, val name: String, val text: String, val go: (text: String, terminal: Boolean, remote: Boolean) -> Unit)

/** The suggestion, with the developer's note on it when there is one. */
fun withNote(text: String, note: String): String = note.trim().let { if (it.isEmpty()) text else "$text\n\nההערה שלי: $it" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoSheet(g: Going, onDismiss: () -> Unit) {
    val c = LocalDeck.current
    val prefs = LocalContext.current.getSharedPreferences("ydeck", Context.MODE_PRIVATE)
    var note by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf(prefs.getBoolean("goRemote", false)) }
    val inTerminal = prefs.getString("goWhere", "ydeck") == "terminal"
    fun go(terminal: Boolean) {
        prefs.edit().putString("goWhere", if (terminal) "terminal" else "ydeck").putBoolean("goRemote", remote).apply()
        onDismiss()
        g.go(withNote(g.text, note), terminal, remote)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(g.name, color = c.faint, fontSize = 13.sp)
            Text(g.text, color = c.fg, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, style = TextStyle(textDirection = dirOf(g.text)))
            val shape = RoundedCornerShape(14.dp)
            Box(Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(shape).background(c.surface2).border(1.dp, c.line, shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 15.sp, lineHeight = 22.sp, textDirection = TextDirection.Content)
                if (note.isEmpty()) Text("להוסיף משהו ל־Claude? (לא חובה)\nלמשל: תסביר לי קודם, או תשנה ל…", style = style.copy(color = c.faint))
                BasicTextField(note, { note = it.take(2000) }, textStyle = style, cursorBrush = SolidColor(c.accentText), maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("להריץ ב־YDeck", strong = !inTerminal) { go(false) }
                Pill("לפתוח ב־Claude Code", strong = inTerminal) { go(true) }
            }
            Text("ב־YDeck: השיחה בעמוד הפרויקט, כאן ובמחשב. ב־Claude Code: חלון חדש במחשב.", color = c.faint, fontSize = 12.5.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth().clickable { remote = !remote }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ב־Claude Code, עם Remote Control", color = c.fg, fontSize = 14.sp)
                    Text("להמשיך את השיחה מאפליקציית Claude בטלפון", color = c.muted, fontSize = 12.5.sp)
                }
                Switch(remote, { remote = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White))
            }
        }
    }
}
