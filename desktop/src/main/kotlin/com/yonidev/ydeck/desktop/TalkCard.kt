package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Writing to Claude on the project's page ([Talk]): the conversation's last lines, a permission to answer when Claude
 * asks for one, and the field. A conversation open in a terminal is only shown.
 */

private val TALK_SHAPE = RoundedCornerShape(18.dp)

@Composable
fun TalkCard(p: Project, dir: File, dark: Boolean) {
    val c = LocalDeck.current
    LaunchedEffect(dir) { withContext(Dispatchers.IO) { Talk.load(dir) } }
    val chat = Talk.chats.collectAsState().value[dir.path] ?: Talk.Chat()
    val sessions by ClaudeLink.sessions.collectAsState()
    val elsewhere = remember(chat.session, sessions, chat.busy) { Talk.elsewhere(chat.session) }
    var all by remember(dir, chat.session) { mutableStateOf(false) }
    var past by remember(dir) { mutableStateOf<List<Talk.Past>?>(null) }
    var text by remember(dir) { mutableStateOf("") }
    if (Tools.claude == null) return

    Column(Modifier.fillMaxWidth().clip(TALK_SHAPE).background(c.surface).border(1.dp, c.line, TALK_SHAPE).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The header: whose conversation, and the ways out of it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.CLAUDE, 18.dp, androidx.compose.ui.graphics.Color(0xFFD97757))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Claude", "Claude"), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                val sub = when {
                    chat.session == null -> tr("A new conversation, in this project's folder", "שיחה חדשה, בתיקייה של הפרויקט")
                    else -> chat.title.ifBlank { tr("Conversation", "שיחה") } + if (chat.at > 0) " · " + ago(chat.at) else ""
                }
                Text(sub, color = c.muted, fontSize = 12.5.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = dirOf(sub)))
            }
            if (!chat.busy) {
                TextAction(tr("Conversations", "שיחות")) {
                    past = if (past == null) Transcript.list(dir) else null
                }
                Spacer(Modifier.width(14.dp))
                if (chat.session != null) { TextAction(tr("New", "חדשה")) { past = null; Talk.fresh(dir) }; Spacer(Modifier.width(14.dp)) }
            }
            if (chat.session != null && !elsewhere) TextAction(tr("Open in terminal", "פתח בטרמינל")) {
                Talk.handOff(dir)
                Tools.claudeIn(dir, p.name, resume = chat.session)
            }
        }

        past?.let { list ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(6.dp)) {
                if (list.isEmpty()) Text(tr("No conversations in this folder yet.", "עוד אין שיחות בתיקייה הזאת."), color = c.muted, fontSize = 13.sp,
                    fontFamily = PlexSans, modifier = Modifier.padding(10.dp))
                list.forEach { s ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { past = null; Talk.open(dir, s) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title, color = if (s.session == chat.session) c.accentText else c.fg, fontSize = 13.5.sp, fontFamily = PlexSans, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = TextStyle(textDirection = dirOf(s.title)))
                        Spacer(Modifier.width(10.dp))
                        Text(ago(s.at), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
                    }
                }
            }
        }

        if (elsewhere) Text(tr("Open in a terminal now: shown here, written to there.", "פתוחה עכשיו בטרמינל: מוצגת כאן, כותבים לה שם."),
            color = c.amberText, fontSize = 13.sp, fontFamily = PlexSans)

        // The conversation: the last few lines, all on a tap.
        val shown = if (all) chat.lines else chat.lines.takeLast(8)
        if (chat.lines.size > shown.size) TextAction(tr("Earlier (${chat.lines.size - shown.size})", "קודם (${chat.lines.size - shown.size})")) { all = true }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { steps(shown).forEach { TalkLine(it) } }

        if (chat.busy && chat.ask == null) Row(verticalAlignment = Alignment.CenterVertically) {
            ClaudeDot(ClaudeMode.WORKING, dark, 8.dp)
            Spacer(Modifier.width(10.dp))
            Text(tr("Claude is working…", "Claude עובד…"), color = claudeColor(ClaudeMode.WORKING, dark), fontSize = 13.5.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
            Pill(tr("Stop", "עצירה"), Tone.SOFT, small = true) { Talk.stop(dir) }
        }
        chat.ask?.let { AskPermission(dir, it, dark) }
        chat.error?.let { Text(it, color = c.danger, fontSize = 13.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content)) }

        if (!elsewhere) TalkField(text, { text = it }, busy = chat.busy, newOne = chat.session == null) {
            past = null
            Talk.send(dir, text); text = ""
        }
    }
}

/** Shown lines with the things Claude did in a row folded into one: the last, and how many before it. */
private sealed interface Shown
private data class Said(val line: Talk.Line) : Shown
private data class Did(val last: String, val more: Int) : Shown

private fun steps(lines: List<Talk.Line>): List<Shown> {
    val out = ArrayList<Shown>()
    var run = ArrayList<String>()
    fun flush() { if (run.isNotEmpty()) { out += Did(run.last(), run.size - 1); run = ArrayList() } }
    lines.forEach { l -> if (l.kind == Talk.Kind.STEP) run += l.text else { flush(); out += Said(l) } }
    flush()
    return out
}

@Composable
private fun TalkLine(s: Shown) {
    val c = LocalDeck.current
    when (s) {
        is Did -> Text(s.last + if (s.more > 0) tr("  · and ${s.more} more", "  · ועוד ${s.more}") else "", color = c.faint, fontSize = 12.5.sp,
            fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(s.last)))
        is Said -> if (s.line.kind == Talk.Kind.USER) Text(s.line.text, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, maxLines = 8, overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = dirOf(s.line.text), lineHeight = 21.sp),
            modifier = Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 9.dp))
        else MarkdownView(s.line.text, Modifier.fillMaxWidth())
    }
}

/** Claude asks to do something: what, and once · always · no. */
@Composable
private fun AskPermission(dir: File, ask: Talk.Ask, dark: Boolean) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CHANGED_DOT.copy(alpha = if (dark) 0.14f else 0.12f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Claude asks to: ", "Claude מבקש: ") + toolWords(ask.tool), color = c.amberText, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
        if (ask.what.isNotBlank()) Text(ask.what.take(600), color = c.fg, fontSize = 13.sp, fontFamily = PlexMono, lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface).padding(10.dp),
            style = TextStyle(textDirection = TextDirection.Ltr))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(tr("Allow", "לאשר"), Tone.PRIMARY, small = true) { Talk.answer(dir, Talk.Answer.ONCE) }
            if (ask.always != null) Pill(tr("Always", "תמיד"), Tone.SOFT, small = true) { Talk.answer(dir, Talk.Answer.ALWAYS) }
            Pill(tr("No", "לא"), Tone.PLAIN, small = true) { Talk.answer(dir, Talk.Answer.NO) }
        }
        ask.always?.let { Text(tr("Always: ", "תמיד: ") + it, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans, style = TextStyle(textDirection = dirOf(it))) }
    }
}

private fun toolWords(tool: String): String = when (tool) {
    "Bash", "PowerShell" -> tr("run a command", "להריץ פקודה")
    "Edit", "MultiEdit", "NotebookEdit" -> tr("change a file", "לשנות קובץ")
    "Write" -> tr("write a file", "לכתוב קובץ")
    "Read" -> tr("read a file", "לקרוא קובץ")
    "WebFetch" -> tr("open a web page", "לפתוח דף אינטרנט")
    "WebSearch" -> tr("search the web", "לחפש ברשת")
    else -> tool.removePrefix("mcp__").replace("__", " · ")
}

/** The field: Enter sends, Shift+Enter is a new line. */
@Composable
private fun TalkField(text: String, onText: (String) -> Unit, busy: Boolean, newOne: Boolean, onSend: () -> Unit) {
    val c = LocalDeck.current
    val ready = !busy && text.isNotBlank()
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(c.bg).border(1.dp, c.line, shape).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(vertical = 4.dp)) {
            val style = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, lineHeight = 21.sp, textDirection = TextDirection.Content)
            if (text.isEmpty()) Text(when {
                busy -> tr("Claude is working…", "Claude עובד…")
                newOne -> tr("Write to Claude, e.g. fix the title on the home page", "לכתוב ל־Claude, למשל: תתקן את הכותרת בעמוד הבית")
                else -> tr("Write to Claude", "לכתוב ל־Claude")
            }, style = style.copy(color = c.faint), maxLines = 1)
            BasicTextField(text, onText, textStyle = style, cursorBrush = SolidColor(c.accent), maxLines = 8,
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) && !e.isShiftPressed) { if (ready) onSend(); true } else false
                })
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(if (ready) c.fg else c.line).clickable(enabled = ready) { onSend() },
            contentAlignment = Alignment.Center) { GlyphIcon(Glyph.UP, 16.dp, c.bg) }
    }
}
