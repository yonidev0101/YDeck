package com.yonidev.ydeck.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * The link between YDeck on the computer and YDeck on the phone, shared by both apps (plain JVM: javax.crypto and
 * HttpURLConnection exist on Windows and on Android alike).
 *
 * Pairing: the computer makes a random 32-byte secret and shows it in a QR code. The phone scans it. That is the only
 * place the secret ever travels. From it both sides derive, with HKDF-SHA256:
 *   box id  - which mailbox on the relay (16 bytes, hex)
 *   token   - proves to the relay that a call comes from one of the two (the relay keeps only its hash)
 *   key     - AES-256-GCM, seals everything that goes through the relay
 * The relay (relay/src/index.js) can neither read a box nor forge one. Only data goes through: the phone never
 * receives anything it runs, and the computer never runs anything from the phone.
 */

/** The relay. Outbound HTTPS from both sides; nothing listens on the computer. */
const val RELAY = "https://ydeck-relay.yonidev-cloud.workers.dev"

class Pairing(val secret: ByteArray) {
    init { require(secret.size == 32) }

    val boxId: String = hkdf("box").copyOf(16).joinToString("") { "%02x".format(it) }
    val token: String = b64(hkdf("token"))
    private val key = SecretKeySpec(hkdf("key"), "AES")

    /** What the QR code holds: `ydeck:pair?v=1&k=<secret>&n=<computer name>`. */
    fun qrText(computer: String): String = "ydeck:pair?v=1&k=${b64(secret)}&n=" + URLEncoder.encode(computer.take(40), "UTF-8")

    /** gzip, then AES-256-GCM. Layout: 1 (format) | 12-byte nonce | ciphertext+tag. [kind] is bound in, so a sealed
     *  state can't be replayed as something else. */
    fun seal(kind: String, plain: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(kind))
        val body = cipher.doFinal(gzip(plain))
        return byteArrayOf(1) + nonce + body
    }

    /** The opposite of [seal]; throws if the box was tampered with or sealed with another key. */
    fun open(kind: String, sealed: ByteArray): ByteArray {
        require(sealed.size > 13 && sealed[0] == 1.toByte()) { "unknown format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 1, 12))
        cipher.updateAAD(aad(kind))
        return gunzip(cipher.doFinal(sealed, 13, sealed.size - 13))
    }

    private fun aad(kind: String) = "ydeck/v1/$boxId/$kind".toByteArray()

    /** HKDF-SHA256 (RFC 5869) for one 32-byte output. */
    private fun hkdf(info: String): ByteArray {
        val prk = hmac("ydeck-relay-v1".toByteArray(), secret)
        return hmac(prk, "ydeck/$info".toByteArray() + byteArrayOf(1))
    }

    companion object {
        private val random = SecureRandom()

        fun create(): Pairing = Pairing(ByteArray(32).also { random.nextBytes(it) })

        /** Reads a scanned QR code; null when it isn't YDeck's. Returns the pairing and the computer's name. */
        fun parse(text: String): Pair<Pairing, String>? = runCatching {
            if (!text.startsWith("ydeck:pair?")) return null
            val q = text.substringAfter('?').split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
            if (q["v"] != "1") return null
            val secret = Base64.getUrlDecoder().decode(q["k"] ?: return null)
            Pairing(secret) to (q["n"] ?: "").ifBlank { "Computer" }
        }.getOrNull()

        fun b64(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

        private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
        private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
    }
}

/** The relay's four calls. Blocking: call from a background thread. */
class Relay(private val pairing: Pairing, private val base: String = RELAY) {

    class Got(val sealed: ByteArray?, val etag: String?)
    class Meta(val stateAt: Long?, val readAt: Long?)

    /** Replaces the box's state; the first call claims the box. Returns false when the relay refused or can't be reached. */
    fun putState(sealed: ByteArray): Boolean = call("PUT", "/state", sealed) { code, _ -> code == 200 } ?: false

    /** The sealed state, or [Got] with null bytes when unchanged since [etag] (304). Null: empty box, refused, or offline. */
    fun getState(etag: String?): Got? = call("GET", "/state", null, etag) { code, c ->
        when (code) {
            200 -> Got(c.inputStream.use { it.readBytes() }, c.getHeaderField("ETag"))
            304 -> Got(null, etag)
            else -> null
        }
    }

    fun meta(): Meta? = call("GET", "/meta", null) { code, c ->
        if (code != 200) return@call null
        val o = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        Meta(o.optLong("stateAt").takeIf { it > 0 }, o.optLong("readAt").takeIf { it > 0 })
    }

    /** The phone leaves a sealed note for the computer. */
    fun postNote(sealed: ByteArray): Boolean = call("POST", "/inbox", sealed) { code, _ -> code == 200 } ?: false

    /** The notes waiting for the computer: (relay's name for it, sealed bytes). Null when refused or offline. */
    fun notes(): List<Pair<String, ByteArray>>? = call("GET", "/inbox", null) { code, c ->
        if (code != 200) return@call null
        val a = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("notes") ?: org.json.JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optString("n") to java.util.Base64.getDecoder().decode(it.optString("sealed")) }
    }

    /** The computer took a note: off the relay. */
    fun dropNote(n: String): Boolean = call("DELETE", "/inbox/$n", null) { code, _ -> code == 200 } ?: false

    /** Unpair: empties the box. */
    fun delete(): Boolean = call("DELETE", "", null) { code, _ -> code == 200 } ?: false

    /** The last call's HTTP status, 0 when the relay couldn't be reached. 401: the box doesn't take our token (the
     *  computer unpaired, and the box was claimed again); 404 on getState: the computer hasn't sent anything yet. */
    var lastCode = 0
        private set

    private fun <T> call(method: String, path: String, body: ByteArray?, etag: String? = null, read: (Int, HttpURLConnection) -> T?): T? = runCatching {
        lastCode = 0
        val c = URI("$base/v1/box/${pairing.boxId}$path").toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.useCaches = false
            c.setRequestProperty("Authorization", "Bearer ${pairing.token}")
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            lastCode = code
            read(code, c)
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
