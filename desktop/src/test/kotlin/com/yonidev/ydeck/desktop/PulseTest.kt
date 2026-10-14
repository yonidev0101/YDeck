package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Where a project stands: time phrases, .env keys, versions, dev servers, Claude Code's folders, what can be freed. */
class PulseTest {
    init { I18n.forced = Lang.HE }

    @Test fun timeReadsNaturally() {
        val now = 1_000_000_000_000L
        val min = 60_000L
        assertEquals("עכשיו", ago(now - 30_000, now))
        assertEquals("לפני 5 דקות", ago(now - 5 * min, now))
        assertEquals("לפני שעה", ago(now - 70 * min, now))
        assertEquals("אתמול", ago(now - 30 * 60 * min, now))
        assertEquals("לפני 3 ימים", ago(now - 3 * 24 * 60 * min, now))
        assertEquals("לפני 2 שבועות", ago(now - 15 * 24 * 60 * min, now))
        assertEquals("לפני 2 חודשים", ago(now - 65 * 24 * 60 * min, now))
    }

    @Test fun envKeysButNeverValues() {
        assertEquals(listOf("A", "B_2", "next.public"), Env.keys("# comment\nA=1\n\nexport B_2=\"x\"\nnext.public=\n=nokey\n  # A=2"))
        val dir = Files.createTempDirectory("ydeck-env").toFile()
        try {
            File(dir, ".env.example").writeText("DATABASE_URL=\nSTRIPE_KEY=\nPORT=3000\n")
            assertEquals(listOf("DATABASE_URL", "STRIPE_KEY", "PORT"), Env.missing(dir))
            File(dir, ".env").writeText("DATABASE_URL=postgres://x\n")
            File(dir, ".env.local").writeText("PORT=4000\n")
            assertEquals(listOf("STRIPE_KEY"), Env.missing(dir))
        } finally { dir.deleteRecursively() }
        assertEquals(emptyList<String>(), Env.missing(Files.createTempDirectory("ydeck-noenv").toFile()))
    }

    @Test fun versionsFromAndroidAndNode() {
        val dir = Files.createTempDirectory("ydeck-ver").toFile()
        try {
            assertNull(Versions.of(dir))
            File(dir, "package.json").writeText("""{"name":"x","version":"2.4.1"}""")
            assertEquals("2.4.1", Versions.of(dir))
            File(dir, "app").mkdirs()
            File(dir, "app/build.gradle.kts").writeText("android {\n defaultConfig {\n  versionCode = 8\n  versionName = \"0.8\"\n }\n}")
            assertEquals("0.8 · 8", Versions.of(dir))
        } finally { dir.deleteRecursively() }
    }

    @Test fun netstatListeners() {
        val text = """
            Active Connections

              Proto  Local Address          Foreign Address        State           PID
              TCP    0.0.0.0:135            0.0.0.0:0              LISTENING       1184
              TCP    127.0.0.1:5173         0.0.0.0:0              LISTENING       9120
              TCP    127.0.0.1:5173         127.0.0.1:50000        ESTABLISHED     9120
              TCP    [::1]:3000             [::]:0                 LISTENING       7000
              TCP    [::1]:24678            [::]:0                 LISTENING       7000
              TCP    0.0.0.0:445            0.0.0.0:0              LISTENING       4
        """.trimIndent()
        val l = Servers.parseNetstat(text)
        assertEquals(setOf(5173), l[9120L])
        assertEquals(setOf(3000, 24678), l[7000L])
        assertFalse(4L in l) // System
    }

    @Test fun claudeKeepsAFolderPerPath() {
        assertEquals("C--Users-user-Desktop-YoniDev-projects-folder-widget",
            Claude.sessionsDir(File("C:\\Users\\user\\Desktop\\YoniDev projects\\folder widget")).name)
    }

    @Test fun onlyWhatToolsRebuild() {
        val dir = Files.createTempDirectory("ydeck-clean").toFile()
        try {
            File(dir, "package.json").writeText("{}")
            File(dir, "node_modules/x").mkdirs(); File(dir, "node_modules/x/a.js").writeText("x".repeat(2000))
            File(dir, ".next/cache").mkdirs()
            File(dir, "dist").mkdirs(); File(dir, "dist/app.msi").writeText("keep")
            File(dir, "build").mkdirs() // no build.gradle beside it: not Gradle's, stays
            File(dir, "settings.gradle.kts").writeText("")
            File(dir, ".gradle").mkdirs()
            File(dir, "app/build/outputs").mkdirs(); File(dir, "app/build.gradle.kts").writeText("")
            File(dir, "app/build/outputs/x.apk").writeText("apk")
            File(dir, "src/build").mkdirs()
            val found = Cleaner.find(dir).map { it.first.relativeTo(dir).path.replace('\\', '/') }.sorted()
            assertEquals(listOf(".gradle", ".next", "app/build", "node_modules"), found)
            assertEquals(2000L, Cleaner.size(File(dir, "node_modules")))
            // Nothing outside the project, and only the known names.
            assertFalse(Cleaner.remove(File(dir, "app"), Junk(File(dir, "node_modules"), 1, "")))
            assertFalse(Cleaner.remove(dir, Junk(File(dir, "dist"), 1, "")))
            assertTrue(File(dir, "dist/app.msi").exists())
            assertTrue(Cleaner.remove(dir, Junk(File(dir, "node_modules"), 1, "")))
            assertFalse(File(dir, "node_modules").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun bytesRead() {
        assertEquals("1KB", bytes(10))
        assertEquals("380MB", bytes(380L shl 20))
        assertEquals("4.2GB", bytes((4.2 * (1L shl 30)).toLong()))
    }
}
