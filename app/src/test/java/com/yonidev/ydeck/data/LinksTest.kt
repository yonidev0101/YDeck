package com.yonidev.ydeck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {

    @Test fun hostDropsWwwAndCase() {
        assertEquals("github.com", Links.host("https://WWW.GitHub.com/yonidev0101"))
    }

    @Test fun hostRejectsNonAddresses() {
        assertNull(Links.host("https://"))
        assertNull(Links.host("not a url"))
        assertNull(Links.host("https://localhost"))
    }

    @Test fun displayIsHostAndPathWithoutTrailingSlash() {
        assertEquals("play.google.com/console", Links.display("https://play.google.com/console/"))
        assertEquals("vercel.com/team/app", Links.display("https://vercel.com/team/app?tab=logs"))
    }

    @Test fun accountChooserEncodesEmailAndTarget() {
        assertEquals(
            "https://accounts.google.com/AccountChooser?Email=dev%40example.com" +
                "&continue=https%3A%2F%2Fconsole.firebase.google.com%2Fu%2F0%3Fx%3D1%26y%3D2",
            Links.accountChooser("dev@example.com", "https://console.firebase.google.com/u/0?x=1&y=2"),
        )
    }
}
