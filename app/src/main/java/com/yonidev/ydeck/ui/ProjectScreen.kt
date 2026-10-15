package com.yonidev.ydeck.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.R
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.data.Router
import com.yonidev.ydeck.link.Launch
import com.yonidev.ydeck.link.Launched
import com.yonidev.ydeck.link.Live
import com.yonidev.ydeck.link.LiveFile
import com.yonidev.ydeck.link.LiveReceipt
import com.yonidev.ydeck.link.LiveSession
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Note

private enum class Tab(val label: String) { MEMORY("זיכרון"), GIT("Git"), LINKS("קישורים") }

/**
 * One project, everything the computer knows about it, in three tabs: its memory (what's next, what's open, the last
 * session, the decisions), Git (branch, changes, sessions and the files they touched, commits), and its links.
 * [onWrite] leaves a decision or a next step for the computer; null when the phone isn't paired. [onLaunch] asks the
 * computer to open Claude Code in the project's folder; null when it doesn't take such requests.
 */
@Composable
fun ProjectScreen(
    p: Project,
    memory: Memory?,
    live: Live?,
    pending: List<Note>,
    onBack: () -> Unit,
    onWrite: ((kind: String, text: String) -> Unit)?,
    launches: List<Pair<Launch, Launched?>> = emptyList(),
    /** The computer takes launch requests now; when false, the card says where to turn it on. */
    launchOn: Boolean = false,
    onLaunch: ((mode: String, session: String, prompt: String, remote: Boolean) -> Unit)? = null,
) {
    val c = LocalDeck.current
    var tab by rememberSaveable { mutableStateOf(Tab.MEMORY) }
    var writing by remember { mutableStateOf<String?>(null) }
    var launching by remember { mutableStateOf(false) }
    val canWrite = onWrite != null && memory != null
    PageScaffold(
        title = p.name, onBack = onBack,
        actions = { if (canWrite) IconButton(onClick = { writing = Note.NEXT }) { Icon(Icons.Outlined.Edit, "כתיבה", tint = c.fg) } },
    ) {
        item { Header(p, live) }
        if (onLaunch != null && live != null) item { ClaudeCard(launches.lastOrNull(), launchOn) { launching = true } }
        item { Tabs(tab) { tab = it } }
        when (tab) {
            Tab.MEMORY -> item {
                MemoryTab(memory, live, pending, if (canWrite) { kind -> writing = kind } else null,
                    onDone = if (canWrite) { next -> onWrite?.invoke(Note.DONE, next) } else null)
            }
            Tab.GIT -> item { GitTab(live) }
            Tab.LINKS -> item { LinksTab(p, live) }
        }
    }
    writing?.let { kind -> WriteSheet(kind, onDismiss = { writing = null }, onSend = { k, text -> writing = null; onWrite?.invoke(k, text) }) }
    if (launching) LaunchSheet(live?.sessions.orEmpty(), onDismiss = { launching = false }) { mode, session, prompt, remote ->
        launching = false
        onLaunch?.invoke(mode, session, prompt, remote)
    }
}

@Composable
private fun Header(p: Project, live: Live?) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Logo(p, 44.dp)
            Column(Modifier.weight(1f)) {
                val line = listOfNotNull(p.kind.takeIf { it.isNotBlank() }, live?.version?.takeIf { it.isNotBlank() }?.let { "גרסה ⁦$it⁩" }).joinToString(" · ")
                if (line.isNotBlank()) Text(line, color = c.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.googleEmail ?: "חשבון גוגל לא הוגדר", color = c.faint, fontSize = 13.sp, fontFamily = if (p.googleEmail != null) PlexMono else PlexSans)
            }
        }
        // The agent in the folder, as on the computer: working (indigo), waiting for you (amber), done (green).
        if (live != null && live.agentMode.isNotBlank()) {
            val name = live.agent.ifBlank { "Claude" }
            val (color, words) = when (live.agentMode) {
                "WORKING" -> c.accentText to "$name עובד"
                "WAITING" -> c.open to "$name מחכה לך"
                else -> c.closed to "$name סיים"
            }
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Column(Modifier.weight(1f)) {
                    Text(words + if (live.agentSince > 0) " · ${ago(live.agentSince)}" else "", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (live.agentDetail.isNotBlank()) Text(live.agentDetail, color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(live.agentDetail)))
                }
            }
        }
    }
}

/** The tabs: one quiet segmented control, as on the computer. */
@Composable
private fun Tabs(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(3.dp)) {
        Tab.entries.forEach { t ->
            val on = t == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) c.line else Color.Transparent).clickable { onSelect(t) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t.label, color = if (on) c.fg else c.muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
        }
    }
}

// ── memory ──

@Composable
private fun MemoryTab(m: Memory?, live: Live?, pending: List<Note>, write: ((String) -> Unit)?, onDone: ((String) -> Unit)?) {
    val c = LocalDeck.current
    if (m == null) {
        FootNote("לפרויקט הזה אין עדיין תיקייה ב־YDeck במחשב, אז אין לו זיכרון. כשמחברים לו תיקייה, יופיעו כאן הצעד הבא, הסשן האחרון, ההחלטות ו־Git.")
        return
    }
    Column {
        if (pending.isNotEmpty()) Block("ממתין למחשב") {
            pending.forEachIndexed { i, n ->
                if (i > 0) Hair()
                Text(when (n.kind) { Note.DECISION -> "החלטה"; Note.DONE -> "בוצע"; else -> "צעד הבא" }, color = c.faint, fontSize = 12.sp)
                Body(n.text)
            }
            Text("יישמר בפרויקט כש־YDeck במחשב פתוח.", color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        // A "done" on its way: the next step already shows as closed here.
        val closing = pending.any { it.kind == Note.DONE }
        NextBlock(if (closing) m.copy(next = "") else m, write, onDone)
        if (live != null && live.loops.isNotEmpty()) Block("פתוח עכשיו") {
            live.loops.forEachIndexed { i, l ->
                if (i > 0) Hair()
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.open))
                    Column(Modifier.weight(1f)) {
                        Body(l.title)
                        if (l.detail.isNotBlank()) Text(l.detail, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(l.detail)))
                    }
                }
            }
        }
        if (m.now.isNotBlank()) Block("עכשיו") { Body(m.now) }
        if (m.done.isNotBlank()) Block("בסשן האחרון" + (if (m.doneAt > 0) " · ${ago(m.doneAt)}" else "") + (if (m.agent.isNotBlank()) " · ${m.agent}" else "")) { Body(m.done) }
        if (m.summary.isNotBlank()) Block("על הפרויקט") { Body(m.summary, muted = true) }
        Decisions(m, write)
        m.releases.firstOrNull()?.let { r ->
            Block("גרסה אחרונה") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.version, color = c.fg, fontSize = 15.sp, fontFamily = PlexMono)
                    if (r.date.isNotBlank()) Text(r.date, color = c.faint, fontSize = 13.sp, fontFamily = PlexMono)
                }
                if (r.note.isNotBlank()) Body(r.note, muted = true)
            }
        }
    }
}

/** The one thing the page is for: what's next, in amber; or, in green, that nothing is left open. */
@Composable
private fun NextBlock(m: Memory, write: ((String) -> Unit)?, onDone: ((String) -> Unit)?) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (m.open) c.open else c.closed))
            Text(if (m.open) "הבא" else "סגור", color = if (m.open) c.openText else c.closed, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (m.open && onDone != null) SmallAction("בוצע") { onDone(m.next) }
            if (write != null) SmallAction(if (m.open) "שינוי" else "צעד הבא") { write(Note.NEXT) }
        }
        Text(
            if (m.open) m.next else "שום דבר לא נשאר פתוח.",
            color = c.fg, fontSize = 22.sp, lineHeight = 30.sp, fontFamily = Display, fontWeight = FontWeight.Light,
            style = TextStyle(textDirection = dirOf(if (m.open) m.next else "א")), modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Decisions(m: Memory, write: ((String) -> Unit)?) {
    val c = LocalDeck.current
    var all by remember { mutableStateOf(false) }
    if (m.decisions.isEmpty() && write == null) return
    val shown = if (all) m.decisions else m.decisions.take(4)
    Block("החלטות" + if (m.decisions.isNotEmpty()) " · ${m.decisions.size}" else "", action = write?.let { w -> "הוספה" to { w(Note.DECISION) } }) {
        if (m.decisions.isEmpty()) Text("עוד אין החלטות. החלטה שנכתבת כאן נשמרת בפרויקט, והסשן הבא של Claude מכיר אותה.", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
        shown.forEachIndexed { i, d ->
            if (i > 0) Hair()
            Body(d.text)
            if (d.date.isNotBlank()) Text(d.date, color = c.faint, fontSize = 12.sp, fontFamily = PlexMono, modifier = Modifier.padding(top = 2.dp))
        }
        if (m.decisions.size > 4) SmallAction(if (all) "פחות" else "עוד ${m.decisions.size - shown.size}", Modifier.padding(top = 10.dp)) { all = !all }
    }
}

// ── git ──

@Composable
private fun GitTab(live: Live?) {
    val c = LocalDeck.current
    val context = LocalContext.current
    if (live == null || !live.hasGit) {
        FootNote(if (live == null) "לפרויקט הזה אין תיקייה ב־YDeck במחשב." else "התיקייה של הפרויקט הזה עוד לא ריפו של Git.")
        return
    }
    Column {
        Block("ענף") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⁦${live.branch}⁩", color = c.fg, fontSize = 17.sp, fontFamily = PlexMono, modifier = Modifier.weight(1f, fill = false))
                if (live.upstream.isNotBlank()) Text("← ⁦${live.upstream}⁩", color = c.faint, fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (live.changes == 0) "נקי" else if (live.changes == 1) "שינוי אחד" else "${live.changes} שינויים",
                    color = if (live.changes > 0) c.openText else c.closed, bg = (if (live.changes > 0) c.open else c.closed).copy(alpha = 0.12f))
                if (live.ahead > 0) Chip("↑${live.ahead} לא נדחפו", color = c.openText, bg = c.open.copy(alpha = 0.12f))
                if (live.behind > 0) Chip("↓${live.behind} חדשים בשרת", color = c.accentText, bg = c.accentTint)
            }
            if (live.lastAt > 0) Text("קומיט אחרון ${ago(live.lastAt)}: ${live.lastSubject}", color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Rtl), modifier = Modifier.padding(top = 10.dp))
            if (live.web.isNotBlank()) SmallAction("פתיחת הריפו", Modifier.padding(top = 10.dp)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(live.web))) }
            }
        }
        if (live.files.isNotEmpty()) Block(if (live.changes > live.files.size) "שינויים · ${live.files.size} מתוך ${live.changes}" else "שינויים") {
            Files(live.files)
        }
        if (live.receipts.isNotEmpty()) Block("סשנים") {
            live.receipts.forEachIndexed { i, r ->
                if (i > 0) Hair()
                ReceiptRow(r)
            }
        }
        if (live.commits.isNotEmpty()) Block("קומיטים") {
            live.commits.forEachIndexed { i, cm ->
                if (i > 0) Hair()
                Body(cm.subject)
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cm.hash, color = c.faint, fontSize = 12.sp, fontFamily = PlexMono)
                    Text("${cm.author} · ${ago(cm.at)}", color = c.faint, fontSize = 12.sp)
                }
            }
        }
        FootNote("לקריאה בלבד: קומיט, דחיפה ושאר הפעולות נעשים במחשב.")
    }
}

/** One agent session: what it was asked, when, and the files it touched (tap to see them). */
@Composable
private fun ReceiptRow(r: LiveReceipt) {
    val c = LocalDeck.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(enabled = r.files.isNotEmpty()) { open = !open }) {
        Body(r.title)
        val files = when (r.files.size) { 0 -> "בלי שינויים בקבצים"; 1 -> "קובץ אחד"; else -> "${r.files.size} קבצים" }
        Text(listOfNotNull(r.agent, ago(r.endAt.takeIf { it > 0 } ?: r.startAt), files, if (r.rewound) "הוחזר" else null).joinToString(" · "),
            color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        if (open) Column(Modifier.padding(top = 8.dp)) { Files(r.files) }
    }
}

/** Changed files: a letter in the state's colour, then the path (left to right, as paths are). */
@Composable
private fun Files(files: List<LiveFile>) {
    val c = LocalDeck.current
    files.forEach { f ->
        val code = f.code.trim()
        val (letter, color) = when {
            f.code == "??" || 'A' in code -> "A" to c.closed
            'D' in code -> "D" to c.danger
            'R' in code -> "R" to c.accentText
            'U' in code -> "U" to c.open
            else -> "M" to c.openText
        }
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(letter, color = color, fontSize = 12.sp, fontFamily = PlexMono, fontWeight = FontWeight.Medium)
            Text(f.path, color = c.fg.copy(alpha = 0.85f), fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.StartEllipsis,
                style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
        }
    }
}

// ── links ──

@Composable
private fun LinksTab(p: Project, live: Live?) {
    val c = LocalDeck.current
    val context = LocalContext.current
    Column {
        if (p.actions.isNotEmpty()) SectionCard("קישורים", hint = p.googleEmail?.let { "שירותי גוגל נפתחים בחשבון $it" }) {
            p.actions.forEachIndexed { i, a ->
                if (i > 0) Divider(inset = 58.dp)
                ListRow(
                    onClick = { Router.open(context, p, a) },
                    leading = { ServiceIcon(a.service, a.faviconHost) },
                    title = a.label,
                    subtitle = Router.displayUrl(a.url),
                    trailing = { val b = Router.browserFor(p, a); if (b.pkg != Router.CHROME) Chip(b.label) },
                )
            }
        } else FootNote("אין לפרויקט קישורים. מוסיפים אותם ב־YDeck במחשב.")
        if (p.copies.isNotEmpty()) SectionCard("העתקה") {
            p.copies.forEachIndexed { i, item ->
                if (i > 0) Divider(inset = 58.dp)
                ListRow(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(item.label, item.value))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "הועתק", Toast.LENGTH_SHORT).show()
                    },
                    leading = { IconBox(R.drawable.ic_s_copy) },
                    title = item.label,
                    subtitle = item.value,
                )
            }
        }
        if (live != null && (live.ports.isNotEmpty() || live.missingEnv.isNotEmpty())) Block("במחשב") {
            if (live.ports.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.closed))
                    Text("שרת פיתוח רץ · ⁦localhost:${live.ports.first()}⁩", color = c.fg, fontSize = 15.sp)
                }
            }
            if (live.missingEnv.isNotEmpty()) {
                if (live.ports.isNotEmpty()) Hair()
                Text("חסרים ב־.env", color = c.openText, fontSize = 13.sp)
                Text(live.missingEnv.joinToString("  "), color = c.fg, fontSize = 13.sp, fontFamily = PlexMono, style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ── writing ──

/**
 * Writing a next step or a decision, typed or spoken. It reaches YDeck on the computer, which keeps it in the
 * project's files; the next Claude session starts knowing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteSheet(initial: String, onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initial) }
    var text by remember { mutableStateOf("") }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { said ->
            text = if (text.isBlank()) said else text.trimEnd() + " " + said
        }
    }
    val canSpeak = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(3.dp)) {
                listOf(Note.NEXT to "צעד הבא", Note.DECISION to "החלטה").forEach { (k, label) ->
                    val on = k == kind
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) c.line else Color.Transparent).clickable { kind = k }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) c.fg else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
                }
            }
            Text(
                if (kind == Note.NEXT) "מחליף את הצעד הבא של הפרויקט." else "נשמרת בהחלטות של הפרויקט, ש־Claude שומר עליהן בכל סשן.",
                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(14.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 17.sp, lineHeight = 25.sp, fontFamily = PlexSans, textDirection = TextDirection.Content)
                if (text.isEmpty()) Text(if (kind == Note.NEXT) "מה הדבר הבא לעשות?" else "מה הוחלט, ולמה?", style = style.copy(color = c.faint))
                BasicTextField(text, { text = it.take(Note.MAX) }, textStyle = style, cursorBrush = SolidColor(c.fg), modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canSpeak) {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape).background(c.surface2).clickable {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, if (kind == Note.NEXT) "מה הצעד הבא?" else "מה ההחלטה?"))
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Mic, "הקלטה", tint = c.fg) }
                }
                Button(
                    onClick = { onSend(kind, text) },
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.fg, contentColor = c.bg, disabledContainerColor = c.surface2, disabledContentColor = c.faint),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("שליחה למחשב", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

// ── Claude Code on the computer ──

private val CLAUDE = Color(0xFFD97757)
private const val CLAUDE_APP = "com.anthropic.claude"

/** "Open Claude on the computer", and what became of the last time it was asked. */
@Composable
private fun ClaudeCard(last: Pair<Launch, Launched?>?, on: Boolean, onOpen: () -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    val l = last?.first
    val done = last?.second
    val status = when {
        !on -> "כבוי במחשב. מדליקים ב־YDeck במחשב ← הגדרות ← טלפון."
        l == null -> null
        done == null -> if (System.currentTimeMillis() - l.at > 90_000) "המחשב עוד לא ענה. YDeck פתוח בו?" else "נשלח, מחכה למחשב…"
        done.ok -> "נפתח במחשב ${ago(l.at)}" + if (l.remote) " · זמין באפליקציית Claude" else ""
        else -> when (done.why) {
            "off" -> "הפתיחה מהטלפון כבויה במחשב (הגדרות ← טלפון)"
            "claude" -> "Claude Code לא מותקן במחשב"
            "stale" -> "הבקשה הגיעה מאוחר מדי ולא נפתחה. אפשר לשלוח שוב."
            "project", "folder" -> "לפרויקט אין תיקייה במחשב"
            "session" -> "הסשן הזה כבר לא נמצא בתיקייה"
            else -> "Claude לא נפתח במחשב"
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(enabled = on, onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBox(R.drawable.si_claude, 34.dp, tint = Color.White, bg = if (on) CLAUDE else c.surface2)
            Column(Modifier.weight(1f)) {
                Text("פתיחת Claude במחשב", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(status ?: "להמשיך, סשן חדש או סשן קודם", color = if (on && done?.ok == false) c.openText else c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (on && l != null && l.remote && done?.ok == true) SmallAction("פתיחת אפליקציית Claude", Modifier.padding(top = 4.dp)) {
            // The session shows up there under the project's name; without the app, its page on Google Play.
            val open = context.packageManager.getLaunchIntentForPackage(CLAUDE_APP)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$CLAUDE_APP"))
            runCatching { context.startActivity(open) }
        }
    }
}

/**
 * How to open Claude Code on the computer: pick up the last session, start a new one, or choose an earlier one;
 * with Remote Control (to go on from the Claude app here) and a first message. The computer does exactly this and
 * nothing else (see [Launch]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaunchSheet(sessions: List<LiveSession>, onDismiss: () -> Unit, onSend: (String, String, String, Boolean) -> Unit) {
    val c = LocalDeck.current
    var mode by remember { mutableStateOf(if (sessions.isEmpty()) Launch.NEW else Launch.CONTINUE) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var remote by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp)) {
            Text("Claude במחשב", color = c.fg, fontSize = 20.sp, fontFamily = Display, modifier = Modifier.padding(bottom = 12.dp))
            val modes = if (sessions.isEmpty()) listOf(Launch.NEW to "סשן חדש")
                else listOf(Launch.CONTINUE to "להמשיך", Launch.NEW to "סשן חדש", Launch.RESUME to "סשן קודם")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(3.dp)) {
                modes.forEach { (k, label) ->
                    val on = k == mode
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) c.line else Color.Transparent).clickable { mode = k }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) c.fg else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
                }
            }
            Text(
                when (mode) {
                    Launch.CONTINUE -> "ממשיך את הסשן האחרון: ${sessions.firstOrNull()?.title.orEmpty()}"
                    Launch.RESUME -> "בוחרים סשן מהתיקייה של הפרויקט."
                    else -> "סשן חדש בתיקייה של הפרויקט, עם הזיכרון שלו."
                },
                color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            if (mode == Launch.RESUME) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(vertical = 4.dp)) {
                sessions.forEach { s ->
                    val on = s.id == chosen
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = s.id }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) c.accent else c.line))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = c.fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(s.title)))
                            if (s.at > 0) Text(ago(s.at), color = c.faint, fontSize = 12.sp)
                        }
                    }
                }
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(14.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = PlexSans, textDirection = TextDirection.Content)
                if (text.isEmpty()) Text(if (mode == Launch.NEW) "מה לעשות? (לא חובה)" else "הודעה להמשך (לא חובה)", style = style.copy(color = c.faint))
                BasicTextField(text, { text = it.take(Launch.MAX) }, textStyle = style, cursorBrush = SolidColor(c.fg), modifier = Modifier.fillMaxWidth())
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { remote = !remote }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Remote Control", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("להמשיך לעבוד על הסשן מאפליקציית Claude בטלפון", color = c.muted, fontSize = 13.sp)
                }
                Switch(remote, { remote = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White))
            }
            Button(
                onClick = { onSend(mode, if (mode == Launch.RESUME) chosen.orEmpty() else "", text, remote) },
                enabled = mode != Launch.RESUME || chosen != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.fg, contentColor = c.bg, disabledContainerColor = c.surface2, disabledContentColor = c.faint),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(50.dp),
            ) { Text("פתיחה במחשב", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            Text("נפתח בטרמינל חדש במחשב, בתיקייה של הפרויקט. YDeck צריך להיות פתוח שם.", color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ── pieces ──

/** A labelled block, between hairlines; [action] puts a small text button at the label's end. */
@Composable
private fun Block(label: String, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { SectionHeader(label) }
            if (action != null) SmallAction(action.first, Modifier.padding(bottom = 4.dp), action.second)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) { content() }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
    }
}

@Composable
private fun SmallAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(text, color = LocalDeck.current.accentText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
private fun Hair() = Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LocalDeck.current.line))

@Composable
private fun Body(text: String, muted: Boolean = false) {
    val c = LocalDeck.current
    Text(text, color = if (muted) c.muted else c.fg, fontSize = 15.sp, lineHeight = 23.sp, style = TextStyle(textDirection = dirOf(text)))
}
