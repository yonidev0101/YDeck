package com.yonidev.ydeck.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.data.Computer
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.link.Card
import com.yonidev.ydeck.link.CardGroup
import com.yonidev.ydeck.link.CardItem

/*
 * An answer from YDeck as a card, in the same order every time: the answer in a line or two, then short lists by kind
 * (what waits for you, what's left, what's done, ideas, decisions, other facts), each line about one project with its
 * logo. A tap on a line opens what can be done with it; what was done shows quietly in its place.
 */

/** What can be done with a line of an answer. */
enum class CardAct(val label: String, val words: String) {
    GO("יאללה", "נשלח למחשב"),
    DONE("נעשה", "סומן כנעשה"),
    TAKE("ל״נשאר״", "עבר ל״נשאר״"),
    LEFT("ל״נשאר״", "נוסף ל״נשאר״"),
    IDEA("לרעיונות", "נוסף לרעיונות"),
    OPEN("לפרויקט", ""),
}

/** What can be done with [item], in [group]: only what fits, and "done" or "take" only on a line really in its list. */
fun actsOf(group: CardGroup, item: CardItem, canLaunch: Boolean): List<CardAct> {
    if (item.project.isEmpty()) return emptyList()
    return buildList {
        val todo = group.kind == CardGroup.LEFT || group.kind == CardGroup.WAITING
        if (todo && canLaunch) add(CardAct.GO)
        if (item.from == CardItem.FROM_LEFT) add(CardAct.DONE)
        if (item.from == CardItem.FROM_IDEAS) add(CardAct.TAKE)
        if (item.from.isEmpty() && group.kind == CardGroup.LEFT) add(CardAct.LEFT)
        if (item.from.isEmpty() && group.kind == CardGroup.IDEA) add(CardAct.IDEA)
        add(CardAct.OPEN)
    }
}

/** A line's key, for what was done with it: the answer, the list and the line. */
fun lineKey(answer: String, g: Int, i: Int) = "$answer/$g/$i"

/** A list's dot, in the dot's language: amber waits for you, a ring is still open, green is done. */
@Composable
private fun KindDot(kind: String) {
    val c = LocalDeck.current
    val m = Modifier.size(8.dp).clip(CircleShape)
    when (kind) {
        CardGroup.WAITING -> Box(m.background(c.open))
        CardGroup.LEFT -> Box(m.border(1.8.dp, c.fg, CircleShape))
        CardGroup.DONE -> Box(m.background(c.closed))
        CardGroup.IDEA -> Box(m.background(c.accentText))
        CardGroup.DECIDED -> Box(m.background(c.muted))
        else -> Box(m.border(1.5.dp, c.faint, CircleShape))
    }
}

@Composable
fun AnswerCard(
    id: String,
    card: Card,
    projects: List<Project>,
    computer: Computer.State?,
    /** What was done with each line, by [lineKey], and when. */
    done: Map<String, Pair<CardAct, Long>>,
    onAct: (key: String, item: CardItem, act: CardAct) -> Unit,
) {
    val c = LocalDeck.current
    var open by remember(id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (card.say.isNotBlank()) Text(card.say, color = c.fg, fontSize = 16.sp, lineHeight = 24.sp, style = TextStyle(textDirection = dirOf(card.say)))
        card.groups.forEachIndexed { g, group ->
            val shape = RoundedCornerShape(18.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(vertical = 6.dp)) {
                Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindDot(group.kind)
                    Text(group.title.ifBlank { titleOf(group.kind) }, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(group.items.size.toString(), color = c.faint, fontSize = 12.sp)
                }
                group.items.forEachIndexed { i, item ->
                    val key = lineKey(id, g, i)
                    val acts = actsOf(group, item, computer?.canLaunch == true)
                    Line(item, projects.firstOrNull { it.id == item.project }, crossed = done[key]?.first == CardAct.DONE,
                        onClick = if (acts.isNotEmpty()) ({ open = if (open == key) null else key }) else null)
                    val was = done[key]
                    when {
                        was?.first == CardAct.GO && computer != null -> Box(Modifier.padding(start = 44.dp, end = 14.dp, bottom = 8.dp)) { GoStatus(computer, item.project, was.second) }
                        was != null -> Row(Modifier.padding(start = 44.dp, end = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Check, null, tint = c.closed, modifier = Modifier.size(15.dp))
                            Text(was.first.words, color = c.faint, fontSize = 12.5.sp)
                        }
                    }
                    AnimatedVisibility(open == key && was == null) {
                        Row(Modifier.padding(start = 44.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            acts.forEachIndexed { n, act ->
                                Pill(act.label, strong = n == 0 && act != CardAct.OPEN, outlined = act == CardAct.OPEN) { open = null; onAct(key, item, act) }
                            }
                        }
                    }
                }
            }
        }
        if (card.more.isNotBlank()) MarkdownView(card.more, Modifier.fillMaxWidth())
    }
}

/** One line: the project's logo, then the words; a line marked done is crossed out quietly. */
@Composable
private fun Line(item: CardItem, p: Project?, crossed: Boolean, onClick: (() -> Unit)?) {
    val c = LocalDeck.current
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p != null) Box(Modifier.padding(top = 1.dp)) { Logo(p, 20.dp) } else Box(Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (p != null) Text(p.name, color = c.faint, fontSize = 11.5.sp)
            Text(item.text, color = if (crossed) c.faint else c.fg, fontSize = 14.5.sp, lineHeight = 21.sp,
                style = TextStyle(textDirection = dirOf(item.text), textDecoration = if (crossed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
        }
    }
}

/** A list's heading when Claude gave none. */
private fun titleOf(kind: String) = when (kind) {
    CardGroup.LEFT -> "מה נשאר"
    CardGroup.WAITING -> "מחכה לך"
    CardGroup.DONE -> "מה נעשה"
    CardGroup.IDEA -> "רעיונות"
    CardGroup.DECIDED -> "מה הוחלט"
    else -> "עוד"
}

/** The words a card is read aloud with: the answer, then each list and its lines, briefly. */
fun spoken(card: Card, name: (String) -> String): String = buildString {
    append(card.say)
    card.groups.forEach { g ->
        append("\n").append(g.title.ifBlank { titleOf(g.kind) }).append(": ")
        append(g.items.joinToString("; ") { (if (it.project.isNotEmpty()) name(it.project) + ", " else "") + it.text })
        append(".")
    }
}

