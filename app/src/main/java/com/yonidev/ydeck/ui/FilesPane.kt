package com.yonidev.ydeck.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.yonidev.ydeck.data.FilesLine
import com.yonidev.ydeck.link.FileAsk
import com.yonidev.ydeck.link.FileEntry
import com.yonidev.ydeck.link.FilePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * A project's folder on the computer, read only: its folders and files, one level at a time; a file opens here
 * (text, a picture), or comes to the phone to open in another app.
 */

private val IMAGES = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "ico")
private val TEXTS = setOf("txt", "md", "json", "kt", "kts", "java", "js", "mjs", "cjs", "ts", "tsx", "jsx", "html", "htm", "css", "scss", "xml", "yml", "yaml",
    "toml", "gradle", "properties", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs", "sh", "ps1", "bat", "cmd", "sql", "csv", "tsv", "log", "ini", "cfg",
    "conf", "svg", "gitignore", "gitattributes", "editorconfig", "lock", "env", "example", "swift", "dart", "vue", "svelte", "php", "pro", "txt")
/** A file with none of these endings opens as text when it's this small and reads as text. */
private const val TEXT_MAX = 2L * 1024 * 1024

private fun extOf(name: String) = name.substringAfterLast('.', if (name.startsWith('.')) name.drop(1) else "").lowercase()

private fun sizeWords(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / (1024.0 * 1024))
}

private fun whenWords(at: Long): String {
    if (at <= 0) return ""
    val d = (System.currentTimeMillis() - at) / 86_400_000L
    return when {
        d < 1 -> "היום"
        d < 2 -> "אתמול"
        d < 30 -> "לפני $d ימים"
        else -> java.text.SimpleDateFormat("d.M.yy", java.util.Locale.getDefault()).format(java.util.Date(at))
    }
}

private const val OFF_WORDS = "כדי לראות כאן את הקבצים, מדליקים במחשב: הגדרות ← טלפון ← \"הטלפון יכול לראות את הקבצים של הפרויקטים\"."

/** The Files tab: [on] when the computer allows it. */
@Composable
fun FilesPane(projectId: String, projectName: String, on: Boolean) {
    val c = LocalDeck.current
    val context = LocalContext.current
    if (!on) {
        Text(OFF_WORDS, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(24.dp))
        return
    }
    DisposableEffect(projectId) {
        FilesLine.start(context)
        onDispose { FilesLine.stop() }
    }
    var path by rememberSaveable(projectId) { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<FileEntry>?>(null) }
    var error by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(0L) }
    var reload by remember { mutableStateOf(0) }
    var opened by remember { mutableStateOf<Pair<String, FileEntry>?>(null) }
    LaunchedEffect(projectId, path, reload) {
        entries = null; error = ""
        val part = FilesLine.ask(projectId, path, FileAsk.LIST)
        when {
            part == null -> error = "המחשב לא ענה. אולי הוא כבוי או ש־YDeck סגור בו."
            part.error == "off" -> error = OFF_WORDS
            part.error.isNotEmpty() -> error = part.error
            else -> { entries = part.entries; more = part.total - part.entries.size }
        }
    }
    if (path.isNotEmpty()) BackHandler { path = path.substringBeforeLast('/', "") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp)) {
        // Where we are, and the way back: "back to <the folder above>" at the start, this folder's name at the end.
        val parts = path.split('/').filter { it.isNotEmpty() }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (parts.isNotEmpty()) {
                val up = parts.dropLast(1).lastOrNull() ?: projectName
                Row(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(50)).background(c.surface2).clickable { path = parts.dropLast(1).joinToString("/") }
                    .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.fg, modifier = Modifier.size(18.dp))
                    Text("חזרה ל־$up", color = c.fg, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(parts.lastOrNull() ?: projectName, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = dirOf(parts.lastOrNull() ?: projectName)), modifier = Modifier.weight(1f, fill = false))
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(c.line))
        val list = entries
        when {
            error.isNotEmpty() -> Column(Modifier.padding(vertical = 20.dp)) {
                Text(error, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
                Text("לנסות שוב", color = c.accentText, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp).clickable { reload++ })
            }
            list == null -> Row(Modifier.padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = c.muted, strokeWidth = 2.dp)
                val here by FilesLine.here.collectAsState()
                Text(if (here) "טוען…" else "מתחבר למחשב… (עד חצי דקה בפעם הראשונה)", color = c.muted, fontSize = 14.sp)
            }
            list.isEmpty() -> Text("התיקייה ריקה.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
            else -> {
                list.forEach { e ->
                    val full = if (path.isEmpty()) e.name else "$path/${e.name}"
                    val detail = if (e.dir) when (e.size) { 0L -> "ריקה"; 1L -> "פריט אחד"; else -> "${e.size} פריטים" }
                        else sizeWords(e.size) + " · " + whenWords(e.at)
                    EntryRow(iconOf(e), e.dir, e.name, detail) {
                        if (e.dir) path = full else opened = full to e
                    }
                }
                if (more > 0) Text("ועוד $more שלא מוצגים", color = c.faint, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    opened?.let { (full, e) -> FileViewer(projectId, full, e) { opened = null } }
}

private fun iconOf(e: FileEntry): ImageVector {
    val ext = extOf(e.name)
    return when {
        e.dir -> Icons.Outlined.Folder
        ext in IMAGES -> Icons.Outlined.Image
        ext in TEXTS -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

@Composable
private fun EntryRow(icon: ImageVector, dir: Boolean, name: String, detail: String, onClick: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (dir) c.accentText else c.muted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = c.fg, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(name)))
            Text(detail, color = c.faint, fontSize = 12.sp, maxLines = 1)
        }
        if (dir) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.faint, modifier = Modifier.size(20.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
}

private sealed interface Shown {
    data object Loading : Shown
    data class Progress(val got: Long, val total: Long) : Shown
    data class Words(val text: String, val cut: Boolean) : Shown
    data class Picture(val bitmap: android.graphics.Bitmap) : Shown
    data class Other(val bytes: ByteArray?) : Shown
    data class Failed(val why: String) : Shown
}

/** Bytes that read as text: UTF-8, with no zero byte. */
private fun asText(b: ByteArray): String? {
    if (b.any { it == 0.toByte() }) return null
    return runCatching {
        java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString()
    }.getOrNull()
}

/** One file, full screen: text, a picture, or what it is with a way to open it on the phone. */
@Composable
private fun FileViewer(projectId: String, path: String, e: FileEntry, onClose: () -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = extOf(e.name)
    val picture = ext in IMAGES
    val text = !picture && (ext in TEXTS || e.size <= 256 * 1024) && e.size <= TEXT_MAX
    var shown by remember(path) { mutableStateOf<Shown>(if (picture || text) Shown.Loading else Shown.Other(null)) }

    suspend fun fetch(what: String): ByteArray? {
        val got = FilesLine.whole(projectId, path, what) { have, total -> shown = Shown.Progress(have, total) }
        when {
            got == null -> shown = Shown.Failed("המחשב לא ענה. אפשר לנסות שוב.")
            got.second == "off" -> shown = Shown.Failed(OFF_WORDS)
            got.second.isNotEmpty() -> shown = Shown.Failed(got.second)
            else -> return got.first
        }
        return null
    }

    LaunchedEffect(path) {
        if (picture) fetch(FileAsk.IMAGE)?.let { b ->
            val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(b, 0, b.size) }
            shown = if (bmp != null) Shown.Picture(bmp) else Shown.Other(b)
        } else if (text) fetch(FileAsk.READ)?.let { b ->
            shown = asText(b)?.let { Shown.Words(if (it.length > 400_000) it.take(400_000) else it, it.length > 400_000) } ?: Shown.Other(b)
        }
    }

    fun openOnPhone(bytes: ByteArray) {
        val dir = File(context.cacheDir, "files").apply { deleteRecursively(); mkdirs() }
        val f = File(dir, e.name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        f.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(view, e.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("✕", color = c.fg, fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = dirOf(e.name)))
                    Text(sizeWords(e.size) + " · " + whenWords(e.at), color = c.faint, fontSize = 12.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val s = shown) {
                    Shown.Loading -> CircularProgressIndicator(color = c.muted)
                    is Shown.Progress -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = c.muted)
                        if (s.total > 0) Text("${sizeWords(s.got)} מתוך ${sizeWords(s.total)}", color = c.muted, fontSize = 13.sp)
                    }
                    is Shown.Words -> SelectionContainer(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
                            Text(s.text, color = c.fg, fontSize = 12.5.sp, lineHeight = 18.sp, fontFamily = PlexMono, softWrap = false,
                                style = TextStyle(textDirection = TextDirection.Ltr))
                            if (s.cut) Text("…הקובץ ארוך, מוצגת רק ההתחלה", color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    is Shown.Picture -> ZoomImage(s.bitmap)
                    is Shown.Other -> Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("אי אפשר להציג את הקובץ הזה כאן.", color = c.muted, fontSize = 14.sp)
                        if (e.size > FilePart.MAX_FILE) Text("הוא גדול מדי להורדה לטלפון (מעל 20MB).", color = c.faint, fontSize = 13.sp)
                        else Text("להוריד ולפתוח בטלפון", color = c.bg, fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.fg).clickable {
                                val have = s.bytes
                                if (have != null) openOnPhone(have)
                                else scope.launch { fetch(FileAsk.READ)?.let { b -> shown = Shown.Other(b); openOnPhone(b) } }
                            }.padding(horizontal = 20.dp, vertical = 11.dp))
                    }
                    is Shown.Failed -> Text(s.why, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(28.dp))
                }
            }
        }
    }
}

/** A picture that fits the screen, and zooms and moves with two fingers. */
@Composable
private fun ZoomImage(bitmap: android.graphics.Bitmap) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(image, null, Modifier.fillMaxSize().pointerInput(Unit) {
        detectTransformGestures { _, pan, z, _ ->
            zoom = (zoom * z).coerceIn(1f, 6f)
            if (zoom == 1f) { x = 0f; y = 0f } else { x += pan.x; y += pan.y }
        }
    }.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = x; translationY = y })
}
