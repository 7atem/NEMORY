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
import androidx.work.workDataOf
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.feature.capture.CaptureEnrichmentGenerator
import com.vaultbrain.feature.capture.R
import com.vaultbrain.feature.capture.VaultEmbeddingGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies the active on-device model to all ready items, including historical imports.
 *
 * The worker first proves the model can be loaded, then drains database claims one at a time.
 * It runs as a visible long-running worker, publishes item progress, refreshes embeddings, and
 * preserves concurrent edits through the repository claim contract.
 */
@HiltWorker
class LlmEnrichmentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: VaultRepository,
    private val llmClient: LlmClient,
    private val enrichmentGenerator: CaptureEnrichmentGenerator,
    private val embeddingGenerator: VaultEmbeddingGenerator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo(processed = 0, total = 0, activating = true))
        if (!llmClient.warmup()) {
            postFailureNotification()
            return retryOrFail()
        }

        val total = repository.countEnrichmentCandidates(MAX_ITEM_ATTEMPTS)
        var processed = 0
        var improved = 0
        var failures = 0
        publishProgress(processed, total)

        while (true) {
            val claimed = repository.claimNextEnrichment(
                staleAfterMillis = STALE_CLAIM_MS,
                retryAfterMillis = RETRY_AFTER_MS,
                maxAttempts = MAX_ITEM_ATTEMPTS
            ) ?: break

            try {
                val evidence = CapturePromptEvidence(
                    ocrText = claimed.rawOcrText.orEmpty(),
                    labels = claimed.detectedObjects,
                    barcodes = claimed.parsedMetadata["barcodes"]
                        ?.split('|')
                        ?.map(String::trim)
                        ?.filter(String::isNotBlank)
                        .orEmpty(),
                    classificationHint = claimed.aiClassification,
                    userClassificationOverride = claimed.userClassificationOverride,
                    systemFacetHints = claimed.lensTags,
                    existingMetadata = claimed.parsedMetadata
                )
                if (evidence.ocrText.isBlank() && evidence.labels.isEmpty() && evidence.barcodes.isEmpty()) {
                    repository.transitionEnrichmentState(
                        claimed.id,
                        setOf(EnrichmentState.RUNNING),
                        EnrichmentState.SKIPPED_UNSUPPORTED
                    )
                    processed++
                    publishProgress(processed, total)
                    continue
                }

                val result = withTimeoutOrNull(ENRICHMENT_TIMEOUT_MS) {
                    enrichmentGenerator.generate(evidence = evidence, image = null)
                }
                if (result == null) {
                    if (!llmClient.isAvailable()) {
                        repository.releaseEnrichmentClaim(claimed.id)
                        postFailureNotification()
                        return retryOrFail()
                    }
                    failures++
                    repository.failClaimedEnrichment(
                        id = claimed.id,
                        errorCode = ERROR_GENERATION_FAILED,
                        maxAttempts = MAX_ITEM_ATTEMPTS
                    )
                    processed++
                    publishProgress(processed, total)
                    continue
                }

                val enrichedWhileClaimed = result.applyTo(
                    item = claimed,
                    replaceHeuristicContent = true
                ).copy(
                    enrichmentState = EnrichmentState.RUNNING,
                    enrichmentClaimedAt = claimed.enrichmentClaimedAt
                )
                if (repository.replaceWhileEnrichmentClaimed(enrichedWhileClaimed)) {
                    repository.transitionEnrichmentState(
                        claimed.id,
                        setOf(EnrichmentState.RUNNING),
                        EnrichmentState.COMPLETE
                    )
                    improved++
                    runCatching { embeddingGenerator.indexItem(enrichedWhileClaimed) }
                        .onFailure { Log.w(TAG, "Embedding refresh failed for ${claimed.id}", it) }
                }
            } catch (cancelled: CancellationException) {
                repository.releaseEnrichmentClaim(claimed.id)
                throw cancelled
            } catch (error: Exception) {
                failures++
                Log.w(TAG, "Local AI enrichment failed for ${claimed.id}", error)
                repository.failClaimedEnrichment(
                    id = claimed.id,
                    errorCode = ERROR_ENRICHMENT_EXCEPTION,
                    maxAttempts = MAX_ITEM_ATTEMPTS
                )
            }
            processed++
            publishProgress(processed, total)
        }

        val remaining = repository.countEnrichmentCandidates(MAX_ITEM_ATTEMPTS)
        return if ((failures > 0 || remaining > 0) && runAttemptCount < MAX_WORK_ATTEMPTS - 1) {
            Result.retry()
        } else {
            postCompletionNotification(improved)
            Result.success(
                workDataOf(
                    KEY_PROCESSED to processed,
                    KEY_TOTAL to total,
                    KEY_IMPROVED to improved
                )
            )
        }
    }

    private suspend fun publishProgress(processed: Int, total: Int) {
        setProgress(workDataOf(KEY_PROCESSED to processed, KEY_TOTAL to total))
        setForeground(createForegroundInfo(processed, total, activating = false))
    }

    private fun createForegroundInfo(processed: Int, total: Int, activating: Boolean): ForegroundInfo {
        val notificationManager = notificationManager()
        ensureChannel(notificationManager)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(
                applicationContext.getString(
                    if (activating) R.string.local_ai_activating else R.string.local_ai_processing
                )
            )
            .setContentText(
                if (total > 0) applicationContext.getString(
                    R.string.local_ai_processing_progress,
                    processed,
                    total
                ) else applicationContext.getString(R.string.local_ai_preparing_vault)
            )
            .setProgress(total.coerceAtLeast(1), processed.coerceAtMost(total), total <= 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun postCompletionNotification(improved: Int) {
        val manager = notificationManager()
        ensureChannel(manager)
        manager?.notify(
            COMPLETION_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(applicationContext.getString(R.string.local_ai_active))
                .setContentText(
                    if (improved == 0) {
                        applicationContext.getString(R.string.local_ai_no_items_to_improve)
                    } else {
                        applicationContext.resources.getQuantityString(
                            R.plurals.local_ai_items_improved,
                            improved,
                            improved
                        )
                    }
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun postFailureNotification() {
        val manager = notificationManager()
        ensureChannel(manager)
        manager?.notify(
            COMPLETION_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(applicationContext.getString(R.string.local_ai_activation_failed))
                .setContentText(applicationContext.getString(R.string.local_ai_activation_failed_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notificationManager(): NotificationManager? =
        applicationContext.getSystemService(NotificationManager::class.java)

    private fun ensureChannel(manager: NotificationManager?) {
        if (manager?.getNotificationChannel(CHANNEL_ID) != null) return
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.local_ai_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.local_ai_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_WORK_ATTEMPTS - 1) Result.retry() else Result.failure()

    companion object {
        const val WORK_NAME = "llm_enrichment"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_IMPROVED = "improved"

        fun enqueue(context: Context, replaceCurrent: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<LlmEnrichmentWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (replaceCurrent) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }

        private const val CHANNEL_ID = "local_ai_enrichment"
        private const val FOREGROUND_NOTIFICATION_ID = 4245
        private const val COMPLETION_NOTIFICATION_ID = 4246
        private const val TAG = "LlmEnrichmentWorker"
        private const val ERROR_GENERATION_FAILED = "GENERATION_FAILED"
        private const val ERROR_ENRICHMENT_EXCEPTION = "ENRICHMENT_EXCEPTION"
        private const val MAX_ITEM_ATTEMPTS = 3
        private const val MAX_WORK_ATTEMPTS = 3
        private const val STALE_CLAIM_MS = 30L * 60 * 1000
        private const val RETRY_AFTER_MS = 15L * 60 * 1000
        private const val ENRICHMENT_TIMEOUT_MS = 90_000L
    }
}
