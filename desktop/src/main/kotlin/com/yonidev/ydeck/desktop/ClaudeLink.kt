package com.yonidev.ydeck.desktop

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

enum class ClaudeMode { WORKING, WAITING, DONE }

/** The coding agents YDeck follows: Claude Code through its hooks, Codex through the sessions it writes (see [Codex]). */
enum class Agent(val label: String) { CLAUDE("Claude"), CODEX("Codex") }

/** One Claude Code session: where it runs, what it's doing since when, and a line about it (the prompt, the question). */
data class ClaudeSession(
    val id: String, val cwd: String, val mode: ClaudeMode, val since: Long, val detail: String,
    val transcript: String?,
    /** When the current piece of work began (the prompt), to tell a long task from a quick answer. */
    val workStart: Long,
    val agent: Agent = Agent.CLAUDE,
)

/** Something worth a Windows notification: Claude needs the user, or finished a long task. */
data class ClaudeAlert(val cwd: String, val mode: ClaudeMode, val detail: String)

object ClaudeLink {
    const val PORT = 47811
    /** A session that ended is shown as done this long. */
    private const val DONE_FOR_MS = 30L * 60 * 1000
    /** Work with no word from Claude for this long has stopped (a closed terminal sends nothing). */
    private const val STALE_MS = 15L * 60 * 1000
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
                    handle(JSONObject(ex.requestBody.readNBytes(1 shl 20).toString(Charsets.UTF_8)), System.currentTimeMillis())
                }.getOrNull()
                val body = (reply ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(if (ok) 200 else 403, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            server.executor = null
            server.start()
        }
        Thread({
            while (true) {
                val now = System.currentTimeMillis()
                runCatching { tidy(now) }
                if ((now / 2000) % 2 == 0L) runCatching { Codex.poll(now) }
                (now / 30_000).let { if (it != minute.value) minute.value = it }
                Thread.sleep(2000)
            }
        }, "ydeck-claude").apply { isDaemon = true; start() }
    }

    /** One hook event. Returns the reply Claude Code reads (the project's context, at a session's start). */
    fun handle(e: JSONObject, now: Long): JSONObject? {
        val id = e.optString("session_id").ifBlank { return null }
        val cwd = e.optString("cwd").ifBlank { return null }
        val transcript = e.optString("transcript_path").ifBlank { null }
        val before = _sessions.value[id]
        fun set(mode: ClaudeMode, detail: String, workStart: Long = before?.workStart ?: now) {
            _sessions.value = _sessions.value + (id to ClaudeSession(id, cwd, mode, now, detail, transcript ?: before?.transcript, workStart))
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
                if (before != null && before.mode != ClaudeMode.DONE && now - before.workStart >= LONG_TASK_MS) {
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
     * Sessions catch up with what the hooks can't say: after the user answers Claude's question the conversation
     * moves on (its transcript grows), so it's working again; work with no sign of life is over; done fades out.
     */
    fun tidy(now: Long) {
        val next = _sessions.value.mapNotNull { (id, s) ->
            if (s.agent != Agent.CLAUDE) return@mapNotNull id to s
            val written = s.transcript?.let { File(it).lastModified() }?.takeIf { it > 0 } ?: s.since
            id to when {
                s.mode == ClaudeMode.WAITING && written > s.since + 1500 -> s.copy(mode = ClaudeMode.WORKING, since = written)
                s.mode == ClaudeMode.WORKING && now - maxOf(written, s.since) > STALE_MS -> s.copy(mode = ClaudeMode.DONE, since = maxOf(written, s.since))
                s.mode == ClaudeMode.DONE && now - s.since > 12 * 60 * 60 * 1000L -> return@mapNotNull null
                else -> s
            }
        }.toMap()
        if (next != _sessions.value) _sessions.value = next
    }

    /** The most pressing session in [folder] (or a folder inside it): waiting, then working, then recently done. */
    fun statusIn(folder: String, all: Map<String, ClaudeSession>, now: Long = System.currentTimeMillis()): ClaudeSession? =
        all.values.filter { isUnder(it.cwd, folder) }
            .filter { it.mode != ClaudeMode.DONE || now - it.since < DONE_FOR_MS }
            .minWithOrNull(compareBy<ClaudeSession> { when (it.mode) { ClaudeMode.WAITING -> 0; ClaudeMode.WORKING -> 1; ClaudeMode.DONE -> 2 } }.thenByDescending { it.since })

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
        val ours = runCatching { JSONObject(File(root, "plugins/ydeck/hooks/hooks.json").readText()).toString() }.getOrNull() ?: return
        val cache = File(System.getProperty("user.home"), ".claude/plugins/cache/ydeck/ydeck")
        val theirs = cache.listFiles()?.maxByOrNull { it.lastModified() }?.let { File(it, "hooks/hooks.json") }
            ?.let { runCatching { JSONObject(it.readText()).toString() }.getOrNull() }
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
        File(root, ".claude-plugin/marketplace.json").writeText(JSONObject()
            .put("name", "ydeck").put("owner", JSONObject().put("name", "YDeck"))
            .put("plugins", JSONArray().put(JSONObject().put("name", "ydeck").put("source", "./plugins/ydeck")
                .put("description", "Claude Code's state on the YDeck widgets, and each project's context from YDeck")))
            .toString(2))
        File(plugin, ".claude-plugin/plugin.json").writeText(JSONObject()
            .put("name", "ydeck").put("version", "1.1.0").put("author", JSONObject().put("name", "YoniDev"))
            .put("description", "Claude Code's state on the YDeck widgets, and each project's context from YDeck")
            .toString(2))
        File(plugin, "hooks/hooks.json").writeText(hooks().toString(2))
    }

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
