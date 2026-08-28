package com.vaultbrain.feature.capture.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.vectorstore.VectorStore
import com.vaultbrain.feature.capture.VaultEmbeddingGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-time backfill that generates embeddings for vault items saved before embedding
 * generation existed. Items already present in the vector store are skipped cheaply via
 * [VectorStore.hasEmbeddingsForItem]. Enqueued with [ExistingWorkPolicy.KEEP] from the
 * application class; per-item failures are logged and skipped so one bad item cannot
 * block the rest.
 */
@HiltWorker
class EmbeddingBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: VaultRepository,
    private val vectorStore: VectorStore,
    private val embeddingGenerator: VaultEmbeddingGenerator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var failures = 0
        repository.getActive().forEach { item ->
            if (vectorStore.hasEmbeddingsForItem(item.id)) return@forEach
            runCatching { embeddingGenerator.indexItem(item) }
                .onFailure {
                    failures++
                    Log.w(TAG, "Backfill embedding failed for ${item.id}", it)
                }
        }
        return if (failures > 0 && runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "embedding_backfill"

        private const val TAG = "EmbeddingBackfill"
        private const val MAX_RUN_ATTEMPTS = 3

        /** Enqueues the backfill once; a no-op while an identical work is pending or running. */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<EmbeddingBackfillWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
