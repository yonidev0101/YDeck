package com.yonidev.ydeck.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.yonidev.ydeck.R
import com.yonidev.ydeck.ui.MainActivity
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/*
 * The good morning on the phone (see Morning in shared/.../Snapshot.kt): the computer writes it a little before the
 * hour set there, and the phone wakes at that hour, takes it from the relay and shows it as a notification. Tapped, it
 * opens the conversation with YDeck, where the answer goes on. No push service: the phone only reaches out.
 */
object MorningNotice {

    private const val CHANNEL = "morning"
    private const val WORK = "morning"
    private const val ID = 4108

    private fun seenFile(c: Context) = File(c.applicationContext.filesDir, "morning.json")

    /** The good morning last shown here, and the hour the phone last set itself to wake for (minutes, -1: none). */
    private fun seen(c: Context): Pair<String, Int> = runCatching {
        val o = org.json.JSONObject(seenFile(c).readText())
        o.optString("id") to o.optInt("at", -1)
    }.getOrDefault("" to -1)

    private fun keep(c: Context, id: String, at: Int) = runCatching {
        val o = runCatching { org.json.JSONObject(seenFile(c).readText()) }.getOrDefault(org.json.JSONObject())
        seenFile(c).writeText(o.put("id", id).put("at", at).toString())
    }

    /** What was done here with each topic of the good morning (see [com.yonidev.ydeck.ui.topicKey]): the latest ones. */
    fun handled(c: Context): Map<String, String> = runCatching {
        val h = org.json.JSONObject(seenFile(c).readText()).getJSONObject("handled")
        h.keys().asSequence().associateWith { h.getString(it) }
    }.getOrDefault(emptyMap())

    fun handle(c: Context, key: String, what: String) = runCatching {
        val o = runCatching { org.json.JSONObject(seenFile(c).readText()) }.getOrDefault(org.json.JSONObject())
        val h = (handled(c) + (key to what)).entries.toList().takeLast(20)
        seenFile(c).writeText(o.put("handled", org.json.JSONObject().apply { h.forEach { put(it.key, it.value) } }).toString())
    }

    /** A good morning not shown yet, and fresh (from the last 12 hours): true once, then never again for it. */
    fun isNew(c: Context, id: String, at: Long): Boolean =
        id.isNotEmpty() && id != seen(c).first && System.currentTimeMillis() - at < 12 * 3600_000L

    /** Shows it: the first lines as the notification, all of it when opened. Marks it seen either way. */
    fun show(c: Context, id: String, text: String) {
        keep(c, id, seen(c).second)
        val app = c.applicationContext
        if (app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = app.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "בוקר טוב", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "ההודעה של YDeck בבוקר: מה עושים היום"
            })
        val plain = text.replace(Regex("[*_`#>]"), "").replace(Regex("\\n{2,}"), "\n").trim()
        val open = PendingIntent.getActivity(app, ID, MainActivity.homeIntent(app), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("בוקר טוב")
            .setContentText(plain.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(plain.take(900)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(ID, n) }
    }

    /**
     * Wakes the phone at [minute] (from midnight) each day to take the good morning; [minute] -1 stops it. Set again
     * only when the hour changed, and after each wake for the next day.
     */
    fun schedule(c: Context, minute: Int, force: Boolean = false) {
        val app = c.applicationContext
        val wm = WorkManager.getInstance(app)
        val (id, at) = seen(app)
        if (minute < 0) { if (at >= 0) { wm.cancelUniqueWork(WORK); keep(app, id, -1) }; return }
        if (at == minute && !force) return
        val now = LocalDateTime.now()
        // A minute after the hour, so the computer's has surely reached the relay.
        var next = now.toLocalDate().atStartOfDay().plusMinutes(minute + 1L)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<Wake>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        keep(app, id, minute)
    }

    class Wake(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): androidx.work.ListenableWorker.Result {
            Computer.sync(applicationContext)
            // Tomorrow's, at the hour the computer says now.
            schedule(applicationContext, Computer.state(applicationContext).morningAt, force = true)
            return androidx.work.ListenableWorker.Result.success()
        }
    }
}
