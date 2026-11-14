package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * The save card, in place of a list of files: what isn't saved yet, in words, what to do about it, and one button.
 * Claude (Sonnet, reading nothing: what git says is in the prompt) writes the words and the steps; YDeck runs the
 * steps itself, from a closed list: commit, push (never force), keep files out of git, merge a branch that merges
 * cleanly. Anything else stops and says so. Claude is asked only when git's state changed; when everything is saved,
 * the card says so without asking anyone.
 */

/** One step of the card, as YDeck runs it. */
data class SaveStep(val kind: String, val paths: List<String> = emptyList(), val message: String = "", val branch: String = "") {
    companion object {
        const val COMMIT = "commit"
        const val PUSH = "push"
        const val IGNORE = "ignore"
        const val MERGE = "merge"
        val KINDS = listOf(COMMIT, PUSH, IGNORE, MERGE)
    }

    fun toJson(): JSONObject = JSONObject().put("kind", kind).put("paths", JSONArray(paths)).put("message", message).put("branch", branch)
}

/** The card: [state] what's open, in a sentence or two; [proposal] what to do; the [steps] that do it. */
data class SaveCard(val state: String, val proposal: String, val steps: List<SaveStep>, val signature: String, val clean: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("state", state).put("proposal", proposal).put("signature", signature).put("clean", clean)
        .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })

    companion object {
        fun of(o: JSONObject): SaveCard = SaveCard(o.optString("state"), o.optString("proposal"),
            o.optJSONArray("steps")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Saving::stepOf) } }.orEmpty(),
            o.optString("signature"), o.optBoolean("clean"))
    }
}

object Saving {
    /** What git says about a folder, gathered once: what the card is written from. */
    data class Facts(
        val info: GitInfo,
        /** main or master, when the repository has one. */
        val main: String?,
        /** Local branches not merged into [main] yet, with their last commit's subject. */
        val unmerged: List<Pair<String, String>>,
        /** The branches' tips, for the signature. */
        val tips: String,
        val diffStat: String,
        val diff: String,
        /** New files, with a look at the small text ones. */
        val untracked: List<String>,
        val log: List<String>,
    ) {
        val clean: Boolean get() = info.changes == 0 && info.ahead == 0 && unmerged.isEmpty() && info.phase == GitPhase.NONE

        /** Changes when anything the card speaks of changes. */
        val signature: String get() = listOf(info.branch, info.ahead, info.behind, info.upstream, info.phase, tips,
            info.files.sortedBy { it.path }.joinToString(",") { it.code + it.path }).joinToString("|").hashCode().toString(16)
    }

    private val _cards = MutableStateFlow<Map<String, SaveCard>>(emptyMap())
    /** By folder: the latest card. */
    val cards: StateFlow<Map<String, SaveCard>> = _cards
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    /** Folders whose card is being written, or whose steps run. */
    val busy: StateFlow<Set<String>> = _busy
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors

    private val file: File get() = File(AppDirs.data, "saving.json")

    init {
        // The last cards, so a start doesn't ask Claude again about what it already said.
        runCatching {
            val o = JSONObject(file.readText())
            _cards.value = o.keySet().associateWith { SaveCard.of(o.getJSONObject(it)) }
        }
    }

    private fun persist() = runCatching {
        file.writeText(JSONObject().apply { _cards.value.forEach { (k, v) -> put(k, v.toJson()) } }.toString())
    }

    /** Cards are written on their own (off in the renders, where no Claude should run). */
    @Volatile var auto = true

    private val _results = MutableStateFlow<Map<String, Pair<Long, String>>>(emptyMap())
    /** By folder: when the last "do it" ran, and what became of it: "" when it worked, else why not, in words. */
    val results: StateFlow<Map<String, Pair<Long, String>>> = _results

    /** One at a time, for the cards written without anyone looking (every project, for the phone). */
    private val quiet = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "ydeck-saving-all").apply { isDaemon = true } }
    private val queued: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Like [ensure], but queued behind the others: each project's card ready before anyone opens its page. */
    fun ensureQuietly(dir: File) {
        val key = dir.path
        if (!auto || key in _busy.value || !queued.add(key)) return
        quiet.execute {
            try {
                if (key !in _busy.value) {
                    _busy.update { it + key }
                    runCatching { update(dir) }.onFailure { e -> _errors.update { it + (key to (e.message ?: tr("Claude didn't answer", "Claude לא ענה"))) } }
                    _busy.update { it - key }
                }
            } finally { queued.remove(key) }
        }
    }

    /** Runs the card's steps and keeps what came of it (for the phone). */
    fun runAndKeep(dir: File): String? = run(dir).also { why -> _results.update { it + (dir.path to (System.currentTimeMillis() to (why ?: ""))) } }

    /** A card to draw, for the renders. */
    fun preview(dir: File, card: SaveCard) = _cards.update { it + (dir.path to card) }

    /** The card for [dir], written again only when git's state changed since the last one. In the background. */
    fun ensure(dir: File) {
        val key = dir.path
        if (!auto) return
        if (key in _busy.value) return
        _busy.update { it + key }
        Thread({
            runCatching { update(dir) }.onFailure { e -> _errors.update { it + (key to (e.message ?: tr("Claude didn't answer", "Claude לא ענה"))) } }
            _busy.update { it - key }
        }, "ydeck-saving").apply { isDaemon = true; start() }
    }

    private fun update(dir: File) {
        val facts = facts(dir) ?: return
        if (_cards.value[dir.path]?.signature == facts.signature) return
        _errors.update { it - dir.path }
        val card = if (facts.clean) cleanCard(facts) else {
            val answer = ClaudeJson.ask(dir, prompt(facts), schema(), tools = false, minutes = 3)
            parse(answer, facts)
        }
        _cards.update { it + (dir.path to card) }
        persist()
    }

    fun cleanCard(f: Facts): SaveCard = SaveCard(
        if (f.info.hasRemote) tr("Everything is saved.", "הכול שמור.")
        else tr("Everything is saved on this PC. The project has no server yet (like GitHub).", "הכול שמור במחשב. לפרויקט עוד אין שרת (כמו GitHub)."),
        "", emptyList(), f.signature, clean = true)

    fun facts(dir: File): Facts? {
        val info = Git.read(dir) ?: return null
        val main = listOf("main", "master").firstOrNull { Git.run(dir, listOf("rev-parse", "--verify", "--quiet", "refs/heads/$it"), errors = false).ok }
        val heads = Git.run(dir, listOf("for-each-ref", "--format=%(refname:short)\t%(objectname:short)\t%(subject)", "refs/heads"), errors = false).out
        val notMerged = if (main == null) emptySet() else
            Git.run(dir, listOf("for-each-ref", "--format=%(refname:short)", "--no-merged=$main", "refs/heads"), errors = false).out.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val unmerged = heads.lines().mapNotNull { l -> l.split('\t').takeIf { it.size >= 3 && it[0] in notMerged }?.let { it[0] to it[2] } }
        val tracked = info.files.filter { !it.untracked }.map { it.path }
        val stat = if (tracked.isEmpty()) "" else Git.run(dir, listOf("diff", "--stat", "HEAD", "--") + tracked.take(200), errors = false).out
        var diff = if (tracked.isEmpty()) "" else Git.run(dir, listOf("diff", "--no-ext-diff", "HEAD", "--") + tracked.take(200), errors = false).out
        if (diff.length > 12_000) diff = diff.take(12_000) + "\n[… the rest is cut]"
        val untracked = info.files.filter { it.untracked }.take(40).map { c ->
            val f = File(dir, c.path)
            val size = if (f.isFile) " (${f.length() / 1024} KB)" else " (folder)"
            val peek = if (f.isFile && f.length() < 20_000 && looksLikeText(f)) "\n" + runCatching { f.readText().take(800) }.getOrDefault("") else ""
            c.path + size + peek
        }
        val log = GitOps.log(dir, 8).map { it.subject }
        return Facts(info, main, unmerged, heads, stat, diff, untracked, log)
    }

    private fun looksLikeText(f: File): Boolean = f.extension.lowercase() in setOf("md", "txt", "json", "kt", "kts", "js", "ts", "tsx", "jsx", "html", "css", "py", "yml", "yaml", "toml", "xml", "sh", "ps1", "cjs", "mjs", "env.example", "gitignore")

    fun schema(): JSONObject = ClaudeJson.obj(listOf("state", "proposal", "steps"),
        "state" to ClaudeJson.string(),
        "proposal" to ClaudeJson.string(),
        "steps" to JSONObject().put("type", "array").put("items", ClaudeJson.obj(listOf("kind"),
            "kind" to JSONObject().put("type", "string").put("enum", JSONArray(SaveStep.KINDS)),
            "paths" to ClaudeJson.strings(),
            "message" to ClaudeJson.string(),
            "branch" to ClaudeJson.string())))

    fun prompt(f: Facts): String = buildString {
        val language = if (I18n.rtl) "Hebrew" else "English"
        appendLine("You are YDeck, the developer's project dashboard. Below is what git says about one project. Tell the developer, in $language, plainly, what isn't saved yet and what to do about it, and give the steps that do it.")
        appendLine("- \"state\": one or two short sentences: what's open, in words (what the changes are about, a file that looks left by mistake, branches waiting to be merged, commits not on the server yet). No file lists unless there are only one or two files.")
        appendLine("- \"proposal\": one sentence: what you suggest doing.")
        appendLine("- \"steps\": the steps that do it, in order, from these only:")
        appendLine("  {\"kind\":\"commit\",\"paths\":[the files to commit, from the list below; empty for all],\"message\":\"the commit message, in the style and language of the recent ones\"}")
        appendLine("  {\"kind\":\"ignore\",\"paths\":[new files that shouldn't be in git, from the list below]} (adds them to .gitignore)")
        appendLine("  {\"kind\":\"push\"} (sends the commits to the server; never force)")
        appendLine("  {\"kind\":\"merge\",\"branch\":\"a branch from the list below\"} (merges it into ${f.main ?: "the main branch"})")
        appendLine("  Leave out what you're not sure of. Nothing else is possible: if the situation needs more (a conflict, a rebase), say so in \"proposal\" and give no steps. Don't suggest committing files that look like secrets, build output or something left by mistake.")
        appendLine("The developer sees the card and presses one button to run the steps; nothing runs before.")
        appendLine()
        appendLine("Branch: ${f.info.branch}" + (f.info.upstream?.let { ", following $it" } ?: ", not on the server yet") + (if (!f.info.hasRemote) ", the repository has no server (remote)" else ""))
        if (f.info.ahead > 0) appendLine("Commits not on the server: ${f.info.ahead}")
        if (f.info.behind > 0) appendLine("Commits on the server not here yet: ${f.info.behind}")
        if (f.info.phase != GitPhase.NONE) appendLine("git is in the middle of: ${f.info.phase.name.lowercase()}" + (if (f.info.conflicts > 0) ", with ${f.info.conflicts} conflicts" else ""))
        if (f.main != null) appendLine("Main branch: ${f.main}")
        if (f.unmerged.isNotEmpty()) appendLine("Branches not merged into ${f.main}: " + f.unmerged.joinToString("; ") { "${it.first} (last: ${it.second})" })
        val changed = f.info.files.filter { !it.untracked }
        if (changed.isNotEmpty()) { appendLine(); appendLine("Changed files:"); changed.take(100).forEach { appendLine("${it.kind} ${it.path}") } }
        if (f.untracked.isNotEmpty()) { appendLine(); appendLine("New files:"); f.untracked.forEach { appendLine("- $it") } }
        if (f.diffStat.isNotBlank()) { appendLine(); appendLine(f.diffStat.trim()) }
        if (f.diff.isNotBlank()) { appendLine(); appendLine("Diff:"); appendLine(f.diff) }
        appendLine()
        appendLine("Recent commit messages:")
        appendLine(f.log.joinToString("\n") { "- $it" }.ifBlank { "(none yet)" })
    }

    fun stepOf(o: JSONObject): SaveStep? {
        val kind = o.optString("kind").trim().takeIf { it in SaveStep.KINDS } ?: return null
        return SaveStep(kind, ClaudeJson.list(o.optJSONArray("paths")), o.optString("message").trim(), o.optString("branch").trim())
    }

    /** Claude's answer, with only the steps YDeck can run as said: files from the list, branches from the list, a message for a commit. */
    fun parse(o: JSONObject, f: Facts): SaveCard {
        val steps = o.optJSONArray("steps")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::stepOf) } }.orEmpty()
        return SaveCard(o.optString("state").trim(), o.optString("proposal").trim(), valid(steps, f), f.signature)
    }

    fun valid(steps: List<SaveStep>, f: Facts): List<SaveStep> {
        val paths = f.info.files.map { it.path }.toSet()
        val untracked = f.info.files.filter { it.untracked }.map { it.path }.toSet()
        val branches = f.unmerged.map { it.first }.toSet()
        val ok = steps.filter { s ->
            when (s.kind) {
                SaveStep.COMMIT -> s.message.isNotBlank() && s.paths.all { it in paths } && f.info.files.isNotEmpty()
                SaveStep.IGNORE -> s.paths.isNotEmpty() && s.paths.all { it in untracked }
                SaveStep.PUSH -> f.info.hasRemote
                SaveStep.MERGE -> f.main != null && s.branch in branches && s.branch != f.main
                else -> false
            }
        }
        // A step that doesn't hold makes the rest unsafe to run in its place (a push without its commit): none then.
        return if (ok.size == steps.size) ok else emptyList()
    }

    /**
     * Runs the card's steps, in order, stopping at the first that fails. Only when git still says what the card was
     * written from. Returns null when all of them worked, or what went wrong, in words.
     */
    fun run(dir: File): String? {
        val card = _cards.value[dir.path] ?: return tr("Nothing to do", "אין מה לעשות")
        if (card.steps.isEmpty()) return tr("Nothing to do", "אין מה לעשות")
        if (dir.path in _busy.value) return null
        _busy.update { it + dir.path }
        try {
            val facts = facts(dir) ?: return tr("Not a git repository", "התיקייה לא ב־git")
            if (facts.signature != card.signature) {
                ensure(dir)
                return tr("Something changed since. Look at the card again.", "משהו השתנה בינתיים. כדאי להסתכל שוב בכרטיס.")
            }
            for (s in card.steps) {
                val why = when (s.kind) {
                    SaveStep.IGNORE -> ignore(dir, s.paths)
                    SaveStep.COMMIT -> GitOps.commit(dir, s.message, s.paths.ifEmpty { null })
                    SaveStep.PUSH -> GitOps.push(dir)
                    SaveStep.MERGE -> merge(dir, s.branch, facts.main ?: return tr("No main branch", "אין ענף ראשי"))
                    else -> tr("A step YDeck doesn't run", "צעד ש־YDeck לא מריץ")
                }
                if (why != null) return why
            }
            return null
        } finally {
            _busy.update { it - dir.path }
            Git.refreshOne(dir.path)
            ensure(dir)
        }
    }

    /** The files into .gitignore, one line each (from the folder's root), unless a line already says so. */
    fun ignore(dir: File, paths: List<String>): String? = runCatching {
        val f = File(dir, ".gitignore")
        val text = if (f.exists()) f.readText() else ""
        val have = text.lines().map { it.trim() }.toSet()
        val add = paths.map { "/" + it.trimStart('/') }.filter { it !in have && it.removePrefix("/") !in have }
        if (add.isNotEmpty()) f.appendText((if (text.isEmpty() || text.endsWith("\n")) "" else "\n") + add.joinToString("\n") + "\n")
        null
    }.getOrElse { it.message ?: ".gitignore" }

    /**
     * [branch] into [main], only as git can do it cleanly. On [main]: a merge, taken back at the first conflict.
     * On [branch] itself, with [main] behind it: [main] moves up to it (a fast-forward; nothing is checked out).
     * Anything else is for Claude or the developer.
     */
    fun merge(dir: File, branch: String, main: String): String? {
        val current = Git.read(dir)?.branch
        if (current == main) {
            if ((Git.read(dir)?.changes ?: 0) > 0) return tr("Save the open changes first", "קודם שומרים את השינויים הפתוחים")
            val r = Git.run(dir, listOf("merge", "--no-edit", branch), seconds = 60)
            if (r.ok) return null
            Git.run(dir, listOf("merge", "--abort"), errors = false)
            return tr("$branch doesn't merge cleanly into $main. Nothing changed; this one is for Claude.", "$branch לא מתמזג נקי ל־$main. שום דבר לא השתנה; את זה כדאי לתת ל־Claude.")
        }
        val ff = Git.run(dir, listOf("merge-base", "--is-ancestor", main, branch), errors = false).ok
        if (!ff) return tr("$main has moved on, so $branch can't simply move it up. This one is for Claude.", "$main התקדם, אז אי אפשר פשוט לקדם אותו ל־$branch. את זה כדאי לתת ל־Claude.")
        val r = Git.run(dir, listOf("update-ref", "refs/heads/$main", "refs/heads/$branch", Git.run(dir, listOf("rev-parse", "refs/heads/$main"), errors = false).out.trim()))
        return if (r.ok) null else GitOps.said(r.out)
    }
}
