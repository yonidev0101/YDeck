package com.yonidev.ydeck.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/*
 * Ask YDeck: questions about every project, answered by the developer's own Claude Code (no key of ours, no server
 * of ours). It lives in the search window (Ctrl+Alt+Space, from anywhere) and, to go on there, beside the app's page.
 * The first question carries what YDeck knows about all the projects; the ones after it continue the same Claude
 * session (--resume). Claude may read the projects' folders to answer, never change them, nor read the files that
 * hold keys.
 */

object Assistant {
    /** A question and its answer (or what went wrong); [reads]: the files Claude looked at, [seconds] it took. */
    data class Turn(val question: String, val answer: String? = null, val error: String? = null, val reads: Int = 0, val seconds: Int = 0)

    private val _open = MutableStateFlow(false)
    /** The panel beside the app's page is open. */
    val open: StateFlow<Boolean> = _open
    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _step = MutableStateFlow<String?>(null)
    /** What Claude is doing now, in words (the file it reads), while it works. */
    val step: StateFlow<String?> = _step
    private val _answeredAt = MutableStateFlow(0L)
    /** When the last answer came, for the mark's green landing. */
    val answeredAt: StateFlow<Long> = _answeredAt

    /** Claude Code's session for this conversation, once the first answer came; a new conversation starts without. */
    @Volatile private var session: String? = null
    /** The folder the conversation runs in: Claude Code keeps a session with its folder, so the questions after the
     *  first go on from the same one, whichever project they're about. */
    @Volatile private var sessionHome: java.io.File? = null
    @Volatile private var proc: Process? = null
    /** Bumps with each new conversation, so an answer to the one before doesn't land in it. */
    @Volatile private var generation = 0

    /** A conversation to draw, for the renders. */
    fun preview(turns: List<Turn>) { _turns.value = turns }

    fun toggle() { _open.value = !_open.value }
    fun show() { _open.value = true }
    fun close() { _open.value = false }

    fun reset() {
        generation++
        proc?.destroyForcibly()
        session = null
        sessionHome = null
        _turns.value = emptyList()
        _step.value = null
    }

    /** Asks [question]; [focus] is the project on the page, so "this project" means it. */
    fun ask(question: String, focus: Project?) {
        val q = question.trim()
        if (q.isEmpty() || _busy.value) return
        _busy.value = true
        _step.value = null
        _turns.update { it + Turn(q) }
        val gen = generation
        val start = System.currentTimeMillis()
        Thread({
            var reads = 0
            val r = runCatching { run(q, focus, gen) { reads++ } }
            val seconds = ((System.currentTimeMillis() - start) / 1000).toInt()
            if (gen == generation) {
                _turns.update { list -> list.dropLast(1) + r.fold({ Turn(q, answer = it, reads = reads, seconds = seconds) }, { Turn(q, error = it.message ?: tr("Claude didn't answer", "Claude לא ענה")) }) }
                if (r.isSuccess) _answeredAt.value = System.currentTimeMillis()
            }
            _step.value = null
            _busy.value = false
        }, "ydeck-assistant").apply { isDaemon = true; start() }
    }

    private fun run(question: String, focus: Project?, gen: Int, onRead: () -> Unit): String {
        val resume = session
        val home = sessionHome?.takeIf { resume != null } ?: homeOf(focus)
        if (resume == null) sessionHome = home
        return try {
            answer(question, focus, resume, home, phone = false,
                onSession = { if (gen == generation) session = it },
                onStep = { _step.value = it; onRead() },
                onProcess = { proc = it })
        } catch (e: Exception) {
            if (resume == null && gen == generation) session = null
            throw e
        }
    }

    /**
     * One question to Claude Code, answered: the first of a conversation carries what YDeck knows ([prompt]); with
     * [resume] it goes on in that conversation. [onSession] gets the conversation's id, [onStep] each thing Claude
     * reads, [onProcess] the process (to stop it). Throws with why, in words, when there's no answer.
     */
    fun answer(question: String, focus: Project?, resume: String?, home: java.io.File, phone: Boolean, onSession: (String) -> Unit, onStep: (String) -> Unit, onProcess: (Process?) -> Unit): String {
        val deck = Repo.deck.value
        val text = if (resume == null) prompt(deck, focus, question, phone) else followUp(focus, question)
        return read(text, home, resume, onSession, onStep, onProcess)
    }

    /** Where a conversation about [focus] runs: the project's folder, or YDeck's own for every project. */
    fun homeOf(focus: Project?): java.io.File = focus?.let { Settings.state.value.localOf(it.id).dir } ?: AppDirs.data

    /**
     * Claude Code (Sonnet), reading only, answers [text]: from [home], with every project's folder within reach and
     * YDeck's own tools, never the files that hold keys. With [resume] it goes on in that conversation. Throws with why,
     * in words, when there's no answer.
     */
    fun read(text: String, home: java.io.File, resume: String?, onSession: (String) -> Unit, onStep: (String) -> Unit, onProcess: (Process?) -> Unit): String {
        val claude = Tools.claude?.exe ?: error(tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב"))
        val dirs = Repo.deck.value.projects.mapNotNull { Settings.state.value.localOf(it.id).dir }
        // Read-only, every project's folder within reach, and never the files that hold keys.
        val cmd = buildList {
            // Sonnet: quick enough for a question, and lighter on the developer's plan than their default model.
            addAll(listOf(claude.path, "-p", "--model", "sonnet", "--output-format", "stream-json", "--verbose"))
            if (resume != null) { add("--resume"); add(resume) }
            dirs.forEach { add("--add-dir"); add(it.path) }
            // YDeck's own tools too (all read only, see [YDeckMcp]): the sessions running now, a search across the projects.
            addAll(listOf("--allowedTools", "Read", "Glob", "Grep", "LS", "mcp__plugin_ydeck_ydeck", "--disallowedTools"))
            addAll(ClaudeFill.SECRETS)
        }
        val p = ProcessBuilder(cmd).directory(home).start()
        onProcess(p)
        p.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        val err = StringBuilder()
        Thread { runCatching { err.append(p.errorStream.bufferedReader(Charsets.UTF_8).readText()) } }.apply { isDaemon = true; start() }
        var answer: String? = null
        var failed: String? = null
        val reader = Thread {
            runCatching {
                p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                    o.optString("session_id").takeIf { it.isNotBlank() }?.let(onSession)
                    when (o.optString("type")) {
                        "assistant" -> ClaudeFill.stepOf(o, home)?.let(onStep)
                        "result" -> if (o.optBoolean("is_error") || !o.optString("subtype").startsWith("success")) failed = o.optString("result") else answer = o.optString("result")
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        if (!p.waitFor(5, TimeUnit.MINUTES)) { p.destroyForcibly(); error(tr("Claude took too long. Try asking again.", "Claude לקח יותר מדי זמן. אפשר לשאול שוב.")) }
        reader.join(3000)
        onProcess(null)
        answer?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        error(ClaudeFill.why(failed.orEmpty() + "\n" + err))
    }

    /** The first question: the projects as YDeck knows them, then the question. */
    fun prompt(deck: Deck, focus: Project?, question: String, phone: Boolean = false): String = buildString {
        appendLine("You are YDeck, the user's project dashboard, answering their questions about their projects (\"Ask YDeck\"), from what YDeck knows (below) and, when needed, by reading the projects' folders. Do not change any file, and never read .env files or anything that holds a key.")
        appendLine("Answer in the language the user writes in, short and plain: the answer first, then only the details that help. Name the project each fact comes from. When YDeck doesn't know and the folders don't say, say so; don't guess.")
        if (phone) appendLine("The user asks from YDeck on their phone, away from the computer: keep it brief, and no wide tables or long code.")
        appendLine("Today is ${LocalDate.now()}.")
        appendLine()
        appendLine("The projects (${deck.projects.size}):")
        deck.projects.forEach { appendLine(); append(about(it)) }
        focus?.let { appendLine(); appendLine("The user is now looking at the project \"${it.name}\"; \"this project\" means it.") }
        appendLine()
        appendLine("The question:")
        append(question)
    }

    private fun followUp(focus: Project?, question: String): String =
        (focus?.let { "(This question is about the project \"${it.name}\".)\n" } ?: "(This question is about all the projects.)\n") + question

    /** One project: what it is, where it stands, what Claude does in it, its decisions, links and releases. */
    fun about(p: Project): String = buildString {
        val local = Settings.state.value.localOf(p.id)
        val dir = local.dir
        appendLine("## ${p.name}" + (if (p.kind.isNotBlank()) " (${p.kind})" else ""))
        appendLine("- folder: " + (dir?.path ?: "(none on this PC)"))
        if (dir == null) {
            if (p.actions.isNotEmpty()) appendLine("- links: " + p.actions.take(12).joinToString("; ") { "${it.label} ${it.url}" })
            if (local.note.isNotBlank()) appendLine("- next: ${local.note}")
            return@buildString
        }
        val shared = ProjectFiles.readShared(dir)
        val state = ProjectFiles.readLocal(dir)
        shared?.summary?.takeIf { it.isNotBlank() }?.let { appendLine("- summary: $it") }
        val now = state?.now.orEmpty()
        val next = state?.next?.ifBlank { null } ?: local.note
        if (now.isNotBlank()) appendLine("- now: $now")
        if (next.isNotBlank()) appendLine("- next: $next")
        state?.handoff?.let { h -> appendLine("- last session (${h.agent.ifBlank { "an agent" }}, ${java.time.Instant.ofEpochMilli(h.at)}): done \"${h.done}\"; suggested next \"${h.next}\"") }
        ClaudeLink.statusIn(dir.path, ClaudeLink.sessions.value)?.let { s -> appendLine("- Claude Code here now: ${s.mode.name.lowercase()}" + (if (s.detail.isNotBlank()) " (\"${s.detail.take(120)}\")" else "")) }
        val links = (p.actions.map { "${it.label} ${it.url}" } + shared?.links.orEmpty().map { "${it.label} ${it.url}" }).distinct()
        if (links.isNotEmpty()) appendLine("- links: " + links.take(16).joinToString("; "))
        val commands = (local.commands + shared?.commands.orEmpty()).distinctBy { it.line }
        if (commands.isNotEmpty()) appendLine("- commands: " + commands.take(10).joinToString("; ") { "${it.label}: ${it.line}" })
        shared?.releases?.take(4)?.takeIf { it.isNotEmpty() }?.let { r -> appendLine("- releases: " + r.joinToString("; ") { "${it.version} (${it.date}) ${it.note}" }) }
        shared?.decisions?.take(12)?.takeIf { it.isNotEmpty() }?.let { d ->
            appendLine("- decisions:")
            d.forEach { appendLine("  - ${it.text}" + (if (it.date.isNotBlank()) " (${it.date})" else "")) }
        }
    }
}

val ASK_TITLE: String get() = tr("Ask YDeck", "לשאול את YDeck")

// ── the mark ──

/** What the mark says: ready, listening (the user types), thinking, answered (lands once), waiting for the user. */
enum class Mood { READY, LISTENING, THINKING, ANSWERED, WAITING }

/** The mood from the conversation: thinking while Claude works, waiting while a session needs the developer (see
 *  [SessionAsks]), green for a moment after an answer. */
@Composable
fun askMood(typing: Boolean): Mood {
    val busy by Assistant.busy.collectAsState()
    val waiting by SessionAsks.waiting.collectAsState()
    val answeredAt by Assistant.answeredAt.collectAsState()
    var fresh by remember(answeredAt) { mutableStateOf(answeredAt > 0 && System.currentTimeMillis() - answeredAt < 4000) }
    LaunchedEffect(answeredAt) { if (fresh) { delay(4000); fresh = false } }
    return when {
        busy -> Mood.THINKING
        waiting.isNotEmpty() -> Mood.WAITING
        fresh -> Mood.ANSWERED
        typing -> Mood.LISTENING
        else -> Mood.READY
    }
}

/**
 * YDeck's small mark (the open ring and its dot, brand/ydeck-mark-small-ink.svg), in the dot's language: amber when
 * ready for you (it breathes while you type), indigo with the ring turning while Claude thinks, green landing once
 * for an answer, an amber wave when it needs your yes. Still otherwise.
 */
@Composable
fun AskMark(mood: Mood, size: Dp) {
    val c = LocalDeck.current
    val dark = c == Dark
    val ink = c.fg
    val dot = when (mood) {
        Mood.THINKING -> claudeColor(ClaudeMode.WORKING, dark)
        Mood.ANSWERED -> CLEAN_DOT
        else -> CHANGED_DOT
    }
    val spin = if (mood == Mood.THINKING) rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing))).value else 0f
    val breathe = if (mood == Mood.LISTENING) rememberInfiniteTransition().animateFloat(1f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse)).value else 1f
    val wave = if (mood == Mood.WAITING) rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing))).value else -1f
    val land = remember { Animatable(1f) }
    LaunchedEffect(mood) { if (mood == Mood.ANSWERED) { land.snapTo(0.3f); land.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) } }
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 100f
        rotate(spin) {
            drawArc(ink, startAngle = -4f, sweepAngle = 288f, useCenter = false, topLeft = Offset(16 * s, 16 * s), size = Size(68 * s, 68 * s),
                style = Stroke(16 * s, cap = StrokeCap.Round))
            val at = Offset(76 * s, 28.1f * s)
            if (wave >= 0f) drawCircle(dot.copy(alpha = 0.55f * (1 - wave)), 8.5f * s * (1 + 1.6f * wave), at)
            val scale = if (mood == Mood.ANSWERED) land.value else breathe
            drawCircle(dot.copy(alpha = if (mood == Mood.LISTENING) 0.45f + 0.55f * (breathe - 0.8f) / 0.2f else 1f), 8.5f * s * scale, at)
        }
    }
}

// ── the conversation (in the search window and beside the page) ──

/** The questions, the answers, and what Claude is doing while it works; examples to start from when empty. */
@Composable
fun AskThread(focus: Project?, modifier: Modifier = Modifier) {
    val c = LocalDeck.current
    val turns by Assistant.turns.collectAsState()
    val busy by Assistant.busy.collectAsState()
    val step by Assistant.step.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(turns.size, busy) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex) }
    Box(modifier) {
        if (turns.isEmpty()) Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Ask about any of your projects: where it stands, what was decided, where something is in the code.",
                "אפשר לשאול על כל אחד מהפרויקטים: איפה הוא עומד, מה הוחלט, איפה משהו נמצא בקוד."),
                color = c.muted, fontSize = 14.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content, lineHeight = 21.sp))
            if (Tools.claude == null) Text(tr("Claude Code isn't installed on this PC.", "Claude Code לא נמצא במחשב."), color = c.danger, fontSize = 13.sp, fontFamily = PlexSans)
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { examples(focus).forEach { q -> Suggestion(q) { Assistant.ask(q, focus) } } }
        } else LazyColumn(state = list, modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(turns) { i, t -> TurnView(t, working = i == turns.lastIndex && busy, step = step) }
        }
    }
}

@Composable
private fun TurnView(t: Assistant.Turn, working: Boolean, step: String?) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The question in a soft bubble at the start; the answer plain, the width of the column.
        Text(t.question, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, style = TextStyle(textDirection = dirOf(t.question), lineHeight = 21.sp),
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 9.dp))
        when {
            working -> Text(step ?: tr("Thinking…", "חושב…"), color = c.faint, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 1)
            t.answer != null -> {
                if (t.reads > 0) Text(tr("Read ${t.reads} files · ${t.seconds} s", "קרא ${t.reads} קבצים · ${t.seconds} שניות"), color = c.faint, fontSize = 12.5.sp, fontFamily = PlexSans)
                MarkdownView(t.answer, Modifier.fillMaxWidth())
            }
            t.error != null -> Text(t.error, color = c.danger, fontSize = 13.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

/** A question to start from: a quiet outlined pill. */
@Composable
private fun Suggestion(text: String, onClick: () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(99.dp)
    Text(text, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 1,
        modifier = Modifier.clip(shape).border(1.dp, c.line, shape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

/**
 * The question field: rounded, with what the question is about ([focus]: one project, or every project, picked from
 * the chip) and a round send button. Enter sends; Shift+Enter is a new line.
 */
@Composable
fun AskComposer(focus: Project?, onFocus: (Project?) -> Unit, text: String, onText: (String) -> Unit, field: FocusRequester = remember { FocusRequester() }) {
    val c = LocalDeck.current
    val busy by Assistant.busy.collectAsState()
    fun send() { if (!busy && text.isNotBlank()) { Assistant.ask(text, focus); onText("") } }
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth()) {
            val style = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, lineHeight = 21.sp, textDirection = TextDirection.Content)
            if (text.isEmpty()) Text(if (busy) tr("YDeck is answering…", "YDeck עונה…") else tr("Ask about your projects", "שאלה על הפרויקטים"), style = style.copy(color = c.faint))
            BasicTextField(text, onText, textStyle = style, cursorBrush = SolidColor(c.accent), maxLines = 6,
                modifier = Modifier.fillMaxWidth().focusRequester(field).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) && !e.isShiftPressed) { send(); true } else false
                })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScopeChip(focus, onFocus)
            Spacer(Modifier.weight(1f))
            val ready = !busy && text.isNotBlank()
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(if (ready) c.fg else c.line).clickable(enabled = ready) { send() },
                contentAlignment = Alignment.Center) { GlyphIcon(Glyph.UP, 16.dp, c.bg) }
        }
    }
}

/** What the question is about: every project, or one, picked from the projects. */
@Composable
private fun ScopeChip(focus: Project?, onFocus: (Project?) -> Unit) {
    val c = LocalDeck.current
    val deck by Repo.deck.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(99.dp)).background(c.surface2).clickable { open = true }.padding(start = 10.dp, end = 7.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (focus != null) { Logo(focus, 14.dp); Spacer(Modifier.width(6.dp)) }
            Text(focus?.name ?: tr("All projects", "כל הפרויקטים"), color = c.muted, fontSize = 12.5.sp, fontFamily = PlexSans, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            GlyphIcon(Glyph.CHEVRON, 13.dp, c.faint)
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            DropdownMenuItem(
                text = { Text(tr("All projects", "כל הפרויקטים"), color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, fontWeight = if (focus == null) FontWeight.Medium else FontWeight.Normal) },
                leadingIcon = { GlyphIcon(Glyph.WIDGET, 16.dp, c.muted) },
                trailingIcon = { if (focus == null) GlyphIcon(Glyph.CHECK, 14.dp, c.muted) },
                onClick = { open = false; onFocus(null) },
            )
            deck.projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, fontWeight = if (p.id == focus?.id) FontWeight.Medium else FontWeight.Normal, maxLines = 1) },
                    leadingIcon = { Logo(p, 18.dp) },
                    trailingIcon = { if (p.id == focus?.id) GlyphIcon(Glyph.CHECK, 14.dp, c.muted) },
                    onClick = { open = false; onFocus(p) },
                )
            }
        }
    }
}

/** The conversation's head: the mark, the name, and what's next to it (new chat, close or move). */
@Composable
fun AskHeader(mood: Mood, actions: @Composable () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AskMark(mood, 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(ASK_TITLE, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        actions()
    }
}

const val ASSISTANT_WIDTH = 380

/** Beside the app's page: the conversation goes on there (from the search window too). */
@Composable
fun AssistantPanel(deck: Deck, route: Screen) {
    val c = LocalDeck.current
    val turns by Assistant.turns.collectAsState()
    val page = ((route as? Screen.Project)?.id ?: (route as? Screen.Details)?.id)?.let(deck::byId)
    // What the questions are about: the project on the page, until another is picked (a new page picks again).
    var focus by remember(page?.id) { mutableStateOf(page) }
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    Row(Modifier.width(ASSISTANT_WIDTH.dp).fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        Column(Modifier.weight(1f).fillMaxHeight().background(c.bg)) {
            AskHeader(askMood(text.isNotEmpty())) {
                if (turns.isNotEmpty()) IconButton(Glyph.EDIT, tr("New chat", "שיחה חדשה"), size = 30.dp, tint = c.muted, filled = false) { Assistant.reset() }
                IconButton(Glyph.CLOSE, tr("Close", "סגירה"), size = 30.dp, tint = c.muted, filled = false) { Assistant.close() }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            WaitingCards(deck)
            AskThread(focus, Modifier.weight(1f).fillMaxWidth())
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AskComposer(focus, { focus = it }, text, { text = it }, field)
                AskFootnote()
            }
        }
    }
}

/** In the search window: the conversation, with a way to go on in the app. */
@Composable
fun ColumnScope.AskInWindow(onExpand: () -> Unit) {
    val c = LocalDeck.current
    val turns by Assistant.turns.collectAsState()
    var text by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf<Project?>(null) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    AskHeader(askMood(text.isNotEmpty())) {
        IconButton(Glyph.EXTERNAL, tr("Go on in YDeck", "להמשיך בחלון של YDeck"), size = 30.dp, tint = c.muted, filled = false, onClick = onExpand)
        if (turns.isNotEmpty()) IconButton(Glyph.EDIT, tr("New chat", "שיחה חדשה"), size = 30.dp, tint = c.muted, filled = false) { Assistant.reset() }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    WaitingCards(Repo.deck.collectAsState().value)
    AskThread(focus, Modifier.weight(1f).fillMaxWidth())
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { AskComposer(focus, { focus = it }, text, { text = it }, field) }
}

@Composable
fun AskFootnote() {
    val c = LocalDeck.current
    Text(tr("Answered by your Claude Code. It reads the projects' folders, never changes them.", "עונה ה־Claude Code שלך. הוא קורא את תיקיות הפרויקטים, אף פעם לא משנה אותן."),
        color = c.faint, fontSize = 11.5.sp, fontFamily = PlexSans, modifier = Modifier.fillMaxWidth(),
        style = TextStyle(textDirection = TextDirection.Content, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
}

/** Questions to start from, about the project on the page when there is one. */
private fun examples(focus: Project?): List<String> = if (focus != null) listOf(
    tr("Where did I stop in ${focus.name}?", "איפה עצרתי ב־${focus.name}?"),
    tr("What was decided in ${focus.name}, and why?", "מה הוחלט ב־${focus.name} ולמה?"),
    tr("What's waiting for me today?", "מה מחכה לי היום?"),
) else listOf(
    tr("What's waiting for me today?", "מה מחכה לי היום?"),
    tr("Which project should I work on next?", "על איזה פרויקט כדאי לעבוד עכשיו?"),
    tr("Which services does each project use?", "באילו שירותים כל פרויקט משתמש?"),
)

/** A question typed in the search window reads as one (rather than a project and what to open). */
fun looksLikeQuestion(query: String): Boolean {
    val q = query.trim()
    return q.endsWith("?") || q.split(Regex("\\s+")).size >= 4 ||
        Regex("^(what|why|how|where|when|which|who|is|are|did|does|can|מה|למה|איך|איפה|מתי|איזה|איזו|מי|האם)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
}
