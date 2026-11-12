package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * A project's home: its header (logo, name, where it stands, the editor and Claude) and its tabs. Overview is where
 * you pick up from, Git what changed, Links the consoles; Files and Commands show once there's something in them, and
 * are always in the menu (ProjectTabs.kt).
 */

private val CLAUDE_ORANGE = Color(0xFFD97757)

@Composable
fun ProjectPage(p: Project, tab: Tab, dark: Boolean, actions: AppActions) {
    val c = LocalDeck.current
    val local = Settings.state.collectAsState().value.localOf(p.id)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 44.dp, end = 44.dp, top = 28.dp)) {
            Header(p, local, actions)
            if (p.archived) { Spacer(Modifier.height(16.dp)); ArchivedNote(p) }
            Spacer(Modifier.height(20.dp))
            Tabs(p, tab, local)
        }
        Box(Modifier.fillMaxSize().background(c.bg)) {
            when (tab) {
                Tab.OVERVIEW -> Overview(p, local, dark, actions)
                Tab.GIT -> GitTab(p, local)
                Tab.LINKS -> LinksTab(p, dark)
                Tab.FILES -> FilesTab(p, local, actions)
                Tab.COMMANDS -> CommandsTab(p, local, dark)
            }
        }
    }
}

@Composable
private fun Header(p: Project, local: Local, actions: AppActions) {
    val dir = local.dir
    val c = LocalDeck.current
    // The mark is the progress: the ring fills with what was done out of what was planned, the logo inside it.
    val progress = dir?.let { Pulses.state.collectAsState().value[it.path] }?.progress()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable { Nav.set(Screen.Details(p.id)) }) {
            if (progress != null) ProgressMark(progress.first, progress.second, 88.dp) { Logo(p, 38.dp) } else Logo(p, 56.dp)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PageTitle(p.name, 34)
                if (progress != null) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("${progress.first}")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.faint)); append("/${progress.second}"); pop()
                        },
                        color = c.fg, fontSize = 20.sp, fontFamily = PlexMono, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp),
                        style = TextStyle(textDirection = TextDirection.Ltr),
                    )
                    val left = progress.second - progress.first
                    Text(if (left == 0) tr("all done", "הכול נעשה") else tr("done · $left left", "נעשו · נשארו $left"), color = c.muted, fontSize = 13.sp,
                        fontFamily = PlexSans, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            StatusLine(p, local)
        }
        Spacer(Modifier.width(12.dp))
        if (dir != null) {
            val editor = Tools.quick(dir).getOrNull(1)?.takeIf { it.kind != ToolKind.AGENT && it.kind != ToolKind.TERMINAL }
            if (editor != null) { Pill(editor.label.removePrefix("Android "), Tone.SOFT, Glyph.CODE) { Tools.launch(editor, dir, p.name) }; Spacer(Modifier.width(8.dp)) }
            Tools.claude?.let { claude -> Pill("Claude", Tone.DARK, Glyph.CLAUDE, CLAUDE_ORANGE) { Tools.launch(claude, dir, p.name) }; Spacer(Modifier.width(8.dp)) }
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(Glyph.MORE, tr("More", "עוד")) { menu = true }
            DropdownMenu(menu, { menu = false }) {
                // The Claude button picks up the last conversation; a fresh one is here.
                if (dir != null) Tools.claude?.let { claude ->
                    MenuItem(Glyph.CLAUDE, tr("New Claude conversation", "שיחה חדשה עם Claude")) { menu = false; Tools.launch(claude, dir, p.name, fresh = true) }
                }
                MenuItem(Glyph.EDIT, tr("Details", "פרטים")) { menu = false; Nav.set(Screen.Details(p.id)) }
                MenuItem(Glyph.DOC, tr("Files", "קבצים")) { menu = false; Nav.set(Screen.Project(p.id, Tab.FILES)) }
                MenuItem(Glyph.PLAY, tr("Commands", "פקודות")) { menu = false; Nav.set(Screen.Project(p.id, Tab.COMMANDS)) }
                // Claude reads the folder and suggests what's missing; the details page shows what it found.
                if (dir != null && Tools.claude != null) MenuItem(Glyph.SPARKLE, tr("Fill in with Claude", "מילוי פרטים עם Claude")) {
                    menu = false; ClaudeFill.start(p, dir); Nav.set(Screen.Details(p.id))
                }
                MenuItem(Glyph.WIDGET, tr("Add a widget to the desktop", "ווידג'ט על שולחן העבודה")) { menu = false; actions.addWidget(p) }
                if (dir != null) {
                    MenuItem(Glyph.FOLDER, tr("Open the folder", "פתיחת התיקייה")) { menu = false; Tools.launch(Tools.explorer, dir, p.name) }
                    MenuItem(Glyph.TERMINAL, tr("Open a terminal", "פתיחת טרמינל")) { menu = false; Tools.launch(Tools.terminal, dir, p.name) }
                }
                if (p.archived) MenuItem(Glyph.ARCHIVE, tr("Bring back from the archive", "החזרה מהארכיון")) { menu = false; archiveProject(p, false) }
                else MenuItem(Glyph.ARCHIVE, tr("Move to the archive", "העברה לארכיון")) { menu = false; archiveProject(p, true) }
                MenuItem(Glyph.TRASH, tr("Delete project", "מחיקת הפרויקט"), danger = true) { menu = false; actions.delete(p) }
            }
        }
    }
}

/** An archived project, opened: one line that says so, and the way back. */
@Composable
private fun ArchivedNote(p: Project) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(c.surface2).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.ARCHIVE, 17.dp, c.muted)
        Spacer(Modifier.width(10.dp))
        Text(tr("In the archive: it isn't on Now, the sidebar, widgets or the phone. Opening Claude in it brings it back.",
            "בארכיון: לא מופיע ב״עכשיו״, בסרגל, בווידג'טים ובטלפון. פתיחת Claude בו מחזירה אותו."),
            color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Pill(tr("Bring back", "החזרה"), Tone.SOFT) { archiveProject(p, false) }
    }
}

@Composable
fun MenuItem(glyph: Glyph, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalDeck.current
    DropdownMenuItem(
        text = { Text(text, color = if (danger) c.danger else c.fg, fontSize = 14.sp, fontFamily = PlexSans) },
        leadingIcon = { GlyphIcon(glyph, 16.dp, if (danger) c.danger else c.muted) },
        onClick = onClick,
    )
}

/** Where the project stands, in one line: Claude, the branch, changes, a running server; the account without a folder. */
@Composable
fun StatusLine(p: Project, local: Local) {
    val c = LocalDeck.current
    val folder = local.folder
    val claude = claudeIn(folder)
    val git = folder?.let { Git.state.collectAsState().value[it] }
    val port = folder?.let { Pulses.state.collectAsState().value[it]?.port }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var first = true
        @Composable fun sep() { if (!first) Text("/", color = c.line, fontSize = 13.sp) ; first = false }
        if (claude != null) {
            sep()
            ClaudeDot(claude.mode, c == Dark, 7.dp)
            Text(claudeText(claude), color = if (claude.mode == ClaudeMode.DONE || claude.mode == ClaudeMode.OPEN) c.muted else claudeColor(claude.mode, c == Dark), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
        }
        if (git != null) {
            sep(); Mono(git.branch)
            sep(); Text(gitWords(git), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
        }
        if (port != null) { sep(); Mono("● :$port", c.liveText) }
        if (folder == null) { sep(); Text(listOf(p.kind, Chrome.describe(p)).filter { it.isNotBlank() }.joinToString(" · "), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans) }
    }
}

/** "2 uncommitted · 1 to push", or "all saved". */
fun gitWords(g: GitInfo): String = buildList {
    if (g.changes > 0) add(tr("${g.changes} uncommitted", if (g.changes == 1) "שינוי אחד לא שמור" else "${g.changes} לא שמורים"))
    if (g.ahead > 0) add(tr("${g.ahead} to push", if (g.ahead == 1) "קומיט אחד לדחוף" else "${g.ahead} לדחוף"))
    if (g.behind > 0) add(tr("${g.behind} to pull", "${g.behind} חדשים בשרת"))
}.ifEmpty { listOf(tr("all saved", "הכול שמור")) }.joinToString(" · ")

@Composable
private fun Tabs(p: Project, tab: Tab, local: Local) {
    val c = LocalDeck.current
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(
                Tab.OVERVIEW to tr("Overview", "סקירה"),
                Tab.GIT to "Git" + (local.folder?.let { Git.state.collectAsState().value[it] }?.changes?.takeIf { it > 0 }?.let { "  $it" } ?: ""),
                Tab.LINKS to tr("Links", "קישורים") + (p.actions.size.takeIf { it > 0 }?.let { "  $it" } ?: ""),
                (Tab.FILES to tr("Files", "קבצים")).takeIf { local.files.isNotEmpty() || tab == Tab.FILES },
                (Tab.COMMANDS to tr("Commands", "פקודות") + (local.commands.size.takeIf { it > 0 }?.let { "  $it" } ?: "")).takeIf { local.commands.isNotEmpty() || tab == Tab.COMMANDS },
            ).filterNotNull().forEach { (t, label) ->
                val on = t == tab
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).clickable { Nav.set(Screen.Project(p.id, t)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (on) c.fg else c.muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans,
                        modifier = Modifier.padding(bottom = 11.dp))
                    Box(Modifier.height(2.dp).fillMaxWidth().background(if (on) c.fg else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

// ── overview ──

/*
 * One column, read top to bottom, each thing once: an agent at work (only while it is), the next step, what's open,
 * and the project's memory (summary, decisions, recent sessions) folded below. The header already says where git
 * stands and opens Claude, so the page doesn't say it again.
 */
@Composable
private fun Overview(p: Project, local: Local, dark: Boolean, actions: AppActions) {
    val dir = local.dir
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 24.dp)) {
        if (dir == null) { ConnectFolder(p, actions); return@Column }
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            LiveAgent(local, dark)
            OfferFill(p, local, dir)
            NowLine(dir)
            PlanLists(p, local, dir)
            LoopsCard(p, local)
            Memory(p, local, dir)
        }
    }
}

/**
 * Once, on a project whose folder YDeck has just met: Claude can fill in the rest (what it is, a summary, a next step,
 * links, commands). Taken or not, it isn't offered again; it stays in the details and the "..." menu.
 */
@Composable
private fun OfferFill(p: Project, local: Local, dir: java.io.File) {
    if (local.fillOffered || Tools.claude == null) return
    val bare = remember(dir) { ProjectFiles.readShared(dir)?.summary.isNullOrBlank() }
    if (!bare) return
    val c = LocalDeck.current
    fun offered() = saveLocal(p, Settings.state.value.localOf(p.id).copy(fillOffered = true))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accentTint).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.SPARKLE, 18.dp, c.accentText)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Let Claude fill in the rest?", "ש־Claude ימלא את השאר?"), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
            Text(tr("It reads the folder, changes nothing, and suggests what it is, a summary, a next step, links and commands.",
                "הוא קורא את התיקייה, לא משנה בה כלום, ומציע מה הפרויקט, תקציר, צעד הבא, קישורים ופקודות."),
                color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans)
        }
        Spacer(Modifier.width(12.dp))
        TextAction(tr("Not now", "לא עכשיו")) { offered() }
        Spacer(Modifier.width(4.dp))
        Pill(tr("Fill in", "מילוי"), Tone.PRIMARY, Glyph.SPARKLE, small = true) { offered(); ClaudeFill.start(p, dir); Nav.set(Screen.Details(p.id)) }
    }
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalDeck.current.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = modifier)
}

/** Claude Code at work or waiting here, and on what; nothing at all otherwise. */
@Composable
private fun LiveAgent(local: Local, dark: Boolean) {
    val c = LocalDeck.current
    val session = claudeIn(local.folder)?.takeIf { it.mode == ClaudeMode.WORKING || it.mode == ClaudeMode.WAITING } ?: return
    val waiting = session.mode == ClaudeMode.WAITING
    Card(fill = if (waiting) CHANGED_DOT.copy(alpha = if (dark) 0.14f else 0.12f) else c.accentTint, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClaudeDot(session.mode, dark, 8.dp)
            Spacer(Modifier.width(10.dp))
            Text(claudeText(session), color = if (waiting) c.amberText else claudeColor(session.mode, dark), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans,
                modifier = Modifier.weight(1f))
            Text(ago(session.workStart), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
        }
        if (session.detail.isNotBlank()) Text(
            if (session.mode == ClaudeMode.WORKING) quoted(session.detail) else session.detail, color = c.fg, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 18.dp, top = 6.dp), style = TextStyle(textDirection = TextDirection.Content),
        )
    }
}

/** What's in progress: one sentence, large and thin, ending in the brand's amber point (from .ydeck.local.json). */
@Composable
private fun NowLine(dir: File) {
    val c = LocalDeck.current
    val now = Pulses.state.collectAsState().value[dir.path]?.now?.trim()?.trimEnd('.', ' ')?.takeIf { it.isNotEmpty() } ?: return
    Column {
        Text(tr("Now", "עכשיו"), color = c.faint, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString { append(now); pushStyle(androidx.compose.ui.text.SpanStyle(color = CHANGED_DOT)); append("."); pop() },
            color = c.fg, fontSize = 28.sp, lineHeight = 38.sp, fontFamily = Display, fontWeight = FontWeight.Light,
            modifier = Modifier.padding(start = 4.dp), style = TextStyle(textDirection = dirOf(now)),
        )
    }
}

/**
 * Where the work stands, as one path (see [Plan]), as on the phone: what was done (folded into one green point), the
 * next step in a card (both ways with .ydeck.local.json, with Claude's suggestion and where the last session ended),
 * and what's left after it as empty rings, ticked off or removed here. Then the ideas set aside, as chips: nothing is
 * built until one is moved to what's left.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PlanLists(p: Project, local: Local, dir: File) {
    val c = LocalDeck.current
    val pulse = Pulses.state.collectAsState().value[dir.path] ?: Pulse()
    val scope = rememberCoroutineScope()
    var draft by remember(dir) { mutableStateOf("") }
    var openDone by remember(dir) { mutableStateOf(false) }
    var note by remember(p.id) { mutableStateOf(local.note) }
    LaunchedEffect(local.note) { if (local.note != note) note = local.note }
    val busy = dir.path in Handoffs.busy.collectAsState().value
    var error by remember(p.id) { mutableStateOf<String?>(null) }
    suspend fun refresh() = Pulses.refresh(Settings.state.value.local.values.mapNotNull { it.folder })
    fun change(op: () -> Unit) = scope.launch { withContext(Dispatchers.IO) { op() }; refresh() }
    fun use(next: String) { note = next; Settings.setNote(p.id, next); ProjectFiles.writeNext(dir, next) }
    val rest = pulse.left.filter { it.trim() != note.trim() }
    val hasDone = pulse.done.isNotEmpty() || pulse.handoff?.done?.isNotBlank() == true
    val leftCount = rest.size + (if (note.isNotBlank()) 1 else 0)
    Column(verticalArrangement = Arrangement.spacedBy(30.dp)) {
        Column {
            Section(tr("The path", "המסלול")) {
                if (leftCount > 0) Text(tr("$leftCount left", if (leftCount == 1) "שלב אחד נשאר" else "$leftCount שלבים נשארו"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
            }
            Column(Modifier.padding(start = 4.dp)) {
                // What was done: one green point, opened on a click.
                if (hasDone) PathRow(nodeTop = 12.dp, below = CLEAN_DOT, node = { DoneNode() }) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { openDone = !openDone }.padding(vertical = 4.dp, horizontal = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (pulse.done.isEmpty()) tr("Last session", "בסשן האחרון") else tr("${pulse.done.size} done", "${pulse.done.size} נעשו"),
                                    color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
                                (pulse.done.firstOrNull() ?: pulse.handoff?.done.orEmpty()).takeIf { it.isNotBlank() }?.let { last ->
                                    Text(tr("Latest: ", "אחרון: ") + last, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(textDirection = dirOf(last)))
                                }
                            }
                            Box(Modifier.rotate(if (openDone) 180f else 0f)) { GlyphIcon(Glyph.CHEVRON, 16.dp, c.faint) }
                        }
                        if (openDone) Column(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            pulse.handoff?.takeIf { it.done.isNotBlank() }?.let { h ->
                                Text(tr("Last session", "בסשן האחרון") + " · " + ago(h.at), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
                                Text(h.done, color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = PlexSans, style = TextStyle(textDirection = dirOf(h.done)),
                                    modifier = Modifier.padding(bottom = 6.dp))
                            }
                            pulse.done.forEach { t ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.padding(top = 7.dp)) { Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(CLEAN_DOT.copy(alpha = 0.7f))) }
                                    Spacer(Modifier.width(10.dp))
                                    Text(t, color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = PlexSans, style = TextStyle(textDirection = dirOf(t)))
                                }
                            }
                        }
                    }
                }
                // The next step: an indigo point and a card, written here or by Claude.
                PathRow(nodeTop = 18.dp, above = if (hasDone) CLEAN_DOT else null, below = if (rest.isNotEmpty()) c.line else null, node = { NextNode() }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Next", "הבא"), color = c.accentText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                            if (note.isNotBlank()) TextAction(tr("Done", "נעשה"), accent = true) {
                                val t = note.trim(); use("")
                                change { Plan.finish(dir, t) }
                            }
                            if (Tools.claude != null) TextAction(if (busy) tr("Claude is summing up…", "Claude מסכם…") else tr("Sum up with Claude", "סיכום עם Claude"), accent = true, enabled = !busy) {
                                error = null
                                scope.launch { error = withContext(Dispatchers.IO) { Handoffs.write(dir) }; refresh() }
                            }
                        }
                        Field(null, note, { use(it) }, placeholder = tr("The next step, so you come back without thinking", "הצעד הבא, כדי לחזור בלי לחשוב"),
                            modifier = Modifier.padding(top = 6.dp))
                        // Claude's suggestion, or the first thing left, one click from being the next step.
                        val suggestion = pulse.suggestion.ifBlank { if (note.isBlank()) pulse.left.firstOrNull().orEmpty() else "" }
                        if (suggestion.isNotBlank() && suggestion != note) Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.SPARKLE, 13.dp, c.accentText)
                            Spacer(Modifier.width(8.dp))
                            Text((if (pulse.suggestion.isNotBlank()) tr("Claude suggests: ", "Claude מציע: ") else tr("First left: ", "הראשון שנשאר: ")) + suggestion,
                                color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false), style = TextStyle(textDirection = TextDirection.Content))
                            TextAction(tr("Use this", "להשתמש בזה"), accent = true) { use(suggestion) }
                        }
                        pulse.handoff?.done?.takeIf { it.isNotBlank() }?.let { before ->
                            Text(tr("Last time: ", "בפעם הקודמת: ") + before + " · " + ago(pulse.handoff.at), color = c.faint, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = PlexSans,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 8.dp), style = TextStyle(textDirection = TextDirection.Content))
                        }
                        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
                    }
                }
                // What's left after it, in order: empty rings, ticked off or removed here.
                rest.forEach { t ->
                    PathRow(above = c.line, below = c.line, node = { RingNode() }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t, color = c.fg.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f),
                                style = TextStyle(textDirection = dirOf(t)))
                            IconButton(Glyph.CHECK, tr("Done", "נעשה"), 26.dp, c.accentText, filled = false) { change { Plan.finish(dir, t) } }
                            IconButton(Glyph.TRASH, tr("Remove", "הסרה"), 26.dp, c.faint, filled = false) { change { Plan.remove(dir, Plan.LEFT, t) } }
                        }
                    }
                }
                // One more, at the end of the path.
                PathRow(above = if (rest.isNotEmpty()) c.line else null, node = { RingNode(add = true) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(null, draft, { draft = it }, tr("Something left to do", "עוד משהו שנשאר"), modifier = Modifier.weight(1f))
                        Pill(tr("Add", "הוספה"), Tone.SOFT, enabled = draft.isNotBlank(), small = true) { val t = draft; draft = ""; change { Plan.add(dir, Plan.LEFT, t) } }
                    }
                }
            }
        }
        if (pulse.ideas.isNotEmpty()) Column {
            Section(tr("Ideas set aside", "רעיונות בצד") + " · ${pulse.ideas.size}")
            Text(tr("Came up along the way and weren't built. Move one to the path only if you want it.",
                "עלו בדרך ולא נבנו. מעבירים למסלול רק את מה שבאמת רוצים."),
                color = c.faint, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pulse.ideas.forEach { t ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(c.surface2).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 320.dp), style = TextStyle(textDirection = dirOf(t)))
                        TextAction(tr("To do", "לעשות"), accent = true) { change { Plan.take(dir, t) } }
                        IconButton(Glyph.CLOSE, tr("Drop", "לוותר"), 24.dp, c.faint, filled = false) { change { Plan.remove(dir, Plan.IDEAS, t) } }
                    }
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
internal fun DoneNode() {
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(CLEAN_DOT), contentAlignment = Alignment.Center) {
        GlyphIcon(Glyph.CHECK, 13.dp, Color(0xFF0B0B0D))
    }
}

@Composable
internal fun NextNode(on: Boolean = true) {
    val c = LocalDeck.current
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(c.accentTint), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (on) c.accent else c.faint))
    }
}

@Composable
internal fun RingNode(add: Boolean = false) {
    val c = LocalDeck.current
    Box(Modifier.width(22.dp).height(12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(6.dp)).background(c.bg).border(2.dp, if (add) c.line else Color(0xFF8A8A90).copy(alpha = 0.6f), RoundedCornerShape(6.dp)))
    }
}

/** What the project remembers, folded: what it is, what it keeps to, the agents' latest sessions. */
@Composable
private fun Memory(p: Project, local: Local, dir: File) {
    val pulse = Pulses.state.collectAsState().value[dir.path] ?: Pulse()
    val sessions = recentSessions(local.folder)
    var open by remember(p.id) { mutableStateOf(setOf("summary")) }
    @Composable fun GroupScope.fold(key: String, title: String, count: Int?, body: @Composable () -> Unit) =
        Fold(title, count, key in open, { open = if (key in open) open - key else open + key }, body)
    Column {
        Section(tr("Memory", "זיכרון"))
        Group {
            fold("summary", tr("Summary", "תקציר"), null) { SummaryBody(dir, pulse) }
            fold("decisions", tr("Decisions", "החלטות"), pulse.decisions.size) { DecisionsBody(dir) }
            if (sessions.isNotEmpty()) fold("sessions", tr("Recent sessions", "סשנים אחרונים"), sessions.size) { SessionsList(p, local) }
        }
    }
}

/** A row of a group that opens to show more: its title, how many there are, and a chevron. */
@Composable
private fun GroupScope.Fold(title: String, count: Int?, expanded: Boolean, onToggle: () -> Unit, body: @Composable () -> Unit) {
    val c = LocalDeck.current
    Item(onToggle) {
        Text(title, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        if (count != null && count > 0) Text("$count", color = c.faint, fontSize = 13.sp, fontFamily = PlexSans)
        Box(Modifier.rotate(if (expanded) 180f else 0f)) { GlyphIcon(Glyph.CHEVRON, 16.dp, c.faint) }
    }
    if (expanded) Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) { body() }
}

/** What the project is: written by Claude on request, kept in .ydeck.json. */
@Composable
private fun SummaryBody(dir: File, pulse: Pulse) {
    val c = LocalDeck.current
    val busy = dir.path in ClaudeSummary.busy.collectAsState().value
    val scope = rememberCoroutineScope()
    var error by remember(dir) { mutableStateOf<String?>(null) }
    Column {
        Text(
            pulse.summary.ifBlank { tr("No summary yet. Claude can read the project and write one, in a few seconds.", "עוד אין תקציר. Claude יכול לקרוא את הפרויקט ולכתוב אחד, בכמה שניות.") },
            color = if (pulse.summary.isBlank()) c.faint else c.fg, fontSize = 14.sp, lineHeight = 22.sp, fontFamily = PlexSans,
            style = TextStyle(textDirection = dirOf(pulse.summary)),
        )
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pulse.summaryAt > 0 && !busy) Text(tr("By Claude · ${ago(pulse.summaryAt)}", "נכתב ע״י Claude · ${ago(pulse.summaryAt)}"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans,
                modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (Tools.claude != null) TextAction(
                if (busy) tr("Claude is reading…", "Claude קורא…") else if (pulse.summary.isBlank()) tr("Write with Claude", "כתיבה עם Claude") else tr("Update", "עדכון"),
                accent = true, enabled = !busy,
            ) {
                error = null
                scope.launch {
                    error = withContext(Dispatchers.IO) { ClaudeSummary.run(dir) }
                    Pulses.refresh(Settings.state.value.local.values.mapNotNull { it.folder })
                }
            }
        }
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans) }
    }
}

/** A project without its code folder: the one thing to do. */
@Composable
private fun ConnectFolder(p: Project, actions: AppActions) {
    val c = LocalDeck.current
    Card(Modifier.widthIn(max = 640.dp)) {
        GlyphIcon(Glyph.FOLDER, 26.dp, c.accentText)
        Spacer(Modifier.height(14.dp))
        CardTitle(tr("Connect the code folder", "חיבור תיקיית הקוד"))
        Spacer(Modifier.height(6.dp))
        Text(tr("YDeck reads it and fills in the rest: the repository, the consoles, the commands and the logo. Then you'll see where the work stands, here and on the widget.",
            "YDeck קורא אותה וממלא את השאר: הריפו, הקונסולות, הפקודות והלוגו. ואז רואים כאן ובווידג'ט איפה העבודה עומדת."),
            color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
        Spacer(Modifier.height(16.dp))
        Pill(tr("Choose a folder", "בחירת תיקייה"), Tone.PRIMARY, Glyph.FOLDER) { actions.chooseFolder()?.let { connectFolder(p, it) } }
    }
}
