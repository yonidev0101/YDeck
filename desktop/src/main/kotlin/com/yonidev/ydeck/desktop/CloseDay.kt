package com.yonidev.ydeck.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Closing the day: every project worked on today, and what's left open in it, closed in one go. Commit what's not
 * committed (Claude writes the message), push, stop the dev servers, and leave the next step, so tomorrow starts
 * from it. Each step can be left out; nothing happens until "Close the day".
 */

enum class DayStep { COMMIT, PUSH, SERVER, NEXT }

/** A project's part in closing the day: what can be done in it now. */
data class DayPlan(val project: Project, val dir: File, val steps: List<DayStep>, val changes: Int, val ahead: Int, val publish: Boolean, val port: Int?)

object CloseDay {
    /** Projects worked on today (a session, a commit, changes) or with something open; and what closing each takes. */
    fun plans(deck: Deck, settings: Settings.State, now: Long = System.currentTimeMillis()): List<DayPlan> = deck.projects.mapNotNull { p ->
        val dir = settings.localOf(p.id).dir ?: return@mapNotNull null
        val git = Git.state.value[dir.path]
        val pulse = Pulses.state.value[dir.path]
        val today = 18 * 3_600_000L
        val worked = (git?.lastAt ?: 0) > now - today || Receipts.state.value[dir.path].orEmpty().any { now - it.endAt < today } ||
            (pulse?.claudeAt ?: 0) > now - today
        val steps = buildList {
            if ((git?.changes ?: 0) > 0) add(DayStep.COMMIT)
            if (git != null && git.hasRemote && (git.ahead > 0 || git.changes > 0 || (git.upstream == null && git.lastAt > 0))) add(DayStep.PUSH)
            if (pulse?.port != null) add(DayStep.SERVER)
            if (worked || isNotEmpty()) add(DayStep.NEXT)
        }
        if (steps.isEmpty()) null
        else DayPlan(p, dir, steps, git?.changes ?: 0, git?.ahead ?: 0, git?.upstream == null, pulse?.port)
    }
}

@Composable
fun CloseDayPage(deck: Deck) {
    val c = LocalDeck.current
    val settings by Settings.state.collectAsState()
    Git.state.collectAsState().value
    Pulses.state.collectAsState().value
    val scope = rememberCoroutineScope()
    val plans = remember(deck, settings.local) { CloseDay.plans(deck, settings) }
    // What's chosen (on by default), the commit messages and next steps typed, and how each step went.
    val off = remember { mutableStateMapOf<String, Boolean>() }
    val messages = remember { mutableStateMapOf<String, String>() }
    val nexts = remember { mutableStateMapOf<String, String>() }
    val status = remember { mutableStateMapOf<String, String>() }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    fun k(p: Project, s: DayStep) = p.id + ":" + s.name
    fun on(p: Project, s: DayStep) = off[k(p, s)] != true

    if (finished) { DayClosed(plans, nexts); return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 30.dp)) {
        Column(Modifier.widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                PageTitle(tr("Close the day", "סגירת היום"))
                Spacer(Modifier.height(6.dp))
                Text(tr("Choose what to close. YDeck does the rest, and tomorrow starts from the next step.",
                    "בוחרים מה לסגור. YDeck עושה את השאר, ומחר מתחילים מהצעד הבא."), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
            }
            if (plans.isEmpty()) {
                Text(tr("Nothing was worked on today, and nothing is open. Good night.", "היום לא עבדו על כלום, ושום דבר לא פתוח. לילה טוב."), color = c.muted, fontSize = 15.sp, fontFamily = PlexSans)
                return@Column
            }
            plans.forEach { plan ->
                key(plan.project.id) {
                    val p = plan.project
                    Column {
                        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Logo(p, 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                        }
                        Group {
                            plan.steps.forEach { s ->
                                Item {
                                    CheckBox(on(p, s)) { v -> if (!running) off[k(p, s)] = !v }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(when (s) {
                                            DayStep.COMMIT -> tr("Commit", "קומיט ל־") + count(plan.changes, "change", "changes", "שינוי אחד", "שינויים").let { if (I18n.rtl) it else " $it" }
                                            DayStep.PUSH -> if (plan.publish) tr("Put the branch on the server", "העלאת הענף לשרת") else tr("Push to the server", "דחיפה לשרת")
                                            DayStep.SERVER -> tr("Stop the dev server on :${plan.port}", "עצירת שרת הפיתוח ב־:${plan.port}")
                                            DayStep.NEXT -> tr("Leave the next step", "להשאיר את הצעד הבא")
                                        }, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans)
                                        when (s) {
                                            DayStep.COMMIT -> if (on(p, s)) Field(null, messages[p.id] ?: "", { messages[p.id] = it },
                                                tr("The message (empty: Claude writes it)", "ההודעה (ריק: Claude כותב)"))
                                            DayStep.NEXT -> if (on(p, s)) {
                                                val local = settings.localOf(p.id)
                                                val suggested = Pulses.state.value[plan.dir.path]?.handoff?.next.orEmpty()
                                                Field(null, nexts[p.id] ?: local.note.ifBlank { suggested }, { nexts[p.id] = it },
                                                    tr("The next step (empty: Claude suggests one)", "הצעד הבא (ריק: Claude מציע)"))
                                            }
                                            else -> {}
                                        }
                                    }
                                    StepState(status[k(p, s)])
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(if (running) tr("Closing…", "סוגר…") else tr("Close the day", "סגירת היום"), Tone.PRIMARY, Glyph.MOON, enabled = !running) {
                    running = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            plans.forEach { plan ->
                                val p = plan.project
                                fun step(s: DayStep, work: () -> String?) {
                                    if (s !in plan.steps || !on(p, s)) return
                                    status[k(p, s)] = RUNNING
                                    status[k(p, s)] = runCatching { work() }.getOrElse { it.message } ?: DONE
                                }
                                step(DayStep.COMMIT) {
                                    val msg = messages[p.id]?.takeIf { it.isNotBlank() } ?: GitOps.suggestMessage(plan.dir, null).let { (m, e) -> m ?: return@step e }
                                    GitOps.commit(plan.dir, msg, null)
                                }
                                // A failed commit leaves nothing new to push.
                                if (status[k(p, DayStep.COMMIT)].let { it == null || it == DONE }) step(DayStep.PUSH) { GitOps.push(plan.dir) }
                                step(DayStep.SERVER) { Servers.stop(plan.dir.path); null }
                                step(DayStep.NEXT) {
                                    var next = nexts[p.id] ?: settings.localOf(p.id).note
                                    if (next.isBlank()) {
                                        Handoffs.write(plan.dir)?.let { return@step it }
                                        next = ProjectFiles.readLocal(plan.dir)?.handoff?.next.orEmpty()
                                    }
                                    nexts[p.id] = next
                                    Settings.setNote(p.id, next)
                                    ProjectFiles.writeNext(plan.dir, next)
                                    null
                                }
                            }
                            Pulses.refresh(Settings.state.value.local.values.mapNotNull { it.folder })
                        }
                        running = false
                        if (status.values.all { it == DONE }) finished = true
                    }
                }
                if (status.values.any { it != DONE && it != RUNNING } && !running) {
                    Spacer(Modifier.width(14.dp))
                    Text(tr("Some steps didn't go through: see above.", "חלק מהצעדים לא עברו: הפרטים למעלה."), color = c.danger, fontSize = 13.sp, fontFamily = PlexSans)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private const val RUNNING = "…"
private const val DONE = "✓"

/** A step's state: nothing yet, at work, done, or what went wrong. */
@Composable
private fun StepState(s: String?) {
    val c = LocalDeck.current
    when (s) {
        null -> {}
        RUNNING -> Text("…", color = c.muted, fontSize = 16.sp, fontFamily = PlexSans)
        DONE -> GlyphIcon(Glyph.CHECK, 18.dp, c.liveText)
        else -> Text(s, color = c.danger, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = PlexSans, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
    }
}

/** The day, closed: tomorrow's first steps. */
@Composable
private fun DayClosed(plans: List<DayPlan>, nexts: Map<String, String>) {
    val c = LocalDeck.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(Glyph.MOON, 34.dp, c.accentText)
            Spacer(Modifier.height(16.dp))
            PageTitle(tr("The day is closed", "היום סגור"), 30)
            Spacer(Modifier.height(8.dp))
            Text(tr("Everything is saved and on the server. Tomorrow starts here:", "הכול שמור ובשרת. מחר מתחילים מכאן:"), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
            Spacer(Modifier.height(22.dp))
            Group {
                plans.forEach { plan ->
                    val next = nexts[plan.project.id].orEmpty()
                    if (next.isNotBlank()) Item {
                        Logo(plan.project, 24.dp)
                        Column(Modifier.weight(1f)) {
                            Text(plan.project.name, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                            Text(next, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content))
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            TextAction(tr("Back to open loops", "חזרה למה שפתוח"), accent = true) { Nav.set(Screen.Loops) }
        }
    }
}
