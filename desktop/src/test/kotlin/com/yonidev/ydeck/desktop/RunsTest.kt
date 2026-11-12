package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Ran
import com.yonidev.ydeck.link.RunCommand
import com.yonidev.ydeck.link.RunOut
import com.yonidev.ydeck.link.RunWatch
import com.yonidev.ydeck.link.Snapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun aCommandChoosesItsWindowAndOldPhonesKeepTheirs() {
        val r = RunCommand("id1", "p1", "npm test", 123L, window = false)
        assertEquals(r, RunCommand.decode(r.encode()))
        // A phone from before the choice: the window it always had.
        assertEquals(true, RunCommand.decode("""{"app":"YDeck","kind":"run","id":"x","line":"git status"}""".toByteArray())?.window)
    }

    @Test fun theLiveMessagesGoAndComeBackAndCantPassForEachOther() {
        val o = RunOut("id1", 40, "שלום\nworld", done = true, code = 3)
        assertEquals(o, RunOut.decode(o.encode()))
        val w = RunWatch("id1", 12)
        assertEquals(w, RunWatch.decode(w.encode()))
        assertNull(RunOut.decode(w.encode()))
        assertNull(RunWatch.decode(o.encode()))
        assertEquals((71..100).joinToString("\n"), Ran.tailOf((1..100).joinToString("\n") + "\n\n"))
    }

    /**
     * Each shell, through the script as YDeck runs it with no window: the output (Hebrew too) reaches the log, the
     * exit code its file, and a line's quotes reach the shell as typed.
     */
    @Test fun theOutputAndTheExitCodeComeBack() {
        val dir = Files.createTempDirectory("ydeck-runs").toFile()
        try {
            val lines = mapOf(
                Shell.CMD to listOf("echo \"a b\" & echo שלום", "git no-such-command"),
                Shell.POWERSHELL to listOf("Write-Output 'a b'; 'שלום'", "git no-such-command"),
                Shell.BASH to listOf("echo \"a b\"; echo שלום", "git no-such-command"),
            )
            for ((shell, two) in lines) {
                val exe = Shells.exe(shell)?.takeIf { it.isFile } ?: continue
                val ok = RunScript.files("${shell.id}-ok", shell)
                run(RunScript.args(shell, exe.path, two[0], ok, stay = false), dir)
                assertEquals("$shell exit", "0", ok.exit.readText().trim())
                val log = ok.log.readText(Charsets.UTF_8)
                println("$shell: $log")
                assertTrue("$shell log: $log", log.contains("שלום") && log.contains("a b"))
                val bad = RunScript.files("${shell.id}-bad", shell)
                run(RunScript.args(shell, exe.path, two[1], bad, stay = false), dir)
                assertNotEquals("$shell failed", "0", bad.exit.readText().trim())
                assertTrue("$shell error in log", bad.log.readText().contains("no-such-command"))
                RunScript.clean(ok); RunScript.clean(bad)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** As YDeck runs it with no window: nobody types, so input ends at once. */
    private fun run(args: List<String>, dir: File) {
        val p = ProcessBuilder(args).directory(dir).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        p.outputStream.close()
        if (!p.waitFor(60, TimeUnit.SECONDS)) p.destroyForcibly()
    }
}
