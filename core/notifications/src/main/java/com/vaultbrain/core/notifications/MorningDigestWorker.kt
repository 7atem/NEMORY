package com.vaultbrain.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultbrain.core.database.dao.VaultItemDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class MorningDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vaultItemDao: VaultItemDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }.timeInMillis

        val expiringSoon = vaultItemDao.getExpiringSoon(now, 5)

        val itemsExpiringToday = expiringSoon.filter { it.expiryDate != null && it.expiryDate!! <= endOfDay }

        if (itemsExpiringToday.isNotEmpty()) {
            postDigestNotification(itemsExpiringToday.size)
        }

        return Result.success()
    }

    private fun postDigestNotification(count: Int) {
        val context = applicationContext
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        val channelId = "morning_digest"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Morning Digest",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intentClass = Class.forName("com.vaultbrain.app.MainActivity")
        val intent = Intent(context, intentClass).apply {
            action = "com.nemory.app.ACTION_VIEW_EXPIRING"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Good morning")
            .setContentText("You have $count item${if (count > 1) "s" else ""} needing your attention today.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, notification)
    }

    companion object {
        const val DIGEST_NOTIFICATION_ID = 8181
    }
}
