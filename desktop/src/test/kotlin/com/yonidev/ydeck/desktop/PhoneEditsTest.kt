package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/** The phone changes YDeck's data only, from a closed list, and what it can't apply it leaves alone. */
class PhoneEditsTest {
    private val p = Project.sample().copy(id = "nova")

    @Test fun listsMoveAsOnTheComputer() {
        val dir = Files.createTempDirectory("edits").toFile()
        try {
            PhoneEdits.list(dir, Note.LEFT_ADD, "a", "")
            PhoneEdits.list(dir, Note.LEFT_ADD, "b", "")
            PhoneEdits.list(dir, Note.LEFT_ADD, "c", "")
            PhoneEdits.list(dir, Note.LEFT_MOVE, "c", "0")
            PhoneEdits.list(dir, Note.LEFT_EDIT, "B", "b")
            PhoneEdits.list(dir, Note.LEFT_DONE, "a", "")
            PhoneEdits.list(dir, Note.IDEA_ADD, "x", "")
            PhoneEdits.list(dir, Note.IDEA_TAKE, "x", "")
            PhoneEdits.list(dir, Note.LEFT_DONE, "gone", "")
            val l = ProjectFiles.readLocal(dir)!!
            assertEquals(listOf("c", "B", "x"), l.left)
            assertEquals(listOf("a"), l.done)
            assertEquals(emptyList<String>(), l.ideas)
        } finally { dir.deleteRecursively() }
    }

    @Test fun detailsChangeOnlyWhatTheySay() {
        assertEquals("Orbit", PhoneEdits.details(p, Note.NAME, "Orbit", emptyList())!!.name)
        assertEquals(0xFF112233.toInt(), PhoneEdits.details(p, Note.COLOR, "#112233", emptyList())!!.color)
        val added = PhoneEdits.details(p, Note.LINK_ADD, "https://nova.dev/admin", listOf("link", "Admin"))!!
        assertEquals("Admin", added.actions.last().label)
        assertNull(PhoneEdits.details(p, Note.LINK_ADD, "file:///C:/Windows", listOf("link", "x")))
        assertNull(PhoneEdits.details(p, Note.LINK_ADD, "javascript:alert(1)", emptyList()))
        val edited = PhoneEdits.details(added, Note.LINK_EDIT, "https://nova.dev/staff", listOf("", "Staff", "https://nova.dev/admin"))!!
        assertEquals("https://nova.dev/staff", edited.actions.last().url)
        assertEquals(p.actions, PhoneEdits.details(edited, Note.LINK_DROP, "https://nova.dev/staff", emptyList())!!.actions)
        val copied = PhoneEdits.details(p, Note.COPY_ADD, "abc", listOf("Key"))!!
        assertEquals(p.copies, PhoneEdits.details(copied, Note.COPY_DROP, "abc", listOf("Key"))!!.copies)
    }

    @Test fun aNoteOfAnUnknownKindIsDropped() {
        val n = Note("1", "nova", "rm -rf", "x", 0)
        assertNull(Note.decode(n.encode()))
        val ok = Note("2", "nova", Note.LEFT_ADD, "one\ntwo", 0, listOf("a\nb"))
        assertEquals("one two", Note.decode(ok.encode())!!.text)
        assertEquals(listOf("a b"), Note.decode(ok.encode())!!.args)
    }
}
