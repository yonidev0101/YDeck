package com.yonidev.ydeck.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Where a project stands, said the way Now says it first: Claude waiting for you, Claude at work, something left open,
 * or all saved (with the next step). The strip in File Explorer and the widgets lead with it.
 */

enum class StandMode { WAITING, WORKING, OPEN, SAVED, UNKNOWN }

class Stand(
    val mode: StandMode,
    /** The line itself: "Claude needs you", "2 files not committed", "All saved". */
    val title: String,
    /** What goes with it: Claude's question or prompt, the next open thing, the next step. */
    val detail: String,
    val session: ClaudeSession?,
    /** What's open, the agent's question aside. */
    val loops: List<Loop>,
    val next: String,
    val handoff: Handoff?,
) {
    /** Now's order: waiting, at work, open, saved. */
    val rank: Int get() = mode.ordinal

    /** Above a list of what's open, how much is open (the list says what); otherwise the line itself. */
    val headline: String get() = if (mode == StandMode.OPEN && loops.size > 1) tr("${loops.size} things open", "${loops.size} דברים פתוחים")
        else if (mode == StandMode.OPEN) tr("One thing open", "דבר אחד פתוח") else title
}

@Composable
fun standOf(p: Project, local: Local): Stand {
    val folder = local.folder
    val session = claudeIn(folder)
    val loops = loopsOf(local).filter { it.kind != LoopKind.AGENT }
    val pulse = folder?.let { Pulses.state.collectAsState().value[it] }
    val git = folder?.let { Git.state.collectAsState().value[it] }
    val next = local.note.ifBlank { pulse?.suggestion.orEmpty() }
    val handoff = pulse?.handoff?.takeIf { it.done.isNotBlank() }
    val then = next.takeIf { it.isNotBlank() }?.let { tr("Next: ", "הבא: ") + it }.orEmpty()
    fun stand(mode: StandMode, title: String, detail: String) = Stand(mode, title, detail, session, loops, next, handoff)
    return when {
        session?.mode == ClaudeMode.WAITING -> stand(StandMode.WAITING, claudeText(session), session.detail)
        session?.mode == ClaudeMode.WORKING -> stand(StandMode.WORKING, claudeText(session), session.detail.takeIf { it.isNotBlank() }?.let { tr("“$it”", "״$it״") }.orEmpty())
        loops.isNotEmpty() -> stand(StandMode.OPEN, loops.first().title,
            if (loops.size > 1) tr("And ${loops.size - 1} more open", if (loops.size == 2) "ועוד דבר אחד פתוח" else "ועוד ${loops.size - 1} דברים פתוחים") else then)
        git != null -> stand(StandMode.SAVED, tr("All saved", "הכול שמור"), then.ifBlank { git.describe() + if (git.lastAt > 0) " · " + ago(git.lastAt) else "" })
        else -> stand(StandMode.UNKNOWN, Chrome.describe(p), then)
    }
}

/** The stand's colour: Claude's own when it waits or works, amber for open, red for conflicts, green for saved. */
fun standColor(s: Stand, dark: Boolean): Color = when (s.mode) {
    StandMode.WAITING -> claudeColor(ClaudeMode.WAITING, dark)
    StandMode.WORKING -> claudeColor(ClaudeMode.WORKING, dark)
    StandMode.OPEN -> when (s.loops.first().kind) {
        LoopKind.CONFLICTS -> Color(0xFFEF4444)
        LoopKind.SERVER -> LIVE_DOT
        else -> CHANGED_DOT
    }
    StandMode.SAVED -> CLEAN_DOT
    StandMode.UNKNOWN -> if (dark) Color(0xFF6E6E78) else Color(0xFFB9B9C2)
}

@Composable
fun StandDot(s: Stand, dark: Boolean, size: Dp = 7.dp) {
    val session = s.session
    if (session != null && (s.mode == StandMode.WAITING || s.mode == StandMode.WORKING)) ClaudeDot(session.mode, dark, size)
    else Dot(standColor(s, dark), size)
}
