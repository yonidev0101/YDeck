package com.yonidev.ydeck.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.R
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.data.Router
import com.yonidev.ydeck.link.Launch
import com.yonidev.ydeck.link.Launched
import com.yonidev.ydeck.link.Live
import com.yonidev.ydeck.link.LiveFile
import com.yonidev.ydeck.link.LiveReceipt
import com.yonidev.ydeck.link.LiveSession
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Note

private enum class Tab(val label: String) { MEMORY("סקירה"), GIT("Git"), LINKS("קישורים") }

/**
 * One project, everything the computer knows about it, in three tabs: its memory (what's next, what's open, the last
 * session, the decisions), Git (branch, changes, sessions and the files they touched, commits), and its links.
 * [onWrite] leaves a decision or a next step for the computer; null when the phone isn't paired. [onLaunch] asks the
 * computer to open Claude Code in the project's folder; null when it doesn't take such requests.
 */
@Composable
fun ProjectScreen(
    p: Project,
    memory: Memory?,
    live: Live?,
    pending: List<Note>,
    onBack: () -> Unit,
    onWrite: ((kind: String, text: String) -> Unit)?,
    launches: List<Pair<Launch, Launched?>> = emptyList(),
    /** The computer takes launch requests now; when false, the card says where to turn it on. */
    launchOn: Boolean = false,
    onLaunch: ((mode: String, session: String, prompt: String, remote: Boolean) -> Unit)? = null,
    /** Ask YDeck about this project (null before the phone is paired). */
    onAsk: (() -> Unit)? = null,
    chat: com.yonidev.ydeck.data.Computer.Chat? = null,
    /** This project's sessions waiting for the developer, and replying to them. */
    waiting: List<com.yonidev.ydeck.link.Waiting> = emptyList(),
    canReply: Boolean = false,
    replies: List<Pair<com.yonidev.ydeck.link.Reply, Launched?>> = emptyList(),
    onReply: ((waiting: String, text: String, choices: Map<String, String>) -> Unit)? = null,
) {
    val c = LocalDeck.current
    var tab by rememberSaveable { mutableStateOf(Tab.MEMORY) }
    var writing by remember { mutableStateOf<String?>(null) }
    var launching by remember { mutableStateOf(false) }
    val canWrite = onWrite != null && memory != null
    PageScaffold(
        title = p.name, onBack = onBack,
        actions = {
            if (onAsk != null) IconButton(onClick = onAsk) { AskMark(askMood(chat, false), 22.dp) }
            if (canWrite) IconButton(onClick = { writing = Note.NEXT }) { Icon(Icons.Outlined.Edit, "כתיבה", tint = c.fg) }
        },
    ) {
        item { Header(p, memory, live) }
        if (onReply != null) waiting.forEach { w ->
            item(key = "w:" + w.id) { WaitingCard(w, p, canReply, replies.lastOrNull { it.first.waiting == w.id }) { text, choices -> onReply(w.id, text, choices) } }
        }
        if (onLaunch != null && live != null) item { ClaudeCard(launches.lastOrNull(), launchOn) { launching = true } }
        item { Tabs(tab, mapOf(Tab.GIT to (live?.changes ?: 0), Tab.LINKS to p.actions.size)) { tab = it } }
        when (tab) {
            Tab.MEMORY -> item {
                MemoryTab(memory, live, pending, if (canWrite) { kind -> writing = kind } else null,
                    onDone = if (canWrite) { next -> onWrite?.invoke(Note.DONE, next) } else null)
            }
            Tab.GIT -> item { GitTab(live) }
            Tab.LINKS -> item { LinksTab(p, live) }
        }
    }
    writing?.let { kind -> WriteSheet(kind, onDismiss = { writing = null }, onSend = { k, text -> writing = null; onWrite?.invoke(k, text) }) }
    if (launching) LaunchSheet(live?.sessions.orEmpty(), live, onDismiss = { launching = false }) { mode, session, prompt, remote ->
        launching = false
        onLaunch?.invoke(mode, session, prompt, remote)
    }
}

@Composable
private fun Header(p: Project, m: Memory?, live: Live?) {
    val c = LocalDeck.current
    val progress = m?.progress()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 6.dp)) {
        // The mark is the progress: the ring fills with what was done out of what was planned, the logo inside it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (progress != null) ProgressMark(progress.first, progress.second, 112.dp) { Logo(p, 46.dp) }
            else Logo(p, 56.dp)
            Column(Modifier.weight(1f)) {
                val about = listOfNotNull(p.kind.takeIf { it.isNotBlank() }, live?.version?.takeIf { it.isNotBlank() }?.let { "גרסה ⁦$it⁩" }).joinToString(" · ")
                if (progress != null) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("${progress.first}")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.faint)); append("/${progress.second}"); pop()
                        },
                        color = c.fg, fontSize = 30.sp, lineHeight = 36.sp, fontFamily = PlexMono, fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em, style = TextStyle(textDirection = TextDirection.Ltr),
                    )
                    val left = progress.second - progress.first
                    Text(if (left == 0) "הכול נעשה" else "נעשו · נשארו $left", color = c.muted, fontSize = 13.sp)
                } else if (about.isNotBlank()) Text(about, color = c.muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (live != null && live.agentMode.isNotBlank()) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ClaudeDot(live.agentMode, 7.dp)
                    Text(claudeText(live), color = if (live.agentMode == "DONE" || live.agentMode == "OPEN") c.muted else claudeColor(live.agentMode), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // One quiet line under it: what it is, the branch and what's not saved, the server running.
        if (live != null || progress != null) {
            val parts = buildList<@Composable () -> Unit> {
                val about = listOfNotNull(p.kind.takeIf { it.isNotBlank() }, live?.version?.takeIf { it.isNotBlank() }?.let { "⁦$it⁩" }).joinToString(" · ")
                if (progress != null && about.isNotBlank()) add { Text(about, color = c.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (live != null && live.hasGit && live.branch.isNotBlank()) add { Text(live.branch, color = c.faint, fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1) }
                if (live != null && live.hasGit) add { Text(gitWords(live), color = if (live.changes > 0 || live.ahead > 0) c.openText else c.faint, fontSize = 13.sp, maxLines = 1) }
                live?.ports?.firstOrNull()?.let { port -> add { Dot(LIVE_DOT, 6.dp); Text(":$port", color = c.accentText, fontSize = 13.sp, fontFamily = PlexMono) } }
            }
            if (parts.isNotEmpty()) Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEachIndexed { i, part -> if (i > 0) Text("·", color = c.faint, fontSize = 13.sp); part() }
            }
        }
        // Claude at work or waiting here, and on what; nothing otherwise.
        if (live != null && (live.agentMode == "WORKING" || live.agentMode == "WAITING")) {
            val waiting = live.agentMode == "WAITING"
            val extra = listOfNotNull("Remote Control".takeIf { live.agentRemote }, "${live.agentOpen} סשנים פתוחים".takeIf { live.agentOpen > 1 })
            Column(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (waiting) CHANGED_DOT.copy(alpha = 0.14f) else c.accentTint).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClaudeDot(live.agentMode, 8.dp)
                    Text((listOf(claudeText(live)) + extra).joinToString(" · "), color = if (waiting) c.openText else claudeColor(live.agentMode), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (live.agentSince > 0) Text(ago(live.agentSince), color = c.muted, fontSize = 12.sp)
                }
                if (live.agentDetail.isNotBlank()) Text(if (waiting) live.agentDetail else "״${live.agentDetail}״", color = c.fg, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = dirOf(live.agentDetail)), modifier = Modifier.padding(start = 18.dp, top = 6.dp))
            }
        }
    }
}

/** Git in words, as the computer says it: what's not committed, not pushed, or that all is saved. */
private fun gitWords(l: Live): String = buildList {
    if (l.changes > 0) add(if (l.changes == 1) "שינוי אחד לא שמור" else "${l.changes} לא שמורים")
    if (l.ahead > 0) add(if (l.ahead == 1) "קומיט אחד לדחוף" else "${l.ahead} לדחוף")
    if (l.behind > 0) add("${l.behind} חדשים בשרת")
}.joinToString(" · ").ifEmpty { "הכול שמור" }


/** The tabs: one quiet segmented control, as on the computer. */
@Composable
private fun Tabs(selected: Tab, counts: Map<Tab, Int>, onSelect: (Tab) -> Unit) {
    val c = LocalDeck.current
    // As on the computer: words with a line under the chosen one, a count beside what has some.
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Tab.entries.forEach { t ->
                val on = t == selected
                val label = t.label + (counts[t]?.takeIf { it > 0 }?.let { "  $it" } ?: "")
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).clickable { onSelect(t) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (on) c.fg else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp, bottom = 11.dp))
                    Box(Modifier.height(2.dp).fillMaxWidth().background(if (on) c.fg else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

// ── memory ──

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun MemoryTab(m: Memory?, live: Live?, pending: List<Note>, write: ((String) -> Unit)?, onDone: ((String) -> Unit)?) {
    val c = LocalDeck.current
    if (m == null) {
        FootNote("לפרויקט הזה אין עדיין תיקייה ב־YDeck במחשב, אז אין לו זיכרון. כשמחברים לו תיקייה, יופיעו כאן הצעד הבא, הסשן האחרון, ההחלטות ו־Git.")
        return
    }
    Column {
        if (pending.isNotEmpty()) Block("ממתין למחשב") {
            pending.forEachIndexed { i, n ->
                if (i > 0) Hair()
                Text(when (n.kind) { Note.DECISION -> "החלטה"; Note.DONE -> "בוצע"; else -> "צעד הבא" }, color = c.faint, fontSize = 12.sp)
                Body(n.text)
            }
            Text("יישמר בפרויקט כש־YDeck במחשב פתוח.", color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        // A "done" on its way: the next step already shows as closed here.
        val closing = pending.any { it.kind == Note.DONE }
        // What's in progress: one sentence, ending in the brand's amber point.
        if (m.now.isNotBlank()) Section("עכשיו", top = 18.dp) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(m.now.trimEnd('.', ' '))
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = c.open)); append("."); pop()
                },
                color = c.fg, fontSize = 26.sp, lineHeight = 35.sp, fontFamily = Display, fontWeight = FontWeight.Light,
                style = TextStyle(textDirection = dirOf(m.now)),
            )
        }
        PathOf(if (closing) m.copy(next = "") else m, write, onDone)
        if (live != null && live.loops.isNotEmpty()) Section("פתוח עכשיו") {
            live.loops.forEachIndexed { i, l ->
                Row(Modifier.padding(top = if (i > 0) 12.dp else 0.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(top = 8.dp)) { Dot(loopDot(l.kind), 7.dp) }
                    Column(Modifier.weight(1f)) {
                        Body(l.title)
                        if (l.detail.isNotBlank()) Text(l.detail, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(l.detail)))
                    }
                }
            }
        }
        if (m.ideas.isNotEmpty()) Section("רעיונות בצד · ${m.ideas.size}") {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                m.ideas.forEach { t ->
                    Text(t, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, style = TextStyle(textDirection = dirOf(t)),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.surface2).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        More(m, write)
    }
}

/** A quiet section: a small label (and a count at its end), then its content. No boxes, no lines. */
@Composable
private fun Section(label: String, trailing: String? = null, top: androidx.compose.ui.unit.Dp = 30.dp, content: @Composable () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = top)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = SectionLabelStyle, color = c.faint, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, color = c.faint, fontSize = 12.sp)
        }
        Column(Modifier.padding(top = 10.dp)) { content() }
    }
}

/**
 * The path: what was done (folded into one green point), the next step (the one that matters, in a card), and what's
 * left after it as empty rings, on one line. The next step is closed or changed here; the rest is kept by Claude and
 * on the computer.
 */
@Composable
private fun PathOf(m: Memory, write: ((String) -> Unit)?, onDone: ((String) -> Unit)?) {
    val c = LocalDeck.current
    var openDone by remember { mutableStateOf(false) }
    val next = m.next.takeIf { it.isNotBlank() } ?: m.left.firstOrNull()
    val rest = m.left.filter { it != next }
    val hasDone = m.doneItems.isNotEmpty() || m.done.isNotBlank()
    if (next == null && !hasDone && write == null) return
    val leftCount = rest.size + (if (next != null) 1 else 0)
    Section("המסלול", trailing = when (leftCount) { 0 -> null; 1 -> "שלב אחד נשאר"; else -> "$leftCount שלבים נשארו" }, top = 26.dp) {
        Column {
            if (hasDone) PathRow(nodeTop = 13.dp, below = c.closed, node = { DoneNode() }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openDone = !openDone }.padding(vertical = 4.dp)) {
                    Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (m.doneItems.isEmpty()) "בסשן האחרון" else "${m.doneItems.size} נעשו", color = c.muted, fontSize = 15.sp)
                            (m.doneItems.firstOrNull() ?: m.done).let { last ->
                                Text("אחרון: $last", color = c.faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(last)))
                            }
                        }
                        Chevron(open = openDone)
                    }
                    if (openDone) Column(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                        if (m.done.isNotBlank()) {
                            Text("בסשן האחרון" + (if (m.doneAt > 0) " · ${ago(m.doneAt)}" else "") + (if (m.agent.isNotBlank()) " · ${m.agent}" else ""), color = c.faint, fontSize = 12.sp)
                            Text(m.done, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, style = TextStyle(textDirection = dirOf(m.done)), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        }
                        m.doneItems.forEach { t ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.padding(top = 8.dp)) { Dot(c.closed.copy(alpha = 0.7f), 5.dp) }
                                Text(t, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, style = TextStyle(textDirection = dirOf(t)))
                            }
                        }
                    }
                }
            }
            // The next step: an indigo point and a card. Closing it or changing it is the phone's one move here.
            PathRow(nodeTop = 16.dp, above = if (hasDone) c.closed else null, below = if (rest.isNotEmpty()) c.line else null, node = { NextNode(next != null) }) {
                Column(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("הבא", color = c.accentText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val own = next != null && next == m.next
                        if (own && onDone != null) SmallAction("בוצע") { onDone(m.next) }
                        if (write != null) SmallAction(if (next != null) "שינוי" else "צעד הבא") { write(Note.NEXT) }
                    }
                    Text(next ?: "עוד אין צעד הבא", color = if (next != null) c.fg else c.faint, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium,
                        style = TextStyle(textDirection = dirOf(next ?: "א")), modifier = Modifier.padding(end = 8.dp))
                }
            }
            rest.forEachIndexed { i, t ->
                PathRow(above = c.line, below = if (i < rest.lastIndex) c.line else null, node = { RingNode() }) {
                    Text(t, color = c.fg.copy(alpha = 0.86f), fontSize = 15.sp, lineHeight = 22.sp, style = TextStyle(textDirection = dirOf(t)),
                        modifier = Modifier.heightIn(min = 44.dp).padding(vertical = 11.dp))
                }
            }
        }
    }
}

/** One stop on the path: its point on the line (at [nodeTop], else centred), the line above and below it, and what it says. */
@Composable
private fun PathRow(nodeTop: androidx.compose.ui.unit.Dp? = null, above: Color? = null, below: Color? = null, node: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            val line = Modifier.width(2.dp)
            if (nodeTop != null) Box(line.height(nodeTop).background(above ?: Color.Transparent))
            else Box(line.weight(1f).background(above ?: Color.Transparent))
            node()
            Box(line.weight(1f).background(below ?: Color.Transparent))
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun DoneNode() {
    val c = LocalDeck.current
    Box(Modifier.size(22.dp).clip(CircleShape).background(c.closed), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
            val s = size.width
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(s * 0.2f, s * 0.53f); lineTo(s * 0.4f, s * 0.72f); lineTo(s * 0.8f, s * 0.3f) }
            drawPath(path, c.bg, style = androidx.compose.ui.graphics.drawscope.Stroke(s * 0.14f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
private fun NextNode(on: Boolean) {
    val c = LocalDeck.current
    Box(Modifier.size(22.dp).clip(CircleShape).background(c.accentTint), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) c.accent else c.faint))
    }
}

@Composable
private fun RingNode() {
    val c = LocalDeck.current
    Box(Modifier.width(22.dp).height(12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(c.bg).border(2.dp, Color(0xFF4A4A52), CircleShape))
    }
}

@Composable
private fun Chevron(open: Boolean) {
    val c = LocalDeck.current
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            if (open) { moveTo(s * 0.25f, s * 0.62f); lineTo(s * 0.5f, s * 0.38f); lineTo(s * 0.75f, s * 0.62f) }
            else { moveTo(s * 0.25f, s * 0.38f); lineTo(s * 0.5f, s * 0.62f); lineTo(s * 0.75f, s * 0.38f) }
        }
        drawPath(path, c.faint, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** What changes rarely (about the project, the decisions, the last version) behind one quiet row. */
@Composable
private fun More(m: Memory, write: ((String) -> Unit)?) {
    val c = LocalDeck.current
    var open by rememberSaveable { mutableStateOf(false) }
    val parts = listOfNotNull(
        "על הפרויקט".takeIf { m.summary.isNotBlank() },
        (if (m.decisions.isEmpty()) "החלטות" else "${m.decisions.size} החלטות").takeIf { m.decisions.isNotEmpty() || write != null },
        m.releases.firstOrNull()?.let { "גרסה ⁦${it.version}⁩" },
    )
    if (parts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 52.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(parts.joinToString(" · "), color = c.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Chevron(open)
        }
        if (open) {
            if (m.summary.isNotBlank()) Block("על הפרויקט") { Body(m.summary, muted = true) }
            Decisions(m, write)
            m.releases.firstOrNull()?.let { r ->
                Block("גרסה אחרונה") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.version, color = c.fg, fontSize = 15.sp, fontFamily = PlexMono)
                        if (r.date.isNotBlank()) Text(r.date, color = c.faint, fontSize = 13.sp, fontFamily = PlexMono)
                    }
                    if (r.note.isNotBlank()) Body(r.note, muted = true)
                }
            }
        }
    }
}

@Composable
private fun Decisions(m: Memory, write: ((String) -> Unit)?) {
    val c = LocalDeck.current
    var all by remember { mutableStateOf(false) }
    if (m.decisions.isEmpty() && write == null) return
    val shown = if (all) m.decisions else m.decisions.take(4)
    Block("החלטות" + if (m.decisions.isNotEmpty()) " · ${m.decisions.size}" else "", action = write?.let { w -> "הוספה" to { w(Note.DECISION) } }) {
        if (m.decisions.isEmpty()) Text("עוד אין החלטות. החלטה שנכתבת כאן נשמרת בפרויקט, והסשן הבא של Claude מכיר אותה.", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
        shown.forEachIndexed { i, d ->
            if (i > 0) Hair()
            Body(d.text)
            if (d.date.isNotBlank()) Text(d.date, color = c.faint, fontSize = 12.sp, fontFamily = PlexMono, modifier = Modifier.padding(top = 2.dp))
        }
        if (m.decisions.size > 4) SmallAction(if (all) "פחות" else "עוד ${m.decisions.size - shown.size}", Modifier.padding(top = 10.dp)) { all = !all }
    }
}

// ── git ──

@Composable
private fun GitTab(live: Live?) {
    val c = LocalDeck.current
    val context = LocalContext.current
    if (live == null || !live.hasGit) {
        FootNote(if (live == null) "לפרויקט הזה אין תיקייה ב־YDeck במחשב." else "התיקייה של הפרויקט הזה עוד לא ריפו של Git.")
        return
    }
    Column {
        Block("ענף") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⁦${live.branch}⁩", color = c.fg, fontSize = 17.sp, fontFamily = PlexMono, modifier = Modifier.weight(1f, fill = false))
                if (live.upstream.isNotBlank()) Text("← ⁦${live.upstream}⁩", color = c.faint, fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (live.changes == 0) "נקי" else if (live.changes == 1) "שינוי אחד" else "${live.changes} שינויים",
                    color = if (live.changes > 0) c.openText else c.closed, bg = (if (live.changes > 0) c.open else c.closed).copy(alpha = 0.12f))
                if (live.ahead > 0) Chip("↑${live.ahead} לא נדחפו", color = c.openText, bg = c.open.copy(alpha = 0.12f))
                if (live.behind > 0) Chip("↓${live.behind} חדשים בשרת", color = c.accentText, bg = c.accentTint)
            }
            if (live.lastAt > 0) Text("קומיט אחרון ${ago(live.lastAt)}: ${live.lastSubject}", color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Rtl), modifier = Modifier.padding(top = 10.dp))
            if (live.web.isNotBlank()) SmallAction("פתיחת הריפו", Modifier.padding(top = 10.dp)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(live.web))) }
            }
        }
        if (live.files.isNotEmpty()) Block(if (live.changes > live.files.size) "שינויים · ${live.files.size} מתוך ${live.changes}" else "שינויים") {
            Files(live.files)
        }
        if (live.receipts.isNotEmpty()) Block("סשנים") {
            live.receipts.forEachIndexed { i, r ->
                if (i > 0) Hair()
                ReceiptRow(r)
            }
        }
        if (live.commits.isNotEmpty()) Block("קומיטים") {
            live.commits.forEachIndexed { i, cm ->
                if (i > 0) Hair()
                Body(cm.subject)
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cm.hash, color = c.faint, fontSize = 12.sp, fontFamily = PlexMono)
                    Text("${cm.author} · ${ago(cm.at)}", color = c.faint, fontSize = 12.sp)
                }
            }
        }
        FootNote("לקריאה בלבד: קומיט, דחיפה ושאר הפעולות נעשים במחשב.")
    }
}

/** One agent session: what it was asked, when, and the files it touched (tap to see them). */
@Composable
private fun ReceiptRow(r: LiveReceipt) {
    val c = LocalDeck.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(enabled = r.files.isNotEmpty()) { open = !open }) {
        Body(r.title)
        val files = when (r.files.size) { 0 -> "בלי שינויים בקבצים"; 1 -> "קובץ אחד"; else -> "${r.files.size} קבצים" }
        Text(listOfNotNull(r.agent, ago(r.endAt.takeIf { it > 0 } ?: r.startAt), files, if (r.rewound) "הוחזר" else null).joinToString(" · "),
            color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        if (open) Column(Modifier.padding(top = 8.dp)) { Files(r.files) }
    }
}

/** Changed files: a letter in the state's colour, then the path (left to right, as paths are). */
@Composable
private fun Files(files: List<LiveFile>) {
    val c = LocalDeck.current
    files.forEach { f ->
        val code = f.code.trim()
        val (letter, color) = when {
            f.code == "??" || 'A' in code -> "A" to c.closed
            'D' in code -> "D" to c.danger
            'R' in code -> "R" to c.accentText
            'U' in code -> "U" to c.open
            else -> "M" to c.openText
        }
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(letter, color = color, fontSize = 12.sp, fontFamily = PlexMono, fontWeight = FontWeight.Medium)
            Text(f.path, color = c.fg.copy(alpha = 0.85f), fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.StartEllipsis,
                style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
        }
    }
}

// ── links ──

@Composable
private fun LinksTab(p: Project, live: Live?) {
    val c = LocalDeck.current
    val context = LocalContext.current
    Column {
        if (p.actions.isNotEmpty()) SectionCard("קישורים", hint = p.googleEmail?.let { "שירותי גוגל נפתחים בחשבון $it" }) {
            p.actions.forEachIndexed { i, a ->
                if (i > 0) Divider(inset = 58.dp)
                ListRow(
                    onClick = { Router.open(context, p, a) },
                    leading = { ServiceIcon(a.service, a.faviconHost) },
                    title = a.label,
                    subtitle = Router.displayUrl(a.url),
                    trailing = { val b = Router.browserFor(p, a); if (b.pkg != Router.CHROME) Chip(b.label) },
                )
            }
        } else FootNote("אין לפרויקט קישורים. מוסיפים אותם ב־YDeck במחשב.")
        if (p.copies.isNotEmpty()) SectionCard("העתקה") {
            p.copies.forEachIndexed { i, item ->
                if (i > 0) Divider(inset = 58.dp)
                ListRow(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(item.label, item.value))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "הועתק", Toast.LENGTH_SHORT).show()
                    },
                    leading = { IconBox(R.drawable.ic_s_copy) },
                    title = item.label,
                    subtitle = item.value,
                )
            }
        }
        if (live != null && (live.ports.isNotEmpty() || live.missingEnv.isNotEmpty())) Block("במחשב") {
            if (live.ports.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Dot(LIVE_DOT, 6.dp)
                    Text("שרת פיתוח רץ · ⁦localhost:${live.ports.first()}⁩", color = c.fg, fontSize = 15.sp)
                }
            }
            if (live.missingEnv.isNotEmpty()) {
                if (live.ports.isNotEmpty()) Hair()
                Text("חסרים ב־.env", color = c.openText, fontSize = 13.sp)
                Text(live.missingEnv.joinToString("  "), color = c.fg, fontSize = 13.sp, fontFamily = PlexMono, style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ── writing ──

/**
 * Writing a next step or a decision, typed or spoken. It reaches YDeck on the computer, which keeps it in the
 * project's files; the next Claude session starts knowing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteSheet(initial: String, onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initial) }
    var text by remember { mutableStateOf("") }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { said ->
            text = if (text.isBlank()) said else text.trimEnd() + " " + said
        }
    }
    val canSpeak = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(3.dp)) {
                listOf(Note.NEXT to "צעד הבא", Note.DECISION to "החלטה").forEach { (k, label) ->
                    val on = k == kind
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) c.line else Color.Transparent).clickable { kind = k }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) c.fg else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
                }
            }
            Text(
                if (kind == Note.NEXT) "מחליף את הצעד הבא של הפרויקט." else "נשמרת בהחלטות של הפרויקט, ש־Claude שומר עליהן בכל סשן.",
                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(14.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 17.sp, lineHeight = 25.sp, fontFamily = PlexSans, textDirection = TextDirection.Content)
                if (text.isEmpty()) Text(if (kind == Note.NEXT) "מה הדבר הבא לעשות?" else "מה הוחלט, ולמה?", style = style.copy(color = c.faint))
                BasicTextField(text, { text = it.take(Note.MAX) }, textStyle = style, cursorBrush = SolidColor(c.fg), modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canSpeak) {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape).background(c.surface2).clickable {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, if (kind == Note.NEXT) "מה הצעד הבא?" else "מה ההחלטה?"))
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Mic, "הקלטה", tint = c.fg) }
                }
                Button(
                    onClick = { onSend(kind, text) },
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.fg, contentColor = c.bg, disabledContainerColor = c.surface2, disabledContentColor = c.faint),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("שליחה למחשב", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

// ── Claude Code on the computer ──

private val CLAUDE = Color(0xFFD97757)
private const val CLAUDE_APP = "com.anthropic.claude"

/** "Open Claude on the computer", and what became of the last time it was asked. */
@Composable
private fun ClaudeCard(last: Pair<Launch, Launched?>?, on: Boolean, onOpen: () -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    val l = last?.first
    val done = last?.second
    val status = when {
        !on -> "כבוי במחשב. מדליקים ב־YDeck במחשב ← הגדרות ← טלפון."
        l == null -> null
        done == null -> if (System.currentTimeMillis() - l.at > 90_000) "המחשב עוד לא ענה. YDeck פתוח בו?" else "נשלח, מחכה למחשב…"
        done.ok -> "נפתח במחשב ${ago(l.at)}" + if (l.remote) " · זמין באפליקציית Claude" else ""
        else -> when (done.why) {
            "off" -> "הפתיחה מהטלפון כבויה במחשב (הגדרות ← טלפון)"
            "claude" -> "Claude Code לא מותקן במחשב"
            "stale" -> "הבקשה הגיעה מאוחר מדי ולא נפתחה. אפשר לשלוח שוב."
            "project", "folder" -> "לפרויקט אין תיקייה במחשב"
            "session" -> "הסשן הזה כבר לא נמצא בתיקייה"
            else -> "Claude לא נפתח במחשב"
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(enabled = on, onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBox(R.drawable.si_claude, 34.dp, tint = Color.White, bg = if (on) CLAUDE else c.surface2)
            Column(Modifier.weight(1f)) {
                Text("פתיחת Claude במחשב", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(status ?: "להמשיך, סשן חדש או סשן קודם", color = if (on && done?.ok == false) c.openText else c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (on && l != null && l.remote && done?.ok == true) SmallAction("פתיחת אפליקציית Claude", Modifier.padding(top = 4.dp)) {
            // The session shows up there under the project's name; without the app, its page on Google Play.
            val open = context.packageManager.getLaunchIntentForPackage(CLAUDE_APP)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$CLAUDE_APP"))
            runCatching { context.startActivity(open) }
        }
    }
}

/**
 * How to open Claude Code on the computer: pick up the last session, start a new one, or choose an earlier one;
 * with Remote Control (to go on from the Claude app here) and a first message. The computer does exactly this and
 * nothing else (see [Launch]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaunchSheet(sessions: List<LiveSession>, live: Live?, onDismiss: () -> Unit, onSend: (String, String, String, Boolean) -> Unit) {
    val c = LocalDeck.current
    var mode by remember { mutableStateOf(if (sessions.isEmpty()) Launch.NEW else Launch.CONTINUE) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var remote by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp)) {
            Text("Claude במחשב", color = c.fg, fontSize = 20.sp, fontFamily = Display, modifier = Modifier.padding(bottom = 12.dp))
            // Already one running there: say so before a second one opens.
            if (live != null && live.agentOpen > 0) Text(
                (if (live.agentOpen == 1) "כבר יש סשן פתוח בתיקייה הזאת" else "כבר יש ${live.agentOpen} סשנים פתוחים בתיקייה הזאת") +
                    if (live.agentRemote) ", עם Remote Control: אפשר להמשיך אותו באפליקציית Claude." else ".",
                color = c.openText, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 12.dp),
            )
            val modes = if (sessions.isEmpty()) listOf(Launch.NEW to "סשן חדש")
                else listOf(Launch.CONTINUE to "להמשיך", Launch.NEW to "סשן חדש", Launch.RESUME to "סשן קודם")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(3.dp)) {
                modes.forEach { (k, label) ->
                    val on = k == mode
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) c.line else Color.Transparent).clickable { mode = k }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) c.fg else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
                }
            }
            Text(
                when (mode) {
                    Launch.CONTINUE -> "ממשיך את הסשן האחרון: ${sessions.firstOrNull()?.title.orEmpty()}"
                    Launch.RESUME -> "בוחרים סשן מהתיקייה של הפרויקט."
                    else -> "סשן חדש בתיקייה של הפרויקט, עם הזיכרון שלו."
                },
                color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            if (mode == Launch.RESUME) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(vertical = 4.dp)) {
                sessions.forEach { s ->
                    val on = s.id == chosen
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = s.id }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) c.accent else c.line))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = c.fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(s.title)))
                            if (s.at > 0) Text(ago(s.at), color = c.faint, fontSize = 12.sp)
                        }
                    }
                }
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(14.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = PlexSans, textDirection = TextDirection.Content)
                if (text.isEmpty()) Text(if (mode == Launch.NEW) "מה לעשות? (לא חובה)" else "הודעה להמשך (לא חובה)", style = style.copy(color = c.faint))
                BasicTextField(text, { text = it.take(Launch.MAX) }, textStyle = style, cursorBrush = SolidColor(c.fg), modifier = Modifier.fillMaxWidth())
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { remote = !remote }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Remote Control", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("להמשיך לעבוד על הסשן מאפליקציית Claude בטלפון", color = c.muted, fontSize = 13.sp)
                }
                Switch(remote, { remote = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White))
            }
            Button(
                onClick = { onSend(mode, if (mode == Launch.RESUME) chosen.orEmpty() else "", text, remote) },
                enabled = mode != Launch.RESUME || chosen != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.fg, contentColor = c.bg, disabledContainerColor = c.surface2, disabledContentColor = c.faint),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(50.dp),
            ) { Text("פתיחה במחשב", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            Text("נפתח בטרמינל חדש במחשב, בתיקייה של הפרויקט. YDeck צריך להיות פתוח שם.", color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ── pieces ──

/** A labelled block, between hairlines; [action] puts a small text button at the label's end. */
@Composable
private fun Block(label: String, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { SectionHeader(label) }
            if (action != null) SmallAction(action.first, Modifier.padding(bottom = 4.dp), action.second)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) { content() }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
    }
}

@Composable
private fun SmallAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(text, color = LocalDeck.current.accentText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
private fun Hair() = Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LocalDeck.current.line))

@Composable
private fun Body(text: String, muted: Boolean = false) {
    val c = LocalDeck.current
    Text(text, color = if (muted) c.muted else c.fg, fontSize = 15.sp, lineHeight = 23.sp, style = TextStyle(textDirection = dirOf(text)))
}
