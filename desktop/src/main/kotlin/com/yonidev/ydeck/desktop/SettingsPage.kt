package com.yonidev.ydeck.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import java.util.Properties

/* Settings, one section at a time: a title, then grouped rows, one line each. */

object AppInfo {
    val version: String by lazy {
        runCatching { Properties().apply { load(AppInfo::class.java.getResourceAsStream("/build.properties")) }.getProperty("version") }.getOrNull() ?: "dev"
    }
    const val SITE = "https://ydeck-app.pages.dev"
    const val REPO = "https://github.com/yonidev0101/YDeck"
}

@Composable
fun SettingsPage(section: SettingsSection, deck: Deck, dark: Boolean, actions: AppActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 56.dp, end = 56.dp, top = 40.dp, bottom = 40.dp)) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (section != SettingsSection.ABOUT) PageTitle(section.title, 34)
            when (section) {
                SettingsSection.GENERAL -> General()
                SettingsSection.WIDGETS -> Widgets(deck, actions)
                SettingsSection.EXPLORER -> Explorer()
                SettingsSection.CLAUDE -> ClaudeSettings(deck)
                SettingsSection.PHONE -> PhoneSettings()
                SettingsSection.DATA -> { Backup(actions); StorageSection(deck) }
                SettingsSection.ABOUT -> About()
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = LocalDeck.current.faint, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun General() {
    val s by Settings.state.collectAsState()
    var autostart by remember { mutableStateOf(Autostart.enabled) }
    Group(tr("App", "אפליקציה")) {
        Setting(tr("Language", "שפה")) {
            Segmented(Lang.entries.map { it to it.label }, I18n.lang) { l ->
                Settings.update { it.copy(language = l.id) }
                // Explorer's menu speaks the app's language too.
                if (Settings.state.value.explorerMenu) Thread { ExplorerMenu.set(true) }.start()
            }
        }
        Setting(tr("Appearance", "מראה")) {
            Segmented(listOf("system" to "Windows", "light" to tr("Light", "בהיר"), "dark" to tr("Dark", "כהה")), s.appearance) { a -> Settings.update { it.copy(appearance = a) } }
        }
        if (Autostart.available) Setting(tr("Start with Windows", "הפעלה עם Windows"), hint = tr("YDeck opens quietly in the background when the PC starts.", "YDeck נפתח ברקע, בשקט, כשהמחשב עולה.")) { Toggle(autostart) { autostart = it; Autostart.set(it) } }
    }
    Column { Group(tr("Work", "עבודה")) {
        Setting(tr("Search, from anywhere", "חיפוש, מכל מקום"), hint = tr("Opens a project or a link from any window.", "פותח פרויקט או קישור מכל חלון.")) {
            // Keys read left to right in every language.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Hotkey.LABEL.split("+").forEach { Key(it) } }
            }
        }
        Setting(tr("Terminal", "טרמינל"), hint = tr("Where the projects' commands run.", "איפה רצות הפקודות של הפרויקטים.")) {
            val current = Shells.available.firstOrNull { it.id == s.shell } ?: Shell.CMD
            Segmented(Shells.available.map { it to it.label }, current) { sh -> Settings.update { it.copy(shell = sh.id) } }
        }
        Setting(tr("Website icons", "סמלים של אתרים"), hint = tr("For a link without a built-in mark, only the site's name is sent to Google's and DuckDuckGo's icon services.", "לקישור בלי סמל מובנה, רק שם האתר נשלח לשירות הסמלים של Google ושל DuckDuckGo.")) {
            Toggle(s.siteIcons) { on -> Settings.update { it.copy(siteIcons = on) } }
        }
        Setting(tr("Check for new commits on the server", "בדיקת קומיטים חדשים בשרת"), hint = tr("Every 10 minutes, quietly (git fetch). Nothing in your folders changes.", "כל 10 דקות, בשקט (git fetch). שום דבר בתיקיות לא משתנה.")) { Toggle(s.gitFetch) { on -> Settings.update { it.copy(gitFetch = on) } } }
    }
    }
}

@Composable
private fun Key(text: String) {
    val c = LocalDeck.current
    Text(text, color = c.fg, fontSize = 12.sp, fontFamily = PlexMono,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.line, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
}

@Composable
private fun Widgets(deck: Deck, actions: AppActions) {
    val c = LocalDeck.current
    val s by Settings.state.collectAsState()
    Column {
        Group {
            s.widgets.forEach { w ->
                val now = w.project == NOW_WIDGET
                val p = if (now) null else deck.byId(w.project) ?: deck.projects.firstOrNull()
                Item {
                    if (p != null) Logo(p, 28.dp)
                    else if (now) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource("icons/app.png"), null, Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(if (now) tr("All projects", "כל הפרויקטים") else p?.name ?: "—", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                        Text(when {
                            w.h < 96 -> tr("One row", "שורה אחת")
                            w.w < 260 -> tr("Small square", "ריבוע קטן")
                            else -> tr("Wide", "רחב")
                        }, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                    }
                    IconButton(Glyph.TRASH, tr("Remove", "הסרה"), 32.dp, c.muted, filled = false) { Settings.removeWidget(w.id) }
                }
            }
            Item({ actions.addWidget(null) }) {
                GlyphIcon(Glyph.PLUS, 18.dp, c.accentText)
                Text(tr("Add a widget to the desktop", "ווידג'ט חדש על שולחן העבודה"), color = c.accentText, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
            }
        }
        Note(tr("Drag a widget anywhere and resize it from its corner. Right-click it to change its project or size.",
            "גוררים ווידג'ט לכל מקום ומשנים גודל מהפינה. בלחיצה ימנית מחליפים פרויקט או גודל."))
    }
}

@Composable
private fun Explorer() {
    val s by Settings.state.collectAsState()
    Column {
        Group {
            Setting(tr("The project's strip in its folder", "פס הפרויקט בתוך התיקייה"), hint = tr("A strip at the bottom of File Explorer while it shows a project's folder.", "פס בתחתית הסייר כשהוא מציג תיקיית פרויקט.")) { Toggle(s.companion) { on -> Settings.update { it.copy(companion = on) } } }
            Setting(tr("Agents at work, on the folder's icon", "סוכן בעבודה, על אייקון התיקייה"), hint = tr("A dot on the project's folder: indigo while an agent works, amber while it waits for you.", "נקודה על תיקיית הפרויקט: אינדיגו כשסוכן עובד, כתומה כשהוא מחכה לך.")) { Toggle(s.folderStatus) { on -> Settings.update { it.copy(folderStatus = on) } } }
            Setting(tr("Uncommitted changes, on the folder's icon", "שינויים שעוד לא בקומיט, על אייקון התיקייה"), hint = tr("A small amber dot in the icon's upper corner.", "נקודה כתומה קטנה בפינה העליונה של האייקון.")) { Toggle(s.folderGit) { on -> Settings.update { it.copy(folderGit = on) } } }
            if (ExplorerMenu.available) Setting(tr("“Open in YDeck” on right-click", "״פתיחה ב־YDeck״ בלחיצה ימנית"), hint = tr("On any folder in File Explorer.", "על כל תיקייה בסייר הקבצים.")) {
                Toggle(s.explorerMenu) { on -> Settings.update { it.copy(explorerMenu = on) }; Thread { ExplorerMenu.set(on) }.start() }
            }
        }
        Note(tr("The dots show on folders that carry their project's logo; the logo is set in each project's details.", "הנקודות מופיעות על תיקיות שיש להן את הלוגו של הפרויקט. הלוגו נקבע בפרטים של כל פרויקט."))
    }
}

@Composable
private fun ClaudeSettings(deck: Deck) {
    val c = LocalDeck.current
    val s by Settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(ClaudePlugin.installed) }
    var busy by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val found = Tools.claude != null
    Column {
        Group {
            Setting(tr("Connect to Claude Code", "חיבור ל־Claude Code"), hint = tr("YDeck sees when Claude works, needs you or is done, and each conversation starts knowing the project.", "YDeck רואה מתי Claude עובד, מחכה לך או סיים, וכל שיחה מתחילה כשהוא מכיר את הפרויקט."), value = when {
                !found -> tr("Not installed", "לא מותקן")
                busy == true -> tr("Connecting…", "מתחבר…")
                busy == false -> tr("Disconnecting…", "מנתק…")
                installed -> tr("Connected", "מחובר")
                else -> null
            }) {
                Toggle(installed, enabled = found && busy == null) { on ->
                    busy = on; error = null
                    scope.launch(Dispatchers.IO) { error = ClaudePlugin.set(on); installed = ClaudePlugin.installed; busy = null }
                }
            }
            if (installed) Setting(tr("Notify me when Claude needs me", "התראה כש־Claude מחכה לי"), hint = tr("A Windows notification when it waits for an answer, or ends a long task.", "התראה של Windows כשהוא מחכה לתשובה, או מסיים משימה ארוכה.")) { Toggle(s.claudeAlerts) { on -> Settings.update { it.copy(claudeAlerts = on) } } }
            if (installed) Setting(tr("Sum up each session when it ends", "סיכום בסוף כל סשן"), hint = tr("What was done and the next step, on the project's overview.", "מה נעשה ומה הצעד הבא, במסך הסקירה של הפרויקט.")) { Toggle(s.handoffs) { on -> Settings.update { it.copy(handoffs = on) } } }
            if (installed) Setting(tr("Claude updates files in the repository", "Claude מעדכן קבצים בריפו"), hint = tr("The project's .ydeck.json (summary, decisions, links, releases), committed with the code, and YDeck's block in an AGENTS.md that's already there. Off for work and team repositories: only the personal .ydeck.local.json is written, kept out of git.",
                "‏.ydeck.json של הפרויקט (תקציר, החלטות, קישורים, גרסאות), שנכנס ל־git עם הקוד, והבלוק של YDeck בקובץ AGENTS.md אם כבר קיים. כבוי לריפו של עבודה או צוות: נכתב רק .ydeck.local.json האישי, שלא נכנס ל־git.")) { Toggle(s.repoFiles) { on -> Settings.update { it.copy(repoFiles = on) } } }
        }
        Note(tr("What's installed: a plugin named “ydeck” in Claude Code, added with Claude Code's own commands. Its hooks tell YDeck on this computer (127.0.0.1 only, with a key) when a prompt is sent, when Claude waits or is done, and when a session ends; at a session's start they hand Claude the project's context below. Nothing goes to the internet. Turning this off removes it.",
            "מה מותקן: פלאגין בשם ‏״ydeck״ ב־Claude Code, שמותקן בפקודות של Claude Code עצמו. ה־hooks שלו מודיעים ל־YDeck במחשב הזה בלבד (127.0.0.1, עם מפתח) כשנשלחת בקשה, כש־Claude מחכה או מסיים, וכשסשן נגמר; בתחילת סשן הם נותנים ל־Claude את ההקשר של הפרויקט שלמטה. שום דבר לא יוצא לאינטרנט. כיבוי מסיר אותו."))
        if (installed) Briefing(deck)
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        Note(tr("Claude keeps the project's summary, decisions, links and releases in .ydeck.json, shared through the repository.", "Claude שומר את התקציר, ההחלטות, הקישורים והגרסאות של הפרויקט ב־.ydeck.json, שמשותף דרך הריפו."))
    }
}

/** "What Claude is told": the exact text a session gets at its start, for any project with a folder. */
@Composable
private fun Briefing(deck: Deck) {
    val c = LocalDeck.current
    val s by Settings.state.collectAsState()
    val projects = deck.projects.filter { s.localOf(it.id).dir != null }
    if (projects.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(projects.first().id) }
    Column(Modifier.padding(top = 18.dp)) {
        Group {
            Setting(tr("What Claude is told at a session's start", "מה Claude מקבל בתחילת סשן"), value = if (open) tr("Hide", "הסתרה") else tr("Show", "הצגה"), onClick = { open = !open })
            Setting(tr("Close the project for today: /ydeck:close-day", "סגירת הפרויקט להיום: ‎/ydeck:close-day"),
                hint = tr("In Claude Code, or just ask Claude to close for today. It commits, pushes, stops what runs and leaves the next step.",
                    "ב־Claude Code, או פשוט לבקש מ־Claude לסגור להיום. הוא עושה commit ו־push, עוצר את מה שרץ ומשאיר את הצעד הבא."),
                value = if (command) tr("Hide", "הסתרה") else tr("Show", "הצגה"), onClick = { command = !command })
        }
        if (command) androidx.compose.foundation.text.selection.SelectionContainer {
            Text(ClaudePlugin.closeDay(), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = PlexMono,
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(16.dp))
        }
        if (open) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                projects.take(8).forEach { p -> Chip(p.name, p.id == pick) { pick = p.id } }
            }
            val p = projects.firstOrNull { it.id == pick } ?: projects.first()
            val text = remember(p, s) { ProjectFiles.briefing(p, s.localOf(p.id).dir!!) }
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = PlexMono,
                    modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(16.dp))
            }
        }
    }
}

@Composable
private fun Backup(actions: AppActions) {
    Column {
        Group(tr("Backup", "גיבוי")) {
            Setting(tr("Export to a file", "ייצוא לקובץ"), onClick = actions.export, hint = tr("Every project and logo in one file.", "כל הפרויקטים והלוגואים בקובץ אחד."))
            Setting(tr("Import from a file", "ייבוא מקובץ"), onClick = actions.import, hint = tr("Replaces the projects here.", "מחליף את הפרויקטים שכאן."))
        }
    }
}

@Composable
private fun About() {
    val c = LocalDeck.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(painterResource("icons/app.png"), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        Text("YDeck", color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans, letterSpacing = (-0.8).sp)
        PageTitle(tr("Nothing left open.", "שום דבר לא נשאר פתוח."), 26)
        Text(tr("Every project on one screen: what Claude did, what waits for you, and what isn't saved.",
            "כל הפרויקטים במסך אחד: מה Claude עשה, מה מחכה לך, ומה לא נשמר."),
            color = c.muted, fontSize = 15.sp, lineHeight = 23.sp, fontFamily = PlexSans, modifier = Modifier.widthIn(max = 520.dp))
        // Plain text, not monospace: a Hebrew line in a left-to-right face turns around.
        Text(tr("Version ${AppInfo.version} for Windows · free and open source", "גרסה ${AppInfo.version} ל־Windows · חינם וקוד פתוח"), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(tr("Website", "אתר"), Tone.SOFT, Glyph.GLOBE) { runCatching { Desktop.getDesktop().browse(URI(AppInfo.SITE)) } }
            Pill(tr("Source on GitHub", "הקוד ב־GitHub"), Tone.SOFT, Glyph.CODE) { runCatching { Desktop.getDesktop().browse(URI(AppInfo.REPO)) } }
            Pill(tr("Release notes", "מה חדש"), Tone.SOFT, Glyph.DOC) { runCatching { Desktop.getDesktop().browse(URI(AppInfo.REPO + "/releases")) } }
            Spacer(Modifier.width(4.dp))
            Text(tr("Made by YoniDev", "נבנה ע״י YoniDev"), color = c.faint, fontSize = 13.sp, fontFamily = PlexSans)
        }
        UninstallRow()
    }
}

/** Uninstalling YDeck: what goes and what stays, and YDeck's own data only if asked (see [Uninstall]). */
@Composable
private fun UninstallRow() {
    val c = LocalDeck.current
    // Asking Windows Installer takes a moment: off the UI thread.
    var available by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { available = kotlinx.coroutines.withContext(Dispatchers.IO) { Uninstall.available } }
    if (!available) return
    var ask by remember { mutableStateOf(false) }
    var withData by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Spacer(Modifier.height(18.dp))
    TextAction(tr("Uninstall YDeck…", "הסרת YDeck…")) { ask = true }
    if (failed) Text(tr("Windows' uninstaller didn't open. Uninstall YDeck from Settings › Apps.", "מסיר ההתקנה של Windows לא נפתח. אפשר להסיר את YDeck מהגדרות Windows ← אפליקציות."),
        color = c.danger, fontSize = 13.sp, fontFamily = PlexSans)
    if (ask) androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(tr("Uninstall YDeck?", "להסיר את YDeck?"), fontFamily = PlexSans) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("YDeck takes back everything it added: the plugin in Claude Code, “Open in YDeck” on the right-click menu, the icons on your project folders, and starting with Windows. Then Windows' uninstaller opens.",
                    "YDeck יסיר את כל מה שהוסיף: הפלאגין ב־Claude Code, ״פתיחה ב־YDeck״ בלחיצה ימנית, האייקונים על תיקיות הפרויקטים, וההפעלה עם Windows. אחר כך ייפתח מסיר ההתקנה של Windows."),
                    fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
                Text(tr("Your code, your folders and the projects' .ydeck files stay as they are.", "הקוד, התיקיות וקובצי ה־.ydeck שבפרויקטים נשארים כמו שהם."),
                    color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Toggle(withData) { withData = it }
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Also delete YDeck's data (the projects list and settings)", "למחוק גם את הנתונים של YDeck (רשימת הפרויקטים וההגדרות)"), fontSize = 13.sp, fontFamily = PlexSans)
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ ask = false; failed = !Uninstall.start(withData) }) { Text(tr("Uninstall", "הסרה"), color = c.danger, fontFamily = PlexSans) } },
        dismissButton = { androidx.compose.material3.TextButton({ ask = false }) { Text(tr("Cancel", "ביטול"), color = c.fg, fontFamily = PlexSans) } },
    )
}
