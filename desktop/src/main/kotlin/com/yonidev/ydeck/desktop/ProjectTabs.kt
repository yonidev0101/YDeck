package com.yonidev.ydeck.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
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

// ── links ──

/*
 * The project's links as tiles (one click opens, editing on hover or a right-click), the browser they open in as one
 * line above them, and the values to copy as chips below. Claude can read the project and find the links it lacks.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    val dir = Settings.state.collectAsState().value.localOf(p.id).dir
    TabColumn {
        OpensIn(p)
        Column {
            Section(tr("Links", "קישורים") + (if (p.actions.isEmpty()) "" else "  ${p.actions.size}")) {
                if (dir != null && Tools.claude != null) {
                    val busy = p.id in ClaudeFill.busy.collectAsState().value
                    TextAction(if (busy) tr("Claude is reading…", "Claude קורא…") else tr("Find links with Claude", "למצוא קישורים עם Claude"), accent = true, enabled = !busy) {
                        ClaudeFill.start(p, dir); Nav.set(Screen.Details(p.id))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 12.dp
                val columns = ((maxWidth + gap) / (210.dp + gap)).toInt().coerceIn(2, 4)
                val width = (maxWidth - gap * (columns - 1)) / columns
                FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                    p.actions.forEachIndexed { i, a ->
                        ContextMenuArea(items = {
                            buildList {
                                add(ContextMenuItem(tr("Edit", "עריכה")) { link = i })
                                if (i > 0) add(ContextMenuItem(tr("Move earlier", "הזזה אחורה")) { saveProject(p.copy(actions = p.actions.toMutableList().apply { add(i - 1, removeAt(i)) })) })
                                if (i < p.actions.lastIndex) add(ContextMenuItem(tr("Move later", "הזזה קדימה")) { saveProject(p.copy(actions = p.actions.toMutableList().apply { add(i + 1, removeAt(i)) })) })
                                add(ContextMenuItem(tr("Remove", "הסרה")) { saveProject(p.copy(actions = p.actions.filterIndexed { k, _ -> k != i })) })
                            }
                        }) { LinkTile(a, dark, Modifier.width(width), onEdit = { link = i }) { Chrome.open(p, a) } }
                    }
                    AddTile(tr("Link", "קישור"), Modifier.width(width)) { link = -1 }
                }
            }
        }
        Column {
            Section(tr("Values to copy", "ערכים להעתקה"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.copies.forEachIndexed { i, item ->
                    ContextMenuArea(items = {
                        listOf(
                            ContextMenuItem(tr("Edit", "עריכה")) { copy = i },
                            ContextMenuItem(tr("Remove", "הסרה")) { saveProject(p.copy(copies = p.copies.filterIndexed { k, _ -> k != i })) },
                        )
                    }) { CopyChip(item) }
                }
                Row(Modifier.clip(RoundedCornerShape(99.dp)).border(1.dp, c.line, RoundedCornerShape(99.dp)).clickable { copy = -1 }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.PLUS, 14.dp, c.accentText)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Value", "ערך"), color = c.accentText, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                }
            }
            if (p.copies.isEmpty()) Text(tr("A package name, a project id, a repo: one click copies it.", "שם חבילה, מזהה פרויקט, ריפו: לחיצה אחת מעתיקה."),
                color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
    }
}

/** A link: its service's mark, its name, the site. Opens on a click; the pencil shows on hover. */
@Composable
private fun LinkTile(a: DeckAction, dark: Boolean, modifier: Modifier, onEdit: () -> Unit, onOpen: () -> Unit) {
    val c = LocalDeck.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.clip(shape).border(1.dp, c.line, shape).background(if (hovered) c.surface2 else Color.Transparent).hoverable(hover).clickable(onClick = onOpen)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        MarkBox { ServiceMark(a.service, a.faviconHost, 20.dp, dark) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Mono(Links.host(a.url) ?: Links.display(a.url), c.faint, 11)
        }
        if (hovered) IconButton(Glyph.EDIT, tr("Edit", "עריכה"), 28.dp, c.muted, filled = false, onClick = onEdit)
    }
}

@Composable
private fun AddTile(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.clip(shape).border(1.dp, c.line, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.PLUS, 18.dp, c.accentText) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = c.accentText, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
    }
}

/** Where the links open: the Chrome profile (by the project's Google account, or chosen); changed in place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpensIn(p: Project) {
    val c = LocalDeck.current
    var open by remember(p.id) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.paper).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.GLOBE, 16.dp, c.muted)
            Spacer(Modifier.width(10.dp))
            Text(tr("Opens in Chrome · ", "נפתחים ב־Chrome · ") + Chrome.describe(p), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextAction(if (open) tr("Done", "סיום") else tr("Change", "שינוי"), accent = true) { open = !open }
        }
        if (open) Box(Modifier.padding(top = 10.dp)) { Account(p) }
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

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalDeck.current
    Text(text, color = if (selected) c.accentText else c.fg, fontSize = 13.sp, fontFamily = PlexSans,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (selected) c.accentTint else c.surface2)
            .then(if (selected) Modifier.border(1.dp, c.accent, RoundedCornerShape(99.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp))
}

/** A value to copy: its name, the value, and a click copies it (editing on a right-click). */
@Composable
private fun CopyChip(item: CopyItem) {
    val c = LocalDeck.current
    var copied by remember { mutableStateOf(false) }
    if (copied) LaunchedEffect(Unit) { delay(1400); copied = false }
    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(c.surface2)
        .clickable { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(item.value), null); copied = true }
        .padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item.label.isNotBlank()) { Text(item.label, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans); Spacer(Modifier.width(8.dp)) }
        Mono(item.value, c.fg, 12)
        Spacer(Modifier.width(8.dp))
        if (copied) Text(tr("Copied", "הועתק"), color = CLEAN_DOT, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
        else GlyphIcon(Glyph.COPY, 13.dp, c.faint)
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

/** The files agents read at the start of a session: Claude Code's memory, and the one Codex, Cursor and others share. */
private val AGENT_DOCS = setOf("claude.md", "agents.md")

/** The project's documents worth reading: the README first, then the other Markdown at the top and in docs/. */
fun docsOf(dir: File): List<File> {
    val top = dir.listFiles { f -> f.isFile && f.extension.equals("md", true) }.orEmpty()
        .sortedWith(compareBy({ !it.name.startsWith("README", true) }, { !it.name.equals("CLAUDE.md", true) }, { it.name.lowercase() }))
    val docs = File(dir, "docs").takeIf { it.isDirectory }?.walkTopDown()?.maxDepth(2)?.filter { it.isFile && it.extension.equals("md", true) }?.sortedBy { it.path.lowercase() }?.toList().orEmpty()
    return (top + docs).take(24)
}

/*
 * The project's documents, read here: its own (the README, docs/), the ones its agents read, and the files pinned to
 * it. A pinned Markdown file reads here too; anything else opens in its app.
 */
@Composable
fun FilesTab(p: Project, local: Local, actions: AppActions) {
    val c = LocalDeck.current
    val dir = local.dir
    val docs = remember(dir) { dir?.let(::docsOf).orEmpty() }
    val (agentDocs, projectDocs) = docs.partition { it.parentFile == dir && it.name.lowercase() in AGENT_DOCS }
    // A pinned file already listed above isn't listed twice.
    val listed = docs.map { it.path.lowercase() }.toSet()
    val pinned = local.files.filter { File(it).path.lowercase() !in listed }
    var open by remember(p.id) { mutableStateOf(docs.firstOrNull()?.path ?: local.files.firstOrNull { it.endsWith(".md", true) }) }
    Row(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 22.dp)) {
        Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            @Composable fun group(label: String, files: List<File>) {
                if (files.isEmpty()) return
                Column {
                    GroupLabel(label)
                    files.forEach { f -> FileRow(Glyph.DOC, f.relativeTo(dir!!).path.replace('\\', '/'), f.path == open) { open = f.path } }
                }
            }
            group(tr("The project", "הפרויקט"), projectDocs)
            group(tr("For agents", "לסוכנים"), agentDocs)
            Column {
                GroupLabel(tr("Pinned", "נעוצים"))
                pinned.forEach { f ->
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

/*
 * The project's commands as tiles (a click runs it in a new terminal tab, editing on hover or a right-click), what's
 * running now, and the tools that open the folder, as one row.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        Pulses.state.collectAsState().value[dir.path]?.port?.let { port ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.paper).clickable { Chrome.open(p, DeckAction(Catalog.LINK, "http://localhost:$port", "localhost")) }
                .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(LIVE_DOT)
                Spacer(Modifier.width(10.dp))
                Text(tr("A dev server is running", "שרת פיתוח רץ"), color = c.fg, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                Mono("localhost:$port", c.accentText, 12)
            }
        }
        Column {
            Section(tr("Commands", "פקודות") + (if (local.commands.isEmpty()) "" else "  ${local.commands.size}")) {
                if (Tools.claude != null) {
                    val busy = p.id in ClaudeFill.busy.collectAsState().value
                    TextAction(if (busy) tr("Claude is reading…", "Claude קורא…") else tr("Find commands with Claude", "למצוא פקודות עם Claude"), accent = true, enabled = !busy) {
                        ClaudeFill.start(p, dir); Nav.set(Screen.Details(p.id))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 12.dp
                val columns = ((maxWidth + gap) / (240.dp + gap)).toInt().coerceIn(2, 3)
                val width = (maxWidth - gap * (columns - 1)) / columns
                FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                    local.commands.forEachIndexed { i, cmd ->
                        ContextMenuArea(items = {
                            listOf(
                                ContextMenuItem(tr("Edit", "עריכה")) { editing = i },
                                ContextMenuItem(tr("Remove", "הסרה")) { saveLocal(p, local.copy(commands = local.commands.filterIndexed { k, _ -> k != i })) },
                            )
                        }) { CommandTile(cmd, Modifier.width(width), onEdit = { editing = i }) { Tools.run(cmd, dir) } }
                    }
                    AddTile(tr("Command", "פקודה"), Modifier.width(width)) { editing = -1 }
                }
            }
            Text(tr("Each runs in ${Shells.current.label}, in the project's folder, in a new terminal tab.", "כל פקודה רצה ב־${Shells.current.label}, בתיקיית הפרויקט, בלשונית חדשה של הטרמינל."),
                color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        }
        Column {
            Section(tr("Open the folder in", "פתיחת התיקייה ב־"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tools.forFolder(dir).forEach { t ->
                    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(c.surface2).clickable { Tools.launch(t, dir, p.name) }.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ToolMark(t, 18.dp, dark)
                        Spacer(Modifier.width(8.dp))
                        Text(t.label, color = c.fg, fontSize = 13.sp, fontFamily = PlexSans)
                    }
                }
            }
            Mono(dir.path, c.faint, 11, Modifier.padding(start = 4.dp, top = 10.dp))
        }
    }
}

/** A command: its name and its line; a click runs it, the pencil shows on hover. */
@Composable
private fun CommandTile(cmd: Command, modifier: Modifier, onEdit: () -> Unit, onRun: () -> Unit) {
    val c = LocalDeck.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.clip(shape).border(1.dp, c.line, shape).background(if (hovered) c.surface2 else Color.Transparent).hoverable(hover).clickable(onClick = onRun)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accentTint), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.PLAY, 16.dp, c.accentText) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cmd.label.ifBlank { cmd.line }, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Mono(cmd.line, c.faint, 11)
        }
        if (hovered) IconButton(Glyph.EDIT, tr("Edit", "עריכה"), 28.dp, c.muted, filled = false, onClick = onEdit)
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
