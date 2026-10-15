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
import com.yonidev.ydeck.link.Launch
import com.yonidev.ydeck.link.Launched
import com.yonidev.ydeck.link.Live
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.NewKey
import com.yonidev.ydeck.link.Note
import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
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
    )

    enum class Result { UPDATED, SAME, EMPTY, OFFLINE, REFUSED, BROKEN }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun state(context: Context): State = _state.value ?: load(context).also { _state.value = it }

    @Volatile private var pairing: Pairing? = null
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
        )
    }

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
        }.toString())
        // Notes the computer has stored are done; the rest wait.
        if (outbox.any { it.first.id in snapshot.applied }) writeOutbox(app, outbox.filterNot { it.first.id in snapshot.applied })
        etag = got.etag
        writeLink(app, p, snapshot.computer, got.etag, now)
        val launches = state(app).launches.map { (l, done) -> l to (done ?: snapshot.launched.firstOrNull { it.id == l.id }) }
        _state.value = State(paired = true, name = snapshot.computer, stateAt = snapshot.at, checkedAt = now, memory = snapshot.memory, live = snapshot.live,
            pending = outbox.map { it.first }, canLaunch = snapshot.launch, claudeHere = snapshot.claude, launches = launches)
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
        _state.value = s.copy(memory = s.memory + (Project.sample().id to SAMPLE_MEMORY))
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
