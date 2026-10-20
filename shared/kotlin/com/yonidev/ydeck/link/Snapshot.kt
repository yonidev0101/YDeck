package com.yonidev.ydeck.link

import org.json.JSONArray
import org.json.JSONObject

/*
 * What the computer puts in the box: the projects (the same projects.json both apps already read, logos embedded),
 * and each project's memory from its folder (.ydeck.json and .ydeck.local.json). Data only.
 */

data class MemDecision(val text: String, val date: String)
data class MemRelease(val version: String, val date: String, val note: String)

/** One project's memory, as the phone shows it. Times in ms, 0 when unknown. */
data class Memory(
    val summary: String = "",
    val now: String = "",
    val next: String = "",
    val done: String = "",
    val doneAt: Long = 0,
    val agent: String = "",
    val decisions: List<MemDecision> = emptyList(),
    val releases: List<MemRelease> = emptyList(),
    /** The folder's name on the computer, to recognise it. */
    val folder: String = "",
) {
    /** Something still waits: a next step was left. */
    val open: Boolean get() = next.isNotBlank()
}

class Snapshot(
    val at: Long,
    val computer: String,
    /** projects.json with "logos" (path → base64 PNG), exactly as the backup file. */
    val deck: JSONObject,
    val memory: Map<String, Memory>,
    /** Git, the agent, receipts and open loops, for projects with a folder. */
    val live: Map<String, Live> = emptyMap(),
    /** The ids of the phone's notes the computer has stored (the latest ones), so the phone stops showing them as waiting. */
    val applied: List<String> = emptyList(),
    /** The computer opens Claude Code sessions the phone asks for (see [Launch]); false when that's off in its
     *  settings (the default) or Claude Code isn't installed. */
    val launch: Boolean = false,
    /** Claude Code is on the computer, and its YDeck can open it for the phone once that's turned on: the phone shows
     *  the way, and where to turn it on. */
    val claude: Boolean = false,
    /** What became of the phone's latest launch requests. */
    val launched: List<Launched> = emptyList(),
    /** The computer sends its state at least this often (ms), changed or not; 0 from a YDeck that doesn't. */
    val beat: Long = 0,
) {
    fun encode(): ByteArray = JSONObject().apply {
        put("app", "YDeck")
        put("kind", "state")
        put("v", 1)
        put("at", at)
        put("computer", computer)
        put("deck", deck)
        put("memory", JSONObject().apply { memory.forEach { (id, m) -> put(id, m.toJson()) } })
        put("live", JSONObject().apply { live.forEach { (id, l) -> put(id, l.toJson()) } })
        put("applied", JSONArray(applied))
        put("launch", launch)
        put("claude", claude)
        put("launched", JSONArray().apply { launched.forEach { put(JSONObject().put("id", it.id).put("ok", it.ok).put("why", it.why)) } })
        put("beat", beat)
    }.toString().toByteArray()

    companion object {
        fun decode(bytes: ByteArray): Snapshot {
            val o = JSONObject(String(bytes))
            require(o.optString("app") == "YDeck" && o.optString("kind") == "state") { "not a YDeck state" }
            val mem = o.optJSONObject("memory") ?: JSONObject()
            return Snapshot(
                at = o.optLong("at"),
                computer = o.optString("computer"),
                deck = o.optJSONObject("deck") ?: JSONObject(),
                memory = mem.keys().asSequence().associateWith { memoryOf(mem.optJSONObject(it) ?: JSONObject()) },
                live = (o.optJSONObject("live") ?: JSONObject()).let { l -> l.keys().asSequence().associateWith { Live.of(l.optJSONObject(it) ?: JSONObject()) } },
                applied = o.optJSONArray("applied")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                launch = o.optBoolean("launch"),
                claude = o.optBoolean("claude"),
                launched = o.optJSONArray("launched")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
                    .map { Launched(it.optString("id"), it.optBoolean("ok"), it.optString("why")) },
                beat = o.optLong("beat"),
            )
        }

        fun Memory.toJson(): JSONObject = JSONObject().apply {
            put("summary", summary); put("now", now); put("next", next)
            put("done", done); put("doneAt", doneAt); put("agent", agent); put("folder", folder)
            put("decisions", JSONArray().apply { decisions.forEach { put(JSONObject().put("text", it.text).put("date", it.date)) } })
            put("releases", JSONArray().apply { releases.forEach { put(JSONObject().put("version", it.version).put("date", it.date).put("note", it.note)) } })
        }

        fun memoryOf(o: JSONObject): Memory {
            fun objs(name: String): List<JSONObject> = o.optJSONArray(name)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
            return Memory(
                summary = o.optString("summary"), now = o.optString("now"), next = o.optString("next"),
                done = o.optString("done"), doneAt = o.optLong("doneAt"), agent = o.optString("agent"), folder = o.optString("folder"),
                decisions = objs("decisions").map { MemDecision(it.optString("text"), it.optString("date")) }.filter { it.text.isNotBlank() },
                releases = objs("releases").map { MemRelease(it.optString("version"), it.optString("date"), it.optString("note")) }.filter { it.version.isNotBlank() },
            )
        }
    }
}

// ── what's live on the computer (git, the agent, receipts, open loops), per project ──

data class LiveFile(val code: String, val path: String)
data class LiveCommit(val hash: String, val at: Long, val author: String, val subject: String)
data class LiveReceipt(val title: String, val agent: String, val startAt: Long, val endAt: Long, val files: List<LiveFile>, val rewound: Boolean)
data class LiveLoop(val kind: String, val title: String, val detail: String)
/** A Claude Code conversation in the folder, to pick up from the phone: its id, a line about it, when it last moved. */
data class LiveSession(val id: String, val title: String, val at: Long)

data class Live(
    val branch: String = "",
    val changes: Int = 0,
    val ahead: Int = 0,
    val behind: Int = 0,
    val upstream: String = "",
    /** The repository's page (https://github.com/me/app). */
    val web: String = "",
    val lastAt: Long = 0,
    val lastSubject: String = "",
    val files: List<LiveFile> = emptyList(),
    val commits: List<LiveCommit> = emptyList(),
    /** WORKING, WAITING, OPEN (running, not busy), DONE, or "" when no agent is in the folder; since when, and a line about it. */
    val agentMode: String = "",
    val agentSince: Long = 0,
    val agentDetail: String = "",
    val agent: String = "",
    /** The session shown has Remote Control on: it can be driven from the Claude app. */
    val agentRemote: Boolean = false,
    /** How many Claude Code sessions run in the folder now (working, waiting or open). */
    val agentOpen: Int = 0,
    val receipts: List<LiveReceipt> = emptyList(),
    val loops: List<LiveLoop> = emptyList(),
    val ports: List<Int> = emptyList(),
    val missingEnv: List<String> = emptyList(),
    val version: String = "",
    val hasGit: Boolean = false,
    /** The folder's latest Claude Code conversations, newest first. */
    val sessions: List<LiveSession> = emptyList(),
) {
    /** Without the agent: what a phone shows once the computer has gone quiet, since Claude there may have moved on. */
    fun withoutAgent(): Live = copy(agentMode = "", agentSince = 0, agentDetail = "", agent = "", agentRemote = false, agentOpen = 0)

    fun toJson(): JSONObject = JSONObject().apply {
        put("branch", branch); put("changes", changes); put("ahead", ahead); put("behind", behind); put("upstream", upstream)
        put("web", web); put("lastAt", lastAt); put("lastSubject", lastSubject); put("hasGit", hasGit)
        put("files", JSONArray().apply { files.forEach { put(it.json()) } })
        put("commits", JSONArray().apply { commits.forEach { put(JSONObject().put("hash", it.hash).put("at", it.at).put("author", it.author).put("subject", it.subject)) } })
        put("agentMode", agentMode); put("agentSince", agentSince); put("agentDetail", agentDetail); put("agent", agent)
        put("agentRemote", agentRemote); put("agentOpen", agentOpen)
        put("receipts", JSONArray().apply {
            receipts.forEach { r ->
                put(JSONObject().put("title", r.title).put("agent", r.agent).put("startAt", r.startAt).put("endAt", r.endAt).put("rewound", r.rewound)
                    .put("files", JSONArray().apply { r.files.forEach { put(it.json()) } }))
            }
        })
        put("loops", JSONArray().apply { loops.forEach { put(JSONObject().put("kind", it.kind).put("title", it.title).put("detail", it.detail)) } })
        put("ports", JSONArray(ports)); put("missingEnv", JSONArray(missingEnv)); put("version", version)
        put("sessions", JSONArray().apply { sessions.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("at", it.at)) } })
    }

    companion object {
        private fun LiveFile.json() = JSONObject().put("code", code).put("path", path)
        private fun JSONObject.objs(name: String): List<JSONObject> = optJSONArray(name)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
        private fun JSONObject.fileList(name: String) = objs(name).map { LiveFile(it.optString("code"), it.optString("path")) }

        fun of(o: JSONObject): Live = Live(
            branch = o.optString("branch"), changes = o.optInt("changes"), ahead = o.optInt("ahead"), behind = o.optInt("behind"),
            upstream = o.optString("upstream"), web = o.optString("web"), lastAt = o.optLong("lastAt"), lastSubject = o.optString("lastSubject"),
            hasGit = o.optBoolean("hasGit"), files = o.fileList("files"),
            commits = o.objs("commits").map { LiveCommit(it.optString("hash"), it.optLong("at"), it.optString("author"), it.optString("subject")) },
            agentMode = o.optString("agentMode"), agentSince = o.optLong("agentSince"), agentDetail = o.optString("agentDetail"), agent = o.optString("agent"),
            agentRemote = o.optBoolean("agentRemote"), agentOpen = o.optInt("agentOpen"),
            receipts = o.objs("receipts").map { LiveReceipt(it.optString("title"), it.optString("agent"), it.optLong("startAt"), it.optLong("endAt"), it.fileList("files"), it.optBoolean("rewound")) },
            loops = o.objs("loops").map { LiveLoop(it.optString("kind"), it.optString("title"), it.optString("detail")) },
            ports = o.optJSONArray("ports")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
            missingEnv = o.optJSONArray("missingEnv")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            version = o.optString("version"),
            sessions = o.objs("sessions").map { LiveSession(it.optString("id"), it.optString("title"), it.optLong("at")) }.filter { Launch.isSessionId(it.id) },
        )
    }
}

// ── what the phone writes: a note into one project's memory ──

/** A decision or a next step written on the phone, or "done" for the next step it names. Data only: the computer
 *  stores it in the project's files. */
data class Note(val id: String, val project: String, val kind: String, val text: String, val at: Long) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "note").put("v", 1)
        .put("id", id).put("project", project).put("note", kind).put("text", text).put("at", at).toString().toByteArray()

    companion object {
        const val NEXT = "next"
        const val DECISION = "decision"
        /** The next step [text] is done: the project's next step is cleared. */
        const val DONE = "done"
        /** The longest note the computer accepts. */
        const val MAX = 1000

        fun decode(bytes: ByteArray): Note? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "note") return null
            val kind = o.optString("note").takeIf { it == NEXT || it == DECISION || it == DONE } ?: return null
            // Plain text only: no control characters, one line for a next step, never longer than MAX.
            val text = o.optString("text").filter { it == '\n' || !it.isISOControl() }.trim()
                .let { if (kind != DECISION) it.replace(Regex("\\s*\\n\\s*"), " ") else it }.take(MAX)
            if (text.isEmpty() && kind != DONE) return null
            Note(o.optString("id").take(64), o.optString("project").take(100), kind, text, o.optLong("at"))
        }.getOrNull()
    }
}

// ── what the phone asks for: a Claude Code session on the computer ──

/**
 * "Open Claude in this project": a request from a closed list, never a command. The computer opens Claude Code in
 * the project's own folder, in a new terminal, and nothing else: a new conversation ([NEW]), the folder's last one
 * ([CONTINUE]), or one of its conversations by id ([RESUME]); optionally with Remote Control on, to drive it from the
 * Claude app, and with a first message typed on the phone. Sealed as "launch", so a note can't pass for one.
 */
data class Launch(
    val id: String,
    val project: String,
    val mode: String,
    /** The conversation to pick up ([RESUME] only): a session id, nothing else. */
    val session: String = "",
    val prompt: String = "",
    val remote: Boolean = false,
    val at: Long,
) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "launch").put("v", 1)
        .put("id", id).put("project", project).put("mode", mode).put("session", session)
        .put("prompt", prompt).put("remote", remote).put("at", at).toString().toByteArray()

    companion object {
        const val NEW = "new"
        const val CONTINUE = "continue"
        const val RESUME = "resume"
        /** The longest first message the computer accepts. */
        const val MAX = 1000
        /** A request that waited longer than this (the computer was off) is dropped, never run late. */
        const val FRESH_MS = 15 * 60_000L

        private val SESSION = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        fun isSessionId(s: String) = SESSION.matches(s)

        fun decode(bytes: ByteArray): Launch? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "launch") return null
            val mode = o.optString("mode").takeIf { it == NEW || it == CONTINUE || it == RESUME } ?: return null
            val session = o.optString("session")
            if (mode == RESUME && !isSessionId(session)) return null
            // One line of plain text: it reaches Claude Code on its command line.
            val prompt = o.optString("prompt").map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim().take(MAX)
            Launch(o.optString("id").take(64), o.optString("project").take(100), mode, if (mode == RESUME) session else "", prompt, o.optBoolean("remote"), o.optLong("at"))
        }.getOrNull()
    }
}

/** What the computer did with a [Launch]: opened it, or why not (in words the phone shows as they are). */
data class Launched(val id: String, val ok: Boolean, val why: String = "")
