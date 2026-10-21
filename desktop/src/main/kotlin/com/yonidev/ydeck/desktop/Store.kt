package com.yonidev.ydeck.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage

object AppDirs {
    /** %APPDATA%\YDeck: the projects (roams with the Windows profile, like the phone's own backup). */
    val data: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "YDeck").apply { mkdirs() }

    /**
     * %LOCALAPPDATA%\YDeck Data: the Claude Code link's key and plugin, session receipts, folder icons, favicons.
     * Not %LOCALAPPDATA%\YDeck, where the app itself is installed: an update replaces that folder whole (1.11 did,
     * and took the key with it, so Claude Code's hooks were turned away). What an earlier version kept there comes over once.
     */
    val local: File = run {
        val root = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"))
        val dir = File(root, "YDeck Data")
        if (!dir.exists()) {
            dir.mkdirs()
            val old = File(root, "YDeck")
            listOf("claude-link.key", "receipts.json", "site-logos-tried.txt", "favicons", "folder-icons").forEach { name ->
                runCatching { File(old, name).takeIf { it.exists() }?.copyRecursively(File(dir, name), overwrite = false) }
            }
        }
        dir
    }
}

/**
 * The projects, as on the phone: projects.json with the previous version kept as projects.json.bak (a load that can't
 * read the main file falls back to it), logos in logos/. Every save updates [deck] at once, so open widgets redraw.
 */
object Repo {
    private val main = File(AppDirs.data, "projects.json")
    private val backup = File(AppDirs.data, "projects.json.bak")

    private val _deck = MutableStateFlow(load())
    val deck: StateFlow<Deck> = _deck

    @Synchronized
    fun save(next: Deck) {
        _deck.value = next
        val tmp = File(AppDirs.data, "projects.json.tmp")
        tmp.writeText(DeckJson.encode(next).toString(2))
        if (main.exists()) main.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(main)) { main.delete(); tmp.renameTo(main) }
    }

    private fun load(): Deck {
        for (f in listOf(main, backup)) {
            if (!f.exists()) continue
            runCatching { DeckJson.decode(f.readText()) }.getOrNull()?.let { return it }
        }
        return Deck(emptyList())
    }

    fun logoFile(path: String?): File? = path?.let { File(AppDirs.data, it) }?.takeIf { it.exists() }

    /** The phone's backup format: the projects plus every logo as base64 PNG. Opens in the app as is. */
    fun exportJson(deck: Deck): String {
        val root = DeckJson.encode(deck)
        val logos = JSONObject()
        deck.projects.mapNotNull { it.logo }.forEach { path ->
            logoFile(path)?.let { logos.put(path, Base64.getEncoder().encodeToString(it.readBytes())) }
        }
        root.put("app", "YDeck")
        root.put("logos", logos)
        return root.toString(2)
    }

    /** Reads a backup made on the phone or here (or a plain projects.json), writing its logos back. */
    fun importJson(text: String): Deck {
        val root = JSONObject(text)
        val deck = DeckJson.decode(text)
        root.optJSONObject("logos")?.let { logos ->
            logos.keys().forEach { path ->
                if (!Regex("logos/[A-Za-z0-9._-]+\\.png").matches(path)) return@forEach
                val f = File(AppDirs.data, path)
                f.parentFile?.mkdirs()
                f.writeBytes(Base64.getDecoder().decode(logos.getString(path)))
            }
        }
        return deck.copy(projects = deck.projects.map { p -> if (p.logo != null && logoFile(p.logo) == null) p.copy(logo = null) else p })
    }
}

/** One widget on the desktop: which project, where, how big (dp). */
data class WidgetSpec(val id: String, val project: String?, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * What only the PC has, kept apart from projects.json so a backup stays the phone's format: the widgets on the
 * desktop, and the Chrome profile chosen for a project (when it isn't found from the project's email).
 */
object Settings {
    private val file = File(AppDirs.data, "desktop.json")

    data class State(
        val widgets: List<WidgetSpec> = emptyList(),
        val profiles: Map<String, String> = emptyMap(),
        /** By project id: its folder, commands and pinned files on this PC. */
        val local: Map<String, Local> = emptyMap(),
        /** The shell for commands and the terminal button: cmd, powershell, pwsh or bash (see [Shells]). */
        val shell: String = "cmd",
        /** The project's strip inside File Explorer, while a project folder is open there (see [FolderStrip]). */
        val companion: Boolean = true,
        /** "Open in YDeck" on a folder's right-click menu in Explorer (see [ExplorerMenu]). */
        val explorerMenu: Boolean = true,
        /** A Windows notification when Claude Code waits for the user, or ends a long task (see [ClaudeLink]). */
        val claudeAlerts: Boolean = true,
        /** Asks each project's server for new commits every 10 minutes (git fetch), so "to pull" is true (see [Git]). */
        val gitFetch: Boolean = true,
        /** Claude keeps .ydeck.json up to date in the project's repository, and YDeck its block in an existing AGENTS.md.
         *  Off: only the personal .ydeck.local.json (kept out of git) is written. See [ProjectFiles.briefing]. */
        val repoFiles: Boolean = true,
        /** When a Claude session that changed files ends, Claude writes where the work stands (see [Handoffs]). */
        val handoffs: Boolean = true,
        /** A tagged folder's icon shows an agent at work (indigo dot) or waiting (amber), see [FolderBadges]. */
        val folderStatus: Boolean = true,
        /** A tagged folder's icon shows a small amber dot while it has changes not yet committed, see [FolderBadges]. */
        val folderGit: Boolean = true,
        /** Links without a brand mark show their site's icon, fetched by the domain name alone (see [Favicons]). */
        val siteIcons: Boolean = true,
        /** The paired phone may open Claude Code in a project's folder (a closed list of ways, see [PhoneLink.launch]). Off
         *  until turned on here, at the computer. */
        val phoneLaunch: Boolean = false,
        /** "system", "en" or "he" (see [I18n]). */
        val language: String = "system",
        /** "system", "light" or "dark". */
        val appearance: String = "dark",
        /** The first run was shown (or skipped). */
        val welcomed: Boolean = false,
        /** Anonymous usage counts, once the choice was shown ([statsAsked]): see [UsageStats]. */
        val usageStats: Boolean = true,
        /** The choice about usage counts was shown: on the first run, or once on Now for those who came before it. */
        val statsAsked: Boolean = false,
        /** This install's random id for the counts (no link to anything else), made on first use. */
        val statsId: String = "",
        /** The install was counted once. */
        val statsInstalled: Boolean = false,
    ) {
        fun localOf(projectId: String?): Local = projectId?.let { local[it] } ?: Local()
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state

    fun update(change: (State) -> State) {
        _state.value = change(_state.value)
        if (previewing) return
        runCatching {
            val s = _state.value
            file.writeText(JSONObject().apply {
                put("widgets", JSONArray().apply {
                    s.widgets.forEach { w -> put(JSONObject().put("id", w.id).put("project", w.project ?: "").put("x", w.x).put("y", w.y).put("w", w.w).put("h", w.h)) }
                })
                put("profiles", JSONObject(s.profiles))
                put("shell", s.shell)
                put("companion", s.companion)
                put("explorerMenu", s.explorerMenu)
                put("claudeAlerts", s.claudeAlerts)
                put("gitFetch", s.gitFetch)
                put("handoffs", s.handoffs)
                put("repoFiles", s.repoFiles)
                put("folderStatus", s.folderStatus)
                put("folderGit", s.folderGit)
                put("siteIcons", s.siteIcons)
                put("phoneLaunchOn", s.phoneLaunch)
                put("language", s.language)
                put("appearance", s.appearance)
                put("appearanceV2", true)
                put("welcomed", s.welcomed)
                put("usageStats", s.usageStats)
                put("statsAsked", s.statsAsked)
                put("statsId", s.statsId)
                put("statsInstalled", s.statsInstalled)
                put("local", JSONObject().apply {
                    s.local.filterValues { !it.isEmpty }.forEach { (id, l) ->
                        put(id, JSONObject().apply {
                            l.folder?.let { put("folder", it) }
                            if (l.tagged) put("tagged", true)
                            put("commands", JSONArray().apply { l.commands.forEach { c -> put(JSONObject().put("label", c.label).put("line", c.line)) } })
                            put("files", JSONArray(l.files))
                            if (l.note.isNotBlank()) put("note", l.note)
                            if (l.fillOffered) put("fillOffered", true)
                        })
                    }
                })
            }.toString(2))
        }
    }

    private fun load(): State = runCatching {
        val o = JSONObject(file.readText())
        val ws = o.optJSONArray("widgets") ?: JSONArray()
        State(
            widgets = (0 until ws.length()).map { i ->
                val w = ws.getJSONObject(i)
                WidgetSpec(w.getString("id"), w.optString("project").ifBlank { null }, w.getDouble("x").toFloat(), w.getDouble("y").toFloat(),
                    w.getDouble("w").toFloat(), w.getDouble("h").toFloat())
            },
            profiles = o.optJSONObject("profiles")?.let { p -> p.keys().asSequence().associateWith { p.getString(it) } } ?: emptyMap(),
            shell = o.optString("shell", "cmd"),
            companion = o.optBoolean("companion", true),
            explorerMenu = o.optBoolean("explorerMenu", true),
            claudeAlerts = o.optBoolean("claudeAlerts", true),
            gitFetch = o.optBoolean("gitFetch", true),
            handoffs = o.optBoolean("handoffs", true),
            repoFiles = o.optBoolean("repoFiles", true),
            folderStatus = o.optBoolean("folderStatus", true),
            folderGit = o.optBoolean("folderGit", true),
            siteIcons = o.optBoolean("siteIcons", true),
            // Off until turned on here; 1.16.0 saved "phoneLaunch" on for everyone, so that one is not read.
            phoneLaunch = o.optBoolean("phoneLaunchOn", false),
            language = o.optString("language", "system"),
            // 1.11: dark became the default; a "system" saved before then was the old default, not a choice.
            appearance = if (!o.has("appearanceV2") && o.optString("appearance", "system") == "system") "dark" else o.optString("appearance", "dark"),
            // Someone who already has projects has no need of the first run.
            welcomed = o.optBoolean("welcomed", o.optJSONArray("widgets")?.length()?.let { it > 0 } ?: false),
            usageStats = o.optBoolean("usageStats", true),
            statsAsked = o.optBoolean("statsAsked", false),
            statsId = o.optString("statsId"),
            statsInstalled = o.optBoolean("statsInstalled", false),
            local = o.optJSONObject("local")?.let { all ->
                all.keys().asSequence().associateWith { id ->
                    val l = all.getJSONObject(id)
                    val cmds = l.optJSONArray("commands") ?: JSONArray()
                    val files = l.optJSONArray("files") ?: JSONArray()
                    Local(
                        folder = l.optString("folder").ifBlank { null },
                        tagged = l.optBoolean("tagged"),
                        commands = (0 until cmds.length()).map { i -> cmds.getJSONObject(i).let { Command(it.optString("label"), it.optString("line")) } }.filter { it.line.isNotBlank() },
                        files = (0 until files.length()).map { files.getString(it) },
                        note = l.optString("note"),
                        fillOffered = l.optBoolean("fillOffered"),
                    )
                }
            } ?: emptyMap(),
        )
    }.getOrDefault(State())

    fun setWidget(spec: WidgetSpec) = update { s -> s.copy(widgets = s.widgets.map { if (it.id == spec.id) spec else it }.let { if (it.any { w -> w.id == spec.id }) it else it + spec }) }
    fun removeWidget(id: String) = update { s -> s.copy(widgets = s.widgets.filterNot { it.id == id }) }
    fun setLocal(projectId: String, l: Local) = update { s -> s.copy(local = if (l.isEmpty) s.local - projectId else s.local + (projectId to l)) }
    /** For the renders: a state shown without being saved, so desktop.json on this PC is never touched. */
    fun preview(s: State) { previewing = true; _state.value = s }
    @Volatile private var previewing = false

    fun setNote(projectId: String, note: String) = setLocal(projectId, state.value.localOf(projectId).copy(note = note))

    /** The project whose folder holds [path] (the deepest, for a project inside another): its id and its folder. */
    fun projectAt(path: File): Pair<String, String>? =
        state.value.local.mapNotNull { (id, l) -> l.folder?.let { id to it } }
            .filter { (_, f) -> isUnder(path.absolutePath, f) }
            .maxByOrNull { it.second.length }

    fun setProfile(projectId: String, dir: String?) = update { s -> s.copy(profiles = if (dir == null) s.profiles - projectId else s.profiles + (projectId to dir)) }
}

/** Project logos: any image, centre-cropped to a square, 256 px, saved as logos/<project>-<time>.png. */
object Logos {
    fun save(projectId: String, src: BufferedImage): String? = runCatching {
        val side = minOf(src.width, src.height)
        val crop = src.getSubimage((src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(crop, 0, 0, 256, 256, null); dispose()
        }
        val rel = "logos/${projectId.replace(Regex("[^A-Za-z0-9._-]"), "_")}-${System.currentTimeMillis().toString(36)}.png"
        val f = File(AppDirs.data, rel).apply { parentFile.mkdirs() }
        ImageIO.write(out, "png", f)
        rel
    }.getOrNull()

    /** The project's own site: its "site" link, else the first link to a service without a brand mark. */
    fun siteOf(p: Project): String? =
        (p.actions.firstOrNull { it.service.id == "site" } ?: p.actions.firstOrNull { it.service.path == null && Links.host(it.url) != null })?.url

    /** The site's icon (apple-touch-icon and the like, 96 px or more) saved as the project's logo. */
    fun fromSite(projectId: String, url: String): String? {
        val img = SiteIcons.logo(url) ?: return null
        val png = img.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        return save(projectId, ImageIO.read(ByteArrayInputStream(png)) ?: return null)
    }

    private val tried = File(AppDirs.local, "site-logos-tried.txt")

    /** Gives each project that has a site but no logo the site's icon; each project is tried only once. */
    suspend fun fillFromSites(deck: Deck) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val done = runCatching { tried.readLines().toMutableSet() }.getOrDefault(mutableSetOf())
        for (p in deck.projects) {
            if (p.logo != null || p.id in done) continue
            val url = siteOf(p) ?: continue
            done += p.id
            runCatching { tried.writeText(done.joinToString("\n")) }
            val rel = runCatching { fromSite(p.id, url) }.getOrNull() ?: continue
            val now = Repo.deck.value
            Repo.save(Deck(now.projects.map { if (it.id == p.id && it.logo == null) it.copy(logo = rel) else it }))
        }
    }

    /** PNG, JPEG, BMP, and WebP (Android's launcher icons) through Skia. */
    fun fromFile(projectId: String, file: File): String? {
        val img = runCatching { ImageIO.read(file) }.getOrNull() ?: runCatching {
            val png = SkiaImage.makeFromEncoded(file.readBytes()).encodeToData(EncodedImageFormat.PNG)!!.bytes
            ImageIO.read(ByteArrayInputStream(png))
        }.getOrNull() ?: return null
        return save(projectId, img)
    }
}
