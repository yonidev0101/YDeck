package com.yonidev.ydeck.desktop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The PC and the phone share one file format: a phone backup must open here and save back unchanged. */
class DeckJsonTest {
    private val phoneBackup = """
        {"version":2,"app":"YDeck","logos":{},"projects":[
          {"id":"nova","name":"Nova","mono":"N","kind":"אפליקציה + אתר","brand":{"color":"#0B0B0C"},"google":"dana@nova.dev",
           "vercel":{"browser":"com.chrome.beta","label":"Chrome Beta"},
           "actions":[{"service":"firebase","url":"https://console.firebase.google.com/project/nova"},
                      {"service":"link","url":"https://nova.dev/admin","label":"Admin"}],
           "copy":[{"label":"Package","value":"dev.nova.app"}]}]}
    """.trimIndent()

    @Test fun phoneBackupRoundTrips() {
        val deck = DeckJson.decode(phoneBackup)
        val p = deck.projects.single()
        assertEquals("dana@nova.dev", p.googleEmail)
        assertEquals(Browser("com.chrome.beta", "Chrome Beta"), p.vercel)
        assertEquals("Firebase", p.actions[0].service.label)
        assertEquals("Admin", p.actions[1].label)
        val again = DeckJson.decode(DeckJson.encode(deck).toString())
        assertEquals(deck, again)
    }

    @Test fun theRepoSeedFileOpens() {
        val seed = File("../projects.json").takeIf { it.exists() } ?: return
        assert(DeckJson.decode(seed.readText()).projects.isNotEmpty())
    }

    @Test fun pastedLinksAreRecognised() {
        assertEquals("supabase", Catalog.detect("https://supabase.com/dashboard/project/orbit")?.id)
        assertEquals("play", Catalog.detect("https://play.google.com/console/developers")?.id)
        assertEquals("store", Catalog.detect("https://play.google.com/store/apps/details?id=x")?.id)
        assertNull(Catalog.detect("not a link"))
        assertNotNull(Catalog.of("vercel").path)
    }

    @Test fun accountChooserKeepsTheTarget() {
        val u = Links.accountChooser("a@b.co", "https://mail.google.com/?x=1")
        assertEquals("https://accounts.google.com/AccountChooser?Email=a%40b.co&continue=https%3A%2F%2Fmail.google.com%2F%3Fx%3D1", u)
        assertEquals("nova", JSONObject(DeckJson.encodeProject(Project.sample().copy(id = "nova")).toString()).getString("id"))
    }
}
