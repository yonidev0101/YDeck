package com.yonidev.ydeck.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/*
 * The phone's data model and file format (app: data/Model.kt, data/Repo.kt DeckJson), in plain JVM code. A backup
 * exported on the phone opens here and the other way round, so the field names and the encoding must stay identical.
 */

/** How a link picks its account. On Windows: Google services and the rest use the project's Chrome profile. */
enum class Route { GOOGLE, VERCEL, PLAIN }

/** One known service, from the app's catalog (generated into catalog.json at build time). */
data class Service(
    val id: String,
    val label: String,
    val short: String,
    /** The simple-icons vector path (24×24), or null: show the site's favicon. */
    val path: String?,
    val color: Int?,
    val route: Route,
    val defaultUrl: String,
    val match: List<String>,
    val category: String,
)

object Catalog {
    val all: List<Service> by lazy {
        val text = Catalog::class.java.getResourceAsStream("/catalog.json")!!.bufferedReader().use { it.readText() }
        JSONArray(text).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Service(
                    id = o.getString("id"), label = o.getString("label"), short = o.getString("short"),
                    path = if (o.isNull("path")) null else o.getString("path"),
                    color = if (o.isNull("color")) null else DeckJson.parseColor(o.getString("color")),
                    route = Route.valueOf(o.getString("route")),
                    defaultUrl = o.getString("url"),
                    match = o.getJSONArray("match").let { m -> (0 until m.length()).map { m.getString(it) } },
                    category = o.getString("category"),
                )
            }
        }
    }
    private val byId by lazy { all.associateBy { it.id } }
    val LINK: Service get() = byId.getValue("link")

    /** Shown first in the link editor before searching (the app's Service.POPULAR). */
    val POPULAR: List<Service> by lazy {
        listOf("play", "firebase", "gmail", "drive", "vercel", "github", "supabase", "mongodb", "railway", "cloudflare",
            "stripe", "sentry", "analytics", "appstore", "openai", "anthropic", "figma", "notion", "site", "link").mapNotNull { byId[it] }
    }

    fun of(id: String): Service = byId[id] ?: LINK

    /** Guesses the service from a pasted URL; the most specific pattern wins (the app's Service.detect). */
    fun detect(url: String): Service? {
        val host = Links.host(url) ?: return null
        val path = Links.path(url)
        return all.flatMap { s -> s.match.map { s to it } }
            .filter { (_, pattern) ->
                val h = pattern.substringBefore('/')
                val p = pattern.substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }
                (host == h || host.endsWith(".$h")) && path.startsWith(p)
            }
            .maxByOrNull { it.second.length }?.first
    }
}

data class DeckAction(val service: Service, val url: String, val label: String) {
    val hasCustomLabel: Boolean get() = label != service.label
    val tileLabel: String get() = if (hasCustomLabel) label else service.short
    val faviconHost: String? get() = if (service.path != null) null else Links.host(url)
}

data class CopyItem(val label: String, val value: String)

/** The phone's Chrome channel for Vercel. Kept as is so a backup goes back to the phone unchanged. */
data class Browser(val pkg: String, val label: String)

data class Project(
    val id: String,
    val name: String,
    val mono: String,
    val color: Int,
    val colorTemporary: Boolean,
    val kind: String,
    val googleEmail: String?,
    val vercel: Browser?,
    val actions: List<DeckAction>,
    val copies: List<CopyItem>,
    val logo: String? = null,
) {
    companion object {
        fun blank(): Project = Project(
            id = "p" + System.currentTimeMillis().toString(36), name = "", mono = "", color = 0xFF1A6FDB.toInt(),
            colorTemporary = false, kind = "", googleEmail = null, vercel = null, actions = emptyList(), copies = emptyList(),
        )

        /** "Nova", the made-up project of the films and the phone's welcome screen. */
        fun sample(): Project = Project(
            id = "nova-sample", name = "Nova", mono = "N", color = 0xFF0B0B0C.toInt(), colorTemporary = false, kind = tr("Sample project", "פרויקט לדוגמה"),
            googleEmail = "dana@nova.dev", vercel = Browser("com.android.chrome", "Chrome"),
            actions = listOf("firebase", "play", "vercel", "github", "sentry", "analytics", "drive").map { id ->
                Catalog.of(id).let { DeckAction(it, it.defaultUrl, it.label) }
            },
            copies = listOf(CopyItem("Package", "dev.nova.app")),
        )
    }
}

data class Deck(val projects: List<Project>) {
    fun byId(id: String?): Project? = projects.firstOrNull { it.id == id }
}

/** projects.json ⇄ [Deck], exactly as the phone reads and writes it. */
object DeckJson {
    private const val FALLBACK_COLOR = 0xFF6E6E73.toInt()

    fun decode(text: String): Deck {
        val root = JSONObject(text)
        val google = root.optJSONObject("googleAccounts") ?: JSONObject()
        val vercel = root.optJSONObject("vercelAccounts") ?: JSONObject()
        val projects = root.optJSONArray("projects") ?: JSONArray()
        return Deck(projects.objects().mapNotNull { decodeProject(it, google, vercel) })
    }

    fun decodeProject(p: JSONObject, google: JSONObject = JSONObject(), vercel: JSONObject = JSONObject()): Project? {
        val name = p.optString("name").ifBlank { return null }
        val brand = p.optJSONObject("brand") ?: JSONObject()
        val googleKey = p.optString("google")
        val email = google.optString(googleKey).takeIf { it.contains('@') } ?: googleKey.takeIf { it.contains('@') }
        val browser = (p.optJSONObject("vercel") ?: vercel.optJSONObject(p.optString("vercel")))?.let {
            Browser(it.optString("browser", "com.android.chrome"), it.optString("label", "Chrome"))
        }
        return Project(
            id = p.optString("id").ifBlank { name },
            name = name,
            mono = p.optString("mono").ifBlank { name.take(1).uppercase() },
            color = parseColor(brand.optString("color")) ?: FALLBACK_COLOR,
            colorTemporary = brand.optBoolean("temporary"),
            kind = p.optString("kind"),
            googleEmail = email,
            vercel = browser,
            actions = p.optJSONArray("actions").objects().mapNotNull { a ->
                val url = a.optString("url").ifBlank { return@mapNotNull null }
                val service = Catalog.of(a.optString("service"))
                DeckAction(service, url, a.optString("label").ifBlank { service.label })
            },
            copies = p.optJSONArray("copy").objects().mapNotNull { c ->
                val value = c.optString("value").ifBlank { return@mapNotNull null }
                CopyItem(c.optString("label"), value)
            },
            logo = brand.optString("logo").ifBlank { null },
        )
    }

    fun encode(deck: Deck): JSONObject = JSONObject().apply {
        put("version", 2)
        put("projects", JSONArray().apply { deck.projects.forEach { put(encodeProject(it)) } })
    }

    fun encodeProject(p: Project): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("mono", p.mono)
        put("kind", p.kind)
        put("brand", JSONObject().apply {
            put("color", String.format("#%06X", p.color and 0xFFFFFF))
            if (p.colorTemporary) put("temporary", true)
            p.logo?.let { put("logo", it) }
        })
        p.googleEmail?.let { put("google", it) }
        p.vercel?.let { put("vercel", JSONObject().put("browser", it.pkg).put("label", it.label)) }
        put("actions", JSONArray().apply {
            p.actions.forEach { a ->
                put(JSONObject().apply {
                    put("service", a.service.id)
                    put("url", a.url)
                    if (a.hasCustomLabel) put("label", a.label)
                })
            }
        })
        put("copy", JSONArray().apply { p.copies.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) } })
    }

    fun parseColor(s: String): Int? {
        val hex = s.trim().removePrefix("#")
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
            8 -> hex.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

object Links {
    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()

    fun host(url: String): String? = parse(url)?.host?.lowercase()?.removePrefix("www.")?.takeIf { it.contains('.') }

    fun path(url: String): String = parse(url)?.rawPath.orEmpty()

    fun display(url: String): String {
        val h = host(url) ?: return url.trim()
        return h + path(url).trimEnd('/')
    }

    /** Google's account chooser: continues to [url] in [email]'s session, when no Chrome profile has that account. */
    fun accountChooser(email: String, url: String): String =
        "https://accounts.google.com/AccountChooser?Email=" + enc(email) + "&continue=" + enc(url)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
