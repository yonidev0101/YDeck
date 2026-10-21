package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/* A project's details (its brand and its folder), and a new project. Changes save as they're made. */

private val SWATCHES = listOf(0xFF0B0B0C, 0xFF3D35E0, 0xFF1A6FDB, 0xFF0EA5E9, 0xFF00A35B, 0xFF16A34A, 0xFFF59E0B, 0xFFFF5A36, 0xFFE11D48, 0xFFDB2777, 0xFF7C3AED, 0xFF6E6E73)
    .map { it.toInt() }

/**
 * Connects [folder] to [p] and brings in what it tells (links, values, name, kind, commands, the logo); the site's
 * icon is looked for in the background when the folder has no logo. Returns what was found, in words.
 */
fun connectFolder(p: Project, folder: File): String {
    val found = Scanner.scan(folder)
    var next = Scanner.merge(p, found)
    if (next.name.isBlank()) next = next.copy(name = folder.name)
    if (next.mono.isBlank()) next = next.copy(mono = next.name.take(1).uppercase())
    if (next.logo == null && found.logo != null) next = next.copy(logo = Logos.fromFile(next.id, found.logo))
    saveProject(next)
    val l = Settings.state.value.localOf(p.id)
    saveLocal(next, l.copy(folder = folder.path, tagged = l.tagged || l.folder == null, commands = l.commands.ifEmpty { found.commands }))
    if (next.logo == null) {
        val id = next.id
        Thread {
            val url = Repo.deck.value.byId(id)?.let(Logos::siteOf) ?: return@Thread
            val rel = Logos.fromSite(id, url) ?: return@Thread
            Repo.deck.value.byId(id)?.takeIf { it.logo == null }?.let { saveProject(it.copy(logo = rel)) }
        }.start()
    }
    return found.summary()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsPage(p: Project, dark: Boolean, actions: AppActions) {
    val c = LocalDeck.current
    val local = Settings.state.collectAsState().value.localOf(p.id)
    var name by remember(p.id) { mutableStateOf(p.name) }
    var mono by remember(p.id) { mutableStateOf(p.mono) }
    var kind by remember(p.id) { mutableStateOf(p.kind) }
    var hex by remember(p.id) { mutableStateOf(String.format("#%06X", p.color and 0xFFFFFF)) }
    var note by remember(p.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 28.dp)) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(Glyph.BACK, tr("Back", "חזרה")) { Nav.set(Screen.Project(p.id)) }
                Spacer(Modifier.width(14.dp))
                PageTitle(tr("Details", "פרטים"), 30)
            }

            // On the desktop: the widget as it looks with these details.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(
                Brush.linearGradient(if (dark) listOf(Color(0xFF2A2560), Color(0xFF14121F), Color(0xFF3A2320)) else listOf(Color(0xFFD9D5FF), Color(0xFFEEEDF6), Color(0xFFFFDCCD))),
            ).padding(28.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(380.dp)) { WidgetPreview(p, dark, local) }
            }

            FillCard(p, local, dark)

            Column {
                GroupLabel(tr("Brand", "מיתוג"))
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Logo(p, 72.dp)
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextAction(if (p.logo == null) tr("Choose a logo file", "בחירת לוגו מקובץ") else tr("Replace the logo", "החלפת הלוגו"), accent = true) {
                                actions.chooseLogo(p.id)?.let { saveProject(p.copy(logo = it)) }
                            }
                            if (p.logo != null) TextAction(tr("Use the letter instead", "אות במקום לוגו")) { saveProject(p.copy(logo = null)) }
                            else Logos.siteOf(p)?.let { url ->
                                TextAction(tr("From the site (${Links.host(url)})", "מהאתר (${Links.host(url)})"), accent = true) {
                                    note = tr("Looking for a logo…", "מחפש לוגו…")
                                    scope.launch {
                                        val rel = withContext(Dispatchers.IO) { Logos.fromSite(p.id, url) }
                                        if (rel != null) Repo.deck.value.byId(p.id)?.let { saveProject(it.copy(logo = rel)) }
                                        note = if (rel == null) tr("The site has no logo big enough", "לא נמצא באתר לוגו בגודל מספיק") else null
                                    }
                                }
                            }
                            note?.let { Text(it, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(horizontal = 10.dp)) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Field(tr("Name", "שם"), name, { name = it; if (it.isNotBlank()) saveProject(p.copy(name = it.trim())) }, modifier = Modifier.weight(2f))
                        Field(tr("Letter", "אות"), mono, { mono = it.take(2); saveProject(p.copy(mono = mono)) }, modifier = Modifier.weight(0.7f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Field(tr("Kind", "סוג"), kind, { kind = it; saveProject(p.copy(kind = it)) }, tr("Android app, website…", "אפליקציית Android, אתר…"))
                    Spacer(Modifier.height(16.dp))
                    Text(tr("Colour", "צבע"), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.padding(bottom = 10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SWATCHES.forEach { s ->
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(s))
                                .then(if (p.color == s) Modifier.border(3.dp, c.bg, CircleShape).border(5.dp, Color(s).copy(alpha = 0.4f), CircleShape) else Modifier)
                                .clickable { saveProject(p.copy(color = s, colorTemporary = false)); hex = String.format("#%06X", s and 0xFFFFFF) })
                        }
                        Box(Modifier.width(120.dp)) { Field(null, hex, { t -> hex = t; DeckJson.parseColor(t)?.let { saveProject(p.copy(color = it)) } }, ltr = true) }
                    }
                }
            }

            Column {
                GroupLabel(tr("Folder", "תיקייה"))
                val folder = local.folder
                if (folder == null) Card {
                    Text(tr("Connect the code folder: YDeck fills in the repository, the consoles, the commands and the logo.", "מחברים את תיקיית הקוד, ו־YDeck ממלא את הריפו, הקונסולות, הפקודות והלוגו."),
                        color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
                    Spacer(Modifier.height(14.dp))
                    Pill(tr("Choose a folder", "בחירת תיקייה"), Tone.PRIMARY, Glyph.FOLDER) { actions.chooseFolder()?.let { note = connectFolder(p, it) } }
                } else Group {
                    Item({ Tools.launch(Tools.explorer, File(folder), p.name) }) {
                        GlyphIcon(Glyph.FOLDER, 18.dp, c.muted)
                        Mono(folder, c.fg, 13, Modifier.weight(1f))
                        if (!File(folder).isDirectory) Text(tr("Not found", "לא נמצאה"), color = c.danger, fontSize = 12.sp, fontFamily = PlexSans)
                    }
                    Setting(tr("Its logo on the folder in Explorer", "הלוגו על התיקייה בסייר")) { Toggle(local.tagged) { saveLocal(p, local.copy(tagged = it)) } }
                    Item {
                        TextAction(tr("Scan again", "סריקה מחדש"), accent = true) { File(folder).takeIf { it.isDirectory }?.let { note = connectFolder(p, it) } }
                        TextAction(tr("Change", "החלפה")) { actions.chooseFolder()?.let { note = connectFolder(p, it) } }
                        Spacer(Modifier.weight(1f))
                        TextAction(tr("Disconnect", "ניתוק")) { saveLocal(p, local.copy(folder = null, tagged = false)) }
                    }
                }
                note?.takeIf { it.isNotBlank() && folder != null }?.let {
                    Text(tr("From the folder: $it", "מהתיקייה: $it"), color = c.accentText, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                }
            }

            Group {
                Item({ actions.delete(p) }) {
                    GlyphIcon(Glyph.TRASH, 18.dp, c.danger)
                    Text(tr("Delete project", "מחיקת הפרויקט"), color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** A new project: from its code folder (the usual way), or empty. A folder handed in starts it at once. */
@Composable
fun NewProjectPage(folder: String?, actions: AppActions) {
    val c = LocalDeck.current
    LaunchedEffect(folder) {
        val f = folder?.let(::File)?.takeIf { it.isDirectory } ?: return@LaunchedEffect
        Settings.projectAt(f)?.let { Nav.set(Screen.Project(it.first)); return@LaunchedEffect }
        val p = Project.blank()
        withContext(Dispatchers.IO) { connectFolder(p, f) }
        Nav.set(Screen.Project(p.id))
    }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 40.dp)) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PageTitle(tr("New project", "פרויקט חדש"), 34)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val stroke = c.line
                Column(Modifier.weight(1.2f).clip(RoundedCornerShape(20.dp)).background(c.paper).drawBehind {
                    drawRoundRect(stroke, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))), cornerRadius = CornerRadius(20.dp.toPx()))
                }.padding(26.dp)) {
                    GlyphIcon(Glyph.FOLDER, 28.dp, c.accentText)
                    Spacer(Modifier.height(16.dp))
                    Text(tr("From a code folder", "מתיקיית קוד"), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Drop it anywhere on this window, or choose it. YDeck reads the repository, the consoles, the package name, the commands and the logo. Read only.",
                        "גוררים אותה לכל מקום בחלון, או בוחרים. YDeck קורא את הריפו, הקונסולות, שם החבילה, הפקודות והלוגו. קריאה בלבד."),
                        color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
                    Spacer(Modifier.height(20.dp))
                    Pill(tr("Choose a folder", "בחירת תיקייה"), Tone.PRIMARY, Glyph.FOLDER) { actions.chooseFolder()?.let { Nav.set(Screen.NewProject(it.path)) } }
                }
                Card(Modifier.weight(1f)) {
                    Text(tr("Start empty", "פרויקט ריק"), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Name it now, add links and a folder later.", "נותנים שם עכשיו, ומוסיפים קישורים ותיקייה אחר כך."), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
                    Spacer(Modifier.height(16.dp))
                    Field(null, name, { name = it }, tr("Project name", "שם הפרויקט"))
                    Spacer(Modifier.height(14.dp))
                    Pill(tr("Create", "יצירה"), Tone.DARK, enabled = name.isNotBlank()) {
                        val p = Project.blank().copy(name = name.trim(), mono = name.trim().take(1).uppercase())
                        saveProject(p)
                        Nav.set(Screen.Project(p.id))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(tr("Restore a backup", "שחזור מגיבוי"), accent = true, onClick = actions.import)
                TextAction(tr("Try a sample project", "פרויקט לדוגמה")) {
                    val nova = Project.sample()
                    saveProject(nova)
                    Nav.set(Screen.Project(nova.id))
                }
            }
        }
    }
}
