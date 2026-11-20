package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The project's dossier on its overview (see Dossier.kt and Saving.kt): the goal and its share, what was built, and
 * the save card. Each says what it knows in words, and offers the one thing to do next.
 */

private val CARD_SHAPE = RoundedCornerShape(18.dp)

/** A white card on the page, as the dossier's cards are. */
@Composable
private fun DossierCard(border: Color? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth().clip(CARD_SHAPE).background(c.surface)
        .border(if (border != null) 2.dp else 1.dp, border ?: c.line, CARD_SHAPE).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) =
    Text(text, color = LocalDeck.current.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = modifier)

@Composable
private fun Quiet(text: String, size: Int = 13) =
    Text(text, color = LocalDeck.current.muted, fontSize = size.sp, lineHeight = (size + 7).sp, fontFamily = PlexSans, style = TextStyle(textDirection = dirOf(text)))

// ── the goal ──

/**
 * The near goal: the ring, its share and its steps, ticked off here (Claude ticks them as it works). With no goal:
 * the one Claude suggests, to take (its title can be changed first) or leave, or a way to ask for one.
 */
@Composable
fun GoalCard(dir: File) {
    val c = LocalDeck.current
    val pulse = Pulses.state.collectAsState().value[dir.path] ?: Pulse()
    val suggested = Dossier.suggested.collectAsState().value[dir.path]
    val busy = dir.path in Dossier.busy.collectAsState().value
    val step = Dossier.step.collectAsState().value[dir.path]
    val error = Dossier.errors.collectAsState().value[dir.path]
    val scope = rememberCoroutineScope()
    fun write(g: Goal?) = scope.launch { withContext(Dispatchers.IO) { ProjectFiles.writeGoal(dir, g) }; Pulses.refreshSoon() }
    val goal = pulse.goal

    DossierCard {
        if (goal != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val share = goal.share
                ProgressMark(share?.first ?: 0, share?.second ?: 1, 68.dp) {
                    Text(goal.percent?.let { "$it%" } ?: "", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Goal", "היעד"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
                    Text(goal.title, color = c.fg, fontSize = 20.sp, lineHeight = 26.sp, fontFamily = PlexSans, fontWeight = FontWeight.Medium, style = TextStyle(textDirection = dirOf(goal.title)))
                    share?.let { (d, t) ->
                        Text(if (d == t) tr("All $t steps done", "כל $t הצעדים נעשו") else tr("$d of $t steps", "$d מתוך $t צעדים"), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
                    }
                }
                TextAction(tr("Remove", "הסרה")) { write(null) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            val next = goal.steps.indexOfFirst { !it.done }
            goal.steps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable { write(goal.copy(steps = goal.steps.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x })) }
                    .padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    StepDot(s.done, i == next)
                    Spacer(Modifier.width(12.dp))
                    Text(s.text, color = if (s.done) c.faint else c.fg, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans,
                        fontWeight = if (i == next) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f),
                        style = TextStyle(textDirection = dirOf(s.text), textDecoration = if (s.done) TextDecoration.LineThrough else null))
                    IconButton(Glyph.TRASH, tr("Remove", "הסרה"), 24.dp, c.faint, filled = false) { write(goal.copy(steps = goal.steps.filterIndexed { j, _ -> j != i })) }
                }
            }
            var draft by remember(dir) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(null, draft, { draft = it }, tr("Another step", "עוד צעד"), modifier = Modifier.weight(1f))
                Pill(tr("Add", "הוספה"), Tone.SOFT, enabled = draft.isNotBlank(), small = true) {
                    val t = draft.trim(); draft = ""
                    write(goal.copy(steps = goal.steps + GoalStep(t, false)))
                }
            }
        } else if (suggested != null) {
            var title by remember(suggested) { mutableStateOf(suggested.title) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.SPARKLE, 16.dp, c.accentText)
                Spacer(Modifier.width(8.dp))
                Title(tr("Claude suggests a goal", "Claude מציע יעד"), Modifier.weight(1f))
            }
            Field(null, title, { title = it }, tr("The goal", "היעד"))
            suggested.steps.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) { StepDot(false, false); Spacer(Modifier.width(12.dp)); Quiet(s.text, 14) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(tr("Set as the goal", "לקבוע כיעד"), Tone.PRIMARY, enabled = title.isNotBlank(), small = true) {
                    scope.launch { withContext(Dispatchers.IO) { Dossier.takeGoal(dir, suggested.copy(title = title.trim())) } }
                }
                TextAction(tr("Not now", "לא עכשיו")) { Dossier.dismissGoal(dir) }
            }
        } else {
            var title by remember(dir) { mutableStateOf("") }
            Title(tr("No goal yet", "עוד אין יעד"))
            Quiet(tr("One near goal, with steps toward it, gives the project a share done. Claude can suggest one from the code and the history, or write your own.",
                "יעד קרוב אחד, עם צעדים אליו, נותן לפרויקט אחוז התקדמות. Claude יכול להציע אחד מהקוד ומההיסטוריה, או שכותבים לבד."))
            if (busy) Quiet(step ?: tr("Claude is reading the project…", "Claude קורא את הפרויקט…"))
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (Tools.claude != null) Pill(tr("Suggest a goal", "להציע יעד"), Tone.PRIMARY, Glyph.SPARKLE, small = true) { Dossier.start(dir) }
                Field(null, title, { title = it }, tr("Or write it: first version in the store…", "או לכתוב: גרסה ראשונה בחנות…"), modifier = Modifier.weight(1f))
                Pill(tr("Set", "קביעה"), Tone.SOFT, enabled = title.isNotBlank(), small = true) { val t = title.trim(); title = ""; write(Goal(t, emptyList())) }
            }
            error?.let { Text(it, color = c.danger, fontSize = 12.sp, fontFamily = PlexSans) }
        }
    }
}

@Composable
private fun StepDot(done: Boolean, next: Boolean) {
    val c = LocalDeck.current
    when {
        done -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(CLEAN_DOT), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.CHECK, 11.dp, Color(0xFF0B0B0D)) }
        next -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).border(2.dp, c.accent, RoundedCornerShape(9.dp)))
        else -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).border(2.dp, c.line, RoundedCornerShape(9.dp)))
    }
}

// ── what was built ──

/** Everything the project can do, the first few and the rest on a click; filled by Claude when it's empty. */
@Composable
fun BuiltCard(dir: File) {
    val c = LocalDeck.current
    val built = (Pulses.state.collectAsState().value[dir.path] ?: Pulse()).built
    val busy = dir.path in Dossier.busy.collectAsState().value
    val step = Dossier.step.collectAsState().value[dir.path]
    var all by remember(dir) { mutableStateOf(false) }
    DossierCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title(tr("What was built", "מה נבנה") + if (built.isNotEmpty()) " · ${built.size}" else "", Modifier.weight(1f))
            if (Tools.claude != null && !busy && built.isNotEmpty()) TextAction(tr("Update", "עדכון"), accent = true) { Dossier.start(dir) }
        }
        if (busy) Quiet(step ?: tr("Claude is reading the project…", "Claude קורא את הפרויקט…"))
        if (built.isEmpty()) {
            if (!busy) {
                Quiet(tr("Nothing listed yet. Claude can read the code and the history and list what the project can do.",
                    "עוד לא רשום כלום. Claude יכול לקרוא את הקוד ואת ההיסטוריה ולרשום מה הפרויקט יודע לעשות."))
                if (Tools.claude != null) Pill(tr("List with Claude", "לרשום עם Claude"), Tone.SOFT, Glyph.SPARKLE, small = true) { Dossier.start(dir) }
            }
        } else {
            (if (all) built else built.take(8)).forEach { t ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp)) { Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(c.faint)) }
                    Spacer(Modifier.width(10.dp))
                    Text(t, color = c.fg, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans, style = TextStyle(textDirection = dirOf(t)))
                }
            }
            if (built.size > 8) TextAction(if (all) tr("Fewer", "פחות") else tr("All ${built.size}", "כל ה־${built.size}"), accent = true) { all = !all }
        }
    }
}

// ── saving ──

/**
 * What isn't saved, in words, and what to do about it, with one button that does it (see [Saving]). Written again
 * only when git's state changes; all saved says so in a line.
 */
@Composable
fun SaveCardView(p: Project, dir: File) {
    val c = LocalDeck.current
    val git = Git.state.collectAsState().value[dir.path]
    val card = Saving.cards.collectAsState().value[dir.path]
    val busy = dir.path in Saving.busy.collectAsState().value
    val error = Saving.errors.collectAsState().value[dir.path]
    val scope = rememberCoroutineScope()
    var result by remember(dir) { mutableStateOf<String?>(null) }
    // Asked again whenever git's state moves; Saving asks Claude only when it really changed.
    LaunchedEffect(git) { if (git != null) Saving.ensure(dir) }
    if (git == null) return
    val clean = card?.clean == true
    DossierCard(border = if (card != null && !clean) CHANGED_DOT else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(if (clean) CLEAN_DOT else CHANGED_DOT))
            Spacer(Modifier.width(10.dp))
            Title(tr("Saving", "שמירה"), Modifier.weight(1f))
            TextAction(tr("Details", "פרטים")) { Nav.set(Screen.Project(p.id, Tab.GIT)) }
        }
        when {
            card == null && busy -> Quiet(tr("Looking at what's open…", "בודק מה פתוח…"), 14)
            card == null -> error?.let { Text(it, color = c.danger, fontSize = 13.sp, fontFamily = PlexSans) } ?: Quiet(gitWords(git), 14)
            else -> {
                Text(card.state, color = c.fg, fontSize = 14.sp, lineHeight = 22.sp, fontFamily = PlexSans, style = TextStyle(textDirection = dirOf(card.state)))
                if (card.proposal.isNotBlank()) Quiet(tr("Suggestion: ", "הצעה: ") + card.proposal)
                if (card.steps.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (busy) tr("Working…", "עובד…") else tr("Do it", "תעשה"), Tone.PRIMARY, enabled = !busy, small = true) {
                        result = null
                        scope.launch { result = withContext(Dispatchers.IO) { Saving.runAndKeep(dir) } ?: "" }
                    }
                    Quiet(stepsWords(card.steps))
                }
                when {
                    result == "" -> Text(tr("Done.", "נעשה."), color = c.liveText, fontSize = 13.sp, fontFamily = PlexSans)
                    result != null -> Text(result!!, color = c.danger, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans)
                }
            }
        }
    }
}

/** What the button will do, short: "commit · push". */
fun stepsWords(steps: List<SaveStep>): String = steps.joinToString(" · ") { s ->
    when (s.kind) {
        SaveStep.COMMIT -> tr("commit", "שמירה")
        SaveStep.PUSH -> tr("push", "העלאה")
        SaveStep.PULL -> tr("bring from the server", "הורדה מהשרת")
        SaveStep.IGNORE -> tr("keep out of git", "הוצאה מ־git")
        SaveStep.MERGE -> tr("merge ${s.branch}", "מיזוג ${s.branch}")
        else -> s.kind
    }
}
