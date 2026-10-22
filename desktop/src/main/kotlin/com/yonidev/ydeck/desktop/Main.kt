package com.yonidev.ydeck.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Point
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File

/** Something asked of the running app: show its window ([folder] null), or open a folder from Explorer's menu. */
private data class Ask(val n: Int, val folder: String?)

fun main(args: Array<String>) {
    // Run by the installer as YDeck is removed: take back what it put outside its folder, and nothing else.
    if (Uninstall.ARG in args) { Uninstall.cleanUp(); return }
    val folder = ExplorerMenu.folderIn(args)
    val background = Autostart.BACKGROUND_ARG in args || folder != null
    val asks = MutableStateFlow(Ask(if (folder != null) 1 else 0, folder))
    if (!SingleInstance.claim(folder) { f -> asks.value = Ask(asks.value.n + 1, f) }) return
    Hotkey.start()
    FolderStrip.start()
    ClaudeLink.start()
    PhoneLink.start()
    UsageStats.start()
    Updates.start()
    // A session that ends in a project's folder: its .ydeck files and its git state, at once rather than in 20 seconds.
    ClaudeLink.onStop = { cwd ->
        Thread {
            ProjectFiles.sync()
            if (Settings.projectAt(File(cwd)) != null) {
                val all = Settings.state.value.local.values.mapNotNull { it.folder }
                kotlinx.coroutines.runBlocking { Git.refresh(all); Pulses.refresh(all) }
            }
        }.start()
    }
    // The plugin's files follow this install (the curl path, the key); Claude Code reads them at each session start.
    // and Claude Code's copy of the plugin is brought up to date when it isn't.
    if (ClaudePlugin.installed) Thread { runCatching { ClaudePlugin.refresh() } }.start()
    // Folder icons whose files an update took away come back.
    Thread { runCatching { FolderTag.repair(Repo.deck.value, Settings.state.value) } }.start()
    // An agent's state on its folder's icon.
    FolderBadges.start()
    // A project folder renamed is followed to its new name.
    FolderMoves.start()
    if (ExplorerMenu.available) Thread { ExplorerMenu.set(Settings.state.value.explorerMenu) }.start()
    application { App(background, asks) }
}

@Composable
private fun ApplicationScope.App(background: Boolean, asks: MutableStateFlow<Ask>) {
    val deck by Repo.deck.collectAsState()
    val settings by Settings.state.collectAsState()
    val dark = isDark()
    // From the Start menu: the window (with the logo). From Windows startup: only the widgets and the tray icon.
    var mainOpen by remember { mutableStateOf(!background || deck.projects.isEmpty()) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var palette by remember { mutableStateOf(false) }

    fun open(route: Screen? = null) { if (route != null) Nav.go(route) else Nav.front.value++; mainOpen = true }

    val ask by asks.collectAsState()
    LaunchedEffect(ask) {
        val f = ask.folder
        when {
            ask.n == 0 -> {}
            f == null -> open()
            else -> {
                // From Explorer's menu: a project's folder opens its quick look by the pointer; another folder, a new project.
                val p = Settings.projectAt(File(f))?.let { Repo.deck.value.byId(it.first) }
                if (p != null) {
                    val at = java.awt.MouseInfo.getPointerInfo()?.location
                    panel = Panel.More(WidgetSpec("explorer", p.id, (at?.x ?: 400).toFloat() + Panel.WIDTH / 2, (at?.y ?: 200).toFloat() - 40f, 0f, 0f), p.id)
                } else open(Screen.NewProject(f))
            }
        }
    }
    val hotkey by Hotkey.presses.collectAsState()
    LaunchedEffect(hotkey) { if (hotkey > 0) palette = !palette }
    LaunchedEffect(palette) { if (palette) UsageStats.mark(UsageStats.Feature.SEARCH) }
    // Where every project folder stands (git, Claude Code, a dev server, .env), now and every 20 seconds.
    // Keyed by the folders only: typing a note (also in settings.local) doesn't start a new round.
    val folders = settings.local.values.mapNotNull { it.folder }.distinct().sorted()
    LaunchedEffect(folders) {
        while (true) {
            Git.refresh(folders)
            Git.fetchDue(folders)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProjectFiles.sync() }
            Pulses.refresh(folders)
            kotlinx.coroutines.delay(20_000)
        }
    }
    // A project without a logo takes its site's icon, once (removing it in the details then sticks).
    LaunchedEffect(deck) { Logos.fillFromSites(deck) }

    /** A project's widget; without a project, Now's widget (every project at once). */
    fun addWidget(p: Project?) {
        val screen = Toolkit.getDefaultToolkit().screenSize
        val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
        val n = Settings.state.value.widgets.size
        val x = screen.width / scale - 380f - 24f - n * 24f
        val y = 24f + n * 230f % ((screen.height / scale) - 260f)
        Settings.setWidget(WidgetSpec("w" + System.currentTimeMillis().toString(36), p?.id ?: NOW_WIDGET, x, y, 380f, if (p == null) 300f else 234f))
    }

    // The icon by the clock (TrayMenu.kt), and where its menu was asked for.
    var trayMenu by remember { mutableStateOf<Point?>(null) }
    val tray = remember { AppTray.icon(onOpen = { open() }, onMenu = { trayMenu = it }) }
    DisposableEffect(Unit) { onDispose { AppTray.remove(tray) } }
    fun notify(title: String, text: String) { tray?.displayMessage(title, text, TrayIcon.MessageType.NONE) }
    // A newer YDeck, told once; a click on the notification opens the window, where the sidebar offers it.
    LaunchedEffect(Unit) {
        Updates.onNew = { r -> notify(tr("YDeck ${r.version} is out", "YDeck ${r.version} יצאה"), tr("Open YDeck to update. It takes a few seconds.", "פותחים את YDeck כדי לעדכן. זה לוקח כמה שניות.")) }
    }

    // Claude Code waiting for the user, or done with a long task, in a project: a Windows notification.
    LaunchedEffect(Unit) {
        ClaudeLink.alerts.collect { a ->
            if (!Settings.state.value.claudeAlerts) return@collect
            val p = Settings.projectAt(File(a.cwd))?.let { Repo.deck.value.byId(it.first) } ?: return@collect
            val title = if (a.mode == ClaudeMode.WAITING) tr("${p.name}: Claude needs you", "${p.name}: Claude מחכה לך") else tr("${p.name}: Claude finished", "${p.name}: Claude סיים")
            notify(title, a.detail.ifBlank { " " })
        }
    }
    // The phone opened Claude Code here: always said, so it never happens unseen.
    LaunchedEffect(Unit) {
        PhoneLink.opened.collect { name ->
            notify(tr("$name: Claude opened from the phone", "$name: Claude נפתח מהטלפון"), tr("Turn this off in Settings → Phone.", "אפשר לכבות בהגדרות ← טלפון."))
        }
    }

    // The app window keeps its place and size when the language changes; it redraws inside (see AppWindow).
    if (mainOpen) AppWindow(deck, dark, playIntro = !background, onClose = { mainOpen = false }, openPalette = { palette = true }, addWidget = ::addWidget)

    // Everything else redraws in a newly chosen language.
    key(settings.language) {
        trayMenu?.let { at ->
            key(at) {
                TrayMenuWindow(at, listOf(
                    listOf(
                        TrayItem(Glyph.EXTERNAL, tr("Open YDeck", "פתיחת YDeck")) { open() },
                        TrayItem(Glyph.SEARCH, tr("Search", "חיפוש"), Hotkey.LABEL) { palette = true },
                        TrayItem(Glyph.WIDGET, tr("New widget on the desktop", "ווידג'ט חדש על שולחן העבודה")) { addWidget(null) },
                        TrayItem(Glyph.MOON, tr("Close the day", "סגירת היום")) { open(Screen.CloseDay) },
                        TrayItem(Glyph.GEAR, tr("Settings", "הגדרות")) { open(Screen.Settings()) },
                        TrayItem(Glyph.EDIT, tr("Send feedback", "שליחת משוב")) { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(AppInfo.feedback())) } },
                    ),
                    listOf(TrayItem(Glyph.POWER, tr("Quit", "יציאה")) { AppTray.remove(tray); exitApplication() }),
                ), dark, onClose = { trayMenu = null })
            }
        }

        val widgetActions = WidgetActions(
            open = { p, a -> Chrome.open(p, a) },
            more = { spec, p -> panel = Panel.More(spec, p.id) },
            pick = { spec -> panel = Panel.Pick(spec) },
            edit = { p -> open(Screen.Project(p.id)) },
            remove = { spec -> Settings.removeWidget(spec.id) },
            resize = { spec, w, h -> Settings.setWidget(spec.copy(w = w, h = h)) },
            tool = { p, t -> Settings.state.value.localOf(p.id).dir?.let { Tools.launch(t, it, p.name) } },
            run = { p, cmd -> Settings.state.value.localOf(p.id).dir?.let { Tools.run(cmd, it) } },
            drop = { _, p, d -> dropOn(p, d) { open(it) } },
        )
        settings.widgets.forEach { spec -> key(spec.id) { WidgetWindow(spec, deck, dark, widgetActions) } }

        if (settings.companion) {
            val attach by FolderStrip.state.collectAsState()
            FolderStripWindow(attach, deck, dark, StripActions(
                tool = { p, t -> Settings.state.value.localOf(p.id).dir?.let { Tools.launch(t, it, p.name) } },
                open = { p, url -> Chrome.open(p, DeckAction(Catalog.LINK, url, "localhost")) },
                link = { p, a -> Chrome.open(p, a) },
                // The quick look above the strip, centred on it.
                more = { p, a -> panel = Panel.More(WidgetSpec("strip", p.id, a.x + a.w / 2 + Panel.WIDTH / 2 - 6f, a.y + 14f - Panel.MORE_HEIGHT, 0f, 0f), p.id) },
                // Git's sheet, the same way.
                git = { p, a -> panel = Panel.Git(WidgetSpec("strip", p.id, a.x + a.w / 2 + Panel.WIDTH / 2 - 6f, a.y + 14f - Panel.GIT_HEIGHT, 0f, 0f), p.id) },
                edit = { p -> open(Screen.Project(p.id)) },
            ))
        }

        panel?.let { pn ->
            key(pn) {
                PanelWindow(pn, deck, dark, onClose = { panel = null },
                    onPick = { spec, id -> Settings.setWidget(spec.copy(project = id)); panel = null },
                    onEdit = { p -> open(Screen.Project(p.id)) },
                    onGit = { p -> open(Screen.Project(p.id, Tab.GIT)) })
            }
        }

        if (palette) PaletteWindow(deck, settings, dark, onRun = { e -> palette = false; runEntry(e, Settings.state.value) { open(Screen.Project(it.id)) } }, onClose = { palette = false })

    }
}

/** Something dropped on a widget: a folder connects it, files are pinned, a link is added. */
private fun dropOn(p: Project, d: Dropped, open: (Screen) -> Unit) {
    when (d) {
        is Dropped.Files -> {
            d.files.firstOrNull { it.isDirectory }?.let { f -> connectFolder(p, f); open(Screen.Project(p.id)); return }
            val files = d.files.filter { it.isFile }.map { it.path }
            if (files.isEmpty()) return
            val l = Settings.state.value.localOf(p.id)
            Settings.setLocal(p.id, l.copy(files = (l.files + files).distinct()))
        }
        is Dropped.Text -> {
            val url = d.text.trim().takeIf { it.startsWith("http") && !it.contains('\n') } ?: return
            val s = Catalog.detect(url) ?: Catalog.LINK
            Repo.deck.value.byId(p.id)?.takeIf { it.actions.none { a -> a.url == url } }?.let { saveProject(it.copy(actions = it.actions + DeckAction(s, url, s.label))) }
        }
    }
}
