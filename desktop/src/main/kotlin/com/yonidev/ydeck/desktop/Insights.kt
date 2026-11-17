package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

/*
 * YDeck's one line on each project: where it really stands and what holds it, the way a partner who read everything
 * would say it ("The payment is nearly done. What stops it: the Stripe key."). Written by Claude (Sonnet, from what
 * YDeck knows, reading nothing), one project at a time in the background, and again only when the project moved.
 * Projects nobody touched for two weeks are left as they are.
 */
object Insights {
    data class Line(val line: String, val signature: String, val at: Long)

    private val _lines = MutableStateFlow<Map<String, Line>>(emptyMap())
    /** By folder. */
    val lines: StateFlow<Map<String, Line>> = _lines

    private val file: File get() = File(AppDirs.data, "insights.json")
    private val queue = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "ydeck-insights").apply { isDaemon = true } }
    private val queued: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Off in the renders and tests, where no Claude should run. */
    @Volatile var auto = true

    init {
        runCatching {
            val o = JSONObject(file.readText())
            _lines.value = o.keySet().associateWith { k -> o.getJSONObject(k).let { Line(it.optString("line"), it.optString("signature"), it.optLong("at")) } }
        }
    }

    private fun persist() = runCatching {
        file.writeText(JSONObject().apply { _lines.value.forEach { (k, v) -> put(k, JSONObject().put("line", v.line).put("signature", v.signature).put("at", v.at)) } }.toString())
    }

    /** What the line speaks of: when any of it changes, the line is written again. */
    fun signature(local: LocalFile?, saving: SaveCard?): String = listOf(
        local?.now, local?.next, local?.left?.joinToString("|"), local?.done?.take(3)?.joinToString("|"),
        local?.goal?.let { g -> g.title + g.steps.joinToString("|") { it.text + it.done } }, saving?.clean, saving?.state?.take(80),
    ).joinToString("§").hashCode().toString(16)

    /** The line for [p] in [dir], written in the background when the project moved since the last one. */
    fun ensure(p: Project, dir: File) {
        if (!auto || Tools.claude == null) return
        val key = dir.path
        val local = ProjectFiles.readLocal(dir) ?: return
        val days = Journal.days(p.id, 14)
        val recent = days.isNotEmpty() || (ClaudeLink.statusIn(dir.path, ClaudeLink.sessions.value) != null)
        if (!recent && _lines.value.containsKey(key)) return
        if (!recent && local.next.isBlank() && local.left.isEmpty() && local.goal == null) return
        val sig = signature(local, Saving.cards.value[key])
        if (_lines.value[key]?.signature == sig || !queued.add(key)) return
        queue.execute {
            try {
                val about = Assistant.about(p)
                val answer = ClaudeJson.ask(dir, prompt(p, about), schema(), tools = false, minutes = 2)
                answer.optString("line").trim().takeIf { it.isNotEmpty() }?.let { line ->
                    _lines.update { it + (key to Line(line.take(160), sig, System.currentTimeMillis())) }
                    persist()
                }
            } catch (_: Exception) {
            } finally { queued.remove(key) }
        }
    }

    fun schema(): JSONObject = ClaudeJson.obj(listOf("line"), "line" to ClaudeJson.string())

    fun prompt(p: Project, about: String): String = buildString {
        val language = if (I18n.rtl) "Hebrew" else "English"
        appendLine("You are YDeck, the developer's partner across their projects. Write ONE line about the project below: where it really stands and what holds it, the way a sharp partner who read everything would say it to them.")
        appendLine("At most 14 words, in $language, plain and specific to this project (name the real thing: the feature, the step, the blocker). Two short sentences at most. No praise, no filler, no \"the project\", no emoji, no quotes.")
        appendLine("Good: \"The payment is nearly done. What stops it: the Stripe key.\" / \"Two weeks without a move; the store listing waits for screenshots.\"")
        appendLine()
        append(about)
    }
}
