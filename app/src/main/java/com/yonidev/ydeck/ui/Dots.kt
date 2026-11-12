package com.yonidev.ydeck.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yonidev.ydeck.link.Live

/*
 * The dots, exactly as YDeck on the computer draws them (desktop Look.kt and Loops.kt; the dark theme's colours), in
 * two lanes that never share a dot: Claude's state (indigo and breathing at work, amber waiting for you, a grey ring
 * open but not busy), always with its words beside the name; and what's left open in the project (amber, a conflict
 * red, a running server indigo), on a line of its own. The next step is words, not a colour.
 */

/** Something open, or waiting for you. */
val CHANGED_DOT = Color(0xFFF5A524)
/** Closed: all saved, nothing left open. */
val CLEAN_DOT = Color(0xFF34C77B)
/** Something running (a dev server). */
val LIVE_DOT = Color(0xFF5B54F0)
val CONFLICT_DOT = Color(0xFFEF4444)
private val OPEN_RING = Color(0xFFB9B9C2)
private val UNKNOWN_DOT = Color(0xFF6E6E78)

/** Claude's colour for a mode the computer sends (WORKING, WAITING, OPEN, DONE). */
fun claudeColor(mode: String): Color = when (mode) {
    "WORKING" -> Color(0xFF9C97FF)
    "WAITING" -> CHANGED_DOT
    "OPEN" -> OPEN_RING
    else -> CLEAN_DOT
}

/** Claude's state in words, as on the computer. */
fun claudeText(live: Live): String {
    val name = live.agent.ifBlank { "Claude" }
    return when (live.agentMode) {
        "WORKING" -> "$name עובד"
        "WAITING" -> "$name מחכה לך"
        "OPEN" -> "$name פתוח" + if (live.agentRemote) " · Remote Control" else ""
        else -> "$name סיים" + if (live.agentSince > 0) " · ${ago(live.agentSince)}" else ""
    }
}

/** A dot in the state's colour; it breathes while Claude works. An open session is a ring: there, not busy. */
@Composable
fun ClaudeDot(mode: String, size: Dp = 7.dp) {
    if (mode == "OPEN") {
        Box(Modifier.size(size).clip(CircleShape).border(1.5.dp, claudeColor(mode), CircleShape))
        return
    }
    val alpha = if (mode == "WORKING") {
        rememberInfiniteTransition(label = "breathe").animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breathe").value
    } else 1f
    Dot(claudeColor(mode).copy(alpha = alpha), size)
}

@Composable
fun Dot(color: Color, size: Dp = 7.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

/** An open loop's colour: open is amber, at work is indigo (a server running), an error is red. */
fun loopDot(kind: String): Color = when (kind) {
    "CONFLICTS" -> CONFLICT_DOT
    "SERVER" -> LIVE_DOT
    else -> CHANGED_DOT
}
