package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Open loops: everything in a project that waits for the developer, so none of it has to be kept in mind. An agent
 * waiting for an answer, a session whose changes aren't committed, commits not pushed, a server left running. Each
 * comes with the one thing that closes it.
 */

enum class LoopKind { AGENT, CONFLICTS, SESSION, UNCOMMITTED, PUSH, PUBLISH, PULL, SERVER, ENV }

data class Loop(val kind: LoopKind, val title: String, val detail: String = "", val receipt: String? = null)

object Loops {
    /** A session's uncommitted changes stay a loop this long; older ones are plain uncommitted changes. */
    private const val SESSION_DAYS = 7L

    fun of(git: GitInfo?, pulse: Pulse?, session: ClaudeSession?, receipts: List<Receipt>, now: Long = System.currentTimeMillis()): List<Loop> = buildList {
        if (session != null && session.mode == ClaudeMode.WAITING) add(Loop(LoopKind.AGENT, claudeText(session), session.detail))
        if (git != null) {
            if (git.conflicts > 0 || git.phase != GitPhase.NONE) add(Loop(LoopKind.CONFLICTS,
                if (git.conflicts > 0) count(git.conflicts, "file with conflicts", "files with conflicts", "קובץ אחד בהתנגשות", "קבצים בהתנגשות")
                else tr("A merge or rebase to finish", "מיזוג או rebase לסיים")))
            // The latest session whose changes are still waiting to be committed.
            val changed = git.files.map { it.path }.toSet()
            val open = receipts.firstOrNull { r -> r.rewound == null && r.files.isNotEmpty() && now - r.endAt < SESSION_DAYS * 86_400_000L && r.files.any { it.path in changed } }
            val inSession = open?.files?.count { it.path in changed } ?: 0
            if (open != null) add(Loop(LoopKind.SESSION,
                tr("${open.agent.label}'s session: " + count(inSession, "file not committed", "files not committed", "", ""),
                    if (inSession == 1) "קובץ אחד מהסשן של ${open.agent.label} לא נשמר" else "$inSession קבצים מהסשן של ${open.agent.label} לא נשמרו"),
                "“${open.title}” · ${ago(open.endAt, now)}", open.id))
            val rest = git.changes - inSession
            if (rest > 0) add(Loop(LoopKind.UNCOMMITTED, count(rest, if (open != null) "other uncommitted change" else "uncommitted change",
                if (open != null) "other uncommitted changes" else "uncommitted changes", if (open != null) "עוד שינוי אחד לא שמור" else "שינוי אחד לא שמור",
                if (open != null) "שינויים נוספים לא שמורים" else "שינויים לא שמורים")))
            if (git.ahead > 0) add(Loop(LoopKind.PUSH, count(git.ahead, "commit not pushed", "commits not pushed", "קומיט אחד לא נדחף", "קומיטים לא נדחפו"), git.upstream.orEmpty()))
            else if (git.hasRemote && git.upstream == null && git.lastAt > 0) add(Loop(LoopKind.PUBLISH, tr("Branch ${git.branch} isn't on the server", "הענף ${git.branch} לא בשרת")))
            if (git.behind > 0) add(Loop(LoopKind.PULL, count(git.behind, "new commit on the server", "new commits on the server", "קומיט חדש בשרת", "קומיטים חדשים בשרת"), git.upstream.orEmpty()))
        }
        pulse?.port?.let { add(Loop(LoopKind.SERVER, tr("A dev server is running", "שרת פיתוח רץ"), "localhost:$it")) }
        if (!pulse?.missingEnv.isNullOrEmpty()) add(Loop(LoopKind.ENV, count(pulse!!.missingEnv.size, "key missing in .env", "keys missing in .env", "חסר מפתח ב־.env", "מפתחות חסרים ב־.env"), pulse.missingEnv.first()))
    }

    /** What Claude Code is told when the developer goes over a project's loops with it: what's open, then how to work. */
    fun brief(p: Project, loops: List<Loop>, next: String): String {
        val open = loops.joinToString(", ") { if (it.detail.isBlank()) it.title else "${it.title} (${it.detail})" }
        val then = next.takeIf { it.isNotBlank() }?.let { tr(" The next step noted for it: $it.", " הצעד הבא שרשום לו: $it.") }.orEmpty()
        return tr(
            "YDeck shows what's still open in ${p.name}: $open.$then Look at git status, the diff and the recent log, tell me briefly what each change is, " +
                "and propose what to do: what to commit together and with what message, what to push, what to leave or drop. " +
                "We decide together - commit, push or throw away nothing until I say so.",
            "ב־YDeck רואים מה עוד פתוח ב־${p.name}: $open.$then תסתכל על git status, על ה־diff ועל הקומיטים האחרונים, תגיד לי בקצרה מה כל שינוי, " +
                "ותציע מה לעשות: מה לשמור יחד ובאיזו הודעת קומיט, מה לדחוף, ומה להשאיר או לזרוק. " +
                "מחליטים ביחד - לא עושים קומיט, דחיפה או מחיקה עד שאני מאשר.",
        )
    }
}

/** Opens Claude Code in the project's folder with everything open there, to settle it together. */
fun goOver(p: Project, local: Local, loops: List<Loop>) {
    val claude = Tools.claude ?: return
    val dir = local.dir ?: return
    val next = local.note.ifBlank { Pulses.state.value[local.folder]?.handoff?.next.orEmpty() }
    Tools.launch(claude, dir, p.name, prompt = Loops.brief(p, loops, next))
}

/** The project's open loops, kept fresh. */
@Composable
fun loopsOf(local: Local): List<Loop> {
    val folder = local.folder ?: return emptyList()
    val git = Git.state.collectAsState().value[folder]
    val pulse = Pulses.state.collectAsState().value[folder]
    val receipts = Receipts.state.collectAsState().value[folder].orEmpty()
    val session = claudeIn(folder)
    return remember(git, pulse, receipts, session) { Loops.of(git, pulse, session, receipts) }
}

/** The session the Git tab opens on (set before going there from a loop or a receipt). */
object GitFocus {
    val receipt = MutableStateFlow<String?>(null)

    fun open(p: Project, receiptId: String?) {
        receipt.value = receiptId
        Nav.set(Screen.Project(p.id, Tab.GIT))
    }
}

/** Open is amber, at work is indigo (a server running), an error is red; green is kept for what's closed. */
private fun loopDot(kind: LoopKind): Color = when (kind) {
    LoopKind.CONFLICTS -> Color(0xFFEF4444)
    LoopKind.SERVER -> LIVE_DOT
    else -> CHANGED_DOT
}

/** One loop: what's open, and the action that closes it. */
@Composable
fun GroupScope.LoopRow(p: Project, local: Local, loop: Loop) {
    val c = LocalDeck.current
    val scope = rememberCoroutineScope()
    val dir = local.dir
    var error by remember(loop) { mutableStateOf<String?>(null) }
    val busy = dir?.let { GitOps.busy.collectAsState().value[it.path] }
    fun git(op: (File) -> String?) { if (dir != null) scope.launch { error = withContext(Dispatchers.IO) { op(dir) } } }
    Item {
        Dot(loopDot(loop.kind))
        Column(Modifier.weight(1f)) {
            Text(loop.title, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = error ?: loop.detail
            if (detail.isNotBlank()) Text(detail, color = if (error != null) c.danger else c.muted, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PlexSans,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        }
        when (loop.kind) {
            LoopKind.SESSION -> Pill(tr("Review", "לבדיקה"), Tone.TINT, small = true) { GitFocus.open(p, loop.receipt) }
            LoopKind.UNCOMMITTED, LoopKind.CONFLICTS -> Pill(tr("Commit", "לקומיט"), Tone.SOFT, small = true) { GitFocus.open(p, null) }
            LoopKind.PUSH -> Pill(if (busy == GitOps.Op.PUSH) tr("Pushing…", "דוחף…") else tr("Push", "דחיפה"), Tone.SOFT, Glyph.UP, enabled = busy == null, small = true) { git(GitOps::push) }
            LoopKind.PUBLISH -> Pill(if (busy == GitOps.Op.PUSH) tr("Pushing…", "דוחף…") else tr("Publish", "העלאה"), Tone.SOFT, Glyph.UP, enabled = busy == null, small = true) { git(GitOps::push) }
            LoopKind.PULL -> Pill(if (busy == GitOps.Op.PULL) tr("Pulling…", "מושך…") else tr("Pull", "משיכה"), Tone.SOFT, Glyph.DOWN, enabled = busy == null, small = true) { git(GitOps::pull) }
            LoopKind.SERVER -> Pill(tr("Open", "פתיחה"), Tone.SOFT, small = true) { Chrome.open(p, DeckAction(Catalog.LINK, "http://${loop.detail}", "localhost")) }
            LoopKind.AGENT, LoopKind.ENV -> {}
        }
    }
}

/** The project's loops on its overview; nothing open says so, briefly. */
@Composable
fun LoopsCard(p: Project, local: Local) {
    val c = LocalDeck.current
    val loops = loopsOf(local)
    if (loops.isEmpty()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.paper).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.CHECK, 16.dp, c.liveText)
            Spacer(Modifier.width(10.dp))
            Text(tr("Nothing open here. Everything is saved and on its way.", "אין כאן שום דבר פתוח. הכול שמור ובדרך."), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
        }
        return
    }
    Column {
        Section(tr("Open", "פתוח") + "  ${loops.size}") {
            if (Tools.claude != null && local.dir != null) TextAction(tr("Go over it with Claude", "לעבור על זה עם Claude"), accent = true) { goOver(p, local, loops) }
        }
        Group { loops.forEach { LoopRow(p, local, it) } }
    }
}

/** Every project's open loops on one page: the end of the day is closing them. */
@Composable
fun LoopsPage(deck: Deck) {
    val c = LocalDeck.current
    val settings by Settings.state.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 30.dp)) {
        Column(Modifier.widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PageTitle(tr("Open loops", "מה פתוח"))
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Everything that waits for you, in every project. Close them, and keep nothing in your head.",
                        "כל מה שמחכה לך, בכל הפרויקטים. סוגרים, ולא מחזיקים כלום בראש."), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
                }
                Spacer(Modifier.width(16.dp))
                Pill(tr("Close the day", "סגירת היום"), Tone.DARK, Glyph.MOON) { Nav.set(Screen.CloseDay) }
            }
            var any = false
            deck.projects.forEach { p ->
                val local = settings.localOf(p.id)
                val loops = loopsOf(local)
                if (loops.isEmpty()) return@forEach
                any = true
                val next = local.note.ifBlank { Pulses.state.value[local.folder]?.suggestion.orEmpty() }
                Column {
                    // The project (it opens), its next step under its name, and settling it all with Claude.
                    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { Nav.set(Screen.Project(p.id)) }.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Logo(p, 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                                if (next.isNotBlank()) Text(tr("Next: ", "הבא: ") + next, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(textDirection = TextDirection.Content))
                            }
                        }
                        if (Tools.claude != null && local.dir != null) TextAction(tr("Go over it with Claude", "לעבור על זה עם Claude"), accent = true) { goOver(p, local, loops) }
                    }
                    Group { loops.forEach { LoopRow(p, local, it) } }
                }
            }
            if (!any) Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GlyphIcon(Glyph.CHECK, 30.dp, c.liveText)
                Spacer(Modifier.height(14.dp))
                Text(tr("Nothing open", "אין שום דבר פתוח"), color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                Spacer(Modifier.height(4.dp))
                Text(tr("Every change is committed, pushed, and no agent is waiting.", "כל שינוי שמור ודחוף, ואף סוכן לא מחכה."), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
            }
        }
    }
}

/** How many loops are open in every project (the sidebar's count). */
@Composable
fun openLoopCount(deck: Deck): Int {
    val settings by Settings.state.collectAsState()
    return deck.projects.sumOf { loopsOf(settings.localOf(it.id)).size }
}
