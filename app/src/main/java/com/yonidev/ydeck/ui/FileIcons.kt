package com.yonidev.ydeck.ui

import android.content.Context
import org.json.JSONObject

/*
 * Which icon a file or folder gets on the Files tab, as in VS Code and GitHub (Material Icon Theme, MIT; the part
 * taken and the table are made by store/fileicons/make.cjs). A folder by its name; a file by its whole name, then by
 * its endings from the longest ("d.ts" before "ts"); else the plain file or folder.
 */
object FileIcons {
    private class Table(val ext: Map<String, String>, val name: Map<String, String>, val dir: Map<String, String>)

    @Volatile private var table: Table? = null

    private fun table(context: Context): Table = table ?: runCatching {
        val o = JSONObject(context.assets.open("fileicons.json").bufferedReader().use { it.readText() })
        fun map(k: String) = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.optString(it) } }.orEmpty()
        Table(map("ext"), map("name"), map("dir"))
    }.getOrElse { Table(emptyMap(), emptyMap(), emptyMap()) }.also { table = it }

    /** The drawable for [name], a folder when [dir]. */
    fun of(context: Context, name: String, dir: Boolean): Int {
        val t = table(context)
        val n = name.lowercase()
        val icon = if (dir) t.dir[n] ?: "folder" else t.name[n] ?: endings(n).firstNotNullOfOrNull { t.ext[it] } ?: "file"
        return FILE_ICON_IDS[icon] ?: FILE_ICON_IDS[if (dir) "folder" else "file"]!!
    }

    /** "a.test.ts" → "test.ts", "ts". */
    private fun endings(n: String): List<String> {
        val start = if (n.startsWith('.')) 1 else 0
        return n.indices.filter { it >= start && n[it] == '.' }.map { n.substring(it + 1) }.filter { it.isNotEmpty() }
    }
}
