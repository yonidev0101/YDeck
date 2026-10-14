package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

/*
 * Freeing space: in each project folder, the folders its tools rebuild by themselves (node_modules, Gradle's build,
 * .next and the like), each only beside the file that proves it (package.json, build.gradle…). Never dist/, never
 * anything else; nothing goes without a confirmation that says what will be deleted and how it comes back.
 */

/** A folder a tool rebuilds: [name] beside one of [beside], brought back by [rebuild]. */
private data class Rule(val name: String, val beside: List<String>, val rebuild: String)

data class Junk(val dir: File, val bytes: Long, val rebuild: String)

data class Clutter(val project: Project, val folder: File, val items: List<Junk>, val lastAt: Long) {
    val bytes: Long get() = items.sumOf { it.bytes }
}

object Cleaner {
    private val NODE = listOf("package.json")
    private val RULES = listOf(
        Rule("node_modules", NODE, "npm install"),
        Rule(".next", NODE, "build"), Rule(".nuxt", NODE, "build"), Rule(".svelte-kit", NODE, "build"),
        Rule(".turbo", NODE, "build"), Rule(".parcel-cache", NODE, "build"), Rule(".angular", NODE, "build"),
        Rule("build", listOf("build.gradle.kts", "build.gradle"), "Gradle"),
        Rule(".gradle", listOf("settings.gradle.kts", "settings.gradle"), "Gradle"),
        Rule(".kotlin", listOf("settings.gradle.kts", "settings.gradle"), "Gradle"),
        Rule("target", listOf("Cargo.toml", "pom.xml"), "cargo / Maven"),
        Rule(".wrangler", listOf("wrangler.toml", "wrangler.jsonc", "wrangler.json"), "wrangler"),
    )
    private val NEVER = setOf(".git", "dist", "releases", "store", "src")

    /** What [root] can rebuild, three levels deep (an app inside a monorepo, Gradle modules). */
    fun find(root: File): List<Pair<File, String>> {
        val out = mutableListOf<Pair<File, String>>()
        fun visit(dir: File, depth: Int) {
            val children = dir.listFiles { f -> f.isDirectory } ?: return
            for (child in children) {
                val rule = RULES.firstOrNull { r -> r.name == child.name && r.beside.any { File(dir, it).isFile } }
                if (rule != null) { if (!Files.isSymbolicLink(child.toPath())) out += child to rule.rebuild; continue }
                if (depth < 3 && child.name !in NEVER && !child.name.startsWith(".") && child.name != "node_modules") visit(child, depth + 1)
            }
        }
        visit(root, 1)
        return out
    }

    /** The bytes under [dir], not following links or junctions (pnpm's), so roughly what deleting it frees. */
    fun size(dir: File): Long {
        var total = 0L
        runCatching {
            Files.walkFileTree(dir.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(d: Path, attrs: BasicFileAttributes) =
                    if (attrs.isSymbolicLink || attrs.isOther) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                override fun visitFile(f: Path, attrs: BasicFileAttributes): FileVisitResult { if (attrs.isRegularFile) total += attrs.size(); return FileVisitResult.CONTINUE }
                override fun visitFileFailed(f: Path, e: IOException) = FileVisitResult.CONTINUE
            })
        }
        return total
    }

    fun scan(deck: Deck, settings: Settings.State, progress: (String) -> Unit): List<Clutter> = deck.projects.mapNotNull { p ->
        val dir = settings.localOf(p.id).dir ?: return@mapNotNull null
        progress(p.name)
        val items = find(dir).map { (f, how) -> Junk(f, size(f), how) }.filter { it.bytes > 0 }
        val last = Git.state.value[dir.path]?.lastAt?.takeIf { it > 0 } ?: dir.lastModified()
        Clutter(p, dir, items, last)
    }.filter { it.items.isNotEmpty() }.sortedByDescending { it.bytes }

    /**
     * Deletes [junk] with `rd /s /q`, which removes junctions without going into them (a pnpm store elsewhere
     * stays). Only a folder inside the project's own folder.
     */
    fun remove(root: File, junk: Junk): Boolean {
        val f = junk.dir.canonicalFile
        if (!f.path.startsWith(root.canonicalPath + File.separator) || f.name !in RULES.map { it.name }) return false
        return runCatching {
            val p = ProcessBuilder("cmd.exe", "/c", "rd", "/s", "/q", f.path).redirectErrorStream(true).start()
            p.inputStream.readAllBytes()
            p.waitFor(10, TimeUnit.MINUTES)
            !f.exists()
        }.getOrDefault(false)
    }
}

/** 4.2GB, 380MB, 12KB. */
fun bytes(n: Long): String = when {
    n >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.1fGB", n / (1L shl 30).toDouble())
    n >= 1L shl 20 -> "${n shr 20}MB"
    else -> "${(n shr 10).coerceAtLeast(1)}KB"
}

/** Settings › Storage: what each project can free, and freeing it after a confirmation. */
@Composable
fun StorageSection(deck: Deck) {
    val c = LocalDeck.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<Clutter>?>(null) }
    var scanning by remember { mutableStateOf<String?>("") }
    var confirm by remember { mutableStateOf<Clutter?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var freed by remember { mutableStateOf(mapOf<String, Long>()) }
    val pulses by Pulses.state.collectAsState()

    fun scan() {
        scanning = ""
        scope.launch {
            found = withContext(Dispatchers.IO) { Cleaner.scan(deck, Settings.state.value) { scanning = it } }
            scanning = null
        }
    }
    LaunchedEffect(Unit) { scan() }

    val list = found
    val total = list?.filter { it.project.id !in freed }?.sumOf { it.bytes } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    scanning != null -> tr("Looking through ${scanning?.ifBlank { null } ?: "the projects"}…", "בודק ${scanning?.ifBlank { null } ?: "את הפרויקטים"}…")
                    list.isNullOrEmpty() -> tr("Nothing to free. The project folders are clean.", "אין מה לפנות. תיקיות הפרויקטים נקיות.")
                    else -> tr("${bytes(total)} can be freed", "אפשר לפנות ${bytes(total)}")
                },
                color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f),
            )
            if (scanning == null) Pill(tr("Scan again", "סריקה מחדש"), Tone.SOFT, small = true) { freed = emptyMap(); scan() }
        }
        if (!list.isNullOrEmpty()) Group {
            list.forEach { cl ->
                val gone = freed[cl.project.id]
                val running = pulses[cl.folder.path]?.port
                Item {
                    Logo(cl.project, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(cl.project.name, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                        Text(if (gone != null) tr("Freed ${bytes(gone)}", "פונו ${bytes(gone)}") else "${bytes(cl.bytes)} · ${tr("last work ${ago(cl.lastAt)}", "עבודה אחרונה ${ago(cl.lastAt)}")}",
                            color = if (gone != null) CLEAN_DOT else c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                        if (gone == null) Mono(cl.items.joinToString("  ·  ") { "${it.dir.relativeTo(cl.folder).invariantSeparatorsPath} ${bytes(it.bytes)}" }, c.faint, 11)
                    }
                    when {
                        gone != null -> GlyphIcon(Glyph.CHECK, 18.dp, CLEAN_DOT)
                        busy == cl.project.id -> Text(tr("Freeing…", "מפנה…"), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
                        running != null -> Text(tr("Running on :$running", "רץ ב־:$running"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
                        else -> Pill(tr("Free", "פינוי"), Tone.SOFT, small = true, enabled = busy == null) { confirm = cl }
                    }
                }
            }
        }
        Text(tr("Only folders the tools rebuild by themselves on the next run: node_modules, Gradle's build, .next and the like. The code, dist and releases are never touched.",
            "רק תיקיות שהכלים בונים מחדש לבד בהרצה הבאה: node_modules, ה־build של Gradle, ‏.next ודומיהן. הקוד, dist והגרסאות לא נוגעים."),
            color = c.faint, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp))
    }

    confirm?.let { cl ->
        val how = cl.items.map { it.rebuild }.distinct().joinToString(" / ")
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(tr("Free ${bytes(cl.bytes)} from ${cl.project.name}?", "לפנות ${bytes(cl.bytes)} מ־${cl.project.name}?"), fontFamily = PlexSans) },
            text = {
                Column {
                    Text(tr("These are deleted for good:", "יימחקו לצמיתות:"), fontFamily = PlexSans, fontSize = 14.sp)
                    cl.items.forEach { Text(it.dir.relativeTo(cl.folder).invariantSeparatorsPath, fontFamily = PlexMono, fontSize = 13.sp, style = TextStyle(textDirection = TextDirection.Ltr)) }
                    Text(tr("They come back on the next run ($how). The code isn't touched.", "הן ייבנו מחדש בהרצה הבאה ($how). הקוד לא נוגע."), fontFamily = PlexSans, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton({
                    confirm = null
                    busy = cl.project.id
                    scope.launch {
                        val done = withContext(Dispatchers.IO) { cl.items.filter { Cleaner.remove(cl.folder, it) }.sumOf { it.bytes } }
                        freed = freed + (cl.project.id to done)
                        busy = null
                    }
                }) { Text(tr("Free", "פינוי"), color = c.danger, fontFamily = PlexSans) }
            },
            dismissButton = { TextButton({ confirm = null }) { Text(tr("Cancel", "ביטול"), color = c.fg, fontFamily = PlexSans) } },
        )
    }
}
