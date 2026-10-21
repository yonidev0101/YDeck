package com.yonidev.ydeck.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.io.File
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable

/** The widget whose "project" is every project: Now on the desktop. */
const val NOW_WIDGET = "*now"

/** The shadow's room around the card inside the transparent window. */
private val EDGE = 10.dp

/** What a widget can ask the app for. */
class WidgetActions(
    val open: (Project, DeckAction) -> Unit,
    val more: (WidgetSpec, Project) -> Unit,
    val pick: (WidgetSpec) -> Unit,
    val edit: (Project) -> Unit,
    val remove: (WidgetSpec) -> Unit,
    val resize: (WidgetSpec, Float, Float) -> Unit,
    val tool: (Project, Tool) -> Unit = { _, _ -> },
    val run: (Project, Command) -> Unit = { _, _ -> },
    /** Files, a folder or a link dropped on the widget. */
    val drop: (WidgetSpec, Project, Dropped) -> Unit = { _, _, _ -> },
)

/** What was dragged onto a widget or the main window. */
sealed class Dropped {
    data class Files(val files: List<File>) : Dropped()
    data class Text(val text: String) : Dropped()
}

/** Accepts files, folders and links dragged from Explorer or the browser. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun Modifier.dropTarget(onDrop: (Dropped) -> Unit): Modifier {
    val target = remember(onDrop) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val t = event.awtTransferable
                val dropped = runCatching {
                    when {
                        t.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> Dropped.Files((t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>())
                        t.isDataFlavorSupported(DataFlavor.stringFlavor) -> Dropped.Text(t.getTransferData(DataFlavor.stringFlavor) as String)
                        else -> null
                    }
                }.getOrNull() ?: return false
                onDrop(dropped)
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

/** One widget: a transparent, frameless window that lives on the desktop (see [Win32.pinToDesktop]). */
@OptIn(FlowPreview::class)
@Composable
fun ApplicationScope.WidgetWindow(spec: WidgetSpec, deck: Deck, dark: Boolean, actions: WidgetActions) {
    val state = rememberWindowState(position = WindowPosition(spec.x.dp, spec.y.dp), size = DpSize(spec.w.dp, spec.h.dp))
    Window(
        onCloseRequest = { actions.remove(spec) }, state = state, title = "YDeck",
        undecorated = true, transparent = true, resizable = false, alwaysOnTop = false,
        icon = androidx.compose.ui.res.painterResource("icons/app.png"),
    ) {
        LaunchedEffect(Unit) { Win32.pinToDesktop(window) }
        // Size changes from the menu or the grip arrive through [spec]; position and size are remembered.
        LaunchedEffect(spec.w, spec.h) { state.size = DpSize(spec.w.dp, spec.h.dp) }
        LaunchedEffect(Unit) {
            snapshotFlow { state.position to state.size }.debounce(400).collect { (pos, size) ->
                val p = pos as? WindowPosition.Absolute ?: return@collect
                Settings.setWidget(spec.copy(x = p.x.value, y = p.y.value, w = size.width.value, h = size.height.value))
            }
        }
        val project = if (spec.project == NOW_WIDGET) null else deck.byId(spec.project) ?: deck.projects.firstOrNull()
        DeckTheme(dark) {
            WidgetFrame(spec, deck, project, dark, actions) { Win32.sendToBack(window) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowScope.WidgetFrame(spec: WidgetSpec, deck: Deck, project: Project?, dark: Boolean, actions: WidgetActions, back: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    // Now's widget is in YDeck's own ink, not a project's colour.
    val color = project?.color ?: (if (dark) 0xFF0B0B0C else 0xFFF4F4F6).toInt()
    val settings by Settings.state.collectAsState()
    val local = settings.localOf(project?.id)
    ContextMenuArea(items = {
        buildList {
            val dir = local.dir
            if (project != null && dir != null) {
                Tools.forFolder(dir).forEach { t -> add(ContextMenuItem(if (t.kind == ToolKind.FOLDER) tr("Open the folder", "פתיחת התיקייה") else tr("Open in ${t.label}", "פתיחה ב־${t.label}")) { actions.tool(project, t) }) }
                local.commands.forEach { c -> add(ContextMenuItem(tr("Run: ${c.label.ifBlank { c.line }}", "הרצה: ${c.label.ifBlank { c.line }}")) { actions.run(project, c) }) }
            }
            if (project != null) add(ContextMenuItem(tr("Open ${project.name}", "פתיחת ${project.name}")) { actions.edit(project) })
            if (deck.projects.size > 1 || spec.project == NOW_WIDGET) add(ContextMenuItem(tr("Another project", "פרויקט אחר")) { actions.pick(spec) })
            add(ContextMenuItem(tr("Size: wide", "גודל: רחב")) { actions.resize(spec, 360f, 214f) })
            add(ContextMenuItem(tr("Size: small square", "גודל: ריבוע קטן")) { actions.resize(spec, 214f, 214f) })
            add(ContextMenuItem(tr("Size: one row", "גודל: שורה אחת")) { actions.resize(spec, 380f, 84f) })
            add(ContextMenuItem(tr("Remove the widget", "הסרת הווידג'ט")) { actions.remove(spec) })
        }
    }) {
        Box(Modifier.fillMaxSize().padding(EDGE).hoverable(hover).then(if (project != null) Modifier.dropTarget { actions.drop(spec, project, it) } else Modifier)) {
            val shape = RoundedCornerShape(26.dp)
            WindowDraggableArea(
                // No drop shadow: on translucent glass it shows through as a lighter patch in the middle.
                Modifier.fillMaxSize().clip(shape)
                    .background(glass(color, dark))
                    .border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f), shape),
            ) {
                WidgetLayout(spec, deck, project, dark, actions, back)
            }
            if (hovered) ResizeGrip(Modifier.align(AbsoluteAlignment.BottomRight)) { dx, dy ->
                actions.resize(spec, (spec.w + dx).coerceIn(150f, 640f), (spec.h + dy).coerceIn(74f, 420f))
            }
        }
    }
}

/**
 * The card's content for its size: one row, a small square, or the wide card. Each leads with where the project stands
 * (as Now says it), its links underneath. The all-projects widget ([NOW_WIDGET]) is Now itself, on the desktop.
 */
@Composable
fun WidgetLayout(spec: WidgetSpec, deck: Deck, project: Project?, dark: Boolean, actions: WidgetActions, back: () -> Unit) {
    if (spec.project == NOW_WIDGET) { NowWidget(deck, dark, actions, back); return }
    if (project == null) { Empty(dark); return }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        when {
            h < 96.dp -> Strip(spec, project, dark, w, actions, back)
            w < 260.dp -> Small(spec, project, dark, w, h, actions, back)
            else -> Wide(spec, deck, project, dark, w, h, actions, back)
        }
    }
}

/** A small corner handle; dragging it resizes the widget (the layout follows, as on the phone). */
@Composable
private fun ResizeGrip(modifier: Modifier, onDrag: (Float, Float) -> Unit) {
    Box(
        modifier.size(18.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
            .pointerInput(Unit) { detectDragGestures { change, d -> change.consume(); onDrag(d.x / density, d.y / density) } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x553C3C43)))
    }
}

@Composable
private fun Empty(dark: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(tr("Open YDeck to add a project", "פתחו את YDeck כדי להוסיף פרויקט"), color = if (dark) Color(0xFFA1A1A8) else Color(0xFF55555B), fontSize = 13.sp, fontFamily = PlexSans)
    }
}

private fun ink(dark: Boolean) = if (dark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F)
private fun muted(dark: Boolean) = if (dark) Color(0xFFA1A1A8) else Color(0xFF55555B)
private fun label(dark: Boolean) = if (dark) Color(0xFFD1D1D6) else Color(0xFF3C3C43)

/** Slots: the first actions, then "more", padded with empties (the phone's slots()). */
private fun slots(p: Project, count: Int): List<Int?> {
    val shown = p.actions.indices.take(count - 1)
    return (shown + MORE).let { it + List(count - it.size) { null } }
}
private const val MORE = -1

@Composable
private fun Wide(spec: WidgetSpec, deck: Deck, p: Project, dark: Boolean, w: Dp, h: Dp, actions: WidgetActions, back: () -> Unit) {
    val local = LocalPreviewLocal.current ?: Settings.state.collectAsState().value.localOf(p.id)
    val stand = standOf(p, local)
    Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { actions.pick(spec) }, verticalAlignment = Alignment.CenterVertically) {
                Logo(p, 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StandLine(stand, dark)
                }
            }
            val dir = local.dir
            if (dir != null) {
                Pulses.state.collectAsState().value[dir.path]?.port?.takeIf { w >= 330.dp }?.let { port ->
                    LivePill(port, dark) { actions.open(p, DeckAction(Catalog.LINK, "http://localhost:$port", "localhost")); back() }
                }
                // The editor and Claude Code; the folder is a right-click away.
                Tools.quick(dir).drop(1).take(if (w >= 300.dp) 2 else 1).forEach { t -> ToolButton(t, p, dark, actions, back) }
                Spacer(Modifier.width(2.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        // What goes with the stand, as much as the height allows; a click opens the project.
        Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).clickable { actions.edit(p); back() }.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            StandBody(stand, dark, lines = ((h.value - 14f - 38f - 8f - 52f) / 20f).toInt().coerceIn(1, 5))
        }
        val marks = ((w.value - 28f) / 52f).toInt().coerceIn(3, 7)
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            slots(p, marks).forEach { slot -> Slot(spec, p, slot, dark, false, 22.dp, actions, back) }
        }
    }
}

/**
 * The stand under the name: its dot and its words, in Claude's colour while it waits or works. Over a list of what's
 * open it says how much ([Stand.headline]); alone ([alone]), what it is.
 */
@Composable
private fun StandLine(stand: Stand, dark: Boolean, alone: Boolean = false) {
    val lit = stand.mode == StandMode.WAITING || stand.mode == StandMode.WORKING
    Row(verticalAlignment = Alignment.CenterVertically) {
        StandDot(stand, dark, 6.dp)
        Spacer(Modifier.width(5.dp))
        val text = if (alone) stand.title else stand.headline
        Text(text, color = if (lit) standColor(stand, dark) else muted(dark),
            style = TextStyle(textDirection = dirOf(text), fontFamily = PlexSans, fontWeight = if (lit) FontWeight.Medium else FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * What goes with the stand, in [lines] at most: Claude's question or prompt, or what's open (a line each); then the
 * next step, then what was done last time, as room allows.
 */
@Composable
private fun StandBody(stand: Stand, dark: Boolean, lines: Int) {
    val body = TextStyle(fontFamily = PlexSans, fontSize = 12.5.sp, lineHeight = 17.sp, textDirection = androidx.compose.ui.text.style.TextDirection.Content)
    var left = lines
    @Composable fun line(text: String, color: Color, max: Int = 1) {
        if (left <= 0 || text.isBlank()) return
        Text(text, color = color, style = body.copy(textDirection = dirOf(text)), maxLines = max.coerceAtMost(left), overflow = TextOverflow.Ellipsis)
        left -= max.coerceAtMost(left)
    }
    when (stand.mode) {
        StandMode.WAITING, StandMode.WORKING -> line(stand.detail, ink(dark), max = 2)
        StandMode.OPEN -> stand.loops.take((left - 1).coerceAtLeast(1)).forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (l.kind == LoopKind.CONFLICTS) Color(0xFFEF4444) else if (l.kind == LoopKind.SERVER) LIVE_DOT else CHANGED_DOT, 5.dp)
                Spacer(Modifier.width(7.dp))
                Text(l.title, color = ink(dark), style = body.copy(textDirection = dirOf(l.title)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            left--
        }
        else -> {}
    }
    if (stand.next.isNotBlank()) line(tr("Next: ", "הבא: ") + stand.next, if (stand.mode == StandMode.SAVED || stand.mode == StandMode.UNKNOWN) ink(dark) else muted(dark))
    stand.handoff?.let { line(tr("Last time: ", "בפעם הקודמת: ") + it.done, muted(dark)) }
}

@Composable
private fun Small(spec: WidgetSpec, p: Project, dark: Boolean, w: Dp, h: Dp, actions: WidgetActions, back: () -> Unit) {
    val local = LocalPreviewLocal.current ?: Settings.state.collectAsState().value.localOf(p.id)
    val stand = standOf(p, local)
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp)).clickable { actions.pick(spec) }, verticalAlignment = Alignment.CenterVertically) {
            Logo(p, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(p.name, color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).clickable { actions.edit(p); back() }.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StandLine(stand, dark)
            StandBody(stand, dark, lines = if (h >= 200.dp) 3 else 2)
        }
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            slots(p, ((w.value - 24f) / 50f).toInt().coerceIn(2, 4)).forEach { slot -> Slot(spec, p, slot, dark, false, 22.dp, actions, back) }
        }
    }
}

/** One row: the project's logo (picks the project), its name and where it stands, then as many marks as fit. */
@Composable
private fun Strip(spec: WidgetSpec, p: Project, dark: Boolean, w: Dp, actions: WidgetActions, back: () -> Unit) {
    val local = LocalPreviewLocal.current ?: Settings.state.collectAsState().value.localOf(p.id)
    val stand = standOf(p, local)
    val cols = ((w.value - 24f - 44f - 150f) / 46f).toInt().coerceIn(1, 5)
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable { actions.pick(spec) }, contentAlignment = Alignment.Center) { Logo(p, 30.dp) }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { actions.edit(p); back() }) {
            Text(p.name, color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StandLine(stand, dark, alone = true)
        }
        Row(Modifier.width((cols * 46).dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            slots(p, cols).forEach { slot -> Slot(spec, p, slot, dark, false, 22.dp, actions, back) }
        }
    }
}

/** The widget that keeps every project: Now on the desktop. The ones that need you first, one line each; the rest, counted. */
@Composable
private fun NowWidget(deck: Deck, dark: Boolean, actions: WidgetActions, back: () -> Unit) {
    val settings by Settings.state.collectAsState()
    val rows = deck.projects.filter { settings.localOf(it.id).folder != null }.map { it to standOf(it, settings.localOf(it.id)) }.sortedBy { it.second.rank }
    val (busy, calm) = rows.partition { it.second.mode == StandMode.WAITING || it.second.mode == StandMode.WORKING || it.second.mode == StandMode.OPEN }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val room = ((maxHeight.value - 20f - 34f - 26f) / 44f).toInt().coerceAtLeast(1).let { if (rows.size <= it + 1 && rows.size * 44f <= maxHeight.value - 54f) rows.size else it }
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource("icons/app.png"), null, Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(8.dp))
                Text(tr("Now", "עכשיו"), color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp), modifier = Modifier.weight(1f))
                if (busy.isNotEmpty()) Text(tr("${busy.size} need a look", if (busy.size == 1) "פרויקט אחד דורש מבט" else "${busy.size} פרויקטים דורשים מבט"),
                    color = muted(dark), style = TextStyle(fontFamily = PlexSans, fontSize = 11.sp))
            }
            Spacer(Modifier.height(4.dp))
            if (rows.isEmpty() || busy.isEmpty() && rows.size > room) Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Dot(CLEAN_DOT, 10.dp)
                Spacer(Modifier.height(10.dp))
                Text(tr("Nothing open", "אין שום דבר פתוח"), color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp))
                Text(tr("Every project is saved", "כל הפרויקטים שמורים"), color = muted(dark), style = TextStyle(fontFamily = PlexSans, fontSize = 12.sp))
            } else Column(Modifier.fillMaxWidth().weight(1f)) {
                rows.take(room).forEach { (p, stand) ->
                    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { actions.edit(p); back() }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Logo(p, 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StandLine(stand, dark, alone = true)
                        }
                    }
                }
            }
            val hidden = rows.drop(room)
            if (hidden.isNotEmpty()) Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val waiting = hidden.count { it.second.mode != StandMode.SAVED && it.second.mode != StandMode.UNKNOWN }
                Dot(if (waiting > 0) CHANGED_DOT else CLEAN_DOT, 6.dp)
                Spacer(Modifier.width(7.dp))
                Text(if (waiting > 0) tr("And ${hidden.size} more, $waiting need a look", "ועוד ${hidden.size}, מהם $waiting דורשים מבט")
                    else tr("And ${hidden.size} more, all saved", if (hidden.size == 1) "ועוד פרויקט אחד, הכול שמור" else "ועוד ${hidden.size} פרויקטים, הכול שמור"),
                    color = muted(dark), style = TextStyle(fontFamily = PlexSans, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Slot(spec: WidgetSpec, p: Project, slot: Int?, dark: Boolean, labels: Boolean, icon: Dp, actions: WidgetActions, back: () -> Unit) {
    val cell = Modifier.weight(1f).fillMaxHeight()
    when (slot) {
        null -> Spacer(cell)
        MORE -> Mark(cell, tr("More", "עוד"), labels, dark, onClick = { actions.more(spec, p) }) { Dots(icon, label(dark)) }
        else -> {
            val a = p.actions[slot]
            TooltipArea(
                tooltip = { Tip("${a.label} · ${Chrome.describe(p)}", dark) },
                modifier = cell, delayMillis = 500, tooltipPlacement = TooltipPlacement.CursorPoint(offset = androidx.compose.ui.unit.DpOffset(0.dp, 18.dp)),
            ) {
                Mark(Modifier.fillMaxSize(), a.tileLabel, labels, dark, onClick = { actions.open(p, a); back() }) {
                    ServiceMark(a.service, a.faviconHost, icon, dark, tint = if (slot == 0) ink(dark) else null)
                }
            }
        }
    }
}

/** The editor's preview shows the folder being edited, before it's saved. */
val LocalPreviewLocal = androidx.compose.runtime.compositionLocalOf<Local?> { null }


@Composable
private fun gitOf(p: Project): GitInfo? {
    val folder = (LocalPreviewLocal.current ?: Settings.state.collectAsState().value.localOf(p.id)).folder ?: return null
    return Git.state.collectAsState().value[folder]
}

@Composable
private fun claudeOf(p: Project): ClaudeSession? = claudeIn((LocalPreviewLocal.current ?: Settings.state.collectAsState().value.localOf(p.id)).folder)

/** A local tool on the widget's header: the folder, the editor, Claude Code. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(t: Tool, p: Project, dark: Boolean, actions: WidgetActions, back: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val claudeAt = if (t.kind == ToolKind.AGENT) (LocalPreviewLocal.current ?: Settings.state.collectAsState().value.localOf(p.id)).folder?.let { Pulses.state.collectAsState().value[it]?.claudeAt } else null
    val tip = when {
        t.kind == ToolKind.FOLDER -> tr("Open the folder", "פתיחת התיקייה")
        claudeAt != null -> tr("Continue with Claude · ${ago(claudeAt)}", "המשך עם Claude · ${ago(claudeAt)}")
        else -> t.label
    }
    TooltipArea(tooltip = { Tip(tip, dark) }, delayMillis = 400,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = androidx.compose.ui.unit.DpOffset(0.dp, 18.dp))) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).hoverable(hover)
            .background(if (hovered) (if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)) else Color.Transparent)
            .clickable { actions.tool(p, t); back() }, contentAlignment = Alignment.Center) {
            ToolMark(t, 19.dp, dark)
        }
    }
}

@Composable
private fun Tip(text: String, dark: Boolean) {
    Text(
        text, color = if (dark) Color(0xFFF4F4F5) else Color(0xFFF4F4F5), fontSize = 12.sp, fontFamily = PlexSans,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xEE0A0A0B)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun Mark(modifier: Modifier, text: String, labels: Boolean, dark: Boolean, onClick: () -> Unit, mark: @Composable () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(14.dp)).hoverable(hover)
        .background(if (hovered) (if (dark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)) else Color.Transparent)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            mark()
            if (labels) {
                Spacer(Modifier.height(5.dp))
                Text(text, color = label(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The widget's content without a window, for the editor's live preview. */
@Composable
fun WidgetPreview(p: Project, dark: Boolean, local: Local = Local()) {
    val shape = RoundedCornerShape(26.dp)
    val noop = WidgetActions({ _, _ -> }, { _, _ -> }, {}, {}, {}, { _, _, _ -> })
    val spec = WidgetSpec("preview", p.id, 0f, 0f, 360f, 214f)
    Box(Modifier.fillMaxWidth().height(214.dp).clip(shape).background(glass(p.color, dark))
        .border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f), shape)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPreviewLocal provides local) {
            Wide(spec, Deck(listOf(p)), p, dark, 360.dp, 214.dp, noop) {}
        }
    }
}

