package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState

private const val W = 460f
private const val H = 300f
private const val EDGE = 12f

/**
 * A command from the phone, waiting here: the exact line, the project and its folder, and three answers. Above every
 * window, by the clock, until it's answered (or it expires, see [Runs.ASK_MS]). The next one shows after it.
 */
@Composable
fun ApplicationScope.RunAskWindow(q: Runs.Request, more: Int, dark: Boolean) {
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
    val w = W + EDGE * 2
    val h = H + EDGE * 2
    val x = bounds.x / scale + bounds.width / scale - w - 8f
    val y = bounds.y / scale + bounds.height / scale - h - 8f
    val state = rememberWindowState(position = WindowPosition(x.dp, y.dp), size = DpSize(w.dp, h.dp))
    Window(onCloseRequest = { Runs.deny(q.r.id) }, state = state, undecorated = true, transparent = true, resizable = false, alwaysOnTop = true,
        title = tr("YDeck: a command from the phone", "YDeck: פקודה מהטלפון"), icon = androidx.compose.ui.res.painterResource("icons/app.png")) {
        LaunchedEffect(q.r.id) { window.toFront() }
        DeckTheme(dark) { RunAsk(q, more) }
    }
}

/** The card itself (drawn also for the renders). */
@Composable
fun RunAsk(q: Runs.Request, more: Int) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxSize().padding(EDGE.dp).shadow(10.dp, shape).clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(CHANGED_DOT))
            Spacer(Modifier.size(10.dp))
            Text(tr("The phone asks to run a command", "הטלפון מבקש להריץ פקודה"), color = c.amberText, fontSize = 14.sp, fontFamily = PlexSans,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (more > 0) Text(tr("$more more", "ועוד $more"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
        }
        Text(q.project, color = c.fg, fontSize = 18.sp, fontFamily = PlexSans, fontWeight = FontWeight.Medium)
        // The exact line, as it will run: nothing added, nothing hidden.
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(q.r.line, color = c.fg, fontSize = 14.sp, fontFamily = PlexMono, maxLines = 4)
        }
        Text(if (q.r.window) tr("In ${q.dir.path}, in a new terminal window. It stays open.", "בתיקייה ${q.dir.path}, בחלון טרמינל חדש שנשאר פתוח.")
            else tr("In ${q.dir.path}, inside YDeck, without a window. Its output shows here and on the phone.", "בתיקייה ${q.dir.path}, בתוך YDeck בלי חלון. הפלט מוצג כאן ובטלפון."),
            color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 2)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(tr("Run once", "הרצה פעם אחת"), Tone.PRIMARY) { Runs.approve(q.r.id, always = false) }
            Pill(tr("Always allow", "לאשר תמיד")) { Runs.approve(q.r.id, always = true) }
            Spacer(Modifier.weight(1f))
            TextAction(tr("Don't run", "לא להריץ")) { Runs.deny(q.r.id) }
        }
        Text(tr("Always: this exact line, in this project only. Undo in Settings → Phone.", "תמיד: רק השורה הזו בדיוק, רק בפרויקט הזה. מבטלים בהגדרות ← טלפון."),
            color = c.faint, fontSize = 11.sp, fontFamily = PlexSans)
    }
}

/** A command that failed: the same red as on the phone. */
private val FAILED_DOT = androidx.compose.ui.graphics.Color(0xFFEF4444)
private const val OUT_W = 540f
private const val OUT_H = 400f

/**
 * A command from the phone that runs inside YDeck (no terminal window): the line, its output as it comes, and how it
 * ended. In the corner, like the card that asked for it; it stays until it's closed, and can stop the command.
 */
@Composable
fun ApplicationScope.RunOutWindow(s: Runs.Shown, dark: Boolean) {
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
    val w = OUT_W + EDGE * 2
    val h = OUT_H + EDGE * 2
    val x = bounds.x / scale + bounds.width / scale - w - 8f
    val y = bounds.y / scale + bounds.height / scale - h - 8f
    val state = rememberWindowState(position = WindowPosition(x.dp, y.dp), size = DpSize(w.dp, h.dp))
    Window(onCloseRequest = { Runs.hide() }, state = state, undecorated = true, transparent = true, resizable = false,
        title = tr("YDeck: a command from the phone", "YDeck: פקודה מהטלפון"), icon = androidx.compose.ui.res.painterResource("icons/app.png")) {
        DeckTheme(dark) { RunOut(s) }
    }
}

/** The panel itself (drawn also for the renders). */
@Composable
fun RunOut(s: Runs.Shown) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(16.dp)
    val scroll = androidx.compose.foundation.rememberScrollState()
    // New output: to the bottom, as a terminal does.
    LaunchedEffect(s.text.length) { scroll.scrollTo(scroll.maxValue) }
    val (words, dot) = when {
        !s.done -> tr("Running, inside YDeck", "רץ, בתוך YDeck") to LIVE_DOT
        s.code == 0 -> tr("Done", "הסתיים בהצלחה") to CLEAN_DOT
        else -> tr("Failed · code ${s.code}", "נכשל · קוד ${s.code}") to FAILED_DOT
    }
    Column(
        Modifier.fillMaxSize().padding(EDGE.dp).shadow(10.dp, shape).clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
            Spacer(Modifier.size(10.dp))
            Text(words, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
            Text(s.project, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1)
        }
        Text(s.line, color = c.fg, fontSize = 14.sp, fontFamily = PlexMono, maxLines = 2)
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(horizontal = 12.dp, vertical = 10.dp)) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(s.text.ifEmpty { if (s.done) tr("(no output)", "(אין פלט)") else "…" }, color = if (s.text.isEmpty()) c.faint else c.fg,
                    fontSize = 12.sp, fontFamily = PlexMono, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().verticalScroll(scroll))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!s.done) Pill(tr("Stop", "עצירה")) { Runs.stop(s.id) }
            Spacer(Modifier.weight(1f))
            TextAction(tr("Close", "סגירה")) { Runs.hide() }
        }
    }
}
