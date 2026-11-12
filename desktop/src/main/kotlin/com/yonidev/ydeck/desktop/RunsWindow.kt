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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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


/*
 * A command from the phone, on this screen: one card in the corner that goes from asking, to running, to ended. The
 * line always sits in the same place, a terminal-like block; its output opens under it. The card is as tall as what
 * it holds, its bottom edge kept where it is.
 */

private const val W = 480f
private const val EDGE = 12f
/** A command that failed: the same red as on the phone. */
private val FAILED_DOT = androidx.compose.ui.graphics.Color(0xFFEF4444)
private val FAILED_TEXT = androidx.compose.ui.graphics.Color(0xFFF87171)

/** The corner window, sized to its card. */
@Composable
private fun ApplicationScope.CornerWindow(title: String, onClose: () -> Unit, top: Boolean, dark: Boolean, content: @Composable androidx.compose.ui.window.FrameWindowScope.() -> Unit) {
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
    val w = W + EDGE * 2
    val right = bounds.x / scale + bounds.width / scale - w - 8f
    val bottom = bounds.y / scale + bounds.height / scale - 8f
    val start = 320f
    val state = rememberWindowState(position = WindowPosition(right.dp, (bottom - start).dp), size = DpSize(w.dp, start.dp))
    Window(onCloseRequest = onClose, state = state, undecorated = true, transparent = true, resizable = false, alwaysOnTop = top,
        title = title, icon = androidx.compose.ui.res.painterResource("icons/app.png")) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        DeckTheme(dark) {
            Box(Modifier.width(w.dp).wrapContentHeight(unbounded = true).onSizeChanged { px ->
                val h = with(density) { px.height.toDp() }
                if (h > 0.dp && kotlin.math.abs((state.size.height - h).value) > 0.5f) {
                    state.size = DpSize(w.dp, h)
                    state.position = WindowPosition(right.dp, bottom.dp - h)
                }
            }) { content() }
        }
    }
}

/** A command from the phone, waiting here: above every window until it's answered (or it expires, see [Runs.ASK_MS]). */
@Composable
fun ApplicationScope.RunAskWindow(q: Runs.Request, more: Int, dark: Boolean) =
    CornerWindow(tr("YDeck: a command from the phone", "YDeck: פקודה מהטלפון"), { Runs.deny(q.r.id) }, top = true, dark = dark) {
        LaunchedEffect(q.r.id) { window.toFront() }
        RunAsk(q, more)
    }

/** One that runs inside YDeck (no terminal window): the same card, running, then ended; it stays until it's closed. */
@Composable
fun ApplicationScope.RunOutWindow(s: Runs.Shown, dark: Boolean) =
    CornerWindow(tr("YDeck: a command from the phone", "YDeck: פקודה מהטלפון"), { Runs.hide() }, top = false, dark = dark) { RunOut(s) }

/** The card's frame and its first line: the dot, what's going on, and on the far side a quiet word. */
@Composable
private fun RunFrame(dot: androidx.compose.ui.graphics.Color, words: String, wordsColor: androidx.compose.ui.graphics.Color, side: String, body: @Composable () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(EDGE.dp).shadow(10.dp, shape).clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
            Spacer(Modifier.size(10.dp))
            Text(words, color = wordsColor, fontSize = 14.sp, fontFamily = PlexSans, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
            if (side.isNotEmpty()) Text(side, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1)
        }
        body()
    }
}

/** The block the line lives in, as a terminal shows it: "> line", and under it (when there is) its output. */
@Composable
private fun LineBlock(line: String, output: (@Composable () -> Unit)? = null) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.bg).border(1.dp, c.line, shape)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text("> ", color = c.faint, fontSize = 14.sp, fontFamily = PlexMono)
                Text(line, color = c.fg, fontSize = 14.sp, fontFamily = PlexMono, maxLines = 3)
            }
            if (output != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                output()
            }
        }
    }
}

@Composable
private fun Chip(text: String, strong: Boolean = false) {
    val c = LocalDeck.current
    Text(text, color = if (strong) c.fg else c.muted, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(c.surface2).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Asking: the line, where it runs (project, inside YDeck or a window, the shell), and three answers. */
@Composable
fun RunAsk(q: Runs.Request, more: Int) {
    val c = LocalDeck.current
    RunFrame(CHANGED_DOT, tr("The phone asks to run a command", "הטלפון מבקש להריץ פקודה"), c.amberText, if (more > 0) tr("$more more", "ועוד $more") else "") {
        // The exact line, as it will run: nothing added, nothing hidden.
        LineBlock(q.r.line)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(q.project, strong = true)
            Chip(if (q.r.window) tr("In a new window", "בחלון חדש") else tr("Inside YDeck, no window", "בתוך YDeck, בלי חלון"))
            Chip(Shells.current.label)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(tr("Run once", "הרצה פעם אחת"), Tone.PRIMARY) { Runs.approve(q.r.id, always = false) }
            Pill(tr("Always allow", "לאשר תמיד")) { Runs.approve(q.r.id, always = true) }
            Spacer(Modifier.weight(1f))
            TextAction(tr("Don't run", "לא להריץ")) { Runs.deny(q.r.id) }
        }
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Text(tr("Always: this exact line, in this project only. Undo in Settings → Phone.", "תמיד: רק השורה הזו בדיוק, רק בפרויקט הזה. מבטלים בהגדרות ← טלפון."),
                color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Running, then ended: the same card, the output under the line, to the bottom as it comes. */
@Composable
fun RunOut(s: Runs.Shown) {
    val c = LocalDeck.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.done) { while (!s.done) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000) } }
    val secs = (((if (s.done && s.ended > 0) s.ended else now) - s.since) / 1000).coerceAtLeast(0)
    val took = if (secs < 60) tr("$secs s", "$secs שניות") else "%d:%02d".format(secs / 60, secs % 60)
    val (dot, words, color) = when {
        !s.done -> Triple(LIVE_DOT, tr("Running · ", "רץ · ") + "%d:%02d".format(secs / 60, secs % 60), c.accentText)
        s.code == 0 -> Triple(CLEAN_DOT, tr("Done · $took", "הסתיים בהצלחה · $took"), c.liveText)
        else -> Triple(FAILED_DOT, tr("Failed · code ${s.code} · $took", "נכשל · קוד ${s.code} · $took"), FAILED_TEXT)
    }
    val scroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(s.text.length) { scroll.scrollTo(scroll.maxValue) }
    var copied by remember { mutableStateOf(false) }
    RunFrame(dot, words, color, s.project) {
        LineBlock(s.line) {
            Box(Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(s.text.trimEnd().ifEmpty { if (s.done) tr("(no output)", "(אין פלט)") else "…" } + if (s.done) "\n— exit ${s.code}" else "",
                        color = if (s.text.isBlank()) c.faint else c.fg, fontSize = 12.sp, fontFamily = PlexMono, lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth().verticalScroll(scroll))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!s.done) Pill(tr("Stop", "עצירה")) { Runs.stop(s.id) }
            else Pill(if (copied) tr("Copied", "הועתק") else tr("Copy", "העתקה")) {
                runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(s.text), null) }
                copied = true
            }
            Spacer(Modifier.weight(1f))
            if (!s.done) Text(tr("On the phone too, live", "גם בטלפון, בזמן אמת"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
            else TextAction(tr("Close", "סגירה")) { Runs.hide() }
        }
    }
}
