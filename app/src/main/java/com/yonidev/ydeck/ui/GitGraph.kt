package com.yonidev.ydeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yonidev.ydeck.data.FilesLine
import com.yonidev.ydeck.link.FileAsk
import com.yonidev.ydeck.link.FileCommit
import com.yonidev.ydeck.link.FileEntry

/*
 * Git as a graph, as VS Code's Git Graph draws it: each branch a coloured line, each commit a dot on it, where a
 * branch split off and where it was merged back; branch and tag names beside the commit. A commit opens to the files
 * it changed, and each file to its change.
 */

internal val LANE_COLORS = listOf(Color(0xFF0085D9), Color(0xFFD9008F), Color(0xFF00C853), Color(0xFFD98500), Color(0xFFA300D9),
    Color(0xFFFF5252), Color(0xFF00BFA5), Color(0xFFE138E8))
private const val MAX_LANES = 7

/** One row's drawing: the commit's lane, lines that pass through, lines that come in from above and go out below. */
internal class GraphRow(
    val lane: Int,
    /** Lanes that go straight through this row. */
    val through: List<Int>,
    /** The line from above reaches the dot. */
    val fromAbove: Boolean,
    /** A line leaves the dot downwards, in its lane. */
    val toBelow: Boolean,
    /** Branches that end here (merged into this commit's line, seen from above): lane → this lane. */
    val joins: List<Int>,
    /** This commit's other parents: this lane → their lanes below. */
    val forks: List<Int>,
    val merge: Boolean,
)

/** The rows of a graph for [commits] (newest first, each parent below its child). */
internal fun graphOf(commits: List<FileCommit>): List<GraphRow> {
    val lanes = ArrayList<String?>()
    val out = ArrayList<GraphRow>(commits.size)
    for (c in commits) {
        var lane = lanes.indexOf(c.hash)
        val fromAbove = lane >= 0
        if (lane < 0) { lane = lanes.indexOf(null).let { if (it < 0) { lanes += null; lanes.size - 1 } else it } }
        val before = lanes.toList()
        // Other lanes waiting for this same commit end here.
        val joins = before.indices.filter { it != lane && before[it] == c.hash }
        joins.forEach { lanes[it] = null }
        lanes[lane] = c.parents.firstOrNull()
        val forks = ArrayList<Int>()
        for (p in c.parents.drop(1)) {
            var at = lanes.indexOf(p)
            if (at < 0) { at = lanes.indexOf(null).let { if (it < 0) { lanes += p; lanes.size - 1 } else { lanes[it] = p; it } } }
            forks += at
        }
        val through = before.indices.filter { it != lane && it !in joins && before[it] != null && lanes.getOrNull(it) == before[it] }
        out += GraphRow(lane, through, fromAbove, lanes[lane] != null, joins, forks, c.parents.size > 1)
        while (lanes.isNotEmpty() && lanes.last() == null) lanes.removeAt(lanes.lastIndex)
    }
    return out
}

private val ROW = 60.dp
private val LANE_W = 14.dp

/** The graph's part of one row. */
@Composable
private fun GraphCell(row: GraphRow, lanes: Int) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shown = lanes.coerceIn(1, MAX_LANES)
    val bg = LocalDeck.current.bg
    Canvas(Modifier.width(LANE_W * shown + 6.dp).fillMaxHeight()) {
        val w = LANE_W.toPx()
        val h = size.height
        val mid = h / 2
        fun x(l: Int) = (l.coerceAtMost(MAX_LANES - 1) * w + w / 2 + 3.dp.toPx()).let { if (rtl) size.width - it else it }
        fun col(l: Int) = LANE_COLORS[l % LANE_COLORS.size]
        val stroke = 2.dp.toPx()
        for (l in row.through) drawLine(col(l), Offset(x(l), 0f), Offset(x(l), h), stroke)
        if (row.fromAbove) drawLine(col(row.lane), Offset(x(row.lane), 0f), Offset(x(row.lane), mid), stroke)
        if (row.toBelow) drawLine(col(row.lane), Offset(x(row.lane), mid), Offset(x(row.lane), h), stroke)
        for (l in row.joins) drawPath(Path().apply {
            moveTo(x(l), 0f); cubicTo(x(l), mid * 0.6f, x(row.lane), mid * 0.4f, x(row.lane), mid)
        }, col(l), style = Stroke(stroke))
        for (l in row.forks) drawPath(Path().apply {
            moveTo(x(row.lane), mid); cubicTo(x(row.lane), mid + mid * 0.6f, x(l), mid + mid * 0.4f, x(l), h)
        }, col(l), style = Stroke(stroke))
        val r = 4.5.dp.toPx()
        if (row.merge) {
            drawCircle(bg, r + 1.dp.toPx(), Offset(x(row.lane), mid))
            drawCircle(col(row.lane), r, Offset(x(row.lane), mid), style = Stroke(2.dp.toPx()))
        } else {
            drawCircle(bg, r + 1.5.dp.toPx(), Offset(x(row.lane), mid))
            drawCircle(col(row.lane), r, Offset(x(row.lane), mid))
        }
    }
}

/** A branch or tag name, as a small label: the branch we're on stands out, a tag is yellow, a remote one ☁. */
private class RefTag(val name: String, val head: Boolean, val tag: Boolean, val remote: Boolean)

private fun refsOf(line: String): List<RefTag> {
    val parts = line.split(", ").map { it.trim() }.filter { it.isNotEmpty() && it != "origin/HEAD" && !it.endsWith("/HEAD") }
    val locals = parts.mapNotNull { p -> when { p.startsWith("HEAD -> ") -> p.removePrefix("HEAD -> "); p.startsWith("tag: ") || p.contains('/') || p == "HEAD" -> null; else -> p } }.toSet()
    return parts.mapNotNull { p ->
        when {
            p.startsWith("HEAD -> ") -> RefTag(p.removePrefix("HEAD -> "), head = true, tag = false, remote = parts.any { it.endsWith("/" + p.removePrefix("HEAD -> ")) })
            p == "HEAD" -> RefTag("HEAD", head = true, tag = false, remote = false)
            p.startsWith("tag: ") -> RefTag(p.removePrefix("tag: "), head = false, tag = true, remote = false)
            p.contains('/') -> if (p.substringAfter('/') in locals) null else RefTag(p, head = false, tag = false, remote = true)
            else -> RefTag(p, head = false, tag = false, remote = parts.any { it.endsWith("/$p") })
        }
    }
}

@Composable
private fun RefChip(r: RefTag, color: Color) {
    val c = LocalDeck.current
    val fg = when { r.tag -> Color(0xFFE5C07B); r.head -> Color.White; else -> color }
    Row(Modifier.clip(RoundedCornerShape(6.dp)).then(if (r.head) Modifier.background(color) else Modifier.border(1.dp, fg.copy(alpha = 0.6f), RoundedCornerShape(6.dp)))
        .padding(horizontal = 6.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((if (r.tag) "🏷 " else "") + r.name + (if (r.remote) " ☁" else ""), color = fg, fontSize = 11.sp, fontFamily = PlexMono, maxLines = 1,
            style = TextStyle(textDirection = TextDirection.Ltr))
    }
}

/** One commit's row: the graph, its message, its branch and tag names, its hash, who and when. */
@Composable
private fun GraphLine(cm: FileCommit, row: GraphRow, lanes: Int, onClick: () -> Unit) {
    val c = LocalDeck.current
    val refs = remember(cm.refs) { refsOf(cm.refs) }
    Row(Modifier.fillMaxWidth().height(ROW).clickable(onClick = onClick).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(start = 10.dp).fillMaxHeight()) { GraphCell(row, lanes) }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                refs.take(3).forEach { RefChip(it, LANE_COLORS[row.lane % LANE_COLORS.size]) }
                Text(cm.subject, color = if (row.merge) c.muted else c.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content), modifier = Modifier.weight(1f, fill = false))
            }
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cm.hash, color = DarkPlus.keyword, fontSize = 11.5.sp, fontFamily = PlexMono)
                Text(cm.author + " · " + agoWords(cm.at), color = c.faint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The graph in a list that scrolls by itself (a file's history, full screen). */
@Composable
fun GitGraphList(commits: List<FileCommit>, onOpen: (FileCommit) -> Unit) {
    val c = LocalDeck.current
    if (commits.isEmpty()) { Text("עוד אין קומיטים כאן.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return }
    val rows = remember(commits) { graphOf(commits) }
    val lanes = remember(rows) { rows.maxOf { maxOf(it.lane, (it.through + it.joins + it.forks).maxOrNull() ?: 0) } + 1 }
    LazyColumn(Modifier.fillMaxSize().background(c.bg)) {
        items(commits.size) { i -> GraphLine(commits[i], rows[i], lanes) { onOpen(commits[i]) } }
    }
}

/** The graph inside a page that scrolls already (the Git tab): the first [show] commits. */
@Composable
fun GitGraphColumn(commits: List<FileCommit>, show: Int, onOpen: (FileCommit) -> Unit) {
    val rows = remember(commits) { graphOf(commits) }
    val lanes = remember(rows) { (rows.take(show).maxOfOrNull { maxOf(it.lane, (it.through + it.joins + it.forks).maxOrNull() ?: 0) } ?: 0) + 1 }
    Column(Modifier.fillMaxWidth()) {
        commits.take(show).forEachIndexed { i, cm -> GraphLine(cm, rows[i], lanes) { onOpen(cm) } }
    }
}

/** A commit, full screen: what it says, and the files it changed; a file opens to its change. */
@Composable
fun CommitView(projectId: String, cm: FileCommit, onClose: () -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<FileEntry>?>(null) }
    var error by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<String?>(null) }
    var diff by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cm.hash) {
        val part = FilesLine.ask(projectId, "", FileAsk.COMMIT, rev = cm.hash)
        when {
            part == null -> error = "המחשב לא ענה. אפשר לנסות שוב."
            part.error.isNotEmpty() -> error = if (part.error == "off") OFF_WORDS else part.error
            else -> files = part.entries
        }
    }
    LaunchedEffect(file) {
        val f = file ?: return@LaunchedEffect
        diff = null
        val got = FilesLine.whole(projectId, f, FileAsk.DIFF, rev = cm.hash)
        diff = when { got == null -> "\u0000המחשב לא ענה."; got.second.isNotEmpty() -> "\u0000" + got.second; else -> String(got.first ?: ByteArray(0), Charsets.UTF_8) }
    }
    Dialog(onDismissRequest = { if (file != null) file = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(DarkPlus.bar).padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).clickable { if (file != null) file = null else onClose() }, contentAlignment = Alignment.Center) {
                    Icon(if (file != null) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close, "חזרה", tint = c.fg, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(file ?: cm.subject, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = if (file != null) TextDirection.Ltr else TextDirection.Content))
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        refsOf(cm.refs).take(3).forEach { RefChip(it, DarkPlus.keyword) }
                        Text(cm.hash + " · " + cm.author + " · " + agoWords(cm.at), color = c.faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val f = file
                val list = files
                when {
                    f != null -> {
                        val d = diff
                        when {
                            d == null -> CircularProgressIndicator(color = DarkPlus.gutter)
                            d.startsWith("\u0000") -> Text(d.drop(1), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                            else -> DiffReader(d, f.substringAfterLast('/'), CodePrefs.size(context).sp, CodePrefs.wrap(context))
                        }
                    }
                    error.isNotEmpty() -> Text(error, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(24.dp))
                    list == null -> CircularProgressIndicator(color = c.muted)
                    list.isEmpty() -> Text("הקומיט הזה לא שינה קבצים בתיקיית הפרויקט.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            val add = list.sumOf { it.size.coerceAtLeast(0) }; val del = list.sumOf { it.at.coerceAtLeast(0) }
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (list.size == 1) "קובץ אחד השתנה" else "${list.size} קבצים השתנו", color = c.muted, fontSize = 13.sp)
                                Text("+$add", color = DarkPlus.addedMark, fontSize = 13.sp, fontFamily = PlexMono)
                                Text("−$del", color = DarkPlus.removedMark, fontSize = 13.sp, fontFamily = PlexMono)
                            }
                        }
                        items(list) { e ->
                            Row(Modifier.fillMaxWidth().clickable { file = e.name }.padding(horizontal = 20.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Image(painterResource(FileIcons.of(context, e.name.substringAfterLast('/'), false)), null, Modifier.size(22.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name.substringAfterLast('/'), color = c.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Ltr))
                                    if (e.name.contains('/')) Text(e.name.substringBeforeLast('/'), color = c.faint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(textDirection = TextDirection.Ltr))
                                }
                                if (e.size < 0) Text("בינארי", color = c.faint, fontSize = 12.sp)
                                else {
                                    Text("+${e.size}", color = DarkPlus.addedMark, fontSize = 12.5.sp, fontFamily = PlexMono)
                                    Text("−${e.at}", color = DarkPlus.removedMark, fontSize = 12.5.sp, fontFamily = PlexMono)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
