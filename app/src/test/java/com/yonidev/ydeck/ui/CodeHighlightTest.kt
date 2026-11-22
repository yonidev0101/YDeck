package com.yonidev.ydeck.ui

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/** Code in colour: the right colour on the right words, line by line, a comment over lines included. */
class CodeHighlightTest {
    private fun AnnotatedString.colorOf(word: String) = spanStyles.firstOrNull { text.substring(it.start, it.end).contains(word) }?.item?.color

    @Test fun kotlin() {
        val l = highlightLines("/* a\n b */ fun main() {\n  val s = \"hi\" // x\n  if (n > 10) Log.d(s)\n}", "Main.kt")
        assertEquals(5, l.size)
        assertEquals(DarkPlus.comment, l[0].colorOf("a"))
        assertEquals(DarkPlus.comment, l[1].colorOf("b"))
        assertEquals(DarkPlus.keyword, l[1].colorOf("fun"))
        assertEquals(DarkPlus.function, l[1].colorOf("main"))
        assertEquals(DarkPlus.string, l[2].colorOf("hi"))
        assertEquals(DarkPlus.comment, l[2].colorOf("x"))
        assertEquals(DarkPlus.control, l[3].colorOf("if"))
        assertEquals(DarkPlus.number, l[3].colorOf("10"))
        assertEquals(DarkPlus.type, l[3].colorOf("Log"))
    }

    @Test fun jsonKeysAndValues() {
        val l = highlightLines("{\n  \"name\": \"YDeck\",\n  \"n\": 3\n}", "package.json")
        assertEquals(DarkPlus.property, l[1].colorOf("name"))
        assertEquals(DarkPlus.string, l[1].colorOf("YDeck"))
        assertEquals(DarkPlus.number, l[2].colorOf("3"))
    }

    @Test fun unknownStaysPlain() {
        assertEquals(0, highlight("whatever 123", "notes.weird").spanStyles.size)
    }
}
