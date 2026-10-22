package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

/*
 * Updates: every few hours YDeck asks GitHub for the latest release. A newer one shows in the sidebar and, once, as a
 * Windows notification; "Update now" downloads its installer, closes YDeck, upgrades in place and opens it again.
 * The check is a plain request to GitHub's API (GitHub sees the address, as with any download); off in Settings.
 */
object Updates {
    data class Release(val version: String, val notes: String, val msi: String?, val page: String)

    private val _available = MutableStateFlow<Release?>(null)
    /** A release newer than this one, once found. */
    val available: StateFlow<Release?> = _available

    private val _progress = MutableStateFlow<Float?>(null)
    /** While the installer downloads: how much of it, 0 to 1. */
    val progress: StateFlow<Float?> = _progress

    /** Told once for each new version (a Windows notification). */
    var onNew: (Release) -> Unit = {}

    private const val LATEST = "https://api.github.com/repos/yonidev0101/YDeck/releases/latest"

    fun start() {
        Thread({
            Thread.sleep(90_000)
            while (true) {
                runCatching { if (Settings.state.value.updateCheck) check() }
                Thread.sleep(6 * 3_600_000L)
            }
        }, "ydeck-updates").apply { isDaemon = true; start() }
    }

    /** Asks GitHub now. Returns the newer release, or null when this one is the latest (or GitHub can't be reached). */
    fun check(): Release? {
        if (AppInfo.version == "dev") return null
        val o = runCatching {
            val c = URI(LATEST).toURL().openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "YDeck/${AppInfo.version}")
                if (c.responseCode != 200) return null
                JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            } finally { c.disconnect() }
        }.getOrNull() ?: return null
        val version = o.optString("tag_name").removePrefix("v")
        if (!newer(version, AppInfo.version)) { _available.value = null; return null }
        val assets = o.optJSONArray("assets")
        val msi = (0 until (assets?.length() ?: 0)).mapNotNull { assets!!.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".msi") }?.optString("browser_download_url")
        val r = Release(version, o.optString("body"), msi, o.optString("html_url"))
        val first = _available.value?.version != version
        _available.value = r
        if (first && Settings.state.value.updateTold != version) {
            Settings.update { it.copy(updateTold = version) }
            onNew(r)
        }
        return r
    }

    /** "1.18.10" is newer than "1.18.9": part by part, as numbers. */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /**
     * Downloads the installer, then hands over to Windows: once YDeck has quit, the installer upgrades it in place
     * (a progress bar, no questions) and opens it again. Returns what went wrong, or never returns.
     */
    fun install(r: Release): String? {
        val url = r.msi ?: return tr("This release has no installer yet", "לגרסה הזאת עוד אין קובץ התקנה")
        val file = File(System.getProperty("java.io.tmpdir"), "YDeck-Windows-${r.version}.msi")
        val got = runCatching {
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = 60_000
                c.setRequestProperty("User-Agent", "YDeck/${AppInfo.version}")
                if (c.responseCode != 200) return@runCatching false
                val total = c.contentLengthLong.takeIf { it > 0 }
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total != null) _progress.value = done.toFloat() / total
                        }
                    }
                }
                file.length() > 1_000_000 && (total == null || file.length() == total)
            } finally { c.disconnect() }
        }.getOrDefault(false)
        _progress.value = null
        if (!got) return tr("The download didn't finish. Try again, or download it from the site.", "ההורדה לא הסתיימה. אפשר לנסות שוב, או להוריד מהאתר.")
        val exe = ProcessHandle.current().info().command().orElse(null)?.takeIf { it.endsWith("YDeck.exe", ignoreCase = true) }
        val reopen = exe?.let { " & start \"\" \"$it\"" }.orEmpty()
        runCatching {
            ProcessBuilder("cmd.exe", "/d", "/c", "ping -n 3 127.0.0.1 >nul & start \"\" /wait msiexec.exe /i \"${file.path}\" /passive$reopen")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }.onFailure { return it.message }
        exitProcess(0)
    }
}

/** Above Settings in the sidebar, while a newer YDeck is out: a click shows what's new and updates. */
@Composable
fun UpdateRow() {
    val r = Updates.available.collectAsState().value ?: return
    val c = LocalDeck.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(c.accentTint).clickable { open = true }
        .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.DOWNLOAD, 16.dp, c.accentText)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Update available", "עדכון זמין"), color = c.accentText, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
            Text(tr("YDeck ${r.version}", "YDeck ${r.version}"), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
        }
    }
    if (open) UpdateDialog(r) { open = false }
}

/** What's new in [r], and the button that updates. */
@Composable
fun UpdateDialog(r: Updates.Release, onClose: () -> Unit) {
    val c = LocalDeck.current
    val progress = Updates.progress.collectAsState().value
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("YDeck ${r.version} is out", "YDeck ${r.version} יצאה"), fontFamily = PlexSans) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("You have ${AppInfo.version}. YDeck closes, updates, and opens again; your projects and settings stay.",
                    "אצלך ${AppInfo.version}. YDeck ייסגר, יתעדכן וייפתח שוב. הפרויקטים וההגדרות נשארים."),
                    color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans)
                val notes = plain(r.notes)
                if (notes.isNotBlank()) Text(notes, color = c.fg, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = PlexSans,
                    style = TextStyle(textDirection = dirOf(notes)), modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
                progress?.let { Text(tr("Downloading… ${(it * 100).toInt()}%", "מוריד… ${(it * 100).toInt()}%"), color = c.accentText, fontSize = 13.sp, fontFamily = PlexSans) }
                error?.let { Text(it, color = c.danger, fontSize = 13.sp, fontFamily = PlexSans) }
            }
        },
        confirmButton = {
            TextButton({
                busy = true; error = null
                Thread { error = Updates.install(r); busy = false }.start()
            }, enabled = !busy) { Text(if (busy) tr("Updating…", "מעדכן…") else tr("Update now", "עדכון עכשיו"), color = c.accentText, fontFamily = PlexSans) }
        },
        dismissButton = {
            Row {
                TextButton({ runCatching { java.awt.Desktop.getDesktop().browse(URI(r.page)) } }) { Text(tr("On GitHub", "ב־GitHub"), color = c.muted, fontFamily = PlexSans) }
                TextButton({ onClose() }, enabled = !busy) { Text(tr("Later", "אחר כך"), color = c.fg, fontFamily = PlexSans) }
            }
        },
    )
}

/** A release's notes without their markdown: the headings and bullets as plain lines, the links and the changelog left out. */
fun plain(md: String): String = md.lines()
    .takeWhile { !it.startsWith("### Install") }
    .filterNot { it.startsWith("**Full Changelog**") }
    .joinToString("\n") { l ->
        l.replace(Regex("^#+\\s*"), "").replace(Regex("^[-*]\\s+"), "• ").replace("**", "").replace("`", "")
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
    }.replace(Regex("\n{3,}"), "\n\n").trim()
