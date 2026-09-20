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
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.database.dao.NotificationQueueDao
import com.vaultbrain.shared.database.entity.NotificationQueueEntity
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
            targetId = item.id,
            targetType = "VAULT_ITEM",
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
     * Schedules a notification for an external record.
     */
    suspend fun scheduleForExternal(
        targetId: String,
        triggerAt: Long,
        title: String,
        body: String,
        channelId: String
    ) {
        val id = UUID.randomUUID().toString()
        val entity = NotificationQueueEntity(
            id = id,
            targetId = targetId,
            targetType = "EXTERNAL_RECORD",
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
            .addTag(itemTag(targetId))
            .build()

        workManager.enqueueUniqueWork(
            "$WORK_TAG:$id",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Cancels all pending notifications for the given target id.
     */
    suspend fun cancelForTarget(targetId: String) {
        notificationDao.deleteForTarget(targetId)
        workManager.cancelAllWorkByTag(itemTag(targetId))
    }

    /**
     * Cancels all pending notifications for the given item id (alias for target).
     */
    suspend fun cancelForItem(itemId: String) = cancelForTarget(itemId)

    suspend fun dismissNotification(id: String) {
        notificationDao.deleteById(id)
        workManager.cancelUniqueWork("$WORK_TAG:$id")
    }

    suspend fun snoozeNotification(id: String) {
        val delay = TimeUnit.HOURS.toMillis(24)
        if (notificationDao.snoozeById(id, System.currentTimeMillis() + delay) == 0) return
        val request = OneTimeWorkRequestBuilder<AlertWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_NOTIFICATION_ID, id).build())
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork("$WORK_TAG:$id", ExistingWorkPolicy.REPLACE, request)
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

    private fun ensureChannel(channelId: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager?.getNotificationChannel(channelId) == null) {
            val (name, desc, importance) = when (channelId) {
                CHANNEL_CALENDAR -> Triple(
                    context.getString(R.string.calendar_notification_channel),
                    context.getString(R.string.calendar_notification_channel_desc),
                    NotificationManager.IMPORTANCE_HIGH
                )
                CHANNEL_GMAIL -> Triple(
                    context.getString(R.string.gmail_notification_channel),
                    context.getString(R.string.gmail_notification_channel_desc),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                else -> Triple(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT)
            }
            val channel = NotificationChannel(
                channelId,
                name,
                importance
            ).apply {
                description = desc
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val WORK_TAG = "AlertWorker"
        private const val KEY_NOTIFICATION_ID = "notification_id"
        private const val CHANNEL_CRITICAL = "CRITICAL"
        private const val CHANNEL_REMINDER = "REMINDER"
        const val CHANNEL_CALENDAR = "CALENDAR_EVENTS"
        const val CHANNEL_GMAIL = "GMAIL_SYNC"
    }
}

/**
 * WorkManager worker that queries due notifications and posts Android notifications.
 */
@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted private val applicationContext: Context,
    @Assisted params: WorkerParameters,
    private val notificationDao: NotificationQueueDao
) : CoroutineWorker(applicationContext, params) {

    override suspend fun doWork(): Result {
        if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return Result.retry()
        val now = System.currentTimeMillis()
        val due = notificationDao.getDue(now)
        due.forEach { entity ->
            if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return Result.retry()
            if (notificationDao.claimVisible(entity.id, entity.triggerAt, now) == 1) {
                try {
                    if (!postNotification(entity)) {
                        notificationDao.releaseClaim(entity.id, entity.triggerAt)
                        return Result.retry()
                    }
                } catch (error: Exception) {
                    notificationDao.releaseClaim(entity.id, entity.triggerAt)
                    throw error
                }
            }
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission") // Guarded immediately below on Android 13+.
    private fun postNotification(entity: NotificationQueueEntity): Boolean {
        if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureChannel(entity.channelId)

        val intentUri = if (entity.targetType == "EXTERNAL_RECORD") {
            "nemory://external/${android.net.Uri.encode(entity.targetId)}".toUri()
        } else {
            "nemory://item/${android.net.Uri.encode(entity.targetId)}".toUri()
        }

        // Snooze action — sends to NotificationActionReceiver
        val snoozeIntent = PendingIntent.getBroadcast(
            applicationContext,
            entity.id.hashCode() + 1,
            Intent(NotificationActionReceiver.ACTION_SNOOZE).apply {
                setClass(applicationContext, NotificationActionReceiver::class.java)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, entity.id.hashCode())
                putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, entity.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = PendingIntent.getBroadcast(
            applicationContext,
            entity.id.hashCode() + 2,
            Intent(NotificationActionReceiver.ACTION_DISMISS).apply {
                setClass(applicationContext, NotificationActionReceiver::class.java)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, entity.id.hashCode())
                putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, entity.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, entity.channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.alert_private_title))
            .setContentText(applicationContext.getString(R.string.alert_private_body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    entity.id.hashCode(),
                    Intent(
                        Intent.ACTION_VIEW,
                        intentUri
                    ).setClassName(applicationContext.packageName, "com.vaultbrain.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(android.R.drawable.ic_lock_idle_alarm, applicationContext.getString(R.string.alert_action_snooze), snoozeIntent)
            .addAction(android.R.drawable.ic_delete, applicationContext.getString(R.string.alert_action_dismiss), dismissIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(entity.id.hashCode(), notification)
        return true
    }

    companion object {
        /** Remove previews produced by versions that placed document content outside the vault. */
        fun clearLegacyPreviews(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.activeNotifications.filter { it.notification.channelId in setOf("CRITICAL", "REMINDER", "CALENDAR_EVENTS", "GMAIL_SYNC", "VAULT_REMINDERS") }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    private fun ensureChannel(channelId: String) {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (notificationManager?.getNotificationChannel(channelId) == null) {
            val (name, desc, importance) = when (channelId) {
                UnifiedAlertManager.CHANNEL_CALENDAR -> Triple(
                    applicationContext.getString(R.string.calendar_notification_channel),
                    applicationContext.getString(R.string.calendar_notification_channel_desc),
                    NotificationManager.IMPORTANCE_HIGH
                )
                UnifiedAlertManager.CHANNEL_GMAIL -> Triple(
                    applicationContext.getString(R.string.gmail_notification_channel),
                    applicationContext.getString(R.string.gmail_notification_channel_desc),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                else -> Triple(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT)
            }
            val channel = NotificationChannel(
                channelId,
                name,
                importance
            ).apply {
                description = desc
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
