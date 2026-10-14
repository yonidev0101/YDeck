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
import com.yonidev.ydeck.link.Live
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Note
import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.Snapshot
import com.yonidev.ydeck.widget.DeckWidget
import kotlinx.coroutines.Dispatchers
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
 *   noBackupFilesDir/computer.json  the pairing's secret, the computer's name, the box's last ETag. Never in a backup.
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
    )

    enum class Result { UPDATED, SAME, EMPTY, OFFLINE, REFUSED, BROKEN }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun state(context: Context): State = _state.value ?: load(context).also { _state.value = it }

    @Volatile private var pairing: Pairing? = null
    @Volatile private var etag: String? = null

    private fun linkFile(c: Context) = File(c.applicationContext.noBackupFilesDir, "computer.json")
    private fun memoryFile(c: Context) = File(c.applicationContext.filesDir, "memory.json")
    /** Notes not yet stored by the computer, and whether each has reached the relay. */
    private fun outboxFile(c: Context) = File(c.applicationContext.filesDir, "outbox.json")

    private fun load(context: Context): State {
        val link = runCatching { JSONObject(linkFile(context).readText()) }.getOrNull()
        pairing = link?.optString("secret")?.takeIf { it.isNotEmpty() }?.let { runCatching { Pairing(Base64.getUrlDecoder().decode(it)) }.getOrNull() }
        etag = link?.optString("etag")?.takeIf { it.isNotEmpty() }
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

    /** A scanned QR code. Null when it isn't a YDeck computer's; otherwise the computer's name, and the pairing is kept. */
    fun pair(context: Context, qr: String): String? {
        val (p, name) = Pairing.parse(qr) ?: return null
        pairing = p
        etag = null
        writeLink(context, p, name, etag = null, checkedAt = 0)
        _state.value = state(context).copy(paired = true, name = name)
        return name
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
        }.toString())
        // Notes the computer has stored are done; the rest wait.
        if (outbox.any { it.first.id in snapshot.applied }) writeOutbox(app, outbox.filterNot { it.first.id in snapshot.applied })
        etag = got.etag
        writeLink(app, p, snapshot.computer, got.etag, now)
        _state.value = State(paired = true, name = snapshot.computer, stateAt = snapshot.at, checkedAt = now, memory = snapshot.memory, live = snapshot.live, pending = outbox.map { it.first })
        DeckWidget().updateAll(app)
        Result.UPDATED
    }

    private fun touch(c: Context, p: Pairing, now: Long) {
        val s = state(c)
        writeLink(c, p, s.name, etag, now)
        _state.value = s.copy(checkedAt = now)
    }

    private fun writeLink(c: Context, p: Pairing, name: String, etag: String?, checkedAt: Long) {
        linkFile(c).writeText(JSONObject().put("secret", Pairing.b64(p.secret)).put("name", name).put("etag", etag ?: "").put("checkedAt", checkedAt).toString())
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
