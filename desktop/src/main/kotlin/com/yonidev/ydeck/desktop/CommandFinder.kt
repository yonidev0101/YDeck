package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

/*
 * "Find commands": Claude reads the project's folder (read-only, never the files that hold keys) for the commands run
 * in it again and again (the dev server, a build, tests, a deploy...) and they join the project's commands at once,
 * the ones already there left as they are. Kept on this PC only, like every command (see [Runs.keep]). Asked from the
 * project's Commands tab here, or from the phone.
 */
object CommandFinder {
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    /** Projects (by id) whose folder Claude reads now. */
    val busy: StateFlow<Set<String>> = _busy

    private val _results = MutableStateFlow<Map<String, Pair<Long, String>>>(emptyMap())
    /** By project id: when the last search ended, and what it came to, in words. */
    val results: StateFlow<Map<String, Pair<Long, String>>> = _results

    fun start(p: Project, dir: File) {
        if (Tools.claude == null || !dir.isDirectory) return
        if (!_busy.value.contains(p.id)) _busy.update { it + p.id } else return
        _results.update { it - p.id }
        Thread({
            val said = try {
                val known = Settings.state.value.localOf(p.id).commands
                val found = parse(ClaudeJson.ask(dir, prompt(p, known), schema(), tools = true, minutes = 3))
                when (val n = add(p.id, found)) {
                    0 -> tr("No new commands found", "לא נמצאו פקודות חדשות")
                    1 -> tr("1 command added", "נוספה פקודה אחת")
                    else -> tr("$n commands added", "נוספו $n פקודות")
                }
            } catch (e: Exception) {
                tr("Couldn't search: ", "החיפוש לא הצליח: ") + (e.message ?: "").take(120)
            }
            _results.update { it + (p.id to (System.currentTimeMillis() to said)) }
            _busy.update { it - p.id }
        }, "ydeck-commands").apply { isDaemon = true; start() }
    }

    /** The commands in Claude's answer: a line each, and a short name. */
    fun parse(a: JSONObject): List<Command> {
        val arr = a.optJSONArray("commands") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val line = o.optString("line").trim().takeIf { it.isNotEmpty() && '\n' !in it && it.length <= 400 } ?: return@mapNotNull null
            Command(o.optString("label").trim().take(40), line)
        }.distinctBy { it.line }.take(20)
    }

    /** Adds the ones not already there (by line); how many were added. */
    fun add(projectId: String, found: List<Command>): Int {
        val l = Settings.state.value.localOf(projectId)
        val new = found.filter { c -> l.commands.none { it.line.trim() == c.line } }
        if (new.isNotEmpty()) Settings.setLocal(projectId, l.copy(commands = l.commands + new))
        return new.size
    }

    fun schema(): JSONObject = ClaudeJson.obj(listOf("commands"),
        "commands" to JSONObject().put("type", "array").put("items", ClaudeJson.obj(listOf("label", "line"), "label" to ClaudeJson.string(), "line" to ClaudeJson.string())))

    fun prompt(p: Project, known: List<Command>): String = buildString {
        val language = if (I18n.rtl) "Hebrew" else "English"
        appendLine("Find the commands a developer runs again and again in this project (\"${p.name}\"), from its folder (the current directory). Read only; change nothing.")
        appendLine("Look at what's there: package.json scripts, gradlew / build.gradle(.kts), Makefile, justfile, docker-compose, wrangler.toml, pyproject.toml, Cargo.toml, the scripts folder, CI workflows, README and CONTRIBUTING.")
        appendLine("Think of: running the app or the dev server, building, tests, lint, deploy, database migrations, releasing, generating code.")
        appendLine("Each line exactly as typed in the project's root folder on Windows, in ${Shells.current.label}. Only commands that really exist here. Never one that holds a secret, a token or a password. No one-off setup unless it's needed often. At most 15, the most useful first.")
        appendLine("label: what it does, 1 to 3 words, in $language (e.g. \"Dev server\", \"Build APK\", \"Tests\").")
        if (known.isNotEmpty()) appendLine("Already kept (don't repeat them): " + known.joinToString("; ") { it.line })
    }
}
