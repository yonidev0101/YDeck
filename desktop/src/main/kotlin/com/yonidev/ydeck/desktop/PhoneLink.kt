package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Launch
import com.yonidev.ydeck.link.Launched
import com.yonidev.ydeck.link.Live
import com.yonidev.ydeck.link.LiveCommit
import com.yonidev.ydeck.link.LiveFile
import com.yonidev.ydeck.link.LiveLoop
import com.yonidev.ydeck.link.LiveReceipt
import com.yonidev.ydeck.link.LiveSession
import com.yonidev.ydeck.link.MemDecision
import com.yonidev.ydeck.link.Note
import com.yonidev.ydeck.link.MemRelease
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/*
 * YDeck on the phone: the projects' memory, in your pocket.
 *
 * Every minute (and right after a change) it gathers the projects, each one's memory from its folder and what's live
 * (git, the agent, receipts, open loops), seals them with the pairing's key (shared/kotlin/.../Link.kt) and puts them
 * in the box on the relay when anything changed. Every 20 seconds it takes the notes the phone left (a decision, a next
 * step) and writes them into the project's files, where the next Claude session reads them. Notes are data: they are
 * stored, never run. The one request the phone can make is to open Claude Code in a project's folder ([launch]): a
 * closed list of ways, checked here, never a command line of its own. Nothing listens here; both sides only reach out.
 * The pairing's secret lives in %LOCALAPPDATA%\YDeck Data\phone.json, never in a backup or a project.
 */
object PhoneLink {

    data class State(
        val paired: Boolean = false,
        /** The secret was just made and the QR is on screen; becomes false once the phone has read the box. */
        val waiting: Boolean = false,
        val pairedAt: Long = 0,
        /** When the box last took our state, and when the phone last read it (from the relay). */
        val sentAt: Long? = null,
        val phoneReadAt: Long? = null,
        /** The last send failed (offline, or the relay is down): it's tried again next minute. */
        val offline: Boolean = false,
    )

    private val file = File(AppDirs.local, "phone.json")
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    @Volatile private var pairing: Pairing? = null
    @Volatile private var lastSent: String? = null
    /** The phone's notes stored lately (ids, newest last), told back to the phone in every state. */
    private val applied = java.util.concurrent.CopyOnWriteArrayList<String>()
    /** What became of the phone's latest launch requests (newest last), told back to it in every state. */
    private val launched = java.util.concurrent.CopyOnWriteArrayList<Launched>()
    /** The last commits of each folder, read again only when its last commit changes. */
    private val logs = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<GitCommit>>>()
    private val wake = Object()

    val computer: String get() = System.getenv("COMPUTERNAME")?.let { it.lowercase().replaceFirstChar(Char::uppercase) } ?: "Windows"

    /** The QR's text while pairing (or to pair a second time with the same phone). */
    fun qrText(): String? = pairing?.qrText(computer)

    fun start() {
        runCatching {
            val o = JSONObject(file.readText())
            pairing = Pairing(Base64.getUrlDecoder().decode(o.getString("secret")))
            _state.value = State(paired = true, pairedAt = o.optLong("pairedAt"))
            o.optJSONArray("applied")?.let { a -> (0 until a.length()).forEach { applied += a.getString(it) } }
            o.optJSONArray("launched")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.forEach { launched += Launched(it.optString("id"), it.optBoolean("ok"), it.optString("why")) } }
        }
        Thread({
            var last = 0L
            while (true) {
                runCatching { takeNotes() }
                // The state at once after a note (the phone sees it stored) or a nudge, else every minute.
                if (System.currentTimeMillis() - last >= 55_000 || pendingSend || _state.value.waiting) {
                    pendingSend = false; last = System.currentTimeMillis(); runCatching { tick() }
                }
                synchronized(wake) { wake.wait(if (_state.value.waiting) 3_000 else 20_000) }
            }
        }, "phone-link").apply { isDaemon = true }.start()
        // A change in the projects goes out within seconds, not at the next minute.
        Thread({
            var seen = Repo.deck.value
            while (true) {
                Thread.sleep(2_000)
                if (Repo.deck.value !== seen) { seen = Repo.deck.value; nudge() }
            }
        }, "phone-link-watch").apply { isDaemon = true }.start()
    }

    @Volatile private var pendingSend = false

    fun nudge() = synchronized(wake) { pendingSend = true; wake.notifyAll() }

    private fun save(p: Pairing, pairedAt: Long) {
        file.writeText(JSONObject().put("secret", Pairing.b64(p.secret)).put("pairedAt", pairedAt).put("applied", org.json.JSONArray(applied.takeLast(50)))
            .put("launched", org.json.JSONArray().apply { launched.takeLast(10).forEach { put(JSONObject().put("id", it.id).put("ok", it.ok).put("why", it.why)) } }).toString())
    }

    /**
     * The phone's notes: each one opened (a note that doesn't open with our key, or isn't a note, is dropped unread),
     * checked, and stored in its project's files. A next step goes to .ydeck.local.json (and the panel's note), a
     * decision to the top of .ydeck.json's decisions. Then it leaves the relay.
     */
    private fun takeNotes() {
        val p = pairing ?: return
        val relay = Relay(p)
        val waiting = relay.notes() ?: return
        if (waiting.isEmpty()) return
        var stored = false
        var asked = false
        for ((n, sealed) in waiting) {
            val note = runCatching { Note.decode(p.open("note", sealed)) }.getOrNull()
            if (note != null && note.id !in applied && store(note)) {
                applied += note.id
                stored = true
            }
            // Not a note: maybe a launch request (sealed apart, so neither passes for the other).
            if (note == null) runCatching { Launch.decode(p.open("launch", sealed)) }.getOrNull()?.let { l ->
                if (launched.none { it.id == l.id }) { launched += launch(l); asked = true }
            }
            relay.dropNote(n)
        }
        if (stored || asked) {
            while (applied.size > 50) applied.removeAt(0)
            while (launched.size > 10) launched.removeAt(0)
            save(p, _state.value.pairedAt)
            if (stored) ProjectFiles.sync()
            pendingSend = true
        }
    }

    /**
     * The phone asks to open Claude Code in a project. Only what [Launch] allows, only in the project's own folder,
     * only while it's on in Settings → Phone, and only when fresh: a request that waited on the relay while this
     * computer was off is dropped, so nothing opens by surprise later. A session to resume must be one of the
     * folder's. The answer goes back as a code the phone puts in words.
     */
    private fun launch(l: Launch): Launched {
        fun no(why: String) = Launched(l.id, false, why)
        if (!Settings.state.value.phoneLaunch) return no("off")
        if (Tools.claude == null) return no("claude")
        if (kotlin.math.abs(System.currentTimeMillis() - l.at) > Launch.FRESH_MS) return no("stale")
        val project = Repo.deck.value.byId(l.project) ?: return no("project")
        val dir = Settings.state.value.localOf(project.id).dir ?: return no("folder")
        val resume = when (l.mode) {
            // The folder's last conversation that a person had (`--continue` could pick up a handoff's `claude -p`).
            Launch.CONTINUE -> Claude.conversations(dir, 1).firstOrNull()?.id
            Launch.RESUME -> l.session.takeIf { Launch.isSessionId(it) && File(Claude.sessionsDir(dir), "$it.jsonl").isFile } ?: return no("session")
            else -> null
        }
        val ok = Tools.claudeIn(dir, project.name, resume, remote = project.name.takeIf { l.remote }, prompt = l.prompt.ifBlank { null })
        return if (ok) Launched(l.id, true) else no("start")
    }

    /** One note into its project. False when the project is gone (the note is dropped). */
    private fun store(note: Note): Boolean {
        val project = Repo.deck.value.byId(note.project) ?: return false
        val dir = Settings.state.value.localOf(project.id).dir
        when (note.kind) {
            Note.NEXT -> {
                if (dir != null) ProjectFiles.writeNext(dir, note.text)
                Settings.setNote(project.id, note.text)
            }
            // Done: the next step is cleared, unless it has changed since the phone saw it (then it stays).
            Note.DONE -> {
                val current = dir?.let(ProjectFiles::readLocal)?.next?.takeIf { it.isNotBlank() } ?: Settings.state.value.localOf(project.id).note
                if (note.text.isBlank() || current.trim() == note.text.trim()) {
                    if (dir != null) ProjectFiles.writeNext(dir, "")
                    Settings.setNote(project.id, "")
                }
            }
            Note.DECISION -> {
                dir ?: return false
                val date = java.time.Instant.ofEpochMilli(note.at.takeIf { it > 0 } ?: System.currentTimeMillis())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                val now = ProjectFiles.readShared(dir)?.decisions.orEmpty()
                if (now.none { it.text == note.text }) ProjectFiles.writeDecisions(dir, listOf(Decision(note.text, date)) + now)
            }
        }
        return true
    }

    /** A new pairing: a new secret (any earlier phone is cut off), saved, sent at once. The QR shows it. */
    fun pair() {
        val old = pairing
        val p = Pairing.create()
        applied.clear()
        launched.clear()
        save(p, System.currentTimeMillis())
        pairing = p
        lastSent = null
        _state.value = State(paired = true, waiting = true, pairedAt = System.currentTimeMillis())
        if (old != null) emptyBox(old)
        nudge()
    }

    /** "Unpair phone": the box on the relay is emptied and the secret forgotten. The phone keeps what it last saw. */
    fun unpair() {
        val old = pairing ?: return
        pairing = null
        lastSent = null
        file.delete()
        _state.value = State()
        emptyBox(old)
    }

    /** Empties a box we no longer use; a few tries, since a network blip shouldn't leave it behind (it would still
     *  expire on its own after 60 days, and stays sealed). */
    private fun emptyBox(p: Pairing) = Thread {
        for (attempt in 0 until 5) { if (Relay(p).delete()) break; Thread.sleep(5_000L * (attempt + 1)) }
    }.apply { isDaemon = true }.start()

    /** The QR was closed before the phone scanned it: stays paired (scan later from Settings), just stops hurrying. */
    fun stopWaiting() { _state.value = _state.value.copy(waiting = false) }

    private fun tick() {
        val p = pairing ?: return
        val relay = Relay(p)
        val snapshot = snapshot()
        val plain = snapshot.encode()
        // What changed, without the time it was made.
        val hash = sha(Snapshot(0, snapshot.computer, snapshot.deck, snapshot.memory, snapshot.live, snapshot.applied, snapshot.launch, snapshot.launched).encode())
        var s = _state.value
        if (hash != lastSent) {
            if (relay.putState(p.seal("state", plain))) {
                lastSent = hash
                s = s.copy(sentAt = System.currentTimeMillis(), offline = false)
            } else s = s.copy(offline = true)
        }
        relay.meta()?.let { m ->
            s = s.copy(phoneReadAt = m.readAt, offline = false)
            if (s.waiting && m.readAt != null && m.readAt!! >= s.pairedAt) s = s.copy(waiting = false)
        }
        if (pairing === p) _state.value = s
    }

    /** The projects (with logos) and, for each one with a folder here, its memory. */
    fun snapshot(): Snapshot {
        val deck = Repo.deck.value
        val settings = Settings.state.value
        val memory = deck.projects.associate { p ->
            val local = settings.localOf(p.id)
            val dir = local.dir
            val shared = dir?.let(ProjectFiles::readShared)
            val mine = dir?.let(ProjectFiles::readLocal)
            p.id to Memory(
                summary = shared?.summary.orEmpty(),
                now = mine?.now.orEmpty(),
                next = mine?.next?.takeIf { it.isNotBlank() } ?: local.note,
                done = mine?.handoff?.done.orEmpty(),
                doneAt = mine?.handoff?.at ?: 0,
                agent = mine?.handoff?.agent.orEmpty(),
                decisions = shared?.decisions.orEmpty().take(30).map { MemDecision(it.text, it.date) },
                releases = shared?.releases.orEmpty().take(10).map { MemRelease(it.version, it.date, it.note) },
                folder = dir?.name.orEmpty(),
            )
        }
        val live = deck.projects.mapNotNull { p -> settings.localOf(p.id).folder?.let { p.id to live(it) } }.toMap()
        return Snapshot(System.currentTimeMillis(), computer, JSONObject(Repo.exportJson(deck)), memory, live, applied.toList(),
            launch = settings.phoneLaunch && Tools.claude != null, launched = launched.toList())
    }

    /** What the project's window shows here: git, the agent, the latest receipts, open loops, the dev server. */
    private fun live(folder: String): Live {
        val git = Git.state.value[folder]
        val pulse = Pulses.state.value[folder]
        val receipts = Receipts.state.value[folder].orEmpty()
        val session = ClaudeLink.statusIn(folder, ClaudeLink.sessions.value)
        val commits = if (git == null || git.lastAt == 0L) emptyList() else logs[folder]?.takeIf { it.first == git.lastAt }?.second
            ?: GitOps.log(java.io.File(folder), 8).also { logs[folder] = git.lastAt to it }
        fun files(list: List<GitChange>) = list.take(60).map { LiveFile(it.code, it.path) }
        return Live(
            hasGit = git != null,
            branch = git?.branch.orEmpty(), changes = git?.changes ?: 0, ahead = git?.ahead ?: 0, behind = git?.behind ?: 0,
            upstream = git?.upstream.orEmpty(), web = git?.web.orEmpty(), lastAt = git?.lastAt ?: 0, lastSubject = git?.lastSubject.orEmpty(),
            files = files(git?.files.orEmpty()),
            commits = commits.map { LiveCommit(it.hash, it.at, it.author, it.subject) },
            agentMode = session?.mode?.name.orEmpty(), agentSince = session?.since ?: 0, agentDetail = session?.detail.orEmpty().take(200),
            agent = session?.agent?.label.orEmpty(),
            receipts = receipts.take(6).map { r -> LiveReceipt(r.title.take(200), r.agent.label, r.startAt, r.endAt, files(r.files), r.rewound != null) },
            loops = Loops.of(git, pulse, session, receipts).map { LiveLoop(it.kind.name, it.title, it.detail) },
            ports = pulse?.ports.orEmpty(), missingEnv = pulse?.missingEnv.orEmpty(), version = pulse?.version.orEmpty(),
            sessions = Claude.conversations(java.io.File(folder)).map { LiveSession(it.id, it.title, it.at) },
        )
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
