package com.yonidev.ydeck.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView

/*
 * What a project has on this PC only, kept in desktop.json beside the widgets (projects.json stays the phone's
 * format): its folder, the commands run in it, and pinned files. From the folder come the local tools (Explorer,
 * the code editors, a terminal, Claude Code), the git state on the widget, and the project's icon on the folder.
 */

data class Command(val label: String, val line: String)

data class Local(
    val folder: String? = null,
    /** The folder shows the project's logo in Explorer (desktop.ini), see [FolderTag]. */
    val tagged: Boolean = false,
    val commands: List<Command> = emptyList(),
    val files: List<String> = emptyList(),
    /** "The next step": one line the developer leaves for themself, shown where the project's state is. */
    val note: String = "",
) {
    val dir: File? get() = folder?.let(::File)?.takeIf { it.isDirectory }
    val isEmpty: Boolean get() = folder == null && commands.isEmpty() && files.isEmpty() && note.isBlank()
}

// ── tools ──

enum class ToolKind { FOLDER, EDITOR, IDE, TERMINAL, AGENT }

/** A program that opens a project's folder. */
data class Tool(val id: String, val label: String, val exe: File, val kind: ToolKind)

object Tools {
    private val localApp = System.getenv("LOCALAPPDATA") ?: ""
    private val home = System.getProperty("user.home")
    private val windows = System.getenv("WINDIR") ?: "C:\\Windows"

    private fun first(vararg paths: String): File? = paths.map(::File).firstOrNull { it.isFile }

    /** A program on the PATH (the first of [names] found). */
    fun which(vararg names: String): File? {
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparatorChar).filter { it.isNotBlank() }
        return names.firstNotNullOfOrNull { n -> dirs.map { File(it, n) }.firstOrNull { it.isFile } }
    }

    val explorer: Tool get() = Tool("explorer", tr("Folder", "תיקייה"), File(windows, "explorer.exe"), ToolKind.FOLDER)
    private val windowsTerminal: File? by lazy { first("$localApp\\Microsoft\\WindowsApps\\wt.exe") }
    private val powershell = File(windows, "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
    private val cmd = File(windows, "System32\\cmd.exe")

    val terminal: Tool get() = Tool("terminal", tr("Terminal", "טרמינל"), windowsTerminal ?: powershell, ToolKind.TERMINAL)
    val vscode: Tool? by lazy {
        first("$localApp\\Programs\\Microsoft VS Code\\Code.exe", "C:\\Program Files\\Microsoft VS Code\\Code.exe")?.let { Tool("vscode", "VS Code", it, ToolKind.EDITOR) }
    }
    val cursor: Tool? by lazy { first("$localApp\\Programs\\cursor\\Cursor.exe")?.let { Tool("cursor", "Cursor", it, ToolKind.EDITOR) } }
    val studio: Tool? by lazy {
        first("C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe", "$localApp\\Programs\\Android Studio\\bin\\studio64.exe")
            ?.let { Tool("studio", "Android Studio", it, ToolKind.IDE) }
    }
    val claude: Tool? by lazy {
        (first("$home\\.local\\bin\\claude.exe") ?: which("claude.exe", "claude.cmd"))?.let { Tool("claude", "Claude Code", it, ToolKind.AGENT) }
    }

    fun isAndroid(dir: File) = listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle").any { File(dir, it).isFile }

    /** Every tool that fits the folder: Explorer, the editors installed, Android Studio for a Gradle project, a terminal, Claude Code. */
    fun forFolder(dir: File): List<Tool> = listOfNotNull(
        explorer, studio?.takeIf { isAndroid(dir) }, vscode, cursor, terminal, claude,
    )

    /** The three on the widget: the folder, the project's editor, and Claude Code (or a terminal). */
    fun quick(dir: File): List<Tool> = listOfNotNull(
        explorer, (studio?.takeIf { isAndroid(dir) } ?: vscode ?: cursor), claude ?: terminal,
    )

    fun byId(id: String): Tool? = listOfNotNull(explorer, terminal, vscode, cursor, studio, claude).firstOrNull { it.id == id }

    /**
     * Opens [dir] in [tool]. Claude Code picks up the folder's last conversation (`--continue`) unless [fresh], or
     * starts a new one on [prompt] (one line: it goes through the terminal's command line).
     */
    fun launch(tool: Tool, dir: File, title: String, fresh: Boolean = false, prompt: String? = null) {
        val wt = windowsTerminal
        val cmdLine = when (tool.kind) {
            ToolKind.FOLDER -> listOf(tool.exe.path, dir.path)
            ToolKind.EDITOR, ToolKind.IDE -> listOf(tool.exe.path, dir.path)
            ToolKind.TERMINAL -> {
                val shell = Shells.interactive(Shells.current)
                if (wt != null) listOf(wt.path, "-d", dir.path, "--title", title) + shell
                else listOf(cmd.path, "/c", "start", title, "/D", dir.path) + shell
            }
            ToolKind.AGENT -> claudeLine(tool.exe, dir, title, when {
                prompt != null -> listOfNotNull(said(prompt))
                !fresh && Claude.lastSession(dir) != null -> listOf("--continue")
                else -> emptyList()
            })
        }
        start(cmdLine, dir)
    }

    /**
     * Claude Code in [dir], in a new terminal: a new conversation, the folder's last one ([resume] = "continue"), or
     * one by its id; with Remote Control on, named [remote]; [prompt] as the first message. False when Claude Code
     * isn't here or the terminal couldn't start.
     */
    fun claudeIn(dir: File, title: String, resume: String? = null, remote: String? = null, prompt: String? = null): Boolean {
        val exe = claude?.exe ?: return false
        val args = buildList {
            when {
                resume == "continue" -> add("--continue")
                resume != null -> { add("--resume"); add(resume) }
            }
            // Named, so the prompt after it isn't taken for the name.
            if (remote != null) { add("--remote-control"); add(said(remote) ?: "YDeck") }
            prompt?.let(::said)?.let(::add)
        }
        return start(claudeLine(exe, dir, title, args), dir)
    }

    private fun claudeLine(exe: File, dir: File, title: String, args: List<String>): List<String> {
        val claude = listOf(exe.path) + args
        val wt = windowsTerminal
        return if (wt != null) listOf(wt.path, "-d", dir.path, "--title", "Claude Code · ${said(title) ?: ""}") + claude
        else listOf(cmd.path, "/c", "start", "Claude Code", "/D", dir.path, cmd.path, "/k") + claude
    }

    /**
     * Words for Claude Code's command line, as one argument: one line; no quotes, ";" (a new tab to Windows
     * Terminal) or cmd's own signs, which would cut it short; no leading "-", which would make it an option; no
     * trailing "\", which would turn the closing quote into one.
     */
    fun said(text: String): String? =
        text.replace(Regex("\\s*[\\r\\n]+\\s*"), " ").replace(Regex("[\";&|<>^%!]"), " ").trim().trimStart('-', ' ').trimEnd('\\', ' ').takeIf { it.isNotEmpty() }

    /** A command in a new terminal tab, in the folder; the tab stays open to show what happened. */
    fun run(command: Command, dir: File) {
        val wt = windowsTerminal
        // Windows Terminal reads ";" as "new tab": escaped, it reaches the shell.
        val shell = Shells.running(Shells.current, command.line).map { if (wt != null) it.replace(";", "\\;") else it }
        start(
            if (wt != null) listOf(wt.path, "-d", dir.path, "--title", command.label.ifBlank { command.line }) + shell
            else listOf(cmd.path, "/c", "start", command.label.ifBlank { "YDeck" }, "/D", dir.path) + shell,
            dir,
        )
    }

    /** One file in an editor (VS Code, Cursor, Android Studio). */
    fun openIn(tool: Tool, file: File) = start(listOf(tool.exe.path, file.path), file.parentFile)

    fun openFile(path: String) {
        val f = File(path)
        if (!f.exists()) return
        runCatching { Desktop.getDesktop().open(f) }.onFailure { start(listOf(explorer.exe.path, f.path), f.parentFile) }
    }

    fun reveal(path: String) {
        start(listOf(explorer.exe.path, "/select,", File(path).path), null)
    }

    private fun start(cmdLine: List<String>, dir: File?): Boolean = runCatching {
        ProcessBuilder(cmdLine).apply { if (dir?.isDirectory == true) directory(dir) }
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    }.isSuccess
}

/** The shell for the terminal button and the project commands, chosen once for the whole PC (desktop.json). */
enum class Shell(val id: String, val label: String) { CMD("cmd", "Command Prompt"), POWERSHELL("powershell", "PowerShell"), PWSH("pwsh", "PowerShell 7"), BASH("bash", "Git Bash") }

object Shells {
    private val windows = System.getenv("WINDIR") ?: "C:\\Windows"

    fun exe(s: Shell): File? = when (s) {
        Shell.CMD -> File(windows, "System32\\cmd.exe")
        Shell.POWERSHELL -> File(windows, "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        Shell.PWSH -> File("C:\\Program Files\\PowerShell\\7\\pwsh.exe").takeIf { it.isFile } ?: Tools.which("pwsh.exe")
        Shell.BASH -> File("C:\\Program Files\\Git\\bin\\bash.exe")
    }?.takeIf { it.isFile }

    val available: List<Shell> by lazy { Shell.entries.filter { exe(it) != null } }

    val current: Shell get() = available.firstOrNull { it.id == Settings.state.value.shell } ?: Shell.CMD

    /** An open shell, waiting for input. */
    fun interactive(s: Shell): List<String> {
        val e = exe(s)!!.path
        return when (s) {
            Shell.CMD -> listOf(e)
            Shell.POWERSHELL, Shell.PWSH -> listOf(e, "-NoLogo")
            Shell.BASH -> listOf(e, "--login", "-i")
        }
    }

    /** [line] run in the shell, which then stays open to show what happened. */
    fun running(s: Shell, line: String): List<String> {
        val e = exe(s)!!.path
        return when (s) {
            Shell.CMD -> listOf(e, "/k", line)
            Shell.POWERSHELL, Shell.PWSH -> listOf(e, "-NoLogo", "-NoExit", "-Command", line)
            Shell.BASH -> listOf(e, "--login", "-c", "$line; exec bash --login -i")
        }
    }
}

/** Each program's own Windows icon (the folder's for Explorer); Claude Code gets its brand mark instead. */
object ToolIcons {
    private val cache = ConcurrentHashMap<String, Any>()
    private object None

    fun of(tool: Tool): ImageBitmap? {
        val v = cache.getOrPut(tool.id) {
            val file = if (tool.kind == ToolKind.FOLDER) File(System.getProperty("java.io.tmpdir")) else tool.exe
            systemIcon(file) ?: None
        }
        return v as? ImageBitmap
    }

    /** A pinned file's icon: by type (folders, documents), or its own for programs and shortcuts. */
    fun file(f: File): ImageBitmap? {
        val ext = f.extension.lowercase()
        val key = when { f.isDirectory -> "dir:" + f.path; ext in setOf("exe", "lnk", "ico", "url") -> "f:" + f.path; else -> "ext:$ext" }
        return cache.getOrPut(key) { systemIcon(f) ?: None } as? ImageBitmap
    }

    private fun systemIcon(file: File): ImageBitmap? = runCatching {
        val icon = FileSystemView.getFileSystemView().getSystemIcon(file, 64, 64) ?: return null
        val img = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        img.createGraphics().apply { icon.paintIcon(null, this, 0, 0); dispose() }
        img.toComposeImageBitmap()
    }.getOrNull()
}

@Composable
fun FileMark(path: String, size: Dp) {
    val bmp = remember(path) { ToolIcons.file(File(path)) }
    if (bmp != null) Image(bmp, File(path).name, Modifier.size(size))
    else Text("📄", fontSize = (size.value * 0.75f).sp)
}

@Composable
fun ToolMark(tool: Tool, size: Dp, dark: Boolean) {
    if (tool.kind == ToolKind.AGENT) { ServiceMark(Catalog.of("claude"), null, size, dark); return }
    val bmp = remember(tool.id) { ToolIcons.of(tool) }
    if (bmp != null) Image(bmp, tool.label, Modifier.size(size))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(Color(0xFF1D1D1F)), contentAlignment = Alignment.Center) {
        Text(">_", color = Color.White, style = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = (size.value * 0.42f).sp))
    }
}

// ── an agent's state on the folder's icon ──

/**
 * A tagged folder's icon shows when an agent works in it (an indigo dot) or waits for the user (amber), and goes back
 * to the plain logo when it's done; a smaller amber dot above it while there are changes not yet committed. A state changes the icon once it has held for a few seconds, and an icon changes
 * at most every few seconds, so a busy session doesn't make Explorer flicker. Off in Settings: every icon goes plain.
 */
object FolderBadges {
    private const val SETTLE_MS = 3_000L
    private const val GAP_MS = 8_000L
    private val shown = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap<String, Pair<String, Long>>()
    private val changed = ConcurrentHashMap<String, Long>()

    fun start() {
        Thread({
            while (true) {
                runCatching { tick(System.currentTimeMillis()) }
                Thread.sleep(2_000)
            }
        }, "ydeck-folder-badges").apply { isDaemon = true; start() }
    }

    fun tick(now: Long) {
        val settings = Settings.state.value
        val sessions = ClaudeLink.sessions.value
        for (p in Repo.deck.value.projects) {
            val local = settings.localOf(p.id)
            if (!local.tagged) continue
            val dir = local.dir ?: continue
            val key = dir.path
            val mode = if (!settings.folderStatus) null else ClaudeLink.statusIn(key, sessions, now)?.mode?.takeIf { it != ClaudeMode.DONE }
            val dirty = settings.folderGit && (Git.state.value[local.folder]?.changes ?: 0) > 0
            val want = (mode?.name ?: "NONE") + if (dirty) "+GIT" else ""
            val have = shown.getOrPut(key) { FolderTag.marks(dir) }
            if (want == have) { pending.remove(key); continue }
            val since = pending[key]?.takeIf { it.first == want }?.second ?: now.also { pending[key] = want to now }
            if (now - since < SETTLE_MS || now - (changed[key] ?: 0L) < GAP_MS) continue
            FolderTag.apply(dir, p, mode, dirty)
            shown[key] = want
            changed[key] = now
            pending.remove(key)
        }
    }
}

// ── the project's icon on its folder ──

/**
 * Explorer shows the project's logo on its folder, and its name and account on hover: a desktop.ini beside the
 * code (hidden, and kept out of git through .git/info/exclude) pointing at an .ico made from the logo.
 */
object FolderTag {
    private const val MARK = "; YDeck"
    private val icons = File(AppDirs.local, "folder-icons").apply { mkdirs() }

    fun isTagged(dir: File): Boolean = runCatching { read(File(dir, "desktop.ini")).contains(MARK) }.getOrDefault(false)

    /**
     * The dots the folder's icon carries now (left from before YDeck last closed, say), as [FolderBadges] names them:
     * "NONE", or "BADGED" for an agent's of either kind, with "+GIT" for uncommitted changes.
     */
    fun marks(dir: File): String {
        val icon = iconName(dir)
        return (if ("-badge" in icon) "BADGED" else "NONE") + if ("-git" in icon) "+GIT" else ""
    }

    /** The name of the .ico the folder's desktop.ini points at ("" for none): it starts with the project's id. */
    fun iconName(dir: File): String {
        val ini = runCatching { read(File(dir, "desktop.ini")) }.getOrDefault("")
        return Regex("(?im)^\\s*IconResource\\s*=\\s*(.+?),\\d+\\s*$").find(ini)?.groupValues?.get(1)?.trim()?.substringAfterLast('\\').orEmpty()
    }

    /** Whether [dir] carries [projectId]'s icon (it's that project's folder, wherever it is now and whatever its name). */
    fun isFolderOf(dir: File, projectId: String): Boolean = iconName(dir).startsWith(safe(projectId) + "-")

    /** Tagged folders whose icon file is gone (an old install folder, cleared by an update) get theirs again. */
    fun repair(deck: Deck, settings: Settings.State) {
        for (p in deck.projects) {
            val local = settings.localOf(p.id)
            val dir = local.dir ?: continue
            if (!local.tagged) continue
            val ini = runCatching { read(File(dir, "desktop.ini")) }.getOrNull() ?: continue
            val icon = Regex("(?im)^\\s*IconResource\\s*=\\s*(.+?),\\d+\\s*$").find(ini)?.groupValues?.get(1)?.trim()
            if (icon == null || !File(icon).isFile) apply(dir, p)
        }
    }

    /**
     * Writes the folder's icon: the project's logo, [badge] (an agent at work or waiting) as a dot in its lower corner,
     * and [git] (changes not yet committed) as a smaller amber dot in its upper one, where the widget shows it.
     */
    fun apply(dir: File, p: Project, badge: ClaudeMode? = null, git: Boolean = false) {
        runCatching {
            // A new file name each time: Explorer caches icons by path. Its dots say so in its name.
            icons.listFiles { f -> f.name.startsWith(safe(p.id) + "-") }?.forEach { it.delete() }
            val ico = File(icons, "${safe(p.id)}-${System.currentTimeMillis().toString(36)}${if (badge != null) "-badge" else ""}${if (git) "-git" else ""}.ico")
            ico.writeBytes(Ico.encode(render(p, badge, git)))
            val tip = listOfNotNull("YDeck", p.name, p.googleEmail).joinToString(" · ")
            write(dir, mapOf("IconResource" to "${ico.path},0", "InfoTip" to tip))
            Files.setAttribute(dir.toPath(), "dos:readonly", true)
            GitExclude.add(dir, "desktop.ini")
            Shell.refresh(dir)
        }
    }

    fun remove(dir: File) {
        runCatching {
            val ini = File(dir, "desktop.ini")
            if (!ini.exists() || !read(ini).contains(MARK)) return
            write(dir, null)
            Shell.refresh(dir)
        }
    }

    /** Sets our keys in [.ShellClassInfo] (or removes them when [keys] is null), keeping everything else in the file. */
    private fun write(dir: File, keys: Map<String, String>?) {
        val ini = File(dir, "desktop.ini")
        val ours = setOf("iconresource", "iconfile", "iconindex", "infotip")
        val lines = (if (ini.exists()) read(ini).lines() else emptyList()).filter { it.isNotBlank() && it.trim() != MARK }.toMutableList()
        var section = ""
        val kept = lines.filter { line ->
            val t = line.trim()
            if (t.startsWith("[")) { section = t.lowercase(); true }
            else !(section == "[.shellclassinfo]" && t.substringBefore('=').trim().lowercase() in ours)
        }.toMutableList()
        if (keys != null) {
            var at = kept.indexOfFirst { it.trim().lowercase() == "[.shellclassinfo]" }
            if (at < 0) { kept.add(0, "[.ShellClassInfo]"); at = 0 }
            kept.addAll(at + 1, keys.map { (k, v) -> "$k=$v" })
            kept.add(MARK)
        }
        val meaningful = kept.any { !it.trim().startsWith("[") && !it.trim().startsWith(";") }
        if (ini.exists()) runCatching {
            Files.setAttribute(ini.toPath(), "dos:hidden", false); Files.setAttribute(ini.toPath(), "dos:system", false); Files.setAttribute(ini.toPath(), "dos:readonly", false)
        }
        if (!meaningful) { ini.delete(); return }
        // UTF-16 with a BOM: Explorer reads Hebrew names and paths correctly only so.
        ini.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + (kept.joinToString("\r\n") + "\r\n").toByteArray(Charsets.UTF_16LE))
        Files.setAttribute(ini.toPath(), "dos:hidden", true)
        Files.setAttribute(ini.toPath(), "dos:system", true)
    }

    private fun read(ini: File): String {
        val b = ini.readBytes()
        return when {
            b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
            b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() -> String(b, 3, b.size - 3, Charsets.UTF_8)
            else -> String(b, Charsets.UTF_8)
        }
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** The logo (or the monogram on the project colour) as a rounded square, 256 px. */
    fun render(p: Project, badge: ClaudeMode? = null, git: Boolean = false): BufferedImage {
        val s = 256
        val inset = 16f
        val img = BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val side = s - inset * 2
        val shape = RoundRectangle2D.Float(inset, inset, side, side, side * 0.58f, side * 0.58f)
        g.color = java.awt.Color(p.color, false)
        g.fill(shape)
        val logo = Repo.logoFile(p.logo)?.let { runCatching { ImageIO.read(it) }.getOrNull() }
        if (logo != null) {
            g.clip = shape
            g.drawImage(logo, inset.toInt(), inset.toInt(), side.toInt(), side.toInt(), null)
        } else {
            val font = runCatching {
                Font.createFont(Font.TRUETYPE_FONT, FolderTag::class.java.getResourceAsStream("/fonts/plex_sans_hebrew_bold.ttf")).deriveFont(side * 0.5f)
            }.getOrElse { Font("Segoe UI", Font.BOLD, (side * 0.5f).toInt()) }
            val text = p.mono.ifBlank { p.name.take(1).uppercase() }
            val bounds = font.createGlyphVector(g.fontRenderContext, text).visualBounds
            g.font = font
            g.color = java.awt.Color.WHITE
            g.drawString(text, (s - bounds.width.toFloat()) / 2 - bounds.x.toFloat(), (s - bounds.height.toFloat()) / 2 - bounds.y.toFloat())
        }
        if (badge != null && badge != ClaudeMode.DONE) {
            // The agent's state, as on the widget: indigo at work, amber waiting; a night rim sets it off the logo.
            g.clip = null
            val d = s * 0.34f
            // whole inside the icon, rim and all
            val x = s - d * 1.16f - 2f
            val y = s - d * 1.16f - 2f
            g.color = java.awt.Color(0x0B, 0x0B, 0x0D)
            g.fill(java.awt.geom.Ellipse2D.Float(x - d * 0.14f, y - d * 0.14f, d * 1.28f, d * 1.28f))
            g.color = if (badge == ClaudeMode.WAITING) java.awt.Color(0xF5, 0xA5, 0x24) else java.awt.Color(0x5B, 0x54, 0xF0)
            g.fill(java.awt.geom.Ellipse2D.Float(x, y, d, d))
        }
        if (git) {
            // Uncommitted changes: amber, as on the widget, smaller than an agent's and in the upper corner.
            g.clip = null
            val d = s * 0.26f
            val x = s - d * 1.16f - 2f
            val y = d * 0.16f + 2f
            g.color = java.awt.Color(0x0B, 0x0B, 0x0D)
            g.fill(java.awt.geom.Ellipse2D.Float(x - d * 0.14f, y - d * 0.14f, d * 1.28f, d * 1.28f))
            g.color = java.awt.Color(0xF5, 0xA5, 0x24)
            g.fill(java.awt.geom.Ellipse2D.Float(x, y, d, d))
        }
        g.dispose()
        return img
    }

    private interface Shell32 : Library {
        fun SHChangeNotify(eventId: Int, flags: Int, item1: WString?, item2: Pointer?)
    }

    private object Shell {
        private val lib by lazy { runCatching { Native.load("shell32", Shell32::class.java) }.getOrNull() }
        fun refresh(dir: File) {
            runCatching {
                lib?.SHChangeNotify(0x00002000 /* SHCNE_UPDATEITEM */, 0x0005 or 0x1000 /* SHCNF_PATHW | FLUSH */, WString(dir.path), null)
                dir.parentFile?.let { lib?.SHChangeNotify(0x00001000 /* SHCNE_UPDATEDIR */, 0x0005 or 0x1000, WString(it.path), null) }
            }
        }
    }
}

/** A Windows .ico holding PNG images (Vista and later read those), from one large square image. */
object Ico {
    val SIZES = listOf(256, 64, 48, 32, 24, 16)

    fun encode(source: BufferedImage): ByteArray {
        val pngs = SIZES.map { size -> png(scale(source, size)) }
        val header = ByteBuffer.allocate(6 + 16 * SIZES.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(SIZES.size.toShort())
        var offset = 6 + 16 * SIZES.size
        SIZES.zip(pngs).forEach { (size, data) ->
            header.put((if (size >= 256) 0 else size).toByte()).put((if (size >= 256) 0 else size).toByte())
            header.put(0).put(0).putShort(1).putShort(32).putInt(data.size).putInt(offset)
            offset += data.size
        }
        return ByteArrayOutputStream().apply { write(header.array()); pngs.forEach { write(it) } }.toByteArray()
    }

    private fun scale(src: BufferedImage, size: Int): BufferedImage {
        var img = src
        // Halving steps keep small sizes smooth.
        while (img.width / 2 >= size) img = resize(img, img.width / 2)
        return if (img.width == size) img else resize(img, size)
    }

    private fun resize(src: BufferedImage, size: Int): BufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { out ->
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(src, 0, 0, size, size, null); dispose()
        }
    }

    private fun png(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
}
