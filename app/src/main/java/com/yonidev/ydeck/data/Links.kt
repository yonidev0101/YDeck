package com.yonidev.ydeck.data

import java.net.URI
import java.net.URLEncoder

/** URL helpers in plain JVM code (no android.net.Uri), so they are unit-tested off-device. */
object Links {

    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()

    /** Lower-case host without "www.", or null for anything that isn't an http(s) address with a dotted host. */
    fun host(url: String): String? =
        parse(url)?.host?.lowercase()?.removePrefix("www.")?.takeIf { it.contains('.') }

    fun path(url: String): String = parse(url)?.rawPath.orEmpty()

    /** Short "host/path" for display. */
    fun display(url: String): String {
        val h = host(url) ?: return url.trim()
        return h + path(url).trimEnd('/')
    }

    /**
     * Google's account chooser: signs the request into [email]'s session (if it's signed in on the device)
     * and then continues to [url]. This is what makes Google links open in the project's account.
     */
    fun accountChooser(email: String, url: String): String =
        "https://accounts.google.com/AccountChooser?Email=" + enc(email) + "&continue=" + enc(url)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
