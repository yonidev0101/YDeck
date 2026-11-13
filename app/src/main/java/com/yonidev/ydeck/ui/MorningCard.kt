package com.yonidev.ydeck.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.data.Computer
import com.yonidev.ydeck.data.Deck
import com.yonidev.ydeck.link.Morning
import com.yonidev.ydeck.link.MorningTopic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * The good morning, at the top of the projects: what YDeck wrote this morning (two or three topics, each with its
 * project, what it noticed, a question, and buttons), or, before it came, when it will and a way to try it now.
 * The topics are acted on with one tap: let's go (Claude opens in that project), tomorrow, or talk about it.
 */

/** What became of a topic on this phone: opened in Claude, put off, talked about; a suggestion taken or let go. */
enum class TopicDone(val words: String) {
    GO("נשלח למחשב"), LATER("יזכיר מחר"), TALK("פתחנו שיחה"), IDEA("נוסף לרעיונות"), LEFT("נוסף ל״נשאר״"), NO("ירד מהפרק")
}

/** What was done with a topic, and when (a "let's go" is followed from then on). */
data class Handled(val how: TopicDone, val at: Long)

@Composable
fun MorningCard(
    computer: Computer.State,
    deck: Deck,
    /** What was done with each topic, by [topicKey]. */
    done: Map<String, Handled>,
    onTry: () -> Unit,
    onGo: (MorningTopic) -> Unit,
    onLater: (MorningTopic) -> Unit,
    onTalk: (MorningTopic) -> Unit,
    /** A suggestion from the day's review: into the ideas or what's left, or let go. */
    onTake: (MorningTopic, TopicDone) -> Unit,
    onPick: (projectId: String, on: Boolean) -> Unit,
) {
    val c = LocalDeck.current
    var picking by remember { mutableStateOf(false) }
    val m = computer.morning?.takeIf { isToday(it.at) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(shape).background(c.surface).border(1.dp, c.line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val dot = when {
            computer.morningBusy -> c.accentText
            m == null && computer.morningError.isNotEmpty() -> c.danger
            m != null -> c.open
            else -> null
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            else Box(Modifier.size(9.dp).clip(CircleShape).border(2.dp, c.faint, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("הבריף של היום", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (m != null) Text("נכתב ב־" + hhmm(m.at), color = c.faint, fontSize = 12.sp)
            }
            if (m != null && !computer.morningBusy) Pill("בריף חדש", strong = false, outlined = true, onClick = onTry)
        }
        when {
            computer.morningBusy -> {
                val n = computer.morningPicks.size
                Text("YDeck כותב בריף חדש. עובר על " + (if (n <= 1) "הפרויקטים" else "$n פרויקטים") + "…", color = c.muted, fontSize = 14.sp)
                Text("בדרך כלל פחות מדקה", color = c.faint, fontSize = 12.sp)
            }
            m != null -> Today(computer, m, deck, done, onGo, onLater, onTalk, onTake)
            computer.morningError.isNotEmpty() -> {
                Text("לא הצליח הפעם: ${computer.morningError}", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("לנסות שוב", strong = false, onClick = onTry) }
            }
            computer.morningAt < 0 -> {
                Text("YDeck עובר על הפרויקטים שעבדת עליהם לאחרונה, ואומר לך מה נעשה, מה כדאי לעשות היום ומה מחכה לך.",
                    color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("ליצור בריף עכשיו", strong = true, onClick = onTry)
                    Pill("על אילו פרויקטים", strong = false, outlined = true) { picking = true }
                }
            }
            else -> {
                val at = computer.morningAt.coerceAtLeast(0)
                val later = LocalTime.now().isBefore(LocalTime.of(at / 60, at % 60))
                val n = computer.morningPicks.size
                Text("${if (later) "היום" else "מחר"} ב־${at / 60}:%02d YDeck יעבור על ${if (n == 1) "פרויקט אחד" else "$n הפרויקטים"} שעבדת עליהם לאחרונה, ויכתוב לך כאן מה כדאי לעשות היום. זה יגיע גם כהתראה.".format(at % 60),
                    color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("ליצור בריף עכשיו", strong = true, onClick = onTry)
                    Pill("על אילו פרויקטים", strong = false, outlined = true) { picking = true }
                }
            }
        }
        if (m != null || computer.morningBusy) Text("על אילו פרויקטים · ${computer.morningPicks.size}", color = c.faint, fontSize = 12.sp,
            modifier = Modifier.clickable { picking = true })
    }
    if (picking) PickSheet(deck, computer.morningPicks.toSet(), { picking = false }, onPick)
}

/** This morning's: the opening line, then each topic not acted on yet, with its buttons; what was acted on, quietly. */
@Composable
private fun Today(computer: Computer.State, m: Morning, deck: Deck, done: Map<String, Handled>, onGo: (MorningTopic) -> Unit, onLater: (MorningTopic) -> Unit,
                  onTalk: (MorningTopic) -> Unit, onTake: (MorningTopic, TopicDone) -> Unit) {
    val c = LocalDeck.current
    if (m.hello.isNotBlank()) Text(m.hello, color = c.fg, fontSize = 15.sp, lineHeight = 23.sp, style = TextStyle(textDirection = dirOf(m.hello)))
    // An older computer sent only words: those, as they are.
    if (m.topics.isEmpty()) { MarkdownView(m.text, Modifier.fillMaxWidth()); return }
    val moved = moved(computer, deck)
    if (moved.isNotEmpty()) Part(PartKind.DONE, "מה נעשה", moved.size) {
        moved.forEach { (p, words) -> PartLine(p, words) }
    }
    Part(PartKind.TODAY, "מה להיום", m.topics.size) { Topics(computer, m, deck, done, onGo, onLater, onTalk, onTake) }
    val waiting = computer.waiting.take(3)
    if (waiting.isNotEmpty()) Part(PartKind.WAITING, "מחכה לך", computer.waiting.size) {
        waiting.forEach { w -> PartLine(deck.byId(w.project), w.text.ifBlank { "Claude מחכה לתשובה שלך" }) }
    }
}

/** The kinds of the brief's parts, each with its dot: green done, a ring for today, amber waiting for you. */
private enum class PartKind { DONE, TODAY, WAITING }

/** One part of the brief: its dot, its name and how many, then its lines, in a box of its own. */
@Composable
private fun Part(kind: PartKind, title: String, count: Int, body: @Composable () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface2).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dot = Modifier.size(8.dp).clip(CircleShape)
            when (kind) {
                PartKind.DONE -> Box(dot.background(c.closed))
                PartKind.TODAY -> Box(dot.border(1.8.dp, c.fg, CircleShape))
                PartKind.WAITING -> Box(dot.background(c.open))
            }
            Text(title, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(count.toString(), color = c.faint, fontSize = 12.sp)
        }
        body()
    }
}

/** A line of the brief: the project's logo and name, then the words. */
@Composable
private fun PartLine(p: com.yonidev.ydeck.data.Project?, words: String) {
    val c = LocalDeck.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p != null) Box(Modifier.padding(top = 1.dp)) { Logo(p, 20.dp) }
        Column(Modifier.weight(1f)) {
            if (p != null) Text(p.name, color = c.faint, fontSize = 11.5.sp)
            Text(words, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, style = TextStyle(textDirection = dirOf(words)))
        }
    }
}

/**
 * What was done lately, from each project's journal: its last day with something in it, today too (what was marked
 * done, else how many commits), newest first, four at most.
 */
private fun moved(computer: Computer.State, deck: Deck): List<Pair<com.yonidev.ydeck.data.Project, String>> {
    val today = LocalDate.now().toString()
    return deck.active.mapNotNull { p ->
        val day = computer.memory[p.id]?.journal.orEmpty().firstOrNull { it.date <= today && (it.done.isNotEmpty() || it.commits.isNotEmpty()) } ?: return@mapNotNull null
        val words = if (day.done.isNotEmpty()) day.done.take(2).joinToString(" · ") else "${day.commits.size} commits"
        Triple(p, words, day.date)
    }.sortedByDescending { it.third }.take(4).map { it.first to it.second }
}

/** Today's topics, each with its project, what YDeck noticed, and its buttons; what was acted on, quietly. */
@Composable
private fun Topics(computer: Computer.State, m: Morning, deck: Deck, done: Map<String, Handled>, onGo: (MorningTopic) -> Unit, onLater: (MorningTopic) -> Unit,
                   onTalk: (MorningTopic) -> Unit, onTake: (MorningTopic, TopicDone) -> Unit) {
    val c = LocalDeck.current
    m.topics.forEachIndexed { i, t ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        val p = deck.byId(t.project)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p != null) Logo(p, 22.dp)
                Text(p?.name ?: t.project, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                KindChip(t.kind)
            }
            val suggest = t.kind == MorningTopic.SUGGEST && t.step.isNotBlank()
            val words = t.say + if (t.ask.isNotBlank()) " " + t.ask else ""
            Text(words, color = c.fg, fontSize = 14.sp, lineHeight = 21.sp, style = TextStyle(textDirection = dirOf(words)))
            // A suggestion shows the line it would add, as it would be written.
            if (suggest) Text("״" + t.step + "״", color = c.muted, fontSize = 13.5.sp, lineHeight = 20.sp, style = TextStyle(textDirection = dirOf(t.step)),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(horizontal = 12.dp, vertical = 8.dp))
            val was = done[topicKey(m, i)]
            when {
                was?.how == TopicDone.GO -> GoStatus(computer, t.project, was.at)
                was != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Check, null, tint = c.closed, modifier = Modifier.size(16.dp))
                    Text(was.how.words, color = c.faint, fontSize = 12.5.sp)
                }
                suggest -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.add == "left") { Pill("ל״נשאר״", strong = true) { onTake(t, TopicDone.LEFT) }; Pill("לרעיונות", strong = false) { onTake(t, TopicDone.IDEA) } }
                    else { Pill("לרעיונות", strong = true) { onTake(t, TopicDone.IDEA) }; Pill("ל״נשאר״", strong = false) { onTake(t, TopicDone.LEFT) } }
                    Pill("לא", strong = false, outlined = true) { onTake(t, TopicDone.NO) }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p != null) Pill("יאללה", strong = true) { onGo(t) }
                    Pill("מחר", strong = false) { onLater(t) }
                    Pill("לדבר על זה", strong = false, outlined = true) { onTalk(t) }
                }
            }
        }
    }
}

/**
 * After "let's go": what became of it, followed live. Sent, then whether the computer opened Claude (or why not), then
 * Claude at work, waiting for you, or done with what it did.
 */
@Composable
internal fun GoStatus(computer: Computer.State, project: String, at: Long) {
    val c = LocalDeck.current
    val launch = computer.launches.lastOrNull { it.first.project == project && it.first.at >= at - 60_000 }
    val live = computer.live(project)
    val finished = live?.receipts?.firstOrNull { it.startAt >= at - 60_000 && it.endAt > 0 }
    val (words, color, done) = when {
        launch == null || launch.second == null -> Triple("נשלח למחשב…", c.faint, false)
        launch.second?.ok == false -> Triple("לא נפתח: " + when (launch.second?.why) {
            "off" -> "פתיחת Claude מהטלפון כבויה במחשב"
            "stale" -> "הבקשה הגיעה מאוחר מדי"
            "folder" -> "לפרויקט אין תיקייה במחשב"
            "claude" -> "Claude Code לא נמצא במחשב"
            else -> "משהו לא עבד במחשב"
        }, c.danger, false)
        live?.agentMode == "WAITING" -> Triple("Claude מחכה לך במחשב", c.open, false)
        live?.agentMode == "WORKING" -> Triple("Claude עובד על זה…", c.accentText, false)
        finished != null -> Triple("Claude סיים: " + finished.title, c.closed, true)
        else -> Triple("Claude נפתח במחשב", c.muted, false)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (done) Icon(Icons.Outlined.Check, null, tint = c.closed, modifier = Modifier.size(16.dp))
        else Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(words, color = if (done) c.muted else color, fontSize = 12.5.sp, maxLines = 2, style = TextStyle(textDirection = dirOf(words)))
    }
}

/** Finish, stuck, idea, decide: a small word in the dot's colors. */
@Composable
private fun KindChip(kind: String) {
    val c = LocalDeck.current
    val (word, color) = when (kind) {
        MorningTopic.FINISH -> "לסיים" to c.closed
        MorningTopic.STUCK -> "תקוע" to c.open
        MorningTopic.IDEA -> "רעיון" to c.accentText
        MorningTopic.DECIDE -> "להחליט" to c.accentText
        MorningTopic.SUGGEST -> "הצעה מהסקירה" to c.accentText
        else -> return
    }
    Text(word, color = color, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
internal fun Pill(text: String, strong: Boolean, outlined: Boolean = false, onClick: () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(99.dp)
    Text(text, color = if (strong) Color.White else if (outlined) c.muted else c.fg, fontSize = 13.sp, fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.clip(shape)
            .then(if (strong) Modifier.background(c.accent) else if (outlined) Modifier.border(1.dp, c.line, shape) else Modifier.background(c.surface2))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}

/** Which projects the good morning goes over: each one on or off, sent to the computer. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PickSheet(deck: Deck, picks: Set<String>, onDismiss: () -> Unit, onPick: (String, Boolean) -> Unit) {
    val c = LocalDeck.current
    var on by remember { mutableStateOf(picks) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("על אילו פרויקטים", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("לבד: אלה שעבדת עליהם בשבועיים האחרונים. אפשר להוסיף או להוציא.", color = c.faint, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 8.dp))
            deck.active.forEach { p ->
                val checked = p.id in on
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { on = if (checked) on - p.id else on + p.id; onPick(p.id, !checked) }
                    .padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Logo(p, 26.dp)
                    Text(p.name, color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).then(if (checked) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.faint, RoundedCornerShape(6.dp))),
                        contentAlignment = Alignment.Center) { if (checked) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

/** A topic's key, for what was done with it. */
fun topicKey(m: Morning, i: Int) = "${m.id}/$i"

private fun isToday(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

private fun hhmm(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime().let { "%d:%02d".format(it.hour, it.minute) }
