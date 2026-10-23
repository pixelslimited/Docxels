package com.docscan.app.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.docscan.app.R
import java.util.concurrent.TimeUnit

/**
 * Tells the person about a new build soon after it is published, even when the app is closed: Android runs a tiny check
 * about every 15 minutes (its shortest allowed interval) and shows one notification per build. The check only reads the
 * public update file (see [UpdateInfo.hostAllowed]) and sends nothing about the person. It does nothing when
 * "Announcements and update notices" is off in Settings.
 */
object UpdateWatch {
    private const val WORK = "docxels-update-watch"
    private const val CHANNEL = "updates"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Returns true when the notification was shown (false when notifications are blocked, so it is tried again later). */
    @SuppressLint("MissingPermission")
    fun notify(context: Context, info: UpdateInfo): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = NotificationChannel(CHANNEL, "New versions", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Tells you when a new version of Docxels is ready"
        manager.createNotificationChannel(channel)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("A new version of Docxels is ready")
            .setContentText("Version ${info.versionName}. Open Docxels and tap Update now.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(1001, n) }.isSuccess
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ctx.getSharedPreferences("docxels_ui", Context.MODE_PRIVATE).getBoolean("announcements", true)) return Result.success()
        val info = runCatching { Updater(ctx).check() }.getOrNull() ?: return Result.success()
        val prefs = ctx.getSharedPreferences("docxels_announce", Context.MODE_PRIVATE)
        if (info.versionCode <= prefs.getLong("notified_update", 0L)) return Result.success()
        if (UpdateWatch.notify(ctx, info)) prefs.edit().putLong("notified_update", info.versionCode).apply()
        return Result.success()
    }
}
