package com.yonidev.ydeck.desktop

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
 * Claude Code asks once per folder whether to trust it, in the terminal, and waits. Opened from the phone, nobody is
 * there to answer: so before YDeck opens Claude in a project's folder for the phone, it marks that folder as trusted
 * the way Claude Code itself does (projects["C:/…"].hasTrustDialogAccepted in ~/.claude.json). Only folders connected
 * to a project in YDeck (the developer chose them, or YDeck made them for a project made on the phone); nothing else
 * in that file is changed, and it's written whole at once, since Claude Code writes it too.
 */
object ClaudeTrust {
    var config: File = File(System.getProperty("user.home"), ".claude.json")

    /** Claude Code's key for [dir]: its full path with "/". */
    fun key(dir: File): String = dir.absoluteFile.normalize().path.replace('\\', '/').trimEnd('/')

    /** Marks [dir] as trusted. False when the file couldn't be read or written (Claude will just ask, as before). */
    @Synchronized
    fun trust(dir: File): Boolean = runCatching {
        val text = if (config.isFile) config.readText(Charsets.UTF_8) else "{}"
        val root = JSONObject(text)
        val projects = root.optJSONObject("projects") ?: JSONObject().also { root.put("projects", it) }
        val key = key(dir)
        // The same folder may be there under another spelling (backslashes, another case): all of them.
        val same = projects.keys().asSequence().filter { it.replace('\\', '/').trimEnd('/').equals(key, ignoreCase = true) }.toList()
        if (same.isNotEmpty() && same.all { projects.optJSONObject(it)?.optBoolean("hasTrustDialogAccepted") == true }) return@runCatching true
        for (k in same.ifEmpty { listOf(key) }) {
            val p = projects.optJSONObject(k) ?: JSONObject().also { projects.put(k, it) }
            p.put("hasTrustDialogAccepted", true)
        }
        val tmp = File(config.parentFile, config.name + ".ydeck.tmp")
        tmp.writeText(root.toString(2), Charsets.UTF_8)
        Files.move(tmp.toPath(), config.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    }.getOrDefault(false)
}
