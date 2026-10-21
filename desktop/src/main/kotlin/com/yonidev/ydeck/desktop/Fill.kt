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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/*
 * "Fill in with Claude": Claude Code (its quick model) reads the project's folder, read-only and never its .env files,
 * together with what YDeck already knows, and suggests what's missing: what the project is, a summary, a first next
 * step, its links (the live site, the repository, its consoles) and its commands. What it reads shows as it goes.
 * Nothing is added until the developer picks (and may edit) what to keep.
 */

/** What Claude suggests for a project; each part can be kept or left out. */
data class FillProposal(val kind: String, val summary: String, val links: List<FileLink>, val commands: List<Command>, val next: String = "") {
    val isEmpty: Boolean get() = kind.isBlank() && summary.isBlank() && next.isBlank() && links.isEmpty() && commands.isEmpty()
}

object ClaudeFill {
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    /** Projects Claude is reading now. */
    val busy: StateFlow<Set<String>> = _busy

    private val _step = MutableStateFlow<Map<String, String>>(emptyMap())
    /** By project: what Claude is doing right now ("Reading package.json"), while it reads. */
    val step: StateFlow<Map<String, String>> = _step

    private val _results = MutableStateFlow<Map<String, Result<FillProposal>>>(emptyMap())
    /** By project: the last suggestion (or what went wrong), until it's applied or dismissed. */
    val results: StateFlow<Map<String, Result<FillProposal>>> = _results

    /** Starts reading in the background (from a menu: the details page shows the result when it comes). */
    fun start(p: Project, dir: File) {
        if (p.id in _busy.value) return
        UsageStats.mark(UsageStats.Feature.FILL)
        _busy.update { it + p.id }
        _results.update { it - p.id }
        Settings.state.value.localOf(p.id).takeIf { !it.fillOffered }?.let { saveLocal(p, it.copy(fillOffered = true)) }
        Thread({
            val r = runCatching { run(p, dir) }
            _results.update { it + (p.id to r) }
            _busy.update { it - p.id }
            _step.update { it - p.id }
        }, "ydeck-fill").apply { isDaemon = true; start() }
    }

    fun dismiss(p: Project) = _results.update { it - p.id }

    private fun run(p: Project, dir: File): FillProposal {
        val claude = Tools.claude?.exe ?: error(tr("Claude Code isn't installed on this PC", "Claude Code לא נמצא במחשב"))
        val local = Settings.state.value.localOf(p.id)
        // Read-only, and never the files that hold keys (they aren't Claude's to read, nor a link's to carry).
        val proc = ProcessBuilder(claude.path, "-p", "--model", "haiku", "--output-format", "stream-json", "--verbose",
            "--allowedTools", "Read", "Glob", "Grep", "LS", "--disallowedTools", *SECRETS.toTypedArray())
            .directory(dir).start()
        proc.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(prompt(p, local)) }
        val err = StringBuilder()
        Thread { runCatching { err.append(proc.errorStream.bufferedReader(Charsets.UTF_8).readText()) } }.apply { isDaemon = true; start() }
        var answer: String? = null
        var failed: String? = null
        val reader = Thread {
            runCatching {
                proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                    when (o.optString("type")) {
                        "assistant" -> stepOf(o, dir)?.let { s -> _step.update { it + (p.id to s) } }
                        "result" -> if (o.optBoolean("is_error") || !o.optString("subtype").startsWith("success")) failed = o.optString("result") else answer = o.optString("result")
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        if (!proc.waitFor(4, TimeUnit.MINUTES)) { proc.destroyForcibly(); error(tr("Claude took too long. Try again, or fill in by hand.", "Claude לקח יותר מדי זמן. אפשר לנסות שוב, או למלא ידנית.")) }
        reader.join(3000)
        answer?.let { a -> return parse(a, p, local) ?: error(tr("Claude's answer couldn't be read. Try again.", "לא הצלחתי לקרוא את התשובה של Claude. אפשר לנסות שוב.")) }
        error(why(failed.orEmpty() + "\n" + err))
    }

    /** Claude Code's permission rules for the files that hold keys. */
    private val SECRETS = listOf("Read(./.env)", "Read(./.env.*)", "Read(**/.env)", "Read(**/.env.*)", "Read(**/*.pem)", "Read(**/*.key)")

    /** What Claude is doing, in words, from a step of its stream: the file it reads, what it looks for. */
    fun stepOf(o: JSONObject, dir: File): String? {
        val content = o.optJSONObject("message")?.optJSONArray("content") ?: return null
        val use = (0 until content.length()).mapNotNull { content.optJSONObject(it) }.lastOrNull { it.optString("type") == "tool_use" } ?: return null
        val input = use.optJSONObject("input") ?: JSONObject()
        fun rel(path: String) = runCatching { File(path).relativeTo(dir).invariantSeparatorsPath }.getOrDefault(path).ifBlank { File(path).name }
        return when (use.optString("name")) {
            "Read" -> tr("Reading ", "קורא את ") + rel(input.optString("file_path"))
            "Grep" -> tr("Looking for “", "מחפש ״") + input.optString("pattern").take(40) + tr("”", "״")
            "Glob" -> tr("Looking for files: ", "מחפש קבצים: ") + input.optString("pattern").take(40)
            "LS" -> tr("Looking through ", "עובר על ") + rel(input.optString("path")).ifBlank { "." }
            else -> null
        }
    }

    /** What went wrong, in words the developer can act on. */
    fun why(text: String): String {
        val t = text.lowercase()
        return when {
            "login" in t || "api key" in t || "authenticat" in t || "unauthorized" in t || "401" in t ->
                tr("Claude Code isn't signed in. Open a terminal, run claude, and sign in; then try again.", "Claude Code לא מחובר לחשבון. פותחים טרמינל, מריצים claude ומתחברים, ואז מנסים שוב.")
            "credit" in t || "usage limit" in t || "rate limit" in t || "limit reached" in t || "429" in t ->
                tr("Claude's usage limit is reached for now. Try again later.", "הגעת למגבלת השימוש של Claude כרגע. אפשר לנסות שוב מאוחר יותר.")
            "enotfound" in t || "network" in t || "econn" in t || "certificate" in t || "fetch failed" in t ->
                tr("Claude Code couldn't reach the internet.", "Claude Code לא הצליח להתחבר לאינטרנט.")
            else -> text.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.take(200)?.let { tr("Claude didn't answer: ", "Claude לא ענה: ") + it }
                ?: tr("Claude didn't answer", "Claude לא ענה")
        }
    }

    /** What Claude is asked: the project as YDeck knows it, and the shape of the answer. */
    fun prompt(p: Project, local: Local): String = buildString {
        appendLine("This folder is the project \"${p.name}\" in YDeck, a developer's project dashboard. Read the folder (README, package files, config, deploy files, docs) and suggest what YDeck is missing about it. Do not change any file.")
        appendLine()
        appendLine("What YDeck already has:")
        appendLine("- kind: ${p.kind.ifBlank { "(none)" }}")
        appendLine("- links: " + (p.actions.joinToString("; ") { "${it.label} ${it.url}" }.ifBlank { "(none)" }))
        appendLine("- commands: " + (local.commands.joinToString("; ") { it.line }.ifBlank { "(none)" }))
        appendLine("- next step: " + local.dir?.let(ProjectFiles::readLocal)?.next.orEmpty().ifBlank { "(none)" })
        appendLine()
        appendLine("Answer with one JSON object only, no markdown and no preamble:")
        appendLine("{\"kind\": \"what it is, 2 to 5 words (e.g. Next.js web app, Android app)\", \"summary\": \"2 or 3 plain sentences: what it is, who it is for, the main stack\", \"links\": [{\"label\": \"short name\", \"url\": \"https://...\"}], \"commands\": [{\"label\": \"short name\", \"line\": \"the command\"}], \"next\": \"one concrete first step, one short line\"}")
        appendLine("Links: only real addresses found in the folder (the live site, the repository, the consoles of its services: Firebase, Vercel, Supabase, Stripe, Play Console, Cloudflare...), never guessed ones, none already listed. Commands: the ones a developer runs every day (dev server, build, test, deploy), from package.json scripts, Gradle, Makefiles and the like, none already listed. Next: only when YDeck has no next step: the most useful thing to do next, from the README, TODO or FIXME notes, or what looks unfinished; specific enough to start from cold. Never read .env files, and never put a key, token or password in a link. Leave a part empty when there is nothing to add.")
        append("Write \"kind\", \"summary\" and \"next\" in ${if (I18n.rtl) "Hebrew" else "English"}.")
    }

    /** Claude's answer, less what YDeck already has. */
    fun parse(answer: String, p: Project, local: Local): FillProposal? {
        val json = answer.substringAfter('{', "").substringBeforeLast('}', "").takeIf { it.isNotBlank() } ?: return null
        val o = runCatching { JSONObject("{$json}") }.getOrNull() ?: return null
        fun list(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
        val known = p.actions.map { it.url.trimEnd('/').lowercase() }.toSet()
        val links = list("links").mapNotNull { l ->
            val url = l.optString("url").trim().takeIf { (it.startsWith("https://") || it.startsWith("http://")) && !secretIn(it) } ?: return@mapNotNull null
            if (url.trimEnd('/').lowercase() in known) null else FileLink(l.optString("label").trim().ifBlank { Links.host(url) ?: url }, url)
        }.distinctBy { it.url.trimEnd('/').lowercase() }.take(12)
        val commands = list("commands").mapNotNull { c ->
            val line = c.optString("line").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (local.commands.any { it.line == line }) null else Command(c.optString("label").trim().ifBlank { line.substringBefore(' ') }, line)
        }.distinctBy { it.line }.take(8)
        val kind = o.optString("kind").trim().take(60).takeIf { it != p.kind }.orEmpty()
        val next = o.optString("next").trim().take(200).takeIf { local.dir?.let(ProjectFiles::readLocal)?.next.isNullOrBlank() }.orEmpty()
        return FillProposal(kind, o.optString("summary").trim().take(800), links, commands, next)
    }

    /** A link that carries a key: a password in it, or a token, key or signature in its query. */
    fun secretIn(url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return true
        if (u.rawUserInfo?.contains(':') == true) return true
        val q = u.rawQuery?.lowercase() ?: return false
        return Regex("(^|&)(key|api_?key|token|access_token|secret|password|pwd|sig|signature|auth)=").containsMatchIn(q)
    }

    /** Keeps the chosen parts: into the project, its PC settings and .ydeck.json's summary. */
    fun apply(p: Project, dir: File, f: FillProposal) {
        val current = Repo.deck.value.byId(p.id) ?: return
        val added = f.links.map { l -> val s = Catalog.detect(l.url) ?: Catalog.LINK; DeckAction(s, l.url, l.label.ifBlank { s.label }) }
        saveProject(current.copy(kind = f.kind.ifBlank { current.kind }, actions = current.actions + added))
        if (f.commands.isNotEmpty()) {
            val local = Settings.state.value.localOf(p.id)
            Settings.setLocal(p.id, local.copy(commands = local.commands + f.commands))
        }
        if (f.summary.isNotBlank()) ClaudeSummary.writeSummary(dir, f.summary)
        if (f.next.isNotBlank()) { ProjectFiles.writeNext(dir, f.next); ProjectFiles.sync() }
    }
}

/** On the details page: the button, Claude at work (what it reads), then what it found, to edit, keep or leave out. */
@Composable
fun FillCard(p: Project, local: Local, dark: Boolean) {
    val c = LocalDeck.current
    val dir = local.dir
    val busy = p.id in ClaudeFill.busy.collectAsState().value
    val result = ClaudeFill.results.collectAsState().value[p.id]
    // What's left out, by key ("kind", "summary", "l:<url>", "c:<line>"); everything is in by default.
    val off = remember(result) { mutableStateMapOf<String, Boolean>() }
    // The words as the developer edited them ("kind", "summary", "next").
    val edited = remember(result) { mutableStateMapOf<String, String>() }
    val step = ClaudeFill.step.collectAsState().value[p.id]
    var done by remember(p.id) { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.CLAUDE, 18.dp, Color(0xFFD97757))
            Spacer(Modifier.width(10.dp))
            Text(tr("Fill in with Claude", "מילוי עם Claude"), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f))
            if (dir != null && Tools.claude != null) Pill(
                when { busy -> tr("Claude is reading…", "Claude קורא…"); result != null -> tr("Read again", "קריאה מחדש"); else -> tr("Fill in", "מילוי") },
                Tone.TINT, Glyph.SPARKLE, enabled = !busy, small = true,
            ) { done = false; ClaudeFill.start(p, dir) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                dir == null -> tr("Connect the code folder first: Claude reads it to fill in the rest.", "קודם מחברים את תיקיית הקוד: Claude קורא אותה כדי למלא את השאר.")
                Tools.claude == null -> tr("Claude Code isn't installed on this PC.", "Claude Code לא נמצא במחשב.")
                busy -> step ?: tr("Starting. Claude reads the folder without changing anything.", "מתחיל. Claude קורא את התיקייה בלי לשנות בה כלום.")
                else -> tr("Claude reads the folder, without changing anything and without its .env files, and suggests what's missing: what it is, a summary, a next step, its links and its commands. You choose what to keep.",
                    "Claude קורא את התיקייה, בלי לשנות בה כלום ובלי קובצי ה־.env, ומציע מה חסר: מה הפרויקט, תקציר, צעד הבא, קישורים ופקודות. אתה בוחר מה להשאיר.")
            },
            color = if (busy && step != null) c.accentText else c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans,
            maxLines = if (busy) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content),
        )
        if (done) Text(tr("Added. You'll find it all on the project's page.", "נוסף. הכול מחכה בעמוד הפרויקט."), color = c.liveText, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 10.dp))
        val f = result?.getOrNull()
        result?.exceptionOrNull()?.let { Text(it.message ?: "", color = c.danger, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 10.dp)) }
        if (f == null || busy) return@Card
        if (f.isEmpty) {
            Text(tr("Nothing missing: YDeck already has everything Claude found.", "לא חסר כלום: כל מה ש־Claude מצא כבר נמצא ב־YDeck."), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 10.dp))
            return@Card
        }
        Spacer(Modifier.height(16.dp))
        @Composable fun choice(key: String, content: @Composable () -> Unit) {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.padding(top = 2.dp)) { CheckBox(off[key] != true) { off[key] = !it } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { content() }
            }
        }
        @Composable fun label(text: String) = Text(text, color = c.faint, fontSize = 12.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // The words can be changed before they're added.
            @Composable fun words(key: String, title: String, value: String, lines: Int = 1) {
                if (value.isBlank()) return
                label(title)
                choice(key) { Field(null, edited[key] ?: value, { edited[key] = it }, lines = lines) }
            }
            words("kind", tr("Kind", "סוג"), f.kind)
            words("summary", tr("Summary", "תקציר"), f.summary, lines = 3)
            words("next", tr("Next step", "הצעד הבא"), f.next)
            if (f.links.isNotEmpty()) {
                label(tr("Links", "קישורים"))
                f.links.forEach { l ->
                    choice("l:" + l.url) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val s = Catalog.detect(l.url) ?: Catalog.LINK
                            ServiceMark(s, Links.host(l.url), 18.dp, dark)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.label, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Mono(l.url, c.faint, 11)
                            }
                        }
                    }
                }
            }
            if (f.commands.isNotEmpty()) {
                label(tr("Commands", "פקודות"))
                f.commands.forEach { cmd ->
                    choice("c:" + cmd.line) {
                        Text(cmd.label, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans)
                        Mono(cmd.line)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val chosen = FillProposal(
            kind = (edited["kind"] ?: f.kind).trim().takeIf { off["kind"] != true && f.kind.isNotBlank() }.orEmpty(),
            summary = (edited["summary"] ?: f.summary).trim().takeIf { off["summary"] != true && f.summary.isNotBlank() }.orEmpty(),
            next = (edited["next"] ?: f.next).trim().takeIf { off["next"] != true && f.next.isNotBlank() }.orEmpty(),
            links = f.links.filter { off["l:" + it.url] != true },
            commands = f.commands.filter { off["c:" + it.line] != true },
        )
        val n = listOf(chosen.kind.isNotBlank(), chosen.summary.isNotBlank(), chosen.next.isNotBlank()).count { it } + chosen.links.size + chosen.commands.size
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(tr("Add", "הוספה") + "  $n", Tone.PRIMARY, Glyph.CHECK, enabled = n > 0 && dir != null) {
                if (dir != null) { ClaudeFill.apply(p, dir, chosen); ClaudeFill.dismiss(p); done = true }
            }
            TextAction(tr("Not now", "לא עכשיו")) { ClaudeFill.dismiss(p) }
        }
    }
}
