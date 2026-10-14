package com.yonidev.ydeck.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/* A project's Links, Files and Commands tabs, and the small forms they open. */

@Composable
fun TabColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 24.dp)) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(28.dp), content = content)
    }
}

@Composable
private fun MarkBox(content: @Composable () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(LocalDeck.current.surface2), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun GroupScope.AddRow(text: String, onClick: () -> Unit) {
    val c = LocalDeck.current
    Item(onClick) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.PLUS, 18.dp, c.accentText) }
        Text(text, color = c.accentText, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
    }
}

// ── links ──

@Composable
fun LinksTab(p: Project, dark: Boolean) {
    val c = LocalDeck.current
    var link by remember(p.id) { mutableStateOf<Int?>(null) }   // -1: a new one
    var copy by remember(p.id) { mutableStateOf<Int?>(null) }
    link?.let { i -> LinkForm(p.actions.getOrNull(i), dark,
        onSave = { a -> saveProject(p.copy(actions = p.actions.toMutableList().apply { if (i >= 0) set(i, a) else add(a) })); link = null },
        onDelete = if (i >= 0) ({ saveProject(p.copy(actions = p.actions.filterIndexed { k, _ -> k != i })); link = null }) else null,
        onClose = { link = null }); return }
    copy?.let { i -> CopyForm(p.copies.getOrNull(i),
        onSave = { item -> saveProject(p.copy(copies = p.copies.toMutableList().apply { if (i >= 0) set(i, item) else add(item) })); copy = null },
        onDelete = if (i >= 0) ({ saveProject(p.copy(copies = p.copies.filterIndexed { k, _ -> k != i })); copy = null }) else null,
        onClose = { copy = null }); return }

    TabColumn {
        Account(p)
        Group(tr("Links", "קישורים")) {
            p.actions.forEachIndexed { i, a ->
                ContextMenuArea(items = {
                    buildList {
                        add(ContextMenuItem(tr("Edit", "עריכה")) { link = i })
                        if (i > 0) add(ContextMenuItem(tr("Move up", "הזזה למעלה")) { saveProject(p.copy(actions = p.actions.toMutableList().apply { add(i - 1, removeAt(i)) })) })
                        if (i < p.actions.lastIndex) add(ContextMenuItem(tr("Move down", "הזזה למטה")) { saveProject(p.copy(actions = p.actions.toMutableList().apply { add(i + 1, removeAt(i)) })) })
                        add(ContextMenuItem(tr("Remove", "הסרה")) { saveProject(p.copy(actions = p.actions.filterIndexed { k, _ -> k != i })) })
                    }
                }) {
                    Item({ Chrome.open(p, a) }) {
                        MarkBox { ServiceMark(a.service, a.faviconHost, 20.dp, dark) }
                        Column(Modifier.weight(1f)) {
                            Text(a.label, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                            Mono(Links.display(a.url))
                        }
                        IconButton(Glyph.EDIT, tr("Edit", "עריכה"), 32.dp, c.muted, filled = false) { link = i }
                    }
                }
            }
            AddRow(tr("Add a link", "הוספת קישור")) { link = -1 }
        }
        Group(tr("Values to copy", "ערכים להעתקה")) {
            p.copies.forEachIndexed { i, item -> CopyRow(item) { copy = i } }
            AddRow(tr("Add a value", "הוספת ערך")) { copy = -1 }
        }
    }
}

/** The project's Google account and the Chrome profile its links open in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Account(p: Project) {
    val c = LocalDeck.current
    val profiles = remember { Chrome.profiles() }
    val chosen = Settings.state.collectAsState().value.profiles[p.id]
    var email by remember(p.id) { mutableStateOf(p.googleEmail.orEmpty()) }
    Column {
        GroupLabel(tr("Account", "חשבון"))
        Card {
            Field(tr("Google account", "חשבון גוגל"), email, { email = it; saveProject(p.copy(googleEmail = it.trim().ifBlank { null })) }, "name@gmail.com", ltr = true)
            Spacer(Modifier.height(16.dp))
            Text(tr("Opens in the Chrome profile", "נפתח בפרופיל Chrome"), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.padding(bottom = 8.dp))
            val auto = profiles.firstOrNull { it.email != null && it.email.equals(email.trim(), true) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (auto != null) tr("Automatic: ${auto.name}", "אוטומטי: ${auto.name}") else tr("Automatic (by the account)", "אוטומטי (לפי החשבון)"), chosen == null) { Settings.setProfile(p.id, null) }
                profiles.forEach { pr -> Chip(pr.email ?: pr.name, chosen == pr.dir) { Settings.setProfile(p.id, pr.dir) } }
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalDeck.current
    Text(text, color = if (selected) c.accentText else c.fg, fontSize = 13.sp, fontFamily = PlexSans,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (selected) c.accentTint else c.surface2)
            .then(if (selected) Modifier.border(1.dp, c.accent, RoundedCornerShape(99.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp))
}

@Composable
private fun GroupScope.CopyRow(item: CopyItem, onEdit: () -> Unit) {
    val c = LocalDeck.current
    var copied by remember { mutableStateOf(false) }
    if (copied) LaunchedEffect(Unit) { delay(1400); copied = false }
    Item({ Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(item.value), null); copied = true }) {
        MarkBox { GlyphIcon(Glyph.COPY, 18.dp, c.fg) }
        Column(Modifier.weight(1f)) {
            Text(item.label.ifBlank { tr("Value", "ערך") }, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
            Mono(item.value, c.fg, 13)
        }
        Text(if (copied) tr("Copied", "הועתק") else tr("Copy", "העתקה"), color = if (copied) CLEAN_DOT else c.accentText, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
        IconButton(Glyph.EDIT, tr("Edit", "עריכה"), 32.dp, c.muted, filled = false, onClick = onEdit)
    }
}

/** A form's top: back, the title, and its actions. */
@Composable
fun FormHeader(title: String, canSave: Boolean, onClose: () -> Unit, onDelete: (() -> Unit)?, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Glyph.BACK, tr("Back", "חזרה"), onClick = onClose)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { PageTitle(title, 26) }
        if (onDelete != null) { TextAction(tr("Delete", "מחיקה"), onClick = onDelete); Spacer(Modifier.width(8.dp)) }
        Pill(tr("Save", "שמירה"), Tone.PRIMARY, enabled = canSave, onClick = onSave)
    }
}

/** Paste a link: the service is recognised from the address, or picked from the catalog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkForm(initial: DeckAction?, dark: Boolean, onSave: (DeckAction) -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit) {
    val c = LocalDeck.current
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var service by remember { mutableStateOf(initial?.service ?: Catalog.LINK) }
    var label by remember { mutableStateOf(initial?.takeIf { it.hasCustomLabel }?.label ?: "") }
    var touched by remember { mutableStateOf(initial != null) }
    var query by remember { mutableStateOf("") }
    val valid = url.trim().let { it.startsWith("https://") || it.startsWith("http://") } && url.trim().length > 10
    TabColumn {
        FormHeader(if (initial == null) tr("New link", "קישור חדש") else tr("Edit link", "עריכת קישור"), valid, onClose, onDelete) {
            onSave(DeckAction(service, url.trim(), label.trim().ifBlank { service.label }))
        }
        Field(tr("Address", "כתובת"), url, { t -> url = t; if (!touched) Catalog.detect(t)?.let { service = it } }, tr("Paste a link", "הדבקת קישור"), ltr = true)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.accentTint).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surface), contentAlignment = Alignment.Center) {
                ServiceMark(service, if (service.path == null) Links.host(url) else null, 22.dp, dark)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(service.label, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans)
                Text(if (touched) service.category else tr("Recognised from the address", "זוהה אוטומטית מהכתובת"), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
            }
        }
        Column {
            Field(tr("Find a service", "חיפוש שירות"), query, { query = it }, "Stripe, Netlify, Notion…", ltr = true)
            val shown = if (query.isBlank()) Catalog.POPULAR else Catalog.all.filter { it.label.contains(query.trim(), true) || it.id.contains(query.trim(), true) }.take(24)
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { s ->
                    val on = s == service
                    Column(Modifier.width(88.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.accentTint else c.surface2)
                        .then(if (on) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable {
                            val wasDefault = url.isBlank() || url == service.defaultUrl
                            service = s; touched = true
                            if (wasDefault && s.defaultUrl.length > 8) url = s.defaultUrl
                        }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ServiceMark(s, null, 24.dp, dark)
                        Spacer(Modifier.height(6.dp))
                        Text(s.short, color = if (on) c.accentText else c.muted, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Field(tr("Name on the button (optional)", "שם על הכפתור (לא חובה)"), label, { label = it }, service.label)
        if (!valid && url.isNotBlank()) Text(tr("The address starts with https://", "הכתובת צריכה להתחיל ב־https://"), color = c.danger, fontSize = 12.sp, fontFamily = PlexSans)
    }
}

@Composable
private fun CopyForm(initial: CopyItem?, onSave: (CopyItem) -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var value by remember { mutableStateOf(initial?.value ?: "") }
    TabColumn {
        FormHeader(if (initial == null) tr("New value", "ערך חדש") else tr("Edit value", "עריכת ערך"), value.isNotBlank(), onClose, onDelete) { onSave(CopyItem(label.trim(), value.trim())) }
        Field(tr("Name", "שם"), label, { label = it }, "Package")
        Field(tr("Value", "ערך"), value, { value = it }, "com.example.app", ltr = true)
    }
}

// ── files ──

/** The project's documents worth reading: the README first, then the other Markdown at the top and in docs/. */
fun docsOf(dir: File): List<File> {
    val top = dir.listFiles { f -> f.isFile && f.extension.equals("md", true) }.orEmpty()
        .sortedWith(compareBy({ !it.name.startsWith("README", true) }, { !it.name.equals("CLAUDE.md", true) }, { it.name.lowercase() }))
    val docs = File(dir, "docs").takeIf { it.isDirectory }?.walkTopDown()?.maxDepth(2)?.filter { it.isFile && it.extension.equals("md", true) }?.sortedBy { it.path.lowercase() }?.toList().orEmpty()
    return (top + docs).take(24)
}

@Composable
fun FilesTab(p: Project, local: Local, actions: AppActions) {
    val c = LocalDeck.current
    val dir = local.dir
    val docs = remember(dir) { dir?.let(::docsOf).orEmpty() }
    var open by remember(p.id) { mutableStateOf(docs.firstOrNull()?.path ?: local.files.firstOrNull { it.endsWith(".md", true) }) }
    Row(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 22.dp)) {
        Column(Modifier.width(230.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (docs.isNotEmpty()) {
                GroupLabel(tr("Read", "לקריאה"))
                docs.forEach { f -> FileRow(Glyph.DOC, f.relativeTo(dir!!).path.replace('\\', '/'), f.path == open) { open = f.path } }
                Spacer(Modifier.height(18.dp))
            }
            GroupLabel(tr("Pinned", "נעוצים"))
            local.files.forEach { f ->
                ContextMenuArea(items = {
                    listOf(
                        ContextMenuItem(tr("Show in the folder", "הצגה בתיקייה")) { Tools.reveal(f) },
                        ContextMenuItem(tr("Unpin", "ביטול הנעיצה")) { saveLocal(p, local.copy(files = local.files - f)) },
                    )
                }) {
                    FileRow(if (File(f).isDirectory) Glyph.FOLDER else Glyph.PIN, File(f).name, f == open) {
                        if (f.endsWith(".md", true)) open = f else Tools.openFile(f)
                    }
                }
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { actions.chooseFile()?.let { saveLocal(p, local.copy(files = (local.files + it.path).distinct())) } }
                .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.PLUS, 16.dp, c.accentText)
                Spacer(Modifier.width(10.dp))
                Text(tr("Pin a file", "נעיצת קובץ"), color = c.accentText, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
            }
            Text(tr("Or drag files here, or onto the widget.", "או גוררים קבצים לכאן, או לווידג'ט."), color = c.faint, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PlexSans,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.width(28.dp))
        val file = open?.let(::File)?.takeIf { it.isFile }
        val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        Column(Modifier.weight(1f).fillMaxHeight().clip(shape).border(1.dp, c.line, shape)
            .dropTarget { d -> (d as? Dropped.Files)?.files?.filter { it.exists() }?.map { it.path }?.takeIf { it.isNotEmpty() }?.let { saveLocal(p, local.copy(files = (local.files + it).distinct())) } }) {
            if (file == null) {
                Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(if (dir == null) tr("Connect the project's folder to read its README here.", "מחברים את תיקיית הפרויקט כדי לקרוא כאן את ה־README.")
                        else tr("No README in this folder. Pin any Markdown file to read it here.", "אין README בתיקייה. אפשר לנעוץ כל קובץ Markdown ולקרוא אותו כאן."),
                        color = c.faint, fontSize = 14.sp, fontFamily = PlexSans)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mono((dir?.let { runCatching { file.relativeTo(it).path }.getOrNull() } ?: file.name) + " · " + tr("edited ${ago(file.lastModified())}", "נערך ${ago(file.lastModified())}"), modifier = Modifier.weight(1f))
                    val editor = dir?.let { Tools.quick(it).getOrNull(1) }?.takeIf { it.kind == ToolKind.EDITOR || it.kind == ToolKind.IDE }
                    TextAction(if (editor != null) tr("Edit in ${editor.label}", "עריכה ב־${editor.label}") else tr("Open", "פתיחה"), accent = true) {
                        if (editor != null) Tools.openIn(editor, file) else Tools.openFile(file.path)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.surface2))
                val text = remember(file.path, file.lastModified()) { runCatching { file.readText() }.getOrDefault("") }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 28.dp)) {
                    MarkdownView(text, Modifier.widthIn(max = 760.dp))
                }
            }
        }
    }
}

@Composable
private fun FileRow(glyph: Glyph, name: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (selected) Modifier.background(c.surface2) else Modifier).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(glyph, 16.dp, if (selected) c.fg else c.muted)
        Spacer(Modifier.width(10.dp))
        Text(name, color = c.fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── commands ──

@Composable
fun CommandsTab(p: Project, local: Local, dark: Boolean) {
    val c = LocalDeck.current
    var editing by remember(p.id) { mutableStateOf<Int?>(null) }
    editing?.let { i -> CommandForm(local.commands.getOrNull(i),
        onSave = { cmd -> saveLocal(p, local.copy(commands = local.commands.toMutableList().apply { if (i >= 0) set(i, cmd) else add(cmd) })); editing = null },
        onDelete = if (i >= 0) ({ saveLocal(p, local.copy(commands = local.commands.filterIndexed { k, _ -> k != i })); editing = null }) else null,
        onClose = { editing = null }); return }
    val dir = local.dir
    TabColumn {
        if (dir == null) {
            Text(tr("Connect the project's folder to run its commands.", "מחברים את תיקיית הפרויקט כדי להריץ את הפקודות שלו."), color = c.faint, fontSize = 14.sp, fontFamily = PlexSans)
            return@TabColumn
        }
        Column {
            Group(tr("Commands", "פקודות")) {
                local.commands.forEachIndexed { i, cmd ->
                    Item({ Tools.run(cmd, dir) }) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accentTint), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.PLAY, 16.dp, c.accentText) }
                        Column(Modifier.weight(1f)) {
                            Text(cmd.label.ifBlank { cmd.line }, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                            Mono(cmd.line)
                        }
                        IconButton(Glyph.EDIT, tr("Edit", "עריכה"), 32.dp, c.muted, filled = false) { editing = i }
                    }
                }
                AddRow(tr("Add a command", "הוספת פקודה")) { editing = -1 }
            }
            Text(tr("Each runs in ${Shells.current.label}, in the project's folder, in a new terminal tab.", "כל פקודה רצה ב־${Shells.current.label}, בתיקיית הפרויקט, בלשונית חדשה של הטרמינל."),
                color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        Pulses.state.collectAsState().value[dir.path]?.port?.let { port ->
            Group(tr("Running", "רץ עכשיו")) {
                StateRow(LIVE_DOT, tr("Dev server", "שרת פיתוח"), "localhost:$port", c.accentText, mono = true) {
                    Chrome.open(p, DeckAction(Catalog.LINK, "http://localhost:$port", "localhost"))
                }
            }
        }
        Group(tr("Open the folder in", "פתיחת התיקייה ב־")) {
            Tools.forFolder(dir).forEach { t ->
                Item({ Tools.launch(t, dir, p.name) }) {
                    MarkBox { ToolMark(t, 20.dp, dark) }
                    Text(t.label, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                }
            }
        }
        Mono(dir.path, c.faint)
    }
}

@Composable
private fun CommandForm(initial: Command?, onSave: (Command) -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var line by remember { mutableStateOf(initial?.line ?: "") }
    TabColumn {
        FormHeader(if (initial == null) tr("New command", "פקודה חדשה") else tr("Edit command", "עריכת פקודה"), line.isNotBlank(), onClose, onDelete) { onSave(Command(label.trim(), line.trim())) }
        Field(tr("Name", "שם"), label, { label = it }, "dev")
        Field(tr("Command line", "שורת הפקודה"), line, { line = it }, "npm run dev", ltr = true)
    }
}
