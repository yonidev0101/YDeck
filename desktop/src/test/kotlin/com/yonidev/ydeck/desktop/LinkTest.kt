package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.MemDecision
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.Snapshot
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {

    @Test fun qrRoundTripGivesTheSameKeys() {
        val p = Pairing.create()
        val text = p.qrText("Yoni's PC")
        assertTrue(text.length < 120)
        val (back, name) = Pairing.parse(text)!!
        assertEquals("Yoni's PC", name)
        assertEquals(p.boxId, back.boxId)
        assertEquals(p.token, back.token)
        assertEquals(32, p.boxId.length)
        assertNull(Pairing.parse("https://example.com"))
        assertNull(Pairing.parse("ydeck:pair?v=2&k=abc"))
    }

    @Test fun sealedOnlyOpensWithTheSameKeyAndKind() {
        val a = Pairing.create()
        val plain = "שום דבר לא נשאר פתוח".repeat(50).toByteArray()
        val sealed = a.seal("state", plain)
        assertArrayEquals(plain, a.open("state", sealed))
        assertTrue(runCatching { a.open("note", sealed) }.isFailure)
        assertTrue(runCatching { Pairing.create().open("state", sealed) }.isFailure)
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(runCatching { a.open("state", tampered) }.isFailure)
        assertNotEquals(a.boxId, Pairing.create().boxId)
    }

    @Test fun noteIsCleaned() {
        val n = com.yonidev.ydeck.link.Note("a", "p", com.yonidev.ydeck.link.Note.NEXT, "  שורה\nשנייה\u0007 ", 0)
        val back = com.yonidev.ydeck.link.Note.decode(n.encode())!!
        assertEquals("שורה שנייה", back.text)
        assertNull(com.yonidev.ydeck.link.Note.decode("""{"app":"YDeck","kind":"note","note":"run","text":"rm -rf"}""".toByteArray()))
    }

    @Test fun snapshotRoundTrip() {
        val s = Snapshot(5L, "PC", JSONObject().put("projects", org.json.JSONArray()), mapOf("x" to Memory(summary = "s", next = "n", decisions = listOf(MemDecision("d", "2026-10-13")))))
        val back = Snapshot.decode(s.encode())
        assertEquals("PC", back.computer)
        assertEquals("n", back.memory["x"]!!.next)
        assertEquals("d", back.memory["x"]!!.decisions.single().text)
    }

    /** Against the live relay: claim, read, 304, a stranger refused, unpair. `-Prelay` only (needs the network). */
    @Test fun liveRelay() {
        if (System.getProperty("relay") == null) return
        val p = Pairing.create()
        val r = Relay(p)
        val sealed = p.seal("state", "hello".toByteArray())
        assertTrue(r.putState(sealed))
        val got = r.getState(null)!!
        assertEquals("hello", String(p.open("state", got.sealed!!)))
        assertNull(r.getState(got.etag)!!.sealed)
        assertTrue(r.meta()!!.readAt != null)
        val stranger = Relay(Pairing(Pairing.create().secret)) // another box altogether
        assertNull(stranger.getState(null))
        // The phone's notes: sealed in, read back, opened, dropped.
        val note = com.yonidev.ydeck.link.Note("n1", "proj", com.yonidev.ydeck.link.Note.DECISION, "נשארים עם Firebase", 1L)
        assertTrue(r.postNote(p.seal("note", note.encode())))
        val waiting = r.notes()!!
        assertEquals(1, waiting.size)
        assertEquals("נשארים עם Firebase", com.yonidev.ydeck.link.Note.decode(p.open("note", waiting[0].second))!!.text)
        assertTrue(r.dropNote(waiting[0].first))
        assertEquals(0, r.notes()!!.size)
        assertTrue(r.delete())
        assertNull(r.getState(null))
    }
}
