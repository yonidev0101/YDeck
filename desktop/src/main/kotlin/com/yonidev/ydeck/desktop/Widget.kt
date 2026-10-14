package com.yonidev.ydeck.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
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
        val project = deck.byId(spec.project) ?: deck.projects.firstOrNull()
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
    val color = project?.color ?: 0xFF000000.toInt()
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
            if (deck.projects.size > 1) add(ContextMenuItem(tr("Another project", "פרויקט אחר")) { actions.pick(spec) })
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

/** The card's content for its size: one row, a small square, or the wide header with one or two rows (DeckWidget.kt). */
@Composable
fun WidgetLayout(spec: WidgetSpec, deck: Deck, project: Project?, dark: Boolean, actions: WidgetActions, back: () -> Unit) {
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
    Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { actions.pick(spec) }, verticalAlignment = Alignment.CenterVertically) {
                Logo(p, 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val git = gitOf(p)
                    val claude = claudeOf(p)
                    // Claude Code at work comes first; then git; then the account.
                    if (claude != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        ClaudeDot(claude.mode, dark)
                        Spacer(Modifier.width(5.dp))
                        Text(claudeText(claude), color = if (claude.mode == ClaudeMode.DONE) muted(dark) else claudeColor(claude.mode, dark),
                            style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else if (git != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (git.changes > 0) CHANGED_DOT else CLEAN_DOT))
                        Spacer(Modifier.width(5.dp))
                        Text(git.describe(), color = muted(dark), style = TextStyle(fontFamily = PlexSans, fontSize = 11.sp, lineHeight = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else Text(Chrome.describe(p), color = muted(dark), style = TextStyle(fontFamily = PlexSans, fontSize = 11.sp, lineHeight = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val dir = (LocalPreviewLocal.current ?: Settings.state.collectAsState().value.localOf(p.id)).dir
            if (dir != null) {
                Pulses.state.collectAsState().value[dir.path]?.port?.takeIf { w >= 330.dp }?.let { port ->
                    LivePill(port, dark) { actions.open(p, DeckAction(Catalog.LINK, "http://localhost:$port", "localhost")); back() }
                }
                // The editor and Claude Code; the folder is a right-click away.
                Tools.quick(dir).drop(1).take(if (w >= 300.dp) 2 else 1).forEach { t -> ToolButton(t, p, dark, actions, back) }
                Spacer(Modifier.width(2.dp))
            }
        }
        val rows = if (h >= 190.dp) 2 else 1
        val showLabels = (h - 70.dp) / rows >= 62.dp
        Spacer(Modifier.height(4.dp))
        slots(p, rows * 4).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { slot -> Slot(spec, p, slot, dark, showLabels, 26.dp, actions, back) }
            }
        }
    }
}

@Composable
private fun Small(spec: WidgetSpec, p: Project, dark: Boolean, w: Dp, h: Dp, actions: WidgetActions, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp)).clickable { actions.pick(spec) }, verticalAlignment = Alignment.CenterVertically) {
            GitLogo(p, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(p.name, color = ink(dark), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val showLabels = (h - 50.dp) / 2 >= 66.dp
        slots(p, 4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { slot -> Slot(spec, p, slot, dark, showLabels, 26.dp, actions, back) }
            }
        }
    }
}

/** One row: the project's logo (picks the project), then as many marks as fit, "more" last. */
@Composable
private fun Strip(spec: WidgetSpec, p: Project, dark: Boolean, w: Dp, actions: WidgetActions, back: () -> Unit) {
    val cols = ((w.value - 24f - 44f) / 48f).toInt().coerceIn(2, 8)
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable { actions.pick(spec) }, contentAlignment = Alignment.Center) { GitLogo(p, 30.dp) }
        slots(p, cols).forEach { slot -> Slot(spec, p, slot, dark, false, 24.dp, actions, back) }
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

/** The logo with a dot: Claude Code's state when it's busy here, else amber for uncommitted changes. */
@Composable
private fun GitLogo(p: Project, size: Dp) {
    val git = gitOf(p)
    val claude = claudeOf(p)
    Box(Modifier.size(size + 4.dp)) {
        Box(Modifier.align(Alignment.Center)) { Logo(p, size) }
        if (claude != null || (git != null && git.changes > 0)) {
            Box(Modifier.align(AbsoluteAlignment.TopRight).size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (claude != null) ClaudeDot(claude.mode, false, 7.dp)
                else Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(CHANGED_DOT))
            }
        }
    }
}

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

