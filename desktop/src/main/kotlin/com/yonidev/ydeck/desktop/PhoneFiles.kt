package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.FileAsk
import com.yonidev.ydeck.link.FileCommit
import com.yonidev.ydeck.link.FileEntry
import com.yonidev.ydeck.link.FilePart
import java.io.File
import java.io.RandomAccessFile

/*
 * The phone looks at a project's files (shared/kotlin/.../Files.kt), and may save one it opened: only while it's on in Settings →
 * Phone, only inside the project's own folder, never .git or what looks like a secret. Nothing is run, made or deleted;
 * a file is written only when editing is on too ([put]). The live line ([LiveLine]) stays open for [WINDOW_MS] after
 * the phone last asked.
 */
object PhoneFiles {
    private const val WINDOW_MS = 10 * 60_000L
    /** A picture bigger than this is made smaller (and a JPEG) before it goes. */
    private const val IMAGE_RAW_MAX = 400_000L
    private const val IMAGE_SIDE = 1600
    private const val MAX_ENTRIES = 500

    @Volatile private var lastAt = 0L

    /** The line is wanted: on, and the phone asked lately. */
    fun wanted(): Boolean = Settings.state.value.phoneFiles && System.currentTimeMillis() - lastAt < WINDOW_MS

    /** The phone's Files page opened (its note in the inbox): open the line now. */
    fun woke() {
        if (!Settings.state.value.phoneFiles) return
        lastAt = System.currentTimeMillis()
        LiveLine.wake()
    }

    private val SECRET = listOf(
        Regex("^\\.env(\\..*)?$"), Regex(".*\\.(pem|key|p12|pfx|jks|keystore|ppk|kdbx|secret)$"), Regex("^id_(rsa|dsa|ecdsa|ed25519).*"),
        Regex("^(credentials|secrets?)(\\..*)?$"), Regex("^service-account.*\\.json$"), Regex("^\\.(npmrc|pypirc|netrc|git-credentials)$"),
        Regex("^(local|keystore|signing)\\.properties$"), Regex("^settings\\.local\\.json$"), Regex("^\\.git$"),
    )
    private val OK_ENV = Regex("^\\.env\\.(example|sample|template|defaults)$")

    /** Never shown nor sent: .git, and what looks like a secret. */
    fun hidden(name: String): Boolean {
        val n = name.lowercase()
        if (OK_ENV.matches(n)) return false
        return SECRET.any { it.matches(n) }
    }

    /** [path] ("a/b.txt", "" for the folder itself) inside [root], or null when it leads anywhere else or to a hidden one. */
    fun resolve(root: File, path: String): File? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." || it.contains('\\') || it.contains(':') || hidden(it) }) return null
        val base = root.canonicalFile
        val f = parts.fold(base) { d, n -> File(d, n) }.canonicalFile
        return f.takeIf { it == base || it.path.startsWith(base.path + File.separator) }
    }

    fun answer(a: FileAsk): FilePart {
        fun no(why: String) = FilePart(a.id, a.path, a.what, error = why)
        if (!Settings.state.value.phoneFiles) return no("off")
        lastAt = System.currentTimeMillis()
        val dir = Settings.state.value.localOf(a.project).dir ?: return no(tr("This project has no folder on the computer.", "לפרויקט הזה אין תיקייה במחשב."))
        val f = resolve(dir, a.path) ?: return no(tr("Not shown.", "לא מוצג."))
        if (!f.exists()) return no(tr("It's not there any more.", "זה כבר לא שם."))
        return runCatching {
            when (a.what) {
                FileAsk.LIST -> {
                    if (!f.isDirectory) return no(tr("Not a folder.", "זו לא תיקייה."))
                    val all = f.listFiles().orEmpty().filter { !hidden(it.name) }
                    val entries = all.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })).take(MAX_ENTRIES)
                        // A folder's size is how many things are in it (as shown: without the hidden ones).
                        .map { FileEntry(it.name, it.isDirectory, if (it.isDirectory) (it.list()?.count { n -> !hidden(n) } ?: 0).toLong() else it.length(), it.lastModified()) }
                    FilePart(a.id, a.path, a.what, entries = entries, total = all.size.toLong())
                }
                FileAsk.HISTORY -> {
                    if (!f.isFile) return no(tr("Not a file.", "זה לא קובץ."))
                    val rel = rel(dir, f)
                    val r = Git.run(dir, listOf("log", "--follow", "-n", "60", "--format=%h%x09%ct%x09%an%x09%s", "--", rel), errors = false)
                    if (!r.ok) return no(tr("No Git history here.", "אין כאן היסטוריית Git."))
                    val commits = r.out.lines().mapNotNull { l ->
                        val p = l.split('\t')
                        if (p.size < 4) null else FileCommit(p[0], (p[1].toLongOrNull() ?: 0) * 1000, p[2], p.drop(3).joinToString("\t"))
                    }
                    FilePart(a.id, a.path, a.what, commits = commits)
                }
                FileAsk.DIFF -> {
                    if (!f.isFile) return no(tr("Not a file.", "זה לא קובץ."))
                    if (!Regex("^[0-9a-f]{4,40}$").matches(a.rev)) return no(tr("No such commit.", "אין קומיט כזה."))
                    val all = cached("diff|" + dir.path + "|" + a.rev + "|" + a.path) {
                        // The file's path in that commit may differ (renamed since): --follow's names, the latest that matches.
                        val r = Git.run(dir, listOf("log", "--follow", "-p", "-n", "1", "--format=", "--no-color", "--no-ext-diff", a.rev, "--", rel(dir, f)), errors = false)
                        if (r.ok) r.out.toByteArray().let { if (it.size > 2_000_000) it.copyOf(2_000_000) else it } else null
                    } ?: return no(tr("Couldn't read that commit.", "לא הצלחתי לקרוא את הקומיט הזה."))
                    piece(a, all)
                }
                FileAsk.PUT -> put(a, f)
                FileAsk.IMAGE -> {
                    if (!f.isFile) return no(tr("Not a file.", "זה לא קובץ."))
                    piece(a, image(f) ?: return no(tr("This picture can't be shown.", "אי אפשר להציג את התמונה הזו.")))
                }
                else -> {
                    if (!f.isFile) return no(tr("Not a file.", "זה לא קובץ."))
                    if (f.length() > FilePart.MAX_FILE) return no(tr("Too big to send to the phone (over 20 MB).", "גדול מדי לשליחה לטלפון (מעל 20MB)."))
                    val total = f.length()
                    val from = a.from.coerceIn(0, total)
                    val n = minOf(FilePart.PIECE.toLong(), total - from).toInt()
                    val b = ByteArray(n)
                    RandomAccessFile(f, "r").use { r -> r.seek(from); r.readFully(b) }
                    FilePart(a.id, a.path, a.what, data = b, from = from, total = total)
                }
            }
        }.getOrElse { no(tr("Couldn't read it.", "לא הצלחתי לקרוא את זה.")) }
    }

    private fun rel(dir: File, f: File): String = f.canonicalFile.relativeTo(dir.canonicalFile).path.replace('\\', '/')

    /** Bytes made once for an ask that comes in pieces (a diff), kept for a few. */
    private val made = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ByteArray>?) = size > 8
    }
    private fun cached(key: String, make: () -> ByteArray?): ByteArray? {
        synchronized(made) { made[key]?.let { return it } }
        val b = make() ?: return null
        synchronized(made) { made[key] = b }
        return b
    }

    /** New content on its way from the phone, by the phone's name for the upload ([FileAsk.rev]). */
    private class Upload(val path: String, val base: String, val total: Long, val out: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream(), var at: Long = System.currentTimeMillis())
    private val uploads = java.util.concurrent.ConcurrentHashMap<String, Upload>()
    private const val MAX_EDIT = 2L * 1024 * 1024

    /**
     * A piece of a file's new content. Only an existing file (never a new one, never a hidden one), only while editing is
     * on, and written only when all of it came and the file is still the one the phone opened.
     */
    private fun put(a: FileAsk, f: File): FilePart {
        fun no(why: String) = FilePart(a.id, a.path, a.what, error = why)
        if (!Settings.state.value.phoneEdit) return no("editoff")
        if (!f.isFile) return no(tr("Only a file that's already there can be changed.", "אפשר לשנות רק קובץ שכבר קיים."))
        if (a.total > MAX_EDIT || a.rev.isBlank()) return no(tr("Too big to save from the phone.", "גדול מדי לשמירה מהטלפון."))
        uploads.values.removeIf { System.currentTimeMillis() - it.at > 10 * 60_000L }
        val u = uploads.getOrPut(a.rev) { Upload(a.path, a.base, a.total) }
        if (u.path != a.path || u.base != a.base || u.total != a.total) return no(tr("Something went wrong on the way. Save again.", "משהו השתבש בדרך. אפשר לשמור שוב."))
        synchronized(u) {
            u.at = System.currentTimeMillis()
            // A piece sent again (its answer was lost) is told again where we are; a piece from the future waits for the rest.
            if (a.from == u.out.size().toLong()) u.out.write(a.data)
            val have = u.out.size().toLong()
            if (have < u.total) return FilePart(a.id, a.path, a.what, from = have, total = u.total)
            uploads.remove(a.rev)
            val now = f.readBytes()
            if (FileAsk.hash(now) != u.base) return no("changed")
            val bytes = u.out.toByteArray()
            f.writeBytes(bytes)
            return FilePart(a.id, a.path, a.what, from = have, total = u.total, hash = FileAsk.hash(bytes))
        }
    }

    private fun piece(a: FileAsk, all: ByteArray): FilePart {
        val from = a.from.coerceIn(0, all.size.toLong()).toInt()
        val to = minOf(all.size, from + FilePart.PIECE)
        return FilePart(a.id, a.path, a.what, data = all.copyOfRange(from, to), from = from.toLong(), total = all.size.toLong())
    }

    /** The last pictures made small, by path and when it changed, so every piece of one comes from the same bytes. */
    private val images = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ByteArray>?) = size > 6
    }

    /** The picture as it goes: as it is when small, else at most [IMAGE_SIDE] on its longer side, as a JPEG. */
    private fun image(f: File): ByteArray? {
        val key = f.path + "|" + f.lastModified() + "|" + f.length()
        synchronized(images) { images[key]?.let { return it } }
        val raw = f.readBytes()
        val made = if (raw.size <= IMAGE_RAW_MAX) raw else runCatching {
            val img = org.jetbrains.skia.Image.makeFromEncoded(raw)
            val scale = minOf(1f, IMAGE_SIDE.toFloat() / maxOf(img.width, img.height))
            val w = maxOf(1, (img.width * scale).toInt()); val h = maxOf(1, (img.height * scale).toInt())
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
            surface.canvas.clear(0xFFFFFFFF.toInt())
            surface.canvas.drawImageRect(img, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
            surface.makeImageSnapshot().encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 82)?.bytes
        }.getOrNull() ?: return null
        synchronized(images) { images[key] = made }
        return made
    }
}
