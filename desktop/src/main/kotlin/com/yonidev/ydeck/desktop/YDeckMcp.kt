package com.yonidev.ydeck.desktop

import com.sun.net.httpserver.HttpExchange
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * YDeck as an MCP server, for every Claude Code session on this computer: what YDeck knows about all the projects,
 * to read. A session in one project can ask where the others stand, what was decided anywhere, which sessions run
 * now, and where something was already done in another project's code. Read only: no tool here changes anything,
 * and the search never opens the files that hold keys.
 *
 * It answers at /mcp on the hooks' server (127.0.0.1 only, the same key; see [ClaudeLink]), and the "ydeck" plugin
 * tells Claude Code where it is ([ClaudePlugin]). MCP over HTTP, each answer plain JSON (no stream).
 */
object YDeckMcp {
    private const val VERSION = "2025-06-18"
    private val VERSIONS = setOf("2024-11-05", "2025-03-26", "2025-06-18")

    /** One HTTP call on /mcp: a JSON-RPC message in, its answer out (202 for a notification, which has none). */
    fun serve(ex: HttpExchange, key: String) {
        fun send(code: Int, body: JSONObject?) {
            val bytes = body?.toString()?.toByteArray(Charsets.UTF_8)
            if (bytes != null) ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(code, bytes?.size?.toLong() ?: -1)
            ex.responseBody.use { if (bytes != null) it.write(bytes) }
        }
        if (ex.requestHeaders.getFirst("X-YDeck") != key) return send(403, null)
        // No stream to open: everything is answered in the reply to its own call.
        if (ex.requestMethod != "POST") return send(405, null)
        val msg = runCatching { JSONObject(ex.requestBody.readNBytes(1 shl 20).toString(Charsets.UTF_8)) }.getOrNull()
            ?: return send(400, error(null, -32700, "Parse error"))
        if (!msg.has("id")) return send(202, null)
        send(200, answer(msg))
    }

    /** The answer to one request (initialize, tools/list, tools/call, ping). */
    fun answer(msg: JSONObject): JSONObject {
        val id = msg.opt("id")
        val params = msg.optJSONObject("params") ?: JSONObject()
        return when (msg.optString("method")) {
            "initialize" -> result(id, JSONObject()
                .put("protocolVersion", params.optString("protocolVersion").takeIf { it in VERSIONS } ?: VERSION)
                .put("capabilities", JSONObject().put("tools", JSONObject()))
                .put("serverInfo", JSONObject().put("name", "ydeck").put("version", "1.0.0"))
                .put("instructions", "YDeck is the developer's dashboard of all their projects. Use these tools to learn where the other projects stand, " +
                    "what was decided in any of them, which Claude Code sessions run now, and where something was already done in another project's code. Read only."))
            "ping" -> result(id, JSONObject())
            "tools/list" -> result(id, JSONObject().put("tools", JSONArray().apply { TOOLS.forEach { put(it.json()) } }))
            "tools/call" -> {
                val name = params.optString("name")
                val args = params.optJSONObject("arguments") ?: JSONObject()
                val tool = TOOLS.firstOrNull { it.name == name } ?: return error(id, -32602, "Unknown tool: $name")
                val r = runCatching { tool.run(args) }
                result(id, JSONObject()
                    .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", r.getOrElse { "YDeck couldn't answer: ${it.message}" })))
                    .put("isError", r.isFailure))
            }
            else -> error(id, -32601, "Method not found")
        }
    }

    private fun result(id: Any?, r: JSONObject) = JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("result", r)
    private fun error(id: Any?, code: Int, message: String) = JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL)
        .put("error", JSONObject().put("code", code).put("message", message))

    private class Tool(val name: String, val description: String, val props: JSONObject = JSONObject(), val required: List<String> = emptyList(), val run: (JSONObject) -> String) {
        fun json(): JSONObject = JSONObject().put("name", name).put("description", description)
            .put("inputSchema", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required)))
            .put("annotations", JSONObject().put("readOnlyHint", true).put("openWorldHint", false))
    }

    private fun str(description: String) = JSONObject().put("type", "string").put("description", description)

    private val TOOLS = listOf(
        Tool("projects", "Every project YDeck knows, one line each: where it stands now (Claude Code's state in it, what's left open, the next step), its kind and folder.") { _ -> projects() },
        Tool("project", "One project in full: its summary, where it stands, the last session's handoff, its decisions, releases, links and commands, and its git state.",
            JSONObject().put("name", str("The project's name or folder name (a part of it is enough).")), listOf("name")) { a -> project(a.getString("name")) },
        Tool("decisions", "The decisions recorded in the projects (.ydeck.json), newest first, with their project and date. Optionally only those that mention some words, or only one project's.",
            JSONObject().put("query", str("Words the decision mentions (optional).")).put("project", str("Only this project (optional)."))) { a -> decisions(a.optString("query"), a.optString("project")) },
        Tool("sessions", "The Claude Code sessions on this computer now, in every project: working, waiting for the user, or open; what each is on, and since when.") { _ -> sessions() },
        Tool("search", "Searches the code and text of the projects' folders for some words (not case sensitive), to find where something was already done. " +
            "Returns up to 40 places as path:line and the line. Skips dependencies, build output, git, and the files that hold keys.",
            JSONObject().put("text", str("The words to find, as they appear in the files.")).put("project", str("Only this project (optional).")), listOf("text")) { a -> search(a.getString("text"), a.optString("project")) },
    )

    // ── the tools ──

    private fun deck() = Repo.deck.value
    private fun local(p: Project) = Settings.state.value.localOf(p.id)

    /** The project a name means: exactly, else the one whose name or folder contains it. */
    private fun find(name: String): Project? {
        val n = name.trim().lowercase().ifEmpty { return null }
        val all = deck().projects
        return all.firstOrNull { it.name.lowercase() == n }
            ?: all.firstOrNull { it.name.lowercase().contains(n) || local(it).dir?.name?.lowercase()?.contains(n) == true }
    }

    private fun projects(): String = buildString {
        val all = deck().projects
        appendLine("${all.size} projects (today is ${java.time.LocalDate.now()}):")
        all.forEach { p ->
            val l = local(p)
            val dir = l.dir
            val mine = dir?.let(ProjectFiles::readLocal)
            val session = dir?.let { ClaudeLink.statusIn(it.path, ClaudeLink.sessions.value) }
            val loops = loopsIn(p).filter { it.kind != LoopKind.AGENT }
            val next = mine?.next?.ifBlank { null } ?: l.note
            append("- ${p.name}")
            if (p.kind.isNotBlank()) append(" (${p.kind})")
            append(": ")
            append(listOfNotNull(
                session?.let { claudeText(it) },
                loops.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.title }?.let { "open: $it" },
                next.takeIf { it.isNotBlank() }?.let { "next: $it" },
                if (dir == null) "no folder on this PC" else "folder ${dir.path}",
            ).joinToString(" · "))
            appendLine()
        }
    }

    private fun project(name: String): String {
        val p = find(name) ?: return "No project called \"$name\". The projects: " + deck().projects.joinToString(", ") { it.name }
        return buildString {
            append(Assistant.about(p))
            val folder = local(p).folder ?: return@buildString
            Git.state.value[folder]?.let { g -> appendLine("- git: ${g.branch}, ${gitWords(g)}" + if (g.lastAt > 0) "; last commit ${java.time.Instant.ofEpochMilli(g.lastAt)}: ${g.lastSubject}" else "") }
            loopsIn(p).filter { it.kind != LoopKind.AGENT }.takeIf { it.isNotEmpty() }?.let { loops ->
                appendLine("- open now:")
                loops.forEach { appendLine("  - ${it.title}" + if (it.detail.isNotBlank()) " (${it.detail})" else "") }
            }
        }
    }

    private fun decisions(query: String, project: String): String {
        val only = project.takeIf { it.isNotBlank() }?.let { find(it) ?: return "No project called \"$it\"." }
        val words = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        val found = deck().projects.filter { only == null || it.id == only.id }.flatMap { p ->
            local(p).dir?.let(ProjectFiles::readShared)?.decisions.orEmpty().map { p to it }
        }.filter { (_, d) -> words.isEmpty() || words.any { d.text.lowercase().contains(it) } }
            .sortedByDescending { it.second.date }
        if (found.isEmpty()) return if (words.isEmpty()) "No decisions are recorded." else "No decision mentions \"$query\"."
        return found.take(60).joinToString("\n") { (p, d) -> "- ${p.name}" + (if (d.date.isNotBlank()) " (${d.date})" else "") + ": ${d.text}" }
    }

    private fun sessions(): String {
        val all = ClaudeLink.sessions.value.values.filter { it.mode != ClaudeMode.DONE }
        if (all.isEmpty()) return "No Claude Code session runs on this computer now."
        return all.sortedBy { it.mode.ordinal }.joinToString("\n") { s ->
            val p = SessionAsks.projectAt(s.cwd)
            "- ${p?.name ?: s.cwd}: ${claudeText(s)}" + (if (s.detail.isNotBlank()) " — \"${s.detail.take(200)}\"" else "") +
                " (since ${java.time.Instant.ofEpochMilli(s.workStart)})"
        }
    }

    private fun loopsIn(p: Project): List<Loop> {
        val folder = local(p).folder ?: return emptyList()
        return Loops.of(Git.state.value[folder], Pulses.state.value[folder], ClaudeLink.statusIn(folder, ClaudeLink.sessions.value), Receipts.state.value[folder].orEmpty())
    }

    // ── the search ──

    /** Folders that are never searched: dependencies, build output, version control, editors' own. */
    private val SKIP_DIRS = setOf("node_modules", ".git", "build", "dist", "out", "target", ".gradle", ".idea", ".next", ".nuxt", ".venv", "venv",
        "__pycache__", ".turbo", ".cache", "coverage", ".dart_tool", "Pods", ".expo", ".wrangler", "bin", "obj")
    private val TEXT = setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "mjs", "cjs", "json", "md", "txt", "html", "css", "scss", "xml", "gradle",
        "py", "go", "rs", "swift", "dart", "vue", "svelte", "sql", "yml", "yaml", "toml", "sh", "ps1", "cs", "c", "cpp", "h", "rb", "php")

    /** The files that hold keys: never opened, whatever they're called inside. */
    fun secret(f: File): Boolean {
        val n = f.name.lowercase()
        return n.startsWith(".env") || n.endsWith(".pem") || n.endsWith(".key") || n.endsWith(".p12") || n.endsWith(".pfx") || n.endsWith(".jks") ||
            n.endsWith(".keystore") || n.contains("secret") || n.contains("credential") || n.startsWith("id_rsa") || n.startsWith("id_ed25519") ||
            n == "local.properties" || n.endsWith("keystore.properties") || n.endsWith("signing.properties") || n == ".npmrc" || n == ".netrc"
    }

    private fun search(text: String, project: String): String {
        val needle = text.trim().lowercase()
        if (needle.length < 3) return "Give at least three letters to search for."
        val only = project.takeIf { it.isNotBlank() }?.let { find(it) ?: return "No project called \"$it\"." }
        val hits = mutableListOf<String>()
        var files = 0
        // The first search opens every file cold; past this, it says what it found so far.
        val until = System.currentTimeMillis() + 20_000
        var partial = false
        for (p in deck().projects) {
            if (only != null && p.id != only.id) continue
            val root = local(p).dir ?: continue
            fun walk(dir: File) {
                for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
                    if (hits.size >= 40 || files >= 20_000) return
                    if (System.currentTimeMillis() > until) { partial = true; return }
                    if (f.isDirectory) { if (f.name !in SKIP_DIRS && !f.name.startsWith(".") || f.name == ".github") walk(f); continue }
                    if (secret(f) || f.extension.lowercase() !in TEXT || f.length() > 400_000) continue
                    files++
                    var inFile = 0
                    runCatching {
                        f.useLines(Charsets.UTF_8) { lines ->
                            lines.forEachIndexed { i, line ->
                                if (inFile < 3 && hits.size < 40 && line.lowercase().contains(needle)) {
                                    hits += "${p.name}: ${f.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${line.trim().take(200)}"
                                    inFile++
                                }
                            }
                        }
                    }
                }
            }
            walk(root)
        }
        val cut = if (partial) "\n(searched for 20 seconds and stopped before the end: narrow it down with project)" else ""
        if (hits.isEmpty()) return "\"$text\" isn't in the projects' files" + (only?.let { " (${it.name})" } ?: "") + "." + cut
        return hits.joinToString("\n") + (if (hits.size >= 40) "\n(more places; narrow it down with project or longer words)" else "") + cut
    }
}
