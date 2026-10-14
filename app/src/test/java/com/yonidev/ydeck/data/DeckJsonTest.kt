package com.yonidev.ydeck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckJsonTest {

    private val project = Project(
        id = "yvoice", name = "YVoice", mono = "Y", color = 0xFF1A6FDB.toInt(), colorTemporary = false,
        kind = "אפליקציית Android", googleEmail = "dev@example.com",
        vercel = Browser("com.chrome.beta", "Chrome Beta"),
        actions = listOf(
            DeckAction(Service.of("play"), "https://play.google.com/console", "Play Console"),
            DeckAction(Service.of("link"), "https://yvoice.pages.dev", "האתר שלנו"),
        ),
        copies = listOf(CopyItem("Package", "com.yonidev.yvoice")),
        logo = "logos/yvoice-abc.png",
    )

    @Test fun roundTripKeepsEverything() {
        val deck = Deck(listOf(project, project.copy(id = "second", name = "Second", logo = null, vercel = null, googleEmail = null)))
        assertEquals(deck, DeckJson.decode(DeckJson.encode(deck).toString()))
    }

    @Test fun defaultLabelsAreNotWrittenButCustomOnesAre() {
        val actions = DeckJson.encodeProject(project).getJSONArray("actions")
        assertEquals(false, actions.getJSONObject(0).has("label"))
        assertEquals("האתר שלנו", actions.getJSONObject(1).getString("label"))
    }

    @Test fun readsTheRepoSeedFormat() {
        // The bundled projects.json refers to accounts by key instead of inline.
        val seed = """
            {
              "googleAccounts": { "main": "me@gmail.com" },
              "vercelAccounts": { "second": { "browser": "com.chrome.beta", "label": "Chrome Beta" } },
              "projects": [
                { "id": "a", "name": "A", "brand": { "color": "#00A35B", "temporary": true },
                  "google": "main", "vercel": "second",
                  "actions": [ { "service": "vercel", "url": "https://vercel.com/x" }, { "service": "github", "url": "" } ] },
                { "id": "b", "name": "", "actions": [] }
              ]
            }
        """.trimIndent()
        val deck = DeckJson.decode(seed)
        assertEquals(1, deck.projects.size) // the nameless project is skipped
        val a = deck.projects.single()
        assertEquals("me@gmail.com", a.googleEmail)
        assertEquals("com.chrome.beta", a.vercel?.pkg)
        assertEquals(0xFF00A35B.toInt(), a.color)
        assertEquals(true, a.colorTemporary)
        assertEquals(1, a.actions.size) // the link without a URL is skipped
        assertEquals("A", a.mono)
    }

    @Test fun parseColorHandlesBothForms() {
        assertEquals(0xFF3D35E0.toInt(), DeckJson.parseColor("#3D35E0"))
        assertEquals(0x803D35E0.toInt(), DeckJson.parseColor("#803D35E0"))
        assertNull(DeckJson.parseColor("TODO"))
        assertNull(DeckJson.parseColor("#12"))
    }

    @Test fun unknownServiceIdsSurviveAsPlainLinks() {
        val text = """{"projects":[{"id":"x","name":"X","actions":[{"service":"from-the-future","url":"https://a.b"}]}]}"""
        assertEquals("link", DeckJson.decode(text).projects.single().actions.single().service.id)
    }
}
