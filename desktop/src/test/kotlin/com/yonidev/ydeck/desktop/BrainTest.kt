package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrainTest {
    init { I18n.forced = Lang.EN }

    @Test fun handoffsAreReadFromClaudesAnswer() {
        val h = Handoffs.parse("Here it is:\n```json\n{\"done\": \"Added the webhook\", \"next\": \"Test it in production\"}\n```", "Claude", 5)!!
        assertEquals(Handoff("Added the webhook", "Test it in production", 5, "Claude"), h)
        assertNull(Handoffs.parse("Sorry, I can't.", "Claude", 5))
        assertNull(Handoffs.parse("{\"done\": \"\", \"next\": \"\"}", "Claude", 5))
    }

    /** Decisions and a handoff go into the project's files, and from there into every session's briefing and AGENTS.md. */
    @Test fun theProjectRemembers() {
        val dir = kotlin.io.path.createTempDirectory("ydeck-brain").toFile()
        try {
            File(dir, ".ydeck.json").writeText("{\"summary\": \"A booking app.\", \"links\": [{\"label\": \"Site\", \"url\": \"https://example.com\"}]}")
            ProjectFiles.writeDecisions(dir, listOf(Decision("Payments go through Stripe only", "2026-10-13")))
            ProjectFiles.writeNext(dir, "Ship deposits")
            ProjectFiles.writeHandoff(dir, Handoff("Added the deposit webhook", "Test it in production", 1_760_000_000_000, "Claude"))
            val shared = ProjectFiles.readShared(dir)!!
            assertEquals("A booking app.", shared.summary)
            assertEquals(1, shared.links.size)
            assertEquals(listOf(Decision("Payments go through Stripe only", "2026-10-13")), shared.decisions)
            val local = ProjectFiles.readLocal(dir)!!
            assertEquals("Ship deposits", local.next)
            assertEquals("Test it in production", local.handoff!!.next)
            // An old handoff's next step was answered by what was written since: it's no longer suggested.
            assertEquals("", local.suggestion)
            ProjectFiles.writeHandoff(dir, Handoff("Added the deposit webhook", "Test it in production", System.currentTimeMillis(), "Claude"))
            assertEquals("Test it in production", ProjectFiles.readLocal(dir)!!.suggestion)

            val brief = ProjectFiles.briefing(Project.sample(), dir)
            assertTrue(brief.contains("Payments go through Stripe only"))
            assertTrue(brief.contains("Added the deposit webhook"))

            // No AGENTS.md: nothing is written. With one: YDeck's block goes in, and the rest of the file stays.
            AgentsFile.sync(dir)
            assertTrue(!File(dir, "AGENTS.md").exists())
            File(dir, "AGENTS.md").writeText("# Agents\n\nUse pnpm.\n")
            AgentsFile.sync(dir)
            val text = File(dir, "AGENTS.md").readText()
            assertTrue(text.startsWith("# Agents\n\nUse pnpm.\n"))
            assertTrue(text.contains("- Payments go through Stripe only (2026-10-13)"))
            // Where it stands changes with every session: it stays in the personal file, not in a committed one.
            assertTrue(!text.contains("Ship deposits") && !text.contains("deposit webhook"))
            ProjectFiles.writeHandoff(dir, Handoff("Something else", "And then", System.currentTimeMillis(), "Claude"))
            ProjectFiles.writeNext(dir, "Another step")
            val stamp = File(dir, "AGENTS.md").lastModified()
            AgentsFile.sync(dir)
            assertEquals(text, File(dir, "AGENTS.md").readText())
            assertEquals(stamp, File(dir, "AGENTS.md").lastModified())
            // Again, and after a change: one block, kept current.
            ProjectFiles.writeDecisions(dir, listOf(Decision("Hebrew first", "2026-10-14")))
            AgentsFile.sync(dir); AgentsFile.sync(dir)
            val again = File(dir, "AGENTS.md").readText()
            assertEquals(1, Regex("ydeck:start").findAll(again).count())
            assertTrue(again.contains("Hebrew first") && !again.contains("Stripe only"))
            assertTrue(again.contains("Use pnpm."))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Claude's suggestions, less what the project already has, and nothing that isn't a real address or command. */
    @Test fun fillKeepsOnlyWhatIsMissing() {
        val p = Project.sample()
        val local = Local(commands = listOf(Command("dev", "pnpm dev")))
        val known = p.actions.first().url
        val answer = "Sure:\n{\"kind\": \"Next.js web app\", \"summary\": \"A booking app for clinics.\", " +
            "\"links\": [{\"label\": \"Site\", \"url\": \"https://nova.dev\"}, {\"label\": \"Old\", \"url\": \"$known/\"}, {\"label\": \"Bad\", \"url\": \"ftp://x\"}], " +
            "\"commands\": [{\"label\": \"dev\", \"line\": \"pnpm dev\"}, {\"label\": \"test\", \"line\": \"pnpm test\"}]}"
        val f = ClaudeFill.parse(answer, p, local)!!
        assertEquals("Next.js web app", f.kind)
        assertEquals(listOf("https://nova.dev"), f.links.map { it.url })
        assertEquals(listOf("pnpm test"), f.commands.map { it.line })
        assertNull(ClaudeFill.parse("I couldn't read it.", p, local))
        assertTrue(ClaudeFill.prompt(p, local).contains("pnpm dev"))
    }

    @Test fun blocksMergeIntoAnyFile() {
        val block = "<!-- ydeck:start (x) -->\nnew\n<!-- ydeck:end -->"
        assertEquals(block + "\n", AgentsFile.merge("", block))
        assertEquals("a\n\n" + block + "\n", AgentsFile.merge("a\n", block))
        assertEquals("a\n" + block + "\nb", AgentsFile.merge("a\n<!-- ydeck:start -->\nold\n<!-- ydeck:end -->\nb", block))
    }
}
