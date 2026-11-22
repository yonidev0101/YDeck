package com.yonidev.ydeck.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.RowScope

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.combinedClickable
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

/** What "save in Git" asks Claude on the computer: commit what is open with a clear message, then push. */
private const val GIT_PROMPT = "שמור את כל השינויים הפתוחים ב־Git: commit עם הודעה ברורה, ואז push. אל תשנה קוד."

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
    /** Changes this project's data on the computer (the lists, the archive, the details; see [Note]); null when not paired. */
    onEdit: ((kind: String, text: String, args: List<String>) -> Unit)? = null,
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
    /** Commands for the computer: on there, sent from here, the lines allowed always, and sending one. */
    runOn: Boolean = false,
    runs: List<Pair<com.yonidev.ydeck.link.RunCommand, com.yonidev.ydeck.link.Ran?>> = emptyList(),
    runOk: List<String> = emptyList(),
    onRun: ((line: String, window: Boolean) -> Unit)? = null,
    /** The computer shows this project's files here, read only (on in its settings). */
    filesOn: Boolean = false,
) {
    val c = LocalDeck.current
    var tab by rememberSaveable { mutableStateOf(Tab.MEMORY) }
    var writing by remember { mutableStateOf<String?>(null) }
    var launching by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    // A command's page: one tapped in the list, or the one just sent (the first sent after [sentAt]).
    var watching by remember { mutableStateOf<String?>(null) }
    var sentAt by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    val canWrite = onWrite != null && memory != null
    var asking by remember { mutableStateOf<TextAsk?>(null) }
    var details by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val edit = if (memory != null) onEdit else null
    // Four questions, always in this order: how is it moving, what needs me, what's next, what's in it. The rest
    // (ideas, decisions, links, Git, commands) lives one tap away, in "What's in the project".
    var inside by rememberSaveable { mutableStateOf<String?>(null) }
    var talking by rememberSaveable { mutableStateOf(false) }
    val onRemote: ((String) -> Unit)? = if (onLaunch != null && launchOn) { session -> onLaunch(if (session.isBlank()) Launch.NEW else Launch.RESUME, session, "", true) } else null
    val menuItems: List<Pair<String, () -> Unit>> = buildList {
        add("עריכת פרטים וקישורים" to { details = true })
        if (onRun != null && live != null) add("להריץ פקודה" to { running = true })
        if (onLaunch != null && live != null) {
            add("לפתוח את Claude בטרמינל" to { launching = true })
            add("סגירה להיום" to { onLaunch(Launch.NEW, "", "/ydeck:close-day", false) })
        }
        if (onEdit != null) add((if (p.archived) "החזרה מהארכיון" else "העברה לארכיון") to { onEdit(Note.ARCHIVE, if (p.archived) "0" else "1", emptyList()) })
    }
    val runsCard: (@Composable () -> Unit)? = if (onRun != null && live != null) {
        { RunCard(runs, runOn, runOk, memory?.commands.orEmpty(), memory?.commandNames.orEmpty(), memory?.commandsBusy == true, memory?.commandsFound.orEmpty(),
            stuck = pending.any { it.kind == Note.COMMANDS_FIND && System.currentTimeMillis() - it.at > 90_000 },
            onFind = onEdit?.let { e -> { e(com.yonidev.ydeck.link.Note.COMMANDS_FIND, "", emptyList()) } }, onRun = { line -> sentAt = System.currentTimeMillis() - 1_000; onRun(line, com.yonidev.ydeck.data.RunChoice.window(context)) },
            onOpen = { watching = it }) { running = true } }
    } else null
    // Read once here: the page's items are drawn later, and by then going back may have emptied it (a crash, before).
    val insideTab = inside
    if (insideTab != null) {
        androidx.activity.compose.BackHandler { inside = null }
        PageScaffold(title = "מה יש ב־" + p.name, onBack = { inside = null }) {
            InsidePage(p, memory, live, insideTab, { inside = it }, edit, if (canWrite) { kind -> writing = kind } else null, { asking = it },
                runs = runsCard, saveOn = launchOn, filesOn = filesOn)
        }
    } else PageScaffold(
        title = p.name, onBack = onBack,
        actions = {
            if (onAsk != null) IconButton(onClick = onAsk) { AskMark(askMood(chat, false), 22.dp) }
            if (menuItems.isNotEmpty()) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "עוד", tint = c.fg) }
                ItemMenu(menu, { menu = false }, menuItems)
            }
        },
        // Writing to Claude: always in the same place, at the bottom.
        bottomBar = { if (live != null && edit != null) TalkBar(memory) { talking = true } },
    ) {
        item { Moving(p, memory, live, edit) }
        // What needs you: one box, gone when there's nothing.
        val asks = if (onReply != null) waiting else emptyList()
        val needs = memory?.let { needsOf(it, live) }.orEmpty()
        if (asks.isNotEmpty() || needs.isNotEmpty()) item { QuestionLabel("מחכה לך · " + (asks.size + needs.size)) }
        asks.forEach { w ->
            item(key = "w:" + w.id) { WaitingCard(w, p, canReply, replies.lastOrNull { it.first.waiting == w.id }) { text, choices -> onReply?.invoke(w.id, text, choices) } }
        }
        if (needs.isNotEmpty()) item { NeedsOf(needs, memory!!, launchOn, edit) { talking = true } }
        if (memory != null) item {
            QuestionLabel("הלאה")
            PathOf(if (pending.any { it.kind == Note.DONE }) memory.copy(next = "") else memory, if (canWrite) { kind -> writing = kind } else null,
                if (canWrite) { next -> onWrite?.invoke(Note.DONE, next) } else null, edit, { asking = it })
        }
        if (memory != null && memory.talk.isNotEmpty()) item { LastTalk(memory) { talking = true } }
        item { InsideList(p, memory, live, runsCard != null) { inside = it } }
        val listed = pending.filter { it.kind !in com.yonidev.ydeck.data.Overlay.inPlace }
        if (listed.isNotEmpty()) item {
            Text("ממתין למחשב: " + listed.joinToString(" · ") { com.yonidev.ydeck.data.Overlay.words(it) }, color = c.faint, fontSize = 12.5.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        }
        if (memory == null) item {
            Text("לפרויקט הזה עוד אין תיקייה ב־YDeck במחשב. כשמחברים לו תיקייה, יופיעו כאן היעד, מה מחכה לך ומה הלאה.",
                color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(24.dp))
        }
    }
    if (talking && memory != null) TalkSheet(memory, launchOn, edit, onRemote) { talking = false }
    asking?.let { a -> TextSheet(a) { asking = null } }
    if (details && onEdit != null) DetailsSheet(p, { details = false }, onEdit)
    writing?.let { kind -> WriteSheet(kind, onDismiss = { writing = null }, onSend = { k, text -> writing = null; onWrite?.invoke(k, text) }) }
    if (running && onRun != null) RunSheet(runOk, onDismiss = { running = false }) { line, window ->
        running = false; sentAt = System.currentTimeMillis() - 1_000; onRun(line, window)
    }
    val shownRun = watching?.let { id -> runs.lastOrNull { it.first.id == id } } ?: if (sentAt > 0) runs.lastOrNull { it.first.at >= sentAt } else null
    shownRun?.let { (r, n) ->
        RunOutSheet(r, n, runOk, (runOk + memory?.commands.orEmpty()).distinct(),
            onNext = if (onRun != null && runOn) { line -> watching = null; sentAt = System.currentTimeMillis() - 1_000; onRun(line, r.window) } else null,
            onDismiss = { watching = null; sentAt = 0 })
    }
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
        // The mark is the progress: the ring fills with the goal's steps done, the logo inside it.
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
                    Text(if (left == 0) "היעד הושג" else "מהיעד · נשארו $left", color = c.muted, fontSize = 13.sp)
                } else if (about.isNotBlank()) Text(about, color = c.muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (live != null && live.agentMode.isNotBlank() && live.agentMode != "WORKING" && live.agentMode != "WAITING") Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ClaudeDot(live.agentMode, 7.dp)
                    Text(claudeText(live), color = if (live.agentMode == "DONE" || live.agentMode == "OPEN") c.muted else claudeColor(live.agentMode), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // One quiet line under it: what it is, the branch and what's not saved, the server running.
        if (live != null || progress != null) {
            val parts = buildList<@Composable () -> Unit> {
                val about = p.kind
                if (progress != null && about.isNotBlank()) add { Text(about, color = c.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (live != null && live.hasGit && live.branch.isNotBlank()) add { Text(live.branch, color = c.faint, fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1) }
                // One short word for Git here (the Git tab says the rest), so the line never runs out of room.
                if (live != null && live.hasGit) add {
                    val short = when { live.changes == 1 -> "שינוי אחד לא שמור"; live.changes > 1 -> "${live.changes} לא שמורים"; live.ahead > 0 -> "${live.ahead} לדחוף"; else -> "הכול שמור" }
                    Text(short, color = if (live.changes > 0 || live.ahead > 0) c.openText else c.faint, fontSize = 13.sp, maxLines = 1)
                }
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
private fun MemoryTab(m: Memory?, live: Live?, pending: List<Note>, write: ((String) -> Unit)?, onDone: ((String) -> Unit)?,
                      edit: ((String, String, List<String>) -> Unit)? = null, ask: (TextAsk) -> Unit = {},
                      /** The computer lets the phone write to Claude and save (see TalkOf). */
                      working: Boolean = false,
                      onRemote: ((String) -> Unit)? = null) {
    val c = LocalDeck.current
    if (m == null) {
        FootNote("לפרויקט הזה אין עדיין תיקייה ב־YDeck במחשב, אז אין לו זיכרון. כשמחברים לו תיקייה, יופיעו כאן הצעד הבא, הסשן האחרון, ההחלטות ו־Git.")
        return
    }
    Column {
        val listed = pending.filter { it.kind !in com.yonidev.ydeck.data.Overlay.inPlace }
        if (listed.isNotEmpty()) Block("ממתין למחשב") {
            listed.forEachIndexed { i, n ->
                if (i > 0) Hair()
                Text(com.yonidev.ydeck.data.Overlay.words(n), color = c.faint, fontSize = 12.sp)
                if (n.text.isNotBlank() && n.kind != Note.ARCHIVE && n.kind != Note.COLOR) Body(n.text)
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
        if (live != null) TalkOf(m, working, edit, onRemote)
        GoalOf(m, edit)
        SavingOf(m, live, working, edit)
        PathOf(if (closing) m.copy(next = "") else m, write, onDone, edit, ask)
        Moved(m.journal)
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
        if (m.ideas.isNotEmpty() || edit != null) Section(if (m.ideas.isEmpty()) "רעיונות בצד" else "רעיונות בצד · ${m.ideas.size}") {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                m.ideas.forEach { t ->
                    // An idea: tapped, it moves on to what's left, or goes.
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Text(t, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, style = TextStyle(textDirection = dirOf(t)),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.surface2).then(if (edit != null) Modifier.clickable { open = true } else Modifier)
                                .padding(horizontal = 12.dp, vertical = 7.dp))
                        if (edit != null) ItemMenu(open, { open = false }, listOf(
                            "להעביר ל״נשאר״" to { edit(Note.IDEA_TAKE, t, emptyList()) },
                            "מחיקה" to { edit(Note.IDEA_DROP, t, emptyList()) },
                        ), danger = true)
                    }
                }
                if (edit != null) Text("+ רעיון", color = c.accentText, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.line, RoundedCornerShape(50)).clickable {
                        ask(TextAsk("רעיון חדש", "מה הרעיון?") { edit(Note.IDEA_ADD, it, emptyList()) })
                    }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        BuiltOf(m)
        More(m, write)
    }
}

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
private fun PathOf(m: Memory, write: ((String) -> Unit)?, onDone: ((String) -> Unit)?, edit: ((String, String, List<String>) -> Unit)? = null, ask: (TextAsk) -> Unit = {}) {
    val c = LocalDeck.current
    var openDone by remember { mutableStateOf(false) }
    val next = m.next.takeIf { it.isNotBlank() } ?: m.left.firstOrNull()
    val rest = m.left.filter { it != next }
    val hasDone = m.doneItems.isNotEmpty() || m.done.isNotBlank()
    if (next == null && !hasDone && write == null && edit == null) return
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
                        // The next step taken from what's left: done, it moves to what was done.
                        if (!own && next != null && edit != null) SmallAction("בוצע") { edit(Note.LEFT_DONE, next, emptyList()) }
                        if (write != null) SmallAction(if (next != null) "שינוי" else "צעד הבא") { write(Note.NEXT) }
                    }
                    Text(next ?: "עוד אין צעד הבא", color = if (next != null) c.fg else c.faint, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium,
                        style = TextStyle(textDirection = dirOf(next ?: "א")), modifier = Modifier.padding(end = 8.dp))
                }
            }
            rest.forEachIndexed { i, t ->
                // A step left: its ring ticks it off; a long press offers the rest.
                var open by remember { mutableStateOf(false) }
                PathRow(above = c.line, below = if (i < rest.lastIndex || edit != null) c.line else null,
                    node = { Box(if (edit != null) Modifier.clip(CircleShape).clickable { edit(Note.LEFT_DONE, t, emptyList()) }.padding(4.dp) else Modifier) { RingNode() } }) {
                    Box {
                        Text(t, color = c.fg.copy(alpha = 0.86f), fontSize = 15.sp, lineHeight = 22.sp, style = TextStyle(textDirection = dirOf(t)),
                            modifier = Modifier.fillMaxWidth().then(if (edit != null) Modifier.combinedClickable(onClick = { open = true }, onLongClick = { open = true }) else Modifier)
                                .heightIn(min = 44.dp).padding(vertical = 11.dp))
                        if (edit != null) {
                            val at = m.left.indexOf(t)
                            ItemMenu(open, { open = false }, buildList {
                                add("סימון כנעשה" to { edit(Note.LEFT_DONE, t, emptyList()) })
                                add("עריכת הטקסט" to { ask(TextAsk("עריכת שלב", "השלב", t) { new -> edit(Note.LEFT_EDIT, new, listOf(t)) }) })
                                if (at > 0) add("הזזה למעלה" to { edit(Note.LEFT_MOVE, t, listOf("${at - 1}")) })
                                if (at in 0 until m.left.lastIndex) add("הזזה למטה" to { edit(Note.LEFT_MOVE, t, listOf("${at + 1}")) })
                                add("מחיקה" to { edit(Note.LEFT_DROP, t, emptyList()) })
                            }, danger = true)
                        }
                    }
                }
            }
            if (edit != null) PathRow(above = c.line, node = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("+", color = c.accentText, fontSize = 16.sp) } }) {
                Text("הוספת שלב", color = c.accentText, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable {
                    ask(TextAsk("שלב חדש", "מה עוד צריך לעשות?") { edit(Note.LEFT_ADD, it, emptyList()) })
                }.heightIn(min = 44.dp).padding(vertical = 11.dp))
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
    var showFiles by remember { mutableStateOf(false) }
    Column {
        // The ring: closed and green when all is saved and pushed; open, with how many, while something isn't.
        val unsaved = live.changes
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val saved = unsaved == 0 && live.ahead == 0
            ProgressMark(if (saved) 1 else 0, 1, 84.dp) {
                if (unsaved > 0) Text("$unsaved", color = c.fg, fontSize = 22.sp, fontFamily = PlexMono, fontWeight = FontWeight.Medium)
                else if (live.ahead > 0) Text("↑${live.ahead}", color = c.fg, fontSize = 18.sp, fontFamily = PlexMono, fontWeight = FontWeight.Medium)
            }
            Column(Modifier.weight(1f)) {
                val title = when {
                    unsaved == 1 -> "קובץ אחד לא שמור"
                    unsaved > 1 -> "$unsaved קבצים לא שמורים"
                    live.ahead == 1 -> "קומיט אחד לדחוף"
                    live.ahead > 1 -> "${live.ahead} קומיטים לדחוף"
                    else -> "הכול שמור"
                }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString { append(title); pushStyle(androidx.compose.ui.text.SpanStyle(color = if (unsaved == 0 && live.ahead == 0) c.closed else c.open)); append("."); pop() },
                    color = c.fg, fontSize = 23.sp, lineHeight = 30.sp, fontFamily = Display, fontWeight = FontWeight.Light,
                )
                val where = listOfNotNull(
                    when { live.upstream.isBlank() -> "עוד לא בשרת"; live.ahead > 0 && unsaved > 0 -> "${live.ahead} לדחוף"; else -> null },
                    if (live.behind > 0) "${live.behind} חדשים בשרת" else null,
                ).joinToString(" · ")
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⁦${live.branch}⁩", color = c.muted, fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (where.isNotBlank()) { Text("·", color = c.faint, fontSize = 13.sp); Text(where, color = c.faint, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
        // What changed, as one bar: new in green, changed in amber, removed in red. The files themselves on a tap.
        if (live.files.isNotEmpty()) {
            val kinds = live.files.groupingBy { fileKind(it) }.eachCount()
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    FileKind.entries.forEach { k -> kinds[k]?.let { n -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(k.color(c))) } }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { showFiles = !showFiles }.heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FileKind.entries.forEach { k ->
                        kinds[k]?.let { n ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(k.color(c)))
                                Text("$n ${k.word}", color = c.muted, fontSize = 13.sp)
                            }
                        }
                    }
                    Box(Modifier.weight(1f))
                    Text(if (showFiles) "הסתרה" else "הקבצים", color = c.accentText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Chevron(showFiles)
                }
                if (showFiles) Column(Modifier.padding(top = 4.dp)) {
                    Files(live.files)
                    if (live.changes > live.files.size) Text("ועוד ${live.changes - live.files.size}", color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        // What happened: one line in time. Each Claude session a point, the commits that came out of it under it.
        val story = storyOf(live)
        if (story.isNotEmpty()) Section("מה קרה", trailing = "סשנים וקומיטים", top = 26.dp) {
            Column {
                story.forEachIndexed { i, e ->
                    val last = i == story.lastIndex
                    when (e) {
                        is Story.Session -> PathRow(nodeTop = 2.dp, above = null, below = if (last) null else c.line, node = { SessionNode(e) }) {
                            SessionRow(e)
                        }
                        is Story.Commit -> PathRow(nodeTop = 8.dp, below = if (last) null else c.line, node = { Box(Modifier.size(22.dp, 10.dp), contentAlignment = Alignment.Center) { Dot(c.closed, 8.dp) } }) {
                            CommitLine(e.c, Modifier.padding(bottom = 14.dp), dot = false)
                        }
                    }
                }
            }
        }
        if (live.web.isNotBlank()) SmallAction("פתיחת הריפו", Modifier.padding(start = 16.dp, top = 18.dp)) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(live.web))) }
        }
        FootNote("לקריאה בלבד: קומיט, דחיפה ושאר הפעולות נעשים במחשב.")
    }
}

private enum class FileKind(val word: String) {
    ADDED("חדשים"), CHANGED("שונו"), REMOVED("נמחקו"), MOVED("הועברו");
    fun color(c: DeckColors): Color = when (this) { ADDED -> c.closed; CHANGED -> c.open; REMOVED -> c.danger; MOVED -> c.accentText }
}

private fun fileKind(f: LiveFile): FileKind {
    val code = f.code.trim()
    return when {
        f.code == "??" || 'A' in code -> FileKind.ADDED
        'D' in code -> FileKind.REMOVED
        'R' in code -> FileKind.MOVED
        else -> FileKind.CHANGED
    }
}

/** The Git tab's line: sessions (with the commits made while they ran) and commits made outside any, newest first. */
private sealed class Story {
    abstract val at: Long
    class Session(val r: LiveReceipt, val commits: List<com.yonidev.ydeck.link.LiveCommit>) : Story() {
        override val at get() = maxOf(r.endAt, r.startAt, commits.maxOfOrNull { it.at } ?: 0)
        val open get() = r.endAt <= 0
    }
    class Commit(val c: com.yonidev.ydeck.link.LiveCommit) : Story() { override val at get() = c.at }
}

private fun storyOf(live: Live): List<Story> {
    val sessions = live.receipts.sortedByDescending { it.startAt }
    val taken = mutableMapOf<LiveReceipt, MutableList<com.yonidev.ydeck.link.LiveCommit>>()
    val loose = mutableListOf<com.yonidev.ydeck.link.LiveCommit>()
    live.commits.forEach { cm ->
        // A commit belongs to the latest session that had started by then and hadn't ended long before.
        val s = sessions.firstOrNull { it.startAt in 1..cm.at && (it.endAt <= 0 || cm.at <= it.endAt + 30 * 60_000L) }
        if (s != null) taken.getOrPut(s) { mutableListOf() }.add(cm) else loose.add(cm)
    }
    return (sessions.map { Story.Session(it, taken[it].orEmpty().sortedByDescending { c -> c.at }) } + loose.map { Story.Commit(it) })
        .sortedByDescending { it.at }
}

/** A session's point: indigo while it runs, green once what it did was committed, an empty ring when it wasn't. */
@Composable
private fun SessionNode(s: Story.Session) {
    when {
        s.open -> NextNode(true)
        s.commits.isNotEmpty() -> DoneNode()
        else -> Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(LocalDeck.current.bg).border(2.dp, Color(0xFF4A4A52), CircleShape))
        }
    }
}

/** One session: who and when, what it was asked; the files it touched on a tap, its commits under it. */
@Composable
private fun SessionRow(s: Story.Session) {
    val c = LocalDeck.current
    val r = s.r
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = r.files.isNotEmpty()) { open = !open }) {
            Text((r.agent.ifBlank { "Claude" }) + " · " + (if (s.open) "עכשיו" else ago(r.endAt.takeIf { it > 0 } ?: r.startAt)),
                color = if (s.open) c.accentText else c.faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(r.title, color = c.fg, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = dirOf(r.title)), modifier = Modifier.padding(top = 2.dp))
            val files = when (r.files.size) { 0 -> null; 1 -> "קובץ אחד"; else -> "${r.files.size} קבצים" }
            val tags = listOfNotNull(files, if (r.rewound) "הוחזר" else null, if (!s.open && s.commits.isEmpty() && r.files.isNotEmpty()) "לא נשמר בקומיט" else null)
            if (tags.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t ->
                    val warn = t == "לא נשמר בקומיט"
                    Chip(t, color = if (warn) c.openText else c.muted, bg = if (warn) c.open.copy(alpha = 0.12f) else c.surface2)
                }
            }
            if (open) Column(Modifier.padding(top = 8.dp)) { Files(r.files) }
        }
        s.commits.forEach { cm -> CommitLine(cm, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun CommitLine(cm: com.yonidev.ydeck.link.LiveCommit, modifier: Modifier = Modifier, dot: Boolean = true) {
    val c = LocalDeck.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dot) Dot(c.closed, 6.dp)
        Text(cm.subject, color = c.fg.copy(alpha = 0.86f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = dirOf(cm.subject)), modifier = Modifier.weight(1f))
        Text(cm.hash, color = c.faint, fontSize = 11.sp, fontFamily = PlexMono)
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun LinksTab(p: Project, live: Live?) {
    val c = LocalDeck.current
    val context = LocalContext.current
    Column {
        if (p.actions.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp)) {
            Hairline()
            p.actions.forEach { a ->
                Row(Modifier.fillMaxWidth().clickable { Router.open(context, p, a) }.heightIn(min = 58.dp).padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ServiceIcon(a.service, a.faviconHost, 22.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.label, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Router.displayUrl(a.url), color = c.muted, fontSize = 12.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
                    }
                    Text("‹", color = c.faint, fontSize = 18.sp)
                }
                Hairline()
            }
        } else FootNote("אין לפרויקט קישורים. מוסיפים אותם ב־YDeck במחשב.")
        if (p.copies.isNotEmpty()) Section("העתקה בנגיעה") {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.copies.forEach { item ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(c.surface2).clickable {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(item.label, item.value))
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "הועתק", Toast.LENGTH_SHORT).show()
                        }.heightIn(min = 40.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_s_copy), null, tint = c.muted, modifier = Modifier.size(15.dp))
                        Text(item.label, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(item.value, color = c.muted, fontSize = 12.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.widthIn(max = 160.dp))
                    }
                }
            }
        }
        if (live != null && (live.ports.isNotEmpty() || live.missingEnv.isNotEmpty())) Section("במחשב") {
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
        p.googleEmail?.let { FootNote("שירותי גוגל נפתחים בחשבון ⁦$it⁩.") }
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

// ── a command on the computer ──

/** What became of a command, in words, and the dot that says it (amber waits for you there, indigo runs, green ok). */
private fun ranWords(r: com.yonidev.ydeck.link.RunCommand, n: com.yonidev.ydeck.link.Ran?): Pair<String, Color> = when {
    n == null -> (if (System.currentTimeMillis() - r.at > 90_000) "המחשב עוד לא ענה. YDeck פתוח בו?" else "נשלח, מחכה למחשב…") to Color(0xFF8A8A90)
    n.state == com.yonidev.ydeck.link.Ran.ASKING -> "מחכה לאישור במחשב" to CHANGED_DOT
    n.state == com.yonidev.ydeck.link.Ran.RUNNING -> (if (r.window) "רץ במחשב, בחלון טרמינל" else "רץ במחשב, בתוך YDeck") to LIVE_DOT
    n.state == com.yonidev.ydeck.link.Ran.DONE && n.code == 0 -> "הסתיים בהצלחה · ${ago(n.at)}" to CLEAN_DOT
    n.state == com.yonidev.ydeck.link.Ran.DONE -> "נכשל · קוד ${n.code}" to CONFLICT_DOT
    else -> when (n.why) {
        "off" -> "הפקודות מהטלפון כבויות במחשב (הגדרות ← טלפון)"
        "stale" -> "הגיעה מאוחר מדי ולא רצה. אפשר לשלוח שוב."
        "project", "folder" -> "לפרויקט אין תיקייה במחשב"
        "denied" -> "לא אושרה במחשב"
        "timeout" -> "אף אחד לא אישר במחשב תוך 10 דקות"
        else -> "לא רצה במחשב"
    } to Color(0xFF8A8A90)
}

/** "Run a command on the computer": the lines allowed always for one tap, a new one, and the last few, with how they went. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RunCard(runs: List<Pair<com.yonidev.ydeck.link.RunCommand, com.yonidev.ydeck.link.Ran?>>, on: Boolean, ok: List<String>, saved: List<String>, names: List<String>,
                    finding: Boolean, found: String, stuck: Boolean, onFind: (() -> Unit)?, onRun: (String) -> Unit,
                    onOpen: (String) -> Unit, onNew: () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
        // A new one: looks like a field, opens the sheet where it's typed.
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(enabled = on, onClick = onNew).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(">_", color = c.faint, fontSize = 13.sp, fontFamily = PlexMono, style = TextStyle(textDirection = TextDirection.Ltr))
            Text("פקודה חדשה במחשב", color = c.faint, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
            Text("הרצה", color = c.muted, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        if (!on) Text("כבוי במחשב. מדליקים ב־YDeck במחשב ← הגדרות ← טלפון.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
        // The project's commands, each with its name and its line; those allowed always run at once.
        val all = (saved.mapIndexed { i, l -> l to names.getOrElse(i) { "" } } + ok.filter { it !in saved }.map { it to "" })
        if (all.isNotEmpty()) Column(Modifier.padding(top = 14.dp)) {
            Hairline()
            all.forEach { (line, name) ->
                Row(Modifier.fillMaxWidth().clickable(enabled = on) { onRun(line) }.heightIn(min = 60.dp).padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (name.isNotBlank()) Text(name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            else Text(line, color = c.fg, fontSize = 14.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f, fill = false))
                            if (line in ok) Text("· בלי אישור", color = c.closed, fontSize = 11.5.sp, maxLines = 1)
                        }
                        if (name.isNotBlank()) Text(line, color = c.muted, fontSize = 12.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).border(1.dp, c.line, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_s_play), "להריץ", tint = if (on) c.fg else c.faint, modifier = Modifier.size(13.dp))
                    }
                }
                Hairline()
            }
        }
        // Claude looks in the project's folder for more.
        if (onFind != null) {
            val waiting = finding && !stuck
            Row(Modifier.fillMaxWidth().clickable(enabled = !waiting, onClick = onFind).heightIn(min = 48.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✦", color = if (waiting) c.faint else CLAUDE, fontSize = 14.sp)
                Column(Modifier.weight(1f)) {
                    Text(if (waiting) "Claude מחפש פקודות בפרויקט…" else if (all.isEmpty()) "למצוא פקודות עם Claude" else "למצוא עוד פקודות עם Claude",
                        color = if (waiting) c.muted else c.fg, fontSize = 14.sp)
                    if (stuck && finding) Text("המחשב עוד לא ענה. YDeck פתוח בו?", color = c.openText, fontSize = 12.sp)
                }
                if (!finding && found.isNotBlank()) Text(found, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The last few, newest first: the line, and how it went.
        if (runs.isNotEmpty()) Column(Modifier.padding(top = 14.dp)) {
            Text("רצו לאחרונה", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            Hairline()
            runs.takeLast(3).reversed().forEach { (r, n) ->
                val (words, _) = ranWords(r, n)
                Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.heightIn(min = 44.dp).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(r.line, color = c.fg, fontSize = 12.5.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
                    Text(words, color = if (n?.state == com.yonidev.ydeck.link.Ran.ASKING) c.openText else c.muted, fontSize = 12.sp, maxLines = 1)
                }
                Hairline()
            }
        }
    }
}

/** One of the project's commands: a tap runs it (green: allowed always, at once; grey: after approval on the computer). */
@Composable
private fun CommandChip(line: String, ok: Boolean, onRun: () -> Unit) {
    val c = LocalDeck.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.surface2).clickable(onClick = onRun).heightIn(min = 36.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_s_play), null, tint = if (ok) c.closed else c.faint, modifier = Modifier.size(13.dp))
        Text(line, color = c.fg, fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.widthIn(max = 220.dp))
    }
}

/** Typing a command for the computer: one line, as it will run there, in the project's folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunSheet(ok: List<String>, onDismiss: () -> Unit, onSend: (line: String, window: Boolean) -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    var line by remember { mutableStateOf("") }
    var window by remember { mutableStateOf(com.yonidev.ydeck.data.RunChoice.window(context)) }
    val clean = com.yonidev.ydeck.link.RunCommand.clean(line)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp)) {
            Text("פקודה במחשב", color = c.fg, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.Light)
            Text("רצה בתיקיית הפרויקט, והפלט מגיע לכאן תוך כדי. במחשב בוחרים: פעם אחת, תמיד, או לא.",
                color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 14.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 16.sp, fontFamily = PlexMono, textDirection = TextDirection.Ltr)
                if (line.isEmpty()) Text("npm run build", style = style.copy(color = c.faint))
                BasicTextField(line, { line = it.replace("\n", " ").take(com.yonidev.ydeck.link.RunCommand.MAX) }, textStyle = style, singleLine = true,
                    cursorBrush = SolidColor(c.fg), modifier = Modifier.fillMaxWidth())
            }
            // Where it runs there; remembered for next time (and for the lines allowed always).
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(3.dp)) {
                listOf(false to "בתוך YDeck", true to "בחלון חדש").forEach { (w, label) ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (window == w) c.surface else Color.Transparent)
                        .clickable { window = w; com.yonidev.ydeck.data.RunChoice.setWindow(context, w) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (window == w) c.fg else c.muted, fontSize = 14.sp, fontWeight = if (window == w) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            Text(if (window) "נפתח חלון טרמינל במחשב ונשאר פתוח, ואפשר להקליד בו שם." else "בלי חלון במחשב. מתאים לפקודה שלא שואלת שאלות באמצע.",
                color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            if (ok.isNotEmpty()) Text("מאושרות תמיד: " + ok.joinToString(" · "), color = c.faint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp))
            Button(
                onClick = { clean?.let { onSend(it, window) } },
                enabled = clean != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.fg, contentColor = c.bg, disabledContainerColor = c.surface2, disabledContentColor = c.faint),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
            ) { Text(if (clean != null && clean in ok) "הרצה במחשב" else "שליחה לאישור במחשב", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/**
 * One command's page: the line, how it's going, and its output as it comes on the computer (over the live line, see
 * LiveOut). After it ended, and when the line can't be had, the last lines the state carries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunOutSheet(r: com.yonidev.ydeck.link.RunCommand, n: com.yonidev.ydeck.link.Ran?, ok: List<String>, chips: List<String>,
                        onNext: ((String) -> Unit)?, onDismiss: () -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    val live by com.yonidev.ydeck.data.LiveOut.state.collectAsState()
    val started = n?.state == com.yonidev.ydeck.link.Ran.RUNNING || n?.state == com.yonidev.ydeck.link.Ran.DONE
    DisposableEffect(r.id, started) {
        if (started) com.yonidev.ydeck.data.LiveOut.watch(context, r.id)
        onDispose { com.yonidev.ydeck.data.LiveOut.unwatch(r.id) }
    }
    val seen = live[r.id]
    // The line said it ended before the state did: said so at once.
    val now = if (seen?.done == true && n?.state == com.yonidev.ydeck.link.Ran.RUNNING) n.copy(state = com.yonidev.ydeck.link.Ran.DONE, code = seen.code, at = System.currentTimeMillis()) else n
    val (words, dot) = ranWords(r, now)
    val text = seen?.text?.takeIf { it.isNotEmpty() } ?: n?.tail.orEmpty()
    val scroll = rememberScrollState()
    LaunchedEffect(text.length) { scroll.animateScrollTo(scroll.maxValue) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        // As on the computer: one card, the dot and what's going on, the line in a terminal-like block, its output under it.
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(dot, 8.dp)
                Text(words, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (r.window) "בחלון חדש" else "בתוך YDeck", color = c.faint, fontSize = 12.sp)
            }
            val done = now?.state == com.yonidev.ydeck.link.Ran.DONE
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(12.dp))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text("> " + r.line, color = c.fg, fontSize = 14.sp, fontFamily = PlexMono, style = TextStyle(textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 440.dp).verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(text.trimEnd().ifEmpty { if (done) "(אין פלט)" else "…" } + if (done) "\n— exit ${now?.code ?: 0}" else "",
                            color = if (text.isBlank()) c.faint else c.fg, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = PlexMono,
                            style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done && text.isNotBlank()) {
                    var copied by remember { mutableStateOf(false) }
                    Text(if (copied) "הועתק" else "העתקה", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.surface2).clickable {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("YDeck", text))
                            copied = true
                        }.padding(horizontal = 16.dp, vertical = 10.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(when {
                    !done -> "במחשב, בזמן אמת"
                    seen == null && text.isNotEmpty() -> "${com.yonidev.ydeck.link.Ran.TAIL_LINES} השורות האחרונות"
                    else -> ""
                }, color = c.faint, fontSize = 12.sp)
            }
            // The next one, from here: typed, or one of the project's commands; it opens in this same page.
            if (onNext != null) NextCommand(ok, chips.filterNot { it == r.line }, onNext)
        }
    }
}

/** Under a command's output: a line for the next command and the project's commands, so one follows another. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NextCommand(ok: List<String>, chips: List<String>, onRun: (String) -> Unit) {
    val c = LocalDeck.current
    var line by remember { mutableStateOf("") }
    val clean = com.yonidev.ydeck.link.RunCommand.clean(line)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = PlexMono, textDirection = TextDirection.Ltr)
                if (line.isEmpty()) Text("הפקודה הבאה", style = style.copy(color = c.faint, fontFamily = PlexSans, textDirection = TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
                BasicTextField(line, { line = it.replace('\n', ' ').take(com.yonidev.ydeck.link.RunCommand.MAX) }, textStyle = style, singleLine = true,
                    cursorBrush = SolidColor(c.fg), modifier = Modifier.fillMaxWidth())
            }
            Text(if (clean != null && clean in ok) "הרצה" else "לאישור", color = if (clean != null) c.bg else c.faint, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (clean != null) c.fg else c.line)
                    .clickable(enabled = clean != null) { clean?.let { onRun(it); line = "" } }.padding(horizontal = 16.dp, vertical = 9.dp))
        }
        if (chips.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips.take(12).forEach { l -> CommandChip(l, l in ok) { onRun(l) } } }
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

/**
 * What moved, day by day (the computer keeps it, see JournalDay): today, yesterday, then dates. Each day in a line or
 * two: what was ticked off, what was added, new ideas, then quietly the commits and Claude's sessions.
 */
@Composable
private fun Moved(days: List<com.yonidev.ydeck.link.JournalDay>) {
    val c = LocalDeck.current
    if (days.isEmpty()) return
    Section("מה התקדם", trailing = "השבוע") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            days.take(5).forEach { d ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(dayName(d.date), color = c.faint, fontSize = 12.5.sp, modifier = Modifier.width(52.dp).padding(top = 2.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        d.done.take(4).forEach { MovedLine("✓", it, c.closed) }
                        d.added.take(3).forEach { MovedLine("+", it, c.muted) }
                        d.ideas.take(2).forEach { MovedLine("◦", it, c.accentText) }
                        val quiet = buildList {
                            if (d.commits.isNotEmpty()) add(if (d.commits.size == 1) "commit אחד" else "${d.commits.size} commits")
                            if (d.sessions.isNotEmpty()) add(if (d.sessions.size == 1) "סשן Claude אחד" else "${d.sessions.size} סשנים של Claude")
                        }
                        if (quiet.isNotEmpty()) Text(quiet.joinToString(" · "), color = c.faint, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovedLine(mark: String, text: String, color: androidx.compose.ui.graphics.Color) {
    val c = LocalDeck.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(mark, color = color, fontSize = 13.sp)
        Text(text, color = c.fg.copy(alpha = 0.86f), fontSize = 14.sp, lineHeight = 20.sp, style = TextStyle(textDirection = dirOf(text)))
    }
}

private fun dayName(date: String): String {
    val d = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return date
    val today = java.time.LocalDate.now()
    return when (d) {
        today -> "היום"
        today.minusDays(1) -> "אתמול"
        else -> "${d.dayOfMonth}.${d.monthValue}"
    }
}


// ── the project page, in four questions ──

@Composable
private fun QuestionLabel(text: String) {
    Text(text, style = SectionLabelStyle, color = LocalDeck.current.faint, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 10.dp))
}

/** Steps ticked off in the last seven days, from the journal. */
private fun movedThisWeek(m: Memory): Int {
    val from = java.time.LocalDate.now().minusDays(6)
    return m.journal.filter { runCatching { !java.time.LocalDate.parse(it.date).isBefore(from) }.getOrDefault(false) }.sumOf { it.done.size }
}

/**
 * How is it moving: Claude's state, YDeck's line on the project, and the goal as a bar of its steps (tap: the steps,
 * ticked here), with what moved this week (tap: day by day). No goal: Claude's suggestion, or a way to ask for one.
 */
@Composable
private fun Moving(p: Project, m: Memory?, live: Live?, edit: ((String, String, List<String>) -> Unit)?) {
    val c = LocalDeck.current
    var steps by rememberSaveable { mutableStateOf(false) }
    var week by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(p, 34.dp)
            val mode = live?.agentMode.orEmpty()
            if (mode.isNotBlank() && mode != "DONE") {
                ClaudeDot(mode, 7.dp)
                Text(claudeText(live!!.copy(agentRemote = false)), color = if (mode == "OPEN") c.muted else claudeColor(mode), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            } else Text(listOfNotNull(p.kind.takeIf { it.isNotBlank() }, live?.version?.takeIf { it.isNotBlank() }?.let { "גרסה ⁦$it⁩" }).joinToString(" · "),
                color = c.muted, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        m ?: return@Column
        val line = m.insight.ifBlank { m.now }.trim().trimEnd('.')
        if (line.isNotBlank()) Text(
            androidx.compose.ui.text.buildAnnotatedString { append(line); pushStyle(androidx.compose.ui.text.SpanStyle(color = c.open)); append("."); pop() },
            color = c.fg, fontSize = 24.sp, lineHeight = 32.sp, fontFamily = Display, fontWeight = FontWeight.Light, style = TextStyle(textDirection = dirOf(line)),
        )
        val share = m.goalShare ?: return@Column
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { steps = !steps }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.goal, color = c.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = TextStyle(textDirection = dirOf(m.goal)))
                Text("${share.first}/${share.second}", color = c.muted, fontSize = 13.sp, fontFamily = PlexMono, style = TextStyle(textDirection = TextDirection.Ltr))
            }
            val next = m.goalSteps.indexOfFirst { !it.done }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                m.goalSteps.forEachIndexed { i, s ->
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(when { s.done -> c.closed; i == next -> claudeColor("WORKING"); else -> c.line }))
                }
            }
        }
        if (steps) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val next = m.goalSteps.indexOfFirst { !it.done }
            m.goalSteps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .then(if (edit != null) Modifier.clickable { edit(Note.GOAL_STEP, s.text, listOf(if (s.done) "0" else "1")) } else Modifier)
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        s.done -> Box(Modifier.size(16.dp).clip(CircleShape).background(c.closed))
                        i == next -> Box(Modifier.size(16.dp).clip(CircleShape).border(2.dp, claudeColor("WORKING"), CircleShape))
                        else -> Box(Modifier.size(16.dp).clip(CircleShape).border(2.dp, c.line, CircleShape))
                    }
                    Text(s.text, color = if (s.done) c.faint else c.fg, fontSize = 15.sp, style = TextStyle(textDirection = dirOf(s.text)))
                }
            }
            if (edit != null) Text("נוגעים בצעד כדי לסמן אותו.", color = c.faint, fontSize = 12.sp)
        }
        val moved = movedThisWeek(m)
        Text(if (moved > 0) "השבוע: ${if (moved == 1) "צעד אחד" else "$moved צעדים"} קדימה" else "השבוע: עוד לא זז",
            color = if (moved > 0) c.closed else c.faint, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = m.journal.isNotEmpty()) { week = !week }.padding(vertical = 2.dp))
        if (week) Moved(m.journal)
    }
    if (m != null && m.goalShare == null) GoalOf(m, edit)
}

/** One thing waiting for you in this project (besides a session's question, shown as its own card). */
private sealed interface Need
private data object PermissionNeed : Need
private data object SaveNeed : Need

private fun needsOf(m: Memory, live: Live?): List<Need> = buildList {
    if (m.talkAsk.isNotBlank()) add(PermissionNeed)
    if (live?.hasGit == true && (live.changes > 0 || live.ahead > 0 || live.behind > 0)) add(SaveNeed)
}

/** What needs you, each with its one button. */
@Composable
private fun NeedsOf(needs: List<Need>, m: Memory, working: Boolean, edit: ((String, String, List<String>) -> Unit)?, onTalk: () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        needs.forEach { n ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface2).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (n) {
                    PermissionNeed -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Dot(c.open, 8.dp)
                            Text("Claude מבקש: " + m.talkAsk, color = c.fg, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable(onClick = onTalk), style = TextStyle(textDirection = TextDirection.Content))
                        }
                        if (working && edit != null && m.talkAskId.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("לאשר", strong = true, modifier = Modifier.weight(1f)) { edit(Note.TALK_ALLOW, m.talkAskId, listOf("once")) }
                            if (m.talkAskAlways.isNotBlank()) PillButton("תמיד") { edit(Note.TALK_ALLOW, m.talkAskId, listOf("always")) }
                            PillButton("לא", quiet = true) { edit(Note.TALK_ALLOW, m.talkAskId, listOf("no")) }
                        }
                    }
                    SaveNeed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Dot(c.open, 8.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.saving.ifBlank { "שינויים לא שמורים" }.let { firstSentence(it) }, color = c.fg, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(textDirection = TextDirection.Content))
                            when {
                                m.savingResult.isNotBlank() && m.savingResult != "ok" -> Text(m.savingResult, color = c.danger, fontSize = 12.5.sp, maxLines = 2)
                                m.savingProposal.isNotBlank() -> Text(m.savingProposal, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                            }
                        }
                        if (working && edit != null && m.savingSteps.isNotBlank()) PillButton(if (m.savingBusy) "עובד…" else "לעשות", enabled = !m.savingBusy) { edit(Note.SAVE_DO, "", emptyList()) }
                    }
                }
            }
        }
    }
}

/** The first sentence of a card's words, so it reads at a glance. */
private fun firstSentence(t: String): String = t.trim().split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim().orEmpty().ifBlank { t }

@Composable
private fun PillButton(text: String, strong: Boolean = false, quiet: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalDeck.current
    Box(modifier.clip(CircleShape).background(when { strong -> c.fg; quiet -> androidx.compose.ui.graphics.Color.Transparent; else -> c.line })
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = when { !enabled -> c.faint; strong -> c.bg; quiet -> c.muted; else -> c.fg }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** The last word in the conversation with Claude here; a tap opens it. */
@Composable
private fun LastTalk(m: Memory, onOpen: () -> Unit) {
    val c = LocalDeck.current
    val last = m.talk.lastOrNull { it.kind != com.yonidev.ydeck.link.MemTalk.STEP } ?: return
    QuestionLabel("השיחה עם Claude")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (last.kind == com.yonidev.ydeck.link.MemTalk.USER) "אתה:" else "Claude:", color = c.faint, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Text(last.text.replace(Regex("[*#`]"), ""), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f), style = TextStyle(textDirection = dirOf(last.text)))
        if (m.talkBusy) Text("עובד…", color = claudeColor("WORKING"), fontSize = 12.5.sp)
    }
}

/** One line that opens everything else: what was built, ideas, decisions, links, Git, commands. */
@Composable
private fun InsideRow(m: Memory?, live: Live?, onOpen: () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.padding(top = 22.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("מה יש בפרויקט", color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
            val counts = listOfNotNull(
                m?.built?.size?.takeIf { it > 0 }?.let { "$it נבנה" },
                m?.ideas?.size?.takeIf { it > 0 }?.let { "$it רעיונות" },
                m?.decisions?.size?.takeIf { it > 0 }?.let { "$it החלטות" },
            )
            if (counts.isNotEmpty()) Text(counts.joinToString(" · "), color = c.muted, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text("‹", color = c.faint, fontSize = 20.sp)
        }
    }
}

/** The bar at the bottom: writing to Claude, one tap away from anywhere on the page. */
@Composable
private fun TalkBar(m: Memory?, onOpen: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(c.surface2).clickable(onClick = onOpen).padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✳", color = androidx.compose.ui.graphics.Color(0xFFD97757), fontSize = 16.sp)
            Text(if (m?.talkBusy == true) "Claude עובד…" else "לכתוב ל־Claude", color = if (m?.talkBusy == true) claudeColor("WORKING") else c.faint, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) { Text("↑", color = c.bg, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/** The conversation with Claude in this project, full height. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun TalkSheet(m: Memory, on: Boolean, edit: ((String, String, List<String>) -> Unit)?, onRemote: ((String) -> Unit)?, onDismiss: () -> Unit) {
    val c = LocalDeck.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).imePadding().padding(bottom = 24.dp)) {
            TalkOf(m, on, edit, onRemote)
        }
    }
}

/** Everything else in the project, by kind: what was built, ideas, decisions, links, Git, commands. */
private fun androidx.compose.foundation.lazy.LazyListScope.InsidePage(
    p: Project, m: Memory?, live: Live?, tab: String, onTab: (String) -> Unit,
    edit: ((String, String, List<String>) -> Unit)?, write: ((String) -> Unit)?, ask: (TextAsk) -> Unit,
    runs: (@Composable () -> Unit)?,
    saveOn: Boolean = false,
    filesOn: Boolean = false,
) {
    item {
        val c = LocalDeck.current
        m?.summary?.takeIf { it.isNotBlank() }?.let { t ->
            var more by remember { mutableStateOf(false) }
            val long = t.length > 110
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).then(if (long) Modifier.clickable { more = !more } else Modifier)) {
                Text(t, color = c.muted, fontSize = 13.5.sp, lineHeight = 20.sp, maxLines = if (more || !long) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = dirOf(t)))
                if (long) Text(if (more) "פחות" else "עוד", color = c.fg, fontSize = 13.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        UnderlineTabs(insideTabs(live, runs != null), tab, onTab)
    }
    when (tab) {
        "built" -> item {
            if (m != null && m.built.isNotEmpty()) BuiltOf(m)
            else Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("עוד לא מולא. Claude יכול לקרוא את הפרויקט ולמלא מה הוא כבר יודע לעשות.", color = LocalDeck.current.muted, fontSize = 14.sp, lineHeight = 21.sp)
                if (edit != null) PillButton("למלא עכשיו", strong = true) { edit(Note.GOAL_SUGGEST, "", emptyList()) }
            }
        }
        "ideas" -> item { if (m != null) IdeasOf(m, edit, ask) }
        "decisions" -> item { if (m != null) { Decisions(m, write); m.releases.firstOrNull()?.let { r -> Block("גרסה אחרונה") { Body(r.version + (if (r.note.isNotBlank()) " · " + r.note else "")) } } } }
        "links" -> item { LinksTab(p, live) }
        "git" -> item { GitPane(live, m, saveOn, edit) }
        "runs" -> item { runs?.invoke() }
        "files" -> item { FilesPane(p.id, p.name, filesOn) }
    }
}

/** The ideas set aside: tapped, one moves on to what's left, or goes. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun IdeasOf(m: Memory, edit: ((String, String, List<String>) -> Unit)?, ask: (TextAsk) -> Unit) {
    val c = LocalDeck.current
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        m.ideas.forEach { t ->
            var open by remember { mutableStateOf(false) }
            Box {
                Text(t, color = c.fg, fontSize = 14.sp, lineHeight = 19.sp, style = TextStyle(textDirection = dirOf(t)),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.surface2).then(if (edit != null) Modifier.clickable { open = true } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 8.dp))
                if (edit != null) ItemMenu(open, { open = false }, listOf(
                    "להעביר ל״נשאר״" to { edit(Note.IDEA_TAKE, t, emptyList()) },
                    "מחיקה" to { edit(Note.IDEA_DROP, t, emptyList()) },
                ), danger = true)
            }
        }
        if (edit != null) Text("+ רעיון", color = c.accentText, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.line, RoundedCornerShape(50)).clickable {
                ask(TextAsk("רעיון חדש", "מה הרעיון?") { edit(Note.IDEA_ADD, it, emptyList()) })
            }.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

// ── What's in the project: a list of lines, and inside, tabs of words ──

private val INSIDE = listOf("runs" to "פקודות", "files" to "קבצים", "git" to "Git", "links" to "קישורים", "built" to "נבנה", "ideas" to "רעיונות", "decisions" to "החלטות")

private fun insideTabs(live: Live?, hasRuns: Boolean) = INSIDE.filter { (k, _) -> when (k) { "runs" -> hasRuns; "files" -> live != null; "git" -> live?.hasGit == true; else -> true } }

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDeck.current.line))

/** "What's in the project": one line each (its name, what's in it in a few words, how many), a tap opens it. */
@Composable
private fun InsideList(p: Project, m: Memory?, live: Live?, hasRuns: Boolean, onOpen: (String) -> Unit) {
    val c = LocalDeck.current
    QuestionLabel("מה יש בפרויקט")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Hairline()
        insideTabs(live, hasRuns).forEach { (k, label) ->
            when (k) {
                "runs" -> {
                    val lines = m?.commands.orEmpty()
                    InsideLine(label, lines.size, { onOpen(k) }) {
                        if (lines.isEmpty()) Peek("עוד אין. Claude יכול למצוא")
                        else Peek(lines.take(3).joinToString(" · "), mono = true)
                    }
                }
                "git" -> InsideLine(label, null, { onOpen(k) }) {
                    val l = live!!
                    val clean = l.changes == 0 && l.ahead == 0
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (clean) c.closed else c.open))
                    Text(when {
                        l.changes == 1 -> "קובץ אחד לא שמור"
                        l.changes > 1 -> "${l.changes} לא שמורים"
                        l.ahead > 0 -> "↑${l.ahead} להעלאה"
                        else -> "הכול שמור"
                    }, color = if (clean) c.muted else c.openText, fontSize = 13.sp, maxLines = 1)
                    if (l.branch.isNotBlank()) {
                        Text("·", color = c.faint, fontSize = 13.sp)
                        Peek(l.branch, mono = true)
                    }
                }
                "files" -> InsideLine(label, null, { onOpen(k) }) { Peek("התיקייה במחשב, לצפייה") }
                "links" -> InsideLine(label, p.actions.size, { onOpen(k) }) { Peek(p.actions.take(3).joinToString(" · ") { it.label }.ifBlank { "אין עדיין" }) }
                "built" -> InsideLine(label, m?.built?.size ?: 0, { onOpen(k) }) { Peek(m?.built?.firstOrNull() ?: "עוד לא מולא") }
                "ideas" -> InsideLine(label, m?.ideas?.size ?: 0, { onOpen(k) }) { Peek(m?.ideas?.take(2)?.joinToString(" · ")?.ifBlank { null } ?: "אין עדיין") }
                "decisions" -> InsideLine(label, m?.decisions?.size ?: 0, { onOpen(k) }) { Peek(m?.decisions?.firstOrNull()?.text?.let { "אחרונה: $it" } ?: "אין עדיין") }
            }
        }
    }
}

@Composable
private fun InsideLine(label: String, count: Int?, onClick: () -> Unit, peek: @Composable RowScope.() -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.width(74.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), content = peek)
        Text(count?.takeIf { it > 0 }?.toString().orEmpty(), color = c.muted, fontSize = 13.sp, fontFamily = PlexMono,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(22.dp))
        Text("‹", color = c.faint, fontSize = 18.sp)
    }
    Hairline()
}

@Composable
private fun RowScope.Peek(t: String, mono: Boolean = false) {
    val c = LocalDeck.current
    Text(t, color = c.muted, fontSize = if (mono) 12.5.sp else 13.sp, fontFamily = if (mono) PlexMono else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(textDirection = if (mono) TextDirection.Ltr else dirOf(t)), modifier = Modifier.weight(1f, fill = false))
}

/** The tabs inside: words in one line, all of them in sight, the chosen one underlined. */
@Composable
private fun UnderlineTabs(tabs: List<Pair<String, String>>, on: String, onTab: (String) -> Unit) {
    val c = LocalDeck.current
    // More tabs than fit: the row scrolls sideways, the chosen one comes into the middle, and the edges fade so the
    // rest shows it's there.
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val centers = remember { mutableMapOf<String, Pair<Int, Int>>() }
    var width by remember { mutableStateOf(0) }
    val rtlNow = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    LaunchedEffect(on, width, scroll.maxValue) {
        val (x, w) = centers[on] ?: return@LaunchedEffect
        // x is from the content's left; in RTL the scroll is counted from its right.
        val fromLeft = x + w / 2 - width / 2
        val target = if (rtlNow) scroll.maxValue - fromLeft else fromLeft
        scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
    }
    val fade = with(density) { 28.dp.toPx() }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth()
            .onSizeChanged { width = it.width }
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // In RTL the row starts at the right: the start fades when scrolled from it, the end while there's more.
                val startFade = if (scroll.value > 0) fade else 0f
                val endFade = if (scroll.value < scroll.maxValue) fade else 0f
                val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                val (left, right) = if (rtl) endFade to startFade else startFade to endFade
                if (left > 0) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = left), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                if (right > 0) drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - right, endX = size.width), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            }
            .horizontalScroll(scroll)
            .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            tabs.forEach { (k, label) ->
                val sel = k == on
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    .onGloballyPositioned { centers[k] = it.positionInParent().x.toInt() to it.size.width }
                    .clickable { onTab(k) }.padding(top = 12.dp)) {
                    Text(label, color = if (sel) c.fg else c.muted, fontSize = 14.5.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.padding(bottom = 9.dp, start = 2.dp, end = 2.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (sel) c.fg else Color.Transparent))
                }
            }
        }
        Box(Modifier.padding(horizontal = 24.dp)) { Hairline() }
    }
}

/** Claude's card in Git: what it suggests, what it sees, and "לעשות" for the steps YDeck can run itself. */
@Composable
private fun ClaudeSuggests(m: Memory, on: Boolean, edit: ((String, String, List<String>) -> Unit)?) {
    val c = LocalDeck.current
    val busy = m.savingBusy
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface2).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✳", color = androidx.compose.ui.graphics.Color(0xFFD97757), fontSize = 15.sp)
            Text("Claude מציע", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        when {
            m.savingProposal.isNotBlank() -> Text(m.savingProposal, color = c.fg, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium,
                style = TextStyle(textDirection = TextDirection.Content))
            busy -> Text("Claude מסתכל על מה שפתוח…", color = c.muted, fontSize = 14.sp)
        }
        if (m.saving.isNotBlank()) Text(m.saving, color = c.muted, fontSize = 13.5.sp, lineHeight = 20.sp, style = TextStyle(textDirection = TextDirection.Content))
        if (m.savingSteps.isNotBlank() && edit != null && on) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (busy) c.line else c.fg)
                .clickable(enabled = !busy) { edit(Note.SAVE_DO, "", emptyList()) }, contentAlignment = Alignment.Center) {
                Text(if (busy) "עובד…" else "לעשות", color = if (busy) c.muted else c.bg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(m.savingSteps, color = c.faint, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (m.savingSteps.isNotBlank() && edit != null && !on) Text("כדי לעשות את זה מכאן, מדליקים במחשב: הגדרות ← טלפון.", color = c.faint, fontSize = 12.sp)
        when {
            m.savingResult == "ok" -> Text("נעשה.", color = c.closed, fontSize = 13.sp)
            m.savingResult.isNotBlank() -> Text(m.savingResult, color = c.danger, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

/** Git inside: where saving stands in words, the files, one button to save and push, the last commits. */
@Composable
private fun GitPane(live: Live?, m: Memory?, on: Boolean, edit: ((String, String, List<String>) -> Unit)?) {
    val c = LocalDeck.current
    val context = LocalContext.current
    if (live == null || !live.hasGit) {
        FootNote(if (live == null) "לפרויקט הזה אין תיקייה ב־YDeck במחשב." else "התיקייה של הפרויקט הזה עוד לא ריפו של Git.")
        return
    }
    val clean = live.changes == 0 && live.ahead == 0 && live.behind == 0
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (clean) c.closed else c.open))
                Text(when {
                    live.changes == 1 -> "קובץ אחד לא שמור"
                    live.changes > 1 -> "${live.changes} קבצים לא שמורים"
                    live.ahead == 1 -> "שמירה אחת לא הועלתה"
                    live.ahead > 1 -> "${live.ahead} שמירות לא הועלו"
                    live.behind == 1 -> "שמירה אחת חדשה בשרת"
                    live.behind > 1 -> "${live.behind} שמירות חדשות בשרת"
                    else -> "הכול שמור ומועלה"
                }, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            val where = listOfNotNull(
                live.branch.takeIf { it.isNotBlank() }?.let { "בענף ⁦$it⁩" },
                when { live.upstream.isBlank() -> "עוד לא ב־GitHub"; live.changes > 0 && live.ahead == 1 -> "שמירה אחת מחכה להעלאה"; live.changes > 0 && live.ahead > 1 -> "${live.ahead} שמירות מחכות להעלאה"; else -> null },
                if (live.behind > 0) "${live.behind} חדשות בשרת" else null,
            ).joinToString(" · ")
            if (where.isNotBlank()) Text(where, color = c.muted, fontSize = 13.sp)
        }
        if (live.files.isNotEmpty()) Column {
            Hairline()
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                live.files.take(8).forEach { f ->
                    val (letter, tint) = when {
                        f.code.contains('?') || f.code.contains('A') -> "A" to c.closed
                        f.code.contains('D') -> "D" to c.danger
                        f.code.contains('R') -> "R" to c.muted
                        else -> "M" to c.openText
                    }
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = tint)); append(letter); pop()
                        append("  " + f.path)
                    }, color = c.fg, fontSize = 12.5.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
                }
                if (live.changes > 8) Text("ועוד ${live.changes - 8}", color = c.faint, fontSize = 12.sp)
            }
            Hairline()
        }
        // What Claude suggests doing next (written on the computer from what git says), and one button that does it.
        if (m != null && (m.savingProposal.isNotBlank() || m.savingSteps.isNotBlank() || (!clean && (m.saving.isNotBlank() || m.savingBusy))))
            ClaudeSuggests(m, on, edit)
        if (live.commits.isNotEmpty()) Column {
            Text("שמירות אחרונות", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            Hairline()
            live.commits.take(5).forEach { k ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Text(k.subject, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = dirOf(k.subject)), modifier = Modifier.weight(1f))
                    if (k.at > 0) Text(ago(k.at), color = c.faint, fontSize = 12.sp, maxLines = 1)
                }
                Hairline()
            }
        }
        if (live.web.isNotBlank()) Text("פתיחה ב־GitHub ‹", color = c.fg, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(live.web))) } }
                .padding(vertical = 10.dp))
    }
}
