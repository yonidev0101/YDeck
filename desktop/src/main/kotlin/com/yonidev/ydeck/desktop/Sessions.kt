package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/*
 * Every agent session leaves a receipt. When Claude Code (or Codex) starts in a project's git folder, YDeck quietly
 * keeps the code as it is (a commit no branch points to, under refs/ydeck/, made through a separate index: the folder,
 * the staged files and the branches are never touched). When the session stops, it does the same again, and the two
 * say exactly what the session changed: which files, each one's diff, ready to commit, or to put back as they were.
 */

/** What a session did: when, what it was asked, and the files it changed (A, M or D) between [before] and [after]. */
data class Receipt(
    val id: String,
    val agent: Agent,
    val folder: String,
    val startAt: Long,
    val endAt: Long,
    val prompts: List<String>,
    /** The code before the session and after it (commits under refs/ydeck/). */
    val before: String,
    val after: String?,
    val files: List<GitChange>,
    /** The files were put back as they were before; this commit holds them as they were before that (to undo it). */
    val rewound: String? = null,
) {
    val title: String get() = prompts.firstOrNull { it.isNotBlank() }?.let { ClaudeLink.firstLine(it) } ?: tr("A session", "סשן")
}

object Receipts {
    private const val KEEP = 20
    /** Where receipts are kept (tests point it elsewhere). */
    @Volatile var store: File = File(AppDirs.local, "receipts.json")
    /** One at a time, in order, off the hooks' thread: a hook is answered at once. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ydeck-receipts").apply { isDaemon = true } }

    private val _state = MutableStateFlow(load())
    /** By project folder, the latest first. */
    val state: StateFlow<Map<String, List<Receipt>>> = _state

    /** Sessions in progress: session id → its project folder. */
    private val open = ConcurrentHashMap<String, String>()

    /** A session starts (or is asked something): the code is kept as it is, once per session; each prompt is noted. */
    fun begin(id: String, agent: Agent, cwd: String, prompt: String?, now: Long) {
        val folder = Settings.projectAt(File(cwd))?.second ?: return
        worker.execute {
            runCatching {
                val known = find(id)
                if (known == null) {
                    val dir = File(folder)
                    if (Git.gitDir(dir) == null) return@runCatching
                    val before = Snapshot.take(dir, "ydeck: before ${agent.label} session $id", "before-$id") ?: return@runCatching
                    put(Receipt(id, agent, folder, now, now, listOfNotNull(prompt?.takeIf { it.isNotBlank() }), before, null, emptyList()))
                } else if (!prompt.isNullOrBlank() && known.prompts.lastOrNull() != prompt) {
                    put(known.copy(prompts = (known.prompts + prompt).takeLast(12)))
                }
                open[id] = folder
            }
        }
    }

    /** A session stops (a turn ends, or it's over): what it changed so far, against the code before it. */
    fun end(id: String, now: Long) {
        worker.execute {
            runCatching {
                val r = find(id) ?: return@runCatching
                val dir = File(r.folder)
                val after = Snapshot.take(dir, "ydeck: after ${r.agent.label} session $id", "after-$id") ?: return@runCatching
                put(r.copy(endAt = now, after = after, files = Snapshot.changes(dir, r.before, after)))
            }
        }
    }

    /** Waits for the work queued so far (tests, and a rewind right after a session). */
    fun settle() { worker.submit {}.get() }

    fun find(id: String): Receipt? = _state.value.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }

    /** The diff of one file the session changed. */
    fun diff(r: Receipt, path: String): List<DiffLine> {
        val dir = File(r.folder)
        val after = r.after ?: return emptyList()
        return GitOps.parseDiff(Git.run(dir, listOf("diff", "--no-ext-diff", "--no-renames", r.before, after, "--", path), errors = false).out)
    }

    /**
     * Puts the session's files back as they were before it (only those files; nothing else in the folder). What they
     * are now is kept first, so [undoRewind] can bring it back. Returns null when it worked, or what went wrong.
     */
    fun rewind(r: Receipt): String? {
        val dir = File(r.folder)
        if (r.files.isEmpty()) return null
        val now = Snapshot.take(dir, "ydeck: before rewinding ${r.id}", "rewound-${r.id}") ?: return tr("Couldn't keep the files as they are now", "לא הצלחתי לשמור את המצב הנוכחי")
        val there = Snapshot.has(dir, r.before, r.files.map { it.path })
        val error = Snapshot.restore(dir, r.before, r.files.map { it.path to (it.path in there) })
        if (error == null) put(r.copy(rewound = now))
        Git.refreshOne(r.folder)
        return error
    }

    /** Brings back what a rewind put away. */
    fun undoRewind(r: Receipt): String? {
        val dir = File(r.folder)
        val kept = r.rewound ?: return null
        val there = Snapshot.has(dir, kept, r.files.map { it.path })
        val error = Snapshot.restore(dir, kept, r.files.map { it.path to (it.path in there) })
        if (error == null) put(r.copy(rewound = null))
        Git.refreshOne(r.folder)
        return error
    }

    private fun put(r: Receipt) {
        var dropped: List<Receipt> = emptyList()
        _state.update { all ->
            val list = listOf(r) + all[r.folder].orEmpty().filter { it.id != r.id }
            val sorted = list.sortedByDescending { it.startAt }
            // Sessions that changed nothing make way first (the latest few stay: they may be still at work).
            val kept = sorted.filterIndexed { i, x -> x.files.isNotEmpty() || x.rewound != null || i < 5 }.take(KEEP)
            dropped = sorted - kept.toSet()
            all + (r.folder to kept)
        }
        // The commits of receipts no longer kept are let go (git cleans them up in its own time).
        dropped.forEach { d -> Snapshot.forget(File(d.folder), d.id) }
        save()
    }

    // ── kept in %LOCALAPPDATA%\YDeck\receipts.json ──

    private fun save() {
        runCatching {
            store.writeText(JSONObject().apply {
                _state.value.forEach { (folder, list) ->
                    put(folder, JSONArray().apply { list.forEach { put(toJson(it)) } })
                }
            }.toString(1))
        }
    }

    fun toJson(r: Receipt): JSONObject = JSONObject().put("id", r.id).put("agent", r.agent.name).put("folder", r.folder).put("startAt", r.startAt).put("endAt", r.endAt)
        .put("prompts", JSONArray(r.prompts)).put("before", r.before).put("after", r.after ?: "").put("rewound", r.rewound ?: "")
        .put("files", JSONArray().apply { r.files.forEach { put(it.code.trim() + "\t" + it.path) } })

    fun fromJson(o: JSONObject): Receipt = Receipt(
        o.getString("id"), runCatching { Agent.valueOf(o.optString("agent")) }.getOrDefault(Agent.CLAUDE), o.getString("folder"),
        o.optLong("startAt"), o.optLong("endAt"),
        o.optJSONArray("prompts")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        o.getString("before"), o.optString("after").ifBlank { null },
        o.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getString(it) }.map { GitChange(it.substringBefore('\t'), it.substringAfter('\t')) } }.orEmpty(),
        o.optString("rewound").ifBlank { null },
    )

    private fun load(): Map<String, List<Receipt>> = runCatching {
        val o = JSONObject(store.readText())
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() } } }
    }.getOrDefault(emptyMap())
}

/** The code as it is, kept as a commit without touching the folder, the index or any branch. */
object Snapshot {
    private val IDENT = mapOf("GIT_AUTHOR_NAME" to "YDeck", "GIT_AUTHOR_EMAIL" to "ydeck@localhost", "GIT_COMMITTER_NAME" to "YDeck", "GIT_COMMITTER_EMAIL" to "ydeck@localhost")
    /** New files this big (not ignored, not yet in git) are left out: a snapshot never copies a video or a dump. */
    private const val BIG = 20L * 1024 * 1024

    /** Returns the commit (kept under refs/ydeck/sessions/[name]), or null. */
    fun take(dir: File, message: String, name: String): String? {
        val gd = Git.gitDir(dir) ?: return null
        // A copy of the real index: git already knows which files are unchanged, so this is quick.
        val index = File.createTempFile("ydeck-index", "")
        try {
            val real = File(gd, "index")
            if (real.isFile) real.copyTo(index, overwrite = true) else index.delete()
            val env = IDENT + ("GIT_INDEX_FILE" to index.path)
            val big = Git.run(dir, listOf("ls-files", "--others", "--exclude-standard", "-z"), errors = false).out.split('\u0000')
                .filter { it.isNotBlank() && File(dir, it).length() > BIG }.take(50)
            if (!Git.run(dir, listOf("add", "-A", "--", ".") + big.map { ":(exclude,literal)$it" }, seconds = 120, env = env).ok) return null
            val tree = Git.run(dir, listOf("write-tree"), env = env, errors = false).out.trim().takeIf { it.length >= 40 } ?: return null
            val head = Git.run(dir, listOf("rev-parse", "-q", "--verify", "HEAD"), errors = false).out.trim().takeIf { it.length >= 40 }
            val commit = Git.run(dir, listOf("commit-tree", tree, "-m", message) + (if (head != null) listOf("-p", head) else emptyList()), env = env, errors = false)
                .out.trim().takeIf { it.length >= 40 } ?: return null
            Git.run(dir, listOf("update-ref", "refs/ydeck/sessions/${safe(name)}", commit), errors = false)
            return commit
        } finally {
            index.delete()
        }
    }

    /** Files that differ between two snapshots: A(dded), M(odified), D(eleted). */
    fun changes(dir: File, from: String, to: String): List<GitChange> {
        val out = Git.run(dir, listOf("diff", "--name-status", "--no-renames", "-z", from, to), errors = false)
        if (!out.ok) return emptyList()
        val parts = out.out.split('\u0000').filter { it.isNotEmpty() }
        return parts.chunked(2).mapNotNull { c -> if (c.size < 2) null else GitChange(c[0].take(1), c[1]) }
    }

    /**
     * Puts [files] back as they are in [source]: those there are written out, the others removed. Only the files
     * themselves change; the index and the branch stay as they are.
     */
    fun restore(dir: File, source: String, files: List<Pair<String, Boolean>>): String? {
        val (there, notThere) = files.partition { it.second }
        there.map { it.first }.chunked(100).forEach { chunk ->
            val r = Git.run(dir, listOf("restore", "--source=$source", "--worktree", "--") + chunk)
            if (!r.ok) return GitOps.said(r.out)
        }
        notThere.forEach { (path, _) ->
            val f = File(dir, path)
            if (f.isFile && !f.delete()) return tr("Couldn't remove $path (is it open?)", "לא הצלחתי למחוק את $path (אולי הוא פתוח?)")
        }
        return null
    }

    /** Which of [paths] the snapshot holds. */
    fun has(dir: File, commit: String, paths: List<String>): Set<String> = paths.chunked(100).flatMap { chunk ->
        Git.run(dir, listOf("ls-tree", "-r", "-z", "--name-only", commit, "--") + chunk, errors = false).out.split('\u0000').filter { it.isNotBlank() }
    }.toSet()

    fun forget(dir: File, id: String) {
        listOf("before-$id", "after-$id", "rewound-$id").forEach { Git.run(dir, listOf("update-ref", "-d", "refs/ydeck/sessions/${safe(it)}"), errors = false) }
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "-").take(120)
}

/**
 * Codex, read from the sessions it writes (~/.codex/sessions/YYYY/MM/DD/rollout-….jsonl, the first line naming the
 * folder it works in): a file still being written is a session at work; one quiet for a while has finished. Read only:
 * YDeck changes nothing in Codex's settings.
 */
object Codex {
    private val root = File(System.getProperty("user.home"), ".codex/sessions")
    /** A session written to this recently is at work. */
    private const val ACTIVE_MS = 90_000L
    private val cwdOf = ConcurrentHashMap<String, String>()
    private val seen = ConcurrentHashMap<String, Long>()

    val present: Boolean get() = root.isDirectory

    /** The sessions of the last two days, by file: (id, folder, last written). */
    fun recent(base: File = root, now: Long = System.currentTimeMillis()): List<Triple<String, String, Long>> {
        if (!base.isDirectory) return emptyList()
        val days = listOf(0L, 1L).map { java.time.LocalDate.now().minusDays(it) }
        return days.flatMap { d ->
            File(base, "%04d/%02d/%02d".format(d.year, d.monthValue, d.dayOfMonth)).listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }.orEmpty().toList()
        }.filter { now - it.lastModified() < 36 * 60 * 60 * 1000L }.mapNotNull { f ->
            val cwd = cwdOf.getOrPut(f.path) { folderOf(f) ?: return@mapNotNull null }
            Triple(f.nameWithoutExtension, cwd, f.lastModified())
        }
    }

    /** The folder a session works in, from its first lines ({"payload":{"cwd":…}} in "session_meta"). */
    fun folderOf(f: File): String? = runCatching {
        f.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.take(5).firstNotNullOfOrNull { l ->
                runCatching { JSONObject(l) }.getOrNull()?.let { o -> o.optJSONObject("payload")?.optString("cwd")?.ifBlank { null } ?: o.optString("cwd").ifBlank { null } }
            }
        }
    }.getOrNull()

    /** Codex's sessions in project folders, for the widgets; each one's start and end leave a receipt. */
    fun poll(now: Long = System.currentTimeMillis()) {
        if (!present) return
        val found = recent(now = now).filter { (_, cwd, _) -> Settings.projectAt(File(cwd)) != null }.associate { (id, cwd, at) ->
            val active = now - at < ACTIVE_MS
            val last = seen.put(id, at)
            when {
                // New, or writing again after a pause: a piece of work begins.
                active && (last == null || now - last > ACTIVE_MS) -> Receipts.begin(id, Agent.CODEX, cwd, null, now)
                // Gone quiet: it ended; what it changed goes on its receipt.
                !active && last != null && now - last < ACTIVE_MS + 10_000 -> Receipts.end(id, at)
            }
            "codex-$id" to ClaudeSession("codex-$id", cwd, if (active) ClaudeMode.WORKING else ClaudeMode.DONE, at, "", null, at, Agent.CODEX)
        }
        ClaudeLink.setCodex(found)
    }
}
