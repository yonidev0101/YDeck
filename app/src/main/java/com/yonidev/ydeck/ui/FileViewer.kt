package com.yonidev.ydeck.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.yonidev.ydeck.data.FilesLine
import com.yonidev.ydeck.link.FileAsk
import com.yonidev.ydeck.link.FileCommit
import com.yonidev.ydeck.link.FileEntry
import com.yonidev.ydeck.link.FilePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * One file from the computer, full screen. Text and code: in colour with line numbers (as VS Code shows it), its Git
 * history and what each commit changed, and editing when the computer allows it. A picture: to zoom. Anything else:
 * to open in another app on the phone.
 */

internal val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "ico")
internal val TEXT_EXTS = setOf("txt", "md", "json", "kt", "kts", "java", "js", "mjs", "cjs", "ts", "tsx", "jsx", "html", "htm", "css", "scss", "xml", "yml", "yaml",
    "toml", "gradle", "properties", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs", "sh", "ps1", "bat", "cmd", "sql", "csv", "tsv", "log", "ini", "cfg",
    "conf", "svg", "gitignore", "gitattributes", "editorconfig", "lock", "env", "example", "swift", "dart", "vue", "svelte", "php", "pro")
private const val TEXT_MAX = 2L * 1024 * 1024
internal const val OFF_WORDS = "כדי לראות כאן את הקבצים, מדליקים במחשב: הגדרות ← טלפון ← \"הטלפון יכול לראות את הקבצים של הפרויקטים\"."

internal fun extOf(name: String) = name.substringAfterLast('.', if (name.startsWith('.')) name.drop(1) else "").lowercase()

internal fun sizeWords(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / (1024.0 * 1024))
}

private sealed interface Shown {
    data object Loading : Shown
    data class Progress(val got: Long, val total: Long) : Shown
    /** Text as it is on the computer: [bytes] (for its hash), shown with "\n" lines; [crlf] and [bom] as it had them. */
    data class Words(val bytes: ByteArray, val text: String, val crlf: Boolean, val bom: Boolean) : Shown
    data class Picture(val bitmap: android.graphics.Bitmap) : Shown
    data class Other(val bytes: ByteArray?) : Shown
    data class Failed(val why: String) : Shown
}

/** Bytes that read as text: UTF-8, with no zero byte. */
private fun words(b: ByteArray): Shown.Words? {
    if (b.any { it == 0.toByte() }) return null
    val s = runCatching { java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString() }.getOrNull() ?: return null
    val bom = s.startsWith('﻿')
    val t = s.removePrefix("﻿")
    return Shown.Words(b, t.replace("\r\n", "\n"), t.contains("\r\n"), bom)
}

/** The bytes to save: the lines and the BOM as the file had them. */
private fun bytesOf(w: Shown.Words, text: String): ByteArray =
    ((if (w.bom) "﻿" else "") + (if (w.crlf) text.replace("\n", "\r\n") else text)).toByteArray(Charsets.UTF_8)

/** How code is shown, as last chosen. */
internal object CodePrefs {
    private fun p(c: Context) = c.getSharedPreferences("code", Context.MODE_PRIVATE)
    fun size(c: Context) = p(c).getFloat("size", 12.5f)
    fun wrap(c: Context) = p(c).getBoolean("wrap", false)
    fun set(c: Context, size: Float, wrap: Boolean) = p(c).edit().putFloat("size", size).putBoolean("wrap", wrap).apply()
}

@Composable
internal fun FileViewer(projectId: String, path: String, e: FileEntry, editOn: Boolean, onClose: () -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = extOf(e.name)
    val picture = ext in IMAGE_EXTS
    val text = !picture && (ext in TEXT_EXTS || e.size <= 256 * 1024) && e.size <= TEXT_MAX
    var shown by remember(path) { mutableStateOf<Shown>(if (picture || text) Shown.Loading else Shown.Other(null)) }
    var tab by remember { mutableStateOf("code") }
    var size by remember { mutableStateOf(CodePrefs.size(context)) }
    var wrap by remember { mutableStateOf(CodePrefs.wrap(context)) }
    var editing by remember { mutableStateOf<TextFieldValue?>(null) }
    var saving by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf("") }
    var changedThere by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var commits by remember { mutableStateOf<List<FileCommit>?>(null) }
    var historyError by remember { mutableStateOf("") }
    var commit by remember { mutableStateOf<FileCommit?>(null) }
    var diff by remember { mutableStateOf<String?>(null) }

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

    suspend fun load() {
        if (picture) fetch(FileAsk.IMAGE)?.let { b ->
            val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(b, 0, b.size) }
            shown = if (bmp != null) Shown.Picture(bmp) else Shown.Other(b)
        } else if (text) fetch(FileAsk.READ)?.let { b -> shown = words(b) ?: Shown.Other(b) }
    }

    LaunchedEffect(path) { load() }
    LaunchedEffect(tab) {
        if (tab == "history" && commits == null) {
            val part = FilesLine.ask(projectId, path, FileAsk.HISTORY)
            when {
                part == null -> historyError = "המחשב לא ענה. אפשר לנסות שוב."
                part.error.isNotEmpty() -> historyError = part.error
                else -> commits = part.commits
            }
        }
    }
    LaunchedEffect(commit) {
        val cm = commit ?: return@LaunchedEffect
        diff = null
        val got = FilesLine.whole(projectId, path, FileAsk.DIFF, rev = cm.hash)
        diff = when {
            got == null -> "\u0000המחשב לא ענה."
            got.second.isNotEmpty() -> "\u0000" + got.second
            else -> String(got.first ?: ByteArray(0), Charsets.UTF_8)
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

    val words = shown as? Shown.Words
    val dirty = words != null && editing != null && editing!!.text != words.text

    fun save() {
        val w = words ?: return
        val now = editing?.text ?: return
        saving = true; said = ""
        scope.launch {
            val bytes = bytesOf(w, now)
            val r = FilesLine.put(projectId, path, FileAsk.hash(w.bytes), bytes)
            saving = false
            when {
                r == null -> said = "המחשב לא ענה. השינויים עדיין כאן, אפשר לנסות שוב."
                r.error == "changed" -> changedThere = true
                r.error == "editoff" -> said = "עריכה מהטלפון כבויה במחשב: הגדרות ← טלפון ← \"הטלפון יכול גם לערוך את הקבצים\"."
                r.error.isNotEmpty() -> said = r.error
                else -> { shown = w.copy(bytes = bytes, text = now); editing = null; said = "נשמר במחשב ✓"; commits = null }
            }
        }
    }

    fun tryClose() { if (dirty) leaving = true else onClose() }

    Dialog(onDismissRequest = ::tryClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(if (words != null) DarkPlus.bg else c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            // The file: its icon, name, size and age.
            Row(Modifier.fillMaxWidth().background(if (words != null) DarkPlus.bar else c.bg).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolIcon(Icons.Outlined.Close, "סגירה", c.fg, onClick = ::tryClose)
                Image(painterResource(FileIcons.of(context, e.name, false)), null, Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = dirOf(e.name)))
                    Text(if (dirty) "● לא נשמר" else sizeWords(e.size) + " · " + agoWords(e.at), color = if (dirty) DarkPlus.selector else c.faint, fontSize = 12.sp)
                }
            }
            if (words != null) {
                // Code / History, and the tools: wrap, text size, edit (or cancel / save while editing).
                Row(Modifier.fillMaxWidth().background(DarkPlus.bar).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editing == null) {
                        TabWord("קוד", tab == "code") { tab = "code" }
                        TabWord("היסטוריה", tab == "history") { tab = "history"; commit = null }
                    } else Text("עריכה", color = DarkPlus.selector, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 12.dp))
                    Spacer(Modifier.weight(1f))
                    ToolIcon(Icons.AutoMirrored.Outlined.WrapText, "גלישת שורות", if (wrap) DarkPlus.keyword else DarkPlus.gutter) { wrap = !wrap; CodePrefs.set(context, size, wrap) }
                    ToolWord("A−") { size = (size - 1f).coerceAtLeast(9f); CodePrefs.set(context, size, wrap) }
                    ToolWord("A+") { size = (size + 1f).coerceAtMost(20f); CodePrefs.set(context, size, wrap) }
                    when {
                        editing != null -> {
                            ToolWord("ביטול") { editing = null; said = "" }
                            Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(if (dirty && !saving) DarkPlus.keyword else DarkPlus.line)
                                .clickable(enabled = dirty && !saving) { save() }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                                if (saving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("שמירה", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        editOn && tab == "code" -> ToolIcon(Icons.Outlined.Edit, "עריכה", DarkPlus.fg) { editing = TextFieldValue(words.text); said = "" }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(DarkPlus.line))
                if (said.isNotEmpty()) Text(said, color = if (said.endsWith("✓")) DarkPlus.addedMark else DarkPlus.selector, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth().background(DarkPlus.bar).padding(horizontal = 16.dp, vertical = 8.dp))
            } else Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val s = shown
                when {
                    s is Shown.Words && editing != null -> CodeEditor(editing!!, e.name, size.sp) { editing = it }
                    s is Shown.Words && tab == "history" -> {
                        val cm = commit
                        if (cm == null) when {
                            historyError.isNotEmpty() -> Text(historyError, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                            commits == null -> CircularProgressIndicator(color = DarkPlus.gutter)
                            else -> Box(Modifier.fillMaxSize().background(c.bg)) { GitGraphList(commits!!) { commit = it } }
                        } else Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().background(DarkPlus.bar).clickable { commit = null }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "כל הקומיטים", tint = DarkPlus.fg, modifier = Modifier.size(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cm.subject, color = DarkPlus.fg, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                                    Text(cm.hash + " · " + cm.author + " · " + agoWords(cm.at), color = DarkPlus.gutter, fontSize = 11.5.sp, maxLines = 1)
                                }
                            }
                            val d = diff
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                when {
                                    d == null -> CircularProgressIndicator(color = DarkPlus.gutter)
                                    d.startsWith("\u0000") -> Text(d.drop(1), color = DarkPlus.gutter, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                                    else -> DiffReader(d, e.name, size.sp, wrap)
                                }
                            }
                        }
                    }
                    s is Shown.Words -> CodeReader(s.text, e.name, size.sp, wrap)
                    s is Shown.Loading -> CircularProgressIndicator(color = c.muted)
                    s is Shown.Progress -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = c.muted)
                        if (s.total > 0) Text("${sizeWords(s.got)} מתוך ${sizeWords(s.total)}", color = c.muted, fontSize = 13.sp)
                    }
                    s is Shown.Picture -> ZoomImage(s.bitmap)
                    s is Shown.Other -> Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("אי אפשר להציג את הקובץ הזה כאן.", color = c.muted, fontSize = 14.sp)
                        if (e.size > FilePart.MAX_FILE) Text("הוא גדול מדי להורדה לטלפון (מעל 20MB).", color = c.faint, fontSize = 13.sp)
                        else Text("להוריד ולפתוח בטלפון", color = c.bg, fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.fg).clickable {
                                val have = s.bytes
                                if (have != null) openOnPhone(have)
                                else scope.launch { fetch(FileAsk.READ)?.let { b -> shown = Shown.Other(b); openOnPhone(b) } }
                            }.padding(horizontal = 20.dp, vertical = 11.dp))
                    }
                    s is Shown.Failed -> Text(s.why, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(28.dp))
                }
            }
        }

        if (changedThere) AlertDialog(
            onDismissRequest = { changedThere = false },
            title = { Text("הקובץ השתנה במחשב") },
            text = { Text("מאז שפתחת אותו מישהו שינה אותו במחשב (אולי Claude). כדי לא לדרוס את זה, השינויים שלך לא נשמרו.") },
            confirmButton = { TextButton({ changedThere = false; editing = null; said = ""; shown = Shown.Loading; scope.launch { load() } }) { Text("לטעון את החדש") } },
            dismissButton = { TextButton({ changedThere = false }) { Text("להמשיך לערוך") } },
        )
        if (leaving) AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("לצאת בלי לשמור?") },
            text = { Text("השינויים שעשית בקובץ לא יישמרו.") },
            confirmButton = { TextButton({ leaving = false; onClose() }) { Text("לצאת") } },
            dismissButton = { TextButton({ leaving = false }) { Text("להישאר") } },
        )
    }
}

@Composable
private fun TabWord(word: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(end = 18.dp)) {
        Text(word, color = if (on) DarkPlus.fg else DarkPlus.gutter, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 11.dp, bottom = 9.dp))
        Box(Modifier.width(if (on) 28.dp else 0.dp).height(2.dp).background(DarkPlus.keyword))
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolWord(word: String, onClick: () -> Unit) {
    Text(word, color = DarkPlus.fg, fontSize = 13.5.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 9.dp))
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
