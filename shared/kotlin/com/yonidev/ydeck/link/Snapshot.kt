package com.yonidev.ydeck.link

import org.json.JSONArray
import org.json.JSONObject

/*
 * What the computer puts in the box: the projects (the same projects.json both apps already read, logos embedded),
 * and each project's memory from its folder (.ydeck.json and .ydeck.local.json). Data only.
 */

data class MemDecision(val text: String, val date: String)
data class MemRelease(val version: String, val date: String, val note: String)
/** A step toward the project's goal. */
data class MemStep(val text: String, val done: Boolean)
/** One line of the conversation with Claude in the project: [kind] "user", "claude" or "step" (one thing Claude did). */
data class MemTalk(val kind: String, val text: String) {
    companion object { const val USER = "user"; const val CLAUDE = "claude"; const val STEP = "step" }
}

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
    /** What moved, day by day (newest first, the last week): see [JournalDay]. */
    val journal: List<JournalDay> = emptyList(),
    /** Everything the project can do that a user sees. */
    val built: List<String> = emptyList(),
    /** The near goal and its steps (empty title: no goal). */
    val goal: String = "",
    val goalSteps: List<MemStep> = emptyList(),
    /** The save card: what isn't saved, in words, and what to do (empty: not known yet). */
    val saving: String = "",
    val savingProposal: String = "",
    /** What the save card's button does, in words ("commit · push"); empty: nothing to do. */
    val savingSteps: String = "",
    /** The card is being written, or its steps run, now. */
    val savingBusy: Boolean = false,
    /** What became of the last "do it": "ok", or why not, in words; empty: not done lately. */
    val savingResult: String = "",
    /** A goal Claude suggests, until it's taken or set aside (empty: none). */
    val suggestedGoal: String = "",
    val suggestedSteps: List<String> = emptyList(),
    /** Claude is reading the project to fill it in (what was built, a goal); or why the last read failed. */
    val dossierBusy: Boolean = false,
    val dossierError: String = "",
    /** The conversation with Claude in this project, the last lines; empty: none lately. */
    val talk: List<MemTalk> = emptyList(),
    val talkTitle: String = "",
    /** Claude works on it now. */
    val talkBusy: Boolean = false,
    /** Claude waits for a permission, answered at the computer: what it asks, in words (empty: none). */
    val talkAsk: String = "",
    val talkError: String = "",
    /** The conversation is open in a terminal on the computer: shown, written to there. */
    val talkElsewhere: Boolean = false,
    /** The permission Claude waits for: its id (answered with [Note.TALK_ALLOW]), and what "always" would allow (empty: no "always"). */
    val talkAskId: String = "",
    val talkAskAlways: String = "",
    /** Claude Code's id for the conversation (to open it elsewhere), and its Remote Control link while it's open with one. */
    val talkSession: String = "",
    val talkUrl: String = "",
    /** YDeck's one line on the project: where it really stands and what holds it, thought through (empty: none yet). */
    val insight: String = "",
    /** The project's commands kept on the computer (each one that ran there well is kept by itself), one tap to run again. */
    val commands: List<String> = emptyList(),
    /** The name of each of [commands], in the same order ("" for one kept without a name). */
    val commandNames: List<String> = emptyList(),
    /** Claude looks for the project's commands now ("Find commands"); what the last search came to, in words. */
    val commandsBusy: Boolean = false,
    val commandsFound: String = "",
) {
    /** Steps done and all of them; null when there's no goal. */
    val goalShare: Pair<Int, Int>? get() = if (goal.isBlank() || goalSteps.isEmpty()) null else goalSteps.count { it.done } to goalSteps.size

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
    /** The latest good morning ([Morning]): YDeck's word to start the day, written here by Claude Code. */
    val morning: Morning? = null,
    /** When the phone shows the good morning, in minutes from midnight; -1 when it's off. */
    val morningAt: Int = -1,
    /** Claude is writing a good morning now; or why the last one failed (empty when it didn't). */
    val morningBusy: Boolean = false,
    val morningError: String = "",
    /** The projects the good morning goes over now (picked, or worked on lately), by id. */
    val morningPicks: List<String> = emptyList(),
) {
    /** The same state made at another time: what changed, without the time it was made. */
    fun at(time: Long) = Snapshot(time, computer, deck, memory, live, applied, launch, claude, launched, beat, ask, answers, waiting, reply, replied, run, ran, runOk, morning, morningAt, morningBusy, morningError, morningPicks)

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
        morning?.let { put("morning", it.toJson()) }
        put("morningAt", morningAt)
        put("morningBusy", morningBusy)
        put("morningError", morningError)
        put("morningPicks", JSONArray(morningPicks))
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
                morning = o.optJSONObject("morning")?.let(Morning::of),
                morningAt = o.optInt("morningAt", -1),
                morningBusy = o.optBoolean("morningBusy"),
                morningError = o.optString("morningError"),
                morningPicks = o.optJSONArray("morningPicks")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            )
        }

        fun Memory.toJson(): JSONObject = JSONObject().apply {
            put("summary", summary); put("now", now); put("next", next)
            put("done", done); put("doneAt", doneAt); put("agent", agent); put("folder", folder)
            put("decisions", JSONArray().apply { decisions.forEach { put(JSONObject().put("text", it.text).put("date", it.date)) } })
            put("releases", JSONArray().apply { releases.forEach { put(JSONObject().put("version", it.version).put("date", it.date).put("note", it.note)) } })
            put("left", JSONArray(left)); put("ideas", JSONArray(ideas)); put("doneItems", JSONArray(doneItems))
            put("journal", JSONArray().apply { journal.forEach { put(it.toJson()) } })
            put("built", JSONArray(built)); put("goal", goal); put("saving", saving); put("savingProposal", savingProposal)
            put("goalSteps", JSONArray().apply { goalSteps.forEach { put(JSONObject().put("text", it.text).put("done", it.done)) } })
            put("savingSteps", savingSteps); put("savingBusy", savingBusy); put("savingResult", savingResult)
            put("suggestedGoal", suggestedGoal); put("suggestedSteps", JSONArray(suggestedSteps))
            put("dossierBusy", dossierBusy); put("dossierError", dossierError)
            put("talk", JSONArray().apply { talk.forEach { put(JSONObject().put("kind", it.kind).put("text", it.text)) } })
            put("talkTitle", talkTitle); put("talkBusy", talkBusy); put("talkAsk", talkAsk); put("talkError", talkError); put("talkElsewhere", talkElsewhere)
            put("talkAskId", talkAskId); put("talkAskAlways", talkAskAlways); put("talkSession", talkSession); put("talkUrl", talkUrl); put("insight", insight); put("commands", JSONArray(commands)); put("commandNames", JSONArray(commandNames)); put("commandsBusy", commandsBusy); put("commandsFound", commandsFound)
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
                journal = objs("journal").map(JournalDay::of).filter { it.date.isNotBlank() },
                built = strings("built"), goal = o.optString("goal"), saving = o.optString("saving"), savingProposal = o.optString("savingProposal"),
                goalSteps = objs("goalSteps").map { MemStep(it.optString("text"), it.optBoolean("done")) }.filter { it.text.isNotBlank() },
                savingSteps = o.optString("savingSteps"), savingBusy = o.optBoolean("savingBusy"), savingResult = o.optString("savingResult"),
                suggestedGoal = o.optString("suggestedGoal"), suggestedSteps = strings("suggestedSteps"),
                dossierBusy = o.optBoolean("dossierBusy"), dossierError = o.optString("dossierError"),
                talk = objs("talk").map { MemTalk(it.optString("kind"), it.optString("text")) }.filter { it.text.isNotBlank() },
                talkTitle = o.optString("talkTitle"), talkBusy = o.optBoolean("talkBusy"), talkAsk = o.optString("talkAsk"),
                talkError = o.optString("talkError"), talkElsewhere = o.optBoolean("talkElsewhere"),
                talkAskId = o.optString("talkAskId"), talkAskAlways = o.optString("talkAskAlways"),
                talkSession = o.optString("talkSession"), talkUrl = o.optString("talkUrl"), insight = o.optString("insight"), commands = strings("commands"), commandNames = o.optJSONArray("commandNames")?.let { a -> (0 until a.length()).map { a.optString(it).trim() } } ?: emptyList(), commandsBusy = o.optBoolean("commandsBusy"), commandsFound = o.optString("commandsFound"),
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

// ── what the phone writes: a change to one project ──

/**
 * A change made on the phone, to YDeck's own data only, never code: the next step, a decision, the three lists, the
 * archive, the project's details, a new project. Data from a closed list of kinds, applied at once on the computer
 * (no approval: nothing here runs anything), kept on the phone until the computer says it applied it, so one made
 * while the computer is off happens when it's back. [text] is the main value; [args] the rest, by kind (see below).
 */
data class Note(val id: String, val project: String, val kind: String, val text: String, val at: Long, val args: List<String> = emptyList()) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "note").put("v", 1)
        .put("id", id).put("project", project).put("note", kind).put("text", text).put("at", at)
        .apply { if (args.isNotEmpty()) put("args", JSONArray(args)) }.toString().toByteArray()

    companion object {
        const val NEXT = "next"
        const val DECISION = "decision"
        /** The next step [text] is done: the project's next step is cleared. */
        const val DONE = "done"

        /** To the archive (text "1") or back ("0"). */
        const val ARCHIVE = "archive"

        /** The lists: [text] is the item. LEFT_MOVE's args[0] is the place (0 first); LEFT_EDIT's args[0] the item before. */
        const val LEFT_ADD = "left.add"
        const val LEFT_DONE = "left.done"
        const val LEFT_DROP = "left.drop"
        const val LEFT_MOVE = "left.move"
        const val LEFT_EDIT = "left.edit"
        const val IDEA_ADD = "idea.add"
        const val IDEA_TAKE = "idea.take"
        const val IDEA_DROP = "idea.drop"
        const val DONE_DROP = "done.drop"

        /** The details: NAME the name, COLOR "#RRGGBB", KIND the line under the name. */
        const val NAME = "name"
        const val COLOR = "color"
        const val KIND = "kind"
        /** Links: [text] the URL; args [service, label] (LINK_EDIT: [service, label, the URL before]). */
        const val LINK_ADD = "link.add"
        const val LINK_EDIT = "link.edit"
        const val LINK_DROP = "link.drop"
        /** Values to copy: [text] the value, args[0] its label. */
        const val COPY_ADD = "copy.add"
        const val COPY_DROP = "copy.drop"
        /** A new project, with no folder yet: [project] its id (made here), [text] its name, args[0] its colour. */
        const val NEW_PROJECT = "project.new"
        /** The good morning: a topic put off to tomorrow, or set aside ([text]: what it said); the project picked for
         *  the good morning or left out of it ([text] "1"/"0"). */
        const val MORNING_LATER = "morning.later"
        const val MORNING_SKIP = "morning.skip"
        const val MORNING_PICK = "morning.pick"
        /** "Let's go" on a topic of the good morning ([text]: what it said): the next morning follows it up. */
        const val MORNING_GO = "morning.go"
        /** The dossier: Claude reads the project to fill in what was built and suggest a goal; the suggested goal taken
         *  or set aside; a goal step ticked or unticked ([text]: the step). */
        const val GOAL_SUGGEST = "goal.suggest"
        const val GOAL_TAKE = "goal.take"
        const val GOAL_DISMISS = "goal.dismiss"
        const val GOAL_STEP = "goal.step"
        /** The save card's button: YDeck runs its steps (a closed list: commit, push, .gitignore, merge). */
        const val SAVE_DO = "save.do"
        /** Writing to Claude in the project: a message ([text], may span lines), a new conversation, stop. */
        const val TALK_SAY = "talk.say"
        const val TALK_NEW = "talk.new"
        const val TALK_STOP = "talk.stop"
        /** The answer to the permission Claude waits for: [text] its id, args[0] "once", "always" or "no". */
        const val TALK_ALLOW = "talk.allow"
        /** "Find commands": Claude reads the project's folder for its commands, and they join the project's. */
        const val COMMANDS_FIND = "commands.find"
        /** The phone's app crashed: [text] is what went wrong (the error and where), kept on the computer to be fixed. */
        const val CRASH = "app.crash"
        /** How long a request that makes something happen on the computer waits on the relay before it's dropped. */
        const val FRESH_MS = 15 * 60_000L

        private val KINDS = setOf(NEXT, DECISION, DONE, ARCHIVE, LEFT_ADD, LEFT_DONE, LEFT_DROP, LEFT_MOVE, LEFT_EDIT, IDEA_ADD, IDEA_TAKE,
            IDEA_DROP, DONE_DROP, NAME, COLOR, KIND, LINK_ADD, LINK_EDIT, LINK_DROP, COPY_ADD, COPY_DROP, NEW_PROJECT, MORNING_LATER, MORNING_SKIP, MORNING_PICK, MORNING_GO,
            GOAL_SUGGEST, GOAL_TAKE, GOAL_DISMISS, GOAL_STEP, SAVE_DO, TALK_SAY, TALK_NEW, TALK_STOP, TALK_ALLOW, COMMANDS_FIND, CRASH)
        /** Kinds whose text may be empty. */
        private val MAY_BE_EMPTY = setOf(DONE, KIND, GOAL_SUGGEST, GOAL_TAKE, GOAL_DISMISS, SAVE_DO, TALK_NEW, TALK_STOP, COMMANDS_FIND)

        /** The longest note the computer accepts. */
        const val MAX = 1000
        /** A message to Claude, or a crash report, may be this long. */
        const val LONG = 4000

        /** One line of plain text, never longer than MAX. */
        fun line(s: String): String = s.filter { !it.isISOControl() || it == '\n' }.replace(Regex("\\s*\\n\\s*"), " ").trim().take(MAX)

        fun decode(bytes: ByteArray): Note? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "note") return null
            val kind = o.optString("note").takeIf { it in KINDS } ?: return null
            // Plain text only: no control characters, one line except for a decision, never longer than MAX.
            val raw = o.optString("text")
            val text = if (kind == DECISION || kind == TALK_SAY || kind == CRASH) raw.filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim().take(if (kind == DECISION) MAX else LONG) else line(raw)
            if (text.isEmpty() && kind !in MAY_BE_EMPTY) return null
            val args = o.optJSONArray("args")?.let { a -> (0 until minOf(a.length(), 4)).map { line(a.optString(it)) } } ?: emptyList()
            Note(o.optString("id").take(64), o.optString("project").take(100), kind, text, o.optLong("at"), args)
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
data class Ask(val id: String, val chat: String, val question: String, val project: String = "", val at: Long,
               /** Asks for the good morning ([Morning]) now, in this conversation, instead of an answer; [question] is
                *  what the phone showed for it, maybe nothing (the morning the computer sent by itself). */
               val morning: Boolean = false) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "ask").put("v", 1)
        .put("id", id).put("chat", chat).put("question", question).put("project", project).put("at", at)
        .apply { if (morning) put("morning", true) }.toString().toByteArray()

    companion object {
        /** The longest question the computer accepts. */
        const val MAX = 2000
        /** A question that waited longer than this (the computer was off) is told it came too late, not answered. */
        const val FRESH_MS = 15 * 60_000L

        fun decode(bytes: ByteArray): Ask? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "ask") return null
            val q = o.optString("question").filter { it == '\n' || !it.isISOControl() }.trim().take(MAX)
            val morning = o.optBoolean("morning")
            if (q.isEmpty() && !morning) return null
            Ask(o.optString("id").take(64), o.optString("chat").take(64), q, o.optString("project").take(100), o.optLong("at"), morning)
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
    /** The same answer in order (one line, then lists by kind), for the phone to show as a card; [answer] is its Markdown. */
    val card: Card? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("chat", chat).put("done", done).put("answer", answer)
        .put("error", error).put("step", step).put("reads", reads).put("seconds", seconds)
        .apply { card?.let { put("card", it.toJson()) } }

    companion object {
        /** The longest answer sent. */
        const val MAX = 12_000

        fun of(o: JSONObject) = Answer(o.optString("id"), o.optString("chat"), o.optBoolean("done"), o.optString("answer"),
            o.optString("error"), o.optString("step"), o.optInt("reads"), o.optInt("seconds"), o.optJSONObject("card")?.let(Card::of))
    }
}

/**
 * An answer in order rather than a block of text: [say], the answer in a line or two; then [groups], short lists by
 * kind (what's left, what's done, what waits for you, ideas, decisions, other facts), each line about one project;
 * then [more], what doesn't fit as lines (Markdown, often empty). The phone shows it as a card with a button on each
 * line, and can read it aloud.
 */
data class Card(val say: String, val groups: List<CardGroup> = emptyList(), val more: String = "") {
    fun toJson(): JSONObject = JSONObject().put("say", say).put("more", more)
        .put("groups", JSONArray().apply { groups.forEach { put(it.toJson()) } })

    /** The same as Markdown: for an older phone, the computer's own panel, and the conversation's memory. */
    fun markdown(name: (String) -> String): String = buildString {
        appendLine(say)
        groups.forEach { g ->
            appendLine().appendLine("**" + g.title + "**")
            g.items.forEach { appendLine("- " + (if (it.project.isNotBlank()) name(it.project) + ": " else "") + it.text) }
        }
        if (more.isNotBlank()) appendLine().appendLine(more)
    }.trim()

    companion object {
        fun of(o: JSONObject): Card? {
            val groups = o.optJSONArray("groups")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(CardGroup::of) } }.orEmpty()
            val say = o.optString("say").trim()
            return if (say.isEmpty() && groups.isEmpty()) null else Card(say, groups, o.optString("more").trim())
        }
    }
}

/** One list in a [Card]: its [kind] (below) sets its mark and the buttons on its lines; [title] is Claude's words. */
data class CardGroup(val kind: String, val title: String, val items: List<CardItem>) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind).put("title", title)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })

    companion object {
        const val LEFT = "left"
        const val DONE = "done"
        const val WAITING = "waiting"
        const val IDEA = "idea"
        const val DECIDED = "decided"
        const val INFO = "info"
        val KINDS = listOf(LEFT, WAITING, DONE, IDEA, DECIDED, INFO)

        fun of(o: JSONObject): CardGroup? {
            val items = o.optJSONArray("items")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(CardItem::of) } }.orEmpty()
            if (items.isEmpty()) return null
            val kind = o.optString("kind").lowercase().takeIf { it in KINDS } ?: INFO
            return CardGroup(kind, o.optString("title").trim(), items)
        }
    }
}

/**
 * One line of a [CardGroup], about [project] (its id, or empty). [from] is set by the computer, never by Claude: the
 * project's list the line is in word for word ("left" or "ideas"), so the phone may offer to mark it done or take it.
 */
data class CardItem(val project: String, val text: String, val from: String = "") {
    fun toJson(): JSONObject = JSONObject().put("project", project).put("text", text).apply { if (from.isNotEmpty()) put("from", from) }

    companion object {
        const val FROM_LEFT = "left"
        const val FROM_IDEAS = "ideas"

        fun of(o: JSONObject): CardItem? = o.optString("text").trim().takeIf { it.isNotEmpty() }
            ?.let { CardItem(o.optString("project").trim(), it, o.optString("from")) }
    }
}

// ── good morning: YDeck starts the day with the developer ──

/**
 * A good morning: at the hour set on the computer, its Claude Code (reading only) goes over the projects and writes to
 * the developer like a partner would: what to finish today, what is stuck, an idea worth thinking about, with
 * questions. The phone shows it as a notification, and the reply goes on in [chat] as any question to Ask YDeck.
 */
data class Morning(
    val id: String,
    val chat: String,
    /** All of it as Markdown: the conversation starts from it. */
    val text: String,
    val at: Long,
    /** The opening line, and two or three topics, each with its own buttons on the phone. */
    val hello: String = "",
    val topics: List<MorningTopic> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("chat", chat).put("text", text).put("at", at).put("hello", hello)
        .put("topics", JSONArray().apply { topics.forEach { put(it.toJson()) } })

    companion object {
        fun of(o: JSONObject): Morning? = Morning(o.optString("id").take(64), o.optString("chat").take(64), o.optString("text").take(Answer.MAX), o.optLong("at"),
            o.optString("hello").take(400),
            o.optJSONArray("topics")?.let { a -> (0 until minOf(a.length(), 4)).mapNotNull { a.optJSONObject(it)?.let(MorningTopic::of) } }.orEmpty())
            .takeIf { it.id.isNotEmpty() && it.chat.isNotEmpty() && it.text.isNotBlank() }
    }
}

/**
 * One thing the good morning brings up: in [project] (its id), of a [kind] (finish, stuck, idea, decide), what it
 * says and the question it asks, and [step]: what Claude is asked to do there when the developer says "let's go".
 */
data class MorningTopic(val project: String, val kind: String, val say: String, val ask: String = "", val step: String = "",
                        /** A suggestion from the day's review ([SUGGEST]): where [step] would go, "idea" or "left". */
                        val add: String = "") {
    fun toJson(): JSONObject = JSONObject().put("project", project).put("kind", kind).put("say", say).put("ask", ask).put("step", step).put("add", add)

    companion object {
        const val FINISH = "finish"
        const val STUCK = "stuck"
        const val IDEA = "idea"
        const val DECIDE = "decide"
        /** Something the day's review of the project's code suggests: a new idea, or something to close. */
        const val SUGGEST = "suggest"

        fun of(o: JSONObject): MorningTopic? = MorningTopic(o.optString("project").take(100), o.optString("kind").take(20),
            o.optString("say").take(400), o.optString("ask").take(300), o.optString("step").take(600), o.optString("add").take(10))
            .takeIf { it.project.isNotBlank() && it.say.isNotBlank() }
    }
}

/**
 * One day of a project, as YDeck saw it move: what was done (ticked off what was left), what was added to it, ideas
 * that came up, the commits made and what each Claude session did. YDeck keeps it so the days connect: the good
 * morning knows what moved since, and the phone shows it on the project's page.
 */
data class JournalDay(
    val date: String,
    val done: List<String> = emptyList(),
    val added: List<String> = emptyList(),
    val ideas: List<String> = emptyList(),
    val commits: List<String> = emptyList(),
    val sessions: List<String> = emptyList(),
) {
    val empty: Boolean get() = done.isEmpty() && added.isEmpty() && ideas.isEmpty() && commits.isEmpty() && sessions.isEmpty()

    fun toJson(): JSONObject = JSONObject().put("date", date).put("done", JSONArray(done)).put("added", JSONArray(added))
        .put("ideas", JSONArray(ideas)).put("commits", JSONArray(commits)).put("sessions", JSONArray(sessions))

    companion object {
        fun of(o: JSONObject): JournalDay {
            fun strings(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            return JournalDay(o.optString("date"), strings("done"), strings("added"), strings("ideas"), strings("commits"), strings("sessions"))
        }
    }
}
