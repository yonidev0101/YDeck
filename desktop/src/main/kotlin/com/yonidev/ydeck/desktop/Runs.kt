package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Ran
import com.yonidev.ydeck.link.RunCommand
import com.yonidev.ydeck.link.RunOut
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/*
 * Commands from the phone. A line typed there never runs on the phone's say alone: it waits here for someone at the
 * computer, who runs it once, approves it always (that exact line, in that project, nothing wider), or says no. A line
 * approved always runs at once, and the computer still says so. Each runs in the project's folder, as the phone chose:
 * in a new terminal window, in sight, which stays open, or inside YDeck with no window, its output in a small panel on
 * this screen. Either way its output goes to a log as it comes ([RunScript]); a phone that watches gets it at once
 * over the live line ([LiveLine]), and the state carries how it ended and its last lines. A request nobody answers
 * within [ASK_MS] is dropped. All of it only while it's on in Settings → Phone.
 */
object Runs {
    /** How long a command waits for someone at the computer. */
    const val ASK_MS = 10 * 60_000L

    /** At most this much of a command's output is kept (its end); the phone is told where it starts. */
    private const val KEEP = 200_000

    /** A command waiting for approval here: the request, the project's name and folder. */
    data class Request(val r: RunCommand, val project: String, val dir: File, val since: Long = System.currentTimeMillis())

    private val _asking = MutableStateFlow<List<Request>>(emptyList())
    val asking: StateFlow<List<Request>> = _asking

    /** What became of the phone's latest commands (newest last), told back to it in every state. */
    val ran = CopyOnWriteArrayList<Ran>()

    /** A command approved always ran: said on the computer every time, so nothing runs unseen. */
    val ranAlone = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 4)

    /** One run's output, as it comes. [base]: how many characters were dropped from its start. */
    class Output(val id: String, val project: String, val line: String, val window: Boolean, val files: RunScript.Files) {
        val text = StringBuilder()
        @Volatile var base = 0L
        @Volatile var done = false
        @Volatile var code = 0
        @Volatile var process: Process? = null
        val at = System.currentTimeMillis()
        val end: Long get() = base + text.length
        internal var pos = 0L
        internal val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
        internal var carry = ByteArray(0)
        internal var cr = false
        /** A copy for the screen: the end of it. */
        fun shown(max: Int = 20_000): String = synchronized(text) { text.takeLast(max).toString() }
    }

    private val outputs = ConcurrentHashMap<String, Output>()

    /** The command shown in the panel on this screen (one that runs inside YDeck), and its output as it grows. */
    data class Shown(val id: String, val project: String, val line: String, val text: String, val done: Boolean, val code: Int)

    private val _shown = MutableStateFlow<Shown?>(null)
    val shown: StateFlow<Shown?> = _shown

    init {
        // Running commands: their output read as it comes, a few times a second; when one ends, its exit code arrives
        // in its file. Unanswered requests expire.
        Thread({
            var n = 0
            while (true) {
                runCatching { read() }
                if (n++ % 8 == 0) runCatching { expire() }
                Thread.sleep(250)
            }
        }, "phone-runs").apply { isDaemon = true }.start()
    }

    /** The phone asks to run [r]: checked, then run (approved always), or held for someone here. */
    fun request(r: RunCommand) {
        if (ran.any { it.id == r.id }) return
        fun no(why: String) = put(Ran(r.id, r.project, r.line, Ran.NO, why = why, at = System.currentTimeMillis()))
        val settings = Settings.state.value
        if (!settings.phoneRun) return no("off")
        if (kotlin.math.abs(System.currentTimeMillis() - r.at) > RunCommand.FRESH_MS) return no("stale")
        val project = Repo.deck.value.byId(r.project) ?: return no("project")
        val dir = settings.localOf(project.id).dir?.takeIf { it.isDirectory } ?: return no("folder")
        if (r.line in settings.runOk[project.id].orEmpty()) {
            start(Request(r, project.name, dir))
            ranAlone.tryEmit(project.name to r.line)
            return
        }
        put(Ran(r.id, r.project, r.line, Ran.ASKING, at = System.currentTimeMillis()))
        _asking.update { it + Request(r, project.name, dir) }
    }

    /** Someone here ran it: [always] also approves that exact line in that project from now on. */
    fun approve(id: String, always: Boolean) {
        val q = _asking.value.firstOrNull { it.r.id == id } ?: return
        _asking.update { l -> l.filterNot { it.r.id == id } }
        if (always) Settings.update { s ->
            val now = s.runOk[q.r.project].orEmpty()
            s.copy(runOk = s.runOk + (q.r.project to (now.filterNot { it == q.r.line } + q.r.line).takeLast(30)))
        }
        start(q)
    }

    fun deny(id: String) {
        val q = _asking.value.firstOrNull { it.r.id == id } ?: return
        _asking.update { l -> l.filterNot { it.r.id == id } }
        put(Ran(q.r.id, q.r.project, q.r.line, Ran.NO, why = "denied", at = System.currentTimeMillis()))
    }

    /** No longer approved always: the next time it asks again. */
    fun forget(project: String, line: String) = Settings.update { s ->
        val left = s.runOk[project].orEmpty().filterNot { it == line }
        s.copy(runOk = if (left.isEmpty()) s.runOk - project else s.runOk + (project to left))
    }

    /** Stop button in the panel: the command and everything it started. */
    fun stop(id: String) {
        val p = outputs[id]?.process ?: return
        runCatching { p.descendants().forEach { it.destroyForcibly() } }
        p.destroyForcibly()
    }

    /** The panel was closed (the command, if still running, goes on). */
    fun hide() { _shown.value = null }

    private fun start(q: Request) {
        val shell = Shells.current
        val exe = Shells.exe(shell)?.path
        val f = RunScript.files(q.r.id, shell)
        val ok = exe != null && runCatching {
            val args = RunScript.args(shell, exe, q.r.line, f, stay = q.r.window)
            val out = Output(q.r.id, q.project, q.r.line, q.r.window, f)
            if (q.r.window) {
                if (!Tools.runInWindow("YDeck · ${q.project} · ${q.r.line}".take(80), args, q.dir)) return@runCatching false
            } else {
                // No window: nobody types into it, so whatever asks for input reads "no more input" and goes on.
                out.process = ProcessBuilder(args).directory(q.dir).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().also { it.outputStream.close() }
                _shown.value = Shown(out.id, out.project, out.line, "", false, 0)
            }
            outputs[q.r.id] = out
            while (outputs.size > 6) outputs.values.filter { it.done }.minByOrNull { it.at }?.let { outputs.remove(it.id) } ?: break
            true
        }.getOrDefault(false)
        put(Ran(q.r.id, q.r.project, q.r.line, if (ok) Ran.RUNNING else Ran.NO, why = if (ok) "" else "start", at = System.currentTimeMillis()))
        if (ok) LiveLine.wake()
    }

    /** What a phone that has [id]'s output up to [have] is missing, as messages for the live line (none: nothing new). */
    fun since(id: String, have: Long): List<RunOut> {
        val o = outputs[id] ?: return ran.firstOrNull { it.id == id && it.state == Ran.DONE }?.let { listOf(RunOut(id, 0, it.tail, true, it.code)) }.orEmpty()
        val (from, text) = synchronized(o.text) {
            val from = maxOf(have, o.base).coerceAtMost(o.end)
            from to o.text.substring((from - o.base).toInt())
        }
        if (text.isEmpty() && !o.done) return emptyList()
        if (text.isEmpty()) return listOf(RunOut(id, from, "", true, o.code))
        return text.chunked(RunOut.MAX).let { parts ->
            var at = from
            parts.mapIndexed { i, p -> RunOut(id, at, p, o.done && i == parts.lastIndex, o.code).also { at += p.length } }
        }
    }

    /** Commands that run now, or ended a moment ago (a phone may still be catching up). */
    fun live(): Boolean = outputs.values.any { !it.done } || ran.any { it.state == Ran.DONE && System.currentTimeMillis() - it.at < 60_000 }

    private fun read() {
        for (o in outputs.values) {
            if (o.done) continue
            val fresh = take(o)
            val exit = runCatching { o.files.exit.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            // A run with no window whose script never got to the end (stopped, or PowerShell failed): ended all the same.
            val gone = exit == null && o.process?.isAlive == false
            if (exit == null && !gone) {
                if (fresh.isNotEmpty()) { LiveLine.send(listOf(RunOut(o.id, o.end - fresh.length, fresh))); show(o) }
                continue
            }
            val more = fresh + take(o)
            o.code = exit?.lines()?.first()?.trim()?.toIntOrNull() ?: o.process?.exitValue()?.takeIf { it != 0 } ?: 1
            o.done = true
            LiveLine.send(listOf(RunOut(o.id, o.end - more.length, more, true, o.code)))
            show(o)
            val r = ran.firstOrNull { it.id == o.id }
            if (r != null) put(r.copy(state = Ran.DONE, code = o.code, at = System.currentTimeMillis(), tail = Ran.tailOf(o.shown(Ran.TAIL_MAX * 2))))
            RunScript.clean(o.files)
        }
    }

    /** The log's new bytes as text (a character cut in two waits for its other half), added to the run's output. */
    private fun take(o: Output): String {
        val f = o.files.log
        if (!f.isFile || f.length() <= o.pos) return ""
        val bytes = RandomAccessFile(f, "r").use { r ->
            r.seek(o.pos)
            val b = ByteArray(minOf(r.length() - o.pos, 1_000_000L).toInt())
            r.readFully(b)
            b
        }
        o.pos += bytes.size
        val all = o.carry + bytes
        val chars = CharBuffer.allocate(all.size + 1)
        val input = ByteBuffer.wrap(all)
        o.decoder.decode(input, chars, false)
        o.carry = all.copyOfRange(input.position(), all.size)
        chars.flip()
        val text = clean(chars.toString(), o)
        if (text.isEmpty()) return ""
        synchronized(o.text) {
            o.text.append(text)
            if (o.text.length > KEEP) {
                val cut = o.text.length - KEEP
                o.text.delete(0, cut)
                o.base += cut
            }
        }
        return text
    }

    /** Line ends as "\n", and no terminal colour codes. */
    private fun clean(s: String, o: Output): String {
        val b = StringBuilder(s.length)
        for (c in s) {
            if (o.cr && c == '\n') { o.cr = false; continue }
            o.cr = c == '\r'
            b.append(if (c == '\r') '\n' else c)
        }
        return b.toString().replace(ANSI, "")
    }

    private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    private fun show(o: Output) {
        val s = _shown.value ?: return
        if (s.id == o.id) _shown.value = Shown(o.id, o.project, o.line, o.shown(), o.done, o.code)
    }

    private fun expire() {
        val now = System.currentTimeMillis()
        _asking.value.filter { now - it.since > ASK_MS }.forEach { q ->
            _asking.update { l -> l.filterNot { it.r.id == q.r.id } }
            put(Ran(q.r.id, q.r.project, q.r.line, Ran.NO, why = "timeout", at = now))
        }
    }

    /** One command's news replaces its earlier news; the phone hears at once. */
    private fun put(r: Ran) {
        val i = ran.indexOfFirst { it.id == r.id }
        if (i >= 0) ran[i] = r else ran += r
        while (ran.size > 12) ran.removeAt(0)
        PhoneLink.nudge()
    }
}
