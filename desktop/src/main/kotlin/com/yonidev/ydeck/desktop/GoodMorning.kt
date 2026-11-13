package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.MorningTopic
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The good morning (see [com.yonidev.ydeck.link.Morning]): which projects it goes over, what Claude Code is told so
 * that it writes like a partner starting the day with the developer, and how its answer is read back into topics.
 * Everything Claude needs is in the prompt, so it reads nothing (no tools): quick, and the same every time.
 */
object GoodMorning {

    /** A project worked on within this many days goes into the good morning by itself. */
    private const val RECENT_DAYS = 14L

    /** The projects it goes over: picked ones, and the rest worked on lately, never archived or left out. */
    fun projects(): List<Project> {
        val s = Settings.state.value
        val now = System.currentTimeMillis()
        return Repo.deck.value.active.filter { p ->
            when (p.id) {
                in s.morningOut -> false
                in s.morningIn -> true
                else -> now - lastWorked(p) < RECENT_DAYS * 86_400_000L
            }
        }
    }

    /** When the project was last worked on: its last commit, its last session, its notes, or Claude in it now. */
    fun lastWorked(p: Project): Long {
        val local = Settings.state.value.localOf(p.id)
        val dir = local.dir ?: return 0
        if (ClaudeLink.statusIn(dir.path, ClaudeLink.sessions.value) != null) return System.currentTimeMillis()
        val state = ProjectFiles.readLocal(dir)
        return maxOf(local.folder?.let { Git.state.value[it]?.lastAt } ?: 0L, state?.handoff?.at ?: 0L, state?.modifiedAt ?: 0L)
    }

    /** The whole prompt: who it is, how it talks, what it answers with, the projects, and what was said last time. */
    fun prompt(projects: List<Project>, talk: List<String>, today: LocalDate = LocalDate.now()): String = buildString {
        val hebrew = I18n.lang == Lang.HE
        val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        appendLine("You are YDeck, the developer's project dashboard. This morning you write to them first: a good morning on their phone, from the projects below. You are their partner for the day, not a reporter.")
        appendLine()
        appendLine("Pick the two or three things that matter most today, each in one project: something to finish (closest to done), something stuck (changes not saved for days, commits not pushed, waiting on someone), an idea that has waited and is worth thinking about together, or a decision still open. Leave the rest out.")
        appendLine()
        appendLine("How to write: ${if (hebrew) "in Hebrew, the way an Israeli friend talks: warm, direct, short (\"בוא נסיים היום את…\", \"מה אתה אומר על…?\", \"מה קורה עם…?\")" else "in English, warm, direct and short"}. Speak to them (\"let's\", \"what do you think\"). Only what the projects below say: never invent a fact, a deadline or a file.")
        appendLine()
        appendLine("Answer with JSON only, no other text, in this shape:")
        appendLine("""{"hello": "one short opening line", "topics": [{"project": "<the project's id, as given below>", "kind": "finish | stuck | idea | decide", "say": "one or two sentences: what you noticed and what you suggest", "ask": "one short question for them", "step": "if they say let's go: what to ask Claude Code to do in that project's folder, one or two plain sentences, in English"}]}""")
        appendLine("At most three topics. \"say\" and \"ask\" are what they read on the phone, so keep them short.")
        appendLine("If they write back after this, answer them in plain, short conversational text (never JSON).")
        appendLine()
        appendLine("Today is $day, $today.")
        if (talk.isNotEmpty()) {
            appendLine()
            appendLine("Last time (keep to it: what they put off or asked to be reminded of comes back; what they set aside or declined doesn't):")
            talk.forEach { appendLine("- " + it.replace('\n', ' ').take(600)) }
        }
        appendLine()
        appendLine("The projects (${projects.size}):")
        projects.forEach { appendLine(); append(about(it)) }
    }

    /** One project, as much as helps decide the day: where it stands, what's left, ideas, Git, Claude, how long it rested. */
    private fun about(p: Project): String = buildString {
        val now = System.currentTimeMillis()
        val local = Settings.state.value.localOf(p.id)
        val dir = local.dir
        appendLine("## ${p.name} (id: ${p.id})" + (if (p.kind.isNotBlank()) " · ${p.kind}" else ""))
        if (dir == null) {
            if (local.note.isNotBlank()) appendLine("- next: ${local.note}")
            return@buildString
        }
        val shared = ProjectFiles.readShared(dir)
        val state = ProjectFiles.readLocal(dir)
        shared?.summary?.takeIf { it.isNotBlank() }?.let { appendLine("- what it is: ${it.take(240)}") }
        state?.now?.takeIf { it.isNotBlank() }?.let { appendLine("- now: $it") }
        (state?.next?.ifBlank { null } ?: local.note).takeIf { it.isNotBlank() }?.let { appendLine("- next step: $it") }
        state?.left?.takeIf { it.isNotEmpty() }?.let { appendLine("- left to do, in order: " + it.take(6).joinToString("; ")) }
        state?.ideas?.takeIf { it.isNotEmpty() }?.let { appendLine("- ideas set aside (not built): " + it.take(5).joinToString("; ")) }
        state?.done?.takeIf { it.isNotEmpty() }?.let { appendLine("- done lately: " + it.take(2).joinToString("; ")) }
        local.folder?.let { Git.state.value[it] }?.let { g ->
            val bits = buildList {
                if (g.changes > 0) add("${g.changes} changes not committed")
                if (g.ahead > 0) add("${g.ahead} commits not pushed")
                if (g.upstream == null && g.hasRemote) add("branch ${g.branch} isn't on the server")
                if (g.lastAt > 0) add("last commit ${days(now - g.lastAt)}")
            }
            if (bits.isNotEmpty()) appendLine("- Git: " + bits.joinToString(", "))
        }
        ClaudeLink.statusIn(dir.path, ClaudeLink.sessions.value)?.let { s -> appendLine("- Claude Code there now: ${s.mode.name.lowercase()}") }
    }

    private fun days(ms: Long): String = when (val d = ms / 86_400_000L) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$d days ago"
    }

    /** What Claude wrote, read back: the opening line and the topics (in projects it was given), or null when it isn't that. */
    fun read(answer: String, projects: List<Project>): Pair<String, List<MorningTopic>>? = runCatching {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = JSONObject(answer.substring(start, end + 1))
        val byId = projects.associateBy { it.id }
        val byName = projects.associateBy { it.name.lowercase() }
        val topics = o.optJSONArray("topics")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(MorningTopic::of) } }.orEmpty()
            .mapNotNull { t -> (byId[t.project] ?: byName[t.project.lowercase()])?.let { t.copy(project = it.id) } }
            .take(3)
        val hello = o.optString("hello").trim()
        if (topics.isEmpty() && hello.isEmpty()) null else hello to topics
    }.getOrNull()

    /** The same as Markdown, for the conversation it starts. */
    fun markdown(hello: String, topics: List<MorningTopic>): String = buildString {
        if (hello.isNotBlank()) appendLine(hello).appendLine()
        topics.forEach { t ->
            val name = Repo.deck.value.byId(t.project)?.name ?: t.project
            appendLine("**$name**: ${t.say}" + (if (t.ask.isNotBlank()) " ${t.ask}" else ""))
            appendLine()
        }
    }.trim()
}
