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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.yonidev.ydeck.link.Waiting

/*
 * The sessions waiting for the developer, at the top of Ask YDeck: what Claude asks, YDeck's suggested reply and where
 * it comes from, and the reply to send (or the terminal, as always). In Claude's own "waiting" look: the amber wash.
 */

/** Every session waiting now, newest first; nothing when none is. */
@Composable
fun WaitingCards(deck: Deck, modifier: Modifier = Modifier) {
    val waiting by SessionAsks.waiting.collectAsState()
    if (waiting.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        waiting.forEach { w -> WaitingCard(w, deck.byId(w.project)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaitingCard(w: Waiting, p: Project?) {
    val c = LocalDeck.current
    val dark = c == Dark
    // The reply: YDeck's draft once it comes, until the developer changes it.
    var text by remember(w.id, w.draft) { mutableStateOf(w.draft) }
    var picked by remember(w.id, w.draftChoices) { mutableStateOf(w.draftChoices) }
    val ready = if (w.kind == Waiting.CHOICE) w.questions.all { picked[it.question] != null } else text.isNotBlank()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CHANGED_DOT.copy(alpha = if (dark) 0.14f else 0.12f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClaudeDot(ClaudeMode.WAITING, dark, 7.dp)
            Spacer(Modifier.width(8.dp))
            if (p != null) { Logo(p, 16.dp); Spacer(Modifier.width(6.dp)) }
            Text(tr("Claude needs you", "Claude מחכה לך") + (p?.let { " · ${it.name}" } ?: ""), color = c.amberText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ago(w.at), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
        }
        // What Claude asks.
        if (w.kind == Waiting.CHOICE) w.questions.forEach { q ->
            Text(q.question, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                q.options.forEach { o ->
                    val on = picked[q.question] == o
                    val shape = RoundedCornerShape(99.dp)
                    Text(o, color = if (on) c.bg else c.fg, fontSize = 13.sp, fontFamily = PlexSans,
                        modifier = Modifier.clip(shape).background(if (on) c.fg else c.surface).border(1.dp, if (on) c.fg else c.line, shape)
                            .clickable { picked = picked + (q.question to o) }.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        } else Text(w.text.lines().filter { it.isNotBlank() }.takeLast(5).joinToString("\n"), color = c.fg, fontSize = 13.5.sp, lineHeight = 20.sp, fontFamily = PlexSans,
            maxLines = 6, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        // YDeck's suggestion, and where it comes from.
        when {
            w.drafting -> Text(tr("YDeck is drafting a reply from the project's records…", "YDeck מנסח תשובה מתוך מה שרשום בפרויקט…"), color = c.muted, fontSize = 12.5.sp, fontFamily = PlexSans)
            w.source.isNotBlank() -> Text((if (w.sure) tr("From the records: ", "לפי מה שרשום: ") else tr("A suggestion, nothing recorded settles it: ", "הצעה, שום דבר רשום לא מכריע: ")) + w.source,
                color = if (w.sure) c.fg else c.muted, fontSize = 12.5.sp, lineHeight = 18.sp, fontFamily = PlexSans, style = TextStyle(textDirection = TextDirection.Content))
        }
        if (w.kind == Waiting.TEXT) {
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(horizontal = 12.dp, vertical = 9.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, lineHeight = 20.sp, textDirection = TextDirection.Content)
                if (text.isEmpty()) Text(tr("Your reply to Claude", "התשובה שלך ל־Claude"), style = style.copy(color = c.faint))
                BasicTextField(text, { text = it }, textStyle = style, cursorBrush = SolidColor(c.accent), maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(tr("Send to Claude", "לשלוח ל־Claude"), Tone.PRIMARY, enabled = ready, small = true) {
                SessionAsks.reply(w.id, SessionAsks.Answer(text.trim(), picked))
            }
            if (w.kind == Waiting.TEXT && w.draft.isBlank() && !w.drafting) TextAction(tr("Suggest a reply", "להציע תשובה"), accent = true) { SessionAsks.draft(w.id) }
            Spacer(Modifier.weight(1f))
            TextAction(tr("I'll answer in the terminal", "אענה בטרמינל")) { SessionAsks.dismiss(w.id) }
        }
    }
}
