package com.yonidev.ydeck.ui

import com.yonidev.ydeck.link.FileCommit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The graph's lanes: a branch splits off to its own lane, and comes back in where it was merged. */
class GitGraphTest {
    private fun c(h: String, vararg p: String) = FileCommit(h, 0, "", h, p.toList())

    @Test fun branchAndMerge() {
        // m merges s (side) into b (main); s and b both come from a.
        val rows = graphOf(listOf(c("m", "b", "s"), c("s", "a"), c("b", "a"), c("a")))
        val (m, s, b, a) = rows
        assertEquals(0, m.lane); assertTrue(m.merge); assertFalse(m.fromAbove); assertEquals(listOf(1), m.forks)
        assertEquals(1, s.lane); assertTrue(s.fromAbove); assertEquals(listOf(0), s.through)
        assertEquals(0, b.lane); assertEquals(listOf(1), b.through)
        // a: lane 0 goes on into it, and lane 1 (side) joins it.
        assertEquals(0, a.lane); assertEquals(listOf(1), a.joins); assertFalse(a.toBelow)
    }

    @Test fun straightLine() {
        val rows = graphOf(listOf(c("3", "2"), c("2", "1"), c("1")))
        assertTrue(rows.all { it.lane == 0 && it.through.isEmpty() })
        assertTrue(rows[1].fromAbove && rows[1].toBelow)
    }
}
