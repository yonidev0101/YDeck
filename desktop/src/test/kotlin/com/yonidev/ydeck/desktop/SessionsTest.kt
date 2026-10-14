package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionsTest {
    init { I18n.forced = Lang.EN }

    private fun repo(): File {
        val dir = kotlin.io.path.createTempDirectory("ydeck-session").toFile()
        Git.run(dir, listOf("init", "-b", "main"))
        Git.run(dir, listOf("config", "user.name", "Test"))
        Git.run(dir, listOf("config", "user.email", "test@example.com"))
        File(dir, "app.kt").writeText("fun main() {}\n")
        File(dir, "keep.txt").writeText("mine\n")
        File(dir, ".gitignore").writeText("build/\n")
        Git.run(dir, listOf("add", "-A"))
        Git.run(dir, listOf("commit", "-m", "Start"))
        return dir
    }

    /** A snapshot keeps the code (new files too, never ignored ones) and touches nothing: not the index, not a branch. */
    @Test fun snapshotsLeaveTheFolderAlone() {
        if (!Git.installed) return
        val dir = repo()
        try {
            File(dir, "app.kt").writeText("fun main() { println() }\n")
            File(dir, "new.kt").writeText("val x = 1\n")
            File(dir, "build").mkdirs(); File(dir, "build/out.bin").writeText("ignored")
            val statusBefore = Git.run(dir, listOf("status", "--porcelain")).out
            val head = Git.run(dir, listOf("rev-parse", "HEAD")).out.trim()
            val a = Snapshot.take(dir, "test", "a")!!
            assertEquals(statusBefore, Git.run(dir, listOf("status", "--porcelain")).out)
            assertEquals(head, Git.run(dir, listOf("rev-parse", "HEAD")).out.trim())
            assertEquals(a, Git.run(dir, listOf("rev-parse", "refs/ydeck/sessions/a")).out.trim())
            assertEquals(setOf("app.kt", "new.kt"), Snapshot.has(dir, a, listOf("app.kt", "new.kt", "build/out.bin", "gone.kt")))
            File(dir, "new.kt").delete()
            File(dir, "keep.txt").writeText("changed\n")
            File(dir, "added.kt").writeText("x\n")
            val b = Snapshot.take(dir, "test", "b")!!
            assertEquals(listOf("A added.kt", "M keep.txt", "D new.kt"), Snapshot.changes(dir, a, b).map { "${it.code} ${it.path}" })
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A session's receipt, then putting its files back, then undoing that: the files the user had before stay theirs. */
    @Test fun aSessionCanBePutBackAndBrought() {
        if (!Git.installed) return
        val dir = repo()
        val store = File.createTempFile("receipts", ".json")
        val was = Settings.state.value
        try {
            Receipts.store = store
            val p = Project.sample()
            Settings.preview(Settings.State(local = mapOf(p.id to Local(folder = dir.path))))
            // The user's own change before the session: it must survive everything below.
            File(dir, "keep.txt").writeText("mine, edited\n")
            Receipts.begin("s1", Agent.CLAUDE, dir.path, "Add a login screen", 1_000)
            Receipts.settle()
            // The agent works.
            File(dir, "app.kt").writeText("fun main() { login() }\n")
            File(dir, "login.kt").writeText("fun login() {}\n")
            Receipts.end("s1", 2_000)
            Receipts.settle()
            val r = Receipts.find("s1")!!
            assertEquals("Add a login screen", r.title)
            assertEquals(listOf("app.kt", "login.kt"), r.files.map { it.path }.sorted())
            assertTrue(Receipts.diff(r, "app.kt").any { it.kind == '+' && "login()" in it.text })

            assertNull(Receipts.rewind(r))
            assertEquals("fun main() {}\n", File(dir, "app.kt").readText().replace("\r", ""))
            assertFalse(File(dir, "login.kt").exists())
            assertEquals("mine, edited\n", File(dir, "keep.txt").readText())
            val back = Receipts.find("s1")!!
            assertNotNull(back.rewound)

            assertNull(Receipts.undoRewind(back))
            assertEquals("fun main() { login() }\n", File(dir, "app.kt").readText().replace("\r", ""))
            assertEquals("fun login() {}\n", File(dir, "login.kt").readText().replace("\r", ""))
            assertNull(Receipts.find("s1")!!.rewound)
            // Kept on disk for the next start.
            assertTrue(store.readText().contains("Add a login screen"))
        } finally {
            Settings.preview(was)
            dir.deleteRecursively()
            store.delete()
        }
    }

    @Test fun receiptsSurviveJson() {
        val r = Receipt("id", Agent.CODEX, "C:\\work\\app", 1, 2, listOf("Fix it"), "a".repeat(40), "b".repeat(40), listOf(GitChange("M", "src/a.kt"), GitChange("A", "b c.kt")))
        assertEquals(r, Receipts.fromJson(Receipts.toJson(r)))
    }

    @Test fun loopsSayWhatIsOpen() {
        val git = GitInfo("main", 3, 2, 1, files = listOf(GitChange(" M", "a.kt"), GitChange("??", "b.kt"), GitChange(" M", "c.kt")), upstream = "origin/main", hasRemote = true, lastAt = 1)
        val receipt = Receipt("s", Agent.CLAUDE, "x", 0, 1000, listOf("Do it"), "a", "b", listOf(GitChange("M", "a.kt"), GitChange("A", "b.kt")))
        val loops = Loops.of(git, Pulse(ports = listOf(3000)), null, listOf(receipt), now = 2000)
        assertEquals(listOf(LoopKind.SESSION, LoopKind.UNCOMMITTED, LoopKind.PUSH, LoopKind.PULL, LoopKind.SERVER), loops.map { it.kind })
        assertEquals("Claude's session: 2 files not committed", loops[0].title)
        assertEquals("1 other uncommitted change", loops[1].title)
        // All committed and pushed: nothing open.
        assertEquals(emptyList<Loop>(), Loops.of(GitInfo("main", 0, 0, 0, upstream = "origin/main", hasRemote = true, lastAt = 1), Pulse(), null, listOf(receipt), now = 2000))
        val waiting = ClaudeSession("w", "x", ClaudeMode.WAITING, 0, "Allow edit?", null, 0)
        assertEquals(LoopKind.AGENT, Loops.of(null, null, waiting, emptyList()).single().kind)
    }

    @Test fun codexSessionsNameTheirFolder() {
        val root = kotlin.io.path.createTempDirectory("codex").toFile()
        try {
            val d = java.time.LocalDate.now()
            val day = File(root, "%04d/%02d/%02d".format(d.year, d.monthValue, d.dayOfMonth)).apply { mkdirs() }
            File(day, "rollout-2026-abc.jsonl").writeText("{\"timestamp\":\"x\",\"type\":\"session_meta\",\"payload\":{\"id\":\"abc\",\"cwd\":\"C:\\\\work\\\\app\"}}\n{\"type\":\"event\"}\n")
            File(day, "rollout-old.jsonl").apply { writeText("{\"payload\":{\"cwd\":\"C:\\\\x\"}}\n"); setLastModified(System.currentTimeMillis() - 3L * 86_400_000) }
            val found = Codex.recent(root)
            assertEquals(listOf("rollout-2026-abc" to "C:\\work\\app"), found.map { it.first to it.second })
        } finally {
            root.deleteRecursively()
        }
    }
}
