package com.yonidev.ydeck.desktop

import java.io.File

/*
 * How a command from the phone runs: Windows PowerShell (there on every Windows) runs a small script, written here by
 * YDeck, that runs the line in the shell chosen in Settings, in the project's folder, and passes every line it prints
 * both to the screen and to a log file as it comes. YDeck reads the log for the phone; the script writes the exit code
 * to its own file at the end. The same script runs in a new terminal window ([window]) or with no window at all.
 *
 * The line itself never goes on a command line: it's written to a file of its own, and the script reads it, so
 * quoting can't change what runs.
 */
object RunScript {

    private const val SCRIPT = """# Written by YDeck (rewritten on every start): runs one command from the phone, see RunScript.kt.
param([string]${'$'}Shell, [string]${'$'}Exe, [string]${'$'}LineFile, [string]${'$'}Log, [string]${'$'}Exit, [switch]${'$'}Stay)
${'$'}utf8 = New-Object System.Text.UTF8Encoding ${'$'}false
try { [Console]::OutputEncoding = ${'$'}utf8 } catch {}
${'$'}OutputEncoding = ${'$'}utf8
${'$'}env:CHERE_INVOKING = '1'
${'$'}w = New-Object System.IO.StreamWriter(${'$'}Log, ${'$'}false, ${'$'}utf8)
${'$'}w.AutoFlush = ${'$'}true
${'$'}code = 1
try {
    switch (${'$'}Shell) {
        'cmd' {
            ${'$'}line = [IO.File]::ReadAllText(${'$'}LineFile, ${'$'}utf8)
            & ${'$'}Exe /d /s /c "chcp 65001>nul & ${'$'}line" 2>&1 | ForEach-Object { ${'$'}s = "${'$'}_"; ${'$'}w.WriteLine(${'$'}s); ${'$'}s }
        }
        'bash' { & ${'$'}Exe --login (${'$'}LineFile -replace '\\', '/') 2>&1 | ForEach-Object { ${'$'}s = "${'$'}_"; ${'$'}w.WriteLine(${'$'}s); ${'$'}s } }
        default { & ${'$'}Exe -NoLogo -ExecutionPolicy Bypass -File ${'$'}LineFile 2>&1 | ForEach-Object { ${'$'}s = "${'$'}_"; ${'$'}w.WriteLine(${'$'}s); ${'$'}s } }
    }
    ${'$'}code = ${'$'}LASTEXITCODE
    if (${'$'}code -eq ${'$'}null) { ${'$'}code = 1 }
} catch {
    ${'$'}w.WriteLine("${'$'}_")
    "${'$'}_"
} finally {
    ${'$'}w.Close()
}
[IO.File]::WriteAllText(${'$'}Exit, "${'$'}code")
if (${'$'}Stay) {
    Write-Host ""
    Write-Host "[YDeck] exit code ${'$'}code" -ForegroundColor DarkGray
    switch (${'$'}Shell) {
        'cmd' { & ${'$'}Exe /k }
        'bash' { & ${'$'}Exe --login -i }
        default { & ${'$'}Exe -NoLogo -NoExit }
    }
}
"""

    private val dir = File(AppDirs.local, "runs").apply { mkdirs() }
    private val script = File(dir, "run.ps1")

    /** Windows PowerShell 5.1, which runs the script whatever shell runs the line. */
    val host: File get() = File(System.getenv("WINDIR") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe")

    /** The files of one run: the line, its output as it comes, its exit code. */
    class Files(val line: File, val log: File, val exit: File)

    fun files(id: String, shell: Shell): Files {
        val safe = id.filter { it.isLetterOrDigit() || it == '-' }.take(64)
        val ext = when (shell) { Shell.CMD -> "txt"; Shell.BASH -> "sh"; else -> "ps1" }
        return Files(File(dir, "$safe.$ext"), File(dir, "$safe.log"), File(dir, "$safe.exit"))
    }

    /** The script's arguments for [line] in [shell] (apart, so a test runs them as they are). The line goes to its file now. */
    fun args(shell: Shell, exe: String, line: String, f: Files, stay: Boolean): List<String> {
        script.writeText(SCRIPT.replace("\n", "\r\n"), Charsets.UTF_8)
        listOf(f.log, f.exit).forEach { it.delete() }
        val utf8 = Charsets.UTF_8
        when (shell) {
            Shell.CMD -> f.line.writeText(line, utf8)
            Shell.BASH -> f.line.writeText(line + "\n", utf8)
            // As the line would end in a terminal: its own exit code, else whether it failed.
            else -> f.line.writeText("\uFEFFtry { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding ${'$'}false } catch {}\r\n" +
                "$line\r\nif (${'$'}?) { exit 0 } elseif (${'$'}LASTEXITCODE) { exit ${'$'}LASTEXITCODE } else { exit 1 }\r\n", utf8)
        }
        val kind = when (shell) { Shell.CMD -> "cmd"; Shell.BASH -> "bash"; else -> "ps" }
        return listOf(host.path, "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.path,
            "-Shell", kind, "-Exe", exe, "-LineFile", f.line.path, "-Log", f.log.path, "-Exit", f.exit.path) + if (stay) listOf("-Stay") else emptyList()
    }

    /** A run's files once it's over and told. */
    fun clean(f: Files) = listOf(f.line, f.log, f.exit).forEach { it.delete() }
}
