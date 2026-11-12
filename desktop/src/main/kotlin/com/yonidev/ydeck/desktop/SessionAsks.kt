package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Choice
import com.yonidev.ydeck.link.Waiting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/*
 * A Claude Code session waits for the developer, and the developer isn't at the terminal: YDeck holds the question,
 * drafts a reply from what the project has recorded, and the developer answers from YDeck's panel or the phone.
 *
 * Two kinds, through the "ydeck" plugin's hooks (see [ClaudePlugin.hooks]):
 *   a choice (Claude's AskUserQuestion tool), a PreToolUse hook that waits here: held only while nobody is at this
 *     computer, and let go the moment someone is (the question then shows in the terminal as always);
 *   a question in words at the end of a turn, a Stop hook in the background (asyncRewake): it blocks nothing; a reply
 *     wakes the session with it, and a message typed in the terminal lets it go.
 *
 * Never a permission: a request to run something or change a file is never held or answered here, it stays in the
 * terminal. A reply is words for Claude to read, and it goes only when the developer sends it.
 */
object SessionAsks {
    /** Nobody is at the computer: no key or mouse for this long. */
    private const val AWAY_MS = 3 * 60_000L
    /** Someone is back: a key or the mouse within this. */
    private const val BACK_MS = 5_000L
    /** How long a choice is held (the hook gives up at 10 minutes; the terminal shows it after). */
    private const val CHOICE_HOLD_MS = 9 * 60_000L
    /** How long a question in words waits for a reply (the background hook's own limit is an hour). */
    private const val TEXT_HOLD_MS = 55 * 60_000L

    /** A reply: words, or the option picked for each question of a choice. */
    class Answer(val text: String, val choices: Map<String, String> = emptyMap())

    private class Held(val session: String, val cwd: String, val input: JSONObject?, val answer: CompletableFuture<Answer?> = CompletableFuture())

    private val held = ConcurrentHashMap<String, Held>()
    private val _waiting = MutableStateFlow<List<Waiting>>(emptyList())
    /** The sessions waiting now, newest first, with YDeck's draft. */
    val waiting: StateFlow<List<Waiting>> = _waiting
    private val drafts = Executors.newSingleThreadExecutor { r -> Thread(r, "ydeck-drafts").apply { isDaemon = true } }
    private val drafted = ConcurrentHashMap.newKeySet<String>()

    val away: Boolean get() = Win32.idleMs() >= AWAY_MS

    /** Sessions to draw waiting, for the renders. */
    fun preview(list: List<Waiting>) { _waiting.value = list }

    /** Drafts are written on their own (off in tests, where no Claude should run). */
    @Volatile var autoDraft = true

    fun start() {
        // A question that came while someone was here gets its draft once they leave.
        Thread({
            while (true) {
                Thread.sleep(30_000)
                if (away) _waiting.value.filter { it.id !in drafted }.forEach { draft(it.id) }
            }
        }, "ydeck-asks").apply { isDaemon = true; start() }
    }

    /** One hook call on /claude/ask, held until it's answered or let go. Returns what the hook prints ("" for nothing). */
    fun hold(e: JSONObject): String {
        if (!Settings.state.value.sessionReplies) return ""
        return when (e.optString("hook_event_name")) {
            "PreToolUse" -> if (e.optString("tool_name") == "AskUserQuestion") choice(e) else ""
            "Stop" -> text(e)
            else -> ""
        }
    }

    private fun choice(e: JSONObject): String {
        if (!away) return ""
        val input = e.optJSONObject("tool_input") ?: return ""
        val questions = choices(input).takeIf { it.isNotEmpty() } ?: return ""
        val (id, h) = register(e, Waiting.CHOICE, "", questions, input) ?: return ""
        draft(id)
        val until = System.currentTimeMillis() + CHOICE_HOLD_MS
        var answer: Answer? = null
        while (System.currentTimeMillis() < until) {
            answer = runCatching { h.answer.get(1, TimeUnit.SECONDS) }.getOrNull()
            if (h.answer.isDone) break
            // Someone is back: the terminal shows the question as always.
            if (Win32.idleMs() < BACK_MS) break
        }
        drop(id)
        answer ?: return ""
        val answers = JSONObject()
        questions.forEach { q -> answers.put(q.question, answer.choices[q.question] ?: answer.text.ifBlank { return "" }) }
        val updated = JSONObject(input.toString()).put("answers", answers)
        return JSONObject().put("hookSpecificOutput", JSONObject().put("hookEventName", "PreToolUse")
            .put("permissionDecision", "allow").put("permissionDecisionReason", "Answered by the developer in YDeck")
            .put("updatedInput", updated)).toString()
    }

    private fun text(e: JSONObject): String {
        // A turn Claude goes on with because a hook asked (this one, too) isn't a new question.
        if (e.optBoolean("stop_hook_active")) return ""
        val message = e.optString("last_assistant_message").trim()
        if (!asks(message)) return ""
        val (id, h) = register(e, Waiting.TEXT, message.takeLast(1500), emptyList(), null) ?: return ""
        if (away) draft(id)
        val answer = runCatching { h.answer.get(TEXT_HOLD_MS, TimeUnit.MILLISECONDS) }.getOrNull()
        drop(id)
        answer ?: return ""
        return "The developer answered from YDeck, away from the terminal: ${answer.text}"
    }

    /** Claude's turn ends asking something: a question mark in its last lines. */
    fun asks(message: String): Boolean = message.takeLast(400).contains('?')

    /** The developer answers [id] (from YDeck's panel or the phone). False when it isn't waiting any more. */
    fun reply(id: String, answer: Answer): Boolean {
        val h = held[id] ?: return false
        return h.answer.complete(answer)
    }

    /** The developer answers in the terminal instead: [id] is let go. */
    fun dismiss(id: String) { held[id]?.answer?.complete(null) }

    /** The session went on (a message typed in the terminal) or ended: what it held is let go. */
    fun release(session: String) {
        held.values.filter { it.session == session }.forEach { it.answer.complete(null) }
    }

    fun isHeld(session: String): Boolean = held.values.any { it.session == session && !it.answer.isDone }

    private fun register(e: JSONObject, kind: String, text: String, questions: List<Choice>, input: JSONObject?): Pair<String, Held>? {
        val session = e.optString("session_id").ifBlank { return null }
        val cwd = e.optString("cwd").ifBlank { return null }
        // A new question replaces the session's earlier one.
        release(session)
        val id = java.util.UUID.randomUUID().toString()
        val h = Held(session, cwd, input)
        held[id] = h
        val project = projectAt(cwd)
        _waiting.value = listOf(Waiting(id, project?.id.orEmpty(), kind, text, questions, at = System.currentTimeMillis())) + _waiting.value
        val line = questions.firstOrNull()?.question ?: text.lines().lastOrNull { it.isNotBlank() }.orEmpty()
        ClaudeLink.markWaiting(session, line)
        return id to h
    }

    private fun drop(id: String) {
        held.remove(id)
        drafted.remove(id)
        _waiting.value = _waiting.value.filterNot { it.id == id }
    }

    private fun update(id: String, f: (Waiting) -> Waiting) {
        _waiting.value = _waiting.value.map { if (it.id == id) f(it) else it }
    }

    /** The project a session runs in: its folder, or a folder above it. */
    fun projectAt(cwd: String): Project? {
        val path = runCatching { File(cwd).canonicalPath }.getOrDefault(cwd).lowercase()
        return Repo.deck.value.projects.firstOrNull { p ->
            Settings.state.value.localOf(p.id).folder?.let { runCatching { File(it).canonicalPath }.getOrDefault(it).lowercase() }
                ?.let { path == it || path.startsWith("$it${File.separator}") } == true
        }
    }

    private fun choices(input: JSONObject): List<Choice> {
        val a = input.optJSONArray("questions") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { q ->
            Choice(q.optString("question"), q.optString("header"),
                (q.optJSONArray("options") ?: JSONArray()).let { o -> (0 until o.length()).mapNotNull { o.optJSONObject(it)?.optString("label")?.takeIf { l -> l.isNotBlank() } } },
                q.optBoolean("multiSelect"))
        }.filter { it.question.isNotBlank() }
    }

    // ── the draft ──

    /** YDeck's suggested reply, from the project's records, in the background; once per question. */
    fun draft(id: String) {
        if (!autoDraft || !drafted.add(id)) return
        update(id) { it.copy(drafting = true) }
        drafts.execute {
            val w = _waiting.value.firstOrNull { it.id == id } ?: return@execute
            val h = held[id] ?: return@execute
            val project = Repo.deck.value.byId(w.project)
            val home = project?.let { Settings.state.value.localOf(it.id).dir } ?: File(h.cwd)
            val r = runCatching { Assistant.read(draftPrompt(w, project), home, null, {}, {}, {}) }
            val o = r.getOrNull()?.let { text -> runCatching { JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)) }.getOrNull() }
            update(id) { cur ->
                if (o == null) cur.copy(drafting = false)
                else cur.copy(drafting = false, draft = o.optString("answer").trim().take(2000), source = o.optString("source").trim().take(300), sure = o.optBoolean("sure"),
                    draftChoices = o.optJSONObject("choices")?.let { c -> c.keys().asSequence().associateWith { c.optString(it) } }.orEmpty()
                        .filter { (q, label) -> cur.questions.firstOrNull { it.question == q }?.options?.contains(label) == true })
            }
        }
    }

    fun draftPrompt(w: Waiting, project: Project?): String = buildString {
        appendLine("You are YDeck, the developer's project dashboard. A Claude Code session" + (project?.let { " in the project \"${it.name}\"" } ?: "") +
            " stopped to ask the developer something, and the developer is away from the terminal. Draft the reply the developer would give.")
        appendLine("Base it only on what is recorded: the project's decisions, its next step and the last handoff (below), and, when needed, its files and YDeck's tools. Do not change any file, and never read .env files or anything that holds a key.")
        appendLine("If a record settles it, say which one in \"source\" (for example: the decision of 2026-10-12) and set \"sure\" to true. If nothing recorded settles it, give your best suggestion, say why in \"source\", and set \"sure\" to false. Never invent a decision.")
        appendLine("Write the reply in the language the session asked in, short, as the developer would type it.")
        appendLine()
        if (project != null) { append(Assistant.about(project)); appendLine() }
        if (w.kind == Waiting.CHOICE) {
            appendLine("The session asks the developer to choose:")
            w.questions.forEach { q -> appendLine("- \"${q.question}\" — options: " + q.options.joinToString(" | ") { "\"$it\"" } + if (q.multi) " (several allowed)" else "") }
            appendLine()
            appendLine("Answer with only this JSON: {\"choices\": {\"<question exactly>\": \"<one option label exactly>\"}, \"answer\": \"<one line: what was chosen and why>\", \"source\": \"...\", \"sure\": true|false}")
        } else {
            appendLine("The end of the session's message to the developer:")
            appendLine(w.text)
            appendLine()
            appendLine("Answer with only this JSON: {\"answer\": \"<the reply>\", \"source\": \"...\", \"sure\": true|false}")
        }
    }
}
