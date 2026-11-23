package com.yonidev.ydeck.desktop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A project's folder marked trusted for Claude Code, and nothing else in its file changed. */
class ClaudeTrustTest {
    @Test fun trustsTheFolderAndKeepsTheRest() {
        val was = ClaudeTrust.config
        val f = File.createTempFile("claude", ".json")
        try {
            ClaudeTrust.config = f
            f.writeText("""{"numStartups": 7, "oauthAccount": {"x": 1}, "projects": {"C:\\Users\\u\\Old": {"hasTrustDialogAccepted": false, "allowedTools": ["a"]}, "C:/Other": {"hasTrustDialogAccepted": false}}}""")
            assertTrue(ClaudeTrust.trust(File("C:\\Users\\u\\Old")))
            assertTrue(ClaudeTrust.trust(File("C:\\Users\\u\\New one")))
            val o = JSONObject(f.readText())
            assertEquals(7, o.getInt("numStartups"))
            assertEquals(1, o.getJSONObject("oauthAccount").getInt("x"))
            val p = o.getJSONObject("projects")
            assertTrue(p.getJSONObject("C:\\Users\\u\\Old").getBoolean("hasTrustDialogAccepted"))
            assertEquals("a", p.getJSONObject("C:\\Users\\u\\Old").getJSONArray("allowedTools").getString(0))
            assertTrue(p.getJSONObject("C:/Users/u/New one").getBoolean("hasTrustDialogAccepted"))
            assertEquals(false, p.getJSONObject("C:/Other").getBoolean("hasTrustDialogAccepted"))
        } finally {
            ClaudeTrust.config = was
            f.delete()
        }
    }
}
