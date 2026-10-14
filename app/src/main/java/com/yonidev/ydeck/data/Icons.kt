package com.yonidev.ydeck.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** In-memory cache for decoded and drawn bitmaps (logos, favicons), bounded by size. */
object BitmapCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Returns the cached bitmap for [key], or makes it with [make] (null results aren't cached). */
    fun get(key: String, make: () -> Bitmap?): Bitmap? =
        cache.get(key) ?: make()?.also { cache.put(key, it) }
}

/** Brand colours as drawn on our surfaces: near-black marks lighten in dark mode, pale ones darken in light mode. */
object BrandColors {
    fun adapt(color: Int, dark: Boolean): Int {
        val l = ColorUtils.calculateLuminance(color)
        return when {
            dark && l < 0.06 -> 0xFFF5F5F7.toInt()
            dark && l < 0.18 -> ColorUtils.blendARGB(color, Color.WHITE, 0.45f)
            !dark && l > 0.55 -> ColorUtils.blendARGB(color, Color.BLACK, 0.35f)
            else -> color
        }
    }
}

/**
 * Site icons for services without a brand mark (and for custom links). Fetched once (see [download] for where
 * from), cached in filesDir/favicons, and read synchronously by the widget, which never touches the network.
 */
object Favicons {
    private const val RETRY_MS = 24L * 60 * 60 * 1000
    private val _version = MutableStateFlow(0)
    /** Bumps when new icons land, so open screens redraw. */
    val version: StateFlow<Int> = _version

    private fun file(context: Context, host: String) = File(context.filesDir, "favicons/$host.png")

    /** Decoded once, then served from memory: the widget redraws often and must not hit the disk each time. */
    fun load(context: Context, host: String?): Bitmap? {
        if (host == null) return null
        val f = file(context, host)
        return BitmapCache.get("fav:$host:${f.lastModified()}") { f.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
    }

    fun hostOf(url: String): String? = Links.host(url)

    /**
     * Downloads whatever icons the deck and the catalog still lack. A host that failed is not retried for a day
     * (remembered across launches). Returns true if anything new arrived.
     */
    suspend fun fetchMissing(context: Context, deck: Deck): Boolean = withContext(Dispatchers.IO) {
        // v2: the fuller search should retry the hosts the Google-only version gave up on
        val failures = context.getSharedPreferences("favicon_failures_v2", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val hosts = (deck.projects.flatMap { p -> p.actions.mapNotNull { it.faviconHost } } +
            Service.all.filterNot { it.brandIcon }.mapNotNull { hostOf(it.defaultUrl) }).distinct()
        var any = false
        for (host in hosts) {
            val f = file(context, host)
            if (f.exists() || now - failures.getLong(host, 0L) < RETRY_MS) continue
            val bmp = runCatching { download(host) }.getOrNull()
            if (bmp == null) {
                failures.edit().putLong(host, now).apply()
                continue
            }
            f.parentFile?.mkdirs()
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            failures.edit().remove(host).apply()
            any = true
        }
        if (any) _version.value++
        any
    }

    /**
     * The best icon we can find, tried in order until one is sharp enough (32 px or more):
     * Google's service for the host; the icons the site's own page declares (what the browser tab shows,
     * following a login redirect if there is one); Google's service for the parent domain (it knows mongodb.com
     * but not cloud.mongodb.com); DuckDuckGo's; the site's /favicon.ico. A small icon is kept only if nothing
     * sharper turns up.
     */
    private fun download(host: String): Bitmap? {
        val parent = host.split('.').let { if (it.size > 2) it.takeLast(2).joinToString(".") else null }
        val sources = sequence<() -> Bitmap?> {
            yield { image(google(host)) }
            yield { pageIcon(host) }
            if (parent != null) yield { image(google(parent)) }
            yield { image("https://icons.duckduckgo.com/ip3/$host.ico") }
            yield { image("https://$host/favicon.ico") }
        }
        var small: Bitmap? = null
        for (source in sources) {
            val bmp = runCatching { source() }.getOrNull() ?: continue
            if (minOf(bmp.width, bmp.height) >= 32) return bmp
            if (small == null || bmp.width > small.width) small = bmp
        }
        return small
    }

    private fun google(host: String) = "https://www.google.com/s2/favicons?sz=128&domain=" + URLEncoder.encode(host, "UTF-8")

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }

    /** An image at [url], or null when it isn't one (a 404, or a login page served in its place). */
    private fun image(url: String): Bitmap? {
        val conn = open(url)
        return try {
            val type = conn.contentType.orEmpty()
            if (conn.responseCode != 200 || !(type.startsWith("image/") || type.isEmpty()) || type.contains("svg")) null
            else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }

    private val LINK = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private fun attr(tag: String, name: String) =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    /** The icons the site's own page lists (<link rel="icon" / "apple-touch-icon">), largest first. */
    private fun pageIcon(host: String): Bitmap? {
        val conn = open("https://$host/")
        val (base, html) = try {
            if (conn.responseCode != 200 || !conn.contentType.orEmpty().contains("html")) return null
            // the URL after redirects: a relative href belongs to the page that served it
            conn.url to conn.inputStream.use { input ->
                // the <head> is enough; readNBytes needs API 33
                val buf = ByteArray(256 * 1024)
                var n = 0
                while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
        val icons = LINK.findAll(html).map { it.value }
            .filter { tag -> attr(tag, "rel")?.lowercase()?.let { "icon" in it } == true }
            .mapNotNull { tag ->
                val href = attr(tag, "href")?.takeIf { it.isNotBlank() && !it.startsWith("data:") && !it.endsWith(".svg") }
                    ?: return@mapNotNull null
                val size = attr(tag, "sizes")?.substringBefore('x')?.toIntOrNull()
                    ?: if (attr(tag, "rel")!!.contains("apple")) 180 else 0
                size to href
            }
            .sortedByDescending { it.first }
        for ((_, href) in icons) {
            runCatching { image(URL(base, href.replace("&amp;", "&")).toString()) }.getOrNull()?.let { return it }
        }
        return null
    }
}
