package com.yonidev.ydeck.data

import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Note

/**
 * What the phone shows while its changes are on their way: the computer's state with the changes made here and not
 * yet applied there laid over it, as the computer will apply them (see PhoneEdits on the computer). Once the
 * computer applies one, its state already says it and the change leaves [pending].
 */
object Overlay {

    fun deck(deck: Deck, pending: List<Note>): Deck {
        if (pending.isEmpty()) return deck
        var projects = deck.projects
        for (n in pending) {
            if (n.kind == Note.NEW_PROJECT) {
                if (projects.none { it.id == n.project }) projects = projects + Project.blank().copy(id = n.project, name = n.text, mono = n.text.take(1).uppercase(),
                    color = DeckJson.parseColor(n.args.firstOrNull().orEmpty()) ?: Project.blank().color)
                continue
            }
            projects = projects.map { p -> if (p.id == n.project) project(p, n) else p }
        }
        return Deck(projects)
    }

    private fun project(p: Project, n: Note): Project {
        val a = { i: Int -> n.args.getOrElse(i) { "" } }
        fun action(url: String, service: String, label: String) = Service.of(service.ifBlank { "link" }).let { s -> DeckAction(s, url, label.ifBlank { s.label }) }
        return when (n.kind) {
            Note.ARCHIVE -> p.copy(archived = n.text == "1")
            Note.NAME -> p.copy(name = n.text)
            Note.COLOR -> DeckJson.parseColor(n.text)?.let { p.copy(color = it, colorTemporary = false) } ?: p
            Note.KIND -> p.copy(kind = n.text)
            Note.LINK_ADD -> if (p.actions.any { it.url == n.text }) p else p.copy(actions = p.actions + action(n.text, a(0), a(1)))
            Note.LINK_EDIT -> p.copy(actions = p.actions.map { if (it.url == a(2)) action(n.text, a(0).ifBlank { it.service.id }, a(1)) else it })
            Note.LINK_DROP -> p.copy(actions = p.actions.filterNot { it.url == n.text })
            Note.COPY_ADD -> p.copy(copies = p.copies + CopyItem(a(0), n.text))
            Note.COPY_DROP -> p.copy(copies = p.copies.filterNot { it.label == a(0) && it.value == n.text })
            else -> p
        }
    }

    fun memory(m: Memory?, pending: List<Note>): Memory? {
        if (m == null || pending.isEmpty()) return m
        var x: Memory = m
        for (n in pending) {
            val t = n.text
            x = when (n.kind) {
                Note.NEXT -> x.copy(next = t)
                Note.DONE -> x.copy(next = "")
                Note.LEFT_ADD -> x.copy(left = x.left - t + t)
                Note.LEFT_DONE -> if (t in x.left) x.copy(left = x.left - t, doneItems = (listOf(t) + (x.doneItems - t)).take(10)) else x
                Note.LEFT_DROP -> x.copy(left = x.left - t)
                Note.LEFT_MOVE -> n.args.firstOrNull()?.toIntOrNull()?.let { to ->
                    if (t !in x.left) x else (x.left - t).toMutableList().apply { add(to.coerceIn(0, size), t) }.let { x.copy(left = it) }
                } ?: x
                Note.LEFT_EDIT -> x.copy(left = x.left.map { if (it == n.args.firstOrNull()) t else it })
                Note.IDEA_ADD -> x.copy(ideas = listOf(t) + (x.ideas - t))
                Note.IDEA_TAKE -> if (t in x.ideas) x.copy(ideas = x.ideas - t, left = x.left - t + t) else x
                Note.IDEA_DROP -> x.copy(ideas = x.ideas - t)
                Note.DONE_DROP -> x.copy(doneItems = x.doneItems - t)
                else -> x
            }
        }
        return x
    }

    /** A change in words, for the line that lists what still waits for the computer. */
    fun words(n: Note): String = when (n.kind) {
        Note.DECISION -> "החלטה"
        Note.DONE, Note.LEFT_DONE -> "סומן כנעשה"
        Note.NEXT -> "צעד הבא"
        Note.ARCHIVE -> if (n.text == "1") "לארכיון" else "חזרה מהארכיון"
        Note.LEFT_ADD -> "שלב חדש"
        Note.LEFT_DROP, Note.IDEA_DROP, Note.DONE_DROP -> "נמחק"
        Note.LEFT_MOVE -> "הוזז"
        Note.LEFT_EDIT -> "שלב נערך"
        Note.IDEA_ADD -> "רעיון חדש"
        Note.IDEA_TAKE -> "רעיון עבר ל״נשאר״"
        Note.NAME, Note.COLOR, Note.KIND -> "פרטי הפרויקט"
        Note.LINK_ADD, Note.LINK_EDIT, Note.LINK_DROP -> "קישורים"
        Note.COPY_ADD, Note.COPY_DROP -> "ערכים להעתקה"
        Note.NEW_PROJECT -> "פרויקט חדש"
        Note.MORNING_LATER, Note.MORNING_SKIP, Note.MORNING_PICK -> "בוקר טוב"
        else -> "שינוי"
    }
}
