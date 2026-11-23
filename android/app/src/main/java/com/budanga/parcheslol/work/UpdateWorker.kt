package com.budanga.parcheslol.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budanga.parcheslol.MainActivity
import com.budanga.parcheslol.R
import com.budanga.parcheslol.data.DATA_BASE_URL
import com.budanga.parcheslol.data.Loaded
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.data.valueOrNull
import com.budanga.parcheslol.domain.PatchAlert
import com.budanga.parcheslol.domain.adcGroups
import com.budanga.parcheslol.domain.alertText
import com.budanga.parcheslol.domain.newsToNotify
import com.budanga.parcheslol.domain.patchesToNotify
import com.budanga.parcheslol.domain.toMarkNotified
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val CHANNEL_PATCHES = "patches"
private const val CHANNEL_NEWS = "news"

/** Every ~3 h: announce new patches (with how many changes touch your ADCs) and, if enabled, new news. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository(DATA_BASE_URL, File(applicationContext.filesDir, "data"))
        val prefs = Prefs(applicationContext)
        val index = (repo.index() as? Loaded.Fresh)?.value ?: return Result.retry()

        val notified = prefs.notifiedPatches.first()
        val myAdcs = prefs.myAdcs.first()
        val myItems = prefs.myItems.first()
        val firstRun = notified.isEmpty()
        val delivered = mutableListOf<String>()
        var failed = false
        for (patch in patchesToNotify(index, notified, firstRun)) {
            // A patch whose file can't be downloaded stays unannounced and is retried later.
            val file = repo.patch(patch).valueOrNull ?: run { failed = true; null } ?: continue
            val count = adcGroups(file, myAdcs, myItems).filter { it.entity != null }.sumOf { it.changes.size }
            val (title, body) = alertText(PatchAlert(patch, count))
            notify(patch.hashCode(), CHANNEL_PATCHES, title, body, null)
            delivered += patch
        }
        prefs.addNotified(toMarkNotified(index, firstRun, delivered))

        val news = repo.news().valueOrNull.orEmpty()
        if (prefs.newsAlerts.first()) newsToNotify(news, prefs.lastNewsUrl.first())?.let {
            notify(it.url.hashCode(), CHANNEL_NEWS, it.title, it.description, it.url)
        }
        news.maxByOrNull { it.publishedAt }?.let { prefs.setLastNewsUrl(it.url) }
        return if (failed) Result.retry() else Result.success()
    }

    private fun notify(id: Int, channel: String, title: String, body: String?, url: String?) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = if (url != null) Intent(Intent.ACTION_VIEW, Uri.parse(url)) else Intent(ctx, MainActivity::class.java)
        val pending = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .apply { body?.let { setContentText(it) } }
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PATCHES, context.getString(R.string.channel_patches), NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_NEWS, context.getString(R.string.channel_news), NotificationManager.IMPORTANCE_LOW))
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
