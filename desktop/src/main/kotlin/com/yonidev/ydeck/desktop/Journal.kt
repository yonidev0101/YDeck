package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.JournalDay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * What moved in each project, day by day, so the days connect: Claude Code starts every session from nothing, and
 * YDeck, which sees them all, keeps the thread. Every few minutes it compares each project's lists with what it saw
 * last (ticked off, added, new ideas), and takes the day's commits and sessions. Kept on this PC only, a month back,
 * in YDeck's own folder (never in the project's).
 */
object Journal {

    /** The three lists, as last seen. */
    data class Lists(val left: List<String>, val ideas: List<String>, val done: List<String>)

    private const val DAYS = 30
    private val dir: File get() = File(AppDirs.data, "journal").apply { mkdirs() }

    fun start() {
        Thread({
            while (true) {
                runCatching { updateAll() }
                Thread.sleep(10 * 60_000L)
            }
        }, "ydeck-journal").apply { isDaemon = true; start() }
    }

    fun updateAll() = Repo.deck.value.active.forEach { p -> runCatching { update(p) } }

    /** The project's days, newest first, at most [n], the empty ones left out. */
    fun days(projectId: String, n: Int = 7): List<JournalDay> = read(projectId).second.filterNot { it.empty }.take(n)

    private fun file(id: String) = File(dir, id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "_" } + ".json")

    private fun read(id: String): Pair<Lists?, List<JournalDay>> = runCatching {
        val o = JSONObject(file(id).readText())
        val last = o.optJSONObject("last")?.let { l -> Lists(strings(l, "left"), strings(l, "ideas"), strings(l, "done")) }
        val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(JournalDay::of) } }.orEmpty()
        last to days
    }.getOrDefault(null to emptyList())

    private fun strings(o: JSONObject, name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

    /** Today's entry for one project: what changed in its lists since last time, and the day's commits and sessions. */
    fun update(p: Project) {
        val local = Settings.state.value.localOf(p.id)
        val folder = local.dir ?: return
        val state = ProjectFiles.readLocal(folder)
        val now = Lists(state?.left.orEmpty(), state?.ideas.orEmpty(), state?.done.orEmpty())
        val today = LocalDate.now()
        val midnight = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val commits = GitOps.log(folder, 20).filter { it.at >= midnight }.map { it.subject }
        val sessions = local.folder?.let { Receipts.state.value[it] }.orEmpty().filter { it.startAt >= midnight }.map { it.title }
        val (last, days) = read(p.id)
        val newDays = step(last, now, days, today.toString(), commits, sessions)
        file(p.id).writeText(JSONObject()
            .put("last", JSONObject().put("left", JSONArray(now.left)).put("ideas", JSONArray(now.ideas)).put("done", JSONArray(now.done)))
            .put("days", JSONArray().apply { newDays.take(DAYS).forEach { put(it.toJson()) } }).toString())
    }

    /**
     * One look: what's new in [now] since [last] joins [date]'s entry (the first look only remembers the lists), and
     * the day's commits and sessions replace what that entry had. Returns the days, newest first.
     */
    fun step(last: Lists?, now: Lists, days: List<JournalDay>, date: String, commits: List<String>, sessions: List<String>): List<JournalDay> {
        val was = days.firstOrNull { it.date == date } ?: JournalDay(date)
        val day = if (last == null) was.copy(commits = commits, sessions = sessions) else was.copy(
            done = (was.done + (now.done - last.done.toSet())).distinct(),
            added = (was.added + (now.left - last.left.toSet()).filter { it !in now.done }).distinct(),
            ideas = (was.ideas + (now.ideas - last.ideas.toSet())).distinct(),
            commits = commits.ifEmpty { was.commits },
            sessions = sessions.ifEmpty { was.sessions },
        )
        return listOf(day) + days.filter { it.date != date }
    }

    /** The days as lines for the good morning's prompt: since [since] (a date), oldest first. */
    fun lines(projectId: String, since: LocalDate): List<String> = days(projectId, 7).filter { it.date >= since.toString() }.reversed().map { d ->
        buildList {
            if (d.done.isNotEmpty()) add("done: " + d.done.take(5).joinToString("; "))
            if (d.added.isNotEmpty()) add("added to what's left: " + d.added.take(4).joinToString("; "))
            if (d.ideas.isNotEmpty()) add("new ideas: " + d.ideas.take(3).joinToString("; "))
            if (d.commits.isNotEmpty()) add("${d.commits.size} commits (" + d.commits.take(3).joinToString("; ") + ")")
            if (d.sessions.isNotEmpty()) add("Claude sessions: " + d.sessions.take(3).joinToString("; "))
        }.joinToString(" · ").let { "${d.date}: $it" }
    }
}
