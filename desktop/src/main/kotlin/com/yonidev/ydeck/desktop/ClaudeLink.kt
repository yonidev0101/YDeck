package com.yonidev.ydeck.desktop

import com.sun.jna.Memory
import com.sun.jna.platform.win32.IPHlpAPI
import com.sun.jna.ptr.IntByReference
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/*
 * YDeck and Claude Code, live. A local plugin ("ydeck", see [ClaudePlugin]) gives Claude Code hooks that post each
 * moment of a session to YDeck: a prompt sent, Claude waiting for the user, Claude done, the session over. YDeck shows
 * it on the project's widget, its strip in Explorer and its panel, and answers a new session with the project's context
 * (its account, links, where it stands, and how to keep .ydeck.json up to date, see [ProjectFiles]).
 *
 * The hooks run in the background (async), so they never slow Claude down, and when YDeck is closed they fail quietly.
 * The server listens on 127.0.0.1 only and needs a key that only the plugin's files hold.
 */

/** WORKING, WAITING for the user, OPEN (the session runs but nothing is going on: it waits for a message), DONE. */
enum class ClaudeMode { WORKING, WAITING, OPEN, DONE }

/** The coding agents YDeck follows: Claude Code through its hooks, Codex through the sessions it writes (see [Codex]). */
enum class Agent(val label: String) { CLAUDE("Claude"), CODEX("Codex") }

/** One Claude Code session: where it runs, what it's doing since when, and a line about it (the prompt, the question). */
data class ClaudeSession(
    val id: String, val cwd: String, val mode: ClaudeMode, val since: Long, val detail: String,
    val transcript: String?,
    /** When the current piece of work began (the prompt), to tell a long task from a quick answer. */
    val workStart: Long,
    val agent: Agent = Agent.CLAUDE,
    /** Remote Control is on: it can be driven from the Claude app. */
    val remote: Boolean = false,
    /** Known only from Claude Code's list of open sessions (no word from the hooks): its state follows that list. */
    val listed: Boolean = false,
    /** The `claude` process it runs in, found from its hooks: while it runs, the session is open. */
    val proc: Proc? = null,
)

/** A process, told apart from a later one under the same pid by when it started (ms). */
data class Proc(val pid: Long, val start: Long)

/** Something worth a Windows notification: Claude needs the user, or finished a long task. */
data class ClaudeAlert(val cwd: String, val mode: ClaudeMode, val detail: String)

object ClaudeLink {
    const val PORT = 47811
    /** A session that ended is shown as done this long. */
    private const val DONE_FOR_MS = 30L * 60 * 1000
    /** Work with no word from Claude for this long has stopped (a closed terminal sends nothing). */
    private const val STALE_MS = 15L * 60 * 1000
    /** Work whose `claude` still runs, with no word for this long, has stopped all the same (a safety net). */
    private const val STALE_RUNNING_MS = 60L * 60 * 1000
    /** A task this long is worth a notification when it ends. */
    private const val LONG_TASK_MS = 60_000L

    /** The key the hooks send; made once, kept in %LOCALAPPDATA%\YDeck. */
    val key: String by lazy {
        val f = File(AppDirs.local, "claude-link.key")
        runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.length >= 32 } ?: run {
            val k = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            f.writeText(k); k
        }
    }

    private val _sessions = MutableStateFlow<Map<String, ClaudeSession>>(emptyMap())
    val sessions: StateFlow<Map<String, ClaudeSession>> = _sessions

    private val _alerts = MutableSharedFlow<ClaudeAlert>(extraBufferCapacity = 8)
    val alerts: SharedFlow<ClaudeAlert> = _alerts

    /** The sessions open on this computer, from Claude Code's own list (null: this Claude Code keeps none). */
    @Volatile private var open: Map<String, OpenSessions.Open>? = null

    /** Ticks every half minute, so "done 3 minutes ago" moves on and fades out on time. */
    val minute = MutableStateFlow(0L)

    /** Called when a session stops in a folder: its .ydeck files may have changed. */
    @Volatile var onStop: (String) -> Unit = {}

    fun start() {
        runCatching {
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 8)
            server.createContext("/claude") { ex ->
                val ok = ex.requestMethod == "POST" && ex.requestHeaders.getFirst("X-YDeck") == key
                val reply = if (!ok) null else runCatching {
                    // The hook's process is still connected now: its `claude` is found through it.
                    handle(JSONObject(ex.requestBody.readNBytes(1 shl 20).toString(Charsets.UTF_8)), System.currentTimeMillis()) {
                        Callers.claudeAt(ex.remoteAddress.port)
                    }
                }.getOrNull()
                val body = (reply ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(if (ok) 200 else 403, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            server.executor = null
            server.start()
        }
        runCatching { restore(System.currentTimeMillis()) }
        Thread({
            var saved = _sessions.value
            while (true) {
                val now = System.currentTimeMillis()
                if ((now / 2000) % 2 == 1L) runCatching { open = OpenSessions.scan() }
                runCatching { tidy(now) }
                if (_sessions.value !== saved) { saved = _sessions.value; runCatching { save(saved) } }
                if ((now / 2000) % 2 == 0L) runCatching { Codex.poll(now) }
                (now / 30_000).let { if (it != minute.value) minute.value = it }
                Thread.sleep(2000)
            }
        }, "ydeck-claude").apply { isDaemon = true; start() }
    }

    /**
     * One hook event. Returns the reply Claude Code reads (the project's context, at a session's start). [caller] finds
     * the `claude` that sent it, asked once per session.
     */
    fun handle(e: JSONObject, now: Long, caller: () -> Proc? = { null }): JSONObject? {
        val id = e.optString("session_id").ifBlank { return null }
        val cwd = e.optString("cwd").ifBlank { return null }
        val transcript = e.optString("transcript_path").ifBlank { null }
        val before = _sessions.value[id]
        val proc = before?.proc ?: if (e.optString("hook_event_name") != "SessionEnd") runCatching(caller).getOrNull() else null
        fun set(mode: ClaudeMode, detail: String, workStart: Long = before?.workStart ?: now) {
            _sessions.value = _sessions.value + (id to ClaudeSession(id, cwd, mode, now, detail, transcript ?: before?.transcript, workStart,
                remote = before?.remote == true, proc = proc))
        }
        when (e.optString("hook_event_name")) {
            "SessionStart" -> {
                if (before == null) set(ClaudeMode.DONE, "", workStart = now)
                Receipts.begin(id, Agent.CLAUDE, cwd, null, now)
                return context(cwd)
            }
            "UserPromptSubmit" -> {
                set(ClaudeMode.WORKING, firstLine(e.optString("prompt")), workStart = now)
                Receipts.begin(id, Agent.CLAUDE, cwd, e.optString("prompt"), now)
            }
            "Notification" -> when (e.optString("notification_type")) {
                // Claude is done and idle: that's already "done".
                "idle_prompt", "auth_success", "elicitation_complete", "elicitation_response", "agent_completed" -> {}
                else -> {
                    set(ClaudeMode.WAITING, firstLine(e.optString("message")))
                    _alerts.tryEmit(ClaudeAlert(cwd, ClaudeMode.WAITING, firstLine(e.optString("message"))))
                }
            }
            "Stop" -> {
                set(ClaudeMode.DONE, firstLine(e.optString("last_assistant_message")))
                if (before != null && (before.mode == ClaudeMode.WORKING || before.mode == ClaudeMode.WAITING) && now - before.workStart >= LONG_TASK_MS) {
                    _alerts.tryEmit(ClaudeAlert(cwd, ClaudeMode.DONE, firstLine(e.optString("last_assistant_message"))))
                }
                Receipts.end(id, now)
                onStop(cwd)
            }
            "SessionEnd" -> {
                _sessions.value = _sessions.value - id
                Receipts.end(id, now)
                // Where the work stands, for the next session: once the receipt is done.
                Thread { runCatching { Receipts.settle(); Handoffs.afterSession(id) } }.apply { isDaemon = true; start() }
            }
        }
        return null
    }

    /**
     * Sessions catch up with what the hooks can't say. Whether a session's `claude` still runs comes from its process
     * (found from its hooks), or else from Claude Code's own list of open sessions ([open], which newer versions no
     * longer keep): a finished session whose `claude` runs is open, one whose `claude` is gone (a closed terminal says
     * nothing) is over at once. Its transcript tells the rest: after the user answers Claude's question the
     * conversation moves on, so it's working again; a turn the user stopped (Esc, which no hook reports) is over; and
     * whether Remote Control is on. Done fades out.
     */
    fun tidy(now: Long, open: Map<String, OpenSessions.Open>? = this.open, alive: (Proc) -> Boolean = Callers::alive) {
        val next = _sessions.value.mapNotNull { (id, s) ->
            if (s.agent != Agent.CLAUDE) return@mapNotNull id to s
            val live = open?.get(id)
            if (s.listed) return@mapNotNull if (live == null) null
                else id to s.copy(mode = if (live.busy) ClaudeMode.WORKING else ClaudeMode.OPEN, cwd = live.cwd, remote = live.remote,
                    since = if (live.busy != (s.mode == ClaudeMode.WORKING)) live.since else s.since)
            // Its claude runs (true), has ended (false), or nobody knows (null). A moment's grace on the list: a
            // session just started may not be on it yet.
            val running = when {
                s.proc != null -> alive(s.proc)
                live != null -> true
                open != null && now - s.since > 10_000 -> false
                else -> null
            }
            val written = s.transcript?.let { File(it).lastModified() }?.takeIf { it > 0 } ?: s.since
            val told = s.transcript?.takeIf { running != false }?.let { runCatching { Transcripts.read(it) }.getOrNull() }
            val remote = live?.remote ?: told?.remote ?: s.remote
            val idle = if (running == true) ClaudeMode.OPEN else ClaudeMode.DONE
            id to when {
                running == false && s.mode == ClaudeMode.OPEN -> return@mapNotNull null
                running == false && (s.mode == ClaudeMode.WORKING || s.mode == ClaudeMode.WAITING) -> s.copy(mode = ClaudeMode.DONE, since = now)
                s.mode == ClaudeMode.WAITING && written > s.since + 1500 -> s.copy(mode = ClaudeMode.WORKING, since = written, remote = remote)
                s.mode == ClaudeMode.WORKING && told?.stopped == true && written > s.workStart && now - written > 2000 ->
                    s.copy(mode = idle, since = written, detail = "", remote = remote)
                s.mode == ClaudeMode.WORKING && now - maxOf(written, s.since) > (if (running == true) STALE_RUNNING_MS else STALE_MS) ->
                    s.copy(mode = idle, since = maxOf(written, s.since), remote = remote)
                s.mode == ClaudeMode.DONE && running == true -> s.copy(mode = ClaudeMode.OPEN, remote = remote)
                s.mode == ClaudeMode.DONE && now - s.since > 12 * 60 * 60 * 1000L -> return@mapNotNull null
                remote != s.remote -> s.copy(remote = remote)
                else -> s
            }
        }.toMap().toMutableMap()
        open?.values?.filter { it.id !in next }?.forEach { o ->
            next[o.id] = ClaudeSession(o.id, o.cwd, if (o.busy) ClaudeMode.WORKING else ClaudeMode.OPEN, o.since, "", null, o.since, remote = o.remote, listed = true)
        }
        Transcripts.keep(next.values.mapNotNull { it.transcript }.toSet())
        if (next != _sessions.value) _sessions.value = next
    }

    private val file get() = File(AppDirs.local, "claude-sessions.json")

    /** Sessions with a known `claude` are kept on disk, so YDeck started again (after an update) still knows them. */
    private fun save(all: Map<String, ClaudeSession>) {
        val a = JSONArray()
        all.values.filter { it.agent == Agent.CLAUDE && it.proc != null }.forEach { s ->
            a.put(JSONObject().put("id", s.id).put("cwd", s.cwd).put("mode", s.mode.name).put("since", s.since).put("detail", s.detail)
                .put("transcript", s.transcript ?: "").put("workStart", s.workStart).put("remote", s.remote)
                .put("pid", s.proc!!.pid).put("start", s.proc.start))
        }
        file.writeText(a.toString())
    }

    /** The sessions saved last time whose `claude` still runs. */
    private fun restore(now: Long) {
        val a = JSONArray(file.readText())
        val found = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val proc = Proc(o.optLong("pid"), o.optLong("start")).takeIf { Callers.alive(it) } ?: return@mapNotNull null
            val mode = runCatching { ClaudeMode.valueOf(o.optString("mode")) }.getOrNull() ?: return@mapNotNull null
            ClaudeSession(o.getString("id"), o.getString("cwd"), mode, o.optLong("since", now), o.optString("detail"),
                o.optString("transcript").ifBlank { null }, o.optLong("workStart", now), remote = o.optBoolean("remote"), proc = proc)
        }
        _sessions.value = found.associateBy { it.id } + _sessions.value
    }

    /** The most pressing session in [folder] (or a folder inside it): waiting, then working, then open, then recently done. */
    fun statusIn(folder: String, all: Map<String, ClaudeSession>, now: Long = System.currentTimeMillis()): ClaudeSession? =
        all.values.filter { isUnder(it.cwd, folder) }
            .filter { it.mode != ClaudeMode.DONE || now - it.since < DONE_FOR_MS }
            .minWithOrNull(compareBy<ClaudeSession> { when (it.mode) { ClaudeMode.WAITING -> 0; ClaudeMode.WORKING -> 1; ClaudeMode.OPEN -> 2; ClaudeMode.DONE -> 3 } }.thenByDescending { it.since })

    /** How many sessions run in [folder] now (working, waiting or open). */
    fun openIn(folder: String, all: Map<String, ClaudeSession>): Int =
        all.values.count { isUnder(it.cwd, folder) && it.mode != ClaudeMode.DONE && it.agent == Agent.CLAUDE }

    /** Codex's sessions, as read from its files: they join Claude's, under their own ids. */
    fun setCodex(found: Map<String, ClaudeSession>) {
        val next = _sessions.value.filterValues { it.agent != Agent.CODEX } + found
        if (next != _sessions.value) _sessions.value = next
    }

    fun firstLine(s: String): String = s.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(140)

    /** What Claude is told when a session starts in a project's folder (nothing for other folders). */
    private fun context(cwd: String): JSONObject? {
        val (id, folder) = Settings.projectAt(File(cwd)) ?: return null
        val p = Repo.deck.value.byId(id) ?: return null
        val text = ProjectFiles.briefing(p, File(folder))
        ProjectFiles.keepLocalOutOfGit(File(folder))
        return JSONObject().put("hookSpecificOutput", JSONObject().put("hookEventName", "SessionStart").put("additionalContext", text))
    }
}

/**
 * Claude Code's own list of the sessions open on this computer: ~/.claude/sessions/<pid>.json, one for each running
 * `claude`, written by Claude Code itself. A record counts only while its process runs: the same pid, started at the
 * same moment (a pid can be reused once a process ends).
 */
object OpenSessions {
    data class Open(val id: String, val cwd: String, val busy: Boolean, val remote: Boolean, val since: Long)

    private val dir = File(System.getProperty("user.home"), ".claude/sessions")

    /**
     * The open sessions by id; null when Claude Code keeps no such list here (then nothing is taken from it). Claude
     * Code 2.1.30x stopped writing it (the folder holds only its .key files), so an empty folder says nothing either.
     */
    fun scan(): Map<String, Open>? {
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" }?.takeIf { it.isNotEmpty() } ?: return null
        return files.mapNotNull { f -> runCatching { read(JSONObject(f.readText())) }.getOrNull() }.associateBy { it.id }
    }

    fun read(o: JSONObject, alive: (Long, Long?) -> Boolean = ::running): Open? {
        if (o.optString("kind", "interactive") != "interactive") return null
        if (!alive(o.getLong("pid"), o.optString("procStart").toLongOrNull())) return null
        val id = o.optString("sessionId").ifBlank { return null }
        val cwd = o.optString("cwd").ifBlank { return null }
        return Open(id, cwd, o.optString("status") == "busy", o.optString("bridgeSessionId").isNotBlank(),
            o.optLong("statusUpdatedAt").takeIf { it > 0 } ?: o.optLong("startedAt"))
    }

    /** The process runs, and is the one that wrote the record: its start (a Windows FILETIME) matches, to 2 seconds. */
    private fun running(pid: Long, procStart: Long?): Boolean {
        val h = ProcessHandle.of(pid).orElse(null)?.takeIf { it.isAlive } ?: return false
        val start = h.info().startInstant().orElse(null) ?: return true
        if (procStart == null) return true
        val mine = start.toEpochMilli() * 10_000 + 116_444_736_000_000_000L
        return kotlin.math.abs(mine - procStart) < 20_000_000
    }
}

/**
 * The `claude` behind a hook: the hook's process (curl) is still connected while YDeck reads it, so Windows' table of
 * TCP connections names it, and its parents lead to `claude`. Known once, the session is open exactly as long as that
 * process runs, whatever Claude Code keeps on disk.
 */
object Callers {
    /** What a hook runs through on its way from `claude` (see [ClaudePlugin.hooks]). */
    private val between = setOf("curl.exe", "cmd.exe", "powershell.exe", "pwsh.exe", "conhost.exe")

    fun claudeAt(port: Int): Proc? {
        var h = ProcessHandle.of(ownerOf(port) ?: return null).orElse(null) ?: return null
        repeat(4) {
            val name = h.info().command().orElse("").substringAfterLast('\\').lowercase()
            // A process whose name can't be read could be anything: better unknown than wrong.
            if (name.isBlank()) return null
            if (name !in between) return proc(h)
            h = h.parent().orElse(null) ?: return null
        }
        return null
    }

    fun alive(p: Proc): Boolean {
        val h = ProcessHandle.of(p.pid).orElse(null)?.takeIf { it.isAlive } ?: return false
        return proc(h)?.start == p.start
    }

    private fun proc(h: ProcessHandle): Proc? = h.info().startInstant().orElse(null)?.let { Proc(h.pid(), it.toEpochMilli()) }

    /** The process at the other end of our connection from [port] (127.0.0.1:[port] to our server). */
    private fun ownerOf(port: Int): Long? {
        val api = IPHlpAPI.INSTANCE
        val size = IntByReference()
        val kind = IPHlpAPI.TCP_TABLE_CLASS.TCP_TABLE_OWNER_PID_CONNECTIONS
        api.GetExtendedTcpTable(null, size, false, IPHlpAPI.AF_INET, kind, 0)
        // Room for a few more connections made meanwhile.
        size.value += 4096
        val mem = Memory(size.value.toLong())
        if (api.GetExtendedTcpTable(mem, size, false, IPHlpAPI.AF_INET, kind, 0) != 0) return null
        // Ports are kept in network order, in the low 16 bits.
        fun port(raw: Int) = ((raw and 0xff) shl 8) or ((raw shr 8) and 0xff)
        return IPHlpAPI.MIB_TCPTABLE_OWNER_PID(mem).table
            .firstOrNull { port(it.dwLocalPort) == port && port(it.dwRemotePort) == ClaudeLink.PORT }?.dwOwningPid?.toLong()
    }
}

/**
 * What a session's transcript says lately, read as it grows (only the new lines each time): whether the user stopped
 * the last turn, and whether Remote Control is on.
 */
object Transcripts {
    class Told(var stopped: Boolean = false, var remote: Boolean? = null)

    private class Seen(var offset: Long, val told: Told = Told())
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Seen>()
    /** A first read starts this far from the end: Remote Control is said near the start, but a huge file isn't read whole. */
    private const val FIRST_READ = 16L shl 20

    fun read(path: String): Told? {
        val f = File(path)
        val length = f.length().takeIf { it > 0 } ?: return null
        val s = seen[path]?.takeIf { it.offset <= length } ?: Seen(maxOf(0, length - FIRST_READ)).also { seen[path] = it }
        if (length > s.offset) java.io.RandomAccessFile(f, "r").use { raf ->
            raf.seek(s.offset)
            val bytes = ByteArray((length - s.offset).toInt()).also { raf.readFully(it) }
            // Whole lines only: a line still being written is read next time.
            val end = bytes.lastIndexOf('\n'.code.toByte())
            if (end >= 0) {
                String(bytes, 0, end, Charsets.UTF_8).lineSequence().forEach { take(s.told, it) }
                s.offset += end + 1
            }
        }
        return Told(s.told.stopped, s.told.remote)
    }

    /** One line of the transcript. Only the main conversation counts (not a subagent's). */
    fun take(t: Told, line: String) {
        if (!line.contains("\"isSidechain\":false")) return
        when {
            line.contains("\"type\":\"assistant\"") -> t.stopped = false
            line.contains("\"type\":\"user\"") -> t.stopped = line.contains("[Request interrupted by user") && stoppedHere(line)
            line.contains("\"subtype\":\"bridge_status\"") -> t.remote = true
            line.contains("Remote Control disconnected") -> t.remote = false
        }
    }

    /** The user's message is the stop itself (not, say, a file that mentions it). */
    private fun stoppedHere(line: String): Boolean = runCatching {
        val o = JSONObject(line)
        if (o.optString("type") != "user") return false
        val text = when (val c = o.optJSONObject("message")?.opt("content")) {
            is String -> c
            is JSONArray -> (0 until c.length()).mapNotNull { c.optJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
            else -> ""
        }
        text.trim().startsWith("[Request interrupted by user")
    }.getOrDefault(false)

    /** Forgets transcripts no session uses now. */
    fun keep(paths: Set<String>) { seen.keys.retainAll(paths) }
}

/** Whether [path] is [folder] or inside it (Windows paths, any case). */
fun isUnder(path: String, folder: String): Boolean {
    val p = path.replace('/', '\\').lowercase().trimEnd('\\')
    val f = folder.replace('/', '\\').lowercase().trimEnd('\\')
    return p == f || p.startsWith("$f\\")
}

/**
 * The "ydeck" plugin for Claude Code, kept in %LOCALAPPDATA%\YDeck\claude-plugin as a local marketplace: its hooks
 * post to [ClaudeLink]. Installed and removed with Claude Code's own commands, so settings.json is only ever written
 * by Claude Code.
 */
object ClaudePlugin {
    const val ID = "ydeck@ydeck"
    val root = File(AppDirs.local, "claude-plugin")
    private val settings = File(System.getProperty("user.home"), ".claude/settings.json")

    /** curl.exe ships with Windows 10 and later; it runs the hook directly, with no shell in between. */
    private val curl: File? get() = File(System.getenv("WINDIR") ?: "C:\\Windows", "System32\\curl.exe").takeIf { it.isFile }
    private val cmd: File get() = File(System.getenv("WINDIR") ?: "C:\\Windows", "System32\\cmd.exe")

    val installed: Boolean
        get() = runCatching { JSONObject(settings.readText()).optJSONObject("enabledPlugins")?.optBoolean(ID) == true }.getOrDefault(false)

    /**
     * Brings Claude Code's copy of the plugin up to date. Claude Code runs the plugin from its own cache, which only
     * changes when the plugin is installed again: a new key or new hooks written here would never reach it (the hooks
     * were turned away, and Claude's work stopped showing). When its copy differs, or the marketplace points at an
     * old folder, the plugin is installed again with Claude Code's own commands. New sessions pick it up.
     */
    fun refresh() {
        write()
        val claude = Tools.claude?.exe ?: return
        // The hooks and the command, as written here and as in Claude Code's copy.
        fun of(dir: File): String? = runCatching {
            JSONObject(File(dir, "hooks/hooks.json").readText()).toString() + File(dir, "commands/$CLOSE_DAY.md").readText()
        }.getOrNull()
        val ours = of(File(root, "plugins/ydeck")) ?: return
        val cache = File(System.getProperty("user.home"), ".claude/plugins/cache/ydeck/ydeck")
        val theirs = cache.listFiles()?.maxByOrNull { it.lastModified() }?.let(::of)
        val path = runCatching {
            JSONObject(settings.readText()).optJSONObject("extraKnownMarketplaces")?.optJSONObject("ydeck")?.optJSONObject("source")?.optString("path")
        }.getOrNull()
        if (theirs == ours && path != null && File(path).canonicalPath == root.canonicalPath) return
        run(claude, "plugin", "marketplace", "remove", "ydeck")
        run(claude, "plugin", "marketplace", "add", root.path)
        run(claude, "plugin", "install", ID)
    }

    /** Writes (or refreshes) the marketplace and the plugin; Claude Code reads it as is at each session's start. */
    fun write() {
        val plugin = File(root, "plugins/ydeck")
        File(root, ".claude-plugin").mkdirs()
        File(plugin, ".claude-plugin").mkdirs()
        File(plugin, "hooks").mkdirs()
        File(plugin, "commands").mkdirs()
        File(root, ".claude-plugin/marketplace.json").writeText(JSONObject()
            .put("name", "ydeck").put("owner", JSONObject().put("name", "YDeck"))
            .put("plugins", JSONArray().put(JSONObject().put("name", "ydeck").put("source", "./plugins/ydeck")
                .put("description", "Claude Code's state on the YDeck widgets, and each project's context from YDeck")))
            .toString(2))
        File(plugin, ".claude-plugin/plugin.json").writeText(JSONObject()
            .put("name", "ydeck").put("version", "1.2.0").put("author", JSONObject().put("name", "YoniDev"))
            .put("description", "Claude Code's state on the YDeck widgets, and each project's context from YDeck")
            .toString(2))
        File(plugin, "hooks/hooks.json").writeText(hooks().toString(2))
        File(plugin, "commands/$CLOSE_DAY.md").writeText(closeDay())
    }

    /** The command's name: /ydeck:close-day in Claude Code (or /close-day, when no other command has that name). */
    const val CLOSE_DAY = "close-day"

    /**
     * /ydeck:close-day: what closing the project for today takes, so nothing in it stays open. The same steps as YDeck's
     * own "Close the day" (commit, push, stop the servers, leave the next step), done by Claude, who knows the session's work.
     */
    fun closeDay(): String = """
        ---
        description: Close this project for today. Commit, push, stop what runs, and leave where it stands and the next step, so nothing stays open.
        argument-hint: "[a note for tomorrow]"
        ---

        Close this project for today, so nothing is left open in it. Go through these steps in order, doing what applies and skipping what doesn't. Don't start new work.

        1. Work in progress: if something from this session is half done, bring it to a safe stopping point (it builds, nothing is half edited). If finishing it would take more than a few minutes, stop and leave it for tomorrow instead.
        2. Check: if code changed in this session and the project has a quick check (tests, type check, build), run it. If it fails and the fix is quick, fix it; otherwise leave it for tomorrow and say so.
        3. Commit: run git status. Commit the changes that belong to this session's work, one commit per logical change, with clear messages. Never commit secrets (.env files, keys, tokens) or build output. .ydeck.json and YDeck's block in AGENTS.md (between the ydeck markers) are the project's memory: commit them with the day's work, without asking, whoever changed them. Other changes you didn't make (the user's own, or another session's): list them and ask whether to commit them; don't decide alone.
        4. Push: if the repository has a remote, push the branch (set its upstream if it has none). Never force-push. If the push fails, say why and leave it.
        5. Stop what runs: dev servers, watchers and background commands started in this session.
        6. Leave where it stands: write .ydeck.local.json in the project root (it is personal and kept out of git, so writing it leaves nothing to commit), keeping what is already in it: "now" is where the project stands, one short line; "next" is the first concrete step for tomorrow, specific enough to start from cold. In the user's language. Update .ydeck.json too if a decision, a release or a link from today belongs there, as this session's instructions from YDeck say, before the commit in step 3, or commit it after. In the end git status shows nothing left to commit.
        7. Report, short, in the user's language:
           - Done today
           - Still open: anything not closed, and why (ideally nothing)
           - Tomorrow starts with: the next step
           Then say the day is closed for this project, and that /exit ends the session.

        A note from the user for tomorrow, if any (put it in "next"): ${'$'}ARGUMENTS
    """.trimIndent() + "\n"

    fun hooks(): JSONObject {
        val url = "http://127.0.0.1:${ClaudeLink.PORT}/claude"
        val key = ClaudeLink.key
        val c = curl
        // Headers without spaces, so no argument needs quotes inside it (cmd and Windows quoting disagree on those).
        val post = "-s -m 2 -H Content-Type:application/json -H X-YDeck:$key --data-binary @- $url"
        val ps = "try { Invoke-RestMethod -Uri '$url' -Method Post -TimeoutSec 2 -ContentType 'application/json' -Headers @{'X-YDeck'='$key'} -Body ([Console]::In.ReadToEnd()) | ConvertTo-Json -Depth 5 -Compress } catch {}"
        fun command(exe: String, args: List<String>) = JSONObject().put("type", "command").put("command", exe).put("args", JSONArray(args))
        // Every moment but the start runs in the background: Claude never waits for it, and a closed YDeck goes unnoticed.
        val background = (if (c != null) command(c.path, post.split(' ')) else command(Ps.exe, listOf("-NoProfile", "-NonInteractive", "-Command", ps))).put("async", true)
        // The start is answered before the first message, so Claude has the project's context from the outset; when YDeck
        // is closed, "|| exit /b 0" makes it a hook with nothing to add rather than an error.
        val start = (if (c != null) command(cmd.path, listOf("/d", "/s", "/c", "${c.path} $post || exit /b 0"))
            else command(Ps.exe, listOf("-NoProfile", "-NonInteractive", "-Command", ps))).put("timeout", 5)
        fun event(h: JSONObject) = JSONArray().put(JSONObject().put("hooks", JSONArray().put(h)))
        return JSONObject().put("description", "YDeck: Claude Code's state on the YDeck widgets, and each project's context from YDeck")
            .put("hooks", JSONObject().apply {
                put("SessionStart", event(start))
                listOf("UserPromptSubmit", "Notification", "Stop", "SessionEnd").forEach { put(it, event(background)) }
            })
    }

    /** Adds the marketplace and installs the plugin (or removes both). Returns Claude Code's last line on failure. */
    fun set(on: Boolean): String? {
        val claude = Tools.claude?.exe ?: return tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב")
        if (on) {
            write()
            run(claude, "plugin", "marketplace", "add", root.path)
            return run(claude, "plugin", "install", ID).takeIf { !installed }
        }
        run(claude, "plugin", "marketplace", "remove", "ydeck")
        return if (installed) tr("Couldn't remove it", "לא הצלחתי להסיר") else null
    }

    private fun run(claude: File, vararg args: String): String? = runCatching {
        val p = ProcessBuilder(listOf(claude.path) + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!p.waitFor(90, TimeUnit.SECONDS)) { p.destroy(); return tr("Claude Code didn't answer", "Claude Code לא ענה") }
        out.lines().lastOrNull { it.isNotBlank() }?.trim()
    }.getOrElse { it.message }
}
