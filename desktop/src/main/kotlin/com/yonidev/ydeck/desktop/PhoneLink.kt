package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Answer
import com.yonidev.ydeck.link.Ask
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
import com.yonidev.ydeck.link.NewKey
import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Reply
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
 * stored, never run. The phone can ask for two things, each only once it's on in Settings: to open Claude Code in a
 * project's folder ([launch]), a closed list of ways, checked here, never a command line of its own; and to answer a
 * question with Ask YDeck ([ask]), which only reads. Nothing listens here; both sides only reach out.
 * The pairing's secret lives in %LOCALAPPDATA%\YDeck Data\phone.json, never in a backup or a project.
 */
object PhoneLink {

    data class State(
        val paired: Boolean = false,
        /** A one-time code on screen (changed every two minutes), waiting for a phone to send its key through it. */
        val qr: String? = null,
        /** When the code on screen stops working. */
        val qrUntil: Long = 0,
        /** Just paired: becomes false once the phone has read the box. */
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
    /** The pairing now, for the live line ([LiveLine]); null when no phone is paired. */
    val pairingNow: Pairing? get() = pairing
    @Volatile private var lastSent: String? = null
    @Volatile private var lastSentAt = 0L
    /** The state goes out at least this often; a phone that hasn't heard for [com.yonidev.ydeck.link.Live.FRESH_MS] stops trusting it. */
    private const val HEARTBEAT_MS = 5L * 60 * 1000
    /** How many answers the state carries. */
    private const val ANSWERS = 6
    /** The phone's notes stored lately (ids, newest last), told back to the phone in every state. */
    private val applied = java.util.concurrent.CopyOnWriteArrayList<String>()
    /** What became of the phone's latest launch requests (newest last), told back to it in every state. */
    private val launched = java.util.concurrent.CopyOnWriteArrayList<Launched>()
    /** The answers to the phone's latest questions (newest last), and the one being worked on, told back in every state. */
    private val answers = java.util.concurrent.CopyOnWriteArrayList<Answer>()
    /** What became of the phone's latest replies to waiting sessions (newest last), told back in every state. */
    private val replied = java.util.concurrent.CopyOnWriteArrayList<Launched>()
    /** The phone's conversations: its name for one → Claude Code's session and the folder it runs in, so a question
     *  goes on where the last one was. */
    private val chats = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    /** One question at a time, apart from the link's own thread (an answer can take a minute). */
    private val asking = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "phone-ask").apply { isDaemon = true } }
    /** The last commits of each folder, read again only when its last commit changes. */
    private val logs = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<GitCommit>>>()
    private val wake = Object()

    val computer: String get() = System.getenv("COMPUTERNAME")?.let { it.lowercase().replaceFirstChar(Char::uppercase) } ?: "Windows"

    /** The one-time code on screen, and whether its box on the relay is claimed yet (a phone can only post to a
     *  claimed box). Never saved: closing YDeck ends it. */
    @Volatile private var offer: Pairing? = null
    @Volatile private var offerClaimed = false

    /** A project's name, each time the phone opened Claude Code here (a Windows notification, see Main). */
    val opened = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun start() {
        runCatching {
            val o = JSONObject(file.readText())
            pairing = Pairing(Base64.getUrlDecoder().decode(o.getString("secret")))
            _state.value = State(paired = true, pairedAt = o.optLong("pairedAt"))
            o.optJSONArray("applied")?.let { a -> (0 until a.length()).forEach { applied += a.getString(it) } }
            o.optJSONArray("launched")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.forEach { launched += Launched(it.optString("id"), it.optBoolean("ok"), it.optString("why")) } }
            // An answer cut off when YDeck closed is told as such.
            o.optJSONArray("answers")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map(Answer::of)
                .forEach { answers += if (it.done) it else it.copy(done = true, step = "", error = tr("YDeck closed before the answer came. Ask again.", "YDeck נסגר לפני שהתשובה הגיעה. אפשר לשאול שוב.")) } }
            o.optJSONObject("chats")?.let { c -> c.keys().forEach { k -> c.optJSONObject(k)?.let { v -> chats[k] = v.optString("session") to v.optString("home") } } }
        }
        Thread({
            var last = 0L
            while (true) {
                runCatching { takeOffer() }
                runCatching { takeNotes() }
                // The state at once after a note (the phone sees it stored) or a nudge, else every minute.
                if (System.currentTimeMillis() - last >= 55_000 || pendingSend || _state.value.waiting) {
                    pendingSend = false; last = System.currentTimeMillis(); runCatching { tick() }
                }
                val hurry = _state.value.waiting || offer != null
                synchronized(wake) { wake.wait(if (hurry) 3_000 else 20_000) }
            }
        }, "phone-link").apply { isDaemon = true }.start()
        // A change in the projects, or in what Claude is doing, goes out within seconds, not at the next minute.
        Thread({
            var seen = Repo.deck.value
            var claude = ClaudeLink.sessions.value
            var waiting = SessionAsks.waiting.value
            var asking = Runs.asking.value
            while (true) {
                Thread.sleep(2_000)
                if (SessionAsks.waiting.value !== waiting) { waiting = SessionAsks.waiting.value; nudge() }
                if (Runs.asking.value !== asking) { asking = Runs.asking.value; nudge() }
                if (Repo.deck.value !== seen) { seen = Repo.deck.value; nudge() }
                if (ClaudeLink.sessions.value !== claude) { claude = ClaudeLink.sessions.value; nudge() }
            }
        }, "phone-link-watch").apply { isDaemon = true }.start()
    }

    @Volatile private var pendingSend = false

    fun nudge() = synchronized(wake) { pendingSend = true; wake.notifyAll() }

    private fun save(p: Pairing, pairedAt: Long) {
        file.writeText(JSONObject().put("secret", Pairing.b64(p.secret)).put("pairedAt", pairedAt).put("applied", org.json.JSONArray(applied.takeLast(50)))
            .put("launched", org.json.JSONArray().apply { launched.takeLast(10).forEach { put(JSONObject().put("id", it.id).put("ok", it.ok).put("why", it.why)) } })
            .put("answers", org.json.JSONArray().apply { answers.takeLast(ANSWERS).forEach { put(it.toJson()) } })
            .put("chats", JSONObject().apply { chats.forEach { (k, v) -> put(k, JSONObject().put("session", v.first).put("home", v.second)) } }).toString())
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
            // A phone paired with an older code moves to a secret that never was on a screen: the rest of this box
            // goes with the old secret (the phone sends again what it hasn't seen stored).
            val key = runCatching { NewKey.decode(p.open("rekey", sealed)) }.getOrNull()
            if (key != null) { adopt(key, samePhone = true); return }
            val note = runCatching { Note.decode(p.open("note", sealed)) }.getOrNull()
            if (note != null && note.id !in applied && store(note)) {
                applied += note.id
                stored = true
            }
            // Not a note: maybe a launch request (sealed apart, so neither passes for the other).
            if (note == null) runCatching { Launch.decode(p.open("launch", sealed)) }.getOrNull()?.let { l ->
                if (launched.none { it.id == l.id }) { launched += launch(l); asked = true }
            }
            // Or a reply to a waiting session (sealed as "reply").
            if (note == null) runCatching { Reply.decode(p.open("reply", sealed)) }.getOrNull()?.let { r ->
                if (replied.none { it.id == r.id }) { replied += reply(r); asked = true }
            }
            // Or a command line to run (sealed as "run"): it waits for approval here, see [Runs].
            if (note == null) runCatching { com.yonidev.ydeck.link.RunCommand.decode(p.open("run", sealed)) }.getOrNull()?.let { r -> Runs.request(r); asked = true }
            // Or a question (sealed as "ask").
            if (note == null) runCatching { Ask.decode(p.open("ask", sealed)) }.getOrNull()?.let { a ->
                if (answers.none { it.id == a.id }) { ask(a); asked = true }
            }
            relay.dropNote(n)
        }
        if (stored || asked) {
            while (applied.size > 50) applied.removeAt(0)
            while (launched.size > 10) launched.removeAt(0)
            while (answers.size > ANSWERS) answers.removeAt(0)
            while (replied.size > 10) replied.removeAt(0)
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
        if (ok) opened.tryEmit(project.name)
        return if (ok) Launched(l.id, true) else no("start")
    }

    /**
     * A question from the phone, answered by Ask YDeck as on this computer: Claude Code reads, never changes. Only while
     * it's on in Settings → Phone, and only when fresh. While Claude works, what it reads goes out every few seconds;
     * the answer goes out the moment it comes.
     */
    private fun ask(a: Ask) {
        fun no(why: String) { answers += Answer(a.id, a.chat, done = true, error = why) }
        if (!Settings.state.value.phoneAsk) return no("off")
        if (Tools.claude == null) return no("claude")
        if (kotlin.math.abs(System.currentTimeMillis() - a.at) > Ask.FRESH_MS) return no("stale")
        answers += Answer(a.id, a.chat)
        asking.execute {
            val start = System.currentTimeMillis()
            var reads = 0
            var told = 0L
            fun put(answer: Answer) { val i = answers.indexOfFirst { it.id == a.id }; if (i >= 0) answers[i] = answer }
            val focus = Repo.deck.value.byId(a.project)
            val before = chats[a.chat]?.takeIf { java.io.File(it.second).isDirectory }
            val home = before?.let { java.io.File(it.second) } ?: Assistant.homeOf(focus)
            val r = runCatching {
                Assistant.answer(a.question, focus, before?.first, home, phone = true,
                    onSession = { chats[a.chat] = it to home.path },
                    onStep = { step ->
                        reads++
                        put(Answer(a.id, a.chat, step = step.take(200), reads = reads))
                        if (System.currentTimeMillis() - told > 4_000) { told = System.currentTimeMillis(); nudge() }
                    },
                    onProcess = {})
            }
            val seconds = ((System.currentTimeMillis() - start) / 1000).toInt()
            put(r.fold({ Answer(a.id, a.chat, done = true, answer = it.take(Answer.MAX), reads = reads, seconds = seconds) },
                { Answer(a.id, a.chat, done = true, error = it.message ?: tr("Claude didn't answer", "Claude לא ענה"), reads = reads, seconds = seconds) }))
            // A conversation that never answered starts afresh next time.
            if (r.isFailure && chats[a.chat] != null && answers.none { it.chat == a.chat && it.answer.isNotEmpty() }) chats.remove(a.chat)
            while (chats.size > 5) chats.remove(chats.keys.first())
            pairing?.let { save(it, _state.value.pairedAt) }
            nudge()
        }
    }

    /**
     * The phone replies to a session waiting for the developer ([SessionAsks]): only while that's on in Settings →
     * Phone, only when fresh, and only to a question still waiting. Words for Claude, never a permission.
     */
    private fun reply(r: Reply): Launched {
        fun no(why: String) = Launched(r.id, false, why)
        if (!Settings.state.value.phoneReply) return no("off")
        if (kotlin.math.abs(System.currentTimeMillis() - r.at) > Reply.FRESH_MS) return no("stale")
        return if (SessionAsks.reply(r.waiting, SessionAsks.Answer(r.text, r.choices))) Launched(r.id, true) else no("gone")
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

    /**
     * Shows a one-time code. A phone that scans it sends the lasting secret it made ([takeOffer]); until then the
     * phone paired now (if any) stays paired. The code changes every two minutes, and works once.
     */
    fun pair() {
        val old = offer
        val o = Pairing.create()
        offer = o
        offerClaimed = false
        _state.update { it.copy(qr = o.qrText(computer), qrUntil = System.currentTimeMillis() + NewKey.CODE_MS) }
        if (old != null) emptyBox(old)
        nudge()
    }

    /** The code was closed: it stops working at once. */
    fun cancelPair() {
        val o = offer ?: return
        offer = null
        _state.update { it.copy(qr = null, qrUntil = 0) }
        emptyBox(o)
    }

    /** The one-time code's box: claimed (so a phone can post to it), changed when it expires, read for a phone's key. */
    private fun takeOffer() {
        val o = offer ?: return
        if (System.currentTimeMillis() >= _state.value.qrUntil) { if (offer === o) pair(); return }
        val relay = Relay(o)
        if (!offerClaimed) offerClaimed = relay.putState(o.seal("hello", "{}".toByteArray()))
        if (!offerClaimed) return
        for ((_, sealed) in relay.notes() ?: return) {
            val key = runCatching { NewKey.decode(o.open("pair", sealed)) }.getOrNull() ?: continue
            if (offer !== o) return
            offer = null
            emptyBox(o)
            adopt(key, samePhone = false)
            return
        }
    }

    /** The phone's own secret becomes the pairing; the earlier one's box is emptied (an earlier phone is cut off). */
    private fun adopt(key: NewKey, samePhone: Boolean) {
        val old = pairing
        val p = Pairing(key.secret)
        if (!samePhone) { applied.clear(); launched.clear(); answers.clear(); chats.clear(); replied.clear(); Runs.ran.clear() }
        val at = if (samePhone) _state.value.pairedAt else System.currentTimeMillis()
        save(p, at)
        pairing = p
        lastSent = null
        _state.update { if (samePhone) it.copy(paired = true) else State(paired = true, waiting = true, pairedAt = at) }
        if (old != null) emptyBox(old)
        nudge()
    }

    /** "Unpair phone": the box on the relay is emptied and the secret forgotten. The phone keeps what it last saw. */
    fun unpair() {
        cancelPair()
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

    private fun tick() {
        val p = pairing ?: return
        val relay = Relay(p)
        val snapshot = snapshot()
        val plain = snapshot.encode()
        // What changed, without the time it was made.
        val hash = sha(snapshot.at(0).encode())
        var sentAt: Long? = null
        var offline = false
        // Sent again now and then even when nothing changed, so the phone knows this is still current (Claude's state
        // shown there would otherwise stay as it was when the computer went to sleep).
        if (hash != lastSent || System.currentTimeMillis() - lastSentAt >= HEARTBEAT_MS) {
            if (relay.putState(p.seal("state", plain))) { UsageStats.mark(UsageStats.Feature.PHONE); lastSent = hash; lastSentAt = System.currentTimeMillis(); sentAt = lastSentAt } else offline = true
        }
        val m = relay.meta()
        // Only what this learned changes: a code put on screen meanwhile stays.
        if (pairing === p) _state.update { s ->
            var n = s.copy(sentAt = sentAt ?: s.sentAt, offline = if (m != null) false else offline || s.offline && sentAt == null)
            if (m != null) {
                n = n.copy(phoneReadAt = m.readAt)
                if (n.waiting && m.readAt != null && m.readAt!! >= n.pairedAt) n = n.copy(waiting = false)
            }
            n
        }
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
                left = mine?.left.orEmpty().take(30), ideas = mine?.ideas.orEmpty().take(30), doneItems = mine?.done.orEmpty(),
            )
        }
        val live = deck.projects.mapNotNull { p -> settings.localOf(p.id).folder?.let { p.id to live(it) } }.toMap()
        return Snapshot(System.currentTimeMillis(), computer, JSONObject(Repo.exportJson(deck)), memory, live, applied.toList(),
            launch = settings.phoneLaunch && Tools.claude != null, claude = Tools.claude != null, launched = launched.toList(), beat = HEARTBEAT_MS,
            ask = settings.phoneAsk && Tools.claude != null, answers = answers.toList(),
            waiting = SessionAsks.waiting.value, reply = settings.phoneReply, replied = replied.toList(),
            run = settings.phoneRun, ran = Runs.ran.toList(), runOk = if (settings.phoneRun) settings.runOk.filterKeys { k -> deck.projects.any { it.id == k } } else emptyMap())
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
            agent = session?.agent?.label.orEmpty(), agentRemote = session?.remote == true,
            agentOpen = ClaudeLink.openIn(folder, ClaudeLink.sessions.value),
            receipts = receipts.take(6).map { r -> LiveReceipt(r.title.take(200), r.agent.label, r.startAt, r.endAt, files(r.files), r.rewound != null) },
            loops = Loops.of(git, pulse, session, receipts).map { LiveLoop(it.kind.name, it.title, it.detail) },
            ports = pulse?.ports.orEmpty(), missingEnv = pulse?.missingEnv.orEmpty(), version = pulse?.version.orEmpty(),
            sessions = Claude.conversations(java.io.File(folder)).map { LiveSession(it.id, it.title, it.at) },
        )
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
