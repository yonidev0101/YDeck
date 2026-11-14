package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * Ask YDeck as a partner: the conversation goes on from one day to the next (kept in YDeck's folder), it knows the
 * developer from "About me" (their words, kept here, read into every conversation), and an answer can end in things to
 * do, shown as buttons. YDeck does them, only on a click; Claude itself still only reads.
 */
object Partner {
    /** Something the answer suggests doing: [kind] one of [KINDS], about [project] (an id; empty for none). */
    data class Act(val kind: String, val project: String, val text: String)

    const val LEFT = "left"
    const val IDEA = "idea"
    const val NEXT = "next"
    /** Work on it now: a conversation with Claude in the project, starting with [Act.text]. */
    const val GO = "go"
    /** A line for "About me". */
    const val ABOUT = "about"
    private val KINDS = setOf(LEFT, IDEA, NEXT, GO, ABOUT)

    // ── about me ──

    private val aboutFile: File get() = File(AppDirs.data, "about-me.md")
    private val _about = MutableStateFlow(runCatching { aboutFile.readText() }.getOrDefault(""))
    /** How the developer works, their goals, what they focus on: in their own words. */
    val about: StateFlow<String> = _about

    fun setAbout(text: String) {
        _about.value = text
        runCatching { aboutFile.writeText(text) }
    }

    fun addAbout(line: String) {
        val t = line.trim().ifEmpty { return }
        if (_about.value.lines().any { it.trim().removePrefix("- ").equals(t, ignoreCase = true) }) return
        setAbout(_about.value.trimEnd().let { if (it.isEmpty()) "- $t\n" else "$it\n- $t\n" })
    }

    // ── what the answer suggests ──

    /** How an answer on the computer says what to do: a fenced block at its end, read out by [split]. */
    val FORMAT = """When something follows from your answer that YDeck can do with one click, end the answer with a block like this (and nothing after it):
```ydeck-actions
[{"kind": "go", "project": "the project's name", "text": "the first message to Claude in that project, ready to send"}]
```
Kinds: "go" work on it now (YDeck opens a conversation with Claude in that project with your message); "left" add a line to the project's left list; "idea" add a line to its ideas; "next" set its next step; "about" a line for the user's "About me" (only something they said about how they work or what they want, never a guess; no project). At most 4, the most useful first; leave the block out when nothing follows. Every text in the language the user writes in."""

    /** The answer without its block, and what the block suggests (unknown kinds and projects left out). */
    fun split(answer: String, projects: List<Project>): Pair<String, List<Act>> {
        val m = Regex("```ydeck-actions\\s*(.*?)```", RegexOption.DOT_MATCHES_ALL).find(answer) ?: return answer.trim() to emptyList()
        val text = answer.removeRange(m.range).trim()
        val acts = runCatching { JSONArray(m.groupValues[1].trim()) }.getOrNull()?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
                val kind = o.optString("kind").takeIf { it in KINDS } ?: return@mapNotNull null
                val t = o.optString("text").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val name = o.optString("project").trim()
                val p = projects.firstOrNull { it.name.equals(name, ignoreCase = true) || it.id == name }
                if (kind != ABOUT && p == null) return@mapNotNull null
                Act(kind, p?.id.orEmpty(), t.take(1000))
            }.take(4)
        }.orEmpty()
        return text to acts
    }

    /** Does [a]. Returns null when it worked, else why not, in words. */
    fun run(a: Act): String? {
        if (a.kind == ABOUT) { addAbout(a.text); return null }
        val p = Repo.deck.value.byId(a.project) ?: return tr("The project isn't here any more", "הפרויקט כבר לא כאן")
        val dir = Settings.state.value.localOf(p.id).dir ?: return tr("The project has no folder on this PC", "לפרויקט אין תיקייה במחשב הזה")
        when (a.kind) {
            LEFT -> Plan.add(dir, Plan.LEFT, a.text)
            IDEA -> Plan.add(dir, Plan.IDEAS, a.text)
            NEXT -> { ProjectFiles.writeNext(dir, a.text); Settings.setNote(p.id, a.text) }
            GO -> {
                Nav.set(Screen.Project(p.id))
                Nav.front.value++
                Talk.load(dir)
                if (Talk.chats.value[dir.path]?.busy == true) return tr("Claude is already working in this project", "Claude כבר עובד בפרויקט הזה")
                if (Talk.elsewhere(Talk.chats.value[dir.path]?.session)) Talk.fresh(dir)
                Talk.send(dir, a.text)
            }
        }
        Pulses.refreshSoon()
        return null
    }

    /** The question behind "What now?": one recommendation, its reason, and the way in. */
    val WHAT_NOW: String get() = tr(
        "What now? Recommend one thing to work on right now, across all my projects: which project, what exactly, and why, in a sentence. Take into account what moved lately, what's left, goals, and what waits for me. Then offer it as a \"go\" action.",
        "מה עכשיו? תמליץ על דבר אחד לעבוד עליו עכשיו, מכל הפרויקטים: איזה פרויקט, מה בדיוק, ולמה, במשפט. תתחשב במה שהתקדם לאחרונה, במה שנשאר, ביעדים ובמה שמחכה לי. ואז תציע אותו כפעולת \"go\".")

    // ── the conversation, kept between days ──

    private val file: File get() = File(AppDirs.data, "partner.json")

    data class Kept(val session: String?, val home: String?, val turns: List<Assistant.Turn>)

    fun load(): Kept = runCatching {
        val o = JSONObject(file.readText())
        val turns = o.optJSONArray("turns")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().map { t ->
            Assistant.Turn(t.optString("q"), t.optString("a").ifEmpty { null }, t.optString("e").ifEmpty { null }, t.optInt("reads"), t.optInt("seconds"),
                acts = t.optJSONArray("acts")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
                    .map { Act(it.optString("kind"), it.optString("project"), it.optString("text")) },
                at = t.optLong("at"))
        }
        Kept(o.optString("session").ifEmpty { null }, o.optString("home").ifEmpty { null }, turns)
    }.getOrDefault(Kept(null, null, emptyList()))

    fun save(session: String?, home: String?, turns: List<Assistant.Turn>) = runCatching {
        file.writeText(JSONObject().put("session", session ?: "").put("home", home ?: "")
            .put("turns", JSONArray().apply {
                turns.filter { it.answer != null || it.error != null }.takeLast(60).forEach { t ->
                    put(JSONObject().put("q", t.question).put("a", t.answer ?: "").put("e", t.error ?: "").put("reads", t.reads).put("seconds", t.seconds).put("at", t.at)
                        .put("acts", JSONArray().apply { t.acts.forEach { put(JSONObject().put("kind", it.kind).put("project", it.project).put("text", it.text)) } }))
                }
            }).toString())
    }

    /** The last turns in a few lines, for a conversation that has to start again (its Claude session is gone). */
    fun recap(turns: List<Assistant.Turn>): String = turns.filter { it.answer != null }.takeLast(6).joinToString("\n") { t ->
        "Me: ${t.question.take(400)}\nYDeck: ${t.answer!!.take(700)}"
    }
}
