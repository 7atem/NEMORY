package com.vaultbrain.core.notifications

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.model.LensId
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.database.dao.NotificationQueueDao
import com.vaultbrain.core.database.entity.NotificationQueueEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max

/**
 * Schedules and delivers local notifications for vault items.
 */
class UnifiedAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val notificationDao: NotificationQueueDao
) {

    /**
     * Schedules a notification for [item] to be delivered no earlier than [triggerAt].
     */
    suspend fun schedule(
        item: VaultItem,
        triggerAt: Long,
        title: String,
        body: String,
        channelId: String
    ) {
        val id = UUID.randomUUID().toString()
        val entity = NotificationQueueEntity(
            id = id,
            itemId = item.id,
            triggerAt = triggerAt,
            channelId = channelId,
            title = title,
            body = body,
            isDelivered = false
        )
        notificationDao.insert(entity)

        val delay = max(triggerAt - System.currentTimeMillis(), 0L)
        val workRequest = OneTimeWorkRequestBuilder<AlertWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(KEY_NOTIFICATION_ID, id)
                    .build()
            )
            .addTag(WORK_TAG)
            .addTag(itemTag(item.id))
            .build()

        workManager.enqueueUniqueWork(
            "$WORK_TAG:$id",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Cancels all pending notifications for the given item id.
     */
    suspend fun cancelForItem(itemId: String) {
        notificationDao.deleteForItem(itemId)
        workManager.cancelAllWorkByTag(itemTag(itemId))
    }

    /**
     * Convenience method called after saving a vault item. Automatically schedules
     * notifications for expiry date and secondary alert date when present.
     */
    suspend fun scheduleAlerts(item: VaultItem) {
        // Cancel any existing alerts for this item first.
        cancelForItem(item.id)

        val now = System.currentTimeMillis()
        val scheduledTriggers = mutableSetOf<Long>()

        item.expiryDate?.let { expiryMs ->
            val reminderDays = when {
                LensId.BUREAUCRACY in item.lensTags -> listOf(90, 30, 7)
                LensId.TRAVEL in item.lensTags -> listOf(30, 7)
                else -> listOf(7)
            }
            reminderDays.forEach { days ->
                val trigger = expiryMs - TimeUnit.DAYS.toMillis(days.toLong())
                if (trigger > now && scheduledTriggers.add(trigger)) {
                    schedule(
                        item = item,
                        triggerAt = trigger,
                        title = "${item.title} expires in $days days",
                        body = "Review the saved document and renew it if needed.",
                        channelId = CHANNEL_REMINDER
                    )
                }
            }
            if (expiryMs > now && scheduledTriggers.add(expiryMs)) {
                schedule(
                    item = item,
                    triggerAt = expiryMs,
                    title = "Expiring: ${item.title}",
                    body = "This item expires today. Tap to view details.",
                    channelId = CHANNEL_CRITICAL
                )
            }
        }

        item.secondaryAlertDate?.let { alertMs ->
            if (alertMs > now && scheduledTriggers.add(alertMs)) {
                val (title, body) = secondaryAlertCopy(item)
                schedule(
                    item = item,
                    triggerAt = alertMs,
                    title = title,
                    body = body,
                    channelId = CHANNEL_REMINDER
                )
            }
        }
    }

    private fun secondaryAlertCopy(item: VaultItem): Pair<String, String> {
        if (LensId.MEDIA in item.lensTags) {
            val mediaType = item.customFields["media_type"]
                ?: item.parsedMetadata["media_type"]
            val verb = if (mediaType == "book") "read" else "watch"
            return "Ready to $verb: ${item.title}" to
                "You saved this for later. Tap to open it in Nemory."
        }
        val vaccine = item.parsedMetadata["vaccine_name"]?.takeIf(String::isNotBlank)
        if (LensId.HEALTH in item.lensTags && vaccine != null) {
            val patient = item.parsedMetadata["patient_name"]?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.alert_vaccine_default_patient)
            return context.getString(R.string.alert_vaccine_title) to
                context.getString(R.string.alert_vaccine_body, patient, vaccine)
        }
        if (item.aiClassification == Classification.INVOICE) {
            return context.getString(R.string.alert_invoice_title) to
                context.getString(R.string.alert_invoice_body, item.title)
        }
        return "Upcoming: ${item.title}" to
            "A reminder you saved is due now. Tap to review it."
    }

    private fun itemTag(itemId: String) = "notification_item_$itemId"

    companion object {
        private const val WORK_TAG = "AlertWorker"
        private const val KEY_NOTIFICATION_ID = "notification_id"
        private const val CHANNEL_CRITICAL = "CRITICAL"
        private const val CHANNEL_REMINDER = "REMINDER"
    }
}

/**
 * WorkManager worker that queries due notifications and posts Android notifications.
 */
@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationDao: NotificationQueueDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val due = notificationDao.getDue(now)
        due.forEach { entity ->
            if (postNotification(entity)) {
                notificationDao.update(entity.copy(isDelivered = true))
            }
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission") // Guarded immediately below on Android 13+.
    private fun postNotification(entity: NotificationQueueEntity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureChannel(entity.channelId)

        val notification = NotificationCompat.Builder(applicationContext, entity.channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(entity.title)
            .setContentText(entity.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    entity.id.hashCode(),
                    Intent(
                        Intent.ACTION_VIEW,
                        "nemory://item/${android.net.Uri.encode(entity.itemId)}".toUri()
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(entity.id.hashCode(), notification)
        return true
    }

    private fun ensureChannel(channelId: String) {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (notificationManager?.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                channelId,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
