package com.yonidev.ydeck.desktop

import java.io.File

/** A new project's folder, made here for a project made on the phone (see [PhoneLink]). */
object NewFolders {
    /** Where most projects' folders sit (their common parent folder), or null when there are none to go by. */
    fun home(folders: Collection<String>): File? =
        folders.mapNotNull { File(it).absoluteFile.parentFile }
            .filter { it.isDirectory }
            .groupBy { it.path.lowercase() }.values
            .maxByOrNull { it.size }?.first()

    /** The folder's name from the project's name: only what Windows allows, never a path. */
    fun safeName(name: String): String {
        val bad = "<>:\"/\\|?*"
        var s = name.map { if (it in bad || it.code < 32) ' ' else it }.joinToString("")
            .replace(Regex("\\s+"), " ").trim('.', ' ').take(60).trim('.', ' ')
        val reserved = Regex("^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?$", RegexOption.IGNORE_CASE)
        if (reserved.matches(s)) s = "$s project"
        return s.ifBlank { "New project" }
    }

    /** Makes a new, empty folder for [name] in [home] ("name 2", "name 3"… when it's taken). Null if it couldn't. */
    fun make(home: File, name: String): File? {
        val base = safeName(name)
        for (n in 1..50) {
            val f = File(home, if (n == 1) base else "$base $n")
            if (f.parentFile?.canonicalPath != home.canonicalPath) return null
            if (f.exists()) continue
            return f.takeIf { it.mkdir() }
        }
        return null
    }
}
