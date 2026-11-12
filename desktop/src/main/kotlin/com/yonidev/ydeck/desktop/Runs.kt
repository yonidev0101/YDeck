package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Ran
import com.yonidev.ydeck.link.RunCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/*
 * Commands from the phone. A line typed there never runs on the phone's say alone: it waits here for someone at the
 * computer, who runs it once, approves it always (that exact line, in that project, nothing wider), or says no. A line
 * approved always runs at once, and the computer still says so. Each runs in a new terminal window, in sight, in the
 * project's folder, and the window stays open; the phone learns when it ends and its exit code. A request nobody
 * answers within [ASK_MS] is dropped. All of it only while it's on in Settings → Phone.
 */
object Runs {
    /** How long a command waits for someone at the computer. */
    const val ASK_MS = 10 * 60_000L

    /** A command waiting for approval here: the request, the project's name and folder. */
    data class Request(val r: RunCommand, val project: String, val dir: File, val since: Long = System.currentTimeMillis())

    private val _asking = MutableStateFlow<List<Request>>(emptyList())
    val asking: StateFlow<List<Request>> = _asking

    /** What became of the phone's latest commands (newest last), told back to it in every state. */
    val ran = CopyOnWriteArrayList<Ran>()

    /** A command approved always ran: said on the computer every time, so nothing runs unseen. */
    val ranAlone = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 4)

    private val exits = File(AppDirs.local, "runs").apply { mkdirs() }

    init {
        // Running commands: when one ends, its exit code arrives in its file. Unanswered requests expire.
        Thread({
            while (true) {
                runCatching { watch() }
                Thread.sleep(2_000)
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

    private fun start(q: Request) {
        val exit = File(exits, q.r.id.filter { it.isLetterOrDigit() || it == '-' }.take(64) + ".exit").apply { delete() }
        val title = "YDeck · ${q.project} · ${q.r.line}".take(80)
        val ok = Tools.runWatched(title, q.r.line, q.dir, exit)
        put(Ran(q.r.id, q.r.project, q.r.line, if (ok) Ran.RUNNING else Ran.NO, why = if (ok) "" else "start", at = System.currentTimeMillis()))
    }

    private fun watch() {
        val now = System.currentTimeMillis()
        _asking.value.filter { now - it.since > ASK_MS }.forEach { q ->
            _asking.update { l -> l.filterNot { it.r.id == q.r.id } }
            put(Ran(q.r.id, q.r.project, q.r.line, Ran.NO, why = "timeout", at = now))
        }
        ran.filter { it.state == Ran.RUNNING }.forEach { r ->
            val f = File(exits, r.id.filter { it.isLetterOrDigit() || it == '-' }.take(64) + ".exit")
            val text = runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val code = text.lines().first().trim().toIntOrNull() ?: 1
            f.delete()
            put(r.copy(state = Ran.DONE, code = code, at = now))
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
