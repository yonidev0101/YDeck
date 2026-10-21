package com.yonidev.ydeck.desktop

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.HttpURLConnection
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/* The app's look (the brand's tokens): one page colour, warm greys, hairlines, a single indigo accent. */

data class DeckColors(
    val bg: Color, val surface: Color, val surface2: Color, val fg: Color, val muted: Color, val faint: Color,
    val line: Color, val danger: Color, val accent: Color, val accentText: Color, val accentTint: Color,
    /** The sidebar and grouped grounds (the brand's paper). */
    val paper: Color = surface2,
    /** Text on the warm amber of "needs you" (a dot alone is CHANGED_DOT). */
    val amberText: Color = Color(0xFF9A5B00),
    /** The green of a running dev server, as text. */
    val liveText: Color = Color(0xFF15803D),
)

val Light = DeckColors(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1F1EF), fg = Color(0xFF0B0B0C),
    muted = Color(0xFF5C5C61), faint = Color(0xFF8A8A90), line = Color(0xFFE7E7E4), danger = Color(0xFFD13438),
    accent = Color(0xFF3D35E0), accentText = Color(0xFF3D35E0), accentTint = Color(0xFFECEBFD),
    paper = Color(0xFFF6F6F4),
)
val Dark = DeckColors(
    bg = Color(0xFF0B0B0D), surface = Color(0xFF16161A), surface2 = Color(0xFF1C1C1F), fg = Color(0xFFF4F4F5),
    muted = Color(0xFFA1A1A8), faint = Color(0xFF8A8A90), line = Color(0xFF26262C), danger = Color(0xFFFF6B6F),
    accent = Color(0xFF5A52F5), accentText = Color(0xFF9C97FF), accentTint = Color(0xFF1D1B3D),
    paper = Color(0xFF111113), amberText = Color(0xFFF5C46B), liveText = Color(0xFF4ADE80),
)
val LocalDeck = staticCompositionLocalOf { Light }

val PlexSans = FontFamily(
    Font("fonts/plex_sans_hebrew_regular.ttf", FontWeight.Normal),
    Font("fonts/plex_sans_hebrew_medium.ttf", FontWeight.Medium),
    Font("fonts/plex_sans_hebrew_semibold.ttf", FontWeight.SemiBold),
    Font("fonts/plex_sans_hebrew_bold.ttf", FontWeight.Bold),
)
val PlexMono = FontFamily(Font("fonts/plex_mono_regular.ttf", FontWeight.Normal), Font("fonts/plex_mono_medium.ttf", FontWeight.Medium))
val Display = FontFamily(Font("fonts/noto_sans_hebrew.ttf", FontWeight.Light))

/** Dark or light, as chosen in Settings (or as Windows is). */
@Composable
fun isDark(): Boolean = when (Settings.state.collectAsState().value.appearance) {
    "light" -> false
    "dark" -> true
    else -> androidx.compose.foundation.isSystemInDarkTheme()
}

/** The app's theme: right to left in Hebrew, left to right in English. */
@Composable
fun DeckTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    // Material's own surfaces (its dialogs and menus) are tinted lavender by default: here they're YDeck's.
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent, background = c.bg, onBackground = c.fg, surface = c.surface, onSurface = c.fg,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = c.line, outlineVariant = c.line, surfaceTint = c.surface,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface2, surfaceBright = c.surface, surfaceDim = c.surface2,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography.let { t ->
        t.copy(
            bodyLarge = t.bodyLarge.copy(fontFamily = PlexSans), bodyMedium = t.bodyMedium.copy(fontFamily = PlexSans),
            labelLarge = t.labelLarge.copy(fontFamily = PlexSans), titleMedium = t.titleMedium.copy(fontFamily = PlexSans),
        )
    }) {
        // A new language redraws everything inside (plain functions call tr too).
        androidx.compose.runtime.key(Settings.state.collectAsState().value.language) {
            CompositionLocalProvider(LocalDeck provides c, LocalLayoutDirection provides if (I18n.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                androidx.compose.foundation.LocalContextMenuRepresentation provides remember(c) { DeckContextMenu(c) }) { content() }
        }
    }
}

// ── colours on our surfaces (app data/Icons.kt BrandColors, widget/DeckWidget.kt glass) ──

/** Near-black marks lighten in dark mode, pale ones darken in light mode. */
fun adapt(color: Int, dark: Boolean): Color {
    val c = Color(color)
    val l = c.luminance()
    return when {
        dark && l < 0.06f -> Color(0xFFF5F5F7)
        dark && l < 0.18f -> lerpColor(c, Color.White, 0.45f)
        !dark && l > 0.55f -> lerpColor(c, Color.Black, 0.35f)
        else -> c
    }
}

private fun lerpColor(a: Color, b: Color, k: Float) = Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, 1f)

fun isNearBlack(color: Int) = Color(color).luminance() < 0.03f

/** The glass card: frosted white by day, graphite by night, washed with the project colour. More opaque than the
 *  phone's, since nothing blurs the wallpaper behind a desktop window. */
fun glass(projectColor: Int, dark: Boolean): Color {
    val tint = if (isNearBlack(projectColor)) 0f else if (dark) 0.18f else 0.14f
    val base = if (dark) Color(0xFF1E1E22) else Color.White
    val ba = if (dark) 0.78f else 0.82f
    val a = tint + ba * (1 - tint)
    val t = Color(projectColor)
    fun ch(tc: Float, bc: Float) = (tc * tint + bc * ba * (1 - tint)) / a
    return Color(ch(t.red, base.red), ch(t.green, base.green), ch(t.blue, base.blue), a)
}

// ── marks ──

private val vectors = ConcurrentHashMap<String, ImageVector>()

private fun vectorOf(s: Service): ImageVector? = s.path?.let { path ->
    vectors.getOrPut(s.id) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
    }
}

/** A service's real mark in its brand colour (or [tint]); the site's favicon for services without one; else a globe. */
@Composable
fun ServiceMark(service: Service, faviconHost: String?, size: Dp, dark: Boolean, tint: Color? = null) {
    val v = vectorOf(service)
    if (v != null) {
        Icon(rememberVectorPainter(v), service.label, tint = tint ?: service.color?.let { adapt(it, dark) } ?: LocalDeck.current.fg, modifier = Modifier.size(size))
        return
    }
    val version by Favicons.version.collectAsState()
    val bmp = remember(faviconHost, version) { Favicons.load(faviconHost) }
    if (bmp != null) Image(bmp, service.label, Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)))
    else Globe(size, tint ?: if (dark) Color(0xFFD1D1D6) else Color(0xFF3C3C43))
}

@Composable
fun Globe(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2 * 0.82f
        val c = center
        val w = Stroke(width = this.size.minDimension * 0.075f)
        drawCircle(color, r, c, style = w)
        drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = w.width)
        drawOval(color, Offset(c.x - r * 0.42f, c.y - r), androidx.compose.ui.geometry.Size(r * 0.84f, r * 2), style = w)
    }
}

@Composable
fun Dots(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val y = this.size.height / 2
        val r = this.size.minDimension * 0.085f
        listOf(0.2f, 0.5f, 0.8f).forEach { drawCircle(color, r, Offset(this.size.width * it, y)) }
    }
}

/** The project's logo image, or its monogram on its colour (the app's BrandArt). */
@Composable
fun Logo(p: Project, size: Dp) {
    val bmp = remember(p.logo) { Repo.logoFile(p.logo)?.let { runCatching { SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }.getOrNull() } }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.29f)).background(Color(p.color))
            .then(if (isNearBlack(p.color)) Modifier.border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(size * 0.29f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, p.name, Modifier.size(size))
        else Text(p.mono.ifBlank { p.name.take(1).uppercase() }, color = Color.White, style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.46f).sp))
    }
}

/**
 * Site icons for links without a brand mark, as on the phone (data/Icons.kt): fetched once, kept in
 * %LOCALAPPDATA%\YDeck\favicons, retried after a day when nothing was found.
 */
object Favicons {
    private const val RETRY_MS = 24L * 60 * 60 * 1000
    private val dir = File(AppDirs.local, "favicons").apply { mkdirs() }
    private val failures = File(dir, "failures.json")
    private val mem = ConcurrentHashMap<String, ImageBitmap>()
    private val busy = ConcurrentHashMap.newKeySet<String>()
    val version = MutableStateFlow(0)

    fun load(host: String?): ImageBitmap? {
        host ?: return null
        mem[host]?.let { return it }
        val f = File(dir, "$host.png")
        if (f.exists()) runCatching { SkiaImage.makeFromEncoded(f.readBytes()).toComposeImageBitmap() }.getOrNull()?.let { mem[host] = it; return it }
        // Off in Settings: nothing new is asked of the icon services (the ones already fetched still show).
        if (Settings.state.value.siteIcons) fetch(host)
        return null
    }

    private fun fetch(host: String) {
        if (!busy.add(host)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val failed = runCatching { JSONObject(failures.readText()) }.getOrDefault(JSONObject())
                if (System.currentTimeMillis() - failed.optLong(host, 0L) < RETRY_MS) return@launch
                val img = runCatching { SiteIcons.best(host, 32) }.getOrNull()
                synchronized(this@Favicons) {
                    val now = runCatching { JSONObject(failures.readText()) }.getOrDefault(JSONObject())
                    if (img == null) now.put(host, System.currentTimeMillis()) else now.remove(host)
                    runCatching { failures.writeText(now.toString()) }
                }
                if (img != null) {
                    File(dir, "$host.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return@launch)
                    version.value++
                }
            } finally {
                busy.remove(host)
            }
        }
    }
}

/**
 * Finding a site's icon, the phone's way: Google's favicon service for the host; the icons the site's own page
 * declares (what the browser tab shows, after a login redirect if there is one); Google's for the parent domain
 * (it knows mongodb.com but not cloud.mongodb.com); DuckDuckGo's; the site's /favicon.ico. Only the host is sent
 * to Google and DuckDuckGo.
 */
object SiteIcons {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    /** The first icon at least [sharp] px wide; failing that the largest found (only if [sharp] allows small ones). */
    fun best(host: String, sharp: Int, acceptSmall: Boolean = true): SkiaImage? {
        val parent = host.split('.').let { if (it.size > 2) it.takeLast(2).joinToString(".") else null }
        val sources = sequence<() -> SkiaImage?> {
            yield { image(google(host)) }
            yield { pageIcon("https://$host/") }
            if (parent != null) yield { image(google(parent)) }
            yield { image("https://icons.duckduckgo.com/ip3/$host.ico") }
            yield { image("https://$host/favicon.ico") }
        }
        var small: SkiaImage? = null
        for (source in sources) {
            val img = runCatching { source() }.getOrNull() ?: continue
            if (minOf(img.width, img.height) >= sharp) return img
            if (small == null || img.width > small.width) small = img
        }
        return small?.takeIf { acceptSmall && it.width >= 16 }
    }

    /** The largest icon the page itself declares (apple-touch-icon, icon sizes), for a project's logo. */
    fun logo(url: String): SkiaImage? = runCatching { pageIcon(url) }.getOrNull()?.takeIf { it.width >= 96 }
        ?: Links.host(url)?.let { host -> best(host, 96, acceptSmall = false) }

    private fun google(host: String) = "https://www.google.com/s2/favicons?sz=128&domain=" + URLEncoder.encode(host, "UTF-8")

    private fun open(url: String): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000; readTimeout = 8000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", UA)
    }

    /** An image at [url], or null when it isn't one (a 404, or a login page served in its place). */
    private fun image(url: String): SkiaImage? {
        val conn = open(url)
        return try {
            val type = conn.contentType.orEmpty()
            if (conn.responseCode != 200 || !(type.startsWith("image/") || type.isEmpty() || type.contains("octet")) || type.contains("svg")) null
            else conn.inputStream.use { SkiaImage.makeFromEncoded(it.readBytes()) }
        } finally {
            conn.disconnect()
        }
    }

    private val LINK = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private fun attr(tag: String, name: String) = Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    /** The icons the page lists (<link rel="icon" / "apple-touch-icon">), largest first. */
    private fun pageIcon(pageUrl: String): SkiaImage? {
        val conn = open(pageUrl)
        val (base, html) = try {
            if (conn.responseCode != 200 || !conn.contentType.orEmpty().contains("html")) return null
            // The URL after redirects: a relative href belongs to the page that served it.
            conn.url to conn.inputStream.use { String(it.readNBytes(256 * 1024), Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
        val icons = LINK.findAll(html).map { it.value }
            .filter { tag -> attr(tag, "rel")?.lowercase()?.let { "icon" in it } == true }
            .mapNotNull { tag ->
                val href = attr(tag, "href")?.takeIf { it.isNotBlank() && !it.startsWith("data:") && !it.endsWith(".svg") } ?: return@mapNotNull null
                val size = attr(tag, "sizes")?.substringBefore('x')?.toIntOrNull() ?: if (attr(tag, "rel")!!.contains("apple")) 180 else 0
                size to href
            }
            .sortedByDescending { it.first }
        for ((_, href) in icons) {
            runCatching { image(java.net.URL(base, href.replace("&amp;", "&")).toString()) }.getOrNull()?.let { return it }
        }
        return null
    }
}

// ── Claude Code's state (see ClaudeLink) ──

/** Indigo at work, amber when it waits for the user, green when done: the same on every surface. */
fun claudeColor(mode: ClaudeMode, dark: Boolean): Color = when (mode) {
    ClaudeMode.WORKING -> if (dark) Color(0xFF9C97FF) else Color(0xFF3D35E0)
    ClaudeMode.WAITING -> CHANGED_DOT
    ClaudeMode.OPEN -> if (dark) Color(0xFFB9B9C2) else Color(0xFF6E6E78)
    ClaudeMode.DONE -> CLEAN_DOT
}

fun claudeText(s: ClaudeSession): String = when (s.mode) {
    ClaudeMode.WORKING -> tr("${s.agent.label} is working", "${s.agent.label} עובד")
    ClaudeMode.WAITING -> tr("${s.agent.label} needs you", "${s.agent.label} מחכה לך")
    ClaudeMode.OPEN -> tr("${s.agent.label} is open", "${s.agent.label} פתוח") + if (s.remote) " · Remote Control" else ""
    ClaudeMode.DONE -> tr("${s.agent.label} finished · ${ago(s.since)}", "${s.agent.label} סיים · ${ago(s.since)}")
}

/** The session that matters in [folder] now (null when Claude isn't busy there), kept fresh as time passes. */
@Composable
fun claudeIn(folder: String?): ClaudeSession? {
    val all by ClaudeLink.sessions.collectAsState()
    val minute by ClaudeLink.minute.collectAsState()
    return remember(folder, all, minute) { folder?.let { ClaudeLink.statusIn(it, all) } }
}

/** A dot in the state's colour; it breathes while Claude works. An open session is a ring: there, not busy. */
@Composable
fun ClaudeDot(mode: ClaudeMode, dark: Boolean, size: Dp = 6.dp) {
    if (mode == ClaudeMode.OPEN) {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 2)).border(1.5.dp, claudeColor(mode, dark), RoundedCornerShape(size / 2)))
        return
    }
    val alpha = if (mode == ClaudeMode.WORKING) {
        val t = rememberInfiniteTransition()
        t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse)).value
    } else 1f
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(claudeColor(mode, dark).copy(alpha = alpha)))
}
