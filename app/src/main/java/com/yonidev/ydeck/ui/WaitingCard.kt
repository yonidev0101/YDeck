package com.yonidev.ydeck.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.link.Launched
import com.yonidev.ydeck.link.Reply
import com.yonidev.ydeck.link.Waiting

/*
 * A Claude Code session waiting for the developer, held on the computer while they're away: what Claude asks, the
 * reply YDeck drafted from the project's records and where it comes from, and the reply to send. The same card as in
 * Ask YDeck on the computer, in Claude's "waiting" look (the amber wash). Never a permission: those stay in the terminal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaitingCard(
    w: Waiting,
    p: Project?,
    /** The phone may reply (on at the computer). */
    canReply: Boolean,
    /** The latest reply sent here to this question, and what the computer did with it (null: not yet). */
    sent: Pair<Reply, Launched?>?,
    onSend: (text: String, choices: Map<String, String>) -> Unit,
) {
    val c = LocalDeck.current
    var text by remember(w.id, w.draft) { mutableStateOf(w.draft) }
    var picked by remember(w.id, w.draftChoices) { mutableStateOf(w.draftChoices) }
    val ready = if (w.kind == Waiting.CHOICE) w.questions.all { picked[it.question] != null } else text.isNotBlank()
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
            .border(1.dp, CHANGED_DOT.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClaudeDot("WAITING", 7.dp)
            Spacer(Modifier.width(8.dp))
            if (p != null) { Logo(p, 18.dp); Spacer(Modifier.width(6.dp)) }
            Text("Claude מחכה לך" + (p?.let { " · ${it.name}" } ?: ""), color = c.openText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ago(w.at), color = c.muted, fontSize = 12.sp)
        }
        if (w.kind == Waiting.CHOICE) w.questions.forEach { q ->
            // The question large and thin; the choices as big buttons, one tap each. YDeck's draft is marked "הצעה".
            Text(q.question, color = c.fg, fontSize = 21.sp, lineHeight = 29.sp, fontFamily = Display, fontWeight = FontWeight.Light,
                style = TextStyle(textDirection = dirOf(q.question)), modifier = Modifier.padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.forEach { o ->
                    val on = picked[q.question] == o
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape).border(if (on) 2.dp else 1.dp, if (on) c.fg else androidx.compose.ui.graphics.Color(0xFF2E2E34), shape)
                            .clickable(enabled = canReply) { picked = picked + (q.question to o) }.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .then(if (on) Modifier.background(c.fg) else Modifier.border(2.dp, androidx.compose.ui.graphics.Color(0xFF4A4A52), androidx.compose.foundation.shape.CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.surface)) }
                        Text(o, color = c.fg, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, style = TextStyle(textDirection = dirOf(o)), modifier = Modifier.weight(1f))
                        if (w.draftChoices[q.question] == o) Text("הצעה", color = c.accentText, fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accentTint).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        } else {
            val asked = w.text.lines().filter { it.isNotBlank() }.takeLast(5).joinToString("\n")
            Text(asked, color = c.fg, fontSize = 14.5.sp, lineHeight = 21.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(asked)))
        }
        when {
            w.drafting -> Text("YDeck מנסח תשובה מתוך מה שרשום בפרויקט…", color = c.muted, fontSize = 13.sp)
            w.source.isNotBlank() -> Text((if (w.sure) "לפי מה שרשום: " else "הצעה, שום דבר רשום לא מכריע: ") + w.source,
                color = if (w.sure) c.fg else c.muted, fontSize = 13.sp, lineHeight = 19.sp, style = TextStyle(textDirection = dirOf(w.source)))
        }
        if (!canReply) {
            Text("כדי לענות מהטלפון, מדליקים פעם אחת במחשב: ⁨YDeck⁩ ← הגדרות ← טלפון ← ״הטלפון יכול לענות לסשנים שמחכים״.",
                color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            return@Column
        }
        if (w.kind == Waiting.TEXT) {
            val shape = RoundedCornerShape(14.dp)
            Box(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(horizontal = 14.dp, vertical = 11.dp)) {
                val style = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = PlexSans, lineHeight = 22.sp, textDirection = TextDirection.Content)
                if (text.isEmpty()) Text("התשובה שלך ל־Claude", style = style.copy(color = c.faint))
                BasicTextField(text, { text = it }, textStyle = style, cursorBrush = SolidColor(c.accentText), maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
        }
        val status = sent?.let { (_, done) ->
            when {
                done == null -> "נשלח למחשב…"
                done.ok -> "התשובה הגיעה ל־Claude"
                done.why == "off" -> "התשובות מהטלפון כבויות במחשב."
                done.why == "stale" -> "התשובה הגיעה למחשב מאוחר מדי."
                else -> "הסשן כבר לא מחכה לתשובה הזו."
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(99.dp)
            Text("לשלוח ל־Claude", color = if (ready) c.bg else c.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(shape).background(if (ready) c.fg else c.line).clickable(enabled = ready) { onSend(text.trim(), picked) }
                    .heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 11.dp))
            if (status != null) Text(status, color = c.muted, fontSize = 13.sp, maxLines = 2)
        }
    }
}
