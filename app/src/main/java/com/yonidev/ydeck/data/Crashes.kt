package com.yonidev.ydeck.data

import android.content.Context
import java.io.File

/**
 * When the app crashes, what went wrong (the error and where in the code) is kept in a file; the next time it opens,
 * it goes to the computer (see Note.CRASH), where it's kept in phone-crashes.txt to be fixed. Nothing else is in it.
 */
object Crashes {
    @Volatile private var caught = false

    private fun file(c: Context) = File(c.applicationContext.filesDir, "crash.txt")

    fun catchIn(c: Context) {
        if (caught) return
        caught = true
        val app = c.applicationContext
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()
                file(app).writeText("YDeck $version · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL} · thread ${t.name}\n" + e.stackTraceToString().take(3800))
            }
            before?.uncaughtException(t, e)
        }
    }

    /** The last crash's report, once (the file is removed); null when there was none. */
    fun take(c: Context): String? = runCatching {
        val f = file(c)
        if (!f.isFile) return null
        f.readText().also { f.delete() }.takeIf { it.isNotBlank() }
    }.getOrNull()
}
