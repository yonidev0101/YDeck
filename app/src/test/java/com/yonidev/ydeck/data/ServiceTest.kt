package com.yonidev.ydeck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTest {

    private fun detected(url: String) = Service.detect(url)?.id

    @Test fun playConsoleAndStoreAreTellApartByPath() {
        assertEquals("play", detected("https://play.google.com/console/u/0/developers/123/app/456"))
        assertEquals("store", detected("https://play.google.com/store/apps/details?id=com.yonidev.yvoice"))
    }

    @Test fun mostSpecificPatternWins() {
        // github.com/copilot is its own service, the rest of github.com is GitHub.
        assertEquals("copilot", detected("https://github.com/copilot"))
        assertEquals("github", detected("https://github.com/yonidev0101/recipe-book"))
        // BigQuery lives inside the Cloud Console host.
        assertEquals("bigquery", detected("https://console.cloud.google.com/bigquery?project=x"))
        assertEquals("gcloud", detected("https://console.cloud.google.com/home"))
    }

    @Test fun subdomainsMatchTheirService() {
        assertEquals("vercel", detected("https://vercel.com/yonidev/site"))
        assertEquals("supabase", detected("https://abc123.supabase.co/project"))
        assertEquals("openai", detected("https://platform.openai.com/usage"))
    }

    @Test fun unknownSitesAreNotGuessed() {
        assertNull(detected("https://example.org/dashboard"))
        assertNull(detected("nonsense"))
    }

    @Test fun unknownIdFallsBackToPlainLink() {
        assertEquals("link", Service.of("no-such-service").id)
    }

    @Test fun catalogIdsAreUniqueAndKeepTheOriginalOnes() {
        val ids = Service.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // Saved projects refer to these; renaming one would break them.
        for (id in listOf("play", "firebase", "gmail", "drive", "analytics", "gcloud", "store", "site", "vercel", "github",
            "mongodb", "neon", "supabase", "railway", "sentry", "cloudinary", "cloudflare", "upstash", "mixpanel",
            "resend", "openai", "anthropic", "link")) {
            assertTrue("missing $id", id in ids)
        }
    }

    @Test fun googleServicesRouteThroughTheAccountChooser() {
        val p = Project.blank().copy(name = "P", googleEmail = "dev@example.com")
        val google = DeckAction(Service.of("firebase"), "https://console.firebase.google.com", "Firebase")
        val plain = DeckAction(Service.of("github"), "https://github.com/x", "GitHub")
        assertTrue(Router.targetUrl(p, google).startsWith("https://accounts.google.com/AccountChooser?Email=dev%40example.com"))
        assertEquals("https://github.com/x", Router.targetUrl(p, plain))
        // Without an email there is no account to choose.
        assertEquals("https://console.firebase.google.com", Router.targetUrl(p.copy(googleEmail = null), google))
    }
}
