package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/*
 * Anonymous usage counts, so it's known whether YDeck helps anyone: how many install it, set it up, come back, and
 * which parts they use. Shown and explained on the first run (and once on Now for earlier installs), and off with one
 * switch in Settings. Nothing is sent before that choice was shown, or when it's off.
 *
 * What a day sends (stats/src/index.js keeps nothing else, and no IP address): a random id made on this PC, the date,
 * the version, the language, how many projects as a range, whether Claude Code is connected, and which parts were
 * used that day ([Feature]). Never a project's name, a path, code, a link, a prompt or an email address.
 */
object UsageStats {
    private const val BASE = "https://ydeck-stats.yonidev-cloud.workers.dev"

    enum class Feature(val key: String) {
        NOW("now"), RECEIPT("receipt"), COMMIT("commit"), PUSH("push"), CLOSE_DAY("closeday"), FILL("fill"),
        WIDGET("widget"), STRIP("strip"), SEARCH("search"), PHONE("phone"),
    }

    /** The parts used today, until they're sent; a new day starts afresh. */
    private val used = ConcurrentHashMap.newKeySet<Feature>()
    @Volatile private var day = LocalDate.now()
    @Volatile private var sent = ""

    fun mark(f: Feature) {
        rollOver()
        used += f
    }

    private val on: Boolean get() = Settings.state.value.let { it.usageStats && it.statsAsked }

    /** Every few minutes, today's counts when they changed; the install once. In the background, never in the way. */
    fun start() {
        Thread({
            Thread.sleep(60_000)
            while (true) {
                runCatching { if (on) tick() }
                Thread.sleep(10 * 60_000L)
            }
        }, "ydeck-stats").apply { isDaemon = true; start() }
    }

    private fun tick() {
        val id = id()
        val s = Settings.state.value
        if (!s.statsInstalled && post("/v1/event", JSONObject().put("id", id).put("e", "install").put("v", AppInfo.version))) {
            Settings.update { it.copy(statsInstalled = true) }
        }
        rollOver()
        val features = buildList {
            addAll(used)
            if (s.widgets.isNotEmpty()) add(Feature.WIDGET)
        }.map { it.key }.distinct().sorted()
        val projects = Repo.deck.value.projects.size.let { n -> when { n == 0 -> "0"; n == 1 -> "1"; n <= 5 -> "2-5"; n <= 15 -> "6-15"; else -> "16+" } }
        val body = JSONObject().put("id", id).put("day", day.toString()).put("v", AppInfo.version).put("lang", if (I18n.rtl) "he" else "en")
            .put("projects", projects).put("claude", ClaudePlugin.installed).put("features", JSONArray(features))
        // The same day again, unchanged, isn't sent again.
        val key = body.toString()
        if (key != sent && post("/v1/day", body)) sent = key
    }

    /** As YDeck is removed (see [Uninstall]): counted once, if counting is on. Quick, and quiet when offline. */
    fun uninstalled() {
        if (!on) return
        post("/v1/event", JSONObject().put("id", id()).put("e", "uninstall").put("v", AppInfo.version), timeout = 3000)
    }

    private fun rollOver() {
        val today = LocalDate.now()
        if (today != day) { day = today; used.clear() }
    }

    private fun id(): String = Settings.state.value.statsId.ifBlank {
        val made = java.security.SecureRandom().let { r -> (1..16).joinToString("") { "%02x".format(r.nextInt(256)) } }
        Settings.update { it.copy(statsId = made) }
        made
    }

    private fun post(path: String, body: JSONObject, timeout: Int = 10_000): Boolean = runCatching {
        val c = URI(BASE + path).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = timeout; c.readTimeout = timeout
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            c.responseCode in 200..299
        } finally { c.disconnect() }
    }.getOrDefault(false)
}

/**
 * The choice about usage counts, plainly: what goes, what never does, and that one switch turns it off. On the first
 * run, once on Now for installs from before it, and in Settings.
 */
@androidx.compose.runtime.Composable
fun UsageStatsChoice(on: Boolean, onChange: (Boolean) -> Unit, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, buttons: (@androidx.compose.runtime.Composable () -> Unit)? = null) {
    val c = LocalDeck.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth().clip(shape).background(c.accentTint).border(1.dp, c.accent.copy(alpha = 0.35f), shape)
        .padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        GlyphIcon(Glyph.INFO, 20.dp, c.accentText)
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
            androidx.compose.material3.Text(tr("Anonymous usage counts help YDeck get better", "נתוני שימוש אנונימיים עוזרים ל־YDeck להשתפר"),
                color = c.fg, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontFamily = PlexSans)
            androidx.compose.material3.Text(tr(
                "Once a day: the version, the language, how many projects (a range), whether Claude Code is connected, and which parts you used. Never names, paths, code, links, prompts or anything that identifies you.",
                "פעם ביום: גרסה, שפה, כמה פרויקטים (טווח), אם Claude Code מחובר, ובאילו חלקים השתמשת. אף פעם לא שמות, נתיבים, קוד, קישורים, בקשות ל־Claude או משהו שמזהה אותך."),
                color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans)
            androidx.compose.material3.Text(tr("Turn it off here or any time in Settings → General. The code is open, so what's sent can be checked.",
                "אפשר לכבות כאן, או בכל רגע בהגדרות ← כללי. הקוד פתוח, ואפשר לבדוק בדיוק מה נשלח."),
                color = c.accentText, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontFamily = PlexSans)
        }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(16.dp))
        if (buttons != null) buttons() else Toggle(on) { onChange(it) }
    }
}
