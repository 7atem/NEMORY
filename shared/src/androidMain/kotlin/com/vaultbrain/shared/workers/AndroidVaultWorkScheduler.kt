package com.vaultbrain.shared.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class AndroidVaultWorkScheduler(
    private val context: Context
) : VaultWorkScheduler {

    override fun scheduleOneOffJob(jobType: VaultJobType) {
        val request = OneTimeWorkRequestBuilder<CoroutineWorkerPlaceholder>()
            .addTag(jobType.name)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            jobType.name,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun schedulePeriodicJob(jobType: VaultJobType, repeatIntervalHours: Long) {
        val request = PeriodicWorkRequestBuilder<CoroutineWorkerPlaceholder>(
            repeatIntervalHours, TimeUnit.HOURS
        )
            .addTag(jobType.name)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            jobType.name,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    override fun cancelJob(jobType: VaultJobType) {
        WorkManager.getInstance(context).cancelAllWorkByTag(jobType.name)
    }
}

/**
 * Placeholder for the actual worker implementation.
 * In a real app, the specific Worker class would be provided or we'd map VaultJobType 
 * to actual worker classes via a factory.
 */
class CoroutineWorkerPlaceholder(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
