package com.yonidev.ydeck.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/*
 * Where a project stands, read from its folder every little while (with git, see [Git]): the last Claude Code
 * conversation in it, a dev server running from it, the .env keys it still lacks, its version. All local, read only.
 */

data class Pulse(
    /** When the last Claude Code conversation in the folder was written to (ms), or null if there's none. */
    val claudeAt: Long? = null,
    /** Ports a dev server started from the folder listens on, lowest first. */
    val ports: List<Int> = emptyList(),
    /** Keys in .env.example that no .env file of the folder sets. */
    val missingEnv: List<String> = emptyList(),
    /** "0.8 · 8" (Android versionName · versionCode) or package.json's version. */
    val version: String? = null,
    /** What's in progress, from .ydeck.local.json (Claude Code writes it, see [ProjectFiles]). */
    val now: String = "",
    /** The latest release listed in .ydeck.json. */
    val release: Release? = null,
    /** The summary in .ydeck.json, and when it was written (ms). */
    val summary: String = "",
    val summaryAt: Long = 0,
    /** The project's memory: decisions (.ydeck.json) and the last session's handoff (.ydeck.local.json). */
    val decisions: List<Decision> = emptyList(),
    val handoff: Handoff? = null,
    /** The handoff's next step while it's still a suggestion, see [LocalFile.suggestion]. */
    val suggestion: String = "",
) {
    val port: Int? get() = ports.firstOrNull()
}

object Pulses {
    private val _state = MutableStateFlow<Map<String, Pulse>>(emptyMap())
    /** By folder path. */
    val state: StateFlow<Map<String, Pulse>> = _state

    suspend fun refresh(folders: Collection<String>) = withContext(Dispatchers.IO) {
        val dirs = folders.distinct().map(::File).filter { it.isDirectory }
        val ports = runCatching { Servers.scan(dirs) }.getOrDefault(emptyMap())
        val shared = dirs.associateWith { ProjectFiles.readShared(it) }
        _state.value = dirs.associate { d ->
            val local = ProjectFiles.readLocal(d)
            d.path to Pulse(
                Claude.lastSession(d), ports[d.path].orEmpty(), runCatching { Env.missing(d) }.getOrDefault(emptyList()), runCatching { Versions.of(d) }.getOrNull(),
                local?.now.orEmpty(), shared[d]?.releases?.firstOrNull(), shared[d]?.summary.orEmpty(), shared[d]?.summaryAt ?: 0,
                shared[d]?.decisions.orEmpty(), local?.handoff, local?.suggestion.orEmpty(),
            )
        }
    }
}

/** "3 days ago" / "לפני 3 ימים", for a moment in ms. */
fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
    val min = ((now - ms) / 60_000).coerceAtLeast(0)
    val h = min / 60
    val d = h / 24
    return when {
        min < 2 -> tr("just now", "עכשיו")
        min < 60 -> tr("$min min ago", "לפני $min דקות")
        h < 2 -> tr("an hour ago", "לפני שעה")
        h < 24 -> tr("$h hours ago", "לפני $h שעות")
        d < 2 -> tr("yesterday", "אתמול")
        d < 7 -> tr("$d days ago", "לפני $d ימים")
        d < 14 -> tr("last week", "לפני שבוע")
        d < 30 -> tr("${d / 7} weeks ago", "לפני ${d / 7} שבועות")
        d < 60 -> tr("a month ago", "לפני חודש")
        d < 365 -> tr("${d / 30} months ago", "לפני ${d / 30} חודשים")
        d < 730 -> tr("a year ago", "לפני שנה")
        else -> tr("${d / 365} years ago", "לפני ${d / 365} שנים")
    }
}

// ── Claude Code ──

/** Claude Code keeps each folder's conversations in ~/.claude/projects/<the path, every other character a dash>. */
object Claude {
    private val root = File(System.getProperty("user.home"), ".claude/projects")

    fun sessionsDir(dir: File): File = File(root, dir.absolutePath.replace(Regex("[^A-Za-z0-9]"), "-"))

    fun lastSession(dir: File): Long? =
        sessionsDir(dir).listFiles { f -> f.isFile && f.extension == "jsonl" }?.maxOfOrNull { it.lastModified() }?.takeIf { it > 0 }
}

// ── dev servers ──

/**
 * Which project folders a dev server runs from, and on what ports: the listening sockets (netstat), each owner's
 * program, and, only when a server-like program listens, the command lines of all processes. A server belongs to a
 * folder when its program or command line (or a parent's, as with `npm run dev`) is inside the folder.
 */
object Servers {
    /** By folder: the processes its dev servers run in (from the last scan), so they can be stopped. */
    @Volatile var pids: Map<String, Set<Long>> = emptyMap()
    @Volatile private var known: Set<Long> = emptySet()
    @Volatile private var table: Map<Long, Pair<Long, String>> = emptyMap()
    private val RUNTIMES = setOf("node", "bun", "deno", "python", "python3", "php", "ruby", "dotnet", "workerd", "hugo", "caddy", "esbuild", "uvicorn", "flask")

    fun scan(dirs: List<File>): Map<String, List<Int>> {
        pids = emptyMap()
        if (dirs.isEmpty()) return emptyMap()
        val listening = listeners()
        if (listening.isEmpty()) return emptyMap()
        val candidates = listening.keys.filter { pid -> exeOf(pid)?.let { File(it).nameWithoutExtension.lowercase() in RUNTIMES } == true }
        if (candidates.isEmpty()) return emptyMap()
        // The same servers as last time: their command lines haven't changed, so PowerShell isn't asked again.
        val procs = if (candidates.toSet() == known && known.isNotEmpty()) table else processes().also { table = it; known = candidates.toSet() }
        val roots = dirs.map { it to it.absolutePath.lowercase().trimEnd('\\') + "\\" }.sortedByDescending { it.second.length }
        val out = mutableMapOf<String, MutableSet<Int>>()
        val owners = mutableMapOf<String, MutableSet<Long>>()
        for (pid in candidates) {
            // The process and up to three parents: npm → node vite.js, cmd → node next.
            var p: Long? = pid
            var depth = 0
            var owner: File? = null
            while (p != null && depth < 4 && owner == null) {
                val text = ((procs[p]?.second ?: "") + " " + (exeOf(p) ?: "")).lowercase()
                owner = roots.firstOrNull { (_, prefix) -> text.contains(prefix) }?.first
                p = procs[p]?.first?.takeIf { it != p && it > 4 }
                depth++
            }
            if (owner != null) {
                out.getOrPut(owner.path) { sortedSetOf() } += listening[pid].orEmpty()
                owners.getOrPut(owner.path) { mutableSetOf() } += pid
            }
        }
        pids = owners
        return out.mapValues { (_, ports) -> ports.sortedWith(compareBy({ it >= 10000 }, { it })) }
    }

    /** Stops the dev servers running from [folder] (each process and the ones it started). Returns how many stopped. */
    fun stop(folder: String): Int {
        val list = pids[folder].orEmpty()
        list.forEach { pid ->
            // Still the dev server it was (a process id can be reused): a runtime like node, else it's left alone.
            ProcessHandle.of(pid).filter { h -> h.info().command().map { File(it).nameWithoutExtension.lowercase() in RUNTIMES }.orElse(false) }.ifPresent { h ->
                h.descendants().forEach { it.destroy() }
                h.destroy()
                runCatching { h.onExit().get(3, TimeUnit.SECONDS) }.onFailure { h.destroyForcibly() }
            }
        }
        pids = pids - folder
        return list.size
    }

    /** pid → listening TCP ports, from `netstat -ano -p TCP` and TCPv6. */
    private fun listeners(): Map<Long, Set<Int>> {
        val text = listOf("TCP", "TCPv6").joinToString("\n") { run(listOf("netstat", "-ano", "-p", it)) ?: "" }
        return parseNetstat(text)
    }

    fun parseNetstat(text: String): Map<Long, Set<Int>> {
        val out = mutableMapOf<Long, MutableSet<Int>>()
        text.lines().forEach { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size < 5 || !cols[0].startsWith("TCP") || cols[3] != "LISTENING") return@forEach
            val port = cols[1].substringAfterLast(':').toIntOrNull() ?: return@forEach
            val pid = cols[4].toLongOrNull()?.takeIf { it > 4 } ?: return@forEach
            out.getOrPut(pid) { mutableSetOf() } += port
        }
        return out
    }

    private fun exeOf(pid: Long): String? = ProcessHandle.of(pid).flatMap { it.info().command() }.orElse(null)

    /** pid → (parent pid, command line), for every process (one PowerShell call). */
    private fun processes(): Map<Long, Pair<Long, String>> {
        val script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-CimInstance Win32_Process | ForEach-Object { \"\$(\$_.ProcessId)`t\$(\$_.ParentProcessId)`t\$(\$_.CommandLine)\" }"
        val text = run(listOf(Ps.exe, "-NoProfile", "-NonInteractive", "-EncodedCommand", Ps.encode(script)), 15) ?: return emptyMap()
        return text.lines().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            val pid = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: return@mapNotNull null
            pid to ((parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L) to parts.getOrNull(2).orEmpty())
        }.toMap()
    }

    private fun run(cmd: List<String>, seconds: Long = 6): String? = runCatching {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!p.waitFor(seconds, TimeUnit.SECONDS)) { p.destroy(); return null }
        out
    }.getOrNull()
}

/** Windows PowerShell, given scripts as -EncodedCommand (no quoting to get wrong). */
object Ps {
    val exe: String = File(System.getenv("WINDIR") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe").path

    fun encode(script: String): String = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
}

// ── .env ──

/** The keys a project's .env.example lists that none of its .env files set (values are never read out). */
object Env {
    private val EXAMPLES = listOf(".env.example", ".env.sample", ".env.template", ".env.local.example", ".env.dist")
    private val ACTUAL = listOf(".env", ".env.local", ".env.development", ".env.development.local")

    fun missing(dir: File): List<String> {
        val example = EXAMPLES.map { File(dir, it) }.firstOrNull { it.isFile } ?: return emptyList()
        val have = ACTUAL.map { File(dir, it) }.filter { it.isFile }.flatMap { keys(it.readText()) }.toSet()
        return keys(example.readText()).filter { it !in have }.distinct()
    }

    fun keys(text: String): List<String> = text.lines().mapNotNull { line ->
        val t = line.trim().removePrefix("export ").trim()
        if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
        t.substringBefore('=', "").trim().takeIf { Regex("[A-Za-z_][A-Za-z0-9_.]*").matches(it) }
    }
}

// ── version ──

object Versions {
    fun of(dir: File): String? {
        // Android: the app module's versionName and versionCode, when they're written out.
        listOf("app/build.gradle.kts", "app/build.gradle", "build.gradle.kts", "build.gradle").map { File(dir, it) }.firstOrNull { it.isFile }?.readText()?.let { g ->
            val name = Regex("""versionName\s*=?\s*"([^"]+)"""").find(g)?.groupValues?.get(1)
            val code = Regex("""versionCode\s*=?\s*(\d+)""").find(g)?.groupValues?.get(1)
            if (name != null) return listOfNotNull(name, code).joinToString(" · ")
        }
        File(dir, "package.json").takeIf { it.isFile }?.let { f ->
            runCatching { JSONObject(f.readText()).optString("version") }.getOrNull()?.takeIf { it.isNotBlank() && it != "0.0.0" }?.let { return it }
        }
        return null
    }
}
