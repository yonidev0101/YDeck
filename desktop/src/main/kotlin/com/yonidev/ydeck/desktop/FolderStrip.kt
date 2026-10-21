package com.yonidev.ydeck.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
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
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.Writer

/*
 * The project inside File Explorer: when a project's folder (or any folder in it) is open in Explorer, a small strip
 * of the project sits at the bottom of that window. It says first where the project stands, as Now does (Claude
 * waiting or at work, what's open, or all saved and the next step), then its editor, Claude Code and git. It follows that window: it moves with it, hides when it is minimised or closed, stays under other
 * apps that cover it, and never takes the focus from it.
 */

/** Where the strip goes: over which Explorer window, for which project, and its place on the screen (dp). */
data class Attach(val hwnd: Long, val projectId: String, val folder: String, val x: Float, val y: Float, val w: Float, val h: Float)

object FolderStrip {
    /** The strip's height, its shadow's room included. */
    const val HEIGHT = 72f
    private const val MAX_WIDTH = 520f

    private val _state = MutableStateFlow<Attach?>(null)
    val state: StateFlow<Attach?> = _state

    private val user32 = User32.INSTANCE
    private val myPid = ProcessHandle.current().pid()

    fun start() {
        Thread({
            var lastQuery = 0L
            var lastHwnd = 0L
            while (true) {
                runCatching {
                    if (!Settings.state.value.companion) { _state.value = null; Thread.sleep(1500); return@runCatching }
                    val fg = user32.GetForegroundWindow()
                    val fgId = fg?.let { Pointer2.id(it) } ?: 0L
                    val now = System.currentTimeMillis()
                    when {
                        fg == null || pidOf(fg) == myPid -> {} // our own windows (the strip, a panel): keep things as they are
                        className(fg) == "CabinetWClass" -> {
                            // Explorer in front: which folder it shows (asked again every so often, as it navigates).
                            if (fgId != lastHwnd || now - lastQuery > 700) {
                                lastHwnd = fgId; lastQuery = now
                                val folder = ExplorerPaths.current(fg)
                                val hit = folder?.let { Settings.projectAt(File(it)) }
                                _state.value = hit?.let { (id, root) -> place(fg, id, root) }
                                    ?: _state.value?.takeIf { it.hwnd != fgId }
                            }
                        }
                        else -> {}
                    }
                    // Follow the window it belongs to; gone when that window is closed or minimised.
                    _state.value?.let { a ->
                        val h = WinDef.HWND(com.sun.jna.Pointer(a.hwnd))
                        _state.value = if (!user32.IsWindow(h) || !user32.IsWindowVisible(h) || Minimized.lib?.IsIconic(h) == true) null else place(h, a.projectId, a.folder)
                        if (_state.value != null) keepAbove(h)
                    }
                }
                Thread.sleep(120)
            }
        }, "ydeck-companion").apply { isDaemon = true; start() }
    }

    private fun place(h: WinDef.HWND, projectId: String, folder: String): Attach? {
        val r = WinDef.RECT()
        if (!user32.GetWindowRect(h, r)) return null
        val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
        val left = r.left / scale
        val top = r.top / scale
        val width = (r.right - r.left) / scale
        val height = (r.bottom - r.top) / scale
        if (width < 360 || height < 240) return null
        val w = (width - 40).coerceAtMost(MAX_WIDTH)
        // Bottom centre, just above Explorer's status bar.
        return Attach(Pointer2.id(h), projectId, folder, left + (width - w) / 2, top + height - HEIGHT - 26, w, HEIGHT)
    }

    private fun pidOf(h: WinDef.HWND): Long = IntByReference().also { user32.GetWindowThreadProcessId(h, it) }.value.toLong()

    private fun className(h: WinDef.HWND): String = CharArray(64).let { buf -> String(buf, 0, user32.GetClassName(h, buf, buf.size)) }

    fun title(h: WinDef.HWND): String = CharArray(512).let { buf -> String(buf, 0, user32.GetWindowText(h, buf, buf.size)) }

    /** The strip's window: never in the taskbar or Alt+Tab, never activated by a click (Explorer keeps the focus). */
    fun adopt(window: java.awt.Window) {
        runCatching {
            val h = WinDef.HWND(Native.getComponentPointer(window))
            val ex = user32.GetWindowLong(h, WinUser.GWL_EXSTYLE)
            user32.SetWindowLong(h, WinUser.GWL_EXSTYLE, ex or 0x00000080 /* TOOLWINDOW */ or 0x08000000 /* NOACTIVATE */)
            strip = h
        }
    }

    @Volatile private var strip: WinDef.HWND? = null

    /**
     * Keeps the strip right above its Explorer window in the z-order: over it, under any app that covers it. (Not an
     * owned window: an owner in another process would tie YDeck's input to Explorer's.)
     */
    private fun keepAbove(explorer: WinDef.HWND) {
        val s = strip ?: return
        if (!user32.IsWindowVisible(s)) return
        val prev = user32.GetWindow(explorer, WinDef.DWORD(3) /* GW_HWNDPREV */)
        if (prev != null && Pointer2.id(prev) == Pointer2.id(s)) return
        val topmost = prev != null && (user32.GetWindowLong(prev, WinUser.GWL_EXSTYLE) and 0x00000008 /* TOPMOST */) != 0
        val after = if (prev == null || topmost) WinDef.HWND(com.sun.jna.Pointer.createConstant(0)) /* HWND_TOP */ else prev
        user32.SetWindowPos(s, after, 0, 0, 0, 0, 0x0001 or 0x0002 or 0x0010 /* NOSIZE | NOMOVE | NOACTIVATE */)
    }
}

private object Pointer2 {
    fun id(h: WinDef.HWND): Long = com.sun.jna.Pointer.nativeValue(h.pointer)
}

/**
 * The folder each Explorer window shows, from the shell itself (Shell.Application) through one PowerShell that stays
 * open and answers a line at a time. A window with tabs lists each tab; the one in front is the one its title names.
 */
private object ExplorerPaths {
    private var process: Process? = null
    private var input: Writer? = null
    private var output: BufferedReader? = null

    private const val SCRIPT = """
[Console]::OutputEncoding = [Text.Encoding]::UTF8
${'$'}sh = New-Object -ComObject Shell.Application
while (${'$'}true) {
  ${'$'}l = [Console]::In.ReadLine(); if (${'$'}l -eq ${'$'}null) { break }
  foreach (${'$'}w in @(${'$'}sh.Windows())) { try { ${'$'}p = ${'$'}w.Document.Folder.Self.Path; if (${'$'}p) { [Console]::Out.WriteLine([string]${'$'}w.HWND + "`t" + ${'$'}p) } } catch {} }
  [Console]::Out.WriteLine('END'); [Console]::Out.Flush()
}
"""

    fun current(h: WinDef.HWND): String? {
        val id = com.sun.jna.Pointer.nativeValue(h.pointer)
        val tabs = windows().filter { it.first == id }.map { it.second }
        if (tabs.size <= 1) return tabs.firstOrNull()
        val title = FolderStrip.title(h)
        return tabs.firstOrNull { title.startsWith(File(it).name.ifBlank { it }) } ?: tabs.first()
    }

    @Synchronized
    private fun windows(): List<Pair<Long, String>> {
        repeat(2) {
            val p = process?.takeIf { it.isAlive } ?: start()
            val result = runCatching {
                input!!.write("q\n"); input!!.flush()
                buildList {
                    while (true) {
                        val line = output!!.readLine() ?: error("closed")
                        if (line == "END") break
                        val hwnd = line.substringBefore('\t').toLongOrNull() ?: continue
                        add(hwnd to line.substringAfter('\t'))
                    }
                }
            }
            result.getOrNull()?.let { return it }
            p.destroy(); process = null
        }
        return emptyList()
    }

    private fun start(): Process {
        val p = ProcessBuilder(Ps.exe, "-NoProfile", "-NonInteractive", "-EncodedCommand", Ps.encode(SCRIPT)).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process = p
        input = p.outputStream.writer(Charsets.UTF_8)
        output = p.inputStream.bufferedReader(Charsets.UTF_8)
        return p
    }
}

/** What the strip can ask the app for. */
class StripActions(
    val tool: (Project, Tool) -> Unit,
    val open: (Project, String) -> Unit,
    val link: (Project, DeckAction) -> Unit,
    val more: (Project, Attach) -> Unit,
    val git: (Project, Attach) -> Unit,
    val edit: (Project) -> Unit,
)

/** The strip's window: made once and shown or hidden, so it appears at once and never flickers. */
@Composable
fun ApplicationScope.FolderStripWindow(attach: Attach?, deck: Deck, dark: Boolean, actions: StripActions) {
    val last = remember { mutableListOf<Attach>() }
    if (attach != null) { last.clear(); last += attach }
    val a = attach ?: last.firstOrNull() ?: return
    val state = rememberWindowState(position = WindowPosition(a.x.dp, a.y.dp), size = DpSize(a.w.dp, a.h.dp))
    Window(onCloseRequest = {}, state = state, visible = attach != null && deck.byId(attach.projectId) != null, title = "YDeck", undecorated = true, transparent = true,
        resizable = false, focusable = false, alwaysOnTop = false, icon = androidx.compose.ui.res.painterResource("icons/app.png")) {
        LaunchedEffect(Unit) { window.setAutoRequestFocus(false); FolderStrip.adopt(window) }
        LaunchedEffect(attach != null) { if (attach != null) UsageStats.mark(UsageStats.Feature.STRIP) }
        LaunchedEffect(a.x, a.y, a.w) {
            state.position = WindowPosition(a.x.dp, a.y.dp)
            state.size = DpSize(a.w.dp, a.h.dp)
        }
        val project = deck.byId(a.projectId) ?: return@Window
        DeckTheme(dark) { FolderStripCard(project, a, dark, actions) }
    }
}

/** The strip itself (also drawn without a window, for the renders). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderStripCard(p: Project, a: Attach, dark: Boolean, actions: StripActions) {
    val c = LocalDeck.current
    val dir = File(a.folder)
    val git = Git.state.collectAsState().value[a.folder]
    val pulse = Pulses.state.collectAsState().value[a.folder]
    val shape = RoundedCornerShape(18.dp)
    ContextMenuArea(items = {
        listOf(
            ContextMenuItem(tr("Open ${p.name}", "פתיחת ${p.name}")) { actions.edit(p) },
            ContextMenuItem(tr("Turn off the strip in File Explorer", "כיבוי הפס בסייר הקבצים")) { Settings.update { it.copy(companion = false) } },
        )
    }) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp).shadow(5.dp, shape).clip(shape)
            .background(glass(p.color, dark).copy(alpha = 1f)).border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f), shape)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val local = Settings.state.collectAsState().value.localOf(p.id).let { if (it.folder == null) it.copy(folder = a.folder) else it }
                val stand = standOf(p, local)
                Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(p, 28.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.fg, style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Where it stands, then what goes with it (Claude's question, the next open thing, the next step).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StandDot(stand, dark, 6.dp)
                            Spacer(Modifier.width(5.dp))
                            val lit = stand.mode == StandMode.WAITING || stand.mode == StandMode.WORKING
                            val line = stand.title + (if (stand.detail.isNotBlank()) " · " + stand.detail else "")
                            Text(line,
                                color = if (lit) standColor(stand, dark) else c.muted,
                                style = TextStyle(fontFamily = PlexSans, fontWeight = if (lit) FontWeight.Medium else FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp,
                                    textDirection = dirOf(line)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    pulse?.port?.let { port -> LivePill(port, dark) { actions.open(p, "http://localhost:$port") } }
                    // Something open: how much, and a click shows it on the project's page, with what closes it.
                    if (stand.loops.isNotEmpty()) StripButton(tr("What's open in ${p.name}", "מה פתוח ב־${p.name}"), dark, { actions.edit(p) }) {
                        Box(Modifier.clip(RoundedCornerShape(99.dp)).background(CHANGED_DOT.copy(alpha = if (dark) 0.22f else 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text("${stand.loops.size}", color = if (dark) Color(0xFFFFC56B) else Color(0xFF9A5B00), style = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp))
                        }
                    }
                    // Explorer is already open here: the editor and Claude Code (a terminal is in "more").
                    listOfNotNull(Tools.studio?.takeIf { Tools.isAndroid(dir) } ?: Tools.vscode ?: Tools.cursor, Tools.claude).forEach { t ->
                        val tip = when {
                            t.kind == ToolKind.AGENT && pulse?.claudeAt != null -> tr("Continue with Claude · ${ago(pulse.claudeAt)}", "המשך עם Claude · ${ago(pulse.claudeAt)}")
                            t.kind == ToolKind.AGENT -> "Claude Code"
                            else -> t.label
                        }
                        StripButton(tip, dark, { actions.tool(p, t) }) { ToolMark(t, 18.dp, dark) }
                    }
                    // Git: its sheet, with a dot while something waits (changes to commit, commits to push or pull).
                    if (git != null) StripButton("Git · " + gitWords(git), dark, { actions.git(p, a) }) {
                        Box {
                            GlyphIcon(Glyph.BRANCH, 18.dp, if (dark) Color(0xFFD1D1D6) else Color(0xFF3C3C43))
                            if (git.changes > 0 || git.ahead > 0 || git.behind > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(7.dp).clip(RoundedCornerShape(4.dp)).background(CHANGED_DOT))
                        }
                    }
                    StripButton(tr("More", "עוד"), dark, { actions.more(p, a) }) { Dots(18.dp, if (dark) Color(0xFFD1D1D6) else Color(0xFF3C3C43)) }
                }
            }
        }
    }
}

val CHANGED_DOT = Color(0xFFF5A524)
val CLEAN_DOT = Color(0xFF34C77B)
/** Something at work (an agent, a dev server running): the brand's indigo, a shade lighter to read on dark. */
val LIVE_DOT = Color(0xFF5B54F0)

/** A dev server running from the project: an indigo dot (at work) and its port; a click opens it. */
@Composable
fun LivePill(port: Int, dark: Boolean, onClick: () -> Unit) {
    val green = if (dark) Color(0xFF9C97FF) else Color(0xFF3D35E0)
    Row(Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(99.dp)).background(green.copy(alpha = if (dark) 0.16f else 0.10f)).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(green))
        Spacer(Modifier.width(5.dp))
        Text(":$port", color = green, style = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.5.sp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripButton(tip: String, dark: Boolean, onClick: () -> Unit, mark: @Composable () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    TooltipArea(tooltip = {
        Text(tip, color = Color(0xFFF4F4F5), fontSize = 12.sp, fontFamily = PlexSans,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xEE0A0A0B)).padding(horizontal = 10.dp, vertical = 5.dp))
    }, delayMillis = 400, tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, (-34).dp))) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).hoverable(hover)
            .background(if (hovered) (if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)) else Color.Transparent)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) { mark() }
    }
}


/** IsIconic, which JNA's User32 leaves out. */
private object Minimized {
    interface Lib : com.sun.jna.Library { fun IsIconic(hwnd: WinDef.HWND): Boolean }
    val lib: Lib? by lazy { runCatching { Native.load("user32", Lib::class.java) }.getOrNull() }
}
