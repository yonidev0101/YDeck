package com.yonidev.ydeck.desktop

import java.io.File
import kotlin.system.exitProcess

/*
 * Uninstalling YDeck leaves nothing behind. As Windows removes it (from Settings › Apps, or from YDeck's own button),
 * the installer first runs YDeck once with [ARG] (desktop/installer/ui.wxf), and it takes back everything it put
 * outside its folder: the Claude Code plugin, "Open in YDeck" on Explorer's menu, the icons on the project folders,
 * starting with Windows. Then whatever an older install left in the install folder and the Start menu goes too.
 * Not on an upgrade. The projects' own files (.ydeck.json, AGENTS.md) are the projects', and stay.
 */
object Uninstall {
    const val ARG = "--uninstall-cleanup"

    /** Everything YDeck put outside its folder, taken back. Each step on its own, so one failing doesn't stop the rest. */
    fun cleanUp() {
        runCatching { Autostart.set(false) }
        runCatching { ExplorerMenu.set(false) }
        runCatching { if (ClaudePlugin.installed) ClaudePlugin.set(false) }
        runCatching {
            Settings.state.value.local.values.filter { it.tagged }.mapNotNull { it.dir }.forEach(FolderTag::remove)
        }
        // The install folder and the Start menu's, once Windows is done with them (an older install's files linger there).
        val local = System.getenv("LOCALAPPDATA") ?: return
        val appData = System.getenv("APPDATA") ?: return
        val folders = listOf(File(local, "YDeck"), File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\YDeck"))
        runCatching {
            ProcessBuilder("cmd.exe", "/d", "/c", "ping -n 21 127.0.0.1 >nul & " + folders.joinToString(" & ") { "rmdir /s /q \"${it.path}\"" })
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }
    }

    /** The installers' upgrade code (desktop/build.gradle.kts): every version of YDeck carries it. */
    private const val UPGRADE_CODE = "{8C3D2F6E-4B71-4F0A-9A52-7D1E6B3C9F24}"

    /** The product code of the installed YDeck (the newest, if an old install still lingers), or null (a dev build). */
    private fun product(): String? = runCatching {
        val ps = """${'$'}i = New-Object -ComObject WindowsInstaller.Installer; ${'$'}i.RelatedProducts('$UPGRADE_CODE') | ForEach-Object { """ +
            """try { '{0} {1}' -f ${'$'}_, ${'$'}i.ProductInfo(${'$'}_, 'VersionString') } catch {} }"""
        val p = ProcessBuilder(Ps.exe, "-NoProfile", "-NonInteractive", "-EncodedCommand", Ps.encode(ps)).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.lines().mapNotNull { l -> l.trim().split(' ').takeIf { it.size == 2 && it[0].startsWith("{") } }
            .maxByOrNull { (_, v) -> v.split('.').fold(0L) { n, x -> n * 1000 + (x.toLongOrNull() ?: 0) } }?.get(0)
    }.getOrNull()

    val available: Boolean get() = Autostart.available && product() != null

    /**
     * YDeck's button: Windows' uninstaller, once YDeck has quit (it cleans up as it goes); with [withData], YDeck's own
     * data too (the projects and settings in %APPDATA%\YDeck and %LOCALAPPDATA%\YDeck Data), only if it went through.
     */
    fun start(withData: Boolean): Boolean {
        val cmd = "msiexec.exe /x " + (product() ?: return false)
        val wipe = if (withData) " && rmdir /s /q \"${AppDirs.data.path}\" & rmdir /s /q \"${AppDirs.local.path}\"" else ""
        runCatching {
            ProcessBuilder("cmd.exe", "/d", "/c", "ping -n 3 127.0.0.1 >nul & start \"\" /wait $cmd$wipe")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }.onFailure { return false }
        exitProcess(0)
    }
}
