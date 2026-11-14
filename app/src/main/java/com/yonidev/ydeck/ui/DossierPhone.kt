package com.yonidev.ydeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.link.MemTalk
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Note

/*
 * The project's dossier on the phone, as on the computer: writing to Claude in the project, the goal (suggested by
 * Claude, taken here, its steps ticked here), saving in words with its button, and what was built. Each request goes
 * to the computer as a note (see Note's dossier kinds); the computer does the work and the state comes back.
 */

typealias Edit = (kind: String, text: String, args: List<String>) -> Unit

private val ROUND = RoundedCornerShape(16.dp)

@Composable
private fun DossierSection(label: String, trailing: String? = null, content: @Composable () -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = SectionLabelStyle, color = c.faint, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, color = c.faint, fontSize = 12.sp)
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Words(text: String, muted: Boolean = false, size: Int = 15) {
    val c = LocalDeck.current
    Text(text, color = if (muted) c.muted else c.fg, fontSize = size.sp, lineHeight = (size + 8).sp, style = TextStyle(textDirection = dirOf(text)))
}

@Composable
private fun Button(text: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalDeck.current
    Text(text, color = if (!enabled) c.faint else if (primary) c.bg else c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(CircleShape).background(if (!enabled) c.surface2 else if (primary) c.fg else c.surface2)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp))
}

@Composable
private fun Working(text: String) {
    val c = LocalDeck.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(claudeColor("WORKING")))
        Text(text, color = claudeColor("WORKING"), fontSize = 14.sp)
    }
}

// ── writing to Claude ──

/**
 * The conversation with Claude in this project (it runs on the computer, in the project's folder): the last lines,
 * what it does now, a permission it waits for (answered at the computer), and the field. [on]: writing from the phone
 * is on at the computer.
 */
@Composable
fun TalkOf(m: Memory, on: Boolean, edit: Edit?) {
    val c = LocalDeck.current
    if (edit == null) return
    var text by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    DossierSection("Claude", trailing = m.talkTitle.takeIf { it.isNotBlank() && m.talk.isNotEmpty() }?.take(40)) {
        val lines = folded(if (all) m.talk else m.talk.takeLast(6))
        if (!all && m.talk.size > 6) Text("קודם (${m.talk.size - 6})", color = c.accentText, fontSize = 13.sp,
            modifier = Modifier.clip(CircleShape).clickable { all = true }.padding(vertical = 4.dp))
        lines.forEach { l ->
            when (l.kind) {
                MemTalk.USER -> Text(l.text, color = c.fg, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 8, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = dirOf(l.text)),
                    modifier = Modifier.widthIn(max = 320.dp).clip(ROUND).background(c.surface2).padding(horizontal = 14.dp, vertical = 9.dp))
                MemTalk.STEP -> Text(l.text, color = c.faint, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(l.text)))
                else -> MarkdownView(l.text, Modifier.fillMaxWidth())
            }
        }
        when {
            m.talkAsk.isNotBlank() -> Column(Modifier.fillMaxWidth().clip(ROUND).background(c.open.copy(alpha = 0.14f)).padding(14.dp)) {
                Text("Claude מחכה לאישור במחשב", color = c.openText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(m.talkAsk, color = c.fg, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp), style = TextStyle(textDirection = TextDirection.Content))
                Text("אישורים נותנים רק ליד המחשב: בכרטיס של הפרויקט ב־YDeck.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            m.talkBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Working("Claude עובד…") }
                Button("עצירה", primary = false) { edit(Note.TALK_STOP, "", emptyList()) }
            }
        }
        if (m.talkError.isNotBlank()) Text(m.talkError, color = c.danger, fontSize = 13.sp, lineHeight = 19.sp, style = TextStyle(textDirection = TextDirection.Content))
        when {
            !on -> Text("כדי לכתוב ל־Claude מכאן, מדליקים במחשב: הגדרות ← טלפון ← ״הטלפון יכול לפתוח את Claude Code״.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            m.talkElsewhere -> Text("השיחה הזאת פתוחה בטרמינל במחשב: כותבים לה שם, או פותחים כאן שיחה חדשה.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (on) {
            Row(Modifier.fillMaxWidth().clip(ROUND).border(1.dp, c.line, ROUND).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    val style = TextStyle(color = c.fg, fontSize = 15.sp, lineHeight = 22.sp, textDirection = TextDirection.Content)
                    if (text.isEmpty()) Text(if (m.talk.isEmpty()) "לכתוב ל־Claude, למשל: תתקן את הכותרת" else "לכתוב ל־Claude", style = style.copy(color = c.faint), maxLines = 1)
                    BasicTextField(text, { text = it.take(4000) }, textStyle = style, cursorBrush = SolidColor(c.fg), maxLines = 6, modifier = Modifier.fillMaxWidth())
                }
                val ready = text.isNotBlank() && !m.talkBusy && !m.talkElsewhere
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (ready) c.fg else c.line).clickable(enabled = ready) {
                    edit(Note.TALK_SAY, text.trim(), emptyList()); text = ""
                }, contentAlignment = Alignment.Center) { Text("↑", color = c.bg, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
            }
            if (m.talk.isNotEmpty() && !m.talkBusy) Text("שיחה חדשה", color = c.accentText, fontSize = 13.sp,
                modifier = Modifier.clip(CircleShape).clickable { edit(Note.TALK_NEW, "", emptyList()) }.padding(vertical = 4.dp))
        }
    }
}

/** Things Claude did in a row, folded into the last one: "changed home.css · and 2 more". */
private fun folded(lines: List<MemTalk>): List<MemTalk> {
    val out = ArrayList<MemTalk>()
    var run = ArrayList<String>()
    fun flush() { if (run.isNotEmpty()) { out += MemTalk(MemTalk.STEP, run.last() + if (run.size > 1) "  · ועוד ${run.size - 1}" else ""); run = ArrayList() } }
    lines.forEach { if (it.kind == MemTalk.STEP) run += it.text else { flush(); out += it } }
    flush()
    return out
}

// ── the goal ──

/**
 * The near goal: its share and its steps, ticked here. With none: the goal Claude suggests (taken or set aside here),
 * Claude reading the project now, or a way to ask for one.
 */
@Composable
fun GoalOf(m: Memory, edit: Edit?) {
    val c = LocalDeck.current
    val share = m.goalShare
    if (share != null) {
        DossierSection("היעד", trailing = "${share.first} מתוך ${share.second}") {
            Text(m.goal, color = c.fg, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, style = TextStyle(textDirection = dirOf(m.goal)))
            val next = m.goalSteps.indexOfFirst { !it.done }
            m.goalSteps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .then(if (edit != null) Modifier.clickable { edit(Note.GOAL_STEP, s.text, listOf(if (s.done) "0" else "1")) } else Modifier)
                    .padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        s.done -> Box(Modifier.size(18.dp).clip(CircleShape).background(c.closed))
                        i == next -> Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, c.accentText, CircleShape))
                        else -> Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, c.line, CircleShape))
                    }
                    Text(s.text, color = if (s.done) c.faint else c.fg, fontSize = 15.sp, lineHeight = 21.sp,
                        fontWeight = if (i == next) FontWeight.Medium else FontWeight.Normal, style = TextStyle(textDirection = dirOf(s.text)))
                }
            }
            if (edit != null) Text("נוגעים בצעד כדי לסמן אותו.", color = c.faint, fontSize = 12.sp)
        }
        return
    }
    DossierSection("היעד") {
        when {
            m.suggestedGoal.isNotBlank() -> {
                Text("Claude מציע:", color = c.muted, fontSize = 13.sp)
                Text(m.suggestedGoal, color = c.fg, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, style = TextStyle(textDirection = dirOf(m.suggestedGoal)))
                m.suggestedSteps.forEach { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).border(2.dp, c.line, CircleShape))
                        Words(s)
                    }
                }
                if (edit != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("לקבוע את היעד") { edit(Note.GOAL_TAKE, m.suggestedGoal, emptyList()) }
                    Button("לא עכשיו", primary = false) { edit(Note.GOAL_DISMISS, "", emptyList()) }
                }
            }
            m.dossierBusy -> Working("Claude קורא את הפרויקט…")
            else -> {
                Words("עוד אין יעד. יעד קרוב אחד, עם צעדים אליו, נותן לפרויקט אחוז התקדמות.", muted = true, size = 14)
                if (m.built.isEmpty()) Words("Claude גם ימלא את ״מה נבנה״: מה הפרויקט כבר יודע לעשות.", muted = true, size = 14)
                if (m.dossierError.isNotBlank()) Text(m.dossierError, color = c.danger, fontSize = 13.sp)
                if (edit != null) Button("להציע יעד") { edit(Note.GOAL_SUGGEST, "", emptyList()) }
            }
        }
    }
}

// ── saving ──

/** What isn't saved, in words, what to do, and the button that does it (on the computer). */
@Composable
fun SavingOf(m: Memory, live: com.yonidev.ydeck.link.Live?, on: Boolean, edit: Edit?) {
    val c = LocalDeck.current
    if (live?.hasGit != true) return
    val clean = m.saving.isNotBlank() && m.savingProposal.isBlank() && m.savingSteps.isBlank()
    DossierSection("שמירה") {
        if (m.saving.isBlank()) {
            if (m.savingBusy) Working("בודק מה פתוח…") else Words("עוד לא נבדק. זה קורה לבד כשהמחשב דולק.", muted = true, size = 14)
            return@DossierSection
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 8.dp)) { Box(Modifier.size(8.dp).clip(CircleShape).background(if (clean) c.closed else c.open)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Words(m.saving)
                if (m.savingProposal.isNotBlank()) Words("הצעה: " + m.savingProposal, muted = true, size = 13)
            }
        }
        if (m.savingSteps.isNotBlank() && edit != null) {
            if (on) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(if (m.savingBusy) "עובד…" else "תעשה", enabled = !m.savingBusy) { edit(Note.SAVE_DO, "", emptyList()) }
                Text(m.savingSteps, color = c.muted, fontSize = 13.sp, style = TextStyle(textDirection = dirOf(m.savingSteps)))
            } else Text("כדי לשמור מכאן, מדליקים במחשב: הגדרות ← טלפון ← ״הטלפון יכול לפתוח את Claude Code״.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        when {
            m.savingResult == "ok" -> Text("נעשה.", color = c.closed, fontSize = 13.sp)
            m.savingResult.isNotBlank() -> Text(m.savingResult, color = c.danger, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

// ── what was built ──

/** Everything the project can do: the first few, the rest on a tap. */
@Composable
fun BuiltOf(m: Memory) {
    val c = LocalDeck.current
    if (m.built.isEmpty()) return
    var all by remember { mutableStateOf(false) }
    DossierSection("מה נבנה · ${m.built.size}") {
        (if (all) m.built else m.built.take(6)).forEach { t ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 9.dp)) { Box(Modifier.size(5.dp).clip(CircleShape).background(c.faint)) }
                Words(t)
            }
        }
        if (m.built.size > 6) Text(if (all) "פחות" else "כל ה־${m.built.size}", color = c.accentText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(CircleShape).clickable { all = !all }.padding(vertical = 6.dp))
    }
}
