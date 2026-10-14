package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

/** Ctrl+Alt+Space from anywhere in Windows: a system-wide hotkey on its own thread's message loop. */
object Hotkey {
    const val LABEL = "Ctrl+Alt+Space"
    private val _presses = MutableStateFlow(0)
    val presses: StateFlow<Int> = _presses
    @Volatile var registered = false; private set

    fun start() {
        Thread({
            val user32 = User32.INSTANCE
            registered = runCatching { user32.RegisterHotKey(null, 1, 0x0002 /* CONTROL */ or 0x0001 /* ALT */ or 0x4000 /* NOREPEAT */, 0x20 /* SPACE */) }.getOrDefault(false)
            if (!registered) return@Thread
            val msg = WinUser.MSG()
            while (user32.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == 0x0312 /* WM_HOTKEY */) _presses.value++
            }
        }, "ydeck-hotkey").apply { isDaemon = true; start() }
    }
}

/** Something the search finds: a link, a tool on the folder, a command, a pinned file, a value to copy, the editor. */
sealed class Target {
    data class Link(val action: DeckAction) : Target()
    data class Open(val tool: Tool) : Target()
    data class Run(val command: Command) : Target()
    data class Pinned(val path: String) : Target()
    data class Copy(val item: CopyItem) : Target()
    data object Edit : Target()
}

data class Entry(val project: Project, val target: Target, val title: String, val hint: String, val words: String)

object Search {
    fun entries(deck: Deck, settings: Settings.State): List<Entry> = deck.projects.flatMap { p ->
        val local = settings.localOf(p.id)
        val dir = local.dir
        buildList {
            p.actions.forEach { a -> add(Entry(p, Target.Link(a), a.label, Links.display(a.url), "${a.service.id} ${a.service.short} ${a.service.label}")) }
            if (dir != null) Pulses.state.value[dir.path]?.port?.let { port ->
                add(Entry(p, Target.Link(DeckAction(Catalog.LINK, "http://localhost:$port", "localhost:$port")), "localhost:$port", tr("The dev server running now", "שרת הפיתוח שרץ עכשיו"), "localhost dev server live שרת"))
            }
            if (dir != null) Tools.forFolder(dir).forEach { t -> add(Entry(p, Target.Open(t), t.label, dir.path, "${t.id} ${t.label} folder code")) }
            if (dir != null) local.commands.forEach { c -> add(Entry(p, Target.Run(c), c.label.ifBlank { c.line }, c.line, "run ${c.line} פקודה")) }
            local.files.forEach { f -> add(Entry(p, Target.Pinned(f), File(f).name, f, "file קובץ")) }
            p.copies.forEach { c -> add(Entry(p, Target.Copy(c), tr("Copy ${c.label.ifBlank { "value" }}", "העתקת \u200F${c.label.ifBlank { "ערך" }}\u200F"), c.value, "copy ${c.label} העתקה")) }
            add(Entry(p, Target.Edit, tr("Open ${p.name}", "פתיחת ${p.name}"), tr("Overview, links, files and commands", "סקירה, קישורים, קבצים ופקודות"), "open project edit settings פתיחה עריכה"))
        }
    }

    /**
     * Every word typed must appear in the project's name or the item ("nova fire" → Firebase of Nova). A word at the
     * start of a name counts more, a whole name more still.
     */
    fun match(query: String, all: List<Entry>): List<Entry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return all.filter { it.target is Target.Link }.distinctBy { it.project.id }.take(12)
        return all.mapIndexedNotNull { i, e ->
            val project = e.project.name.lowercase()
            val title = e.title.lowercase()
            val extra = e.words.lowercase()
            var score = 0
            for (w in words) {
                score += when {
                    title == w || project == w -> 8
                    title.startsWith(w) || project.startsWith(w) -> 6
                    Regex("(^|[\\s·.-])" + Regex.escape(w)).containsMatchIn("$title $project") -> 4
                    title.contains(w) || project.contains(w) -> 2
                    extra.contains(w) -> 1
                    else -> return@mapIndexedNotNull null
                }
            }
            Triple(e, score, i)
        }.sortedWith(compareByDescending<Triple<Entry, Int, Int>> { it.second }.thenBy { it.third }).map { it.first }.take(40)
    }
}

/** The search window: a field and the results, high in the middle of the screen, gone on Esc or a click elsewhere. */
@Composable
fun ApplicationScope.PaletteWindow(deck: Deck, settings: Settings.State, dark: Boolean, onRun: (Entry) -> Unit, onClose: () -> Unit) {
    val w = 640f
    val h = 470f
    val screen = Toolkit.getDefaultToolkit().screenSize
    val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
    val state = rememberWindowState(position = WindowPosition(((screen.width / scale - w) / 2).dp, (screen.height / scale * 0.16f).dp), size = DpSize(w.dp, h.dp))
    Window(onCloseRequest = onClose, state = state, undecorated = true, transparent = true, resizable = false, alwaysOnTop = true, title = "YDeck",
        icon = androidx.compose.ui.res.painterResource("icons/app.png")) {
        DisposableEffect(Unit) {
            val l = object : WindowAdapter() { override fun windowLostFocus(e: WindowEvent?) = onClose() }
            window.addWindowFocusListener(l)
            window.toFront(); window.requestFocus()
            runCatching { User32.INSTANCE.SetForegroundWindow(WinDef.HWND(Native.getComponentPointer(window))) }
            onDispose { window.removeWindowFocusListener(l) }
        }
        DeckTheme(dark) { PaletteCard(deck, settings, dark, onRun, onClose) }
    }
}

/** The search itself: the field and the results (a card, so it also draws without a window). */
@Composable
fun PaletteCard(deck: Deck, settings: Settings.State, dark: Boolean, onRun: (Entry) -> Unit, onClose: () -> Unit, initialQuery: String = "") {
    val c = LocalDeck.current
    val all = remember(deck, settings) { Search.entries(deck, settings) }
    var query by remember { mutableStateOf(initialQuery) }
    val results = remember(query, all) { Search.match(query, all) }
    var selected by remember(results) { mutableStateOf(0) }
    val list = rememberLazyListState()
    LaunchedEffect(selected) { if (results.isNotEmpty()) list.animateScrollToItem((selected - 3).coerceAtLeast(0)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.padding(14.dp).fillMaxWidth().shadow(22.dp, shape).clip(shape).background(c.surface).border(1.dp, c.line, shape)
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.Escape -> { onClose(); true }
                Key.DirectionDown -> { if (results.isNotEmpty()) selected = (selected + 1) % results.size; true }
                Key.DirectionUp -> { if (results.isNotEmpty()) selected = (selected - 1 + results.size) % results.size; true }
                Key.Enter, Key.NumPadEnter -> { results.getOrNull(selected)?.let(onRun); true }
                else -> false
            }
        }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                val style = TextStyle(color = c.fg, fontSize = 21.sp, fontFamily = PlexSans, textDirection = TextDirection.ContentOrRtl, textAlign = androidx.compose.ui.text.style.TextAlign.Right)
                if (query.isEmpty()) Text(tr("A project and what to open, like nova firebase", "פרויקט ומה לפתוח, למשל \u200Enova firebase\u200F"), color = c.faint, style = style.copy(color = c.faint), maxLines = 1, modifier = Modifier.fillMaxWidth())
                BasicTextField(query, { query = it }, singleLine = true, textStyle = style, cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            Spacer(Modifier.width(16.dp))
            Text(Hotkey.LABEL, color = c.faint, fontSize = 12.sp, fontFamily = PlexMono)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        if (results.isEmpty()) {
            Text(tr("Nothing found. Search by a project, a service, a tool or a command.", "לא נמצא. אפשר לחפש לפי שם פרויקט, שירות, כלי או פקודה."), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(vertical = 6.dp), state = list) {
                itemsIndexed(results) { i, e -> ResultRow(e, i == selected, dark) { onRun(e) } }
            }
        }
    }
}

@Composable
private fun ResultRow(e: Entry, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) c.accentTint else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2), contentAlignment = Alignment.Center) { TargetMark(e.target, 20.dp, dark) }
        Column(Modifier.weight(1f)) {
            Text(e.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.hint, color = c.muted, fontSize = 12.sp, fontFamily = if (e.target is Target.Edit) PlexSans else PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.ContentOrLtr))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.project.name, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Logo(e.project, 22.dp)
        }
    }
}

@Composable
fun TargetMark(t: Target, size: androidx.compose.ui.unit.Dp, dark: Boolean) {
    val c = LocalDeck.current
    when (t) {
        is Target.Link -> ServiceMark(t.action.service, t.action.faviconHost, size, dark)
        is Target.Open -> ToolMark(t.tool, size, dark)
        is Target.Run -> Text("▶", color = c.accentText, fontSize = (size.value * 0.7f).sp)
        is Target.Pinned -> FileMark(t.path, size)
        is Target.Copy -> Text("⧉", color = c.fg, fontSize = (size.value * 0.85f).sp)
        Target.Edit -> Text("✎", color = c.fg, fontSize = (size.value * 0.8f).sp)
    }
}

/** Does what an entry stands for. Editing goes back to the app (it needs the main window). */
fun runEntry(e: Entry, settings: Settings.State, edit: (Project) -> Unit) {
    val dir = settings.localOf(e.project.id).dir
    when (val t = e.target) {
        is Target.Link -> Chrome.open(e.project, t.action)
        is Target.Open -> dir?.let { Tools.launch(t.tool, it, e.project.name) }
        is Target.Run -> dir?.let { Tools.run(t.command, it) }
        is Target.Pinned -> Tools.openFile(t.path)
        is Target.Copy -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(t.item.value), null)
        Target.Edit -> edit(e.project)
    }
}
