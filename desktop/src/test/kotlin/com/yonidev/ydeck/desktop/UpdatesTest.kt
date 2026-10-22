package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun versionsCompareByNumber() {
        assertTrue(Updates.newer("1.18.10", "1.18.9"))
        assertTrue(Updates.newer("1.19.0", "1.18.5"))
        assertTrue(Updates.newer("2.0", "1.99.99"))
        assertFalse(Updates.newer("1.18.5", "1.18.5"))
        assertFalse(Updates.newer("1.18.4", "1.18.5"))
        assertFalse(Updates.newer("1.18", "1.18.0"))
    }

    @Test fun notesLoseTheirMarkdownAndTheInstallPart() {
        val md = "**Every project.** Intro.\n\n### New\n- **A widget.** It [lists](https://x.y) `Now`.\n\n### Install\nDownload it.\n\n**Full Changelog**: https://…"
        assertEquals("Every project. Intro.\n\nNew\n• A widget. It lists Now.", plain(md))
    }
}
