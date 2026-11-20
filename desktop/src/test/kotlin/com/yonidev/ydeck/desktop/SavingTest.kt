package com.yonidev.ydeck.desktop

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SavingTest {
    private lateinit var dir: File

    @Before fun setUp() { dir = Files.createTempDirectory("ydeck-saving").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun git(vararg args: String) = assertTrue(args.joinToString(" "), Git.run(dir, args.toList(), errors = false).ok)

    private fun repo() {
        git("init", "-q", "-b", "main")
        git("config", "user.email", "t@example.com")
        git("config", "user.name", "T")
        git("config", "commit.gpgsign", "false")
        File(dir, "a.txt").writeText("one\n")
        git("add", "-A")
        git("commit", "-q", "-m", "First")
    }

    private fun facts(files: List<GitChange>, unmerged: List<Pair<String, String>> = emptyList(), remote: Boolean = true) = Saving.Facts(
        GitInfo("main", files.size, 0, 0, files = files, hasRemote = remote, upstream = "origin/main"),
        "main", unmerged, "", "", "", emptyList(), emptyList())

    @Test fun onlyTheStepsYDeckCanRunAsSaid() {
        val f = facts(listOf(GitChange(" M", "a.txt"), GitChange("??", "beta.png")), unmerged = listOf("partner" to "x"))
        val good = listOf(SaveStep(SaveStep.IGNORE, listOf("beta.png")), SaveStep(SaveStep.COMMIT, listOf("a.txt"), "Fix"),
            SaveStep(SaveStep.MERGE, branch = "partner"), SaveStep(SaveStep.PUSH))
        assertEquals(good, Saving.valid(good, f))
        // A file not in the list, a commit without a message, a branch that isn't waiting, ignoring a tracked file: none at all.
        assertTrue(Saving.valid(good + SaveStep(SaveStep.COMMIT, listOf("other.txt"), "x"), f).isEmpty())
        assertTrue(Saving.valid(listOf(SaveStep(SaveStep.COMMIT, emptyList(), " ")), f).isEmpty())
        assertTrue(Saving.valid(listOf(SaveStep(SaveStep.MERGE, branch = "elsewhere")), f).isEmpty())
        assertTrue(Saving.valid(listOf(SaveStep(SaveStep.IGNORE, listOf("a.txt"))), f).isEmpty())
        // No server: no push.
        assertTrue(Saving.valid(listOf(SaveStep(SaveStep.PUSH)), facts(emptyList(), remote = false)).isEmpty())
    }

    @Test fun pullOnlyWhenTheServerHasNews() {
        val behind = Saving.Facts(GitInfo("main", 0, 0, 2, hasRemote = true, upstream = "origin/main"), "main", emptyList(), "", "", "", emptyList(), emptyList())
        assertEquals(listOf(SaveStep(SaveStep.PULL)), Saving.valid(listOf(SaveStep(SaveStep.PULL)), behind))
        // Not clean while the server has news: Claude is asked what to do.
        assertTrue(!behind.clean)
        assertTrue(Saving.valid(listOf(SaveStep(SaveStep.PULL)), facts(listOf(GitChange(" M", "a.txt")))).isEmpty())
        // A pull with nothing to bring is left out; the commit and push after it still stand.
        val rest = listOf(SaveStep(SaveStep.COMMIT, listOf("a.txt"), "Fix"), SaveStep(SaveStep.PUSH))
        assertEquals(rest, Saving.valid(listOf(SaveStep(SaveStep.PULL)) + rest, facts(listOf(GitChange(" M", "a.txt")))))
        assertEquals(SaveStep(SaveStep.PULL), Saving.stepOf(JSONObject("""{"kind":"pull"}""")))
    }

    @Test fun theBranchWeAreOnMergesAfterItsCommit() {
        val f = Saving.Facts(GitInfo("rules", 1, 0, 0, files = listOf(GitChange(" M", "a.txt")), hasRemote = false), "main", emptyList(), "", "", "", emptyList(), emptyList())
        val steps = listOf(SaveStep(SaveStep.COMMIT, emptyList(), "Rules"), SaveStep(SaveStep.MERGE, branch = "rules"))
        assertEquals(steps, Saving.valid(steps, f))
        // Without the commit before it, there's nothing on the branch to merge.
        assertTrue(Saving.valid(listOf(SaveStep(SaveStep.MERGE, branch = "rules")), f).isEmpty())
    }

    @Test fun aNewFolderWithOrWithoutItsSlash() {
        val f = facts(listOf(GitChange(" M", "a.txt"), GitChange("??", "scripts/tmp/")))
        val steps = listOf(SaveStep(SaveStep.IGNORE, listOf("scripts/tmp")), SaveStep(SaveStep.COMMIT, listOf("a.txt"), "Fix"))
        assertEquals(steps, Saving.valid(steps, f))
        assertEquals(listOf(SaveStep(SaveStep.IGNORE, listOf("scripts/tmp/x.log"))), Saving.valid(listOf(SaveStep(SaveStep.IGNORE, listOf("scripts/tmp/x.log"))), f))
        assertTrue(Saving.valid(listOf(SaveStep(SaveStep.IGNORE, listOf("scripts"))), f).isEmpty())
    }

    @Test fun unknownKindsAreDropped() {
        assertNull(Saving.stepOf(JSONObject("""{"kind":"reset"}""")))
        assertEquals(SaveStep(SaveStep.PUSH), Saving.stepOf(JSONObject("""{"kind":"push"}""")))
    }

    @Test fun theCardKeepsAcrossAStart() {
        val c = SaveCard("One file open.", "Commit it.", listOf(SaveStep(SaveStep.COMMIT, listOf("a"), "m")), "abc")
        assertEquals(c, SaveCard.of(c.toJson()))
    }

    @Test fun cleanAndSignatureFollowGit() {
        repo()
        val clean = Saving.facts(dir)!!
        assertTrue(clean.clean)
        assertTrue(Saving.cleanCard(clean).clean)
        File(dir, "a.txt").writeText("two\n")
        val changed = Saving.facts(dir)!!
        assertTrue(!changed.clean)
        assertNotEquals(clean.signature, changed.signature)
        assertTrue(changed.diff.contains("+two"))
    }

    @Test fun aBranchWaitingToMergeIsSeenAndMovesMainUp() {
        repo()
        git("switch", "-q", "-c", "partner")
        File(dir, "b.txt").writeText("b\n")
        git("add", "-A")
        git("commit", "-q", "-m", "Partner work")
        val f = Saving.facts(dir)!!
        assertEquals(listOf("partner" to "Partner work"), f.unmerged)
        assertNull(Saving.merge(dir, "partner", "main"))
        assertTrue(Saving.facts(dir)!!.unmerged.isEmpty())
    }

    @Test fun mainThatMovedOnIsNotForced() {
        repo()
        git("switch", "-q", "-c", "partner")
        File(dir, "b.txt").writeText("b\n"); git("add", "-A"); git("commit", "-q", "-m", "B")
        git("switch", "-q", "main")
        File(dir, "c.txt").writeText("c\n"); git("add", "-A"); git("commit", "-q", "-m", "C")
        git("switch", "-q", "partner")
        assertNotNull(Saving.merge(dir, "partner", "main"))
    }

    @Test fun ignoringAddsEachFileOnce() {
        File(dir, ".gitignore").writeText("build")
        assertNull(Saving.ignore(dir, listOf("beta.png", "build")))
        assertNull(Saving.ignore(dir, listOf("beta.png")))
        assertEquals("build\n/beta.png\n", File(dir, ".gitignore").readText())
    }
}
