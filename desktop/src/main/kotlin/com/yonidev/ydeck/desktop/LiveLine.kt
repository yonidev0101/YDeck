package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.RunOut
import com.yonidev.ydeck.link.RunWatch
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/*
 * The live line to the phone: a WebSocket through the relay (relay/src/index.js, /live), open only while a command
 * from the phone runs or ended a moment ago. A command's output goes on it as it comes, sealed ("out"); the phone
 * says which command it looks at and how much it has ("watch"), and gets the rest. Nothing listens here: the
 * computer reaches out, like for everything else. When the line drops it's opened again; the state still carries
 * how each command ended and its last lines, so nothing depends on it.
 */
object LiveLine {
    private val client: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }
    @Volatile private var ws: WebSocket? = null
    @Volatile private var on: Pairing? = null
    @Volatile private var connecting = false
    private val wake = Object()
    /** One message at a time on the socket (it allows no more). */
    private val sending = Object()

    init {
        Thread({
            var pinged = 0L
            while (true) {
                runCatching {
                    val p = PhoneLink.pairingNow
                    val want = p != null && Runs.live()
                    val open = ws
                    when {
                        open != null && (!want || on !== p) -> close()
                        want && open == null && !connecting -> connect(p!!)
                        // Keeps the line from being cut as idle on the way.
                        open != null && System.currentTimeMillis() - pinged > 25_000 -> {
                            pinged = System.currentTimeMillis()
                            synchronized(sending) { open.sendText("ping", true).get(10, TimeUnit.SECONDS) }
                        }
                    }
                }
                synchronized(wake) { wake.wait(3_000) }
            }
        }, "phone-live").apply { isDaemon = true }.start()
    }

    /** A command started: open the line now, not in a few seconds. */
    fun wake() = synchronized(wake) { wake.notifyAll() }

    /** Output for the phone, sealed. Dropped when the line is down: the phone asks again for what it missed. */
    fun send(outs: List<RunOut>) {
        val w = ws ?: return
        val p = on ?: return
        synchronized(sending) {
            for (o in outs) runCatching { w.sendBinary(ByteBuffer.wrap(p.seal("out", o.encode())), true).get(10, TimeUnit.SECONDS) }
        }
    }

    private fun connect(p: Pairing) {
        connecting = true
        val relay = Relay(p)
        client.newWebSocketBuilder().header("Authorization", relay.authorization).connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI(relay.liveUrl("computer")), Listener(p))
            .whenComplete { w, e ->
                connecting = false
                if (e == null && w != null) { on = p; ws = w } else Thread.sleep(2_000)
            }
    }

    private fun close() {
        val w = ws ?: return
        ws = null
        synchronized(sending) { runCatching { w.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS) } }
    }

    private class Listener(val p: Pairing) : WebSocket.Listener {
        private val buf = ByteArrayOutputStream()

        override fun onBinary(w: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
            val b = ByteArray(data.remaining()); data.get(b); buf.write(b)
            if (last) {
                val sealed = buf.toByteArray(); buf.reset()
                // Only what opens with our key and is a "watch"; anything else is dropped unread.
                runCatching { RunWatch.decode(p.open("watch", sealed)) }.getOrNull()?.let { watch ->
                    Thread { send(Runs.since(watch.id, watch.have)) }.apply { isDaemon = true }.start()
                }
            }
            w.request(1)
            return null
        }

        override fun onText(w: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? { w.request(1); return null }

        override fun onClose(w: WebSocket, code: Int, reason: String): CompletionStage<*>? { if (ws === w) ws = null; return null }

        override fun onError(w: WebSocket, error: Throwable) { if (ws === w) ws = null }
    }
}
