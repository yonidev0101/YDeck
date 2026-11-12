package com.yonidev.ydeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.data.Computer
import com.yonidev.ydeck.data.Deck
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.link.Memory

@Composable
fun HomeScreen(
    deck: Deck,
    computer: Computer.State?,
    connecting: String?,
    onOpen: (Project) -> Unit,
    onPair: () -> Unit,
    onSample: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onAsk: () -> Unit,
    onReply: (waiting: String, text: String, choices: Map<String, String>) -> Unit,
) {
    val c = LocalDeck.current
    if (connecting != null) return Connecting(connecting)
    if (deck.projects.isEmpty()) return Welcome(onPair, onSample)
    val paired = computer?.paired == true
    PageScaffold(
        title = "YDeck",
        actions = {
            if (paired) IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "עדכון", tint = c.muted) }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "הגדרות", tint = c.muted) }
        },
        // Ask YDeck at the bottom, as the search window is on the computer: one reach away from anywhere.
        bottomBar = { if (paired) AskBar(computer?.chat, onAsk) },
    ) {
        item {
            Text(
                when {
                    paired && computer!!.stateAt > 0 -> "מ־${computer.name} · עודכן ${ago(computer.checkedAt.coerceAtLeast(computer.stateAt))}"
                    paired -> "מחובר ל־${computer!!.name} · מחכה שישלח"
                    else -> "לא מחובר למחשב"
                },
                color = c.faint, fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 6.dp),
            )
        }
        // What's left open in all the projects, as the computer's sidebar counts it (Claude's state is shown apart).
        // Sessions waiting for you come first, as on the computer's Now.
        val waiting = computer?.waiting.orEmpty()
        if (waiting.isNotEmpty()) {
            item { SectionHeader(if (waiting.size == 1) "מחכה לך" else "${waiting.size} מחכים לך") }
            items(waiting, key = { "w:" + it.id }) { w ->
                WaitingCard(w, deck.byId(w.project), computer!!.canReply, computer.replies.lastOrNull { it.first.waiting == w.id }) { text, choices -> onReply(w.id, text, choices) }
            }
        }
        val open = deck.projects.sumOf { p -> computer?.live(p.id)?.loops.orEmpty().count { it.kind != "AGENT" } }
        item { SectionHeader(when (open) { 0 -> "פרויקטים"; 1 -> "פרויקטים · דבר אחד פתוח"; else -> "פרויקטים · $open דברים פתוחים" }) }
        item { Line() }
        items(deck.projects, key = { it.id }) { p ->
            ProjectRow(p, computer?.memory?.get(p.id), computer?.live(p.id), onClick = { onOpen(p) })
            Divider(inset = 80.dp)
        }
        if (!paired) item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text("הפרויקטים האמיתיים שלך מגיעים מהמחשב.", color = c.muted, fontSize = 14.sp)
                TextButton(onClick = onPair, modifier = Modifier.padding(top = 4.dp)) {
                    Text("חיבור למחשב", color = c.accentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Line() = Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(LocalDeck.current.line))

/**
 * A project as Now lists it on the computer, in two lanes that never mix: Claude's state beside the name (its dot and
 * its words), and under it what's left open in the project (a loop's dot), else the next step in words.
 */
@Composable
private fun ProjectRow(p: Project, memory: Memory?, live: com.yonidev.ydeck.link.Live?, onClick: () -> Unit) {
    val c = LocalDeck.current
    val loops = live?.loops.orEmpty().filter { it.kind != "AGENT" }
    val agent = live?.agentMode?.takeIf { it == "WORKING" || it == "WAITING" || it == "OPEN" }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // A thin ring around the logo: what was done out of what was planned, as the project page's mark says it.
        val progress = memory?.progress()
        if (progress != null) RingedLogo(p, progress.first, progress.second, 48.dp)
        else Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Logo(p, 40.dp) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (agent != null) {
                    Spacer(Modifier.width(10.dp))
                    ClaudeDot(agent, 7.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(claudeText(live), color = if (agent == "OPEN") c.muted else claudeColor(agent), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                loops.isNotEmpty() -> Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(loopDot(loops.first().kind), 6.dp)
                    val text = if (loops.size > 1) "${loops.size} דברים פתוחים · ${loops.first().title}" else loops.first().title
                    Text(text, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(text)))
                }
                memory?.next?.isNotBlank() == true -> Text("הבא: ${memory.next}", color = c.faint, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = dirOf(memory.next)), modifier = Modifier.padding(top = 2.dp))
                memory?.left?.isNotEmpty() == true -> Text(if (memory.left.size == 1) "נשאר דבר אחד" else "נשארו ${memory.left.size}", color = c.faint, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                else -> (memory?.summary?.takeIf { it.isNotBlank() } ?: p.kind).takeIf { it.isNotBlank() }?.let { t ->
                    Text(t, color = c.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = dirOf(t)), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/**
 * First run: nothing is bundled, so the app starts empty. The mark, the line, and the two ways in: pair with
 * YDeck on the computer, or Nova to see it first.
 */
@Composable
private fun Welcome(onPair: () -> Unit, onSample: () -> Unit) {
    val c = LocalDeck.current
    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Mark(64.dp)
        Text(
            "שום דבר\nלא נשאר פתוח.", color = c.fg, fontSize = 34.sp, lineHeight = 42.sp,
            fontFamily = Display, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            "הזיכרון של הפרויקטים שלך, בכיס: איפה כל פרויקט עומד, מה הסשן האחרון עשה, ומה הבא.",
            color = c.muted, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.weight(1.2f))
        Button(
            onClick = onPair,
            colors = ButtonDefaults.buttonColors(containerColor = c.fg, contentColor = c.bg),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("חיבור למחשב", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        Text(
            "במחשב: ⁨YDeck⁩ ← הגדרות ← טלפון, וסורקים את הקוד.",
            color = c.faint, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp).height(48.dp)) {
            Text("לנסות עם פרויקט לדוגמה", color = c.muted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Connecting(name: String) {
    val c = LocalDeck.current
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = c.accentText, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Text("מתחבר ל־⁨$name⁩…", color = c.fg, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp))
        Text("הפרויקטים מגיעים מוצפנים מהמחשב.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
