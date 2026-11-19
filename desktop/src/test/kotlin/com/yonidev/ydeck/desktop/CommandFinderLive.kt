package com.yonidev.ydeck.desktop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Claude really finds this repository's commands (reading only, nothing kept). Run with -Plive. */
class CommandFinderLive {
    @Test fun live() {
        if (System.getProperty("live") == null) return
        val dir = File("..").canonicalFile
        val answer = ClaudeJson.ask(dir, CommandFinder.prompt(Project.sample().copy(id = "ydeck", name = "YDeck"), emptyList()), CommandFinder.schema(), tools = true, minutes = 3)
        val found = CommandFinder.parse(answer)
        found.forEach { println("FOUND: ${it.label} | ${it.line}") }
        assertTrue(found.any { "gradlew" in it.line })
    }
}
