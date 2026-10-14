package com.yonidev.ydeck.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.FileDialog
import java.io.File
import java.time.LocalDate

/*
 * The app window: a sidebar of projects (or of settings), and the page beside it. Anything in the app can send it
 * somewhere ([Nav.go]): a widget to a project, the tray to settings, Explorer to a new project from a folder.
 */

enum class Tab { OVERVIEW, GIT, LINKS, FILES, COMMANDS }

enum class SettingsSection { GENERAL, WIDGETS, EXPLORER, CLAUDE, PHONE, DATA, ABOUT }

sealed class Screen {
    data class Project(val id: String, val tab: Tab = Tab.OVERVIEW) : Screen()
    data class Details(val id: String) : Screen()
    /** [folder]: a folder dropped or chosen, to start from. */
    data class NewProject(val folder: String? = null) : Screen()
    data class Settings(val section: SettingsSection = SettingsSection.GENERAL) : Screen()
    data object Welcome : Screen()
    /** Every project's open loops. */
    data object Loops : Screen()
    /** Closing the day: everything open, closed in one go. */
    data object CloseDay : Screen()
}

object Nav {
    private val _route = MutableStateFlow<Screen?>(null)
    val route: StateFlow<Screen?> = _route
    /** Bumps each time the window is asked to come to the front. */
    val front = MutableStateFlow(0)

    fun go(r: Screen) { _route.value = r; front.value++ }
    fun set(r: Screen) { _route.value = r }
}

/** Where the window opens: the first run, else the first project, else a new one. */
fun homeRoute(deck: Deck): Screen = when {
    !Settings.state.value.welcomed && deck.projects.isEmpty() -> Screen.Welcome
    deck.projects.isEmpty() -> Screen.NewProject()
    else -> Screen.Project(deck.projects.first().id)
}

@Composable
fun ApplicationScope.AppWindow(deck: Deck, dark: Boolean, playIntro: Boolean, onClose: () -> Unit, openPalette: () -> Unit, addWidget: (Project?) -> Unit) {
    val state = rememberWindowState(size = DpSize(1200.dp, 780.dp), position = WindowPosition(Alignment.Center))
    val routeNow by Nav.route.collectAsState()
    val route = routeNow ?: homeRoute(deck).also { Nav.set(it) }
    val front by Nav.front.collectAsState()
    var intro by remember { mutableStateOf(playIntro) }
    var confirmDelete by remember { mutableStateOf<Project?>(null) }
    var pendingImport by remember { mutableStateOf<Deck?>(null) }
    // Frameless: YDeck draws its own title bar and window buttons (WindowChrome.kt).
    Window(onCloseRequest = onClose, state = state, title = "YDeck", icon = painterResource("icons/app.png"), undecorated = true) {
        LaunchedEffect(front) { window.toFront(); window.requestFocus() }
        LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(980, 640); dressFrameless(window) }
        // A project that's gone (deleted, replaced by an import) leaves its page.
        LaunchedEffect(deck, route) {
            val id = (route as? Screen.Project)?.id ?: (route as? Screen.Details)?.id
            if (id != null && deck.byId(id) == null) Nav.set(homeRoute(deck))
        }
        val actions = AppActions(
            window = window, openPalette = openPalette, addWidget = addWidget,
            delete = { confirmDelete = it },
            export = { exportTo(window, deck) },
            import = { importFrom(window)?.let { pendingImport = it } },
        )
        DeckTheme(dark) {
            val c = LocalDeck.current
            Box(Modifier.fillMaxSize().background(c.bg).dropTarget { d ->
                // A folder dropped anywhere: its project, or a new project from it.
                (d as? Dropped.Files)?.files?.firstOrNull { it.isDirectory }?.let { f ->
                    val known = Settings.projectAt(f)?.first
                    Nav.go(if (known != null) Screen.Project(known) else Screen.NewProject(f.path))
                }
            }) {
                Column(Modifier.fillMaxSize()) {
                    // The night ground of the first run and the sting runs up through the title bar.
                    val night = intro || route is Screen.Welcome
                    TitleBar(window, state, sidebar = if (night) null else SIDEBAR_WIDTH.dp,
                        fill = if (night) Color(0xFF0B0B0D) else c.bg, sidebarFill = c.paper, ink = if (night) Color(0xFFF4F4F5) else c.fg, onClose = onClose)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AppContent(deck, route, dark, actions)
                    }
                }
                // The sting covers the whole window, its title bar too; a click skips it.
                AnimatedVisibility(intro, enter = EnterTransition.None, exit = fadeOut(tween(400))) { LogoIntro(onDone = { intro = false }) }
                ResizeEdges(window, state)
            }
            confirmDelete?.let { p ->
                AlertDialog(
                    onDismissRequest = { confirmDelete = null },
                    title = { Text(tr("Delete ${p.name}?", "למחוק את ${p.name}?"), fontFamily = PlexSans) },
                    text = { Text(tr("The project and its links leave this PC. Its widgets move to the first project. The folder and its code stay as they are.",
                        "הפרויקט והקישורים שלו יימחקו מהמחשב. הווידג'טים שלו יעברו לפרויקט הראשון. התיקייה והקוד נשארים כמו שהם."), fontFamily = PlexSans) },
                    confirmButton = {
                        TextButton({
                            deleteProject(p)
                            confirmDelete = null
                            Nav.set(homeRoute(Repo.deck.value))
                        }) { Text(tr("Delete", "מחיקה"), color = c.danger, fontFamily = PlexSans) }
                    },
                    dismissButton = { TextButton({ confirmDelete = null }) { Text(tr("Cancel", "ביטול"), color = c.fg, fontFamily = PlexSans) } },
                )
            }
            pendingImport?.let { incoming ->
                AlertDialog(
                    onDismissRequest = { pendingImport = null },
                    title = { Text(tr("Restore from the file?", "לשחזר מהקובץ?"), fontFamily = PlexSans) },
                    text = { Text(tr("The file has ${incoming.projects.size} projects. They replace the ${deck.projects.size} here.",
                        "יש בקובץ ${incoming.projects.size} פרויקטים. הם יחליפו את ${deck.projects.size} הפרויקטים שיש כאן."), fontFamily = PlexSans) },
                    confirmButton = { TextButton({ Repo.save(incoming); pendingImport = null; Nav.set(homeRoute(incoming)) }) { Text(tr("Restore", "שחזור"), color = c.accentText, fontFamily = PlexSans) } },
                    dismissButton = { TextButton({ pendingImport = null }) { Text(tr("Cancel", "ביטול"), color = c.fg, fontFamily = PlexSans) } },
                )
            }
        }
    }
}

/** What the pages can ask the window for. */
class AppActions(
    /** Null when drawn without a window (the renders). */
    val window: ComposeWindow?,
    val openPalette: () -> Unit,
    val addWidget: (Project?) -> Unit,
    val delete: (Project) -> Unit,
    val export: () -> Unit,
    val import: () -> Unit,
) {
    fun chooseFolder(): File? = chooser(window, tr("Choose the project's folder", "בחירת תיקיית הפרויקט"), javax.swing.JFileChooser.DIRECTORIES_ONLY)
    fun chooseFile(): File? = chooser(window, tr("A file to pin", "קובץ לנעיצה"), javax.swing.JFileChooser.FILES_AND_DIRECTORIES)

    /** A logo from an image file (see [Logos]). */
    fun chooseLogo(projectId: String): String? {
        val d = FileDialog(window, tr("Choose a logo", "בחירת לוגו"), FileDialog.LOAD).apply { file = "*.png;*.jpg;*.jpeg;*.webp;*.bmp"; isVisible = true }
        val name = d.file ?: return null
        return Logos.fromFile(projectId, File(d.directory, name))
    }
}

// ── the sidebar ──

const val SIDEBAR_WIDTH = 248

@Composable
private fun Sidebar(deck: Deck, route: Screen, actions: AppActions) {
    val c = LocalDeck.current
    Column(Modifier.width(SIDEBAR_WIDTH.dp).fillMaxHeight().background(c.paper).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource("icons/app.png"), null, Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(9.dp))
            Text("YDeck", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans, letterSpacing = (-0.3).sp)
        }
        if (route is Screen.Settings) SettingsNav(route.section) else ProjectsNav(deck, route, actions)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ProjectsNav(deck: Deck, route: Screen, actions: AppActions) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(10.dp))
        .clickable(onClick = actions.openPalette).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.SEARCH, 15.dp, c.faint)
        Spacer(Modifier.width(8.dp))
        Text(tr("Search", "חיפוש"), color = c.faint, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        Mono(Hotkey.LABEL.replace("+", " "), c.faint, 11)
    }
    Spacer(Modifier.height(12.dp))
    val open = openLoopCount(deck)
    NavRow(Glyph.CHECK, tr("Open loops", "מה פתוח"), route is Screen.Loops || route is Screen.CloseDay, badge = open.takeIf { it > 0 }?.toString()) { Nav.set(Screen.Loops) }
    Spacer(Modifier.height(14.dp))
    GroupLabel(tr("Projects", "פרויקטים"), Modifier.padding(start = 6.dp))
    val selected = (route as? Screen.Project)?.id ?: (route as? Screen.Details)?.id
    LazyColumn(Modifier.weight(1f, fill = false)) {
        itemsIndexed(deck.projects, key = { _, p -> p.id }) { i, p ->
            ContextMenuArea(items = {
                buildList {
                    if (i > 0) add(ContextMenuItem(tr("Move up", "הזזה למעלה")) { moveProject(i, i - 1) })
                    if (i < deck.projects.lastIndex) add(ContextMenuItem(tr("Move down", "הזזה למטה")) { moveProject(i, i + 1) })
                    add(ContextMenuItem(tr("Add a widget to the desktop", "ווידג'ט על שולחן העבודה")) { actions.addWidget(p) })
                    add(ContextMenuItem(tr("Details", "פרטים")) { Nav.set(Screen.Details(p.id)) })
                    add(ContextMenuItem(tr("Delete", "מחיקה")) { actions.delete(p) })
                }
            }) { ProjectNavRow(p, p.id == selected) { Nav.set(Screen.Project(p.id)) } }
        }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { Nav.set(Screen.NewProject()) }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.PLUS, 18.dp, c.muted) }
        Spacer(Modifier.width(10.dp))
        Text(tr("New project", "פרויקט חדש"), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
    }
    Spacer(Modifier.weight(1f))
    NavRow(Glyph.GEAR, tr("Settings", "הגדרות"), false) { Nav.set(Screen.Settings()) }
}

/** A project in the sidebar: its logo, its name, and a dot when Claude Code is busy in it. */
@Composable
private fun ProjectNavRow(p: Project, selected: Boolean, onClick: () -> Unit) {
    val c = LocalDeck.current
    val claude = claudeIn(Settings.state.collectAsState().value.localOf(p.id).folder)
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape).then(if (selected) Modifier.background(c.bg).border(1.dp, c.line, shape) else Modifier)
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(p, 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(p.name, color = c.fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (claude != null && claude.mode != ClaudeMode.DONE) ClaudeDot(claude.mode, c == Dark, 7.dp)
    }
}

@Composable
private fun NavRow(glyph: Glyph, text: String, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape).then(if (selected) Modifier.background(c.bg).border(1.dp, c.line, shape) else Modifier)
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(glyph, 18.dp, if (selected) c.fg else c.muted)
        Spacer(Modifier.width(12.dp))
        Text(text, color = c.fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        if (badge != null) Text(badge, color = c.amberText, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans,
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(CHANGED_DOT.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 1.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SettingsNav(section: SettingsSection) {
    val c = LocalDeck.current
    Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { Nav.set(homeRoute(Repo.deck.value)) }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.BACK, 16.dp, c.muted)
        Spacer(Modifier.width(6.dp))
        Text(tr("Projects", "פרויקטים"), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
    }
    Spacer(Modifier.height(10.dp))
    Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { PageTitle(tr("Settings", "הגדרות"), 26) }
    Spacer(Modifier.height(10.dp))
    SettingsSection.entries.filter { it != SettingsSection.ABOUT }.forEach { s ->
        NavRow(s.glyph, s.title, s == section) { Nav.set(Screen.Settings(s)) }
    }
    Spacer(Modifier.weight(1f))
    NavRow(Glyph.INFO, tr("About YDeck", "אודות YDeck"), section == SettingsSection.ABOUT) { Nav.set(Screen.Settings(SettingsSection.ABOUT)) }
}

val SettingsSection.title: String
    get() = when (this) {
        SettingsSection.GENERAL -> tr("General", "כללי")
        SettingsSection.WIDGETS -> tr("Desktop widgets", "ווידג'טים")
        SettingsSection.EXPLORER -> tr("File Explorer", "סייר הקבצים")
        SettingsSection.CLAUDE -> "Claude Code"
        SettingsSection.PHONE -> tr("Phone", "טלפון")
        SettingsSection.DATA -> tr("Data", "נתונים")
        SettingsSection.ABOUT -> tr("About YDeck", "אודות YDeck")
    }

val SettingsSection.glyph: Glyph
    get() = when (this) {
        SettingsSection.GENERAL -> Glyph.GEAR
        SettingsSection.WIDGETS -> Glyph.WIDGET
        SettingsSection.EXPLORER -> Glyph.FOLDER
        SettingsSection.CLAUDE -> Glyph.CLAUDE
        SettingsSection.PHONE -> Glyph.PHONE
        SettingsSection.DATA -> Glyph.DISK
        SettingsSection.ABOUT -> Glyph.INFO
    }

// ── changes that touch more than one store ──

private fun moveProject(from: Int, to: Int) {
    val list = Repo.deck.value.projects.toMutableList()
    if (from !in list.indices || to !in list.indices) return
    list.add(to, list.removeAt(from))
    Repo.save(Deck(list))
}

/** A project out of YDeck: its entry, its PC settings, and the icon on its folder (the code stays). */
fun deleteProject(p: Project) {
    Repo.save(Deck(Repo.deck.value.projects.filterNot { it.id == p.id }))
    Settings.state.value.localOf(p.id).let { l -> if (l.tagged) l.dir?.let { d -> Thread { FolderTag.remove(d) }.start() } }
    Settings.setLocal(p.id, Local())
}

/** Saves a project's changed entry (by id) in place. */
fun saveProject(p: Project) {
    val list = Repo.deck.value.projects.toMutableList()
    val i = list.indexOfFirst { it.id == p.id }
    if (i >= 0) list[i] = p else list += p
    Repo.save(Deck(list))
}

/** Saves a project's PC-only side, keeping the folder's icon in step with it. */
fun saveLocal(p: Project, next: Local) {
    val before = Settings.state.value.localOf(p.id)
    Settings.setLocal(p.id, next)
    if (before.tagged != next.tagged || before.folder != next.folder) Thread {
        val old = before.dir
        val now = next.dir
        if (before.tagged && old != null && (!next.tagged || old != now)) FolderTag.remove(old)
        if (next.tagged && now != null) FolderTag.apply(now, p)
    }.start()
}

// ── files ──

/** Windows' own folder dialog look (Swing with the system look and feel), in the reading direction. */
private fun chooser(window: ComposeWindow?, title: String, mode: Int): File? {
    runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
    val c = javax.swing.JFileChooser(File(System.getProperty("user.home"), "Desktop")).apply {
        dialogTitle = title
        fileSelectionMode = mode
        applyComponentOrientation(if (I18n.rtl) java.awt.ComponentOrientation.RIGHT_TO_LEFT else java.awt.ComponentOrientation.LEFT_TO_RIGHT)
    }
    return if (c.showDialog(window, tr("Choose", "בחירה")) == javax.swing.JFileChooser.APPROVE_OPTION) c.selectedFile else null
}

private fun exportTo(window: ComposeWindow, deck: Deck) {
    val d = FileDialog(window, tr("Export a backup", "ייצוא גיבוי"), FileDialog.SAVE).apply { file = "ydeck-backup-${LocalDate.now()}.json"; isVisible = true }
    val name = d.file ?: return
    runCatching { File(d.directory, if (name.endsWith(".json")) name else "$name.json").writeText(Repo.exportJson(deck)) }
}

private fun importFrom(window: ComposeWindow): Deck? {
    val d = FileDialog(window, tr("Import a backup", "ייבוא גיבוי"), FileDialog.LOAD).apply { file = "*.json"; isVisible = true }
    val name = d.file ?: return null
    return runCatching { Repo.importJson(File(d.directory, name).readText()) }.getOrNull()?.takeIf { it.projects.isNotEmpty() }
}

/** The window's content for a [route]: the first run, or the sidebar and its page (drawn also for the renders). */
@Composable
fun AppContent(deck: Deck, route: Screen, dark: Boolean, actions: AppActions, welcomeStep: Int = 1) {
    if (route is Screen.Welcome) { WelcomeFlow(deck, actions, welcomeStep); return }
    Row(Modifier.fillMaxSize()) {
        Sidebar(deck, route, actions)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (route) {
                is Screen.Project -> deck.byId(route.id)?.let { ProjectPage(it, route.tab, dark, actions) }
                is Screen.Details -> deck.byId(route.id)?.let { DetailsPage(it, dark, actions) }
                is Screen.NewProject -> NewProjectPage(route.folder, actions)
                is Screen.Settings -> SettingsPage(route.section, deck, dark, actions)
                Screen.Welcome -> {}
                Screen.Loops -> LoopsPage(deck)
                Screen.CloseDay -> CloseDayPage(deck)
            }
        }
    }
}
