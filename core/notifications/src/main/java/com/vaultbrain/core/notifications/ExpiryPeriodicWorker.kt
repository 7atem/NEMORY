package com.vaultbrain.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaultbrain.core.database.repository.VaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Daily background worker that scans the vault for items expiring within 30 days,
 * ensuring all alerts are properly queued in [UnifiedAlertManager].
 */
@HiltWorker
class ExpiryPeriodicWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vaultRepository: VaultRepository,
    private val alertManager: UnifiedAlertManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val now = System.currentTimeMillis()
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
            val limit = now + thirtyDaysMs

            val expiringItems = vaultRepository.observeExpiring(limit).first()

            for (item in expiringItems) {
                alertManager.scheduleAlerts(item)
            }
            Result.success()
        }.getOrDefault(Result.retry())
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "VaultBrainExpiryDailySync"

        /**
         * Enqueues a repeating 24-hour periodic work request to scan for expiring items.
         */
        fun enqueueDailySync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ExpiryPeriodicWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
