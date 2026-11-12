package com.yonidev.ydeck.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.data.Computer
import com.yonidev.ydeck.data.Project
import kotlinx.coroutines.delay

/*
 * Ask YDeck on the phone, as on the computer: questions about every project, answered on the computer by the
 * developer's own Claude Code, which reads the projects' folders and never changes them. The question goes sealed
 * through the relay (see Ask in shared/.../Snapshot.kt); what Claude reads, and then the answer, come back in the
 * computer's state.
 */

const val ASK_TITLE = "לשאול את YDeck"

/** What the mark says: ready, listening (typing), thinking, answered (lands once). The same moods as on the computer. */
enum class Mood { READY, LISTENING, THINKING, ANSWERED }

/** The mood from the conversation: thinking while the computer works, green for a moment after an answer. */
@Composable
fun askMood(chat: Computer.Chat?, typing: Boolean): Mood {
    val answered = chat?.turns?.count { it.answer?.answer?.isNotEmpty() == true } ?: 0
    var seen by remember { mutableStateOf(answered) }
    var fresh by remember { mutableStateOf(false) }
    LaunchedEffect(answered) {
        if (answered > seen) { fresh = true; seen = answered; delay(4000); fresh = false } else seen = answered
    }
    return when {
        chat?.busy == true -> Mood.THINKING
        fresh -> Mood.ANSWERED
        typing -> Mood.LISTENING
        else -> Mood.READY
    }
}

/**
 * YDeck's small mark (the open ring and its dot), in the dot's language: amber when ready (it breathes while you
 * type), indigo with the ring turning while Claude thinks, green landing once for an answer. Still otherwise.
 */
@Composable
fun AskMark(mood: Mood, size: Dp) {
    val c = LocalDeck.current
    val dot = when (mood) {
        Mood.THINKING -> c.accentText
        Mood.ANSWERED -> c.closed
        else -> c.open
    }
    val spin = if (mood == Mood.THINKING) rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "spin").value else 0f
    val breathe = if (mood == Mood.LISTENING) rememberInfiniteTransition(label = "breathe").animateFloat(1f, 0.8f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe").value else 1f
    val land = remember { Animatable(1f) }
    LaunchedEffect(mood) { if (mood == Mood.ANSWERED) { land.snapTo(0.3f); land.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) } }
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 100f
        rotate(spin) {
            drawArc(c.fg, startAngle = -4f, sweepAngle = 288f, useCenter = false, topLeft = Offset(16 * s, 16 * s), size = Size(68 * s, 68 * s),
                style = Stroke(16 * s, cap = StrokeCap.Round))
            val scale = if (mood == Mood.ANSWERED) land.value else breathe
            drawCircle(dot.copy(alpha = if (mood == Mood.LISTENING) 0.45f + 0.55f * (breathe - 0.8f) / 0.2f else 1f), 8.5f * s * scale, Offset(76 * s, 28.1f * s))
        }
    }
}

/**
 * The conversation, the whole screen: the mark and the name on top, the questions and answers, and the field at the
 * bottom. [focus]: the project it was opened from ("this project").
 */
@Composable
fun AskScreen(
    computer: Computer.State?,
    projects: List<Project>,
    /** The project it was opened from: what the questions are about, until another (or every one) is picked. */
    from: Project?,
    onBack: () -> Unit,
    onAsk: (question: String, projectId: String?) -> Unit,
    onNewChat: () -> Unit,
) {
    val c = LocalDeck.current
    var focus by remember(from?.id) { mutableStateOf(from) }
    val chat = computer?.chat ?: Computer.Chat()
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val list = rememberLazyListState()
    val last = chat.turns.lastOrNull()
    LaunchedEffect(chat.turns.size, last?.answer) { if (chat.turns.isNotEmpty()) list.animateScrollToItem(chat.turns.lastIndex) }
    val paired = computer?.paired == true
    val canAsk = paired && computer!!.canAsk
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "חזרה", tint = c.fg) }
            AskMark(askMood(chat, text.isNotEmpty()), 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(ASK_TITLE, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (chat.turns.isNotEmpty()) IconButton(onClick = { text = ""; onNewChat() }) { Icon(Icons.Outlined.EditNote, "שיחה חדשה", tint = c.muted) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat.turns.isEmpty()) Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("אפשר לשאול על כל אחד מהפרויקטים: איפה הוא עומד, מה הוחלט, איפה משהו נמצא בקוד.",
                    color = c.muted, fontSize = 15.sp, lineHeight = 23.sp)
                when {
                    !paired -> Text("כדי לשאול, מחברים את הטלפון ל־YDeck במחשב. התשובות מגיעות משם.", color = c.faint, fontSize = 14.sp, lineHeight = 21.sp)
                    !canAsk -> OffNotice(computer!!)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { examples(focus).forEach { q -> Suggestion(q) { onAsk(q, focus?.id) } } }
                }
            } else LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                itemsIndexed(chat.turns, key = { _, t -> t.ask.id }) { _, t -> TurnView(t) }
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chat.turns.isNotEmpty() && paired && !canAsk) OffNotice(computer!!)
            Composer(focus, projects, { focus = it }, text, { text = it }, field, enabled = canAsk && !chat.busy, busy = chat.busy) { if (text.isNotBlank()) { onAsk(text.trim(), focus?.id); text = "" } }
            Text("עונה ה־Claude Code שבמחשב שלך. הוא קורא את תיקיות הפרויקטים, אף פעם לא משנה אותן.",
                color = c.faint, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Asking from the phone is off at the computer (or that YDeck is older): where to turn it on. */
@Composable
private fun OffNotice(computer: Computer.State) {
    val c = LocalDeck.current
    Text(
        if (computer.claudeHere) "כדי לשאול מהטלפון, מדליקים את זה פעם אחת במחשב: ⁨YDeck⁩ ← הגדרות ← טלפון ← ״הטלפון יכול לשאול את YDeck״."
        else "כדי לשאול מהטלפון צריך במחשב את Claude Code ואת ⁨YDeck⁩ ‏1.19 ומעלה, ולהדליק: הגדרות ← טלפון ← ״הטלפון יכול לשאול את YDeck״.",
        color = c.openText, fontSize = 13.5.sp, lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.paper).padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

@Composable
private fun TurnView(t: Computer.Turn) {
    val c = LocalDeck.current
    val q = t.ask.question
    val a = t.answer
    val late = !t.done && System.currentTimeMillis() - t.ask.at > Computer.WAIT_MS
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The question in a soft bubble at the start; the answer plain, the width of the page.
        Text(q, color = c.fg, fontSize = 15.sp, lineHeight = 22.sp, style = TextStyle(textDirection = dirOf(q)),
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 10.dp))
        when {
            late -> Text("המחשב לא ענה. ודאו ש־YDeck פתוח במחשב, ושאלו שוב.", color = c.danger, fontSize = 13.5.sp)
            a == null -> Working("נשלח למחשב…")
            !a.done -> Working(a.step.ifBlank { "חושב…" })
            a.answer.isNotEmpty() -> {
                if (a.reads > 0) Text("קרא ${a.reads} קבצים · ${a.seconds} שניות", color = c.faint, fontSize = 12.5.sp)
                MarkdownView(a.answer, Modifier.fillMaxWidth())
            }
            else -> Text(when (a.error) {
                "off" -> "השאלות מהטלפון כבויות במחשב. מדליקים ב־⁨YDeck⁩ במחשב: הגדרות ← טלפון."
                "claude" -> "Claude Code לא נמצא במחשב."
                "stale" -> "השאלה הגיעה למחשב מאוחר מדי. אפשר לשאול שוב."
                else -> a.error
            }, color = c.danger, fontSize = 13.5.sp, lineHeight = 20.sp, style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

/** What Claude is doing now, quietly, with a small indigo dot. */
@Composable
private fun Working(text: String) {
    val c = LocalDeck.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accentText))
        Text(text, color = c.faint, fontSize = 13.5.sp, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
    }
}

/** A question to start from: a quiet outlined pill. */
@Composable
private fun Suggestion(text: String, onClick: () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(99.dp)
    Text(text, color = c.muted, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.clip(shape).border(1.dp, c.line, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** The question field: rounded, with what the question is about (one project, or every project, picked from the chip) and a round send button. */
@Composable
private fun Composer(focus: Project?, projects: List<Project>, onFocus: (Project?) -> Unit, text: String, onText: (String) -> Unit, field: FocusRequester, enabled: Boolean, busy: Boolean, onSend: () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth()) {
            val style = TextStyle(color = c.fg, fontSize = 16.sp, fontFamily = PlexSans, lineHeight = 23.sp, textDirection = TextDirection.Content)
            if (text.isEmpty()) Text(if (busy) "YDeck עונה…" else "שאלה על הפרויקטים", style = style.copy(color = c.faint))
            BasicTextField(text, onText, textStyle = style, cursorBrush = SolidColor(c.accentText), maxLines = 6,
                modifier = Modifier.fillMaxWidth().focusRequester(field))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScopeChip(focus, projects, onFocus)
            Spacer(Modifier.weight(1f))
            val ready = enabled && text.isNotBlank()
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (ready) c.fg else c.line).clickable(enabled = ready, onClick = onSend),
                contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowUpward, "שליחה", tint = c.bg, modifier = Modifier.size(19.dp)) }
        }
    }
}

/** What the question is about: every project, or one, picked from the projects. As on the computer. */
@Composable
private fun ScopeChip(focus: Project?, projects: List<Project>, onFocus: (Project?) -> Unit) {
    val c = LocalDeck.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(99.dp)).background(c.surface2).clickable { open = true }.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (focus != null) { Logo(focus, 16.dp); Spacer(Modifier.width(6.dp)) }
            Text(focus?.name ?: "כל הפרויקטים", color = c.muted, fontSize = 13.sp, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, tint = c.faint, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 420.dp), containerColor = c.surface) {
            DropdownMenuItem(
                text = { Text("כל הפרויקטים", color = c.fg, fontSize = 15.sp, fontWeight = if (focus == null) FontWeight.Medium else FontWeight.Normal) },
                leadingIcon = { Icon(Icons.Outlined.Apps, null, tint = c.muted, modifier = Modifier.size(20.dp)) },
                trailingIcon = { if (focus == null) Icon(Icons.Outlined.Check, null, tint = c.muted, modifier = Modifier.size(18.dp)) },
                onClick = { open = false; onFocus(null) },
            )
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, color = c.fg, fontSize = 15.sp, fontWeight = if (p.id == focus?.id) FontWeight.Medium else FontWeight.Normal, maxLines = 1) },
                    leadingIcon = { Logo(p, 22.dp) },
                    trailingIcon = { if (p.id == focus?.id) Icon(Icons.Outlined.Check, null, tint = c.muted, modifier = Modifier.size(18.dp)) },
                    onClick = { open = false; onFocus(p) },
                )
            }
        }
    }
}

/**
 * On the projects page, at the bottom: what the floating search is on the computer, a field-shaped way into the
 * conversation, with the mark in its mood.
 */
@Composable
fun AskBar(chat: Computer.Chat?, onClick: () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(99.dp)
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape).background(c.surface).border(1.dp, c.line, shape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AskMark(askMood(chat, false), 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(if (chat?.turns?.isNotEmpty() == true) "לחזור לשיחה" else "$ASK_TITLE…", color = c.muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

/** Questions to start from, about the project when there is one. */
private fun examples(focus: Project?): List<String> = if (focus != null) listOf(
    "איפה עצרתי ב־${focus.name}?",
    "מה הוחלט ב־${focus.name} ולמה?",
    "מה מחכה לי היום?",
) else listOf(
    "מה מחכה לי היום?",
    "על איזה פרויקט כדאי לעבוד עכשיו?",
    "באילו שירותים כל פרויקט משתמש?",
)
