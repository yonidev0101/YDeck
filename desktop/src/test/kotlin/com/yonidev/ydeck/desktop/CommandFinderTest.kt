package com.yonidev.ydeck.desktop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Claude's commands: a line each, no blanks, no multi-line ones, no repeats; the personal file's commands are read. */
class CommandFinderTest {
    @Test fun readsClaudesAnswer() {
        val a = JSONObject("""{"commands":[{"label":"שרת פיתוח","line":" pnpm dev "},{"label":"x","line":""},{"label":"bad","line":"a\nb"},{"label":"again","line":"pnpm dev"},{"line":"pnpm test"}]}""")
        assertEquals(listOf(Command("שרת פיתוח", "pnpm dev"), Command("", "pnpm test")), CommandFinder.parse(a))
    }

    @Test fun thePromptNamesWhatIsKnown() {
        val p = CommandFinder.prompt(Project.blank().copy(id = "p", name = "YDeck"), listOf(Command("Build", "gradlew build")))
        assertTrue(p.contains("gradlew build"))
    }

    @Test fun thePersonalFileHoldsCommands() {
        val dir = createTempDir()
        File(dir, ProjectFiles.LOCAL).writeText("""{"now":"x","commands":[{"label":"Tests","line":"npm test"},{"line":""}]}""")
        assertEquals(listOf(Command("Tests", "npm test")), ProjectFiles.readLocal(dir)?.commands)
        dir.deleteRecursively()
    }
}
