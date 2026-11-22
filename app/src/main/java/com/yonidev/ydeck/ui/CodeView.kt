package com.yonidev.ydeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.link.FileCommit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Reading and writing code on the phone, as in VS Code: Dark+ colours, line numbers that stay in place while the code
 * moves sideways, wrap on or off; a file's commits and what each one changed in it, green and red.
 */

private fun codeStyle(size: TextUnit) = TextStyle(fontFamily = PlexMono, fontSize = size, lineHeight = size * 1.55f, color = DarkPlus.fg, textDirection = TextDirection.Ltr)

/** Lines shown with their numbers; long lines move sideways together (one drag for all), or wrap. */
@Composable
private fun Lines(count: Int, longest: Int, size: TextUnit, wrap: Boolean, gutter: @Composable (Int) -> Unit, line: @Composable (Int) -> Unit, rowBg: (Int) -> Color = { Color.Transparent }) {
    val style = codeStyle(size)
    val measurer = rememberTextMeasurer()
    val charW = remember(size) { measurer.measure(AnnotatedString("0"), style).size.width.toFloat() }
    var viewW by remember { mutableIntStateOf(0) }
    var x by remember { mutableFloatStateOf(0f) }
    val maxX = if (wrap) 0f else (longest * charW + 48f - viewW).coerceAtLeast(0f)
    if (x > maxX) x = maxX
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyColumn(Modifier.fillMaxSize().background(DarkPlus.bg).onSizeChanged { viewW = it.width }
            .draggable(rememberDraggableState { d -> x = (x - d).coerceIn(0f, maxX) }, Orientation.Horizontal, enabled = !wrap)) {
            items(count) { i ->
                Row(Modifier.fillMaxWidth().background(rowBg(i))) {
                    gutter(i)
                    Box(Modifier.weight(1f).clipToBounds().padding(start = 10.dp, end = 12.dp)) {
                        Box(if (wrap) Modifier else Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset { IntOffset(-x.roundToInt(), 0) }) { line(i) }
                    }
                }
            }
            item { Box(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun Gutter(text: String, digits: Int, size: TextUnit, color: Color = DarkPlus.gutter) {
    Text(text, style = codeStyle(size).copy(color = color, textAlign = TextAlign.End), maxLines = 1,
        modifier = Modifier.width((digits * size.value * 0.62f + 14).dp).padding(start = 6.dp))
}

/** A file's text in colour, numbered. */
@Composable
fun CodeReader(text: String, name: String, size: TextUnit, wrap: Boolean) {
    val lines by produceState<List<AnnotatedString>?>(null, text, name) {
        value = withContext(Dispatchers.Default) { highlightLines(text, name) }
    }
    val l = lines
    if (l == null) {
        Box(Modifier.fillMaxSize().background(DarkPlus.bg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DarkPlus.gutter) }
        return
    }
    val longest = remember(l) { l.maxOfOrNull { it.length } ?: 0 }
    val digits = l.size.toString().length
    SelectionContainer {
        Lines(l.size, longest, size, wrap,
            gutter = { i -> Gutter((i + 1).toString(), digits, size) },
            line = { i -> Text(l[i], style = codeStyle(size), softWrap = wrap) })
    }
}

/** Colours what's typed, as it's typed (only while the file isn't long: colouring it all on every key would lag). */
private class CodeColors(val name: String) : VisualTransformation {
    private var last: String? = null
    private var made: AnnotatedString? = null
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        val out = if (t.length > 80_000) AnnotatedString(t) else if (t == last) made!! else highlight(t, name).also { last = t; made = it }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/** The file, editable: lines wrap, in colour. */
@Composable
fun CodeEditor(value: TextFieldValue, name: String, size: TextUnit, onChange: (TextFieldValue) -> Unit) {
    val colors = remember(name) { CodeColors(name) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(value, onChange, textStyle = codeStyle(size), cursorBrush = SolidColor(Color(0xFFAEAFAD)), visualTransformation = colors,
            modifier = Modifier.fillMaxSize().background(DarkPlus.bg).padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

/** A file's commits, newest first; a tap opens what it changed. */
@Composable
fun HistoryList(commits: List<FileCommit>, onOpen: (FileCommit) -> Unit) {
    val c = LocalDeck.current
    if (commits.isEmpty()) {
        Text("עוד אין קומיטים לקובץ הזה.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(commits) { i, cm ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(cm) }.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // A line of dots, as a graph.
                Column(Modifier.width(12.dp).padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(10.dp).height(10.dp).background(if (i == 0) DarkPlus.keyword else c.muted, androidx.compose.foundation.shape.CircleShape))
                }
                Column(Modifier.weight(1f)) {
                    Text(cm.subject, color = c.fg, fontSize = 14.5.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content))
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cm.hash, color = DarkPlus.keyword, fontSize = 12.sp, fontFamily = PlexMono)
                        Text(cm.author + " · " + agoWords(cm.at), color = c.faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(1.dp).background(c.line))
        }
    }
}

fun agoWords(at: Long): String {
    if (at <= 0) return ""
    val m = (System.currentTimeMillis() - at) / 60_000
    return when {
        m < 1 -> "עכשיו"
        m < 60 -> "לפני $m דק׳"
        m < 24 * 60 -> "לפני ${m / 60} שע׳"
        m < 2 * 24 * 60 -> "אתמול"
        m < 30 * 24 * 60 -> "לפני ${m / (24 * 60)} ימים"
        else -> java.text.SimpleDateFormat("d.M.yy", java.util.Locale.getDefault()).format(java.util.Date(at))
    }
}

/** One line of a diff: '+', '-', ' ' or '@' (a hunk's head), with its numbers before and after. */
private class DiffLine(val kind: Char, val text: String, val old: Int?, val new: Int?)

private fun parseDiff(diff: String): List<DiffLine> {
    val out = ArrayList<DiffLine>()
    var o = 0; var n = 0
    var inHunk = false
    for (l in diff.split('\n')) {
        val h = Regex("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@(.*)").find(l)
        when {
            h != null -> { o = h.groupValues[1].toInt(); n = h.groupValues[2].toInt(); inHunk = true; out += DiffLine('@', l, null, null) }
            !inHunk -> {}
            l.startsWith("+") -> { out += DiffLine('+', l.drop(1), null, n); n++ }
            l.startsWith("-") -> { out += DiffLine('-', l.drop(1), o, null); o++ }
            l.startsWith("\\") -> {}
            l.startsWith("diff --git") -> inHunk = false
            else -> { out += DiffLine(' ', l.drop(1), o, n); o++; n++ }
        }
    }
    return out
}

/** What a commit changed in the file: added lines green, removed red, both numbers in the margin. */
@Composable
fun DiffReader(diff: String, name: String, size: TextUnit, wrap: Boolean) {
    val lines = remember(diff) { parseDiff(diff) }
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize().background(DarkPlus.bg)) { Text("אין כאן שינוי בתוכן (אולי רק שינוי שם או קובץ בינארי).", color = DarkPlus.gutter, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
        return
    }
    val colored = remember(lines, name) { highlightLines(lines.joinToString("\n") { if (it.kind == '@') "" else it.text }, name) }
    val digits = (lines.maxOfOrNull { maxOf(it.old ?: 0, it.new ?: 0) } ?: 0).toString().length
    val longest = remember(lines) { lines.maxOfOrNull { it.text.length } ?: 0 }
    SelectionContainer {
        Lines(lines.size, longest + 2, size, wrap,
            rowBg = { i -> when (lines[i].kind) { '+' -> DarkPlus.added; '-' -> DarkPlus.removed; '@' -> DarkPlus.hunk.copy(alpha = 0.35f); else -> Color.Transparent } },
            gutter = { i ->
                val d = lines[i]
                Gutter(d.old?.toString() ?: "", digits, size)
                Gutter(d.new?.toString() ?: "", digits, size)
                Text(when (d.kind) { '+' -> "+"; '-' -> "−"; else -> " " }, style = codeStyle(size).copy(color = when (d.kind) { '+' -> DarkPlus.addedMark; '-' -> DarkPlus.removedMark; else -> DarkPlus.gutter }),
                    modifier = Modifier.width(16.dp).padding(start = 6.dp))
            },
            line = { i ->
                val d = lines[i]
                if (d.kind == '@') Text(d.text, style = codeStyle(size).copy(color = DarkPlus.gutter), softWrap = wrap, maxLines = if (wrap) Int.MAX_VALUE else 1)
                else Text(colored.getOrElse(i) { AnnotatedString(d.text) }, style = codeStyle(size), softWrap = wrap)
            })
    }
}
