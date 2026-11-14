package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/*
 * Writing to Claude from the project's page: a work conversation with the developer's own Claude Code (their model,
 * their settings, the plugin's hooks), run in the project's folder with no terminal. One process per conversation,
 * kept alive between turns (stream-json in and out); a quiet one is closed after a while and the next message goes on
 * with --resume. When Claude needs a permission, it's asked on the page, on the computer only.
 *
 * Two writers to one conversation break it, so a conversation open in a terminal is shown, never written to here.
 */
object Talk {
    enum class Kind { USER, CLAUDE, STEP }

    /** One line of a conversation: what the developer wrote, what Claude answered, or one thing Claude did, in words. */
    data class Line(val kind: Kind, val text: String)

    /** Claude asks to use [tool] on [what]; [always]: what "always" would allow, in words (null: there's no "always"). */
    data class Ask(val request: String, val tool: String, val what: String, val always: String?, internal val input: JSONObject, internal val suggestions: JSONArray?)

    /** A conversation in a project: [session] is Claude Code's (null for a new one, until it starts). */
    data class Chat(
        val session: String? = null,
        val title: String = "",
        val lines: List<Line> = emptyList(),
        val busy: Boolean = false,
        val ask: Ask? = null,
        val error: String? = null,
        /** When it was last written to, from either side. */
        val at: Long = 0,
    )

    /** A past conversation in the folder, to go back to. */
    data class Past(val session: String, val title: String, val at: Long)

    enum class Answer { ONCE, ALWAYS, NO }

    private val _chats = MutableStateFlow<Map<String, Chat>>(emptyMap())
    /** By folder: the conversation on its page. */
    val chats: StateFlow<Map<String, Chat>> = _chats

    private class Run(val proc: Process, val session: String, val out: BufferedWriter, @Volatile var last: Long)
    private val runs = ConcurrentHashMap<String, Run>()
    /** The sessions YDeck runs for the developer: theirs (not YDeck's own), but not held by [SessionAsks]. */
    private val mine: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A conversation quiet this long is closed; the next message goes on with --resume. */
    private const val IDLE_MS = 10 * 60_000L
    /** The last conversation is taken up again when it was written to this recently; else a new one starts. */
    const val GO_ON_MS = 2 * 60 * 60_000L

    init {
        Thread({
            while (true) {
                Thread.sleep(60_000)
                val now = System.currentTimeMillis()
                runs.forEach { (key, r) -> if (now - r.last > IDLE_MS && _chats.value[key]?.busy != true) close(key) }
            }
        }, "ydeck-talk").apply { isDaemon = true; start() }
    }

    fun isMine(session: String): Boolean = session in mine

    /** A conversation to draw, for the renders. */
    fun preview(dir: File, chat: Chat) = _chats.update { it + (dir.path to chat) }

    /** The conversation for [dir]'s page: the one on it, or the last one in the folder when it's recent. */
    fun load(dir: File) {
        if (_chats.value.containsKey(dir.path)) return
        val last = Transcript.list(dir).firstOrNull()?.takeIf { System.currentTimeMillis() - it.at < GO_ON_MS }
        val chat = last?.let { p -> Chat(p.session, p.title, Transcript.lines(dir, p.session), at = p.at) } ?: Chat()
        _chats.update { if (it.containsKey(dir.path)) it else it + (dir.path to chat) }
    }

    /** Opens a past conversation on the page. */
    fun open(dir: File, past: Past) {
        if (_chats.value[dir.path]?.busy == true) return
        close(dir.path)
        _chats.update { it + (dir.path to Chat(past.session, past.title, Transcript.lines(dir, past.session), at = past.at)) }
    }

    /** A new conversation: the next message starts it. */
    fun fresh(dir: File) {
        if (_chats.value[dir.path]?.busy == true) return
        close(dir.path)
        _chats.update { it + (dir.path to Chat()) }
    }

    /** The conversation is open in a terminal (or anywhere else YDeck doesn't run it): only shown here. */
    fun elsewhere(session: String?): Boolean {
        session ?: return false
        if (runs.values.any { it.session == session }) return false
        val s = ClaudeLink.sessions.value[session] ?: return false
        return s.mode != ClaudeMode.DONE || s.proc != null
    }

    /** Sends [text] to Claude in [dir]: in the conversation on the page, or a new one. */
    fun send(dir: File, text: String) {
        val t = text.trim()
        val key = dir.path
        val chat = _chats.value[key] ?: Chat()
        if (t.isEmpty() || chat.busy) return
        if (elsewhere(chat.session)) {
            set(key) { it.copy(error = tr("This conversation is open in a terminal: write to it there, or start a new one here.",
                "השיחה הזאת פתוחה בטרמינל: אפשר לכתוב לה שם, או לפתוח כאן שיחה חדשה.")) }
            return
        }
        set(key) { it.copy(lines = it.lines + Line(Kind.USER, t), busy = true, error = null, at = System.currentTimeMillis(),
            title = it.title.ifBlank { t.lineSequence().first().take(80) }) }
        Thread({
            runCatching {
                val run = runs[key]?.takeIf { it.proc.isAlive && it.session == chat.session } ?: start(dir, chat.session)
                run.last = System.currentTimeMillis()
                write(run, JSONObject().put("type", "user").put("message", JSONObject().put("role", "user").put("content", t)))
            }.onFailure { e -> set(key) { it.copy(busy = false, error = e.message ?: tr("Claude didn't start", "Claude לא עלה")) } }
        }, "ydeck-talk-send").apply { isDaemon = true; start() }
    }

    /** The developer's answer to the permission Claude asked for. */
    fun answer(dir: File, how: Answer) {
        val key = dir.path
        val ask = _chats.value[key]?.ask ?: return
        val run = runs[key] ?: return
        val response = when (how) {
            Answer.NO -> JSONObject().put("behavior", "deny").put("message", "The user said no to this. Ask them what to do instead.")
            else -> JSONObject().put("behavior", "allow").put("updatedInput", ask.input).apply {
                if (how == Answer.ALWAYS && ask.suggestions != null) put("updatedPermissions", ask.suggestions)
            }
        }
        set(key) { it.copy(ask = null) }
        runCatching { write(run, reply(ask.request, response)) }
    }

    /** Stops Claude mid-turn, as Esc does in the terminal. */
    fun stop(dir: File) {
        val key = dir.path
        val run = runs[key] ?: return set(key) { it.copy(busy = false) }
        _chats.value[key]?.ask?.let { answer(dir, Answer.NO) }
        runCatching { write(run, JSONObject().put("type", "control_request").put("request_id", java.util.UUID.randomUUID().toString())
            .put("request", JSONObject().put("subtype", "interrupt"))) }
        Thread({
            Thread.sleep(5000)
            if (_chats.value[key]?.busy == true) { close(key); set(key) { it.copy(busy = false) } }
        }, "ydeck-talk-stop").apply { isDaemon = true; start() }
    }

    /** Ends the process (the conversation stays, and goes on with --resume). */
    fun close(key: String) {
        val r = runs.remove(key) ?: return
        runCatching { r.out.close() }
        Thread { if (!r.proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) r.proc.destroyForcibly() }.apply { isDaemon = true; start() }
    }

    /** Before opening the conversation in a terminal: YDeck lets go of it, so there's one writer. */
    fun handOff(dir: File) = close(dir.path)

    private fun start(dir: File, resume: String?): Run {
        val claude = Tools.claude?.exe ?: error(tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב"))
        val session = resume ?: java.util.UUID.randomUUID().toString()
        mine += session
        val cmd = buildList {
            addAll(listOf(claude.path, "-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
                "--permission-prompt-tool", "stdio"))
            if (resume != null) { add("--resume"); add(resume) } else { add("--session-id"); add(session) }
            // A question is asked in words, in the conversation: there's no picker here.
            addAll(listOf("--disallowedTools", "AskUserQuestion"))
        }
        val p = ProcessBuilder(cmd).directory(dir).start()
        val run = Run(p, session, p.outputStream.bufferedWriter(Charsets.UTF_8), System.currentTimeMillis())
        val key = dir.path
        runs[key] = run
        set(key) { it.copy(session = session) }
        val err = StringBuilder()
        Thread { runCatching { err.append(p.errorStream.bufferedReader(Charsets.UTF_8).readText()) } }.apply { isDaemon = true; start() }
        Thread({
            runCatching { p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line -> runCatching { take(key, dir, run, JSONObject(line)) } } }
            if (runs[key] === run) runs.remove(key)
            set(key) { c -> if (c.session != session) c else c.copy(busy = false, ask = null,
                error = c.error ?: if (c.busy) ClaudeFill.why(err.toString()) else null) }
            Pulses.refreshSoon()
        }, "ydeck-talk-read").apply { isDaemon = true; start() }
        return run
    }

    /** One line from Claude Code. */
    private fun take(key: String, dir: File, run: Run, o: JSONObject) {
        run.last = System.currentTimeMillis()
        when (o.optString("type")) {
            "assistant" -> {
                if (o.opt("parent_tool_use_id").let { it != null && it != JSONObject.NULL }) return
                val lines = linesOf(o.optJSONObject("message")?.optJSONArray("content"), dir)
                if (lines.isNotEmpty()) set(key) { it.copy(lines = it.lines + lines, at = System.currentTimeMillis()) }
            }
            "control_request" -> {
                val r = o.optJSONObject("request") ?: return
                if (r.optString("subtype") != "can_use_tool") return
                val input = r.optJSONObject("input") ?: JSONObject()
                val tool = r.optString("tool_name")
                val suggestions = r.optJSONArray("permission_suggestions")?.takeIf { it.length() > 0 }
                set(key) { it.copy(ask = Ask(o.optString("request_id"), tool, permissionWhat(tool, input, dir, r.optString("description")),
                    suggestions?.let(::alwaysWords), input, suggestions)) }
            }
            "result" -> {
                val failed = o.optBoolean("is_error") || !o.optString("subtype").startsWith("success")
                set(key) { it.copy(busy = false, ask = null, at = System.currentTimeMillis(),
                    error = if (failed && o.optString("subtype") != "error_during_execution") ClaudeFill.why(o.optString("result")) else null) }
                Pulses.refreshSoon()
            }
        }
    }

    private fun write(run: Run, o: JSONObject) = synchronized(run) { run.out.write(o.toString()); run.out.write("\n"); run.out.flush() }

    private fun reply(request: String, response: JSONObject) = JSONObject().put("type", "control_response")
        .put("response", JSONObject().put("subtype", "success").put("request_id", request).put("response", response))

    private fun set(key: String, f: (Chat) -> Chat) = _chats.update { it + (key to f(it[key] ?: Chat())) }

    /** A message's text and the things Claude did, in words; thinking is left out. */
    fun linesOf(content: JSONArray?, dir: File): List<Line> {
        content ?: return emptyList()
        return (0 until content.length()).mapNotNull { content.optJSONObject(it) }.mapNotNull { b ->
            when (b.optString("type")) {
                "text" -> b.optString("text").trim().takeIf { it.isNotEmpty() }?.let { Line(Kind.CLAUDE, it) }
                "tool_use" -> stepOf(b.optString("name"), b.optJSONObject("input") ?: JSONObject(), dir)?.let { Line(Kind.STEP, it) }
                else -> null
            }
        }
    }

    /** One thing Claude does, as a short line. */
    fun stepOf(tool: String, input: JSONObject, dir: File): String? {
        fun rel(path: String) = runCatching { File(path).relativeTo(dir).invariantSeparatorsPath }.getOrDefault(path).ifBlank { File(path).name }
        val file = input.optString("file_path").ifBlank { input.optString("notebook_path") }
        return when (tool) {
            "Read" -> tr("Read ", "קרא את ") + rel(file)
            "Edit", "MultiEdit", "NotebookEdit" -> tr("Changed ", "שינה את ") + rel(file)
            "Write" -> tr("Wrote ", "כתב את ") + rel(file)
            "Grep" -> tr("Looked for “", "חיפש ״") + input.optString("pattern").take(40) + tr("”", "״")
            "Glob" -> tr("Looked for files: ", "חיפש קבצים: ") + input.optString("pattern").take(40)
            "LS" -> tr("Looked through ", "עבר על ") + rel(input.optString("path")).ifBlank { "." }
            "Bash", "PowerShell" -> tr("Ran: ", "הריץ: ") + input.optString("description").ifBlank { input.optString("command").lineSequence().first() }.take(90)
            "WebFetch" -> tr("Opened ", "פתח את ") + input.optString("url").take(80)
            "WebSearch" -> tr("Searched the web: ", "חיפש ברשת: ") + input.optString("query").take(60)
            "Task", "Agent" -> tr("Sent a helper: ", "שלח עוזר: ") + input.optString("description").take(60)
            "TodoWrite", "TaskCreate", "TaskUpdate" -> null
            "" -> null
            else -> tr("Used ", "השתמש ב־") + tool.removePrefix("mcp__").replace("__", " · ")
        }
    }

    /** What a permission is for, in words. */
    fun permissionWhat(tool: String, input: JSONObject, dir: File, description: String): String {
        fun rel(path: String) = runCatching { File(path).relativeTo(dir).invariantSeparatorsPath }.getOrDefault(path)
        return when (tool) {
            "Bash", "PowerShell" -> input.optString("command").trim()
            "Edit", "MultiEdit", "Write", "NotebookEdit", "Read" -> rel(input.optString("file_path").ifBlank { input.optString("notebook_path") })
            "WebFetch" -> input.optString("url")
            else -> description.ifBlank { input.toString().take(200) }
        }
    }

    /** What "always" would allow, from Claude Code's own suggestions. */
    fun alwaysWords(suggestions: JSONArray): String? {
        val parts = (0 until suggestions.length()).mapNotNull { suggestions.optJSONObject(it) }.mapNotNull { s ->
            when (s.optString("type")) {
                "setMode" -> when (s.optString("mode")) {
                    "acceptEdits" -> tr("changing files, for this conversation", "שינוי קבצים, בשיחה הזאת")
                    else -> null
                }
                "addRules" -> s.optJSONArray("rules")?.let { r ->
                    (0 until r.length()).mapNotNull { r.optJSONObject(it) }.joinToString(", ") { rule ->
                        rule.optString("ruleContent").takeIf { it.isNotBlank() }?.let { "${rule.optString("toolName")}($it)" } ?: rule.optString("toolName")
                    }.takeIf { it.isNotBlank() }
                }
                "addDirectories" -> tr("the folder ", "התיקייה ") + (s.optJSONArray("directories")?.optString(0) ?: "")
                else -> null
            }
        }
        return parts.joinToString(" · ").takeIf { it.isNotBlank() }
    }
}

/** Claude Code's conversations in a folder, read from its transcripts (~/.claude/projects/<folder>/<session>.jsonl). */
object Transcript {
    private val root = File(System.getProperty("user.home"), ".claude/projects")

    /** Claude Code's folder for [dir]'s conversations: every sign but letters and digits becomes "-". */
    fun folderOf(dir: File): File = File(root, dir.absolutePath.replace(Regex("[^A-Za-z0-9]"), "-"))

    /** The conversations in [dir], newest first: the developer's own (not YDeck's, not a helper's), with a title. */
    fun list(dir: File, max: Int = 12): List<Talk.Past> {
        val files = folderOf(dir).listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }?.sortedByDescending { it.lastModified() } ?: return emptyList()
        return files.asSequence().mapNotNull { f ->
            val session = f.name.removeSuffix(".jsonl")
            if (Assistant.isOwn(session, "")) return@mapNotNull null
            val title = titleOf(f) ?: return@mapNotNull null
            Talk.Past(session, title, f.lastModified())
        }.take(max).toList()
    }

    /** The conversation's name, or its first message; null for one with no message of the developer's (YDeck's own runs). */
    fun titleOf(f: File): String? {
        var named: String? = null
        var first: String? = null
        runCatching {
            f.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines.take(400)) {
                    if (line.contains("\"type\":\"custom-title\"") || line.contains("\"type\":\"summary\"")) {
                        val o = JSONObject(line)
                        named = o.optString("customTitle").ifBlank { o.optString("summary") }.ifBlank { null } ?: named
                    }
                    if (first == null && line.contains("\"type\":\"user\"") && !line.contains("\"isSidechain\":true") && !line.contains("\"isMeta\":true")) {
                        val text = userText(JSONObject(line))
                        // YDeck's own run: its first message is YDeck's.
                        if (text != null && YDECK_ASKS.any { text.startsWith(it) }) return null
                        first = text?.takeIf { looksOwn(it) }
                    }
                    if (named != null && first != null) break
                }
            }
        }
        val f1 = first ?: return null
        return (named ?: f1.lineSequence().first()).trim().take(90)
    }

    /** The conversation, line by line: the developer's messages, Claude's answers, what it did; the last [max]. */
    fun lines(dir: File, session: String, max: Int = 120): List<Talk.Line> {
        val f = File(folderOf(dir), "$session.jsonl").takeIf { it.isFile } ?: return emptyList()
        val out = ArrayList<Talk.Line>()
        runCatching {
            f.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line -> runCatching { lineOf(JSONObject(line), dir)?.let(out::addAll) } }
            }
        }
        return out.takeLast(max)
    }

    /** One transcript line, as conversation lines (none for what isn't the main conversation). */
    fun lineOf(o: JSONObject, dir: File): List<Talk.Line>? {
        if (o.optBoolean("isSidechain") || o.optBoolean("isMeta")) return null
        return when (o.optString("type")) {
            "user" -> userText(o)?.takeIf { looksOwn(it) }?.let { listOf(Talk.Line(Talk.Kind.USER, it)) }
            "assistant" -> Talk.linesOf(o.optJSONObject("message")?.optJSONArray("content"), dir)
            else -> null
        }
    }

    /** What the developer wrote in a user line (not a tool's result). */
    fun userText(o: JSONObject): String? = when (val c = o.optJSONObject("message")?.opt("content")) {
        is String -> c
        is JSONArray -> (0 until c.length()).mapNotNull { c.optJSONObject(it) }.filter { it.optString("type") == "text" }
            .joinToString("\n") { it.optString("text") }.ifBlank { null }
        else -> null
    }?.trim()?.ifBlank { null }

    /** Written by the developer, not by Claude Code (a command's output, a reminder, a stop). */
    private fun looksOwn(text: String): Boolean = !text.startsWith("<") && !text.startsWith("[Request interrupted") && !text.startsWith("Caveat:") &&
        YDECK_ASKS.none { text.startsWith(it) }

    /** How YDeck's own runs of Claude begin (answers, the dossier, saving, summaries): not the developer's conversations. */
    private val YDECK_ASKS = listOf("You are YDeck", "You are filling in this project's page in YDeck", "You write the handoff note",
        "This folder is the project \"", "Write the git commit message", "Summarize this project for its dashboard in YDeck")
}
