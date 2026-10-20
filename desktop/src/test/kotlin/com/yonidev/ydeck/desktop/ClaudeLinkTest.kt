package com.yonidev.ydeck.desktop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Claude Code's hooks into a session's state, the project files Claude keeps, and the plugin YDeck writes. */
class ClaudeLinkTest {
    private val dir = "C:\\Work\\nova"

    private fun event(name: String, session: String, vararg extra: Pair<String, String>) =
        JSONObject().put("hook_event_name", name).put("session_id", session).put("cwd", "$dir\\web").apply { extra.forEach { (k, v) -> put(k, v) } }

    @Test fun aSessionMovesThroughItsStates() {
        val t = 1_000_000L
        val s = "s-${System.nanoTime()}"
        ClaudeLink.handle(event("SessionStart", s, "source" to "startup"), t)
        assertEquals(ClaudeMode.DONE, ClaudeLink.sessions.value[s]!!.mode)

        ClaudeLink.handle(event("UserPromptSubmit", s, "prompt" to "\n  add the Stripe webhook\nand tests"), t + 1000)
        val working = ClaudeLink.sessions.value[s]!!
        assertEquals(ClaudeMode.WORKING, working.mode)
        assertEquals("add the Stripe webhook", working.detail)

        ClaudeLink.handle(event("Notification", s, "notification_type" to "permission_prompt", "message" to "Claude needs your permission to use Bash"), t + 5000)
        assertEquals(ClaudeMode.WAITING, ClaudeLink.sessions.value[s]!!.mode)
        // "Idle" only repeats that Claude is done; it doesn't change anything.
        ClaudeLink.handle(event("Notification", s, "notification_type" to "idle_prompt", "message" to "waiting"), t + 6000)
        assertEquals(ClaudeMode.WAITING, ClaudeLink.sessions.value[s]!!.mode)

        ClaudeLink.handle(event("Stop", s, "last_assistant_message" to "Done: webhook added."), t + 90_000)
        val done = ClaudeLink.sessions.value[s]!!
        assertEquals(ClaudeMode.DONE, done.mode)
        assertEquals("Done: webhook added.", done.detail)

        ClaudeLink.handle(event("SessionEnd", s, "reason" to "prompt_input_exit"), t + 100_000)
        assertNull(ClaudeLink.sessions.value[s])
    }

    @Test fun waitingTurnsToWorkingOnceTheConversationMovesOn() {
        val transcript = Files.createTempFile("ydeck-transcript", ".jsonl").toFile()
        try {
            val s = "w-${System.nanoTime()}"
            val t = System.currentTimeMillis() - 60_000
            ClaudeLink.handle(event("Notification", s, "notification_type" to "permission_prompt", "message" to "?", "transcript_path" to transcript.path), t)
            transcript.setLastModified(t - 10_000)
            ClaudeLink.tidy(t + 2000)
            assertEquals(ClaudeMode.WAITING, ClaudeLink.sessions.value[s]!!.mode)
            transcript.setLastModified(t + 5000) // the user answered; Claude went on
            ClaudeLink.tidy(t + 6000)
            assertEquals(ClaudeMode.WORKING, ClaudeLink.sessions.value[s]!!.mode)
            ClaudeLink.handle(event("SessionEnd", s), t + 7000)
        } finally { transcript.delete() }
    }

    @Test fun theMostPressingSessionShows() {
        val now = 10_000_000L
        fun s(id: String, cwd: String, mode: ClaudeMode, since: Long) = id to ClaudeSession(id, cwd, mode, since, "", null, since)
        val all = mapOf(
            s("a", "$dir\\web", ClaudeMode.WORKING, now - 1000),
            s("b", dir, ClaudeMode.WAITING, now - 5000),
            s("c", "C:\\Work\\novaX", ClaudeMode.WAITING, now),
            s("d", "C:\\Work\\other", ClaudeMode.WORKING, now),
        )
        assertEquals("b", ClaudeLink.statusIn(dir, all, now)!!.id)
        assertEquals("a", ClaudeLink.statusIn(dir, all - "b", now)!!.id)
        // Done shows for half an hour, then the widget goes back to git.
        val done = mapOf(s("e", dir, ClaudeMode.DONE, now - 10 * 60_000))
        assertEquals("e", ClaudeLink.statusIn(dir, done, now)!!.id)
        assertNull(ClaudeLink.statusIn(dir, done, now + 40 * 60_000))
    }

    @Test fun foldersAreMatchedWhole() {
        assertTrue(isUnder("C:\\Work\\nova", "c:/work/nova/"))
        assertTrue(isUnder("C:\\Work\\nova\\app\\src", "C:\\Work\\nova"))
        assertFalse(isUnder("C:\\Work\\novaX", "C:\\Work\\nova"))
    }

    @Test fun projectFilesReadAndTheNextStepGoesBack() {
        val d = Files.createTempDirectory("ydeck-files").toFile()
        try {
            assertNull(ProjectFiles.readShared(d))
            File(d, ".ydeck.json").writeText("""
                {"links":[{"label":"Stripe","url":"https://dashboard.stripe.com/acct_1"},{"label":"bad","url":"ftp://x"}],
                 "commands":[{"label":"dev","line":"pnpm dev"},{"line":""}],
                 "releases":[{"version":"0.9","date":"2026-10-12","note":"Webhooks"}],
                 "other":true}
            """.trimIndent())
            val shared = ProjectFiles.readShared(d)!!
            assertEquals(listOf(FileLink("Stripe", "https://dashboard.stripe.com/acct_1")), shared.links)
            assertEquals(listOf(Command("dev", "pnpm dev")), shared.commands)
            assertEquals("0.9", shared.releases.first().version)

            File(d, ".git/info").mkdirs()
            File(d, ".ydeck.local.json").writeText("""{"now":"Stripe webhooks","next":"test in production","mine":1}""")
            ProjectFiles.writeNext(d, " deploy the fix ")
            val local = ProjectFiles.readLocal(d)!!
            assertEquals("Stripe webhooks", local.now)
            assertEquals("deploy the fix", local.next)
            assertEquals(1, JSONObject(File(d, ".ydeck.local.json").readText()).getInt("mine"))
            // The personal file stays out of git, once.
            ProjectFiles.writeNext(d, "again")
            assertEquals(1, File(d, ".git/info/exclude").readLines().count { it == ".ydeck.local.json" })
        } finally { d.deleteRecursively() }
    }

    @Test fun theBriefingTellsClaudeWhatToKeep() {
        val p = DeckJson.decode(File("../store/play/source/demo-projects.json").readText()).projects[0]
        val text = ProjectFiles.briefing(p, Files.createTempDirectory("ydeck-brief").toFile())
        assertTrue(text.contains("\"${p.name}\""))
        assertTrue(text.contains(".ydeck.local.json") && text.contains(".ydeck.json"))
        assertTrue(text.contains("never secrets"))
    }

    @Test fun thePluginsHooksPostInTheBackground() {
        val hooks = ClaudePlugin.hooks().getJSONObject("hooks")
        assertEquals(setOf("SessionStart", "UserPromptSubmit", "Notification", "Stop", "SessionEnd"), hooks.keySet())
        val h = hooks.getJSONArray("Stop").getJSONObject(0).getJSONArray("hooks").getJSONObject(0)
        assertEquals("command", h.getString("type"))
        assertTrue(h.getBoolean("async"))
        val args = h.getJSONArray("args").toList().joinToString(" ")
        assertTrue(args.contains("X-YDeck:${ClaudeLink.key}"))
        // The start answers before the first message, and never fails when YDeck is closed.
        val start = hooks.getJSONArray("SessionStart").getJSONObject(0).getJSONArray("hooks").getJSONObject(0)
        assertFalse(start.optBoolean("async"))
        assertTrue(start.getJSONArray("args").toList().last().toString().endsWith("|| exit /b 0"))
        assertTrue(args.contains("http://127.0.0.1:${ClaudeLink.PORT}/claude"))
    }

    @Test fun aSessionStillRunningIsOpenAndGoesWithItsProcess() {
        val folder = "C:\\Work\\open-${System.nanoTime()}"
        fun ev(name: String, s: String, vararg extra: Pair<String, String>) =
            JSONObject().put("hook_event_name", name).put("session_id", s).put("cwd", folder).apply { extra.forEach { (k, v) -> put(k, v) } }
        val t = System.currentTimeMillis()
        val s = "o-${System.nanoTime()}"
        ClaudeLink.handle(ev("SessionStart", s), t - 60_000)
        ClaudeLink.handle(ev("UserPromptSubmit", s, "prompt" to "fix it"), t - 55_000)
        ClaudeLink.handle(ev("Stop", s, "last_assistant_message" to "Fixed."), t - 50_000)
        // Done, but its claude still runs (with Remote Control): open.
        ClaudeLink.tidy(t, mapOf(s to OpenSessions.Open(s, folder, busy = false, remote = true, since = t - 50_000)))
        val open = ClaudeLink.statusIn(folder, ClaudeLink.sessions.value, t)!!
        assertEquals(ClaudeMode.OPEN, open.mode)
        assertTrue(open.remote)
        assertEquals(1, ClaudeLink.openIn(folder, ClaudeLink.sessions.value))
        // The terminal closed: gone at once, nothing left to show.
        ClaudeLink.tidy(t + 4000, emptyMap())
        assertNull(ClaudeLink.sessions.value[s])

        // Closed in the middle of work: over now, not 15 minutes later.
        val w = "w-${System.nanoTime()}"
        ClaudeLink.handle(ev("UserPromptSubmit", w, "prompt" to "long task"), t - 30_000)
        ClaudeLink.tidy(t, emptyMap())
        assertEquals(ClaudeMode.DONE, ClaudeLink.sessions.value[w]!!.mode)

        // Open before YDeck started (no hooks heard): taken from the list, and follows it.
        val l = "l-${System.nanoTime()}"
        ClaudeLink.tidy(t, mapOf(l to OpenSessions.Open(l, folder, busy = true, remote = false, since = t)))
        assertEquals(ClaudeMode.WORKING, ClaudeLink.sessions.value[l]!!.mode)
        ClaudeLink.tidy(t + 2000, mapOf(l to OpenSessions.Open(l, folder, busy = false, remote = false, since = t + 2000)))
        assertEquals(ClaudeMode.OPEN, ClaudeLink.sessions.value[l]!!.mode)
        ClaudeLink.tidy(t + 4000, emptyMap())
        assertNull(ClaudeLink.sessions.value[l])
    }

    @Test fun claudeCodesListOfOpenSessions() {
        val rec = JSONObject("""{"pid":4084,"sessionId":"26bf8ead-d698-4c2e-a936-52bd2a44199b","cwd":"C:\\Work\\nova","procStart":"134364667085451274","kind":"interactive","status":"idle","statusUpdatedAt":5,"bridgeSessionId":"session_01"}""")
        val open = OpenSessions.read(rec) { _, _ -> true }!!
        assertEquals("C:\\Work\\nova", open.cwd)
        assertFalse(open.busy)
        assertTrue(open.remote)
        // A process that ended (or another under its pid) doesn't count; nor does a session that isn't interactive.
        assertNull(OpenSessions.read(rec) { _, _ -> false })
        assertNull(OpenSessions.read(JSONObject(rec.toString()).put("kind", "background")) { _, _ -> true })
    }

    @Test fun closingTheDayIsACommandClaudeKnows() {
        val text = ClaudePlugin.closeDay()
        assertTrue(text.startsWith("---\ndescription: "))
        assertTrue(text.contains("\$ARGUMENTS"))
        assertTrue(text.contains(".ydeck.local.json") && text.contains("Never force-push"))
        val p = DeckJson.decode(File("../store/play/source/demo-projects.json").readText()).projects[0]
        assertTrue(ProjectFiles.briefing(p, Files.createTempDirectory("ydeck-brief").toFile()).contains("/ydeck:close-day"))
    }

    @Test fun aSessionFollowsItsOwnProcess() {
        val folder = "C:\\Work\\proc-${System.nanoTime()}"
        fun ev(name: String, s: String, vararg extra: Pair<String, String>) =
            JSONObject().put("hook_event_name", name).put("session_id", s).put("cwd", folder).apply { extra.forEach { (k, v) -> put(k, v) } }
        val t = System.currentTimeMillis()
        val proc = Proc(4242, 77)
        var running = true
        val s = "p-${System.nanoTime()}"
        // Asked once, at the first hook; Claude Code's list says nothing (it keeps none now).
        var asked = 0
        ClaudeLink.handle(ev("SessionStart", s), t - 60_000) { asked++; proc }
        ClaudeLink.handle(ev("UserPromptSubmit", s, "prompt" to "fix it"), t - 55_000) { asked++; proc }
        assertEquals(1, asked)
        assertEquals(proc, ClaudeLink.sessions.value[s]!!.proc)
        // Long work with no word from Claude: still working while its claude runs.
        ClaudeLink.tidy(t - 55_000 + 20 * 60_000, null) { running }
        assertEquals(ClaudeMode.WORKING, ClaudeLink.sessions.value[s]!!.mode)
        ClaudeLink.handle(ev("Stop", s, "last_assistant_message" to "Fixed."), t - 50_000)
        ClaudeLink.tidy(t, null) { running }
        assertEquals(ClaudeMode.OPEN, ClaudeLink.sessions.value[s]!!.mode)
        // The terminal closed: gone.
        running = false
        ClaudeLink.tidy(t + 2000, null) { running }
        assertNull(ClaudeLink.sessions.value[s])

        // Closed in the middle of work: over at once.
        running = true
        val w = "pw-${System.nanoTime()}"
        ClaudeLink.handle(ev("UserPromptSubmit", w, "prompt" to "long task"), t) { proc }
        running = false
        ClaudeLink.tidy(t + 2000, null) { running }
        assertEquals(ClaudeMode.DONE, ClaudeLink.sessions.value[w]!!.mode)
        ClaudeLink.handle(ev("SessionEnd", w), t + 3000)
    }

    @Test fun aTurnTheUserStoppedIsOver() {
        val transcript = Files.createTempFile("ydeck-transcript", ".jsonl").toFile()
        try {
            val s = "i-${System.nanoTime()}"
            val t = System.currentTimeMillis() - 60_000
            fun line(type: String, content: String) = JSONObject().put("isSidechain", false).put("type", type)
                .put("message", JSONObject().put("role", type).put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", content)))).toString()
            transcript.writeText(line("user", "fix it") + "\n" + line("assistant", "On it.") + "\n")
            ClaudeLink.handle(event("UserPromptSubmit", s, "prompt" to "fix it", "transcript_path" to transcript.path), t) { Proc(1, 1) }
            transcript.setLastModified(t + 1000)
            ClaudeLink.tidy(t + 10_000, null) { true }
            assertEquals(ClaudeMode.WORKING, ClaudeLink.sessions.value[s]!!.mode)
            // Esc: no hook says so, the transcript does.
            transcript.appendText(line("user", "[Request interrupted by user]") + "\n")
            transcript.setLastModified(t + 20_000)
            ClaudeLink.tidy(t + 30_000, null) { true }
            assertEquals(ClaudeMode.OPEN, ClaudeLink.sessions.value[s]!!.mode)
            ClaudeLink.handle(event("SessionEnd", s), t + 40_000)
        } finally { transcript.delete() }
    }

    @Test fun theTranscriptTellsStopsAndRemoteControl() {
        val told = Transcripts.Told()
        Transcripts.take(told, """{"isSidechain":false,"type":"system","subtype":"bridge_status","content":"/remote-control is active"}""")
        assertEquals(true, told.remote)
        // A tool's output that mentions a stop isn't one.
        Transcripts.take(told, """{"isSidechain":false,"type":"user","message":{"role":"user","content":[{"type":"tool_result","content":"[Request interrupted by user]"}]}}""")
        assertFalse(told.stopped)
        Transcripts.take(told, """{"isSidechain":false,"type":"user","message":{"role":"user","content":[{"type":"text","text":"[Request interrupted by user for tool use]"}]}}""")
        assertTrue(told.stopped)
        // A subagent's lines don't count.
        Transcripts.take(told, """{"isSidechain":true,"type":"assistant","message":{"role":"assistant","content":"go on"}}""")
        assertTrue(told.stopped)
        Transcripts.take(told, """{"isSidechain":false,"type":"system","subtype":"informational","content":"Remote Control disconnected — run /login"}""")
        assertEquals(false, told.remote)
    }
}
