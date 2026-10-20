package com.yonidev.ydeck.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/*
 * The project's memory, one for every agent: the decisions in .ydeck.json and the last session's handoff (what was
 * done, what's next). Claude Code gets them at every session's start (see ProjectFiles.briefing); other agents read
 * them in AGENTS.md, where YDeck keeps a block of its own when the project has that file.
 */

object Handoffs {
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    /** Folders whose handoff Claude is writing now. */
    val busy: StateFlow<Set<String>> = _busy

    /**
     * Claude (its small, quick model) reads what happened in the folder today, the sessions' prompts, the files they
     * changed, today's commits, and writes where the work stands. Returns null when it worked, or what went wrong.
     */
    fun write(dir: File, agent: String = "Claude"): String? {
        val claude = Tools.claude?.exe ?: return tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב")
        if (dir.path in _busy.value) return null
        _busy.update { it + dir.path }
        try {
            val prompt = context(dir) ?: return tr("Nothing happened here today", "היום לא קרה כאן כלום")
            val p = ProcessBuilder(claude.path, "-p", "--model", "haiku", "--output-format", "text").directory(dir).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            p.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(prompt) }
            val out = StringBuilder()
            val reader = Thread { runCatching { out.append(p.inputStream.bufferedReader(Charsets.UTF_8).readText()) } }.apply { isDaemon = true; start() }
            if (!p.waitFor(2, TimeUnit.MINUTES)) { p.destroyForcibly(); return tr("Claude took too long", "Claude לקח יותר מדי זמן") }
            reader.join(2000)
            val h = parse(out.toString(), agent, System.currentTimeMillis()) ?: return tr("Claude didn't answer", "Claude לא ענה")
            ProjectFiles.writeHandoff(dir, h)
            return null
        } catch (e: Exception) {
            return e.message
        } finally {
            _busy.update { it - dir.path }
        }
    }

    /** {"done": …, "next": …} from Claude's answer (a JSON object somewhere in it). */
    fun parse(answer: String, agent: String, at: Long): Handoff? {
        val json = answer.substringAfter('{', "").substringBeforeLast('}', "").takeIf { it.isNotBlank() } ?: return null
        val o = runCatching { JSONObject("{$json}") }.getOrNull() ?: return null
        val done = o.optString("done").trim()
        val next = o.optString("next").trim()
        return if (done.isEmpty() && next.isEmpty()) null else Handoff(done.take(400), next.take(200), at, agent)
    }

    /** What Claude reads: today's sessions in the folder, its changes and commits, where it stood. */
    fun context(dir: File, now: Long = System.currentTimeMillis()): String? {
        val receipts = Receipts.state.value[dir.path].orEmpty().filter { now - it.endAt < 18 * 3_600_000L && it.files.isNotEmpty() }
        val commits = Git.run(dir, listOf("log", "--since=18.hours.ago", "--format=%s"), errors = false).out.lines().filter { it.isNotBlank() }.take(20)
        val changes = Git.state.value[dir.path]?.files.orEmpty()
        if (receipts.isEmpty() && commits.isEmpty() && changes.isEmpty()) return null
        val local = ProjectFiles.readLocal(dir)
        return buildString {
            appendLine("You write the handoff note for this project: where the work stands, so the next session (by any agent, or the developer tomorrow) starts without asking.")
            appendLine("Answer with one JSON object only: {\"done\": \"what was done, one or two short sentences\", \"next\": \"the next concrete step, one short line\"}.")
            appendLine("Write in the language of the prompts below (Hebrew if they are in Hebrew). No markdown, no preamble.")
            appendLine()
            if (receipts.isNotEmpty()) {
                appendLine("Today's agent sessions:")
                receipts.take(8).forEach { r ->
                    appendLine("- ${r.agent.label}, asked: " + r.prompts.take(4).joinToString(" / ") { ClaudeLink.firstLine(it) })
                    appendLine("  changed: " + r.files.take(25).joinToString(", ") { "${it.kind} ${it.path}" })
                }
            }
            if (commits.isNotEmpty()) appendLine("Today's commits:\n" + commits.joinToString("\n") { "- $it" })
            if (changes.isNotEmpty()) appendLine("Not committed yet: " + changes.take(30).joinToString(", ") { it.path })
            local?.let { appendLine("It said before: now \"${it.now}\", next \"${it.next}\".") }
        }
    }

    /** When a session that changed files is over, its handoff is written (if the setting is on). */
    fun afterSession(id: String) {
        if (!Settings.state.value.handoffs) return
        val r = Receipts.find(id) ?: return
        if (r.files.isEmpty()) return
        write(File(r.folder), r.agent.label)
        ProjectFiles.sync()
    }
}

/**
 * AGENTS.md, which Codex, Cursor and other agents read: when a project has one, YDeck keeps a block of its own in it
 * (between two markers) with the project's memory. Nothing else in the file is touched.
 *
 * Only what is committed with the project goes in (the summary and the decisions from .ydeck.json), so the block
 * changes only when they do, and goes into the same commit. Where the work stands changes with every session: it
 * stays in the personal .ydeck.local.json, which the block points to. (It used to be written here, so the file
 * changed again after every session, the one that committed it included, and a project could never be closed.)
 */
object AgentsFile {
    const val NAME = "AGENTS.md"
    private const val START = "<!-- ydeck:start (kept by YDeck: edit the decisions in YDeck or in .ydeck.json) -->"
    private const val END = "<!-- ydeck:end -->"

    fun block(dir: File): String? {
        val shared = ProjectFiles.readShared(dir)
        val decisions = shared?.decisions.orEmpty()
        if (shared?.summary.isNullOrBlank() && decisions.isEmpty()) return null
        return buildString {
            appendLine(START)
            appendLine("## Project memory")
            shared?.summary?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine(it) }
            if (decisions.isNotEmpty()) {
                appendLine(); appendLine("### Decisions")
                decisions.take(20).forEach { appendLine("- ${it.text}" + (if (it.date.isNotBlank()) " (${it.date})" else "")) }
            }
            appendLine()
            appendLine("Where the work stands and the next step: ${ProjectFiles.LOCAL} in the project root (personal, kept out of git), when it's there.")
            append(END)
        }
    }

    /** Puts the block in [text] (replacing the old one, or at the end). */
    fun merge(text: String, block: String): String {
        val a = text.indexOf("<!-- ydeck:start")
        val b = text.indexOf(END)
        return if (a >= 0 && b > a) text.substring(0, a) + block + text.substring(b + END.length)
        else text.trimEnd() + (if (text.isBlank()) "" else "\n\n") + block + "\n"
    }

    /** Keeps the block current in a project that has AGENTS.md. */
    fun sync(dir: File) {
        if (!Settings.state.value.repoFiles) return
        val f = File(dir, NAME).takeIf { it.isFile } ?: return
        val block = block(dir) ?: return
        runCatching {
            val text = f.readText()
            val next = merge(text, block)
            if (next != text) f.writeText(next)
        }
    }

    /** Starts AGENTS.md with the block. */
    fun create(dir: File) {
        val f = File(dir, NAME)
        if (f.exists()) return sync(dir)
        f.writeText("# Agents\n\n" + (block(dir) ?: "$START\n## Project memory\n$END") + "\n")
    }
}

// ── on the overview ──

/** Decisions the project keeps to: every session starts from them (the body of the overview's "Decisions"). */
@Composable
fun DecisionsBody(dir: File) {
    val c = LocalDeck.current
    val pulse = Pulses.state.collectAsState().value[dir.path] ?: Pulse()
    val scope = rememberCoroutineScope()
    var draft by remember(dir) { mutableStateOf("") }
    fun refresh() = scope.launch { Pulses.refresh(Settings.state.value.local.values.mapNotNull { it.folder }) }
    Column {
        Text(tr("What every session keeps to. Claude adds to it too.", "מה שכל סשן שומר עליו. גם Claude מוסיף לכאן."), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pulse.decisions.take(12).forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("•", color = c.faint, fontSize = 14.sp, modifier = Modifier.width(14.dp))
                    Text(d.text, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f), style = TextStyle(textDirection = dirOf(d.text)))
                    if (d.date.isNotBlank()) Mono(d.date, c.faint, 11, Modifier.padding(horizontal = 8.dp))
                    IconButton(Glyph.TRASH, tr("Remove", "הסרה"), 28.dp, c.faint, filled = false) {
                        ProjectFiles.writeDecisions(dir, pulse.decisions.filterIndexed { k, _ -> k != i }); AgentsFile.sync(dir); refresh()
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(null, draft, { draft = it }, tr("A decision to remember", "החלטה לזכור"), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Pill(tr("Add", "הוספה"), Tone.SOFT, enabled = draft.isNotBlank(), small = true) {
                ProjectFiles.writeDecisions(dir, listOf(Decision(draft.trim(), java.time.LocalDate.now().toString())) + pulse.decisions)
                draft = ""
                AgentsFile.sync(dir); refresh()
            }
        }
        // Other agents read AGENTS.md; once it's there, it stays in step by itself.
        if (!File(dir, AgentsFile.NAME).isFile) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Using Codex or Cursor too?", "עובדים גם עם Codex או Cursor?"), color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
            TextAction(tr("Write AGENTS.md", "יצירת AGENTS.md"), accent = true) { AgentsFile.create(dir); refresh() }
        }
    }
}
