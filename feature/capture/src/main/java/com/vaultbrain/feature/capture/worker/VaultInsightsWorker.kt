package com.vaultbrain.feature.capture.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.vectorstore.VectorStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.UUID

@HiltWorker
class VaultInsightsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: VaultRepository,
    private val llmClient: LlmClient,
    private val embeddingModel: TextEmbeddingModel,
    private val vectorStore: VectorStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!llmClient.isAvailable() || !embeddingModel.isAvailable()) {
            return Result.retry()
        }
        
        val recentItems = repository.getActive().sortedByDescending { it.createdAt }.take(20)
        if (recentItems.isEmpty()) return Result.success()

        for (item in recentItems) {
            val summaryText = item.summary ?: item.title ?: continue
            val queryVector = embeddingModel.encode(summaryText)
            val neighbors = vectorStore.nearestNeighbors(queryVector, 5)
            
            for (neighbor in neighbors) {
                if (neighbor.itemId == item.id) continue
                val targetItem = repository.getById(neighbor.itemId) ?: continue
                
                // If they already share a dynamic tag, skip
                val sharedTags = item.lensTags.intersect(targetItem.lensTags.toSet())
                if (sharedTags.any { it.startsWith("auto_") }) continue

                val prompt = """
                    Do these two items belong to the same specific event, trip, or project?
                    Item 1: ${item.title} - ${item.summary}
                    Item 2: ${targetItem.title} - ${targetItem.summary}
                    
                    Answer only in JSON format: {"related": true, "tag": "auto_trip_name"}
                    If false, answer: {"related": false}
                """.trimIndent()
                
                val response = withTimeoutOrNull(30_000L) { llmClient.generate(prompt) }
                if (response != null && response.contains("\"related\": true", ignoreCase = true)) {
                    val tagMatch = Regex("\"tag\":\\s*\"(.*?)\"").find(response)
                    if (tagMatch != null) {
                        val newTag = tagMatch.groupValues[1].lowercase().replace(" ", "_").take(30)
                        if (newTag.isNotBlank() && newTag.startsWith("auto_")) {
                            repository.save(item.copy(lensTags = item.lensTags + newTag))
                            repository.save(targetItem.copy(lensTags = targetItem.lensTags + newTag))
                        }
                    }
                }
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "vault_insights"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<VaultInsightsWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
