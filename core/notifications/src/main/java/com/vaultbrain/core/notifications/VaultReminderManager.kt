package com.vaultbrain.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import com.vaultbrain.core.common.model.VaultReminder
import com.vaultbrain.core.common.model.VaultReminderStatus
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.dao.ExternalRecordDao
import com.vaultbrain.core.database.dao.VaultReminderDao
import com.vaultbrain.core.database.entity.VaultReminderEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class VaultReminderManager @Inject constructor(
    private val reminderDao: VaultReminderDao,
    private val workManager: WorkManager
) {
    fun observeActive(): Flow<List<VaultReminder>> =
        combine(reminderDao.observeActive(), DecoySessionState.isDecoy) { reminders, isDecoy ->
            if (isDecoy) emptyList() else reminders.map(VaultReminderEntity::toDomain)
        }

    suspend fun get(id: String): VaultReminder? =
        if (DecoySessionState.isDecoy.value) null else reminderDao.get(id)?.toDomain()

    suspend fun create(reminder: VaultReminder): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        require(reminder.title.isNotBlank()) { "Reminder title cannot be blank" }
        val now = System.currentTimeMillis()
        if (reminder.dueAt <= now) return false
        if (reminder.vaultItemId?.let {
                reminderDao.getActiveForVaultItemAt(it, reminder.dueAt)
            } != null
        ) return true
        val scheduled = reminder.copy(
            title = reminder.title.trim(),
            status = VaultReminderStatus.SCHEDULED,
            updatedAt = now
        )
        upsert(scheduled)
        enqueue(scheduled)
        return true
    }

    suspend fun editDueTime(id: String, dueAt: Long): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val current = reminderDao.get(id) ?: return false
        val updated = current.copy(
            dueAt = dueAt,
            status = VaultReminderStatus.SCHEDULED,
            updatedAt = System.currentTimeMillis()
        )
        reminderDao.update(updated)
        enqueue(updated.toDomain())
        return true
    }

    suspend fun snooze(id: String, until: Long): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val current = reminderDao.get(id) ?: return false
        val updated = current.copy(
            dueAt = until,
            status = VaultReminderStatus.SNOOZED,
            updatedAt = System.currentTimeMillis()
        )
        reminderDao.update(updated)
        enqueue(updated.toDomain())
        return true
    }

    suspend fun complete(id: String): Boolean = setTerminalStatus(id, VaultReminderStatus.COMPLETED)

    suspend fun dismiss(id: String): Boolean = setTerminalStatus(id, VaultReminderStatus.DISMISSED)

    private suspend fun setTerminalStatus(id: String, status: VaultReminderStatus): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val changed = reminderDao.setStatus(id, status, System.currentTimeMillis()) > 0
        if (changed) workManager.cancelUniqueWork(workName(id))
        return changed
    }

    private suspend fun upsert(reminder: VaultReminder) {
        val entity = reminder.toEntity()
        if (reminderDao.insert(entity) == -1L) reminderDao.update(entity)
    }

    private fun enqueue(reminder: VaultReminder) {
        val delay = max(0L, reminder.dueAt - System.currentTimeMillis())
        val work = OneTimeWorkRequestBuilder<VaultReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_REMINDER_ID, reminder.id).build())
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName(reminder.id), ExistingWorkPolicy.REPLACE, work)
    }

    companion object {
        const val KEY_REMINDER_ID = "vault_reminder_id"
        const val WORK_TAG = "vault_reminder"
        fun workName(id: String) = "$WORK_TAG:$id"
    }
}

@HiltWorker
class VaultReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderDao: VaultReminderDao,
    private val externalRecordDao: ExternalRecordDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(VaultReminderManager.KEY_REMINDER_ID) ?: return Result.failure()
        if (DecoySessionState.isDecoy.value) return Result.retry()
        val reminder = reminderDao.get(id) ?: return Result.success()
        if (reminder.status !in setOf(VaultReminderStatus.SCHEDULED, VaultReminderStatus.SNOOZED)) {
            return Result.success()
        }
        if (reminder.dueAt > System.currentTimeMillis()) return Result.retry()
        return if (postNotification(reminder)) Result.success() else Result.retry()
    }

    @SuppressLint("MissingPermission")
    private suspend fun postNotification(reminder: VaultReminderEntity): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(applicationContext.getString(R.string.alert_private_title))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentText(applicationContext.getString(R.string.vault_reminder_due))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(reminder))
            .addAction(0, applicationContext.getString(R.string.vault_reminder_snooze), actionIntent(reminder.id, ACTION_SNOOZE))
            .addAction(0, applicationContext.getString(R.string.vault_reminder_complete), actionIntent(reminder.id, ACTION_COMPLETE))
            .setDeleteIntent(actionIntent(reminder.id, ACTION_DISMISS))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(reminder.id.hashCode(), notification)
        return true
    }

    private suspend fun contentIntent(reminder: VaultReminderEntity): PendingIntent {
        val externalConnectorId = reminder.externalConnectorId
        val externalAccountId = reminder.externalAccountId
        val externalRecordId = reminder.externalRecordId
        val externalUri = if (
            externalConnectorId != null &&
            externalAccountId != null &&
            externalRecordId != null
        ) {
            externalRecordDao.get(
                externalConnectorId,
                externalAccountId,
                externalRecordId
            )?.deepLinkUri
        } else null
        val intent = when {
            externalUri != null -> Intent(Intent.ACTION_VIEW, externalUri.toUri())
            reminder.vaultItemId != null -> Intent(
                Intent.ACTION_VIEW,
                "nemory://item/${android.net.Uri.encode(reminder.vaultItemId)}".toUri()
            ).setPackage(applicationContext.packageName)
            reminder.personalCollectionId != null -> Intent(
                Intent.ACTION_VIEW,
                "nemory://collection/${android.net.Uri.encode(reminder.personalCollectionId)}".toUri()
            ).setPackage(applicationContext.packageName)
            else -> Intent().setClassName(applicationContext.packageName, "com.vaultbrain.app.MainActivity")
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            applicationContext,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(id: String, action: String): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        31 * id.hashCode() + action.hashCode(),
        Intent(applicationContext, VaultReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(VaultReminderManager.KEY_REMINDER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.vault_reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "VAULT_REMINDERS"
        const val ACTION_SNOOZE = "com.nemory.app.action.SNOOZE_VAULT_REMINDER"
        const val ACTION_COMPLETE = "com.nemory.app.action.COMPLETE_VAULT_REMINDER"
        const val ACTION_DISMISS = "com.nemory.app.action.DISMISS_VAULT_REMINDER"
    }
}

@AndroidEntryPoint
class VaultReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var manager: VaultReminderManager

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(VaultReminderManager.KEY_REMINDER_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    VaultReminderWorker.ACTION_SNOOZE -> manager.snooze(
                        id,
                        System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15)
                    )
                    VaultReminderWorker.ACTION_COMPLETE -> manager.complete(id)
                    VaultReminderWorker.ACTION_DISMISS -> manager.dismiss(id)
                }
                NotificationManagerCompat.from(context).cancel(id.hashCode())
            } finally {
                pending.finish()
            }
        }
    }
}

private fun VaultReminderEntity.toDomain() = VaultReminder(
    id = id,
    title = title,
    dueAt = dueAt,
    status = status,
    vaultItemId = vaultItemId,
    externalConnectorId = externalConnectorId,
    externalAccountId = externalAccountId,
    externalRecordId = externalRecordId,
    personalCollectionId = personalCollectionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun VaultReminder.toEntity() = VaultReminderEntity(
    id = id,
    title = title,
    dueAt = dueAt,
    status = status,
    vaultItemId = vaultItemId,
    externalConnectorId = externalConnectorId,
    externalAccountId = externalAccountId,
    externalRecordId = externalRecordId,
    personalCollectionId = personalCollectionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
