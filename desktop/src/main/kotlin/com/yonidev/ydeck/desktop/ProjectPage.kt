package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * A project's home: its header (logo, name, where it stands, the editor and Claude) and four tabs. Overview is where
 * you pick up from; Links, Files and Commands hold the rest (ProjectTabs.kt).
 */

private val CLAUDE_ORANGE = Color(0xFFD97757)

@Composable
fun ProjectPage(p: Project, tab: Tab, dark: Boolean, actions: AppActions) {
    val c = LocalDeck.current
    val local = Settings.state.collectAsState().value.localOf(p.id)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 44.dp, end = 44.dp, top = 28.dp)) {
            Header(p, local, actions)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable { Nav.set(Screen.Details(p.id)) }) { Logo(p, 56.dp) }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            PageTitle(p.name, 34)
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
                MenuItem(Glyph.EDIT, tr("Details", "פרטים")) { menu = false; Nav.set(Screen.Details(p.id)) }
                // Claude reads the folder and suggests what's missing; the details page shows what it found.
                if (dir != null && Tools.claude != null) MenuItem(Glyph.SPARKLE, tr("Fill in with Claude", "מילוי פרטים עם Claude")) {
                    menu = false; ClaudeFill.start(p, dir); Nav.set(Screen.Details(p.id))
                }
                MenuItem(Glyph.WIDGET, tr("Add a widget to the desktop", "ווידג'ט על שולחן העבודה")) { menu = false; actions.addWidget(p) }
                if (dir != null) {
                    MenuItem(Glyph.FOLDER, tr("Open the folder", "פתיחת התיקייה")) { menu = false; Tools.launch(Tools.explorer, dir, p.name) }
                    MenuItem(Glyph.TERMINAL, tr("Open a terminal", "פתיחת טרמינל")) { menu = false; Tools.launch(Tools.terminal, dir, p.name) }
                }
                MenuItem(Glyph.TRASH, tr("Delete project", "מחיקת הפרויקט"), danger = true) { menu = false; actions.delete(p) }
            }
        }
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
            Text(claudeText(claude), color = if (claude.mode == ClaudeMode.DONE) c.muted else claudeColor(claude.mode, c == Dark), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
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
                Tab.FILES to tr("Files", "קבצים"),
                Tab.COMMANDS to tr("Commands", "פקודות") + (local.commands.size.takeIf { it > 0 }?.let { "  $it" } ?: ""),
            ).forEach { (t, label) ->
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

@Composable
private fun Overview(p: Project, local: Local, dark: Boolean, actions: AppActions) {
    val dir = local.dir
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 820.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 24.dp)) {
            if (dir == null) { ConnectFolder(p, actions); return@Column }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.55f), verticalArrangement = Arrangement.spacedBy(20.dp)) { LoopsCard(p, local); LeftOff(p, local, dir); Summary(p, dir); DecisionsCard(dir) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { ClaudeCard(p, local, dir, dark); SessionsCard(p, local); StateCard(p, local) }
            } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LoopsCard(p, local); ClaudeCard(p, local, dir, dark); LeftOff(p, local, dir); Summary(p, dir); DecisionsCard(dir); SessionsCard(p, local); StateCard(p, local)
            }
        }
    }
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalDeck.current.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = modifier)
}

/** What the project is: written by Claude on request, kept in .ydeck.json. */
@Composable
private fun Summary(p: Project, dir: File) {
    val c = LocalDeck.current
    val pulse = Pulses.state.collectAsState().value[dir.path] ?: Pulse()
    val busy = dir.path in ClaudeSummary.busy.collectAsState().value
    val scope = rememberCoroutineScope()
    var error by remember(p.id) { mutableStateOf<String?>(null) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Summary", "תקציר"), Modifier.weight(1f))
            if (pulse.summaryAt > 0 && !busy) {
                Text(tr("by Claude · ${ago(pulse.summaryAt)}", "נכתב ע״י Claude · ${ago(pulse.summaryAt)}"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
                Spacer(Modifier.width(10.dp))
            }
            if (Tools.claude != null) Pill(
                if (busy) tr("Claude is reading…", "Claude קורא…") else if (pulse.summary.isBlank()) tr("Write with Claude", "כתיבה עם Claude") else tr("Update with Claude", "עדכון עם Claude"),
                Tone.TINT, Glyph.SPARKLE, enabled = !busy, small = true,
            ) {
                error = null
                scope.launch {
                    error = withContext(Dispatchers.IO) { ClaudeSummary.run(dir) }
                    val all = Settings.state.value.local.values.mapNotNull { it.folder }
                    Pulses.refresh(all)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            pulse.summary.ifBlank { tr("No summary yet. Claude can read the project and write one, in a few seconds.", "עוד אין תקציר. Claude יכול לקרוא את הפרויקט ולכתוב אחד, בכמה שניות.") },
            color = if (pulse.summary.isBlank()) c.faint else c.fg, fontSize = 15.sp, lineHeight = 24.sp, fontFamily = PlexSans,
            style = TextStyle(textDirection = dirOf(pulse.summary)),
        )
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** Now, next, last: what's in progress (Claude's), the next step (both ways with .ydeck.local.json), the last commit. */
@Composable
private fun LeftOff(p: Project, local: Local, dir: File) {
    val c = LocalDeck.current
    val pulse = Pulses.state.collectAsState().value[dir.path] ?: Pulse()
    val git = Git.state.collectAsState().value[dir.path]
    var note by remember(p.id) { mutableStateOf(local.note) }
    LaunchedEffect(local.note) { if (local.note != note) note = local.note }
    val busy = dir.path in Handoffs.busy.collectAsState().value
    val scope = rememberCoroutineScope()
    var error by remember(p.id) { mutableStateOf<String?>(null) }
    val handoff = pulse.handoff
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Where you left off", "איפה עצרת"), Modifier.weight(1f))
            if (Tools.claude != null) Pill(if (busy) tr("Claude is summing up…", "Claude מסכם…") else tr("Sum up with Claude", "סיכום עם Claude"), Tone.TINT, Glyph.SPARKLE, enabled = !busy, small = true) {
                error = null
                scope.launch {
                    error = withContext(Dispatchers.IO) { Handoffs.write(dir) }
                    Pulses.refresh(Settings.state.value.local.values.mapNotNull { it.folder })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        @Composable fun line(label: String, content: @Composable () -> Unit) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = c.faint, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f)) { content() }
            }
        }
        line(tr("Now", "עכשיו")) {
            Text(pulse.now.ifBlank { "—" }, color = if (pulse.now.isBlank()) c.faint else c.fg, fontSize = 15.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content))
        }
        if (handoff != null && handoff.done.isNotBlank()) line(tr("Done", "נעשה")) {
            Text(handoff.done + " · " + ago(handoff.at), color = c.fg, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content))
        }
        line(tr("Next", "הבא")) {
            Field(null, note, { note = it; Settings.setNote(p.id, it); ProjectFiles.writeNext(dir, it) }, placeholder = tr("The next step, so you come back without thinking", "הצעד הבא, כדי לחזור בלי לחשוב"))
        }
        // Claude's suggestion for the next step, one click from being it.
        if (handoff != null && handoff.next.isNotBlank() && handoff.next != note) Row(Modifier.padding(start = 64.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.SPARKLE, 13.dp, c.accentText)
            Spacer(Modifier.width(6.dp))
            Text(handoff.next, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(textDirection = TextDirection.Content))
            TextAction(tr("Use", "לקחת"), accent = true) { note = handoff.next; Settings.setNote(p.id, handoff.next); ProjectFiles.writeNext(dir, handoff.next) }
        }
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 6.dp)) }
        if (git != null && git.lastAt > 0) line(tr("Last", "אחרון")) {
            Text("“${git.lastSubject}” · ${ago(git.lastAt)}", color = c.muted, fontSize = 14.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

/** Claude Code here: what it's doing now, or a way in (continue the last conversation, or a new one). */
@Composable
private fun ClaudeCard(p: Project, local: Local, dir: File, dark: Boolean) {
    val c = LocalDeck.current
    val claude = Tools.claude ?: return
    val session = claudeIn(local.folder)
    val pulse = Pulses.state.collectAsState().value[dir.path] ?: Pulse()
    if (session != null && session.mode != ClaudeMode.DONE) {
        val color = claudeColor(session.mode, dark)
        Card(fill = if (session.mode == ClaudeMode.WAITING) CHANGED_DOT.copy(alpha = if (dark) 0.14f else 0.12f) else c.accentTint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClaudeDot(session.mode, dark, 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(claudeText(session), color = if (session.mode == ClaudeMode.WAITING) c.amberText else color, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                Text(ago(session.workStart), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
            }
            if (session.detail.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(if (session.mode == ClaudeMode.WORKING) "“${session.detail}”" else session.detail, color = c.fg, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
            }
        }
        return
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.CLAUDE, 18.dp, CLAUDE_ORANGE)
            Spacer(Modifier.width(10.dp))
            CardTitle("Claude Code", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            session?.let { claudeText(it) } ?: pulse.claudeAt?.let { tr("Last conversation ${ago(it)}", "שיחה אחרונה ${ago(it)}") } ?: tr("No conversations here yet", "עוד אין כאן שיחות"),
            color = c.muted, fontSize = 13.sp, fontFamily = PlexSans,
        )
        session?.detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = c.fg, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(textDirection = TextDirection.Content))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pulse.claudeAt != null) {
                Pill(tr("Continue", "המשך"), Tone.DARK, small = true) { Tools.launch(claude, dir, p.name) }
                Pill(tr("New conversation", "שיחה חדשה"), Tone.SOFT, small = true) { Tools.launch(claude, dir, p.name, fresh = true) }
            } else Pill(tr("Start", "פתיחה"), Tone.DARK, small = true) { Tools.launch(claude, dir, p.name, fresh = true) }
        }
    }
}

/** The project's state, one row each: git, the dev server, .env, the version. */
@Composable
private fun StateCard(p: Project, local: Local) {
    val c = LocalDeck.current
    val folder = local.folder ?: return
    val git = Git.state.collectAsState().value[folder]
    val pulse = Pulses.state.collectAsState().value[folder] ?: Pulse()
    Group {
        val toGit = { Nav.set(Screen.Project(p.id, Tab.GIT)) }
        if (git != null) StateRow(if (git.changes > 0 || git.ahead > 0 || git.behind > 0) CHANGED_DOT else CLEAN_DOT, gitWords(git), git.branch, mono = true, onClick = toGit)
        else StateRow(c.line, tr("Not a git repository", "התיקייה לא ב־git"), tr("Start", "הפעלה"), c.accentText, onClick = toGit)
        pulse.port?.let { port ->
            StateRow(LIVE_DOT, tr("Dev server", "שרת פיתוח"), "localhost:$port", c.accentText, mono = true) {
                Chrome.open(p, DeckAction(Catalog.LINK, "http://localhost:$port", "localhost"))
            }
        }
        if (pulse.missingEnv.isNotEmpty()) {
            StateRow(CHANGED_DOT, count(pulse.missingEnv.size, "key missing in .env", "keys missing in .env", "חסר מפתח אחד ב־.env", "מפתחות חסרים ב־.env"),
                pulse.missingEnv.first(), c.amberText, mono = true)
        }
        val version = pulse.release?.version ?: pulse.version
        if (version != null) StateRow(c.line, tr("Version $version", "גרסה ⁦$version⁩"), pulse.release?.let { r -> listOf(r.date, r.note).filter { it.isNotBlank() }.joinToString(" · ") })
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
