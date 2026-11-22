package com.yonidev.ydeck.data

import android.content.Context
import com.yonidev.ydeck.link.FileAsk
import com.yonidev.ydeck.link.FilePart
import com.yonidev.ydeck.link.FileWake
import com.yonidev.ydeck.link.Relay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/*
 * A project's files on the computer, read only (see Files.kt in shared). While the Files page is open, a line is held
 * open to the computer through the relay; opening the page also leaves a note in the inbox, which tells the computer
 * to join the line (it looks every 20 seconds, so the first answer may take that long). Each ask waits for its answer,
 * and is sent again when the computer joins (again) or nothing came back.
 */
object FilesLine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).build() }
    @Volatile private var ws: WebSocket? = null
    @Volatile private var users = 0
    private val waiting = ConcurrentHashMap<String, Pair<FileAsk, CompletableDeferred<FilePart>>>()

    /** The computer is on the line now (it answered, or said hello). */
    private val _here = MutableStateFlow(false)
    val here: StateFlow<Boolean> = _here

    /** The Files page opened. */
    fun start(context: Context) {
        Computer.state(context.applicationContext)
        users++
        wake()
        if (ws == null) open()
    }

    @Volatile private var wokeAt = 0L

    /** Tells the computer, through the inbox, to join the line (at most every half a minute). */
    private fun wake() {
        val now = System.currentTimeMillis()
        if (now - wokeAt < 30_000) return
        wokeAt = now
        val p = Computer.pairingNow ?: return
        scope.launch { runCatching { Relay(p).postNote(p.seal("files", FileWake(now).encode())) } }
    }

    /** It closed: when no page uses the line, it closes. */
    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) { ws?.close(1000, null); ws = null; _here.value = false }
    }

    /** Asks and waits; null when nothing came back in [seconds] (the computer is off, or looking at files is off there). */
    suspend fun ask(project: String, path: String, what: String, from: Long = 0, seconds: Long = 45): FilePart? {
        val a = FileAsk(UUID.randomUUID().toString().take(12), project, path, what, from)
        val d = CompletableDeferred<FilePart>()
        waiting[a.id] = a to d
        try {
            return withTimeoutOrNull(seconds * 1000) {
                var tries = 0
                var got: FilePart? = null
                while (got == null) {
                    if (ws == null) open()
                    send(a)
                    // Sent again now and then: one lost while the computer was joining comes again.
                    got = withTimeoutOrNull(if (_here.value) 8_000L else 4_000L) { d.await() }
                    // No answer: the computer may have left the line (nothing was asked for a while), so it's called again.
                    if (got == null && ++tries == 2) wake()
                }
                got
            }
        } finally {
            waiting.remove(a.id)
        }
    }

    /** A whole file (or picture), piece by piece; [progress] gets how much came, of how much. Null with no answer. */
    suspend fun whole(project: String, path: String, what: String, progress: (Long, Long) -> Unit = { _, _ -> }): Pair<ByteArray?, String>? {
        val out = java.io.ByteArrayOutputStream()
        var from = 0L
        while (true) {
            val part = ask(project, path, what, from) ?: return null
            if (part.error.isNotEmpty()) return null to part.error
            if (part.from != from) continue
            withContext(Dispatchers.IO) { out.write(part.data) }
            from += part.data.size
            progress(from, part.total)
            if (part.done || part.data.isEmpty()) return out.toByteArray() to ""
        }
    }

    private fun send(a: FileAsk) {
        val p = Computer.pairingNow ?: return
        ws?.send(p.seal("fileask", a.encode()).toByteString())
    }

    private fun open() {
        val p = Computer.pairingNow ?: return
        val relay = Relay(p)
        val req = Request.Builder().url(relay.liveUrl("phone")).header("Authorization", relay.authorization).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { waiting.values.forEach { send(it.first) } }

            // The computer joined (or joined again): what waits is asked again.
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "hello:computer") { _here.value = true; waiting.values.forEach { send(it.first) } }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Only what opens with our key and is an answer about files; anything else (a command's output) is not for here.
                val part = runCatching { FilePart.decode(p.open("filepart", bytes.toByteArray())) }.getOrNull() ?: return
                _here.value = true
                waiting[part.id]?.second?.complete(part)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (ws === webSocket) ws = null }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (ws === webSocket) ws = null }
        })
    }
}
