package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Snapshot
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlanTest {
    private lateinit var dir: File

    @Before fun setUp() { dir = Files.createTempDirectory("ydeck-plan").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun local() = ProjectFiles.readLocal(dir)!!

    @Test fun readsTheListsAgentsWrite() {
        // Strings, or objects with a "text", as an agent may write them; blanks and repeats dropped.
        File(dir, ProjectFiles.LOCAL).writeText("""{"now":"n","next":"x","left":["a",{"text":"b"},"", "a"],"ideas":["i"],"done":["d"]}""")
        val l = local()
        assertEquals(listOf("a", "b"), l.left)
        assertEquals(listOf("i"), l.ideas)
        assertEquals(listOf("d"), l.done)
        assertEquals("x", l.next)
    }

    @Test fun movesThingsBetweenTheListsAndKeepsTheRest() {
        File(dir, ProjectFiles.LOCAL).writeText("""{"now":"n","next":"x","handoff":{"done":"h","next":"y","agent":"Claude","at":"2026-11-12T10:00:00Z"}}""")
        Plan.add(dir, Plan.LEFT, "first")
        Plan.add(dir, Plan.LEFT, "second")
        Plan.add(dir, Plan.IDEAS, "maybe")
        assertEquals(listOf("first", "second"), local().left)

        Plan.finish(dir, "first")
        assertEquals(listOf("second"), local().left)
        assertEquals(listOf("first"), local().done)

        Plan.take(dir, "maybe")
        assertEquals(listOf("second", "maybe"), local().left)
        assertTrue(local().ideas.isEmpty())

        Plan.remove(dir, Plan.LEFT, "second")
        assertEquals(listOf("maybe"), local().left)

        // What else the file holds stays: now, next, the handoff.
        val o = JSONObject(File(dir, ProjectFiles.LOCAL).readText())
        assertEquals("n", o.getString("now"))
        assertEquals("x", o.getString("next"))
        assertEquals("h", local().handoff?.done)
    }

    @Test fun keepsTheLastTenDone() {
        repeat(12) { Plan.add(dir, Plan.LEFT, "t$it"); Plan.finish(dir, "t$it") }
        assertEquals(Plan.KEEP_DONE, local().done.size)
        assertEquals("t11", local().done.first())
    }

    @Test fun theBriefingSaysWhatIsLeftAndThatIdeasWait() {
        Plan.add(dir, Plan.LEFT, "ship it")
        Plan.add(dir, Plan.IDEAS, "a dark mode")
        val p = Project.sample().copy(id = "plan-test", name = "P")
        val text = ProjectFiles.briefing(p, dir)
        assertTrue(text.contains("Left to do: ship it."))
        assertTrue(text.contains("a dark mode"))
        assertTrue(text.contains("\"ideas\""))
        assertTrue(text.contains("Only once they said so clearly"))
    }

    @Test fun theListsReachThePhone() {
        val m = Memory(next = "x", left = listOf("a", "b"), ideas = listOf("i"), doneItems = listOf("d"))
        val back = Snapshot.memoryOf(with(Snapshot) { m.toJson() })
        assertEquals(m.left, back.left)
        assertEquals(m.ideas, back.ideas)
        assertEquals(m.doneItems, back.doneItems)
    }
}
