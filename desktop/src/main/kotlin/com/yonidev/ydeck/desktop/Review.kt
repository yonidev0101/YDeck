package com.yonidev.ydeck.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * The day's review: once a day, before the good morning, one project in turn (the one reviewed longest ago) is read by
 * Claude Code, its code too, reading only, with time to look. It suggests what could come next (an idea) and what
 * should be closed. Nothing goes into the lists by itself: the suggestions reach the good morning, and the developer
 * takes one into the ideas or what's left, or lets it go.
 */
object Review {

    data class Item(val text: String, val why: String)
    data class Result(val project: String, val date: String, val ideas: List<Item>, val close: List<Item>)

    private val dir: File get() = File(AppDirs.data, "review").apply { mkdirs() }
    private fun file(id: String) = File(dir, id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "_" } + ".json")

    fun last(projectId: String): Result? = runCatching {
        val o = JSONObject(file(projectId).readText())
        fun items(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
            .map { Item(it.optString("text").take(300), it.optString("why").take(300)) }.filter { it.text.isNotBlank() }
        Result(projectId, o.optString("date"), items("ideas"), items("close"))
    }.getOrNull()

    /** The next one to review: of the good morning's projects with a folder, the one reviewed longest ago (never first). */
    fun next(projects: List<Project>): Project? = projects.filter { Settings.state.value.localOf(it.id).dir != null }
        .minByOrNull { last(it.id)?.date ?: "" }

    /** Reviews [p] now (minutes, at most five): Claude reads the project and suggests. Throws with why. */
    fun run(p: Project) {
        val home = Settings.state.value.localOf(p.id).dir ?: error("no folder")
        val answer = Assistant.read(prompt(p, home), home, null, onSession = {}, onStep = {}, onProcess = {})
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        require(start >= 0 && end > start) { "no answer" }
        val o = JSONObject(answer.substring(start, end + 1))
        fun items(name: String) = o.optJSONArray(name)?.let { a -> (0 until minOf(a.length(), 2)).mapNotNull { a.optJSONObject(it) } }.orEmpty()
        file(p.id).writeText(JSONObject().put("date", LocalDate.now().toString()).put("ideas", JSONArray(items("ideas"))).put("close", JSONArray(items("close"))).toString())
    }

    private fun prompt(p: Project, home: File): String = buildString {
        val state = ProjectFiles.readLocal(home)
        val shared = ProjectFiles.readShared(home)
        appendLine("You are YDeck, the developer's project dashboard, doing the day's review of one project: \"${p.name}\", in this folder. Read it as a partner who wants it to move forward: its README and structure, the code that changed lately, what's half done. Take a few minutes at most, and read only (never change a file, never read .env files or anything that holds a key).")
        appendLine()
        appendLine("Where it stands, as recorded:")
        shared?.summary?.takeIf { it.isNotBlank() }?.let { appendLine("- what it is: $it") }
        state?.next?.takeIf { it.isNotBlank() }?.let { appendLine("- next step: $it") }
        state?.left?.takeIf { it.isNotEmpty() }?.let { appendLine("- left to do: " + it.joinToString("; ")) }
        state?.ideas?.takeIf { it.isNotEmpty() }?.let { appendLine("- ideas already set aside: " + it.joinToString("; ")) }
        state?.done?.takeIf { it.isNotEmpty() }?.let { appendLine("- done lately: " + it.take(5).joinToString("; ")) }
        Journal.lines(p.id, LocalDate.now().minusDays(7)).takeIf { it.isNotEmpty() }?.let { appendLine("- the last week: " + it.joinToString(" | ")) }
        appendLine()
        appendLine("Then suggest, from what you read: up to two new ideas worth building next (not already in the lists), and up to one thing that should be closed (something half done, a loose end, a risk you saw in the code). Concrete and short, in ${if (I18n.lang == Lang.HE) "Hebrew" else "English"}; \"why\" says what in the project made you think of it.")
        appendLine("Answer with JSON only:")
        appendLine("""{"ideas": [{"text": "…", "why": "…"}], "close": [{"text": "…", "why": "…"}]}""")
    }

    /** What the good morning is told: the latest review of each project, when it is fresh (two days). */
    fun lines(projects: List<Project>): List<String> = projects.mapNotNull { p ->
        val r = last(p.id)?.takeIf { it.date >= LocalDate.now().minusDays(2).toString() } ?: return@mapNotNull null
        val bits = r.ideas.map { "idea: ${it.text} (${it.why})" } + r.close.map { "to close: ${it.text} (${it.why})" }
        if (bits.isEmpty()) null else "${p.name} (id: ${p.id}), reviewed ${r.date}: " + bits.joinToString("; ")
    }
}
