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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
 * A project's folder on the computer: its folders and files, one level at a time, each with its icon as in VS Code;
 * a file opens full screen ([FileViewer]).
 */


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



/** The Files tab: [on] when the computer allows it. */
@Composable
fun FilesPane(projectId: String, projectName: String, on: Boolean, editOn: Boolean = false) {
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
    opened?.let { (full, e) -> FileViewer(projectId, full, e, editOn) { opened = null } }
}

/** The icon as in VS Code and GitHub (see [FileIcons]). */
@Composable
private fun iconOf(e: FileEntry): Int {
    val context = LocalContext.current
    return remember(e.name, e.dir) { FileIcons.of(context, e.name, e.dir) }
}

@Composable
private fun EntryRow(icon: Int, dir: Boolean, name: String, detail: String, onClick: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Image(androidx.compose.ui.res.painterResource(icon), null, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = c.fg, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(name)))
            Text(detail, color = c.faint, fontSize = 12.sp, maxLines = 1)
        }
        if (dir) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.faint, modifier = Modifier.size(20.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
}

