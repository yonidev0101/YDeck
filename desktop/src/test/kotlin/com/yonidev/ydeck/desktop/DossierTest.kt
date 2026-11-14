package com.yonidev.ydeck.desktop

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DossierTest {
    private lateinit var dir: File

    @Before fun setUp() { dir = Files.createTempDirectory("ydeck-dossier").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun local() = ProjectFiles.readLocal(dir)!!

    @Test fun readsBuiltAndTheGoalAsAgentsWriteThem() {
        File(dir, ProjectFiles.LOCAL).writeText("""{"now":"n","built":["Sign in",{"text":"Pay"},"","Sign in"],
            "goal":{"title":"First version","steps":[{"text":"Sign up","done":true},"Payments",{"text":""},{"text":"Store page","done":false}]}}""")
        val l = local()
        assertEquals(listOf("Sign in", "Pay"), l.built)
        val g = l.goal!!
        assertEquals("First version", g.title)
        assertEquals(listOf(GoalStep("Sign up", true), GoalStep("Payments", false), GoalStep("Store page", false)), g.steps)
        assertEquals(1 to 3, g.share)
        assertEquals(33, g.percent)
    }

    @Test fun aGoalWithoutTitleIsNoGoal() {
        File(dir, ProjectFiles.LOCAL).writeText("""{"goal":{"title":"  ","steps":["a"]}}""")
        assertNull(local().goal)
        assertNull(Goal("x", emptyList()).share)
    }

    @Test fun writingTheGoalAndBuiltKeepsEverythingElse() {
        File(dir, ProjectFiles.LOCAL).writeText("""{"now":"n","next":"x","left":["a"],"ideas":["i"],"done":["d"],"extra":42}""")
        ProjectFiles.writeGoal(dir, Goal("Live", listOf(GoalStep("Deploy", false))))
        ProjectFiles.writeList(dir, ProjectFiles.BUILT, listOf("Home page"))
        val l = local()
        assertEquals("Live", l.goal!!.title)
        assertEquals(listOf("Home page"), l.built)
        assertEquals(listOf("a"), l.left)
        assertEquals("x", l.next)
        assertEquals(42, JSONObject(File(dir, ProjectFiles.LOCAL).readText()).getInt("extra"))

        ProjectFiles.writeGoal(dir, null)
        assertNull(local().goal)
        assertEquals(listOf("Home page"), local().built)
    }

    @Test fun progressComesFromTheGoalOnly() {
        assertNull(Pulse(left = listOf("a", "b"), done = listOf("c")).progress())
        assertEquals(2 to 4, Pulse(goal = Goal("g", listOf(GoalStep("1", true), GoalStep("2", true), GoalStep("3", false), GoalStep("4", false)))).progress())
    }

    @Test fun aFirstReadAddsWithoutLosingWhatsThere() {
        assertEquals(listOf("Sign in", "Pay", "Calendar"), Dossier.merge(listOf("Sign in", "Pay"), listOf("pay", " Calendar ", "", "Sign in")))
    }

    @Test fun claudesAnswerIsReadIntoBuiltAndAGoal() {
        val f = Dossier.parse(JSONObject("""{"built":["A","A","B"," "],"goal":{"title":"Ship","steps":["one","two","one"]}}"""))
        assertEquals(listOf("A", "B"), f.built)
        assertEquals(Goal("Ship", listOf(GoalStep("one", false), GoalStep("two", false))), f.goal)
        // A goal with no steps isn't suggested.
        assertNull(Dossier.parse(JSONObject("""{"built":[],"goal":{"title":"Ship","steps":[]}}""")).goal)
    }

    @Test fun theBriefingTellsClaudeToKeepBuiltAndTheGoal() {
        File(dir, ProjectFiles.LOCAL).writeText("""{"built":["Sign in"],"goal":{"title":"Ship","steps":[{"text":"one","done":true},"two"]}}""")
        val p = Project("p1", "App", "A", 0, false, "web", null, null, emptyList(), emptyList())
        val text = ProjectFiles.briefing(p, dir)
        assertTrue(text.contains("\"built\""))
        assertTrue(text.contains("The project's goal: \"Ship\", 1 of 2 steps done: [x] one; [ ] two."))
        assertTrue(text.contains("What the project can already do: Sign in."))
    }
}
