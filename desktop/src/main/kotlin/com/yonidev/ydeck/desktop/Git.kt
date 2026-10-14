package com.yonidev.ydeck.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/*
 * Git, for every project folder: where it stands (read every little while, and at once when .git changes), the
 * server asked quietly for new commits, and the few things a developer does all day: see what changed, commit (with
 * a message Claude can write), push, pull, switch branch. Nothing that throws work away: no reset, no force.
 */

/** One changed path: its two-letter status ("M ", " M", "??", "UU"…) and where it is, relative to the repository. */
data class GitChange(val code: String, val path: String, val from: String? = null) {
    val untracked: Boolean get() = code == "??"
    val conflict: Boolean get() = code in CONFLICTS
    /** One letter for the list: A(dded), M(odified), D(eleted), R(enamed), U(nresolved). */
    val kind: Char get() = when {
        untracked -> 'A'
        conflict -> 'U'
        'R' in code -> 'R'
        'D' in code -> 'D'
        'A' in code -> 'A'
        else -> 'M'
    }
    val name: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/', "")

    companion object { val CONFLICTS = setOf("DD", "AU", "UD", "UA", "DU", "AA", "UU") }
}

/** Work git is in the middle of, which the developer has to finish. */
enum class GitPhase { NONE, MERGING, REBASING, PICKING }

data class GitInfo(
    val branch: String, val changes: Int, val ahead: Int, val behind: Int,
    /** The last commit: when (ms, 0 when there's none) and its subject. */
    val lastAt: Long = 0, val lastSubject: String = "",
    val files: List<GitChange> = emptyList(),
    /** The branch on the server this one follows ("origin/main"); null when it follows none yet. */
    val upstream: String? = null,
    /** The repository has a server to push to (a remote). */
    val hasRemote: Boolean = false,
    /** The repository's page (https://github.com/me/app), from its origin. */
    val web: String? = null,
    /** When it last heard from the server (a fetch, a pull): 0 for never. */
    val fetchedAt: Long = 0,
    val phase: GitPhase = GitPhase.NONE,
) {
    val conflicts: Int get() = files.count { it.conflict }

    /** "main · 3 changes · ↑1" */
    fun describe(): String = buildList {
        add(branch)
        add(if (changes == 0) tr("clean", "נקי") else count(changes, "change", "changes", "שינוי אחד", "שינויים"))
        if (ahead > 0) add("↑$ahead")
        if (behind > 0) add("↓$behind")
    }.joinToString(" · ") { if (I18n.rtl) "\u200F$it\u200F" else it }
}

data class GitCommit(val hash: String, val at: Long, val author: String, val subject: String)

/** A line of a diff: a hunk's head, a line kept, added or removed. */
data class DiffLine(val kind: Char, val text: String)

object Git {
    private val exe: File? by lazy { Tools.which("git.exe") ?: File("C:\\Program Files\\Git\\cmd\\git.exe").takeIf { it.isFile } }
    val installed: Boolean get() = exe != null

    private val _state = MutableStateFlow<Map<String, GitInfo>>(emptyMap())
    /** By folder path. */
    val state: StateFlow<Map<String, GitInfo>> = _state

    suspend fun refresh(folders: Collection<String>) = withContext(Dispatchers.IO) {
        _state.value = folders.distinct().mapNotNull { f -> read(File(f))?.let { f to it } }.toMap()
        GitWatch.follow(_state.value.keys)
    }

    /** One folder again, now (after a commit, a push, or a change under .git). */
    fun refreshOne(folder: String) {
        val info = read(File(folder))
        _state.update { if (info == null) it - folder else it + (folder to info) }
    }

    /** The repository's own folder: .git, or where a worktree's .git file points. */
    fun gitDir(dir: File): File? {
        val g = File(dir, ".git")
        return when {
            g.isDirectory -> g
            g.isFile -> runCatching { g.readText().trim().removePrefix("gitdir:").trim() }.getOrNull()?.let { p -> File(p).let { if (it.isAbsolute) it else File(dir, p) } }?.takeIf { it.isDirectory }
            else -> null
        }
    }

    fun read(dir: File): GitInfo? {
        val gd = gitDir(dir) ?: return null
        val status = run(dir, listOf("status", "--porcelain=v1", "-b", "-z"), seconds = 8, errors = false).takeIf { it.ok } ?: return null
        var info = parse(status.out) ?: return null
        val last = run(dir, listOf("log", "-1", "--format=%ct%x09%s"), errors = false)
        val at = last.out.trim().substringBefore('\t').toLongOrNull()
        if (last.ok && at != null) info = info.copy(lastAt = at * 1000, lastSubject = last.out.trim().substringAfter('\t'))
        val config = runCatching { File(gd.takeIf { File(it, "config").isFile } ?: commonDir(gd), "config").readText() }.getOrDefault("")
        val origin = Scanner.originUrl(config)
        return info.copy(
            hasRemote = config.contains("[remote \""),
            web = origin?.let(Scanner::webUrl),
            fetchedAt = File(commonDir(gd), "FETCH_HEAD").lastModified(),
            phase = when {
                File(gd, "rebase-merge").exists() || File(gd, "rebase-apply").exists() -> GitPhase.REBASING
                File(gd, "MERGE_HEAD").exists() -> GitPhase.MERGING
                File(gd, "CHERRY_PICK_HEAD").exists() -> GitPhase.PICKING
                else -> GitPhase.NONE
            },
        )
    }

    /** A worktree's own folder points at the main repository's in "commondir". */
    fun commonDir(gd: File): File = runCatching { File(gd, "commondir").readText().trim() }.getOrNull()
        ?.let { File(gd, it).canonicalFile }?.takeIf { it.isDirectory } ?: gd

    /** `git status --porcelain=v1 -b` (with -z: NUL between entries, a rename's old path after it; or lines). */
    fun parse(out: String): GitInfo? {
        val z = out.contains('\u0000')
        val parts = if (z) out.split('\u0000') else out.lines()
        val head = parts.firstOrNull()?.takeIf { it.startsWith("## ") }?.removePrefix("## ") ?: return null
        val branch = when {
            head.startsWith("No commits yet on ") -> head.removePrefix("No commits yet on ")
            head.startsWith("Initial commit on ") -> head.removePrefix("Initial commit on ")
            head.startsWith("HEAD (no branch)") -> "HEAD"
            else -> head.substringBefore("...").substringBefore(" ")
        }
        val upstream = head.substringAfter("...", "").substringBefore(" ").ifBlank { null }?.takeIf { "[gone]" !in head }
        fun count(word: String) = Regex("$word (\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
        val files = mutableListOf<GitChange>()
        var i = 1
        while (i < parts.size) {
            val e = parts[i]
            i++
            if (e.length < 4) continue
            val code = e.take(2)
            var path = e.drop(3)
            var from: String? = null
            if (z && (code[0] == 'R' || code[0] == 'C')) { from = parts.getOrNull(i); i++ } // the old path comes next
            if (!z && " -> " in path) { from = path.substringBefore(" -> ").trim('"'); path = path.substringAfter(" -> ") }
            files += GitChange(code, path.trim('"'), from)
        }
        return GitInfo(branch, files.size, count("ahead"), count("behind"), files = files, upstream = upstream)
    }

    // ── running git ──

    class Result(val ok: Boolean, val out: String)

    /**
     * git in [dir], waiting at most [seconds]. Never asks in the console; [interactive] lets Git's credential manager
     * show its own sign-in window (a push or pull asked for), otherwise it fails quietly.
     */
    fun run(dir: File, args: List<String>, seconds: Long = 30, interactive: Boolean = false, errors: Boolean = true, env: Map<String, String> = emptyMap()): Result {
        val git = exe ?: return Result(false, tr("Git isn't installed on this PC", "Git לא מותקן במחשב"))
        return runCatching {
            val pb = ProcessBuilder(listOf(git.path, "--no-optional-locks", "-c", "core.quotePath=false", "-c", "color.ui=false") + args).directory(dir)
            pb.environment().apply {
                put("GIT_TERMINAL_PROMPT", "0")
                put("LC_ALL", "C")
                if (!interactive) put("GCM_INTERACTIVE", "never")
                putAll(env)
            }
            if (errors) pb.redirectErrorStream(true) else pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            val p = pb.start()
            p.outputStream.close()
            val out = StringBuilder()
            val reader = Thread { runCatching { p.inputStream.bufferedReader(Charsets.UTF_8).use { r -> val buf = CharArray(8192); while (true) { val n = r.read(buf); if (n < 0) break; out.append(buf, 0, n) } } } }
            reader.isDaemon = true
            reader.start()
            if (!p.waitFor(seconds, TimeUnit.SECONDS)) { p.destroyForcibly(); return Result(false, tr("Git took too long", "Git לקח יותר מדי זמן")) }
            reader.join(2000)
            Result(p.exitValue() == 0, out.toString())
        }.getOrElse { Result(false, it.message ?: "git") }
    }

    // ── the server, quietly ──

    private val tried = ConcurrentHashMap<String, Long>()

    /** Folders that haven't heard from their server in 10 minutes ask it now (git fetch), one after the other. */
    suspend fun fetchDue(folders: Collection<String>) = withContext(Dispatchers.IO) {
        if (!Settings.state.value.gitFetch) return@withContext
        val now = System.currentTimeMillis()
        folders.forEach { f ->
            val info = _state.value[f] ?: return@forEach
            if (!info.hasRemote || now - info.fetchedAt < FETCH_EVERY || now - (tried[f] ?: 0) < FETCH_EVERY) return@forEach
            tried[f] = now
            if (run(File(f), listOf("fetch", "--quiet", "--no-tags"), seconds = 45, errors = false).ok) refreshOne(f)
        }
    }

    private const val FETCH_EVERY = 10 * 60_000L
}

/**
 * Each repository's .git, looked at every moment: a commit, a checkout or a pull made anywhere (a terminal, an editor,
 * Claude Code) shows in YDeck at once, without waiting for the next round. Only the dates and sizes of a few files are
 * read and nothing stays open: Windows won't rename a folder while something inside it is held open, and a
 * WatchService on .git held it open for as long as YDeck ran.
 */
object GitWatch {
    private val dirs = ConcurrentHashMap<String, File>()
    private val seen = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap<String, Long>()
    @Volatile private var started = false

    @Synchronized
    fun follow(folders: Collection<String>) {
        (dirs.keys - folders.toSet()).forEach { dirs.remove(it); seen.remove(it); pending.remove(it) }
        folders.filter { !dirs.containsKey(it) }.forEach { f ->
            val gd = Git.gitDir(File(f)) ?: return@forEach
            dirs[f] = gd
            seen[f] = signature(f, gd)
        }
        if (!started) { started = true; start() }
    }

    /** What changes when git does anything: HEAD, the index, the branch and its server's, a merge or a rebase under way. */
    private fun signature(folder: String, gd: File): String {
        val common = Git.commonDir(gd)
        val head = runCatching { File(gd, "HEAD").readText().trim() }.getOrDefault("")
        val files = buildList {
            listOf("HEAD", "index", "ORIG_HEAD", "MERGE_HEAD", "CHERRY_PICK_HEAD", "rebase-merge", "rebase-apply").forEach { add(File(gd, it)) }
            listOf("packed-refs", "FETCH_HEAD").forEach { add(File(common, it)) }
            if (head.startsWith("ref:")) add(File(common, head.removePrefix("ref:").trim()))
            Git.state.value[folder]?.upstream?.let { add(File(common, "refs/remotes/$it")) }
        }
        return head + "|" + files.joinToString("|") { "${it.lastModified()}:${it.length()}" }
    }

    private fun start() {
        Thread({
            while (true) {
                runCatching {
                    val now = System.currentTimeMillis()
                    dirs.forEach { (f, gd) ->
                        val sig = signature(f, gd)
                        if (sig != seen[f]) { seen[f] = sig; pending[f] = now }
                    }
                    // Once git has been quiet for a moment.
                    pending.entries.filter { now - it.value > 400 }.forEach { (f, _) -> pending.remove(f); Git.refreshOne(f) }
                }
                Thread.sleep(700)
            }
        }, "ydeck-git-watch").apply { isDaemon = true; start() }
    }
}

/** What the developer does with git from YDeck. Each returns null when it worked, or what went wrong, in words. */
object GitOps {
    enum class Op { COMMIT, PUSH, PULL, FETCH, SWITCH, MESSAGE, INIT }

    private val _busy = MutableStateFlow<Map<String, Op>>(emptyMap())
    /** By folder: what's running there now. */
    val busy: StateFlow<Map<String, Op>> = _busy

    private inline fun <T> doing(dir: File, op: Op, block: () -> T): T {
        _busy.update { it + (dir.path to op) }
        try { return block() } finally {
            _busy.update { it - dir.path }
            Git.refreshOne(dir.path)
        }
    }

    /** Commits [paths] (null: everything), new files included. */
    fun commit(dir: File, message: String, paths: List<String>?): String? = doing(dir, Op.COMMIT) {
        if (message.isBlank()) return tr("Write a message first", "קודם כותבים הודעה")
        val msg = File.createTempFile("ydeck-commit", ".txt").apply { writeText(message.trim() + "\n", Charsets.UTF_8); deleteOnExit() }
        try {
            // A merge is committed whole; a long list goes by "everything" too (Windows' command line has a limit).
            val all = paths == null || paths.size > 200 || Git.state.value[dir.path]?.phase == GitPhase.MERGING
            val add = Git.run(dir, if (all) listOf("add", "-A") else listOf("add", "-A", "--") + paths!!)
            if (!add.ok) return said(add.out)
            val commit = Git.run(dir, listOf("commit", "-F", msg.path) + (if (all) emptyList() else listOf("--") + paths!!), seconds = 60)
            if (commit.ok) null else said(commit.out)
        } finally { msg.delete() }
    }

    fun push(dir: File): String? = doing(dir, Op.PUSH) {
        val info = Git.state.value[dir.path] ?: Git.read(dir)
        if (info?.hasRemote != true) return tr("This repository has no server yet (a remote, like GitHub)", "לריפו הזה עוד אין שרת (remote, כמו GitHub)")
        val r = Git.run(dir, if (info.upstream == null) listOf("push", "-u", "origin", "HEAD") else listOf("push"), seconds = 120, interactive = true)
        if (r.ok) null else said(r.out)
    }

    /** Only a fast-forward: when both sides have new commits, it says so and changes nothing. */
    fun pull(dir: File): String? = doing(dir, Op.PULL) {
        val r = Git.run(dir, listOf("pull", "--ff-only"), seconds = 120, interactive = true)
        if (r.ok) null else said(r.out)
    }

    fun fetch(dir: File): String? = doing(dir, Op.FETCH) {
        val r = Git.run(dir, listOf("fetch", "--quiet", "--no-tags"), seconds = 60, interactive = true)
        if (r.ok) null else said(r.out)
    }

    fun switch(dir: File, branch: String): String? = doing(dir, Op.SWITCH) {
        val r = Git.run(dir, listOf("switch", branch))
        if (r.ok) null else said(r.out)
    }

    fun init(dir: File): String? = doing(dir, Op.INIT) {
        val r = Git.run(dir, listOf("init"))
        if (r.ok) null else said(r.out)
    }

    /** The branches here, the latest worked on first. */
    fun branches(dir: File): List<String> =
        Git.run(dir, listOf("for-each-ref", "--sort=-committerdate", "--count=30", "--format=%(refname:short)", "refs/heads"), errors = false)
            .takeIf { it.ok }?.out?.lines()?.filter { it.isNotBlank() }.orEmpty()

    fun log(dir: File, n: Int = 30): List<GitCommit> =
        Git.run(dir, listOf("log", "-$n", "--format=%h%x09%ct%x09%an%x09%s"), errors = false).takeIf { it.ok }?.out?.lines()?.mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 4) null else GitCommit(f[0], (f[1].toLongOrNull() ?: 0) * 1000, f[2], f.drop(3).joinToString("\t"))
        }.orEmpty()

    /** What changed in one file since the last commit (a new file: all of it, added). */
    fun diff(dir: File, change: GitChange): List<DiffLine> {
        if (change.untracked) {
            val f = File(dir, change.path)
            if (f.isDirectory) return listOf(DiffLine('@', tr("A new folder", "תיקייה חדשה")))
            if (f.length() > 400_000) return listOf(DiffLine('@', tr("A new file, too big to show", "קובץ חדש, גדול מכדי להציג")))
            val text = runCatching { f.readText() }.getOrDefault("")
            if (text.contains('\u0000')) return listOf(DiffLine('@', tr("A new binary file", "קובץ בינארי חדש")))
            return text.lines().take(MAX_LINES).map { DiffLine('+', it) }
        }
        val r = Git.run(dir, listOf("diff", "--no-ext-diff", "HEAD", "--", change.path), errors = false)
        val out = if (r.ok && r.out.isNotBlank()) r.out else Git.run(dir, listOf("diff", "--no-ext-diff", "--cached", "--", change.path), errors = false).out
        return parseDiff(out)
    }

    fun parseDiff(out: String): List<DiffLine> {
        val lines = mutableListOf<DiffLine>()
        var body = false
        for (l in out.lines()) {
            when {
                l.startsWith("@@") -> { body = true; lines += DiffLine('@', l.substringAfter("@@").substringAfter("@@").trim().ifBlank { l }) }
                !body -> if (l.startsWith("Binary files")) lines += DiffLine('@', tr("A binary file changed", "קובץ בינארי השתנה"))
                l.startsWith("+") -> lines += DiffLine('+', l.drop(1))
                l.startsWith("-") -> lines += DiffLine('-', l.drop(1))
                l.startsWith(" ") -> lines += DiffLine(' ', l.drop(1))
                l.startsWith("diff --git") -> body = false
            }
            if (lines.size >= MAX_LINES) break
        }
        return lines
    }

    private const val MAX_LINES = 3000

    /**
     * A commit message for what's waiting, from Claude Code: it reads the diff (given, not fetched) and writes in the
     * language and style of the repository's last commits. Returns the message, or null and what went wrong.
     */
    fun suggestMessage(dir: File, paths: List<String>?): Pair<String?, String?> = doing(dir, Op.MESSAGE) {
        val claude = Tools.claude?.exe ?: return null to tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב")
        val info = Git.state.value[dir.path] ?: Git.read(dir) ?: return null to tr("Not a git repository", "התיקייה לא ב־git")
        val chosen = info.files.filter { paths == null || it.path in paths }
        if (chosen.isEmpty()) return null to tr("Nothing to commit", "אין מה לשמור")
        val tracked = chosen.filter { !it.untracked }.map { it.path }
        var diff = if (tracked.isEmpty()) "" else Git.run(dir, listOf("diff", "--no-ext-diff", "HEAD", "--") + tracked.take(200), errors = false).out
            .ifBlank { Git.run(dir, listOf("diff", "--no-ext-diff", "--cached", "--") + tracked.take(200), errors = false).out }
        chosen.filter { it.untracked }.take(20).forEach { c ->
            val f = File(dir, c.path)
            if (f.isFile && f.length() < 40_000) diff += "\n--- new file: ${c.path}\n" + runCatching { f.readText() }.getOrDefault("").take(6000)
        }
        if (diff.length > 60_000) diff = diff.take(60_000) + "\n[… the rest is cut]"
        val recent = log(dir, 8).joinToString("\n") { "- " + it.subject }
        val prompt = buildString {
            appendLine("Write the git commit message for the changes below.")
            appendLine("A subject line under 72 characters, in the imperative mood. If the change needs it, a blank line and a body of at most three short lines.")
            appendLine("Match the language and the style of this repository's recent commit messages; if there are none, write in English.")
            appendLine("Answer with the commit message only: no quotes, no markdown, no preamble.")
            appendLine()
            appendLine("Recent commit messages:")
            appendLine(recent.ifBlank { "(none yet)" })
            appendLine()
            appendLine("Changed files:")
            chosen.forEach { appendLine("${it.kind} ${it.path}") }
            appendLine()
            appendLine("Diff:")
            append(diff)
        }
        try {
            val p = ProcessBuilder(claude.path, "-p", "--output-format", "text", "--allowedTools", "Read", "Glob", "Grep").directory(dir)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            p.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(prompt) }
            val out = StringBuilder()
            val reader = Thread { runCatching { out.append(p.inputStream.bufferedReader(Charsets.UTF_8).readText()) } }.apply { isDaemon = true; start() }
            if (!p.waitFor(3, TimeUnit.MINUTES)) { p.destroyForcibly(); return null to tr("Claude took too long", "Claude לקח יותר מדי זמן") }
            reader.join(2000)
            val msg = out.toString().lines().filter { !it.trim().startsWith("```") }.joinToString("\n").trim().trim('"', '“', '”').trim()
            if (p.exitValue() != 0 || msg.isBlank()) null to tr("Claude didn't answer", "Claude לא ענה") else msg to null
        } catch (e: Exception) {
            null to (e.message ?: "Claude")
        }
    }

    /** git's own words, made short: the lines that say what happened, without its hints. */
    fun said(out: String): String {
        val lines = out.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("hint:") }
        val text = lines.joinToString("\n")
        return when {
            "Please tell me who you are" in text || "empty ident" in text ->
                tr("Git doesn't know your name yet. In a terminal: git config --global user.name \"Your Name\", and user.email.",
                    "Git עוד לא יודע את השם שלך. בטרמינל: git config --global user.name \"השם שלך\", וגם user.email.")
            "nothing to commit" in text -> tr("Nothing to commit", "אין מה לשמור")
            "Not possible to fast-forward" in text || "diverging" in text || "divergent" in text ->
                tr("Both your branch and the server have new commits. Merge them in your editor or a terminal.", "גם אצלך וגם בשרת יש קומיטים חדשים. ממזגים אותם בעורך או בטרמינל.")
            "rejected" in text && ("fetch first" in text || "non-fast-forward" in text) ->
                tr("The server has commits you don't have yet. Pull first, then push.", "בשרת יש קומיטים שעוד אין אצלך. קודם מושכים, ואז דוחפים.")
            "Authentication failed" in text || "could not read Username" in text || "Permission denied" in text ->
                tr("The server didn't let you in. Sign in to Git once in a terminal (git push), then try again.", "השרת לא אישר כניסה. מתחברים פעם אחת ל־Git בטרמינל (git push), ואז מנסים שוב.")
            "would be overwritten" in text ->
                tr("Your uncommitted changes would be overwritten. Commit them first.", "השינויים שלא נשמרו היו נדרסים. קודם שומרים אותם בקומיט.")
            "no tracking information" in text -> tr("This branch doesn't follow a branch on the server yet. Push it first.", "הענף הזה עוד לא מחובר לענף בשרת. קודם דוחפים אותו.")
            else -> lines.lastOrNull { it.startsWith("fatal:") || it.startsWith("error:") }?.substringAfter(':')?.trim() ?: lines.takeLast(2).joinToString(" ").take(300)
        }
    }
}
