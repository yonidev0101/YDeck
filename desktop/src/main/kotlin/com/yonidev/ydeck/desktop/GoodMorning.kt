package com.yonidev.ydeck.desktop

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The good morning (see [com.yonidev.ydeck.link.Morning]): what Claude Code is told, so that it writes to the developer
 * like a partner starting the day with them, not a report. It reads only (the same Claude as Ask YDeck, see
 * [Assistant.read]); the projects as YDeck knows them are below, and yesterday's talk, so "remind me tomorrow" holds.
 */
object GoodMorning {

    /** The whole prompt: who it is, how it talks, the projects, and what was said last time. */
    fun prompt(talk: List<String>, today: LocalDate = LocalDate.now()): String = buildString {
        val hebrew = I18n.lang == Lang.HE
        val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        appendLine("You are YDeck, the developer's project dashboard, and this morning you write to them first: a good morning on their phone, from the projects below. You are their partner for the day, not a reporter.")
        appendLine()
        appendLine("How to write:")
        appendLine("- In ${if (hebrew) "Hebrew, the way an Israeli friend talks: warm, direct, short sentences (\"בוא נסיים היום את…\", \"מה אתה אומר על…?\", \"מה קורה עם…?\")" else "English, warm and direct, short sentences"}. Speak to them, in the first person (\"let's\", \"what do you think\").")
        appendLine("- Three or four things at most, one short paragraph each, starting with the project's name in bold. Each one is a suggestion or a question that asks for their view: something to finish today (what is closest to done), something stuck (changes not saved for days, commits not pushed, waiting on someone), an idea that has waited and is worth thinking about together, or a decision still open.")
        appendLine("- Pick what matters most today; leave the rest out. No headings, no long lists, no summary of everything, no numbers they can't act on.")
        appendLine("- Only what the projects below say. Never invent a fact, a deadline or a file. When unsure, ask.")
        appendLine("- End with one open question: what they feel like doing today.")
        appendLine("- Under 160 words. Plain Markdown (bold, short paragraphs).")
        appendLine("- You can only read: you don't change files or start work. When they want to start on something, tell them to open that project on the phone and start Claude from there.")
        appendLine("- You may read a project's folder when its state below isn't enough (never .env files or anything that holds a key), but keep it quick.")
        appendLine()
        appendLine("Today is $day, $today.")
        if (talk.isNotEmpty()) {
            appendLine()
            appendLine("Your last morning's conversation with them (keep to what they asked: what they put off, what they asked to be reminded of; don't repeat a push they declined):")
            talk.forEach { appendLine("- " + it.replace('\n', ' ').take(600)) }
        }
        val deck = Repo.deck.value
        val projects = deck.active
        appendLine()
        appendLine("The projects (${projects.size}; archived ones left out):")
        projects.forEach { appendLine(); append(about(it)) }
    }

    /** One project, as much as helps decide the day: where it stands, what's left, ideas, Git, Claude, how long it rested. */
    private fun about(p: Project): String = buildString {
        val now = System.currentTimeMillis()
        val local = Settings.state.value.localOf(p.id)
        val dir = local.dir
        appendLine("## ${p.name}" + (if (p.kind.isNotBlank()) " (${p.kind})" else ""))
        if (dir == null) {
            if (local.note.isNotBlank()) appendLine("- next: ${local.note}")
            appendLine("- no folder on this PC")
            return@buildString
        }
        val shared = ProjectFiles.readShared(dir)
        val state = ProjectFiles.readLocal(dir)
        shared?.summary?.takeIf { it.isNotBlank() }?.let { appendLine("- what it is: ${it.take(300)}") }
        state?.now?.takeIf { it.isNotBlank() }?.let { appendLine("- now: $it") }
        (state?.next?.ifBlank { null } ?: local.note).takeIf { it.isNotBlank() }?.let { appendLine("- next step: $it") }
        state?.left?.takeIf { it.isNotEmpty() }?.let { appendLine("- left to do, in order: " + it.take(8).joinToString("; ")) }
        state?.ideas?.takeIf { it.isNotEmpty() }?.let { appendLine("- ideas set aside (not built): " + it.take(6).joinToString("; ")) }
        state?.done?.takeIf { it.isNotEmpty() }?.let { appendLine("- done lately: " + it.take(3).joinToString("; ")) }
        state?.handoff?.let { h -> appendLine("- last session: ${h.agent.ifBlank { "an agent" }}, ${days(now - h.at)}; did \"${h.done.take(200)}\"") }
        local.folder?.let { Git.state.value[it] }?.let { g ->
            val bits = buildList {
                add("branch ${g.branch}")
                if (g.changes > 0) add("${g.changes} changes not committed")
                if (g.ahead > 0) add("${g.ahead} commits not pushed")
                if (g.upstream == null && g.hasRemote) add("the branch isn't on the server")
                if (g.lastAt > 0) add("last commit ${days(now - g.lastAt)} (\"${g.lastSubject.take(80)}\")")
            }
            appendLine("- Git: " + bits.joinToString(", "))
        }
        ClaudeLink.statusIn(dir.path, ClaudeLink.sessions.value)?.let { s -> appendLine("- Claude Code there now: ${s.mode.name.lowercase()}") }
        shared?.decisions?.firstOrNull()?.let { appendLine("- latest decision: ${it.text.take(160)} (${it.date})") }
        shared?.releases?.firstOrNull()?.let { appendLine("- latest release: ${it.version} (${it.date})") }
    }

    private fun days(ms: Long): String = when (val d = ms / 86_400_000L) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$d days ago"
    }
}
