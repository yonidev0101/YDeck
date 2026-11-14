package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/*
 * The project's dossier: what was built and the near goal, kept in .ydeck.local.json. Claude keeps them as it works
 * (it's told how in [ProjectFiles.briefing]); for a project that never had them, one read of the code and of git's
 * history fills "built" and suggests a goal. The goal is written only once the developer agrees to it.
 */

/**
 * One answer from Claude Code in a set shape ([schema], a JSON Schema): Sonnet, reading only (or, with [tools] false,
 * from [prompt] alone), never the files that hold keys. Returns the answer, or throws with why, in words.
 */
object ClaudeJson {
    fun ask(dir: File, prompt: String, schema: JSONObject, tools: Boolean, minutes: Long = 4, onStep: (String) -> Unit = {}): JSONObject {
        val claude = Tools.claude?.exe ?: error(tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב"))
        val cmd = buildList {
            addAll(listOf(claude.path, "-p", "--model", "sonnet", "--output-format", "stream-json", "--verbose", "--json-schema", arg(schema.toString())))
            if (tools) { addAll(listOf("--allowedTools", "Read", "Glob", "Grep", "LS", "--disallowedTools")); addAll(ClaudeFill.SECRETS) }
            else addAll(listOf("--tools", "", "--strict-mcp-config"))
        }
        val p = ProcessBuilder(cmd).directory(dir).start()
        p.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(prompt) }
        val err = StringBuilder()
        Thread { runCatching { err.append(p.errorStream.bufferedReader(Charsets.UTF_8).readText()) } }.apply { isDaemon = true; start() }
        var answer: JSONObject? = null
        var failed: String? = null
        val reader = Thread {
            runCatching {
                p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                    o.optString("session_id").takeIf { it.isNotBlank() }?.let(Assistant::markOwn)
                    when (o.optString("type")) {
                        "assistant" -> ClaudeFill.stepOf(o, dir)?.let(onStep)
                        "result" -> if (o.optBoolean("is_error") || !o.optString("subtype").startsWith("success")) failed = o.optString("result")
                            else answer = o.optJSONObject("structured_output") ?: runCatching { JSONObject(o.optString("result")) }.getOrNull()
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        if (!p.waitFor(minutes, TimeUnit.MINUTES)) { p.destroyForcibly(); error(tr("Claude took too long. Try again.", "Claude לקח יותר מדי זמן. אפשר לנסות שוב.")) }
        reader.join(3000)
        answer?.let { return it }
        error(ClaudeFill.why(failed.orEmpty() + "\n" + err))
    }

    /**
     * [text] as one argument on Windows' command line: Java passes it as is, so the quotes inside would be taken for the
     * argument's own and dropped; escaped, the program gets them back.
     */
    fun arg(text: String): String = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) text.replace("\"", "\\\"") else text

    /** {"type": "array", "items": {"type": "string"}} */
    fun strings(): JSONObject = JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))

    fun obj(required: List<String>, vararg props: Pair<String, JSONObject>): JSONObject = JSONObject().put("type", "object")
        .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } }).put("required", JSONArray(required))

    fun string(): JSONObject = JSONObject().put("type", "string")

    fun list(a: JSONArray?): List<String> = a?.let { (0 until it.length()).map { i -> it.optString(i).trim() }.filter { s -> s.isNotEmpty() } }.orEmpty()
}

object Dossier {
    /** What a first read found: what the project can do, and a goal to suggest. */
    data class Found(val built: List<String>, val goal: Goal?)

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    /** Folders being read now. */
    val busy: StateFlow<Set<String>> = _busy
    private val _step = MutableStateFlow<Map<String, String>>(emptyMap())
    /** By folder: what Claude is reading now. */
    val step: StateFlow<Map<String, String>> = _step
    private val _suggested = MutableStateFlow<Map<String, Goal>>(emptyMap())
    /** By folder: a goal Claude suggests, until the developer takes it, changes it or says not now. */
    val suggested: StateFlow<Map<String, Goal>> = _suggested
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    /** By folder: why the last read didn't work. */
    val errors: StateFlow<Map<String, String>> = _errors

    /** Reads the project in the background: fills "built" (keeping what's there) and suggests a goal when there is none. */
    fun start(dir: File) {
        val key = dir.path
        if (key in _busy.value) return
        _busy.update { it + key }
        _errors.update { it - key }
        Thread({
            runCatching { read(dir) { s -> _step.update { it + (key to s) } } }
                .onSuccess { f ->
                    val local = ProjectFiles.readLocal(dir)
                    val merged = merge(local?.built.orEmpty(), f.built)
                    if (merged != local?.built) ProjectFiles.writeList(dir, ProjectFiles.BUILT, merged)
                    if (local?.goal == null && f.goal != null) _suggested.update { it + (key to f.goal) }
                }
                .onFailure { e -> _errors.update { it + (key to (e.message ?: tr("Claude didn't answer", "Claude לא ענה"))) } }
            _busy.update { it - key }
            _step.update { it - key }
            Pulses.refreshSoon()
        }, "ydeck-dossier").apply { isDaemon = true; start() }
    }

    /** The developer agreed to the goal (as suggested, or changed). */
    fun takeGoal(dir: File, goal: Goal) {
        ProjectFiles.writeGoal(dir, goal)
        _suggested.update { it - dir.path }
        Pulses.refreshSoon()
    }

    fun dismissGoal(dir: File) = _suggested.update { it - dir.path }

    /** What's there stays, in its order; new lines follow (the same line again, whatever its case, isn't added). */
    fun merge(have: List<String>, found: List<String>): List<String> {
        val seen = have.map { it.trim().lowercase() }.toMutableSet()
        return have + found.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    private fun read(dir: File, onStep: (String) -> Unit): Found {
        val log = Git.run(dir, listOf("log", "--no-merges", "--format=%ad %s", "--date=short", "-120"), errors = false)
            .out.takeIf { it.isNotBlank() }.orEmpty()
        val local = ProjectFiles.readLocal(dir)
        val answer = ClaudeJson.ask(dir, prompt(local, ProjectFiles.readShared(dir)?.summary.orEmpty(), log), schema(), tools = true, onStep = onStep)
        return parse(answer)
    }

    fun schema(): JSONObject = ClaudeJson.obj(listOf("built", "goal"),
        "built" to ClaudeJson.strings(),
        "goal" to ClaudeJson.obj(listOf("title", "steps"), "title" to ClaudeJson.string(), "steps" to ClaudeJson.strings()))

    fun parse(o: JSONObject): Found {
        val built = ClaudeJson.list(o.optJSONArray("built")).distinct().take(60)
        val g = o.optJSONObject("goal")
        val goal = g?.optString("title")?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            Goal(t, ClaudeJson.list(g.optJSONArray("steps")).distinct().take(12).map { GoalStep(it, false) })
        }?.takeIf { it.steps.isNotEmpty() }
        return Found(built, goal)
    }

    fun prompt(local: LocalFile?, summary: String, log: String): String = buildString {
        val language = if (I18n.rtl) "Hebrew" else "English"
        appendLine("You are filling in this project's page in YDeck, the developer's dashboard of their projects. Read the README and the code as you need; do not change any file, and never read .env files or anything that holds a key.")
        appendLine()
        appendLine("1. \"built\": everything the project can do today that a user of it sees, one short line per capability (\"Sign in with Google\", \"A booking calendar\", \"Pay with Stripe\"), most important first, at most 40 lines. Only what exists in the code and works, not plans. No technical internals (\"refactored X\", \"added tests\").")
        appendLine("2. \"goal\": a near goal to suggest, the next milestone that makes the project more real (\"First version in the store\", \"The site live for the first client\"), and 3 to 8 concrete steps toward it, each one short line, in order, none of them done yet. Build it from what's left, the next step and what the history shows.")
        appendLine()
        appendLine("Write every line in $language, plain, as the developer would say it.")
        if (summary.isNotBlank()) { appendLine(); appendLine("What the project is: $summary") }
        if (local != null) {
            if (local.next.isNotBlank()) appendLine("The next step: ${local.next}")
            if (local.left.isNotEmpty()) appendLine("What's left: " + local.left.joinToString("; "))
            if (local.done.isNotEmpty()) appendLine("Done lately: " + local.done.joinToString("; "))
            if (local.built.isNotEmpty()) appendLine("Already listed as built (keep these, add only what's missing): " + local.built.joinToString("; "))
        }
        if (log.isNotBlank()) { appendLine(); appendLine("git history, newest first:"); appendLine(log.take(12_000)) }
    }
}
