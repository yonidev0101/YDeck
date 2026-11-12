package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
                quoted(open.title) + " · " + ago(open.endAt, now), open.id))
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
        if (receiptId != null) UsageStats.mark(UsageStats.Feature.RECEIPT)
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
    val loops = loopsOf(local).filter { it.kind != LoopKind.AGENT }
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
            local.dir?.let { CloseWithClaude(p, it, small = true) }
        }
        Group { loops.forEach { LoopRow(p, local, it) } }
    }
}

/*
 * The home page, Now: where every project stands, the one that needs you first. Claude waiting, then Claude at work,
 * then something left open (each with what closes it), then, folded into one quiet list, what is all saved.
 */
@Composable
fun LoopsPage(deck: Deck) {
    val c = LocalDeck.current
    androidx.compose.runtime.LaunchedEffect(Unit) { UsageStats.mark(UsageStats.Feature.NOW) }
    val dark = c == Dark
    val settings by Settings.state.collectAsState()
    val pulses by Pulses.state.collectAsState()
    val rows = deck.projects.mapNotNull { p ->
        val local = settings.localOf(p.id)
        if (local.folder == null) return@mapNotNull null
        NowRow(p, local, claudeIn(local.folder), loopsOf(local).filter { it.kind != LoopKind.AGENT })
    }
    // Three numbers before any words, as on the phone: waiting for you (Claude asking, or something open), Claude at work,
    // steps left. Each one keeps only those projects below; again, all of them.
    var filter by remember { mutableStateOf<NowFilter?>(null) }
    val needsYou = rows.count { it.rank == 0 || it.loops.isNotEmpty() }
    val working = rows.count { it.rank == 1 }
    val left = rows.sumOf { pulses[it.local.folder]?.left?.size ?: 0 }
    val kept = when (filter) {
        NowFilter.NEEDS_YOU -> rows.filter { it.rank == 0 || it.loops.isNotEmpty() }
        NowFilter.WORKING -> rows.filter { it.rank == 1 }
        NowFilter.LEFT -> rows.filter { pulses[it.local.folder]?.left?.isNotEmpty() == true }
        null -> rows
    }
    val (busy, calm) = kept.sortedBy { it.rank }.partition { it.rank < 3 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 30.dp)) {
        Column(Modifier.widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PageTitle(tr("Now", "עכשיו"))
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Where every project stands: what Claude is doing, what waits for you, and what isn't saved.",
                        "איפה כל פרויקט עומד: מה Claude עושה, מה מחכה לך, ומה לא נשמר."), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
                }
                Spacer(Modifier.width(16.dp))
                if (busy.any { it.loops.isNotEmpty() }) Pill(tr("Close the day", "סגירת היום"), Tone.DARK, Glyph.MOON) { Nav.set(Screen.CloseDay) }
            }
            if (rows.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NowStat(needsYou, tr("waiting for you", "מחכה לך"), NowFilter.NEEDS_YOU, filter, Modifier.weight(1f)) { filter = if (filter == it) null else it }
                NowStat(working, tr("at work", "עובד"), NowFilter.WORKING, filter, Modifier.weight(1f)) { filter = if (filter == it) null else it }
                NowStat(left, tr("left", "נשארו"), NowFilter.LEFT, filter, Modifier.weight(1f)) { filter = if (filter == it) null else it }
            }
            // Installs from before the first run asked: the choice about usage counts, once. Nothing is sent before it.
            if (!settings.statsAsked && settings.welcomed) UsageStatsChoice(settings.usageStats, {}) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(tr("Keep it on", "להשאיר פועל"), Tone.PRIMARY, small = true) { Settings.update { it.copy(usageStats = true, statsAsked = true) } }
                    TextAction(tr("Turn off", "לכבות")) { Settings.update { it.copy(usageStats = false, statsAsked = true) } }
                }
            }
            busy.forEach { r -> NowProject(r, pulses[r.local.folder], dark) }
            if (busy.isEmpty() && filter == null) Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Dot(CLEAN_DOT, 14.dp)
                Spacer(Modifier.height(16.dp))
                Text(tr("Nothing open", "אין שום דבר פתוח"), color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                Spacer(Modifier.height(4.dp))
                Text(tr("Every change is committed, pushed, and no agent is waiting.", "כל שינוי שמור ודחוף, ואף סוכן לא מחכה."), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
            }
            // The calm ones, one line each: all saved, and where they'll pick up.
            if (calm.isNotEmpty()) Group(tr("All saved", "הכול שמור")) {
                calm.forEach { r ->
                    val next = r.local.note.ifBlank { pulses[r.local.folder]?.suggestion.orEmpty() }
                    Item({ Nav.set(Screen.Project(r.p.id)) }) {
                        pulses[r.local.folder]?.progress()?.let { (d, t) -> RingedLogo(r.p, d, t, 32.dp) } ?: Logo(r.p, 22.dp)
                        Text(r.p.name, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans)
                        Text(next, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f), style = TextStyle(textDirection = TextDirection.Content))
                        planCount(pulses[r.local.folder])?.let { Text(it, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1) }
                        Dot(CLEAN_DOT)
                    }
                }
            }
        }
    }
}

private enum class NowFilter { NEEDS_YOU, WORKING, LEFT }

/** One of Now's three numbers: the count large, a dot in its state's colour and the word. A click keeps only those. */
@Composable
private fun NowStat(n: Int, word: String, f: NowFilter, on: NowFilter?, modifier: Modifier, onClick: (NowFilter) -> Unit) {
    val c = LocalDeck.current
    val hot = f == NowFilter.NEEDS_YOU && n > 0
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(if (hot) CHANGED_DOT.copy(alpha = if (c == Dark) 0.12f else 0.10f) else c.surface2)
            .then(if (on == f) Modifier.border(1.5.dp, c.fg, shape) else Modifier)
            .clickable { onClick(f) }.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("$n", color = if (hot) c.amberText else c.fg, fontSize = 28.sp, lineHeight = 32.sp, fontFamily = PlexMono, fontWeight = FontWeight.Medium)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            when (f) {
                NowFilter.NEEDS_YOU -> Dot(CHANGED_DOT, 7.dp)
                NowFilter.WORKING -> Dot(claudeColor(ClaudeMode.WORKING, c == Dark), 7.dp)
                NowFilter.LEFT -> Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).border(1.5.dp, c.faint, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.width(7.dp))
            Text(word, color = if (hot) c.amberText else c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 1)
        }
    }
}

/** "3 left · 2 ideas set aside", or null when both lists are empty. */
fun planCount(pulse: Pulse?): String? {
    val left = pulse?.left?.size ?: 0
    val ideas = pulse?.ideas?.size ?: 0
    return listOfNotNull(
        left.takeIf { it > 0 }?.let { tr(if (it == 1) "1 thing left" else "$it left", if (it == 1) "נשאר דבר אחד" else "נשארו $it") },
        ideas.takeIf { it > 0 }?.let { tr(if (it == 1) "1 idea set aside" else "$it ideas set aside", if (it == 1) "רעיון אחד בצד" else "$it רעיונות בצד") },
    ).joinToString(" · ").ifEmpty { null }
}

/** A project on Now: Claude waiting comes first, then at work, then something open, then all saved. */
private class NowRow(val p: Project, val local: Local, val session: ClaudeSession?, val loops: List<Loop>) {
    val rank = when {
        session?.mode == ClaudeMode.WAITING -> 0
        session?.mode == ClaudeMode.WORKING -> 1
        loops.isNotEmpty() -> 2
        else -> 3
    }
}

/** A project that needs a look: where Claude is in it, what it did last time, what's open, and closing it for today. */
@Composable
private fun NowProject(r: NowRow, pulse: Pulse?, dark: Boolean) {
    val c = LocalDeck.current
    val next = r.local.note.ifBlank { pulse?.suggestion.orEmpty() }
    val handoff = pulse?.handoff?.takeIf { it.done.isNotBlank() }
    val live = r.session?.takeIf { (it.mode == ClaudeMode.WORKING || it.mode == ClaudeMode.WAITING) && it.detail.isNotBlank() }
    Column {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { Nav.set(Screen.Project(r.p.id)) }.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                pulse?.progress()?.let { (d, t) -> RingedLogo(r.p, d, t, 42.dp) } ?: Logo(r.p, 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.p.name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                        r.session?.takeIf { it.mode != ClaudeMode.DONE }?.let { s ->
                            Spacer(Modifier.width(12.dp))
                            ClaudeDot(s.mode, dark, 7.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(claudeText(s), color = if (s.mode == ClaudeMode.OPEN) c.muted else claudeColor(s.mode, dark), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                        }
                    }
                    if (next.isNotBlank()) Text(tr("Next: ", "הבא: ") + next, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content))
                    planCount(pulse)?.let { Text(it, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1) }
                }
            }
            if (r.loops.isNotEmpty()) r.local.dir?.let { CloseWithClaude(r.p, it, small = true) }
        }
        if (live == null && handoff == null && r.loops.isEmpty()) return@Column
        Group {
            // What Claude is on now (its prompt, or its question), then what it did last time, then what's open.
            if (live != null) Item {
                ClaudeDot(live.mode, dark, 7.dp)
                Text(if (live.mode == ClaudeMode.WORKING) quoted(live.detail) else live.detail, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), style = TextStyle(textDirection = TextDirection.Content))
                Text(ago(live.workStart), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
            }
            if (handoff != null) Item {
                GlyphIcon(Glyph.CLAUDE, 13.dp, c.faint)
                Text(tr("Last time: ", "בפעם הקודמת: ") + handoff.done, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), style = TextStyle(textDirection = TextDirection.Content))
                Text(ago(handoff.at), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
            }
            r.loops.forEach { LoopRow(r.p, r.local, it) }
        }
    }
}

/** How many loops are open in every project (the sidebar's count). */
@Composable
fun openLoopCount(deck: Deck): Int {
    val settings by Settings.state.collectAsState()
    return deck.projects.sumOf { loopsOf(settings.localOf(it.id)).size }
}
