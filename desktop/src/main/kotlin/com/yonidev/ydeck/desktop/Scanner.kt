package com.yonidev.ydeck.desktop

import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/** What a project folder tells about the project: its links, values to copy, commands, a name, a kind and a logo. */
data class Found(
    val name: String?,
    val kind: String?,
    val actions: List<DeckAction>,
    val copies: List<CopyItem>,
    val commands: List<Command>,
    val logo: File?,
) {
    /** "GitHub, Firebase, Package, 3 commands, logo" */
    fun summary(): String = buildList {
        addAll(actions.map { it.label })
        addAll(copies.map { it.label })
        if (commands.isNotEmpty()) add(count(commands.size, "command", "commands", "פקודה אחת", "פקודות"))
        if (logo != null) add(tr("logo", "לוגו"))
    }.joinToString(", ")
}

/**
 * Reads the usual files of a project folder: the git remote, .firebaserc, .vercel/project.json, the Supabase project
 * ref, wrangler.toml, the Android applicationId and launcher icon, package.json (name, scripts, homepage).
 * Only reads; nothing leaves the PC.
 */
object Scanner {
    private val SKIP = setOf("node_modules", "build", "dist", ".git", ".gradle", ".next", ".idea", "out", "target", ".vercel", ".wrangler", "vendor")

    fun scan(dir: File): Found {
        val actions = mutableListOf<DeckAction>()
        val copies = mutableListOf<CopyItem>()
        val commands = mutableListOf<Command>()
        fun link(serviceId: String, url: String) {
            val s = Catalog.of(serviceId)
            if (actions.none { it.url == url }) actions += DeckAction(s, url, s.label)
        }

        // git: the repository page, and owner/name to copy.
        text(File(dir, ".git/config"))?.let(::originUrl)?.let(::webUrl)?.let { web ->
            link(Catalog.detect(web)?.id ?: "github", web)
            repoName(web)?.let { copies += CopyItem("Repo", it) }
        }
        // Firebase: the default project in .firebaserc (here or one level down, as in an app + functions layout).
        find(dir, ".firebaserc", 2)?.let(::text)?.let(::firebaseProject)?.let { link("firebase", "https://console.firebase.google.com/project/$it/overview") }
        // Vercel: the linked project, opened in whichever team it belongs to.
        find(dir, ".vercel/project.json", 2)?.let(::text)?.let { runCatching { JSONObject(it).optString("projectName") }.getOrNull() }?.takeIf { it.isNotBlank() }?.let {
            link("vercel", "https://vercel.com/d?to=" + URLEncoder.encode("/[team]/$it", "UTF-8"))
        }
        // Supabase: the CLI keeps the linked project's ref.
        find(dir, "supabase/.temp/project-ref", 2)?.let(::text)?.trim()?.takeIf { Regex("[a-z0-9]{10,40}").matches(it) }?.let {
            link("supabase", "https://supabase.com/dashboard/project/$it")
        }
        val wrangler = File(dir, "wrangler.toml").takeIf { it.isFile } ?: File(dir, "wrangler.jsonc").takeIf { it.isFile }
        if (wrangler != null) link("cloudflare", Catalog.of("cloudflare").defaultUrl)

        // Android: the package to copy, and Play Console.
        val android = Tools.isAndroid(dir)
        val gradleFiles = listOf("app/build.gradle.kts", "app/build.gradle", "build.gradle.kts", "build.gradle").map { File(dir, it) }.filter { it.isFile }
        val pkg = gradleFiles.firstNotNullOfOrNull { text(it)?.let(::applicationId) }
        if (pkg != null) {
            copies += CopyItem("Package", pkg)
            link("play", Catalog.of("play").defaultUrl)
        }

        // package.json: the site, the scripts worth a button.
        val pkgJson = text(File(dir, "package.json"))?.let { runCatching { JSONObject(it) }.getOrNull() }
        pkgJson?.optString("homepage")?.takeIf { it.startsWith("http") }?.let { link(Catalog.detect(it)?.id ?: "site", it) }
        if (pkgJson != null) {
            val runner = when {
                File(dir, "pnpm-lock.yaml").isFile -> "pnpm"
                File(dir, "yarn.lock").isFile -> "yarn"
                File(dir, "bun.lockb").isFile || File(dir, "bun.lock").isFile -> "bun"
                else -> "npm"
            }
            val scripts = pkgJson.optJSONObject("scripts")
            listOf("dev", "start", "build", "deploy", "test").filter { scripts?.has(it) == true }.take(4).forEach { s ->
                commands += Command(s, if (runner == "npm" && s in setOf("start", "test")) "npm $s" else "$runner run $s")
            }
        }
        if (wrangler != null && commands.none { it.label == "deploy" }) commands += Command("deploy", "npx wrangler deploy")
        if (android && File(dir, "gradlew.bat").isFile) commands += Command(tr("Build APK", "בניית APK"), "gradlew.bat assembleDebug")

        val name = listOf("settings.gradle.kts", "settings.gradle").firstNotNullOfOrNull { f ->
            text(File(dir, f))?.let { Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""").find(it)?.groupValues?.get(1) }
        } ?: prettify(dir.name)
        val deps = pkgJson?.let { listOfNotNull(it.optJSONObject("dependencies"), it.optJSONObject("devDependencies")).flatMap { d -> d.keys().asSequence().toList() } }.orEmpty()
        val kind = when {
            android -> tr("Android app", "אפליקציית Android")
            "next" in deps -> tr("Next.js site", "אתר Next.js")
            "astro" in deps -> tr("Astro site", "אתר Astro")
            "vite" in deps || "react" in deps -> tr("Website", "אתר")
            wrangler != null -> "Cloudflare Worker"
            pkgJson != null -> tr("Node project", "פרויקט Node")
            else -> null
        }
        return Found(name, kind, actions, copies, commands, logo(dir))
    }

    /** The app's launcher icon, or the site's: the large ones first. */
    fun logo(dir: File): File? {
        val fixed = listOf(
            "store/play/icon-512.png", "store/icon-512.png", "play/icon-512.png", "app/src/main/ic_launcher-playstore.png", "src/main/ic_launcher-playstore.png",
            "public/apple-touch-icon.png", "public/icon-512.png", "public/android-chrome-512x512.png", "public/logo512.png", "public/icon.png", "public/logo.png",
            "app/icon.png", "src/app/icon.png", "app/apple-icon.png", "src/app/apple-icon.png", "static/icon.png", "assets/icon.png", "icon.png", "logo.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp", "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "public/favicon.png", "public/logo192.png", "app/favicon.ico", "src/app/favicon.ico", "public/favicon.ico",
        )
        fixed.map { File(dir, it) }.firstOrNull { it.isFile && it.length() > 200 && sharp(it) }?.let { return it }
        return find(dir, "src/main/ic_launcher-playstore.png", 2)
    }

    /** Big enough to be a logo (a 32 px favicon would only blur). */
    private fun sharp(f: File): Boolean = runCatching { org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).width >= 96 }.getOrDefault(false)

    fun originUrl(gitConfig: String): String? {
        var inOrigin = false
        var first: String? = null
        for (line in gitConfig.lines().map { it.trim() }) {
            if (line.startsWith("[")) { inOrigin = line.replace(" ", "") == "[remote\"origin\"]"; continue }
            val url = line.takeIf { it.startsWith("url") && it.contains('=') }?.substringAfter('=')?.trim() ?: continue
            if (inOrigin) return url
            if (first == null) first = url
        }
        return first
    }

    /** git@github.com:o/r.git, ssh://git@host/o/r, https://user@host/o/r.git → https://host/o/r */
    fun webUrl(remote: String): String? {
        val r = remote.trim().removeSuffix("/").removeSuffix(".git")
        val m = Regex("""^(?:ssh://)?[\w.-]+@([\w.-]+)[:/](.+)$""").find(r)
        val (host, path) = when {
            m != null && !r.startsWith("http") -> m.groupValues[1] to m.groupValues[2]
            r.startsWith("http") -> Regex("""^https?://(?:[^@/]+@)?([^/]+)/(.+)$""").find(r)?.let { it.groupValues[1] to it.groupValues[2] } ?: return null
            else -> return null
        }
        return "https://$host/${path.trimStart('/')}"
    }

    private fun repoName(web: String): String? = Links.path(web).trim('/').takeIf { it.count { c -> c == '/' } >= 1 }

    fun firebaseProject(rc: String): String? = runCatching { JSONObject(rc).optJSONObject("projects")?.optString("default") }.getOrNull()?.takeIf { it.isNotBlank() }

    fun applicationId(gradle: String): String? =
        Regex("""applicationId\s*(?:=\s*)?["']([\w.]+)["']""").find(gradle)?.groupValues?.get(1)
            ?: Regex("""namespace\s*(?:=\s*)?["']([\w.]+)["']""").find(gradle)?.groupValues?.get(1)

    fun prettify(folder: String): String = folder.replace('-', ' ').replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

    private fun text(f: File): String? = runCatching { f.takeIf { it.isFile && it.length() < 512_000 }?.readText() }.getOrNull()

    /** [rel] in [dir], or in a folder up to [depth] levels below it (skipping dependencies and build output). */
    private fun find(dir: File, rel: String, depth: Int): File? {
        File(dir, rel).takeIf { it.isFile }?.let { return it }
        if (depth <= 0) return null
        val subs = dir.listFiles { f -> f.isDirectory && f.name !in SKIP && !f.name.startsWith(".") }?.sortedBy { it.name } ?: return null
        for (s in subs) find(s, rel, depth - 1)?.let { return it }
        return null
    }

    /** Merges what was found into [p]: links and values it doesn't have yet; the name, kind and colour only when empty. */
    fun merge(p: Project, found: Found): Project {
        fun has(a: DeckAction) = p.actions.any { it.url == a.url || (it.service.id == a.service.id && a.service.id !in setOf("link", "site")) }
        return p.copy(
            name = p.name.ifBlank { found.name.orEmpty() },
            kind = p.kind.ifBlank { found.kind.orEmpty() },
            actions = p.actions + found.actions.filterNot(::has),
            copies = p.copies + found.copies.filter { c -> p.copies.none { it.value == c.value } },
        )
    }
}
