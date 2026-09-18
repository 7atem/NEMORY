package com.vaultbrain.feature.brain.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * RadarWorker scans new external records and proactively alerts the user of important events.
 */
@HiltWorker
class RadarWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val externalContextRepository: ExternalContextRepository,
    private val alertManager: UnifiedAlertManager,
    private val radarEngine: RadarEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (DecoySessionState.isDecoy.value) return Result.success()

        // Fetch recent records that haven't been resolved 
        // In this simple prototype, we'll fetch the latest records and evaluate them.
        try {
            // we will query from Gmail
            val recentRecords = externalContextRepository.getRecordsForConnection("gmail", "primary") // Simplified account fetching
            // A more robust implementation would use a "processed" flag, but for now we look at the last 10 records as an example
            
            recentRecords.take(10).forEach { record ->
                val insight = radarEngine.evaluate(record)
                if (insight != null) {
                    // In a production app, we would verify we haven't already notified about this entity.
                    // For demo purposes, we schedule immediately.
                    alertManager.scheduleForExternal(
                        targetId = record.externalId,
                        triggerAt = insight.triggerAt,
                        title = insight.title,
                        body = insight.body,
                        channelId = "CRITICAL"
                    )
                }
            }
        } catch (e: Exception) {
            return Result.failure()
        }

        return Result.success()
    }

    companion object {
        fun enqueuePeriodic(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<RadarWorker>(4, java.util.concurrent.TimeUnit.HOURS)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "radar_proactive_worker",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
