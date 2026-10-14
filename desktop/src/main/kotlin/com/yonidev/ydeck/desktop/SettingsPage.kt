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
                SettingsSection.CLAUDE -> ClaudeSettings()
                SettingsSection.PHONE -> PhoneSettings()
                SettingsSection.BACKUP -> Backup(actions)
                SettingsSection.STORAGE -> StorageSection(deck)
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
        if (Autostart.available) Setting(tr("Start with Windows", "הפעלה עם Windows")) { Toggle(autostart) { autostart = it; Autostart.set(it) } }
    }
    Column { Group(tr("Work", "עבודה")) {
        Setting(tr("Search, from anywhere", "חיפוש, מכל מקום")) {
            // Keys read left to right in every language.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Hotkey.LABEL.split("+").forEach { Key(it) } }
            }
        }
        Setting(tr("Terminal", "טרמינל")) {
            val current = Shells.available.firstOrNull { it.id == s.shell } ?: Shell.CMD
            Segmented(Shells.available.map { it to it.label }, current) { sh -> Settings.update { it.copy(shell = sh.id) } }
        }
        Setting(tr("Check for new commits on the server", "בדיקת קומיטים חדשים בשרת")) { Toggle(s.gitFetch) { on -> Settings.update { it.copy(gitFetch = on) } } }
    }
    Note(tr("Every 10 minutes, quietly (git fetch): nothing in your folders changes, and \"to pull\" stays true.",
        "כל 10 דקות, בשקט (git fetch): שום דבר בתיקיות לא משתנה, ו״חדשים בשרת״ נשאר מדויק."))
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
                val p = deck.byId(w.project) ?: deck.projects.firstOrNull()
                Item {
                    if (p != null) Logo(p, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p?.name ?: "—", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
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
            Setting(tr("The project's strip in its folder", "פס הפרויקט בתוך התיקייה")) { Toggle(s.companion) { on -> Settings.update { it.copy(companion = on) } } }
            Setting(tr("Agents at work, on the folder's icon", "סוכן בעבודה, על אייקון התיקייה")) { Toggle(s.folderStatus) { on -> Settings.update { it.copy(folderStatus = on) } } }
            Setting(tr("Uncommitted changes, on the folder's icon", "שינויים שעוד לא בקומיט, על אייקון התיקייה")) { Toggle(s.folderGit) { on -> Settings.update { it.copy(folderGit = on) } } }
            if (ExplorerMenu.available) Setting(tr("“Open in YDeck” on right-click", "״פתיחה ב־YDeck״ בלחיצה ימנית")) {
                Toggle(s.explorerMenu) { on -> Settings.update { it.copy(explorerMenu = on) }; Thread { ExplorerMenu.set(on) }.start() }
            }
        }
        Note(tr("The strip sits at the bottom of a File Explorer window while it shows a project's folder. A folder with its project's logo shows a dot while an agent works in it (indigo) or waits for you (amber); the logo is set in each project's details.",
            "הפס יושב בתחתית חלון הסייר כשהוא מציג תיקיית פרויקט. תיקייה עם הלוגו של הפרויקט מראה נקודה כשסוכן עובד בה (אינדיגו) או מחכה לך (כתום). הלוגו נקבע בפרטים של כל פרויקט."))
    }
}

@Composable
private fun ClaudeSettings() {
    val c = LocalDeck.current
    val s by Settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(ClaudePlugin.installed) }
    var busy by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val found = Tools.claude != null
    Column {
        Group {
            Setting(tr("Connect to Claude Code", "חיבור ל־Claude Code"), value = when {
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
            if (installed) Setting(tr("Notify me when Claude needs me", "התראה כש־Claude מחכה לי")) { Toggle(s.claudeAlerts) { on -> Settings.update { it.copy(claudeAlerts = on) } } }
            if (installed) Setting(tr("Write a handoff when a session ends", "סיכום מעבר בסוף כל סשן")) { Toggle(s.handoffs) { on -> Settings.update { it.copy(handoffs = on) } } }
        }
        error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        Note(tr("Connected, YDeck shows when Claude works, needs you or is done, and each new conversation starts knowing the project. Claude keeps its summary, next step, links and releases in .ydeck.json. Set up through Claude Code's own plugin commands.",
            "כשמחוברים, YDeck מראה מתי Claude עובד, מחכה לך או סיים, וכל שיחה חדשה מתחילה כשהוא מכיר את הפרויקט. Claude שומר את התקציר, הצעד הבא, הקישורים והגרסאות ב־.ydeck.json. ההתקנה דרך הפקודות של Claude Code עצמו."))
    }
}

@Composable
private fun Backup(actions: AppActions) {
    Column {
        Group {
            Setting(tr("Export to a file", "ייצוא לקובץ"), onClick = actions.export)
            Setting(tr("Import from a file", "ייבוא מקובץ"), onClick = actions.import)
        }
        Note(tr("Every project and logo in one file, the same one YDeck for Android reads. An import replaces the projects here.",
            "כל הפרויקטים והלוגואים בקובץ אחד, אותו קובץ ש־YDeck לאנדרואיד קורא. ייבוא מחליף את הפרויקטים שכאן."))
    }
}

@Composable
private fun About() {
    val c = LocalDeck.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(painterResource("icons/app.png"), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        Text("YDeck", color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans, letterSpacing = (-0.8).sp)
        PageTitle(tr("Nothing left open.", "שום דבר לא נשאר פתוח."), 26)
        Text(tr("Your agents work. YDeck keeps track of what they did, what changed, and what still needs you, across every project.",
            "הסוכנים עובדים. YDeck זוכר מה הם עשו, מה השתנה ומה עוד מחכה לך, בכל הפרויקטים."),
            color = c.muted, fontSize = 15.sp, lineHeight = 23.sp, fontFamily = PlexSans, modifier = Modifier.widthIn(max = 520.dp))
        Mono(tr("Version ${AppInfo.version} for Windows", "גרסה ⁦${AppInfo.version}⁩ ל־Windows"))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(tr("Website", "אתר"), Tone.SOFT, Glyph.GLOBE) { runCatching { Desktop.getDesktop().browse(URI(AppInfo.SITE)) } }
            Spacer(Modifier.width(4.dp))
            Text(tr("Made by YoniDev", "נבנה ע״י YoniDev"), color = c.faint, fontSize = 13.sp, fontFamily = PlexSans)
        }
    }
}
