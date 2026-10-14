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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Git in the app: the project page's Git tab, and the same essentials in a sheet over the strip in File Explorer.
 * The tab reads top to bottom: where the branch stands against its server (and the one thing to do about it), then
 * one of three views: the changes (pick, commit, each file's diff), the history, the agents' sessions.
 */

private enum class GitView { CHANGES, HISTORY, SESSIONS }

@Composable
fun GitTab(p: Project, local: Local) {
    val c = LocalDeck.current
    val dir = local.dir
    if (dir == null) {
        TabColumn { Text(tr("Connect the project's folder to see its git here.", "מחברים את תיקיית הפרויקט כדי לראות כאן את ה־git שלו."), color = c.faint, fontSize = 14.sp, fontFamily = PlexSans) }
        return
    }
    val git = Git.state.collectAsState().value[dir.path]
    if (git == null) { TabColumn { NoRepo(dir) }; return }
    var view by remember(p.id) { mutableStateOf(GitView.CHANGES) }
    // Every change goes in the commit unless taken out.
    var excluded by remember(p.id) { mutableStateOf(setOf<String>()) }
    // An agent session's receipt, opened from a loop or the overview, or picked here.
    var session by remember(p.id) { mutableStateOf<String?>(null) }
    val focus by GitFocus.receipt.collectAsState()
    LaunchedEffect(focus) { focus?.let { session = it; view = GitView.SESSIONS; GitFocus.receipt.value = null } }
    val receipts = Receipts.state.collectAsState().value[dir.path].orEmpty().filter { it.files.isNotEmpty() }
    Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 22.dp)) {
        SyncBar(p, dir, git)
        PhaseNote(git, Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(18.dp))
        Segmented(
            listOfNotNull(
                GitView.CHANGES to tr("Changes", "שינויים") + (if (git.files.isEmpty()) "" else "  ${git.files.size}"),
                GitView.HISTORY to tr("History", "היסטוריה"),
                (GitView.SESSIONS to tr("Sessions", "סשנים") + "  ${receipts.size}").takeIf { receipts.isNotEmpty() },
            ),
            view,
        ) { view = it }
        Spacer(Modifier.height(16.dp))
        when (view) {
            GitView.CHANGES -> ChangesView(p, local, dir, git, receipts, excluded) { excluded = it }
            GitView.HISTORY -> Panel(Modifier.fillMaxSize()) { History(p, dir, git) }
            GitView.SESSIONS -> SessionsView(git, receipts, session, { session = it }) { r ->
                // Commit only what the session changed: everything else comes out of the commit.
                excluded = git.files.map { it.path }.toSet() - r.files.map { it.path }.toSet()
                view = GitView.CHANGES
            }
        }
    }
}

/** A tall pane with a hairline, open at the bottom (it runs to the window's edge). */
@Composable
private fun Panel(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Column(modifier.fillMaxHeight().clip(shape).border(1.dp, LocalDeck.current.line, shape), content = content)
}

/** A pane's top line: what it shows, and its actions at the end. */
@Composable
private fun PanelHead(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDeck.current.surface2))
}

/** The changes: the files (each in the commit or out) and the commit under them; the chosen file's diff beside. */
@Composable
private fun ChangesView(p: Project, local: Local, dir: File, git: GitInfo, receipts: List<Receipt>, excluded: Set<String>, setExcluded: (Set<String>) -> Unit) {
    val c = LocalDeck.current
    if (git.files.isEmpty()) { AllSaved(git); return }
    val loops = loopsOf(local)
    // Which agent left each file as it is: the latest session that changed it and wasn't put back.
    val byAgent = remember(receipts) { receipts.filter { it.rewound == null }.reversed().flatMap { r -> r.files.map { it.path to r.agent } }.toMap() }
    var shown by remember(p.id) { mutableStateOf(git.files.first().path) }
    val change = git.files.firstOrNull { it.path == shown } ?: git.files.first()
    val chosen = git.files.filter { it.path !in excluded }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(360.dp).fillMaxHeight()) {
            // With Claude Code: it gets everything open and proposes the commits; nothing happens until you say so.
            if (Tools.claude != null) Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.accentTint)
                .clickable { goOver(p, local, loops) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.CLAUDE, 16.dp, Color(0xFFD97757))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Sort it out with Claude Code", "לסדר עם Claude Code"), color = c.accentText, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                    Text(tr("It proposes the commits and their messages; you approve.", "הוא מציע את הקומיטים וההודעות, ואתה מאשר."), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                }
            }
            // One box for all: on when every file is in, off when none is.
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CheckBox(chosen.size == git.files.size) { on -> setExcluded(if (on) emptySet() else git.files.map { it.path }.toSet()) }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (chosen.size == git.files.size) count(git.files.size, "file in the commit", "files in the commit", "קובץ אחד בקומיט", "קבצים בקומיט")
                    else tr("${chosen.size} of ${git.files.size} files in the commit", "${chosen.size} מתוך ${git.files.size} קבצים בקומיט"),
                    color = c.muted, fontSize = 13.sp, fontFamily = PlexSans,
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                git.files.take(300).forEach { ch ->
                    ChangeRow(ch, byAgent[ch.path], selected = ch.path == change.path, included = ch.path !in excluded,
                        onInclude = { on -> setExcluded(if (on) excluded - ch.path else excluded + ch.path) }) { shown = ch.path }
                }
                if (git.files.size > 300) Text(tr("and ${git.files.size - 300} more", "ועוד ${git.files.size - 300}"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(10.dp))
            }
            Box(Modifier.padding(top = 12.dp, bottom = 20.dp)) {
                CommitBox(p.id, dir, if (excluded.none { e -> git.files.any { it.path == e } }) null else chosen)
            }
        }
        Spacer(Modifier.width(28.dp))
        Panel(Modifier.weight(1f)) { DiffPane(dir, change) }
    }
}

/** The agents' sessions that changed files; the chosen one's receipt beside. */
@Composable
private fun SessionsView(git: GitInfo, receipts: List<Receipt>, session: String?, onPick: (String) -> Unit, onCommitThese: (Receipt) -> Unit) {
    val receipt = receipts.firstOrNull { it.id == session } ?: receipts.firstOrNull() ?: return
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            receipts.take(20).forEach { r -> ReceiptRow(r, git, selected = r.id == receipt.id) { onPick(r.id) } }
        }
        Spacer(Modifier.width(28.dp))
        Panel(Modifier.weight(1f)) { SessionView(receipt, git, onCommitThese = { onCommitThese(receipt) }) }
    }
}

/** github.com → GitHub; anything else by its host. */
fun hostName(web: String): String {
    val host = web.substringAfter("://").substringBefore('/')
    return when {
        "github" in host -> "GitHub"
        "gitlab" in host -> "GitLab"
        "bitbucket" in host -> "Bitbucket"
        else -> host
    }
}

/** A folder without git: what it gives, and starting it. */
@Composable
private fun NoRepo(dir: File) {
    val c = LocalDeck.current
    val scope = rememberCoroutineScope()
    var error by remember(dir) { mutableStateOf<String?>(null) }
    val busy = dir.path in GitOps.busy.collectAsState().value
    Card(Modifier.width(560.dp)) {
        GlyphIcon(Glyph.BRANCH, 26.dp, c.accentText)
        Spacer(Modifier.height(14.dp))
        Text(if (Git.installed) tr("This folder isn't in git yet", "התיקייה הזו עוד לא ב־git") else tr("Git isn't installed on this PC", "Git לא מותקן במחשב"),
            color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
        Spacer(Modifier.height(6.dp))
        Text(tr("Git keeps every version of the code, so nothing is ever lost, and puts it on GitHub when you want.",
            "Git שומר כל גרסה של הקוד, כך ששום דבר לא הולך לאיבוד, ומעלה אותו ל־GitHub כשרוצים."),
            color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
        Spacer(Modifier.height(16.dp))
        if (Git.installed) Pill(if (busy) tr("Starting…", "מתחיל…") else tr("Start git here", "הפעלת git כאן"), Tone.PRIMARY, Glyph.BRANCH, enabled = !busy) {
            scope.launch { error = withContext(Dispatchers.IO) { GitOps.init(dir) } }
        } else Pill(tr("Download Git", "הורדת Git"), Tone.PRIMARY, Glyph.DOWNLOAD) { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://git-scm.com/download/win")) } }
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** Nothing to commit (and, given [git], the last commit). */
@Composable
private fun AllSaved(git: GitInfo? = null) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.paper).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.CHECK, 16.dp, c.liveText)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(tr("Nothing to commit. Everything is saved.", "אין מה לשמור. הכול שמור."), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
            if (git != null && git.lastAt > 0) Text(tr("Last commit: ", "הקומיט האחרון: ") + "“${git.lastSubject}” · ${ago(git.lastAt)}", color = c.faint, fontSize = 12.sp,
                fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

// ── the branch and its server ──

/** "Up to date with origin/main · checked 3 min ago", or why it can't be. */
fun syncWords(g: GitInfo): String = buildList {
    when {
        !g.hasRemote -> add(tr("Only on this PC: no server yet", "רק במחשב הזה: עוד אין שרת"))
        g.upstream == null -> add(tr("Not on the server yet", "עוד לא בשרת"))
        g.ahead == 0 && g.behind == 0 -> add(tr("Up to date with ${g.upstream}", "מעודכן מול ${g.upstream}"))
        else -> {
            if (g.ahead > 0) add(count(g.ahead, "commit to push", "commits to push", "קומיט אחד לדחוף", "קומיטים לדחוף"))
            if (g.behind > 0) add(count(g.behind, "new on the server", "new on the server", "קומיט חדש בשרת", "חדשים בשרת"))
        }
    }
    if (g.hasRemote && g.fetchedAt > 0) add(tr("checked ${ago(g.fetchedAt)}", "נבדק ${ago(g.fetchedAt)}"))
}.joinToString(" · ")

/** The branch (another one from its menu), where it stands against its server, and what to do about it. */
@Composable
private fun SyncBar(p: Project, dir: File, git: GitInfo) {
    val c = LocalDeck.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var error by remember(p.id) { mutableStateOf<String?>(null) }
    val branches by produceState(emptyList<String>(), menu) { if (menu) value = withContext(Dispatchers.IO) { GitOps.branches(dir) } }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface2).clickable { menu = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.BRANCH, 15.dp, c.muted)
                    Spacer(Modifier.width(8.dp))
                    Mono(git.branch, c.fg, 14)
                    Spacer(Modifier.width(6.dp))
                    GlyphIcon(Glyph.CHEVRON, 14.dp, c.faint)
                }
                DropdownMenu(menu, { menu = false }) {
                    branches.forEach { b ->
                        MenuItem(if (b == git.branch) Glyph.CHECK else Glyph.BRANCH, b) {
                            menu = false
                            if (b != git.branch) scope.launch { error = withContext(Dispatchers.IO) { GitOps.switch(dir, b) } }
                        }
                    }
                    if (branches.isEmpty()) MenuItem(Glyph.BRANCH, git.branch) { menu = false }
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(syncWords(git), color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = PlexSans, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            if (git.hasRemote) SyncButtons(dir, git, onError = { error = it })
            git.web?.let { web -> WebMenu(p, web) }
        }
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** The repository's pages on GitHub (or GitLab): the code, pull requests, actions. */
@Composable
private fun WebMenu(p: Project, web: String) {
    var open by remember { mutableStateOf(false) }
    val host = hostName(web)
    Box {
        IconButton(Glyph.EXTERNAL, tr("Open on $host", "פתיחה ב־$host"), 32.dp, LocalDeck.current.muted, filled = false) { open = true }
        DropdownMenu(open, { open = false }) {
            fun go(url: String) { open = false; Chrome.open(p, DeckAction(Catalog.LINK, url, host)) }
            MenuItem(Glyph.CODE, tr("The repository", "הריפו")) { go(web) }
            when (host) {
                "GitHub" -> {
                    MenuItem(Glyph.BRANCH, "Pull requests") { go("$web/pulls") }
                    MenuItem(Glyph.PLAY, "Actions") { go("$web/actions") }
                }
                "GitLab" -> {
                    MenuItem(Glyph.BRANCH, "Merge requests") { go("$web/-/merge_requests") }
                    MenuItem(Glyph.PLAY, "Pipelines") { go("$web/-/pipelines") }
                }
            }
        }
    }
}

/** Pull and push, each only when there's something to bring or send, and asking the server now. */
@Composable
fun SyncButtons(dir: File, git: GitInfo, small: Boolean = true, onError: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val busy = GitOps.busy.collectAsState().value[dir.path]
    fun go(op: (File) -> String?) { onError(null); scope.launch { onError(withContext(Dispatchers.IO) { op(dir) }) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val publish = git.upstream == null
        // What's new on the server comes first: pushing over it would be refused.
        if (git.behind > 0 && !publish) Pill(
            if (busy == GitOps.Op.PULL) tr("Pulling…", "מושך…") else tr("Pull ${git.behind}", "למשוך ${git.behind}"),
            Tone.PRIMARY, Glyph.DOWN, enabled = busy == null, small = small,
        ) { go(GitOps::pull) }
        if (git.ahead > 0 || (publish && git.lastAt > 0)) Pill(
            when {
                busy == GitOps.Op.PUSH -> tr("Pushing…", "דוחף…")
                publish -> tr("Put on the server", "להעלות לשרת")
                else -> tr("Push ${git.ahead}", "לדחוף ${git.ahead}")
            },
            if (git.behind > 0) Tone.SOFT else Tone.PRIMARY, Glyph.UP, enabled = busy == null, small = small,
        ) { go(GitOps::push) }
        IconButton(Glyph.REFRESH, if (busy == GitOps.Op.FETCH) tr("Checking…", "בודק…") else tr("Check the server now", "בדיקה בשרת עכשיו"), 32.dp,
            LocalDeck.current.muted, filled = false) { if (busy == null) go(GitOps::fetch) }
    }
}

/** A merge or a rebase in the middle, or conflicts: what to do. */
@Composable
private fun PhaseNote(git: GitInfo, modifier: Modifier = Modifier) {
    val c = LocalDeck.current
    if (git.phase == GitPhase.NONE && git.conflicts == 0) return
    val dark = c == Dark
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CHANGED_DOT.copy(alpha = if (dark) 0.14f else 0.12f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(when (git.phase) {
            GitPhase.MERGING -> tr("A merge is in progress", "מיזוג באמצע")
            GitPhase.REBASING -> tr("A rebase is in progress", "rebase באמצע")
            GitPhase.PICKING -> tr("A cherry-pick is in progress", "cherry-pick באמצע")
            GitPhase.NONE -> tr("Conflicts", "התנגשויות")
        }, color = c.amberText, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
        Text(when {
            git.conflicts > 0 -> count(git.conflicts, "file has conflicts: resolve it in your editor, then commit.", "files have conflicts: resolve them in your editor, then commit.",
                "קובץ אחד עם התנגשויות: פותרים בעורך, ואז שומרים בקומיט.", "קבצים עם התנגשויות: פותרים בעורך, ואז שומרים בקומיט.")
            git.phase == GitPhase.MERGING -> tr("Commit to finish it.", "שומרים בקומיט כדי לסיים.")
            else -> tr("Finish it in a terminal (git ${if (git.phase == GitPhase.REBASING) "rebase" else "cherry-pick"} --continue).",
                "מסיימים בטרמינל (git ${if (git.phase == GitPhase.REBASING) "rebase" else "cherry-pick"} --continue).")
        }, color = c.fg, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans)
    }
}

// ── changes ──

@Composable
fun KindBadge(kind: Char) {
    val c = LocalDeck.current
    val color = when (kind) {
        'A' -> c.liveText
        'D', 'U' -> c.danger
        'R' -> c.accentText
        else -> c.amberText
    }
    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Text(kind.toString(), color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, lineHeight = 11.sp,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)))
    }
}

/** What happened to a file, in a word, in the badge's colour. */
@Composable
private fun KindWord(kind: Char) {
    val c = LocalDeck.current
    val (word, color) = when (kind) {
        'A' -> tr("new", "חדש") to c.liveText
        'D' -> tr("deleted", "נמחק") to c.danger
        'U' -> tr("conflict", "התנגשות") to c.danger
        'R' -> tr("renamed", "שם חדש") to c.accentText
        else -> tr("changed", "שונה") to c.amberText
    }
    Text(word, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
}

/** A changed file: in the commit or not, its name over its folder, and what happened to it. */
@Composable
private fun ChangeRow(ch: GitChange, agent: Agent?, selected: Boolean, included: Boolean, onInclude: (Boolean) -> Unit, onClick: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (selected) Modifier.background(c.surface2) else Modifier).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(included, onInclude)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = if (included) c.fg else c.muted, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Ltr))
            if (ch.folder.isNotBlank()) Mono(ch.folder, c.faint, 11)
        }
        Spacer(Modifier.width(10.dp))
        // Changed by an agent's session (see the Sessions view).
        if (agent != null) { AgentMark(agent, 13.dp); Spacer(Modifier.width(8.dp)) }
        KindWord(ch.kind)
    }
}

/** The file's name, then its folder, quieter (both left to right: they are paths). */
@Composable
private fun FileName(ch: GitChange, modifier: Modifier, strong: Boolean = false) {
    val c = LocalDeck.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(ch.name, color = c.fg, fontSize = 13.5.sp, fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false), style = TextStyle(textDirection = TextDirection.Ltr))
        if (ch.folder.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Mono(ch.folder, c.faint, 11, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
fun CheckBox(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.size(17.dp).clip(shape).then(if (on) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.line, shape)).clickable { onChange(!on) },
        contentAlignment = Alignment.Center) {
        if (on) GlyphIcon(Glyph.CHECK, 13.dp, Color.White)
    }
}

/** The message (Claude can write it) and the commit. [chosen] null: every change. */
@Composable
fun CommitBox(key: String, dir: File, chosen: List<GitChange>?, compact: Boolean = false) {
    val c = LocalDeck.current
    val scope = rememberCoroutineScope()
    var message by remember(key) { mutableStateOf("") }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var done by remember(key) { mutableStateOf<String?>(null) }
    val busy = GitOps.busy.collectAsState().value[dir.path]
    val all = Git.state.collectAsState().value[dir.path]?.files.orEmpty()
    val files = chosen ?: all
    val paths = chosen?.flatMap { listOfNotNull(it.path, it.from) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MessageField(message, { message = it; error = null; done = null }, tr("What changed, in a line", "מה השתנה, בשורה"), if (compact) 3 else 6)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (Tools.claude != null) Pill(if (busy == GitOps.Op.MESSAGE) tr("Claude is writing…", "Claude כותב…") else tr("Claude writes it", "Claude יכתוב"),
                Tone.PLAIN, Glyph.SPARKLE, enabled = busy == null && files.isNotEmpty(), small = true) {
                error = null; done = null
                scope.launch {
                    val (m, e) = withContext(Dispatchers.IO) { GitOps.suggestMessage(dir, paths) }
                    if (m != null) message = m
                    error = e
                }
            }
            Spacer(Modifier.weight(1f))
            Pill(if (busy == GitOps.Op.COMMIT) tr("Committing…", "שומר…") else tr("Commit", "קומיט") + "  ${files.size}", Tone.PRIMARY, Glyph.CHECK,
                enabled = busy == null && files.isNotEmpty() && message.isNotBlank(), small = true) {
                val subject = message.lines().first().trim()
                error = null
                scope.launch {
                    error = withContext(Dispatchers.IO) { GitOps.commit(dir, message, paths) }
                    if (error == null) { done = subject; message = "" }
                }
            }
        }
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PlexSans) }
        done?.let { subject ->
            // Committed: the next thing is usually to push it.
            val git = Git.state.collectAsState().value[dir.path]
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.CHECK, 14.dp, c.liveText)
                Spacer(Modifier.width(6.dp))
                Text(tr("Committed: “$subject”", "נשמר: “$subject”"), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false), style = TextStyle(textDirection = TextDirection.Content))
                if (git != null && git.hasRemote && git.ahead > 0 && git.behind == 0 && git.upstream != null) TextAction(tr("Push now", "לדחוף עכשיו"), accent = true, enabled = busy == null) {
                    error = null
                    scope.launch { error = withContext(Dispatchers.IO) { GitOps.push(dir) }; if (error == null) done = null }
                }
            }
        }
    }
}

/** A few lines for the message: the subject, and a body when there is one. */
@Composable
private fun MessageField(value: String, onChange: (String) -> Unit, placeholder: String, maxLines: Int) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(c.paper).border(1.dp, c.line, shape).padding(horizontal = 12.dp, vertical = 10.dp)) {
        val style = TextStyle(color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = PlexSans, textDirection = TextDirection.Content)
        if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.faint))
        BasicTextField(value, onChange, textStyle = style, minLines = 2, maxLines = maxLines, cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
    }
}

// ── the diff and the history ──

/** A changed file: its path, how many lines came and went, opening it, and its diff. */
@Composable
private fun DiffPane(dir: File, change: GitChange) {
    val c = LocalDeck.current
    val stamp = File(dir, change.path).lastModified()
    val lines by produceState<List<DiffLine>?>(null, change, stamp) { value = withContext(Dispatchers.IO) { GitOps.diff(dir, change) } }
    PanelHead {
        Mono(change.path, c.fg, 13, Modifier.weight(1f))
        lines?.let { l ->
            val added = l.count { it.kind == '+' }
            val removed = l.count { it.kind == '-' }
            if (added > 0) Mono("+$added", c.liveText, 12, Modifier.padding(start = 10.dp))
            if (removed > 0) Mono("−$removed", c.danger, 12, Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.width(10.dp))
        val editor = Tools.quick(dir).getOrNull(1)?.takeIf { it.kind == ToolKind.EDITOR || it.kind == ToolKind.IDE }
        val file = File(dir, change.path)
        if (file.isFile) TextAction(if (editor != null) tr("Open in ${editor.label}", "פתיחה ב־${editor.label}") else tr("Open", "פתיחה"), accent = true) {
            if (editor != null) Tools.openIn(editor, file) else Tools.openFile(file.path)
        }
    }
    DiffView(lines ?: return)
}

@Composable
private fun DiffView(shown: List<DiffLine>) {
    val c = LocalDeck.current
    if (shown.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Text(tr("No changes to show in this file.", "אין שינויים להציג בקובץ הזה."), color = c.faint, fontSize = 14.sp, fontFamily = PlexSans)
        }
        return
    }
    // Code reads left to right, in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
            itemsIndexed(shown) { i, l -> DiffRow(l, i == 0) }
        }
    }
}

/** One line of a diff: a hunk's head, or a line kept, added (green) or removed (red). */
@Composable
private fun DiffRow(l: DiffLine, first: Boolean) {
    val c = LocalDeck.current
    val dark = c == Dark
    val green = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val red = if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
    val bg = when (l.kind) { '+' -> green.copy(alpha = if (dark) 0.13f else 0.09f); '-' -> red.copy(alpha = if (dark) 0.13f else 0.08f); else -> Color.Transparent }
    if (l.kind == '@') {
        Text(l.text, color = c.faint, fontSize = 12.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = if (first) 4.dp else 14.dp, bottom = 4.dp))
    } else Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp)) {
        Text(if (l.kind == ' ') "" else l.kind.toString(), color = if (l.kind == '+') green else red, fontSize = 12.5.sp, lineHeight = 20.sp, fontFamily = PlexMono,
            modifier = Modifier.width(18.dp))
        Text(l.text.replace("\t", "    ").ifEmpty { " " }, color = c.fg, fontSize = 12.5.sp, lineHeight = 20.sp, fontFamily = PlexMono,
            style = TextStyle(textDirection = TextDirection.Ltr))
    }
}

// ── agent sessions ──

/** Where a session's changes stand now: still waiting in the folder, committed, or put back. */
private fun receiptState(r: Receipt, git: GitInfo?): String {
    val open = git?.files?.map { it.path }?.toSet().orEmpty()
    val waiting = r.files.count { it.path in open }
    return when {
        r.rewound != null -> tr("put back", "הוחזר")
        waiting == 0 -> tr("committed", "נשמר")
        waiting == r.files.size -> tr("not committed", "לא נשמר")
        else -> tr("$waiting not committed", "$waiting לא נשמרו")
    }
}

@Composable
private fun AgentMark(agent: Agent, size: androidx.compose.ui.unit.Dp = 16.dp) {
    when (agent) {
        Agent.CLAUDE -> GlyphIcon(Glyph.CLAUDE, size, Color(0xFFD97757))
        Agent.CODEX -> GlyphIcon(Glyph.CODE, size, LocalDeck.current.fg)
    }
}

@Composable
private fun ReceiptRow(r: Receipt, git: GitInfo?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (selected) Modifier.background(c.surface2) else Modifier).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AgentMark(r.agent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = c.fg, fontSize = 13.5.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
            Text(count(r.files.size, "file", "files", "קובץ אחד", "קבצים") + " · " + receiptState(r, git) + " · " + ago(r.endAt), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1)
        }
    }
}

/** The project's latest agent sessions that changed files, newest first (the overview shows a few). */
@Composable
fun recentSessions(folder: String?, n: Int = 3): List<Receipt> =
    folder?.let { Receipts.state.collectAsState().value[it] }.orEmpty().filter { it.files.isNotEmpty() }.take(n)

/** The body of the overview's "Recent sessions": each opens its receipt on the Git tab. */
@Composable
fun SessionsList(p: Project, local: Local) {
    val folder = local.folder ?: return
    val git = Git.state.collectAsState().value[folder]
    Column { recentSessions(folder).forEach { r -> ReceiptRow(r, git, false) { GitFocus.open(p, r.id) } } }
}

/** A session's receipt: what it was asked, the files it changed and their diffs; commit them, or put them back. */
@Composable
private fun SessionView(r: Receipt, git: GitInfo, onCommitThese: () -> Unit) {
    val c = LocalDeck.current
    val scope = rememberCoroutineScope()
    var confirm by remember(r.id) { mutableStateOf(false) }
    var busy by remember(r.id) { mutableStateOf(false) }
    var error by remember(r.id) { mutableStateOf<String?>(null) }
    val open = git.files.map { it.path }.toSet()
    val waiting = r.files.count { it.path in open }
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentMark(r.agent, 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(r.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), style = TextStyle(textDirection = TextDirection.Content))
        }
        Text(listOf(r.agent.label, ago(r.startAt), count(r.files.size, "file changed", "files changed", "קובץ אחד השתנה", "קבצים השתנו"), receiptState(r, git)).joinToString(" · "),
            color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.rewound == null) {
                if (waiting > 0) Pill(tr("Commit these files", "קומיט לקבצים האלה"), Tone.PRIMARY, Glyph.CHECK, small = true) { onCommitThese() }
                Pill(
                    when { busy -> tr("Putting back…", "מחזיר…"); confirm -> tr("Put ${r.files.size} files back", "להחזיר ${r.files.size} קבצים"); else -> tr("Put back as before", "החזרה למצב שלפני") },
                    if (confirm) Tone.DARK else Tone.SOFT, Glyph.BACK, enabled = !busy, small = true,
                ) {
                    if (!confirm) { confirm = true; return@Pill }
                    busy = true
                    scope.launch { error = withContext(Dispatchers.IO) { Receipts.rewind(r) }; busy = false; confirm = false }
                }
                if (confirm && !busy) TextAction(tr("Cancel", "ביטול")) { confirm = false }
            } else {
                Text(tr("Back as before this session.", "חזר למצב שלפני הסשן."), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
                Pill(if (busy) tr("Bringing back…", "מחזיר…") else tr("Undo", "ביטול ההחזרה"), Tone.SOFT, enabled = !busy, small = true) {
                    busy = true
                    scope.launch { error = withContext(Dispatchers.IO) { Receipts.undoRewind(r) }; busy = false }
                }
            }
        }
        if (confirm) Text(tr("Only the files this session changed go back. A copy of them as they are now is kept, so this can be undone.",
            "רק הקבצים שהסשן הזה שינה חוזרים אחורה. עותק שלהם כמו שהם עכשיו נשמר, כך שאפשר לבטל."), color = c.faint, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PlexSans)
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.surface2))
    val diffs by produceState<List<Pair<GitChange, List<DiffLine>>>?>(null, r.id, r.after) {
        value = withContext(Dispatchers.IO) { r.files.take(60).map { it to Receipts.diff(r, it.path).take(800) } }
    }
    val all = diffs ?: return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyColumn(Modifier.fillMaxSize().padding(bottom = 10.dp)) {
            all.forEach { (ch, lines) ->
                item(key = "h-" + ch.path) {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        KindBadge(ch.kind)
                        Spacer(Modifier.width(10.dp))
                        Mono(ch.path, c.fg, 13)
                    }
                }
                itemsIndexed(lines, key = { i, _ -> ch.path + "#" + i }) { i, l -> DiffRow(l, i == 0) }
            }
        }
    }
}

@Composable
private fun History(p: Project, dir: File, git: GitInfo) {
    val c = LocalDeck.current
    val commits by produceState<List<GitCommit>?>(null, git.branch, git.lastAt) { value = withContext(Dispatchers.IO) { GitOps.log(dir, 40) } }
    PanelHead {
        Text(tr("Latest commits on ${git.branch}", "הקומיטים האחרונים ב־${git.branch}"), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        git.web?.let { web -> TextAction(tr("Open on ${hostName(web)}", "פתיחה ב־${hostName(web)}"), accent = true) { Chrome.open(p, DeckAction(Catalog.LINK, web, hostName(web))) } }
    }
    val list = commits ?: return
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Text(tr("No commits yet. The first one starts the history.", "עוד אין קומיטים. הראשון יפתח את ההיסטוריה."), color = c.faint, fontSize = 14.sp, fontFamily = PlexSans)
        }
        return
    }
    // The newest ones the server doesn't have yet: all of them while the branch isn't there at all.
    val local = when { !git.hasRemote -> 0; git.upstream == null -> list.size; else -> git.ahead }
    LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        itemsIndexed(list, key = { _, cm -> cm.hash }) { i, cm ->
            val pushed = i >= local
            // A commit's page on the server exists once it's there.
            val url = git.web?.takeIf { pushed }?.let { w -> if (hostName(w) == "GitLab") "$w/-/commit/${cm.hash}" else "$w/commit/${cm.hash}" }
            Column(Modifier.fillMaxWidth().then(if (url != null) Modifier.clickable { Chrome.open(p, DeckAction(Catalog.LINK, url, hostName(url))) } else Modifier)
                .padding(horizontal = 22.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(cm.subject, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono(cm.hash, c.accentText, 11)
                    Text("  ·  ${cm.author}  ·  ${ago(cm.at)}", color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1)
                    if (!pushed) {
                        Spacer(Modifier.width(10.dp))
                        Dot(CHANGED_DOT, 6.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(tr("not on the server yet", "עוד לא בשרת"), color = c.amberText, fontSize = 12.sp, fontFamily = PlexSans)
                    }
                }
            }
        }
    }
}

// ── the sheet over the strip ──

/** Git over File Explorer: the branch and its server, what changed, and a commit, without leaving the folder. */
@Composable
fun GitSheet(p: Project, dark: Boolean, onClose: () -> Unit, onOpen: (Project) -> Unit, local: Local = Settings.state.collectAsState().value.localOf(p.id)) {
    val c = LocalDeck.current
    val dir = local.dir
    val git = dir?.let { Git.state.collectAsState().value[it.path] }
    var error by remember(p.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(p, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (git != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.BRANCH, 13.dp, c.muted)
                    Spacer(Modifier.width(5.dp))
                    Mono(git.branch, c.muted, 12)
                }
            }
            Pill(tr("All of git", "כל ה־git"), Tone.TINT, small = true) { onOpen(p); onClose() }
        }
        if (dir == null || git == null) {
            Text(tr("This folder isn't in git yet. Start it from the project's Git tab.", "התיקייה עוד לא ב־git. מפעילים אותו מלשונית Git של הפרויקט."), color = c.muted, fontSize = 14.sp,
                lineHeight = 20.sp, fontFamily = PlexSans)
            return@Column
        }
        PhaseNote(git)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(syncWords(git), color = c.muted, fontSize = 12.5.sp, lineHeight = 17.sp, fontFamily = PlexSans)
            if (git.hasRemote) SyncButtons(dir, git) { error = it }
            error?.let { Text(it, color = c.danger, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PlexSans) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.surface2))
        Column(Modifier.weight(1f)) {
            if (git.files.isEmpty()) AllSaved()
            else {
                GroupLabel(tr("Changes", "שינויים") + "  ${git.files.size}")
                val room = 5
                git.files.take(room).forEach { ch ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(p); onClose() }.padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        KindBadge(ch.kind)
                        Spacer(Modifier.width(10.dp))
                        FileName(ch, Modifier.weight(1f))
                    }
                }
                if (git.files.size > room) Text(tr("and ${git.files.size - room} more", "ועוד ${git.files.size - room}"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
        }
        if (git.files.isNotEmpty()) CommitBox(p.id + "-sheet", dir, null, compact = true)
    }
}
