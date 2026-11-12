package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Ran
import com.yonidev.ydeck.link.RunCommand
import com.yonidev.ydeck.link.Snapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class RunsTest {

    @Test fun oneLineOnlyNothingHiddenAfterANewline() {
        assertEquals("npm run build", RunCommand.clean("  npm run build  "))
        assertNull(RunCommand.clean("git status\nrm -rf ."))
        assertNull(RunCommand.clean("git status\rdel x"))
        assertNull(RunCommand.clean(""))
        assertNull(RunCommand.clean("x".repeat(RunCommand.MAX + 1)))
    }

    @Test fun aCommandGoesAndComesBackAsItWas() {
        val r = RunCommand("id1", "p1", "npm test", 123L)
        assertEquals(r, RunCommand.decode(r.encode()))
        // A note or a launch can't pass for one.
        assertNull(RunCommand.decode("""{"app":"YDeck","kind":"launch","line":"x"}""".toByteArray()))
        assertNull(RunCommand.decode("""{"app":"YDeck","kind":"run","line":"a\nb"}""".toByteArray()))
    }

    @Test fun theStateCarriesWhatBecameOfThemAndTheLinesAllowedAlways() {
        val s = Snapshot(1L, "PC", JSONObject(), emptyMap(), run = true,
            ran = listOf(Ran("id1", "p1", "npm test", Ran.DONE, code = 2, at = 5L)), runOk = mapOf("p1" to listOf("git status")))
        val back = Snapshot.decode(s.encode())
        assertEquals(true, back.run)
        assertEquals(s.ran, back.ran)
        assertEquals(s.runOk, back.runOk)
    }

    /** The exit code reaches its file, through each shell's own arguments as YDeck builds them (without the window). */
    @Test fun theExitCodeComesBack() {
        val dir = Files.createTempDirectory("ydeck-runs").toFile()
        try {
            for (shell in listOf(Shell.CMD, Shell.POWERSHELL).filter { Shells.exe(it) != null }) {
                val ok = File(dir, "${shell.id}-ok.exit")
                val bad = File(dir, "${shell.id}-bad.exit")
                run(Tools.watchedArgs(shell, Shells.exe(shell)!!.path, "git --version", ok), dir)
                run(Tools.watchedArgs(shell, Shells.exe(shell)!!.path, "git no-such-command", bad), dir)
                assertEquals("$shell ok", "0", ok.readText().trim())
                assertNotEquals("$shell failed", "0", bad.readText().trim())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Runs as the window would, but with nobody typing: the shell that stays open reads the end of input and goes. */
    private fun run(args: List<String>, dir: File) {
        val p = ProcessBuilder(args).directory(dir).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        p.outputStream.close()
        if (!p.waitFor(60, TimeUnit.SECONDS)) p.destroyForcibly()
    }
}
