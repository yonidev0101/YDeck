package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Reply
import com.yonidev.ydeck.link.Waiting
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class SessionAsksTest {
    @Before fun noClaude() { SessionAsks.autoDraft = false }
    @After fun drafts() { SessionAsks.autoDraft = true }

    private fun stop(session: String, message: String, active: Boolean = false) = JSONObject().put("hook_event_name", "Stop")
        .put("session_id", session).put("cwd", System.getProperty("java.io.tmpdir")).put("last_assistant_message", message).put("stop_hook_active", active)

    /** Runs the held hook call on its own thread, and waits until YDeck shows it as waiting. */
    private fun held(e: JSONObject): Pair<CompletableFuture<String>, Waiting> {
        val out = CompletableFuture.supplyAsync { SessionAsks.hold(e) }
        val until = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < until) {
            SessionAsks.waiting.value.firstOrNull()?.let { return out to it }
            Thread.sleep(20)
        }
        error("never held")
    }

    @Test fun aTurnEndingInAQuestionWaitsAndTheReplyWakesTheSession() {
        val (out, w) = held(stop("s1", "I can use Firebase or Supabase here.\n\nWhich one should I use?"))
        assertEquals(Waiting.TEXT, w.kind)
        assertTrue(SessionAsks.isHeld("s1"))
        assertTrue(SessionAsks.reply(w.id, SessionAsks.Answer("Firebase, as decided")))
        assertEquals("The developer answered from YDeck, away from the terminal: Firebase, as decided", out.get(5, TimeUnit.SECONDS))
        assertTrue(SessionAsks.waiting.value.isEmpty())
        assertFalse(SessionAsks.isHeld("s1"))
        // Answered once: a second reply finds nothing.
        assertFalse(SessionAsks.reply(w.id, SessionAsks.Answer("again")))
    }

    @Test fun typingInTheTerminalLetsItGo() {
        val (out, _) = held(stop("s2", "Should I push now?"))
        SessionAsks.release("s2")
        assertEquals("", out.get(5, TimeUnit.SECONDS))
        assertTrue(SessionAsks.waiting.value.isEmpty())
    }

    @Test fun answeringInTheTerminalFromYDeckLetsItGo() {
        val (out, w) = held(stop("s3", "Do you want tests for this too?"))
        SessionAsks.dismiss(w.id)
        assertEquals("", out.get(5, TimeUnit.SECONDS))
    }

    @Test fun onlyQuestionsAreHeld() {
        assertEquals("", SessionAsks.hold(stop("s4", "Done. The form sends once now.")))
        // A turn a hook asked for isn't a new question.
        assertEquals("", SessionAsks.hold(stop("s4", "Which one?", active = true)))
        // Permissions are never held, whatever they ask.
        assertEquals("", SessionAsks.hold(JSONObject().put("hook_event_name", "PermissionRequest").put("session_id", "s4").put("cwd", "C:\\")
            .put("tool_name", "Bash").put("tool_input", JSONObject().put("command", "git push"))))
        assertEquals("", SessionAsks.hold(JSONObject().put("hook_event_name", "PreToolUse").put("session_id", "s4").put("cwd", "C:\\").put("tool_name", "Bash")))
        assertTrue(SessionAsks.waiting.value.isEmpty())
    }

    @Test fun aChoiceIsHeldOnlyWhileNobodyIsAtTheComputer() {
        // The tests run with someone at the computer (or Windows can't tell): the terminal shows the choice at once.
        if (SessionAsks.away) return
        val choice = JSONObject().put("hook_event_name", "PreToolUse").put("tool_name", "AskUserQuestion").put("session_id", "s5").put("cwd", "C:\\")
            .put("tool_input", JSONObject().put("questions", JSONArray().put(JSONObject().put("question", "Which database?").put("header", "DB")
                .put("options", JSONArray().put(JSONObject().put("label", "Firebase")).put(JSONObject().put("label", "Supabase"))))))
        assertEquals("", SessionAsks.hold(choice))
    }

    @Test fun repliesFromThePhoneAreWordsOnly() {
        val r = Reply("r1", "w1", "Firebase\u0007", mapOf("Which database?" to "Firebase"), 1L)
        val back = Reply.decode(r.encode())!!
        assertEquals("Firebase", back.text)
        assertEquals("Firebase", back.choices["Which database?"])
        assertNull(Reply.decode(JSONObject().put("app", "YDeck").put("kind", "reply").put("text", " ").toString().toByteArray()))
        assertNull(Reply.decode(JSONObject().put("app", "YDeck").put("kind", "note").put("text", "x").toString().toByteArray()))
    }

    @Test fun theDraftIsAskedOnlyFromTheRecords() {
        val p = SessionAsks.draftPrompt(Waiting("w", "", Waiting.TEXT, "Which one should I use?"), null)
        assertTrue(p.contains("Never invent a decision"))
        assertTrue(p.contains("never read .env"))
        assertTrue(p.contains("Which one should I use?"))
    }
}
