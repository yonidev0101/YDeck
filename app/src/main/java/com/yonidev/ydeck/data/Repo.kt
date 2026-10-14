package com.yonidev.ydeck.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * Projects live in filesDir/projects.json once the user edits anything on the phone. Until then a debug build
 * seeds from the bundled asset (the repo's projects.json); a release has no asset and starts empty. Every save keeps the previous file as
 * projects.json.bak, and a load that can't read the main file falls back to it, so one bad write never
 * empties the deck.
 */
object Repo {
    private const val FILE = "projects.json"
    private const val BACKUP = "projects.json.bak"

    @Volatile private var cached: Deck? = null

    private val _version = MutableStateFlow(0)
    /** Bumps on every save, so a running widget session recomposes with the new data. */
    val version: StateFlow<Int> = _version

    fun deck(context: Context): Deck = cached ?: load(context.applicationContext).also { cached = it }

    /** Makes [deck] current right away (UI and widgets), then writes it. Call from a background thread. */
    @Synchronized
    fun save(context: Context, deck: Deck) {
        cached = deck
        _version.value++
        val dir = context.applicationContext.filesDir
        val main = File(dir, FILE)
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(DeckJson.encode(deck).toString(2))
        if (main.exists()) main.copyTo(File(dir, BACKUP), overwrite = true)
        if (!tmp.renameTo(main)) {
            main.delete()
            tmp.renameTo(main)
        }
    }

    private fun load(context: Context): Deck {
        val dir = context.filesDir
        for (name in listOf(FILE, BACKUP)) {
            val f = File(dir, name)
            if (!f.exists()) continue
            runCatching { DeckJson.decode(f.readText()) }.getOrNull()?.let { return it }
        }
        return runCatching { DeckJson.decode(context.assets.open(FILE).bufferedReader().use { it.readText() }) }
            .getOrElse { Deck(emptyList()) }
    }

    // ---- Backup file (export / restore) ----

    /** A self-contained backup: the projects plus every logo image, embedded as base64 PNG. */
    fun exportJson(context: Context, deck: Deck): String {
        val root = DeckJson.encode(deck)
        val logos = JSONObject()
        deck.projects.mapNotNull { it.logo }.forEach { path ->
            val f = File(context.filesDir, path)
            if (f.exists()) logos.put(path, Base64.getEncoder().encodeToString(f.readBytes()))
        }
        root.put("app", "YDeck")
        root.put("logos", logos)
        return root.toString(2)
    }

    /** Reads a backup made by [exportJson] (or a plain projects.json), writing its logos back to filesDir. */
    fun importJson(context: Context, text: String): Deck {
        val root = JSONObject(text)
        val deck = DeckJson.decode(text)
        root.optJSONObject("logos")?.let { logos ->
            logos.keys().forEach { path ->
                // Only our own "logos/<file>.png" paths; never write outside the logos folder.
                if (!Regex("logos/[A-Za-z0-9._-]+\\.png").matches(path)) return@forEach
                val f = File(context.filesDir, path)
                f.parentFile?.mkdirs()
                f.writeBytes(Base64.getDecoder().decode(logos.getString(path)))
            }
        }
        return deck.copy(projects = deck.projects.map { p ->
            if (p.logo != null && !File(context.filesDir, p.logo).exists()) p.copy(logo = null) else p
        })
    }
}

/** projects.json ⇄ [Deck], in plain JVM code so it is unit-tested off-device. */
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
        val email = google.optString(googleKey).takeIf { it.contains('@') }
            ?: googleKey.takeIf { it.contains('@') }
        // "vercel" is either a key into vercelAccounts (repo file) or an inline {browser, label} (saved on the phone).
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
                val service = Service.of(a.optString("service"))
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

    /** "#RRGGBB" or "#AARRGGBB" → ARGB int (android.graphics.Color.parseColor without Android). */
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
