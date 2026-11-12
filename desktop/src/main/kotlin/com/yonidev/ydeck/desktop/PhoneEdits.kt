package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Note
import java.io.File

/** What a change from the phone ([Note]) does to the three lists and to a project's details. Pure where it can be. */
object PhoneEdits {

    /** One of the lists in [dir]'s personal file. An item that isn't there any more is left alone. */
    fun list(dir: File, kind: String, text: String, arg: String) {
        val l = ProjectFiles.readLocal(dir) ?: LocalFile("", "")
        when (kind) {
            Note.LEFT_ADD -> Plan.add(dir, Plan.LEFT, text)
            Note.LEFT_DONE -> if (text in l.left) Plan.finish(dir, text)
            Note.LEFT_DROP -> Plan.remove(dir, Plan.LEFT, text)
            Note.LEFT_MOVE -> arg.toIntOrNull()?.let { to -> ProjectFiles.writeList(dir, Plan.LEFT, moved(l.left, text, to)) }
            Note.LEFT_EDIT -> if (arg in l.left) ProjectFiles.writeList(dir, Plan.LEFT, l.left.map { if (it == arg) text else it })
            Note.IDEA_ADD -> Plan.add(dir, Plan.IDEAS, text)
            Note.IDEA_TAKE -> if (text in l.ideas) Plan.take(dir, text)
            Note.IDEA_DROP -> Plan.remove(dir, Plan.IDEAS, text)
            Note.DONE_DROP -> Plan.remove(dir, Plan.DONE, text)
        }
    }

    /** [item] moved to place [to] (0 first) in [list]; unchanged when it isn't in it. */
    fun moved(list: List<String>, item: String, to: Int): List<String> {
        if (item !in list) return list
        val rest = list - item
        return rest.toMutableList().apply { add(to.coerceIn(0, rest.size), item) }
    }

    /** A project's details after the change, or null when nothing changes (or the change isn't acceptable). */
    fun details(p: Project, kind: String, text: String, args: List<String>): Project? {
        val a = { i: Int -> args.getOrElse(i) { "" } }
        return when (kind) {
            Note.NAME -> text.take(80).takeIf { it.isNotBlank() && it != p.name }?.let { p.copy(name = it, mono = if (p.mono == p.name.take(1).uppercase()) it.take(1).uppercase() else p.mono) }
            Note.COLOR -> DeckJson.parseColor(text)?.takeIf { it != p.color }?.let { p.copy(color = it, colorTemporary = false) }
            Note.KIND -> text.take(80).takeIf { it != p.kind }?.let { p.copy(kind = it) }
            Note.LINK_ADD -> web(text)?.takeIf { url -> p.actions.none { it.url == url } }?.let { p.copy(actions = p.actions + action(it, a(0), a(1))) }
            Note.LINK_EDIT -> web(text)?.let { url ->
                val i = p.actions.indexOfFirst { it.url == a(2) }.takeIf { it >= 0 } ?: return null
                p.copy(actions = p.actions.toMutableList().apply { this[i] = action(url, a(0).ifBlank { this[i].service.id }, a(1)) })
            }
            Note.LINK_DROP -> p.actions.filterNot { it.url == text }.takeIf { it.size != p.actions.size }?.let { p.copy(actions = it) }
            Note.COPY_ADD -> CopyItem(a(0).take(80), text).takeIf { c -> p.copies.none { it == c } }?.let { p.copy(copies = p.copies + it) }
            Note.COPY_DROP -> p.copies.filterNot { it.label == a(0) && it.value == text }.takeIf { it.size != p.copies.size }?.let { p.copy(copies = it) }
            else -> null
        }
    }

    private fun action(url: String, service: String, label: String): DeckAction {
        val s = Catalog.of(service.ifBlank { "link" })
        return DeckAction(s, url, label.take(80).ifBlank { s.label })
    }

    /** Only a web address opens from a link: nothing else (no file:, no javascript:) comes in from the phone. */
    fun web(url: String): String? = url.trim().takeIf { u ->
        runCatching { java.net.URI(u) }.getOrNull()?.let { (it.scheme.equals("https", true) || it.scheme.equals("http", true)) && !it.host.isNullOrBlank() } == true
    }
}
