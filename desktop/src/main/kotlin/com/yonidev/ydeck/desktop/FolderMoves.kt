package com.yonidev.ydeck.desktop

import org.json.JSONObject
import java.io.File

/*
 * A project folder renamed (or moved within the folder it was in) is found again by itself. While a folder is there,
 * YDeck remembers what makes it that project's: the icon it carries, its last commit, its server's address. When it's
 * gone, the folders beside where it was are looked at, and the project follows the one that is clearly it. Only one:
 * when two could be it (a copy of the folder, say), nothing changes and the developer picks.
 */
object FolderMoves {
    private data class Print(val head: String?, val origin: String?)

    private val file = File(AppDirs.local, "folder-prints.json")
    private val prints: MutableMap<String, Print> = load()

    /** Every few seconds: a folder renamed shows under its new name at once. What a folder is, is noted every minute. */
    fun start() {
        Thread({
            var round = 0
            while (true) {
                runCatching { check(remember = round++ % 20 == 0) }
                Thread.sleep(3_000)
            }
        }, "ydeck-folder-moves").apply { isDaemon = true; start() }
    }

    /** Moves projects whose folder is gone to where it went, and (with [remember]) notes what every folder there is. */
    fun check(remember: Boolean = true) {
        val settings = Settings.state.value
        val deck = Repo.deck.value
        val taken = settings.local.values.mapNotNull { it.folder?.let { f -> File(f).absolutePath.lowercase() } }.toSet()
        var changed = false
        for (p in deck.projects) {
            val local = settings.localOf(p.id)
            val folder = local.folder ?: continue
            val dir = File(folder)
            if (dir.isDirectory) {
                if (!remember) continue
                val now = printOf(dir)
                if (now != prints[p.id] && (now.head != null || now.origin != null)) { prints[p.id] = now; changed = true }
                continue
            }
            val found = find(p.id, dir, prints[p.id], taken) ?: continue
            saveLocal(p, local.copy(folder = found.path))
        }
        if (changed) save()
    }

    private fun find(projectId: String, gone: File, print: Print?, taken: Set<String>): File? {
        val parent = gone.absoluteFile.parentFile?.takeIf { it.isDirectory } ?: return null
        val around = parent.listFiles { f -> f.isDirectory && f.absolutePath.lowercase() !in taken }?.toList().orEmpty()
        around.filter { FolderTag.isFolderOf(it, projectId) }.singleOrNull()?.let { return it }
        if (print == null) return null
        val prints = around.associateWith { printOf(it) }
        print.head?.let { h -> prints.filter { it.value.head == h }.keys.singleOrNull()?.let { return it } }
        print.origin?.let { o -> prints.filter { it.value.origin == o }.keys.singleOrNull()?.let { return it } }
        return null
    }

    /** The commit HEAD is on and the origin's address, read from .git's files (no git run: it's done for many folders). */
    private fun printOf(dir: File): Print {
        val gd = Git.gitDir(dir) ?: return Print(null, null)
        val common = Git.commonDir(gd)
        val head = runCatching {
            val h = File(gd, "HEAD").readText().trim()
            if (!h.startsWith("ref:")) h else {
                val ref = h.removePrefix("ref:").trim()
                File(common, ref).takeIf { it.isFile }?.readText()?.trim()
                    ?: File(common, "packed-refs").takeIf { it.isFile }?.readLines()?.firstOrNull { it.endsWith(" $ref") }?.substringBefore(' ')
            }
        }.getOrNull()?.takeIf { it.length >= 40 }
        val origin = runCatching { Scanner.originUrl(File(common, "config").readText()) }.getOrNull()
        return Print(head, origin)
    }

    private fun load(): MutableMap<String, Print> = runCatching {
        val o = JSONObject(file.readText())
        o.keySet().associateWith { k -> o.getJSONObject(k).let { Print(it.optString("head").ifBlank { null }, it.optString("origin").ifBlank { null }) } }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun save() {
        runCatching {
            val o = JSONObject()
            prints.forEach { (id, p) -> o.put(id, JSONObject().put("head", p.head ?: "").put("origin", p.origin ?: "")) }
            file.writeText(o.toString(1))
        }
    }
}
