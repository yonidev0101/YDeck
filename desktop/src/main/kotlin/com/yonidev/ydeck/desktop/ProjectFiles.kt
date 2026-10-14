package com.yonidev.ydeck.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/*
 * Two small files in a project's folder that Claude Code keeps up to date as it works (it's told how when a session
 * starts, see [briefing]), and that YDeck reads:
 *
 *   .ydeck.json        shared with the repository: links to the project's services, commands, releases.
 *   .ydeck.local.json  personal, kept out of git: what's in progress now, the next step, and the last session's handoff.
 *
 * The decisions in .ydeck.json and the last handoff are the project's memory: every new session starts from them.
 *
 * YDeck only adds from them (a new link, a new command), never removes; the next step goes both ways with the note
 * in the project's panel.
 */

data class FileLink(val label: String, val url: String)
data class Release(val version: String, val date: String, val note: String)
/** A decision future sessions must keep to (a library chosen, an approach dropped), and its date (YYYY-MM-DD). */
data class Decision(val text: String, val date: String)
/** Where a session left the work: what it did, the next step it suggests, when, and by which agent. */
data class Handoff(val done: String, val next: String, val at: Long, val agent: String)
data class SharedFile(
    val links: List<FileLink>, val commands: List<Command>, val releases: List<Release>,
    /** What the project is, in two or three sentences (Claude writes it), and when (ms). */
    val summary: String = "", val summaryAt: Long = 0,
    val decisions: List<Decision> = emptyList(),
)
data class LocalFile(val now: String, val next: String, val handoff: Handoff? = null, val modifiedAt: Long = 0) {
    /**
     * The handoff's next step as a suggestion: only while the handoff is the newest thing in the file. Once the next
     * step was written after it (by the developer or an agent), it has been answered.
     */
    val suggestion: String get() = handoff?.takeIf { it.at <= 0 || modifiedAt - it.at < 60_000 }?.next.orEmpty()
}

object ProjectFiles {
    const val SHARED = ".ydeck.json"
    const val LOCAL = ".ydeck.local.json"

    fun readShared(dir: File): SharedFile? = runCatching {
        val o = JSONObject(File(dir, SHARED).takeIf { it.isFile }?.readText() ?: return null)
        fun arr(name: String) = o.optJSONArray(name) ?: JSONArray()
        fun <T> objs(a: JSONArray, f: (JSONObject) -> T?): List<T> = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(f) }
        SharedFile(
            links = objs(arr("links")) { l -> l.optString("url").trim().takeIf { it.startsWith("http") }?.let { FileLink(l.optString("label").trim(), it) } },
            commands = objs(arr("commands")) { c -> c.optString("line").trim().takeIf { it.isNotEmpty() }?.let { Command(c.optString("label").trim(), it) } },
            releases = objs(arr("releases")) { r -> r.optString("version").trim().takeIf { it.isNotEmpty() }?.let { Release(it, r.optString("date").trim(), r.optString("note").trim()) } },
            summary = o.optString("summary").trim(),
            summaryAt = runCatching { java.time.Instant.parse(o.optString("summaryAt")).toEpochMilli() }.getOrDefault(0L),
            decisions = objs(arr("decisions")) { d -> d.optString("text").trim().takeIf { it.isNotEmpty() }?.let { Decision(it, d.optString("date").trim()) } },
        )
    }.getOrNull()

    fun readLocal(dir: File): LocalFile? = runCatching {
        val f = File(dir, LOCAL).takeIf { it.isFile } ?: return null
        val o = JSONObject(f.readText())
        val h = o.optJSONObject("handoff")
        LocalFile(o.optString("now").trim(), o.optString("next").trim(), h?.let {
            Handoff(it.optString("done").trim(), it.optString("next").trim(), runCatching { java.time.Instant.parse(it.optString("at")).toEpochMilli() }.getOrDefault(0L), it.optString("agent"))
        }?.takeIf { it.done.isNotEmpty() || it.next.isNotEmpty() }, f.lastModified())
    }.getOrNull()

    /** The next step written from YDeck's panel; whatever else the file holds stays. */
    fun writeNext(dir: File, next: String) {
        runCatching {
            val f = File(dir, LOCAL)
            val o = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
            o.put("next", next.trim())
            f.writeText(o.toString(2) + "\n")
            seen[dir.path.lowercase()] = next.trim()
            keepLocalOutOfGit(dir)
        }
    }

    /** The last session's handoff, into the personal file. */
    fun writeHandoff(dir: File, h: Handoff) {
        runCatching {
            val f = File(dir, LOCAL)
            val o = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
            o.put("handoff", JSONObject().put("done", h.done).put("next", h.next).put("agent", h.agent)
                .put("at", java.time.Instant.ofEpochMilli(h.at).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()))
            f.writeText(o.toString(2) + "\n")
            keepLocalOutOfGit(dir)
        }
    }

    /** The decisions in .ydeck.json, rewritten (the newest first); everything else in the file stays. */
    fun writeDecisions(dir: File, decisions: List<Decision>) {
        runCatching {
            val f = File(dir, SHARED)
            val o = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
            o.put("decisions", JSONArray().apply { decisions.forEach { put(JSONObject().put("text", it.text).put("date", it.date)) } })
            f.writeText(o.toString(2) + "\n")
        }
    }

    fun keepLocalOutOfGit(dir: File) = GitExclude.add(dir, LOCAL)

    /** The last next step seen in each folder's file (or written there), to tell Claude's change from ours. */
    private val seen = ConcurrentHashMap<String, String>()

    /** Brings what the files say into YDeck: new links into the project, new commands, a changed next step. */
    @Synchronized
    fun sync() {
        val settings = Settings.state.value
        var deck = Repo.deck.value
        var changed = false
        for (p in deck.projects) {
            val local = settings.localOf(p.id)
            val dir = local.dir ?: continue
            readShared(dir)?.let { shared ->
                val add = shared.links.filter { l -> p.actions.none { it.url.trimEnd('/') == l.url.trimEnd('/') } }
                if (add.isNotEmpty()) {
                    val actions = add.map { l -> val s = Catalog.detect(l.url) ?: Catalog.LINK; DeckAction(s, l.url, l.label.ifBlank { s.label }) }
                    deck = Deck(deck.projects.map { if (it.id == p.id) it.copy(actions = it.actions + actions) else it })
                    changed = true
                }
                val cmds = shared.commands.filter { c -> local.commands.none { it.line == c.line } }
                if (cmds.isNotEmpty()) Settings.setLocal(p.id, Settings.state.value.localOf(p.id).copy(commands = local.commands + cmds))
            }
            AgentsFile.sync(dir)
            readLocal(dir)?.next?.let { next ->
                val key = dir.path.lowercase()
                if (seen[key] != next) {
                    seen[key] = next
                    if (next.isNotEmpty() && next != Settings.state.value.localOf(p.id).note) Settings.setNote(p.id, next)
                }
            }
        }
        if (changed) Repo.save(deck)
    }

    /** What Claude Code is told at the start of a session in the project's folder. */
    fun briefing(p: Project, dir: File): String {
        val local = readLocal(dir)
        val note = Settings.state.value.localOf(p.id).note
        val profile = Chrome.profileFor(p)
        return buildString {
            appendLine("This folder is the project \"${p.name}\" in YDeck, the user's project dashboard (widgets on the Windows desktop, a strip in File Explorer).")
            if (p.googleEmail != null || profile != null) {
                appendLine("Account: ${p.googleEmail ?: "-"}" + (profile?.let { "; its links open in the Chrome profile \"${it.name}\"" } ?: "") + ".")
            }
            if (p.actions.isNotEmpty()) {
                appendLine("Links: " + p.actions.take(14).joinToString("; ") { "${it.label} ${it.url}" })
            }
            val now = local?.now.orEmpty()
            val next = local?.next?.ifBlank { null } ?: note
            if (now.isNotBlank() || next.isNotBlank()) appendLine("Where it stands in YDeck: now \"$now\", next \"$next\".")
            local?.handoff?.let { h ->
                appendLine("The last session (${h.agent.ifBlank { "an agent" }}, ${java.time.Instant.ofEpochMilli(h.at)}) handed off: done \"${h.done}\"; suggested next \"${h.next}\".")
            }
            val decisions = readShared(dir)?.decisions.orEmpty()
            if (decisions.isNotEmpty()) {
                appendLine("Decisions already made in this project (keep to them unless the user changes them):")
                decisions.take(15).forEach { appendLine("- ${it.text}" + (if (it.date.isNotBlank()) " (${it.date})" else "")) }
            }
            appendLine()
            appendLine("Keep YDeck current as you work, briefly and without making a point of it:")
            appendLine("- When you finish a meaningful piece of work, or the user is about to stop, write $LOCAL in the project root: {\"now\": \"what is in progress, one short line\", \"next\": \"the next concrete step\"}, in the language the user writes in. It is personal and kept out of git.")
            appendLine("- When you connect the project to a new service or console (a Supabase project, a Stripe account, a new Vercel project...), add {\"label\": \"...\", \"url\": \"https://...\"} to \"links\" in $SHARED.")
            appendLine("- When you release a version, add {\"version\": \"...\", \"date\": \"YYYY-MM-DD\", \"note\": \"one line\"} at the start of \"releases\" in $SHARED.")
            appendLine("- When the user and you settle something future sessions must keep to (a library or service chosen, an approach dropped, a convention), add {\"text\": \"the decision and why, one line\", \"date\": \"YYYY-MM-DD\"} at the start of \"decisions\" in $SHARED, in the user's language.")
            appendLine("- When what the project is or does changes, rewrite \"summary\" in $SHARED: two or three plain sentences (what it is, who it is for, the main stack), with \"summaryAt\" set to the current time in ISO 8601.")
            append("$SHARED is shared through the repository: only \"summary\", \"decisions\", \"links\", \"commands\" ({\"label\", \"line\"}) and \"releases\", never secrets, tokens or personal emails. Create a file when it is missing and keep what is already in it.")
        }
    }
}

/**
 * "Update with Claude": Claude Code reads the project (read-only tools, no edits) and answers with a short summary,
 * which YDeck writes into .ydeck.json. One run per folder at a time.
 */
object ClaudeSummary {
    private val _busy = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    /** Folders whose summary Claude is writing now. */
    val busy: kotlinx.coroutines.flow.StateFlow<Set<String>> = _busy

    /** Returns null when it worked, else what went wrong (for the page to say). */
    fun run(dir: File): String? {
        val claude = Tools.claude?.exe ?: return tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב")
        if (dir.path in _busy.value) return null
        _busy.value = _busy.value + dir.path
        try {
            val language = if (I18n.rtl) "Hebrew" else "English"
            val prompt = "Summarize this project for its dashboard in YDeck: what it is, who it is for, and the main stack, in two or three " +
                "plain sentences in $language. No headings, lists, markdown or preamble: answer with the summary only. Read the README and " +
                "the code as you need; do not change any file."
            val p = ProcessBuilder(claude.path, "-p", prompt, "--output-format", "text", "--allowedTools", "Read", "Glob", "Grep", "LS")
                .directory(dir).redirectErrorStream(false).start()
            p.outputStream.close()
            val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            if (!p.waitFor(4, java.util.concurrent.TimeUnit.MINUTES)) { p.destroy(); return tr("Claude took too long", "Claude לקח יותר מדי זמן") }
            if (p.exitValue() != 0 || out.isBlank()) return tr("Claude didn't answer", "Claude לא ענה")
            writeSummary(dir, out.lines().filter { it.isNotBlank() }.joinToString(" ").take(800))
            return null
        } catch (e: Exception) {
            return e.message
        } finally {
            _busy.value = _busy.value - dir.path
        }
    }

    /** The summary into .ydeck.json, keeping everything else in it. */
    fun writeSummary(dir: File, text: String) {
        val f = File(dir, ProjectFiles.SHARED)
        val o = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        o.put("summary", text.trim())
        o.put("summaryAt", java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString())
        f.writeText(o.toString(2) + "\n")
    }
}

/** Names kept out of git for one clone only, in .git/info/exclude (nothing in the repository changes). */
object GitExclude {
    fun add(dir: File, name: String) {
        runCatching {
            val git = File(dir, ".git").takeIf { it.isDirectory } ?: return
            val exclude = File(git, "info/exclude").apply { parentFile.mkdirs() }
            val text = if (exclude.exists()) exclude.readText() else ""
            if (text.lines().none { it.trim() == name || it.trim() == "/$name" }) {
                exclude.appendText((if (text.isEmpty() || text.endsWith("\n")) "" else "\n") + name + "\n")
            }
        }
    }
}
