package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/** A panel opened from a widget: its "more" sheet, or the project picker. */
sealed class Panel {
    companion object {
        const val WIDTH = 360f
        const val MORE_HEIGHT = 500f
        const val GIT_HEIGHT = 560f
    }
    abstract val spec: WidgetSpec
    data class More(override val spec: WidgetSpec, val projectId: String) : Panel()
    data class Pick(override val spec: WidgetSpec) : Panel()
    data class Git(override val spec: WidgetSpec, val projectId: String) : Panel()
}

/** A floating card next to the widget it came from; it closes when it loses focus, like the phone's bottom sheet. */
@Composable
fun ApplicationScope.PanelWindow(panel: Panel, deck: Deck, dark: Boolean, onClose: () -> Unit, onPick: (WidgetSpec, Project) -> Unit, onEdit: (Project) -> Unit, onGit: (Project) -> Unit) {
    val w = Panel.WIDTH
    val h = when (panel) {
        is Panel.Pick -> (96f + 64f * deck.projects.size).coerceAtMost(520f)
        is Panel.Git -> Panel.GIT_HEIGHT
        else -> Panel.MORE_HEIGHT
    }
    val screen = Toolkit.getDefaultToolkit().screenSize
    val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
    val sw = screen.width / scale
    val sh = screen.height / scale
    val s = panel.spec
    // Beside the widget, on whichever side has room; never off the screen.
    val x = if (s.x - w - 4 >= 0) s.x - w - 4 + 10 else (s.x + s.w - 10).coerceAtMost(sw - w)
    val y = s.y.coerceIn(0f, (sh - h - 48).coerceAtLeast(0f))
    val state = rememberWindowState(position = WindowPosition(x.dp, y.dp), size = DpSize(w.dp, h.dp))
    Window(onCloseRequest = onClose, state = state, undecorated = true, transparent = true, resizable = false, alwaysOnTop = true, title = "YDeck",
        icon = androidx.compose.ui.res.painterResource("icons/app.png")) {
        DisposableEffect(Unit) {
            val l = object : WindowAdapter() { override fun windowLostFocus(e: WindowEvent?) = onClose() }
            window.addWindowFocusListener(l)
            window.toFront(); window.requestFocus()
            onDispose { window.removeWindowFocusListener(l) }
        }
        DeckTheme(dark) {
            val c = LocalDeck.current
            val shape = RoundedCornerShape(22.dp)
            Box(Modifier.fillMaxSize().padding(14.dp).shadow(6.dp, shape).clip(shape).background(c.surface).border(1.dp, c.line, shape)) {
                when (panel) {
                    is Panel.More -> deck.byId(panel.projectId)?.let { QuickLook(it, dark, onClose, onEdit) }
                    is Panel.Pick -> Picker(deck, panel.spec.project, onPick = { onPick(panel.spec, it) })
                    is Panel.Git -> deck.byId(panel.projectId)?.let { GitSheet(it, dark, onClose, onGit) }
                }
            }
        }
    }
}

/**
 * The quick look: who the project is, what Claude is doing, the next step, six things to open, and where it stands.
 * Everything else is one click away, on the project's page (drawn also for the renders).
 */
@Composable
fun QuickLook(p: Project, dark: Boolean, onClose: () -> Unit, onOpen: (Project) -> Unit, local: Local = Settings.state.collectAsState().value.localOf(p.id)) {
    val c = LocalDeck.current
    val dir = local.dir
    val session = claudeIn(local.folder)
    val git = local.folder?.let { Git.state.collectAsState().value[it] }
    val pulse = local.folder?.let { Pulses.state.collectAsState().value[it] } ?: Pulse()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(p, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Chrome.describe(p), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pill(tr("Open project", "לפרויקט"), Tone.TINT, small = true) { onOpen(p); onClose() }
        }

        if (session != null && (session.mode == ClaudeMode.WORKING || session.mode == ClaudeMode.WAITING)) {
            val waiting = session.mode == ClaudeMode.WAITING
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (waiting) CHANGED_DOT.copy(alpha = if (dark) 0.14f else 0.12f) else c.accentTint)
                .padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClaudeDot(session.mode, dark, 7.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(claudeText(session), color = if (waiting) c.amberText else claudeColor(session.mode, dark), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                    Text(ago(session.workStart), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                }
                if (session.detail.isNotBlank()) Text(if (waiting) session.detail else "“${session.detail}”", color = c.fg, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
            }
        }

        if (dir != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(p); onClose() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Next", "הבא"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
            Text(local.note.ifBlank { tr("No next step yet", "עוד אין צעד הבא") }, color = if (local.note.isBlank()) c.faint else c.fg, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = PlexSans,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        }

        // Six to open: the first links, then the editor, Claude and the running server (all links without a folder).
        val tools = mutableListOf<QuickItem>()
        if (dir != null) {
            Tools.quick(dir).getOrNull(1)?.takeIf { it.kind == ToolKind.EDITOR || it.kind == ToolKind.IDE }?.let { t -> tools += QuickItem.OfTool(t, t.label.removePrefix("Android ")) { Tools.launch(t, dir, p.name) } }
            Tools.claude?.let { t -> tools += QuickItem.OfTool(t, "Claude") { Tools.launch(t, dir, p.name) } }
            val port = pulse.port
            if (port != null) tools += QuickItem.Server(port) { Chrome.open(p, DeckAction(Catalog.LINK, "http://localhost:$port", "localhost")) }
            else tools += QuickItem.OfTool(Tools.terminal, Tools.terminal.label) { Tools.launch(Tools.terminal, dir, p.name) }
        }
        val items: List<QuickItem> = p.actions.take(6 - tools.size).map { a -> QuickItem.OfLink(a) { Chrome.open(p, a) } } + tools
        if (items.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item -> Box(Modifier.weight(1f)) { QuickTile(item, dark) { item.run(); onClose() } } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (git != null || pulse.missingEnv.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.surface2))
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(p); onClose() }, verticalAlignment = Alignment.CenterVertically) {
                Dot(if ((git?.changes ?: 0) > 0 || (git?.ahead ?: 0) > 0 || pulse.missingEnv.isNotEmpty()) CHANGED_DOT else CLEAN_DOT)
                Spacer(Modifier.width(8.dp))
                Text(buildList {
                    git?.let { add(it.branch); add(gitWords(it)) }
                    if (pulse.missingEnv.isNotEmpty()) add(count(pulse.missingEnv.size, "key missing in .env", "keys missing in .env", "חסר מפתח ב־.env", "מפתחות חסרים ב־.env"))
                }.joinToString(" · "), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One of the quick look's six: a link, a tool on the folder, or the running dev server. */
private sealed class QuickItem(val run: () -> Unit) {
    class OfLink(val action: DeckAction, run: () -> Unit) : QuickItem(run)
    class OfTool(val tool: Tool, val label: String, run: () -> Unit) : QuickItem(run)
    class Server(val port: Int, run: () -> Unit) : QuickItem(run)
}

@Composable
private fun QuickTile(item: QuickItem, dark: Boolean, onClick: () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.paper).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            when (item) {
                is QuickItem.OfLink -> ServiceMark(item.action.service, item.action.faviconHost, 22.dp, dark)
                is QuickItem.OfTool -> ToolMark(item.tool, 22.dp, dark)
                is QuickItem.Server -> Text(":${item.port}", color = c.liveText, fontSize = 12.sp, fontFamily = PlexMono)
            }
        }
        val label = when (item) {
            is QuickItem.OfLink -> item.action.tileLabel
            is QuickItem.OfTool -> item.label
            is QuickItem.Server -> tr("Dev server", "שרת פיתוח")
        }
        Text(label, color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Picker(deck: Deck, currentId: String?, onPick: (Project) -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxSize().padding(vertical = 14.dp)) {
        Text(tr("Projects", "פרויקטים"), color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        LazyColumn {
            items(deck.projects, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().height(60.dp).clickable { onPick(p) }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Logo(p, 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                        if (p.kind.isNotBlank()) Text(p.kind, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                    }
                    if (p.id == currentId) GlyphIcon(Glyph.CHECK, 18.dp, c.accentText)
                }
            }
        }
    }
}
