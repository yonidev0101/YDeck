package com.yonidev.ydeck.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.awt.Window
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds

object Win32 {
    private val user32 = User32.INSTANCE

    private fun hwnd(window: Window) = WinDef.HWND(Native.getComponentPointer(window))

    /**
     * A widget belongs to the desktop, not to the open apps: it's owned by the desktop window (Progman), so
     * "Show desktop" (Win+D) doesn't hide it; it stays out of the taskbar and Alt+Tab; and it sits under other windows.
     */
    fun pinToDesktop(window: Window) {
        runCatching {
            val h = hwnd(window)
            val ex = user32.GetWindowLong(h, WinUser.GWL_EXSTYLE)
            user32.SetWindowLong(h, WinUser.GWL_EXSTYLE, (ex or WS_EX_TOOLWINDOW) and WS_EX_APPWINDOW.inv())
            user32.FindWindow("Progman", null)?.let { progman ->
                user32.SetWindowLongPtr(h, GWLP_HWNDPARENT, progman.pointer)
            }
            sendToBack(window)
        }
    }

    /** Back under the other windows (after a click, so the widget never covers the app being worked in). */
    fun sendToBack(window: Window) {
        runCatching {
            user32.SetWindowPos(hwnd(window), HWND_BOTTOM, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE)
        }
    }

    private interface Dwm : com.sun.jna.Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }
    private val dwm by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    /** Windows 11's rounded corners and shadow on a frameless window (the main window, the panels). */
    fun roundCorners(window: Window) {
        runCatching { dwm?.DwmSetWindowAttribute(hwnd(window), 33 /* DWMWA_WINDOW_CORNER_PREFERENCE */, IntByReference(2 /* ROUND */), 4) }
    }

    private val HWND_BOTTOM = WinDef.HWND(Pointer.createConstant(1))
    private const val GWLP_HWNDPARENT = -8
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOACTIVATE = 0x0010
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_APPWINDOW = 0x00040000

}

/** "Start with Windows": the installed launcher in the user's Run key, started quietly (widgets and tray only). */
object Autostart {
    private const val RUN = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "YDeck"
    const val BACKGROUND_ARG = "--background"

    /** The installed YDeck.exe (null when running from Gradle during development). */
    private val launcher: String? get() = System.getProperty("jpackage.app-path")

    val available get() = launcher != null

    val enabled: Boolean
        get() = runCatching { Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN, NAME) }.getOrDefault(false)

    fun set(on: Boolean) {
        val exe = launcher ?: return
        runCatching {
            if (on) Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN, NAME, "\"$exe\" $BACKGROUND_ARG")
            else if (enabled) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN, NAME)
        }
    }
}

/**
 * "Open in YDeck" on Explorer's right-click menu, on a folder and on the empty space inside one: a project's folder
 * opens its panel, any other folder starts a new project from it. Written under HKCU (no admin), only for the
 * installed app.
 */
object ExplorerMenu {
    const val ARG = "--folder"
    private val KEYS = listOf("Software\\Classes\\Directory\\shell\\YDeck", "Software\\Classes\\Directory\\Background\\shell\\YDeck")
    private val root = WinReg.HKEY_CURRENT_USER

    val available get() = System.getProperty("jpackage.app-path") != null

    fun set(on: Boolean) {
        val exe = System.getProperty("jpackage.app-path") ?: return
        runCatching {
            KEYS.forEach { k ->
                if (on) {
                    Advapi32Util.registryCreateKey(root, "$k\\command")
                    Advapi32Util.registrySetStringValue(root, k, "", tr("Open in YDeck", "פתיחה ב־YDeck"))
                    Advapi32Util.registrySetStringValue(root, k, "Icon", "\"$exe\",0")
                    Advapi32Util.registrySetStringValue(root, "$k\\command", "", "\"$exe\" $ARG \"%V\"")
                } else if (Advapi32Util.registryKeyExists(root, k)) {
                    if (Advapi32Util.registryKeyExists(root, "$k\\command")) Advapi32Util.registryDeleteKey(root, "$k\\command")
                    Advapi32Util.registryDeleteKey(root, k)
                }
            }
        }
    }

    /** The folder after [ARG] in the program's arguments. */
    fun folderIn(args: Array<String>): String? = args.indexOf(ARG).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.trim('"')?.takeIf { File(it).isDirectory }
}

/** One YDeck at a time: a second start hands the running one what it was asked (show the window, or a folder), then exits. */
object SingleInstance {
    private val dir = File(AppDirs.local, "instance").apply { mkdirs() }
    private var lock: FileLock? = null

    /** [onAsk] gets a folder to open, or null for "show the window". */
    fun claim(folder: String?, onAsk: (String?) -> Unit): Boolean {
        val channel = RandomAccessFile(File(dir, "lock"), "rw").channel
        lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock == null) {
            // Written under another name first, so the running app never reads a half-written request.
            val tmp = File(dir, "tmp-${System.nanoTime()}").apply { writeText(folder ?: "show", Charsets.UTF_8) }
            tmp.renameTo(File(dir, "show-${System.nanoTime()}"))
            return false
        }
        dir.listFiles { f -> f.name.startsWith("show-") || f.name.startsWith("tmp-") }?.forEach { it.delete() }
        Thread({ watch(onAsk) }, "ydeck-instance").apply { isDaemon = true; start() }
        return true
    }

    private fun watch(onAsk: (String?) -> Unit) {
        val watcher = FileSystems.getDefault().newWatchService()
        dir.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
        while (true) {
            val key = watcher.take()
            val shows = key.pollEvents().map { it.context().toString() }.filter { it.startsWith("show-") }.distinct()
            shows.forEach { name ->
                val f = File(dir, name)
                val text = runCatching { f.readText(Charsets.UTF_8).trim() }.getOrNull() ?: return@forEach
                f.delete()
                onAsk(text.takeIf { it != "show" && File(it).isDirectory })
            }
            key.reset()
        }
    }
}
