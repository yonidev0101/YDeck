package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Launch
import com.yonidev.ydeck.link.Launched
import com.yonidev.ydeck.link.Live
import com.yonidev.ydeck.link.LiveSession
import com.yonidev.ydeck.link.NewKey
import com.yonidev.ydeck.link.Note
import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
import com.yonidev.ydeck.link.Snapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LaunchTest {
    private val sid = "6149f888-014d-4282-a848-fb1862c47e06"

    @Test fun roundTripAndSealedApartFromNotes() {
        val p = Pairing.create()
        val l = Launch("id1", "proj", Launch.RESUME, sid, "תמשיך מאיפה שעצרנו", remote = true, at = 42)
        val sealed = p.seal("launch", l.encode())
        assertEquals(l, Launch.decode(p.open("launch", sealed)))
        // A launch never opens as a note, and a note never as a launch.
        assertTrue(runCatching { p.open("note", sealed) }.isFailure)
        assertNull(Launch.decode(Note("n", "proj", Note.NEXT, "x", 1).encode()))
    }

    @Test fun onlyTheClosedList() {
        fun raw(vararg kv: Pair<String, Any>) = JSONObject().put("app", "YDeck").put("kind", "launch").apply { kv.forEach { (k, v) -> put(k, v) } }.toString().toByteArray()
        assertNull(Launch.decode(raw("mode" to "shell", "project" to "p")))
        assertNull(Launch.decode(raw("mode" to Launch.RESUME, "session" to "../../etc")))
        assertNull(Launch.decode(raw("mode" to Launch.RESUME, "session" to "$sid --dangerously-skip-permissions")))
        // A session id is kept only for RESUME; the prompt becomes one line.
        val l = Launch.decode(raw("mode" to Launch.NEW, "session" to sid, "prompt" to "one\ntwo\r\n\tthree"))!!
        assertEquals("", l.session)
        assertEquals("one two three", l.prompt)
    }

    /** The one-time code against the real relay: the phone's key goes through it once, then the code is dead. */
    @Test fun oneTimeCodeCarriesThePhonesKeyOnce() {
        val offer = Pairing.create()
        val code = Pairing.parse(offer.qrText("PC"))!!
        assertTrue(code.oneTime)
        // Before the computer claims the code's box, a phone can't post to it.
        val phoneSide = Relay(code.pairing)
        assertTrue(!phoneSide.postNote(code.pairing.seal("pair", NewKey(ByteArray(32), 1).encode())))
        val computerSide = Relay(offer)
        assertTrue(computerSide.putState(offer.seal("hello", "{}".toByteArray())))
        val mine = Pairing.create()
        assertTrue(phoneSide.postNote(code.pairing.seal("pair", NewKey(mine.secret, 2).encode())))
        val got = computerSide.notes()!!.single().second
        val key = NewKey.decode(offer.open("pair", got))!!
        assertTrue(key.secret.contentEquals(mine.secret))
        // A key can't pass for a note, a launch or a rekey.
        assertTrue(runCatching { offer.open("rekey", got) }.isFailure)
        // Used: the computer empties the code's box, and the code no longer takes anything.
        assertTrue(computerSide.delete())
        assertTrue(!phoneSide.postNote(code.pairing.seal("pair", NewKey(Pairing.create().secret, 3).encode())))
        assertEquals(401, phoneSide.lastCode)
        // Older codes still read, as lasting secrets.
        assertTrue(!Pairing.parse(offer.qrText("PC").replace("v=2", "v=1"))!!.oneTime)
    }

    @Test fun promptStaysOneHarmlessArgument() {
        assertEquals("ends with", Tools.said("ends with\\\\"))
        assertEquals("dangerously-skip-permissions", Tools.said("--dangerously-skip-permissions"))
        assertEquals("a   b   c", Tools.said("a\"; b & c"))
        assertEquals("line one line two", Tools.said("line one\r\nline two"))
        assertNull(Tools.said("  -- "))
    }

    @Test fun snapshotCarriesSessionsAndAnswers() {
        val live = Live(sessions = listOf(LiveSession(sid, "פרסומות", 7), LiveSession("not-an-id", "x", 8)))
        val s = Snapshot(1, "PC", JSONObject(), emptyMap(), mapOf("p" to live), launch = true, launched = listOf(Launched("id1", false, "stale")))
        val back = Snapshot.decode(s.encode())
        assertTrue(back.launch)
        assertEquals(listOf(Launched("id1", false, "stale")), back.launched)
        assertEquals(listOf(LiveSession(sid, "פרסומות", 7)), back.live["p"]!!.sessions)
    }

    @Test fun conversationsSkipHandoffsAndTakeTheLatestTitle() {
        val dir = Files.createTempDirectory("ydeck-proj").toFile()
        val sessions = Claude.sessionsDir(dir).apply { mkdirs() }
        try {
            fun user(text: String, entry: String = "cli") = JSONObject().put("type", "user").put("entrypoint", entry)
                .put("message", JSONObject().put("role", "user").put("content", text)).toString()
            File(sessions, "$sid.jsonl").writeText(listOf(
                user("<command-name>/clear</command-name>"),
                user("תעשה שהטלפון יפתח סשן"),
                JSONObject().put("type", "ai-title").put("aiTitle", "ישן").toString(),
                JSONObject().put("type", "ai-title").put("aiTitle", "פתיחת סשן מהטלפון").toString(),
            ).joinToString("\n"))
            File(sessions, "11111111-2222-3333-4444-555555555555.jsonl").writeText(user("You write the handoff note", entry = "sdk-cli"))
            val list = Claude.conversations(dir)
            assertEquals(1, list.size)
            assertEquals(sid, list[0].id)
            assertEquals("פתיחת סשן מהטלפון", list[0].title)
        } finally {
            sessions.deleteRecursively()
            dir.deleteRecursively()
        }
    }
}
