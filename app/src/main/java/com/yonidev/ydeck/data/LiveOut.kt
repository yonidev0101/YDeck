package com.yonidev.ydeck.data

import android.content.Context
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.RunOut
import com.yonidev.ydeck.link.RunWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/*
 * A command's output on the computer, live: while its page is open here, a line is held open to the computer through
 * the relay (a WebSocket, see RunOut in Snapshot.kt). The phone says which command it looks at and how much of its
 * output it has; the computer sends the rest, and then each new piece as it comes. Everything on the line is sealed
 * with the pairing's key. When the line drops (the screen went off, the network changed) it's opened again and the
 * phone asks for what it missed; the state still carries how the command ended and its last lines.
 */
object LiveOut {

    /** What came of one command so far: its output from character [base] on, and whether it ended, with its [code]. */
    data class Seen(val text: String, val base: Long, val done: Boolean, val code: Int) {
        val end: Long get() = base + text.length
    }

    /** The phone keeps at most this much of one command's output (its end). */
    private const val KEEP = 200_000

    private val _state = MutableStateFlow<Map<String, Seen>>(emptyMap())
    val state: StateFlow<Map<String, Seen>> = _state

    private val watching = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).build() }
    @Volatile private var ws: WebSocket? = null
    @Volatile private var keeping: Job? = null

    /** The page of command [id] opened: the line opens (if it isn't), and the computer is asked for what's missing. */
    fun watch(context: Context, id: String) {
        Computer.state(context.applicationContext)
        watching += id
        if (ws == null) open() else ask(id)
        if (keeping?.isActive != true) keeping = scope.launch {
            // Asked again now and then: a message lost while the computer's side was reconnecting comes again.
            var n = 0
            while (isActive && watching.isNotEmpty()) {
                delay(2_000)
                if (ws == null) open()
                else if (++n % 5 == 0) watching.filter { _state.value[it]?.done != true }.forEach(::ask)
            }
        }
    }

    /** Its page closed: when nothing is watched any more, the line closes. */
    fun unwatch(id: String) {
        watching -= id
        if (watching.isEmpty()) { ws?.close(1000, null); ws = null; keeping?.cancel() }
    }

    private fun open() {
        val p = Computer.pairingNow ?: return
        val relay = Relay(p)
        val req = Request.Builder().url(relay.liveUrl("phone")).header("Authorization", relay.authorization).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { watching.forEach(::ask) }

            // The computer joined (or joined again): it doesn't know what's watched here yet.
            override fun onMessage(webSocket: WebSocket, text: String) { if (text == "hello:computer") watching.forEach(::ask) }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Only what opens with our key and is a command's output; anything else is dropped unread.
                val out = runCatching { RunOut.decode(p.open("out", bytes.toByteArray())) }.getOrNull() ?: return
                if (out.id in watching) add(out)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (ws === webSocket) ws = null }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (ws === webSocket) ws = null }
        })
    }

    private fun ask(id: String) {
        val p = Computer.pairingNow ?: return
        val have = _state.value[id]?.end ?: 0L
        ws?.send(p.seal("watch", RunWatch(id, have).encode()).toByteString())
    }

    /** A piece of output onto what came before: what's already here is skipped; after a gap (the computer kept only the
     *  end of a long output) it starts afresh from there. */
    internal fun add(o: RunOut) = _state.update { m ->
        val s = m[o.id]
        val n = if (s == null || o.from > s.end) Seen(o.text, o.from, o.done, o.code)
        else {
            val text = s.text + o.text.drop((s.end - o.from).toInt().coerceAtMost(o.text.length))
            val cut = (text.length - KEEP).coerceAtLeast(0)
            Seen(text.substring(cut), s.base + cut, s.done || o.done, if (o.done) o.code else s.code)
        }
        (m + (o.id to n)).entries.toList().takeLast(6).associate { it.key to it.value }
    }
}

/** Where a command from the phone runs on the computer, as chosen last time: inside YDeck (no window) or in a new window. */
object RunChoice {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("run", Context.MODE_PRIVATE)
    fun window(c: Context): Boolean = prefs(c).getBoolean("window", false)
    fun setWindow(c: Context, window: Boolean) = prefs(c).edit().putBoolean("window", window).apply()
}
