package com.vaultbrain.feature.capture.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaultbrain.shared.model.ProcessingState
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import com.vaultbrain.feature.capture.DeferredItemAnalyzer
import com.vaultbrain.feature.capture.VaultEmbeddingGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Drains the deferred analysis queue in the background.
 *
 * Items saved with `indexingState = PENDING` (shared placeholders and reviewed captures) are
 * claimed one at a time via [VaultRepository.claimNextIndexing]. Items whose extraction is still
 * pending are run through [DeferredItemAnalyzer]; every claimed item gets embeddings generated via
 * [VaultEmbeddingGenerator] and is then marked `indexingState = COMPLETE` via
 * [VaultRepository.completeIndexing]. Failures become `FAILED_RETRYABLE` and are retried by
 * WorkManager backoff (up to [MAX_RUN_ATTEMPTS] runs). Embedding failures are logged and skipped
 * so the item remains searchable via FTS.
 */
@HiltWorker
class DeferredAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: VaultRepository,
    private val analyzer: DeferredItemAnalyzer,
    private val alertManager: UnifiedAlertManager,
    private val embeddingGenerator: VaultEmbeddingGenerator,
    private val gemmaModelManager: GemmaModelManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        showStatusNotification()
        var failures = 0
        try {
            while (true) {
                val claimed = repository.claimNextIndexing(
                    staleAfterMillis = STALE_CLAIM_MS,
                    retryAfterMillis = RETRY_AFTER_MS
                ) ?: break
                try {
                    val analyzed = if (claimed.extractionState != ProcessingState.COMPLETE) {
                        analyzer.analyze(claimed)
                    } else {
                        claimed
                    }
                    if (analyzed !== claimed) {
                        repository.save(analyzed)
                    }
                    // Embedding failures must not fail analysis — the item stays searchable via FTS.
                    runCatching { embeddingGenerator.indexItem(analyzed) }
                        .onFailure { Log.w(TAG, "Embedding generation failed for ${analyzed.id}", it) }
                    repository.completeIndexing(analyzed.id, duplicateItemId = null, similarity = null)
                    if (analyzed.extractionState == ProcessingState.COMPLETE) {
                        alertManager.scheduleAlerts(analyzed)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    failures++
                    repository.transitionIndexingState(
                        claimed.id,
                        ProcessingState.RUNNING,
                        ProcessingState.FAILED_RETRYABLE
                    )
                }
            }
        } finally {
            dismissStatusNotification()
        }
        if (gemmaModelManager.status.value == OnDeviceModelStatus.READY) {
            LlmEnrichmentWorker.enqueue(applicationContext)
        }
        return if (failures > 0 && runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.success()
    }

    private suspend fun showStatusNotification() {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Background analysis",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shown while shared items are analyzed in the background"
                    setShowBadge(false)
                }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Organizing captured items…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        runCatching { setForegroundAsync(ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)) }
    }

    private fun dismissStatusNotification() {
        applicationContext.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val WORK_NAME = "deferred_analysis"

        private const val CHANNEL_ID = "deferred_analysis"
        private const val NOTIFICATION_ID = 4243
        private const val TAG = "DeferredAnalysis"
        private const val MAX_RUN_ATTEMPTS = 3
        private const val STALE_CLAIM_MS = 5 * 60 * 1000L
        private const val RETRY_AFTER_MS = 10 * 1000L
        private const val BACKOFF_SECONDS = 30L

        /** Enqueues (or appends to) the deferred analysis queue. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DeferredAnalysisWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
