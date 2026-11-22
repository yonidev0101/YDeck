package com.yonidev.ydeck.link

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/*
 * A project's files, looked at from the phone (read only). The phone leaves a sealed "files" note on the relay when
 * its Files page opens ([FileWake]): the computer then holds the live line open for a while. On the line the phone
 * asks ([FileAsk], sealed "fileask") for a folder's list or a piece of a file, and the computer answers
 * ([FilePart], sealed "filepart"), one piece at a time, each small enough for the line. Paths are always inside the
 * project's folder, with "/" between names, "" being the folder itself; the computer checks every one.
 */

/** "The Files page is open": sent through the inbox, sealed "files". */
data class FileWake(val at: Long) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "files").put("at", at).toString().toByteArray()

    companion object {
        fun decode(bytes: ByteArray): FileWake? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "files") return null
            FileWake(o.optLong("at"))
        }.getOrNull()
    }
}

/**
 * One ask: [LIST] a folder, [READ] a file from byte [from] on, [IMAGE] a picture made small enough to send,
 * [HISTORY] the commits that changed a file, [DIFF] what commit [rev] changed in it (from byte [from] on), or [PUT]
 * a piece of a file's new content: bytes [from]–[from]+[data].size of [total], written once all came, and only if the
 * file is still the one the phone opened (its SHA-256 is [base]).
 */
data class FileAsk(
    val id: String,
    val project: String,
    val path: String,
    val what: String,
    val from: Long = 0,
    val rev: String = "",
    val base: String = "",
    val total: Long = 0,
    val data: ByteArray = ByteArray(0),
) {
    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "fileask").put("id", id).put("project", project)
        .put("path", path).put("what", what).put("from", from).put("rev", rev).put("base", base).put("total", total)
        .put("data", Base64.getEncoder().encodeToString(data)).toString().toByteArray()

    companion object {
        const val LIST = "list"
        const val READ = "read"
        const val IMAGE = "image"
        const val HISTORY = "history"
        const val DIFF = "diff"
        const val PUT = "put"
        private val WHATS = setOf(LIST, READ, IMAGE, HISTORY, DIFF, PUT)

        fun decode(bytes: ByteArray): FileAsk? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "fileask") return null
            val what = o.optString("what").takeIf { it in WHATS } ?: return null
            FileAsk(o.optString("id").take(64), o.optString("project").take(64), o.optString("path").take(1000), what, o.optLong("from").coerceAtLeast(0),
                o.optString("rev").take(64), o.optString("base").take(64), o.optLong("total").coerceAtLeast(0),
                Base64.getDecoder().decode(o.optString("data")))
        }.getOrNull()

        /** The SHA-256 of a file's bytes, in hex: what [base] holds. */
        fun hash(b: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}

/** A commit that changed a file. */
data class FileCommit(val hash: String, val at: Long, val author: String, val subject: String)

/** One thing in a folder: a folder ([dir]) or a file, its size in bytes and when it last changed. */
data class FileEntry(val name: String, val dir: Boolean, val size: Long, val at: Long)

/**
 * The answer to [FileAsk] [id]: a folder's [entries], or bytes [from]–[from]+[data].size of [total]. An [error] in
 * words when it can't be given ("off": looking at files is off on the computer).
 */
data class FilePart(
    val id: String,
    val path: String,
    val what: String,
    val entries: List<FileEntry> = emptyList(),
    val data: ByteArray = ByteArray(0),
    val from: Long = 0,
    val total: Long = 0,
    val error: String = "",
    /** [FileAsk.HISTORY]: the commits, newest first. */
    val commits: List<FileCommit> = emptyList(),
    /** [FileAsk.PUT]: the file's SHA-256 now, once written. */
    val hash: String = "",
) {
    val done: Boolean get() = error.isNotEmpty() || what == FileAsk.LIST || what == FileAsk.HISTORY || from + data.size >= total

    fun encode(): ByteArray = JSONObject().put("app", "YDeck").put("kind", "filepart").put("id", id).put("path", path).put("what", what)
        .put("entries", JSONArray().apply { entries.forEach { put(JSONObject().put("n", it.name).put("d", it.dir).put("s", it.size).put("t", it.at)) } })
        .put("data", Base64.getEncoder().encodeToString(data)).put("from", from).put("total", total).put("error", error)
        .put("commits", JSONArray().apply { commits.forEach { put(JSONObject().put("h", it.hash).put("t", it.at).put("a", it.author).put("s", it.subject)) } })
        .put("hash", hash)
        .toString().toByteArray()

    companion object {
        /** The most bytes of a file in one piece: with base64 and sealing it stays under the relay's 64 KB a message. */
        const val PIECE = 36_000
        /** The most of a file the phone takes. */
        const val MAX_FILE = 20L * 1024 * 1024

        fun decode(bytes: ByteArray): FilePart? = runCatching {
            val o = JSONObject(String(bytes))
            if (o.optString("app") != "YDeck" || o.optString("kind") != "filepart") return null
            val a = o.optJSONArray("entries") ?: JSONArray()
            val cs = o.optJSONArray("commits") ?: JSONArray()
            FilePart(o.optString("id"), o.optString("path"), o.optString("what"),
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { FileEntry(it.optString("n"), it.optBoolean("d"), it.optLong("s"), it.optLong("t")) },
                Base64.getDecoder().decode(o.optString("data")), o.optLong("from"), o.optLong("total"), o.optString("error"),
                (0 until cs.length()).mapNotNull { cs.optJSONObject(it) }.map { FileCommit(it.optString("h"), it.optLong("t"), it.optString("a"), it.optString("s")) },
                o.optString("hash"))
        }.getOrNull()
    }
}
