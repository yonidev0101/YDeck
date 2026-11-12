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
    /** Where the work stands: what's left, ideas set aside (not built), what was done lately (newest first). */
    val left: List<String> = emptyList(),
    val ideas: List<String> = emptyList(),
    val doneItems: List<String> = emptyList(),
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
    /** The computer answers the phone's questions ([Ask]): on in its settings, and Claude Code is there. */
    val ask: Boolean = false,
    /** The answers to the phone's latest questions, and the one being worked on. */
    val answers: List<Answer> = emptyList(),
    /** Claude Code sessions waiting for the developer, with YDeck's suggested reply. */
    val waiting: List<Waiting> = emptyList(),
    /** The phone may reply to them ([Reply]): on in the computer's settings. */
    val reply: Boolean = false,
    /** What became of the phone's latest replies: sent to the session, or why not. */
    val replied: List<Launched> = emptyList(),
    /** The phone may ask to run its own commands here ([RunCommand]): on in the computer's settings. Each one still waits
     *  for someone at the computer, unless that exact line was approved always for that project. */
    val run: Boolean = false,
    /** What became of the phone's latest commands. */
    val ran: List<Ran> = emptyList(),
    /** The command lines approved always, by project: the phone offers them for one tap. */
    val runOk: Map<String, List<String>> = emptyMap(),
) {
    /** The same state made at another time: what changed, without the time it was made. */
    fun at(time: Long) = Snapshot(time, computer, deck, memory, live, applied, launch, claude, launched, beat, ask, answers, waiting, reply, replied, run, ran, runOk)

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
        put("ask", ask)
        put("answers", JSONArray().apply { answers.forEach { put(it.toJson()) } })
        put("waiting", JSONArray().apply { waiting.forEach { put(it.toJson()) } })
        put("reply", reply)
        put("replied", JSONArray().apply { replied.forEach { put(JSONObject().put("id", it.id).put("ok", it.ok).put("why", it.why)) } })
        put("run", run)
        put("ran", JSONArray().apply { ran.forEach { put(it.toJson()) } })
        put("runOk", JSONObject().apply { runOk.forEach { (k, v) -> put(k, JSONArray(v)) } })
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
                ask = o.optBoolean("ask"),
                answers = o.optJSONArray("answers")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().map(Answer::of),
                waiting = o.optJSONArray("waiting")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().map(Waiting::of),
                reply = o.optBoolean("reply"),
                replied = o.optJSONArray("replied")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
                    .map { Launched(it.optString("id"), it.optBoolean("ok"), it.optString("why")) },
                run = o.optBoolean("run"),
                ran = o.optJSONArray("ran")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().map(Ran::of),
                runOk = o.optJSONObject("runOk")?.let { r -> r.keys().asSequence().associateWith { k -> r.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty() } }.orEmpty(),
            )
        }

        fun Memory.toJson(): JSONObject = JSONObject().apply {
            put("summary", summary); put("now", now); put("next", next)
            put("done", done); put("doneAt", doneAt); put("agent", agent); put("folder", folder)
            put("decisions", JSONArray().apply { decisions.forEach { put(JSONObject().put("text", it.text).put("date", it.date)) } })
            put("releases", JSONArray().apply { releases.forEach { put(JSONObject().put("version", it.version).put("date", it.date).put("note", it.note)) } })
            put("left", JSONArray(left)); put("ideas", JSONArray(ideas)); put("doneItems", JSONArray(doneItems))
        }

        fun memoryOf(o: JSONObject): Memory {
            fun objs(name: String): List<JSONObject> = o.optJSONArray(name)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
            fun strings(name: String): List<String> = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } } ?: emptyList()
            return Memory(
                summary = o.optString("summary"), now = o.optString("now"), next = o.optString("next"),
                done = o.optString("done"), doneAt = o.optLong("doneAt"), agent = o.optString("agent"), folder = o.optString("folder"),
                decisions = objs("decisions").map { MemDecision(it.optString("text"), it.optString("date")) }.filter { it.text.isNotBlank() },
                releases = objs("releases").map { MemRelease(it.optString("version"), it.optString("date"), it.optString("note")) }.filter { it.version.isNotBlank() },
                left = strings("left"), ideas = strings("ideas"), doneItems = strings("doneItems"),
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

/**
 * "Run this in the project's folder": a command line typed on the phone. Unlike [Launch] it is the developer's own
 * line, so the computer never runs it on the phone's say alone: someone at the computer approves it, once or always
 * (always is that exact line, in that project, nothing wider), unless it was approved always before. It runs either
 * in a new terminal window there, in sight, which stays open ([window]), or inside YDeck without a window, its output
 * shown on the computer's screen. Either way its output reaches the phone as it comes ([RunOut]). One line only.
 * Sealed as "run".
 */
data class RunCommand(val id: String, val project: String, val line: String, val at: Long, val window: Boolean = false) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "run").put("v", 1)
        .put("id", id).put("project", project).put("line", line).put("at", at).put("window", window).toString().toByteArray()

    companion object {
        const val MAX = 500
        /** A command that waited longer than this (the computer was off) is dropped, never run late. */
        const val FRESH_MS = 15 * 60_000L

        /** The line as it will run: one line, no control characters; null when it isn't one (nothing hidden after a newline). */
        fun clean(line: String): String? {
            val t = line.trim()
            if (t.isEmpty() || t.length > MAX || t.any { it == '\n' || it == '\r' || it.isISOControl() }) return null
            return t
        }

        fun decode(bytes: ByteArray): RunCommand? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "run") return null
            val line = clean(o.optString("line")) ?: return null
            // A phone from before the choice expects the window it always had.
            RunCommand(o.optString("id").take(64), o.optString("project").take(100), line, o.optLong("at"), o.optBoolean("window", true))
        }.getOrNull()
    }
}

/**
 * What became of a [RunCommand]: [ASKING] (waiting for someone at the computer), [RUNNING], [DONE] with its exit
 * [code], or [NO] and why: "off", "stale", "project", "folder", "denied", "timeout", "start". [tail]: the last lines it
 * printed, once it's [DONE] (the live output goes apart, see [RunOut]).
 */
data class Ran(val id: String, val project: String, val line: String, val state: String, val code: Int = 0, val why: String = "", val at: Long = 0,
               val tail: String = "") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("project", project).put("line", line).put("state", state)
        .put("code", code).put("why", why).put("at", at).put("tail", tail)

    companion object {
        const val ASKING = "asking"
        const val RUNNING = "running"
        const val DONE = "done"
        const val NO = "no"

        /** How many of the last lines [tail] keeps, and at most how many characters. */
        const val TAIL_LINES = 30
        const val TAIL_MAX = 4_000

        fun of(o: JSONObject) = Ran(o.optString("id"), o.optString("project"), o.optString("line"), o.optString("state"),
            o.optInt("code"), o.optString("why"), o.optLong("at"), o.optString("tail"))

        /** The end of [text] as [tail] keeps it. */
        fun tailOf(text: String): String = text.trimEnd().lines().takeLast(TAIL_LINES).joinToString("\n").takeLast(TAIL_MAX)
    }
}

/*
 * A command's output, live: while a phone watches, the computer and the phone hold a line open through the relay
 * (a WebSocket, /v1/box/<id>/live), and each message on it is sealed like everything else. The relay passes a
 * message from one side to the other and keeps nothing.
 */

/**
 * Computer → phone, sealed as "out": [text] is the command's output from character [from] on (counted from its
 * start). [done] once it ended, with its exit [code]. A phone that has up to [from] already adds it; one that missed
 * some asks again ([RunWatch]).
 */
data class RunOut(val id: String, val from: Long, val text: String, val done: Boolean = false, val code: Int = 0) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "out").put("id", id).put("from", from)
        .put("text", text).put("done", done).put("code", code).toString().toByteArray()

    companion object {
        /** At most this many characters in one message; more goes in the next ones. */
        const val MAX = 32_000

        fun decode(bytes: ByteArray): RunOut? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "out") return null
            RunOut(o.optString("id").take(64), o.optLong("from"), o.optString("text").take(MAX), o.optBoolean("done"), o.optInt("code"))
        }.getOrNull()
    }
}

/** Phone → computer, sealed as "watch": "I'm looking at command [id] and have its output up to [have]; send the rest." */
data class RunWatch(val id: String, val have: Long) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "watch").put("id", id).put("have", have).toString().toByteArray()

    companion object {
        fun decode(bytes: ByteArray): RunWatch? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "watch") return null
            RunWatch(o.optString("id").take(64), o.optLong("have").coerceAtLeast(0))
        }.getOrNull()
    }
}

// ── a Claude Code session waits for the developer: its question, YDeck's suggested reply, and the reply ──

/** One question of a choice Claude asks (its AskUserQuestion tool): the question, its short header, the options. */
data class Choice(val question: String, val header: String, val options: List<String>, val multi: Boolean) {
    fun toJson(): JSONObject = JSONObject().put("question", question).put("header", header).put("options", JSONArray(options)).put("multi", multi)

    companion object {
        fun of(o: JSONObject) = Choice(o.optString("question"), o.optString("header"),
            o.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(), o.optBoolean("multi"))
    }
}

/**
 * A Claude Code session waiting for the developer, held by YDeck so it can be answered away from the terminal: a
 * choice ([CHOICE], its [questions]) or a question in words at the end of its turn ([TEXT], Claude's own [text]).
 * YDeck drafts a reply from what the project has recorded: [draft] (or [draftChoices]), where it comes from ([source]),
 * and whether a record settles it ([sure]). Never a permission: those stay in the terminal.
 */
data class Waiting(
    val id: String,
    val project: String,
    val kind: String,
    val text: String = "",
    val questions: List<Choice> = emptyList(),
    val draft: String = "",
    val draftChoices: Map<String, String> = emptyMap(),
    val source: String = "",
    val sure: Boolean = false,
    /** The draft is still being written. */
    val drafting: Boolean = false,
    val at: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("project", project).put("kind", kind).put("text", text)
        .put("questions", JSONArray().apply { questions.forEach { put(it.toJson()) } })
        .put("draft", draft).put("draftChoices", JSONObject(draftChoices)).put("source", source).put("sure", sure)
        .put("drafting", drafting).put("at", at)

    companion object {
        const val CHOICE = "choice"
        const val TEXT = "text"

        fun of(o: JSONObject) = Waiting(
            o.optString("id"), o.optString("project"), o.optString("kind"), o.optString("text"),
            o.optJSONArray("questions")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().map(Choice::of),
            o.optString("draft"), o.optJSONObject("draftChoices")?.let { d -> d.keys().asSequence().associateWith { d.optString(it) } }.orEmpty(),
            o.optString("source"), o.optBoolean("sure"), o.optBoolean("drafting"), o.optLong("at"),
        )
    }
}

/**
 * The developer's reply to a [Waiting], from the phone: words ([text]) or, for a choice, the option picked for each
 * question ([choices]). Text for Claude to read, never a command, and never a permission. Sealed as "reply".
 */
data class Reply(val id: String, val waiting: String, val text: String = "", val choices: Map<String, String> = emptyMap(), val at: Long) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "reply").put("v", 1)
        .put("id", id).put("waiting", waiting).put("text", text).put("choices", JSONObject(choices)).put("at", at).toString().toByteArray()

    companion object {
        const val MAX = 4000
        /** A reply that waited longer than this (the computer was off) is dropped: the session has moved on. */
        const val FRESH_MS = 15 * 60_000L

        fun decode(bytes: ByteArray): Reply? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "reply") return null
            val text = o.optString("text").filter { it == '\n' || !it.isISOControl() }.trim().take(MAX)
            val choices = o.optJSONObject("choices")?.let { c -> c.keys().asSequence().take(8).associate { it.take(500) to c.optString(it).take(500) } }.orEmpty()
            if (text.isEmpty() && choices.isEmpty()) return null
            Reply(o.optString("id").take(64), o.optString("waiting").take(64), text, choices, o.optLong("at"))
        }.getOrNull()
    }
}

// ── what the phone asks: a question for Ask YDeck on the computer ──

/**
 * A question typed on the phone, answered on the computer by Ask YDeck: the developer's Claude Code, reading only.
 * [chat] names the phone's conversation, so the next question goes on in the same one; [project] is the page it was
 * asked from ("this project"). Text, never a command. Sealed as "ask", so it can't pass for a note or a launch.
 */
data class Ask(val id: String, val chat: String, val question: String, val project: String = "", val at: Long) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "ask").put("v", 1)
        .put("id", id).put("chat", chat).put("question", question).put("project", project).put("at", at).toString().toByteArray()

    companion object {
        /** The longest question the computer accepts. */
        const val MAX = 2000
        /** A question that waited longer than this (the computer was off) is told it came too late, not answered. */
        const val FRESH_MS = 15 * 60_000L

        fun decode(bytes: ByteArray): Ask? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "ask") return null
            val q = o.optString("question").filter { it == '\n' || !it.isISOControl() }.trim().take(MAX)
            if (q.isEmpty()) return null
            Ask(o.optString("id").take(64), o.optString("chat").take(64), q, o.optString("project").take(100), o.optLong("at"))
        }.getOrNull()
    }
}

/**
 * The computer's answer to an [Ask]. While Claude works, [done] is false and [step] says what it reads. Once done:
 * [answer] (Markdown), or [error]: "off" (asking from the phone is off at the computer), "claude" (no Claude Code
 * there), "stale" (the question waited too long), or why, in words.
 */
data class Answer(
    val id: String,
    val chat: String,
    val done: Boolean = false,
    val answer: String = "",
    val error: String = "",
    val step: String = "",
    val reads: Int = 0,
    val seconds: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("chat", chat).put("done", done).put("answer", answer)
        .put("error", error).put("step", step).put("reads", reads).put("seconds", seconds)

    companion object {
        /** The longest answer sent. */
        const val MAX = 12_000

        fun of(o: JSONObject) = Answer(o.optString("id"), o.optString("chat"), o.optBoolean("done"), o.optString("answer"),
            o.optString("error"), o.optString("step"), o.optInt("reads"), o.optInt("seconds"))
    }
}
