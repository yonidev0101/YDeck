package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A real conversation with the Claude Code on this PC: write, a permission asked and given, the file there. Run with -Plive. */
class TalkLive {
    @Test fun live() {
        if (System.getProperty("live") == null) return
        val dir = Files.createTempDirectory("ydeck-talk").toFile()
        fun chat() = Talk.chats.value[dir.path] ?: Talk.Chat()
        fun until(what: String, minutes: Long = 3, ok: () -> Boolean) {
            val end = System.currentTimeMillis() + minutes * 60_000
            while (!ok()) { check(System.currentTimeMillis() < end) { "timed out waiting for $what: ${chat()}" }; Thread.sleep(300) }
        }
        try {
            Talk.load(dir)
            Talk.send(dir, "Create the file hello.txt containing the word hi, with the Write tool. Then answer only: done.")
            until("the permission") { chat().ask != null || !chat().busy }
            val ask = chat().ask
            println("asked: $ask")
            if (ask != null) Talk.answer(dir, Talk.Answer.ONCE)
            until("the answer") { !chat().busy }
            println("lines: " + chat().lines)
            assertEquals("hi", File(dir, "hello.txt").readText().trim())
            assertTrue(chat().lines.any { it.kind == Talk.Kind.CLAUDE })
            // The same conversation goes on.
            val session = chat().session
            Talk.send(dir, "Answer only: second.")
            until("the second answer") { !chat().busy }
            assertEquals(session, chat().session)
            println("second: " + chat().lines.last())
            // And it's there to come back to.
            Thread.sleep(1000)
            val past = Transcript.list(dir)
            println("past: $past")
            assertEquals(session, past.first().session)
            assertTrue(Transcript.lines(dir, session!!).any { it.kind == Talk.Kind.USER })
        } finally {
            Talk.close(dir.path)
            Thread.sleep(2000)
            dir.deleteRecursively()
        }
    }
}
