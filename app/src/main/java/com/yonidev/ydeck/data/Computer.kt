package com.yonidev.ydeck.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.yonidev.ydeck.link.Answer
import com.yonidev.ydeck.link.Ask
import com.yonidev.ydeck.link.Launch
import com.yonidev.ydeck.link.Launched
import com.yonidev.ydeck.link.Live
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.NewKey
import com.yonidev.ydeck.link.Note
import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.Reply
import com.yonidev.ydeck.link.Waiting
import com.yonidev.ydeck.link.Snapshot
import com.yonidev.ydeck.widget.DeckWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/*
 * YDeck on the computer, seen from the phone. The computer is where projects are made and kept; the phone reads them,
 * with each project's memory, from the sealed box on the relay (shared/kotlin/.../Link.kt).
 *
 *   noBackupFilesDir/computer.json  the pairing's secret, the computer's name, the box's last ETag, and the secret it
 *                                   moves to (see [moveKey]). Never in a backup.
 *   filesDir/memory.json            the memory of every project, as the computer last sent it.
 *   filesDir/projects.json          the projects themselves (Repo), replaced by every new state.
 *   filesDir/ask.json               the conversation with Ask YDeck: the questions asked here and the answers.
 */
object Computer {

    data class State(
        val paired: Boolean = false,
        val name: String = "",
        /** When the computer made the state we have, and when we last asked for it. */
        val stateAt: Long = 0,
        val checkedAt: Long = 0,
        val memory: Map<String, Memory> = emptyMap(),
        /** Git, the agent, receipts, open loops: what the computer's window shows for each project with a folder. */
        val live: Map<String, Live> = emptyMap(),
        /** Notes written here that the computer hasn't stored yet (sent or not). */
        val pending: List<Note> = emptyList(),
        /** The computer opens Claude Code when asked (see [launch]). */
        val canLaunch: Boolean = false,
        /** Claude Code is on the computer, so the phone shows the way (and, while [canLaunch] is off, where to turn it on). */
        val claudeHere: Boolean = false,
        /** Claude Code sessions asked for here, and what the computer did with each (null: not yet). */
        val launches: List<Pair<Launch, Launched?>> = emptyList(),
        /** The computer sends its state at least this often (0: an older YDeck that doesn't). */
        val beat: Long = 0,
        /** The computer answers questions (see [ask]): on in its settings, and Claude Code is there. */
        val canAsk: Boolean = false,
        /** The conversation with Ask YDeck. */
        val chat: Chat = Chat(),
        /** Claude Code sessions waiting for the developer, held on the computer, with YDeck's drafted reply. */
        val waiting: List<Waiting> = emptyList(),
        /** The phone may reply to them (on in the computer's settings). */
        val canReply: Boolean = false,
        /** Replies sent from here, and what the computer did with each (null: not yet). */
        val replies: List<Pair<Reply, Launched?>> = emptyList(),
        /** The computer takes command lines from here (each waits for approval there, unless allowed always). */
        val canRun: Boolean = false,
        /** Commands sent from here, and what became of each (null: not heard yet). */
        val runs: List<Pair<com.yonidev.ydeck.link.RunCommand, com.yonidev.ydeck.link.Ran?>> = emptyList(),
        /** Lines allowed always, by project: offered for one tap. */
        val runOk: Map<String, List<String>> = emptyMap(),
    ) {
        /**
         * A project's live state, as far as it can be trusted now: when the computer has stopped sending (asleep, off,
         * YDeck closed), what Claude was doing there is no longer shown, since it may have changed since.
         */
        fun live(projectId: String, now: Long = System.currentTimeMillis()): Live? {
            val l = live[projectId] ?: return null
            return if (beat > 0 && now - stateAt > 2 * beat + 60_000) l.withoutAgent() else l
        }
    }

    /** A question asked here, and the computer's answer (null: not taken yet; not [Answer.done]: Claude is at work). */
    data class Turn(val ask: Ask, val answer: Answer? = null) {
        val done: Boolean get() = answer?.done == true
    }

    /** One conversation with Ask YDeck: the next question goes on in it, until a new one starts. */
    data class Chat(val id: String = java.util.UUID.randomUUID().toString(), val turns: List<Turn> = emptyList()) {
        /** A question still waits for its answer. */
        val busy: Boolean get() = turns.any { !it.done && System.currentTimeMillis() - it.ask.at < WAIT_MS }
    }

    /** How long a question waits for its answer before the phone stops waiting (the computer gives up on its own at 5 minutes). */
    const val WAIT_MS = 7 * 60_000L

    enum class Result { UPDATED, SAME, EMPTY, OFFLINE, REFUSED, BROKEN }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun state(context: Context): State = _state.value ?: load(context).also { _state.value = it }

    @Volatile private var pairing: Pairing? = null
    /** The pairing now, for the live line ([LiveOut]); null when not paired. */
    val pairingNow: Pairing? get() = pairing
    @Volatile private var etag: String? = null
    /** The secret was made here, never shown on a screen (a one-time code, or moved to after an older one). */
    @Volatile private var made = false
    /** The secret it's moving to, and when the computer was last told; see [moveKey]. */
    @Volatile private var nextKey: ByteArray? = null
    @Volatile private var nextAt = 0L

    private fun linkFile(c: Context) = File(c.applicationContext.noBackupFilesDir, "computer.json")
    private fun memoryFile(c: Context) = File(c.applicationContext.filesDir, "memory.json")
    /** Notes not yet stored by the computer, and whether each has reached the relay. */
    private fun outboxFile(c: Context) = File(c.applicationContext.filesDir, "outbox.json")
    private fun chatFile(c: Context) = File(c.applicationContext.filesDir, "ask.json")

    private fun load(context: Context): State {
        val link = runCatching { JSONObject(linkFile(context).readText()) }.getOrNull()
        pairing = link?.optString("secret")?.takeIf { it.isNotEmpty() }?.let { runCatching { Pairing(Base64.getUrlDecoder().decode(it)) }.getOrNull() }
        etag = link?.optString("etag")?.takeIf { it.isNotEmpty() }
        made = link?.optBoolean("made") ?: false
        nextKey = link?.optString("next")?.takeIf { it.isNotEmpty() }?.let { runCatching { Base64.getUrlDecoder().decode(it) }.getOrNull() }
        nextAt = link?.optLong("nextAt") ?: 0
        val mem = runCatching { JSONObject(memoryFile(context).readText()) }.getOrNull()
        val memory = mem?.optJSONObject("memory")?.let { m -> m.keys().asSequence().associateWith { Snapshot.memoryOf(m.getJSONObject(it)) } }.orEmpty()
        val live = mem?.optJSONObject("live")?.let { m -> m.keys().asSequence().associateWith { Live.of(m.getJSONObject(it)) } }.orEmpty()
        outbox = readOutbox(context)
        return State(
            paired = pairing != null,
            name = link?.optString("name").orEmpty().ifEmpty { mem?.optString("computer").orEmpty() },
            stateAt = mem?.optLong("at") ?: 0,
            checkedAt = link?.optLong("checkedAt") ?: 0,
            memory = memory,
            live = live,
            pending = outbox.map { it.first },
            canLaunch = mem?.optBoolean("launch") ?: false,
            claudeHere = mem?.optBoolean("claude") ?: false,
            beat = mem?.optLong("beat") ?: 0,
            canAsk = mem?.optBoolean("ask") ?: false,
            chat = readChat(context),
            waiting = mem?.optJSONArray("waiting")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().map(Waiting::of),
            canReply = mem?.optBoolean("reply") ?: false,
            canRun = mem?.optBoolean("run") ?: false,
            runOk = mem?.optJSONObject("runOk")?.let { r -> r.keys().asSequence().associateWith { k -> r.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty() } }.orEmpty(),
        )
    }

    // ── Ask YDeck: questions answered on the computer ──

    private fun readChat(c: Context): Chat = runCatching {
        val o = JSONObject(chatFile(c).readText())
        val a = o.getJSONArray("turns")
        Chat(o.getString("id"), (0 until a.length()).mapNotNull { i ->
            val t = a.getJSONObject(i)
            Ask.decode(t.getString("ask").toByteArray())?.let { Turn(it, t.optJSONObject("answer")?.let(Answer::of)) }
        })
    }.getOrDefault(Chat())

    private fun writeChat(c: Context, chat: Chat) {
        chatFile(c).writeText(JSONObject().put("id", chat.id).put("turns", org.json.JSONArray().apply {
            chat.turns.takeLast(30).forEach { t -> put(JSONObject().put("ask", String(t.ask.encode())).apply { t.answer?.let { put("answer", it.toJson()) } }) }
        }).toString())
        _state.value = state(c).copy(chat = chat)
    }

    /**
     * Asks Ask YDeck on the computer: [projectId] is the project the question was asked from ("this project"). Sent now
     * or not at all; the answer comes in a later state (see [sync]).
     */
    suspend fun ask(context: Context, question: String, projectId: String?): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val p = pairing ?: return@withContext false
        val chat = state(app).chat
        val a = Ask(java.util.UUID.randomUUID().toString(), chat.id, question.trim().take(Ask.MAX), projectId.orEmpty(), System.currentTimeMillis())
        if (!Relay(p).postNote(p.seal("ask", a.encode()))) return@withContext false
        writeChat(app, chat.copy(turns = chat.turns + Turn(a)))
        true
    }

    /**
     * Replies to a session waiting for the developer: words, or the option picked for each question of a choice. Sent
     * now or not at all (a session doesn't wait forever); the computer says what it did in a later state.
     */
    suspend fun reply(context: Context, waiting: String, text: String, choices: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val p = pairing ?: return@withContext false
        val r = Reply(java.util.UUID.randomUUID().toString(), waiting, text.trim().take(Reply.MAX), choices, System.currentTimeMillis())
        if (!Relay(p).postNote(p.seal("reply", r.encode()))) return@withContext false
        val s = state(app)
        _state.value = s.copy(replies = (s.replies + (r to null)).takeLast(10))
        true
    }

    /** A new conversation: the next question starts afresh, with what YDeck knows. */
    fun newChat(context: Context) = writeChat(context.applicationContext, Chat())

    // ── writing: notes into a project's memory ──

    /** Notes waiting for the computer, and whether each reached the relay. */
    @Volatile private var outbox: List<Pair<Note, Boolean>> = emptyList()

    private fun readOutbox(c: Context): List<Pair<Note, Boolean>> = runCatching {
        val a = org.json.JSONArray(outboxFile(c).readText())
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            Note.decode(o.getString("note").toByteArray())?.let { it to o.optBoolean("sent") }
        }
    }.getOrDefault(emptyList())

    private fun writeOutbox(c: Context, list: List<Pair<Note, Boolean>>) {
        outbox = list
        outboxFile(c).writeText(org.json.JSONArray().apply { list.forEach { (n, sent) -> put(JSONObject().put("note", String(n.encode())).put("sent", sent)) } }.toString())
        _state.value = state(c).copy(pending = list.map { it.first })
    }

    /** A decision or a next step for [projectId]: kept here at once, sent to the relay now or at the next chance. */
    suspend fun write(context: Context, projectId: String, kind: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        state(app)
        val note = Note(java.util.UUID.randomUUID().toString(), projectId, kind, text.trim().take(Note.MAX), System.currentTimeMillis())
        writeOutbox(app, outbox + (note to false))
        sendPending(app)
    }

    /** Sends the notes that haven't reached the relay. True when none is left unsent. */
    private fun sendPending(c: Context): Boolean {
        val p = pairing ?: return false
        val relay = Relay(p)
        var list = outbox
        for ((note, sent) in list) {
            if (sent) continue
            if (relay.postNote(p.seal("note", note.encode()))) list = list.map { if (it.first.id == note.id) note to true else it } else break
        }
        writeOutbox(c, list)
        return list.all { it.second }
    }

    // ── asking: Claude Code on the computer ──

    /**
     * Asks the computer to open Claude Code in [projectId]'s folder (see [Launch]). Sent now or never: unlike a note
     * it isn't kept for later, since a session that opens hours after it was asked for is a surprise, not a help.
     */
    suspend fun launch(context: Context, projectId: String, mode: String, session: String, prompt: String, remote: Boolean): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val p = pairing ?: return@withContext false
        val l = Launch(java.util.UUID.randomUUID().toString(), projectId, mode, session, prompt.trim().take(Launch.MAX), remote, System.currentTimeMillis())
        if (!Relay(p).postNote(p.seal("launch", l.encode()))) return@withContext false
        val s = state(app)
        _state.value = s.copy(launches = (s.launches + (l to null)).takeLast(10))
        true
    }

    /**
     * Asks the computer to run [line] in [projectId]'s folder (see [com.yonidev.ydeck.link.RunCommand]), in a new
     * terminal window there or inside YDeck ([window]). It waits there
     * for approval unless that exact line was allowed always. Sent now or never, like [launch].
     */
    suspend fun run(context: Context, projectId: String, line: String, window: Boolean): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val p = pairing ?: return@withContext false
        val clean = com.yonidev.ydeck.link.RunCommand.clean(line) ?: return@withContext false
        val r = com.yonidev.ydeck.link.RunCommand(java.util.UUID.randomUUID().toString(), projectId, clean, System.currentTimeMillis(), window)
        if (!Relay(p).postNote(p.seal("run", r.encode()))) return@withContext false
        val s = state(app)
        _state.value = s.copy(runs = (s.runs + (r to null)).takeLast(12))
        true
    }

    sealed interface Paired {
        data class Done(val name: String) : Paired
        data object NotOurs : Paired
        /** The code was used already, changed (every two minutes), or closed. */
        data object Expired : Paired
        data object Offline : Paired
    }

    /**
     * A scanned QR code. A one-time code (YDeck for Windows 1.16.1 on) carries the key for this pairing only: the
     * lasting secret is made here and sent through it, so it never shows on a screen. An older code is the lasting
     * secret itself; [moveKey] then moves to one made here.
     */
    suspend fun pair(context: Context, qr: String): Paired = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val code = Pairing.parse(qr) ?: return@withContext Paired.NotOurs
        val p = if (code.oneTime) {
            val mine = Pairing.create()
            val relay = Relay(code.pairing)
            var sent = false
            // The computer claims the code's box a second or two after showing it.
            for (attempt in 0 until 4) {
                if (relay.postNote(code.pairing.seal("pair", NewKey(mine.secret, System.currentTimeMillis()).encode()))) { sent = true; break }
                if (relay.lastCode == 0) return@withContext Paired.Offline
                delay(2_000)
            }
            if (!sent) return@withContext Paired.Expired
            mine
        } else code.pairing
        state(app)
        pairing = p
        etag = null
        made = code.oneTime
        nextKey = null
        nextAt = 0
        writeLink(app, p, code.computer, etag = null, checkedAt = 0)
        _state.value = state(app).copy(paired = true, name = code.computer)
        Paired.Done(code.computer)
    }

    /**
     * A pairing from an older code (the lasting secret was on the computer's screen) moves to a secret made here: sent
     * sealed with the old one; once the computer has put a state in the new box, the phone follows. A computer too old
     * to know this ignores it, and the phone asks again every hour or so until one answers.
     */
    private fun moveKey(c: Context) {
        val p = pairing ?: return
        if (made) return
        val now = System.currentTimeMillis()
        nextKey?.let { next ->
            val n = Pairing(next)
            val got = Relay(n).getState(null)?.sealed
            if (got != null && runCatching { n.open("state", got) }.isSuccess) {
                pairing = n
                made = true
                nextKey = null
                etag = null
                writeLink(c, n, state(c).name, etag = null, checkedAt = state(c).checkedAt)
                // Notes left in the old box may have gone with it: sent again (the computer skips what it has).
                if (outbox.isNotEmpty()) writeOutbox(c, outbox.map { it.first to false })
                return
            }
            if (now - nextAt < 3600_000L) return
        }
        val key = nextKey ?: Pairing.create().secret
        nextKey = key
        if (Relay(p).postNote(p.seal("rekey", NewKey(key, now).encode()))) nextAt = now
        writeLink(c, p, state(c).name, etag, state(c).checkedAt)
    }

    /** Forgets the computer. The projects and memory last seen stay on the phone. */
    fun unpair(context: Context) {
        pairing = null
        etag = null
        linkFile(context).delete()
        _state.value = state(context).copy(paired = false)
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Asks the box for the computer's latest state; a new one replaces the projects and the memory. Blocking-free. */
    suspend fun sync(context: Context): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        state(app)
        runCatching { moveKey(app) }
        val p = pairing ?: return@withContext Result.REFUSED
        if (outbox.any { !it.second }) sendPending(app)
        val relay = Relay(p)
        val got = relay.getState(etag)
        val now = System.currentTimeMillis()
        if (got == null) {
            return@withContext when (relay.lastCode) {
                401 -> Result.REFUSED
                404 -> Result.EMPTY
                else -> Result.OFFLINE
            }
        }
        val sealed = got.sealed
        if (sealed == null) {
            touch(app, p, now)
            return@withContext Result.SAME
        }
        val snapshot = runCatching { Snapshot.decode(p.open("state", sealed)) }.getOrNull() ?: return@withContext Result.BROKEN
        val deck = Repo.importJson(app, snapshot.deck.toString())
        Repo.save(app, deck)
        memoryFile(app).writeText(JSONObject().apply {
            put("at", snapshot.at)
            put("computer", snapshot.computer)
            put("memory", JSONObject().apply { snapshot.memory.forEach { (id, m) -> put(id, with(Snapshot) { m.toJson() }) } })
            put("live", JSONObject().apply { snapshot.live.forEach { (id, l) -> put(id, l.toJson()) } })
            put("launch", snapshot.launch)
            put("claude", snapshot.claude)
            put("beat", snapshot.beat)
            put("ask", snapshot.ask)
            put("waiting", org.json.JSONArray().apply { snapshot.waiting.forEach { put(it.toJson()) } })
            put("reply", snapshot.reply)
            put("run", snapshot.run)
            put("runOk", JSONObject().apply { snapshot.runOk.forEach { (k, v) -> put(k, org.json.JSONArray(v)) } })
        }.toString())
        // The answers that came: each onto its question.
        val chat = state(app).chat
        if (chat.turns.any { t -> !t.done && snapshot.answers.any { it.id == t.ask.id && it != t.answer } })
            writeChat(app, chat.copy(turns = chat.turns.map { t -> if (t.done) t else snapshot.answers.firstOrNull { it.id == t.ask.id }?.let { t.copy(answer = it) } ?: t }))
        // Notes the computer has stored are done; the rest wait.
        if (outbox.any { it.first.id in snapshot.applied }) writeOutbox(app, outbox.filterNot { it.first.id in snapshot.applied })
        etag = got.etag
        writeLink(app, p, snapshot.computer, got.etag, now)
        val launches = state(app).launches.map { (l, done) -> l to (done ?: snapshot.launched.firstOrNull { it.id == l.id }) }
        val replies = state(app).replies.map { (r, done) -> r to (done ?: snapshot.replied.firstOrNull { it.id == r.id }) }
        // A command's news changes (asking, running, done): the latest always wins.
        val runs = state(app).runs.map { (r, was) -> r to (snapshot.ran.firstOrNull { it.id == r.id } ?: was) }
        _state.value = State(paired = true, name = snapshot.computer, stateAt = snapshot.at, checkedAt = now, memory = snapshot.memory, live = snapshot.live,
            pending = outbox.map { it.first }, canLaunch = snapshot.launch, claudeHere = snapshot.claude, launches = launches, beat = snapshot.beat,
            canAsk = snapshot.ask, chat = state(app).chat, waiting = snapshot.waiting, canReply = snapshot.reply, replies = replies,
            canRun = snapshot.run, runs = runs, runOk = snapshot.runOk)
        DeckWidget().updateAll(app)
        Result.UPDATED
    }

    private fun touch(c: Context, p: Pairing, now: Long) {
        val s = state(c)
        writeLink(c, p, s.name, etag, now)
        _state.value = s.copy(checkedAt = now)
    }

    private fun writeLink(c: Context, p: Pairing, name: String, etag: String?, checkedAt: Long) {
        linkFile(c).writeText(JSONObject().put("secret", Pairing.b64(p.secret)).put("name", name).put("etag", etag ?: "").put("checkedAt", checkedAt)
            .put("made", made).put("next", nextKey?.let(Pairing::b64) ?: "").put("nextAt", nextAt).toString())
    }

    /** The sample's memory, so "try a sample" shows what a paired phone shows. */
    fun addSample(context: Context) {
        val s = state(context)
        _state.value = s.copy(memory = s.memory + (Project.sample().id to SAMPLE_MEMORY), live = s.live + (Project.sample().id to sampleLive()))
    }

    /** The sample's Git: a session at work with files not yet committed, an earlier one and the commits it made. */
    private fun sampleLive(): com.yonidev.ydeck.link.Live {
        val now = System.currentTimeMillis()
        val h = 3600_000L
        val files = listOf(
            com.yonidev.ydeck.link.LiveFile("??", "app/src/main/java/nova/stats/CsvExport.kt"),
            com.yonidev.ydeck.link.LiveFile(" M", "app/src/main/java/nova/stats/StatsScreen.kt"),
            com.yonidev.ydeck.link.LiveFile(" M", "app/src/main/res/values/strings.xml"),
        )
        return com.yonidev.ydeck.link.Live(
            branch = "stats-export", changes = files.size, ahead = 2, upstream = "origin/stats-export", hasGit = true,
            lastAt = now - 3 * h, lastSubject = "Weekly chart on the stats screen",
            files = files,
            commits = listOf(
                com.yonidev.ydeck.link.LiveCommit("a41c9e2", now - 3 * h, "Dana", "Weekly chart on the stats screen"),
                com.yonidev.ydeck.link.LiveCommit("7d03b18", now - 4 * h, "Dana", "Streak counts in the user's time zone"),
                com.yonidev.ydeck.link.LiveCommit("e9a2f40", now - 30 * h, "Dana", "New settings screen"),
            ),
            agentMode = "WORKING", agentSince = now - 25 * 60_000L, agent = "Claude", agentDetail = "מוסיף כפתור ייצוא למסך הסטטיסטיקה",
            receipts = listOf(
                com.yonidev.ydeck.link.LiveReceipt("ייצוא ל־CSV במסך הסטטיסטיקה", "Claude", now - 25 * 60_000L, 0, files, false),
                com.yonidev.ydeck.link.LiveReceipt("גרף שבועי ותיקון הרצף בחצות", "Claude", now - 5 * h, now - 3 * h + 60_000L,
                    listOf(com.yonidev.ydeck.link.LiveFile(" M", "app/src/main/java/nova/stats/WeekChart.kt"), com.yonidev.ydeck.link.LiveFile(" M", "app/src/main/java/nova/streak/Streak.kt")), false),
            ),
            version = "1.4.0",
        )
    }

    private val SAMPLE_MEMORY = Memory(
        summary = "Nova היא אפליקציית הרגלים קטנה: תזכורות, רצפים וסטטיסטיקה שבועית. Kotlin עם Compose, Firebase מאחור.",
        now = "מסך הסטטיסטיקה החדש",
        next = "להוסיף ייצוא ל־CSV במסך הסטטיסטיקה",
        done = "בנה את הגרף השבועי ותיקן את הרצף שהתאפס בחצות.",
        doneAt = System.currentTimeMillis() - 3 * 3600_000L,
        agent = "Claude",
        decisions = listOf(
            com.yonidev.ydeck.link.MemDecision("נשארים עם Firebase ולא עוברים ל־Supabase", "2026-10-12"),
            com.yonidev.ydeck.link.MemDecision("הרצף נספר לפי אזור הזמן של המשתמש", "2026-10-11"),
        ),
        releases = listOf(com.yonidev.ydeck.link.MemRelease("1.4.0", "2026-10-12", "גרף שבועי")),
        folder = "nova",
        left = listOf("להוסיף ייצוא ל־CSV במסך הסטטיסטיקה", "תזכורת שבועית ביום ראשון בבוקר", "לבדוק את הרצף במעבר לשעון חורף", "לשחרר את 1.5 לבדיקה הסגורה"),
        ideas = listOf("ווידג׳ט של הרצף למסך הבית", "שיתוף הרצף עם חבר", "מצב כהה לגרף"),
        doneItems = listOf("הגרף השבועי", "תיקון הרצף שהתאפס בחצות", "מסך ההגדרות החדש", "התחברות עם Google", "גרסה 1.4.0 בחנות"),
    )

    // ── background ──

    private const val WORK = "computer-sync"

    /** Every hour or so, when online: keeps the widget's "next" fresh without opening the app. */
    fun schedule(context: Context) {
        if (pairing == null && !state(context).paired) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): androidx.work.ListenableWorker.Result {
            sync(applicationContext)
            return androidx.work.ListenableWorker.Result.success()
        }
    }
}
