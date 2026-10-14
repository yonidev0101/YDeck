package com.yonidev.ydeck.data


/** How a link picks its account: Google services switch account by email, Vercel picks a Chrome channel, the rest open plainly. */
enum class Route { GOOGLE, VERCEL, PLAIN }

enum class Category(val label: String) {
    GOOGLE("גוגל"), HOSTING("אחסון ופריסה"), CODE("קוד"), DATA("דאטה ו־CMS"), AI("AI"),
    ANALYTICS("אנליטיקס וניטור"), PAYMENTS("תשלומים"), STORES("חנויות אפליקציות"), COMMS("מייל ותקשורת"),
    AUTH("התחברות"), DESIGN("עיצוב"), WORK("ניהול ואוטומציה"), DOMAINS("דומיינים"), FILES("קבצים ומדיה"),
    PASSWORDS("סיסמאות"), SOCIAL("רשתות"), GENERAL("כללי"),
}

/**
 * One known service. The catalog itself is generated (ServiceCatalog.kt, from tools/gen-services.mjs) with
 * real brand marks from simple-icons; [brandIcon] false means "no mark in the library, show the site's favicon".
 */
data class Service(
    val id: String,
    val label: String,
    val short: String,
    val icon: Int,
    val brandIcon: Boolean,
    /** Official brand colour, or null for favicon-only services. */
    val color: Int?,
    val route: Route,
    val defaultUrl: String,
    /** "host" or "host/path-prefix" patterns that identify a pasted URL. */
    val match: List<String>,
    val category: Category,
) {
    companion object {
        val all: List<Service> get() = CATALOG
        private val byId by lazy { CATALOG.associateBy { it.id } }
        val LINK: Service get() = byId.getValue("link")

        /** Shown first in the link editor before the user searches. */
        val POPULAR: List<Service> by lazy {
            listOf(
                "play", "firebase", "gmail", "drive", "vercel", "github", "supabase", "mongodb",
                "railway", "cloudflare", "stripe", "sentry", "analytics", "appstore", "openai", "anthropic",
                "figma", "notion", "site", "link",
            ).mapNotNull { byId[it] }
        }

        fun of(id: String): Service = byId[id] ?: LINK

        /** Guesses the service from a pasted URL, so the user only has to paste. The most specific pattern wins. */
        fun detect(url: String): Service? {
            val host = Links.host(url) ?: return null
            val path = Links.path(url)
            return CATALOG.flatMap { s -> s.match.map { s to it } }
                .filter { (_, pattern) ->
                    val h = pattern.substringBefore('/')
                    val p = pattern.substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }
                    (host == h || host.endsWith(".$h")) && path.startsWith(p)
                }
                .maxByOrNull { it.second.length }?.first
        }
    }
}

data class DeckAction(val service: Service, val url: String, val label: String) {
    val hasCustomLabel: Boolean get() = label != service.label

    /** Widget tiles are narrow on a 4" screen: use the short service name unless the user wrote their own. */
    val tileLabel: String get() = if (hasCustomLabel) label else service.short

    /** Host whose favicon stands in when the service has no brand mark. */
    val faviconHost: String? get() = if (service.brandIcon) null else Links.host(url)
}

data class CopyItem(val label: String, val value: String)

/** A Chrome channel (separate app, separate sign-in) that one Vercel account lives in. */
data class Browser(val pkg: String, val label: String) {
    companion object {
        val CHANNELS = listOf(
            Browser("com.android.chrome", "Chrome"),
            Browser("com.chrome.beta", "Chrome Beta"),
            Browser("com.chrome.dev", "Chrome Dev"),
            Browser("com.chrome.canary", "Chrome Canary"),
        )
    }
}

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
    /** Logo image, relative to filesDir (e.g. "logos/yvoice-1.png"); null draws the monogram. */
    val logo: String? = null,
) {
    val accountLabel: String get() = googleEmail?.substringBefore('@') ?: "חשבון לא הוגדר"

    companion object {
        fun blank(): Project = Project(
            id = "p" + System.currentTimeMillis().toString(36),
            name = "", mono = "", color = 0xFF1A6FDB.toInt(), colorTemporary = false, kind = "",
            googleEmail = null, vercel = null, actions = emptyList(), copies = emptyList(),
        )

        /** "Nova", the made-up project from the marketing films: what the welcome screen adds to try the widget. */
        fun sample(): Project = Project(
            id = "nova-sample",
            name = "Nova", mono = "N", color = 0xFF0B0B0C.toInt(), colorTemporary = false, kind = "פרויקט לדוגמה",
            googleEmail = "dana@nova.dev", vercel = Browser.CHANNELS.first(),
            actions = listOf("firebase", "play", "vercel", "github", "sentry", "analytics", "drive").map { id ->
                Service.of(id).let { DeckAction(it, it.defaultUrl, it.label) }
            },
            copies = listOf(CopyItem("Package", "dev.nova.app")),
        )
    }
}

data class Deck(val projects: List<Project>) {
    fun byId(id: String?): Project? = projects.firstOrNull { it.id == id }
}
