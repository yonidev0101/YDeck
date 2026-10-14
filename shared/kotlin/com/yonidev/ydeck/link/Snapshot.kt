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
    /** WORKING, WAITING, DONE, or "" when no agent is in the folder; since when, and a line about it. */
    val agentMode: String = "",
    val agentSince: Long = 0,
    val agentDetail: String = "",
    val agent: String = "",
    val receipts: List<LiveReceipt> = emptyList(),
    val loops: List<LiveLoop> = emptyList(),
    val ports: List<Int> = emptyList(),
    val missingEnv: List<String> = emptyList(),
    val version: String = "",
    val hasGit: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("branch", branch); put("changes", changes); put("ahead", ahead); put("behind", behind); put("upstream", upstream)
        put("web", web); put("lastAt", lastAt); put("lastSubject", lastSubject); put("hasGit", hasGit)
        put("files", JSONArray().apply { files.forEach { put(it.json()) } })
        put("commits", JSONArray().apply { commits.forEach { put(JSONObject().put("hash", it.hash).put("at", it.at).put("author", it.author).put("subject", it.subject)) } })
        put("agentMode", agentMode); put("agentSince", agentSince); put("agentDetail", agentDetail); put("agent", agent)
        put("receipts", JSONArray().apply {
            receipts.forEach { r ->
                put(JSONObject().put("title", r.title).put("agent", r.agent).put("startAt", r.startAt).put("endAt", r.endAt).put("rewound", r.rewound)
                    .put("files", JSONArray().apply { r.files.forEach { put(it.json()) } }))
            }
        })
        put("loops", JSONArray().apply { loops.forEach { put(JSONObject().put("kind", it.kind).put("title", it.title).put("detail", it.detail)) } })
        put("ports", JSONArray(ports)); put("missingEnv", JSONArray(missingEnv)); put("version", version)
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
            receipts = o.objs("receipts").map { LiveReceipt(it.optString("title"), it.optString("agent"), it.optLong("startAt"), it.optLong("endAt"), it.fileList("files"), it.optBoolean("rewound")) },
            loops = o.objs("loops").map { LiveLoop(it.optString("kind"), it.optString("title"), it.optString("detail")) },
            ports = o.optJSONArray("ports")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
            missingEnv = o.optJSONArray("missingEnv")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            version = o.optString("version"),
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
