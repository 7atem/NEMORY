package com.vaultbrain.feature.capture.collections

import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.common.model.CollectionSuggestionStatus
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.vectorstore.CollectionVectorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionSuggestionMatcher @Inject constructor(
    private val embeddingManager: CollectionEmbeddingManager,
    private val textEmbeddingModel: TextEmbeddingModel,
    private val collectionVectorStore: CollectionVectorStore,
    private val repository: VaultRepository
) {

    suspend fun match(item: VaultItem) = withContext(Dispatchers.Default) {
        if (!textEmbeddingModel.isAvailable()) return@withContext

        embeddingManager.ensureCollectionEmbeddingsCurrent()

        val itemSemanticText = buildItemSemanticText(item)
        if (itemSemanticText.isBlank()) return@withContext
        
        val itemVector = textEmbeddingModel.encode(itemSemanticText)
        
        // Find top candidates
        val neighbors = collectionVectorStore.nearestCollectionNeighbors(itemVector, topK = 5)
        if (neighbors.isEmpty()) return@withContext

        val existingMemberships = repository.observeCollectionsForItem(item.id).first().map { it.id }.toSet()
        val allSuggestions = repository.getAllSuggestionsForItem(item.id).associateBy { it.collectionId }

        for (neighbor in neighbors) {
            val collectionId = neighbor.collectionId
            if (collectionId in existingMemberships) continue
            
            val existingSuggestion = allSuggestions[collectionId]
            if (existingSuggestion != null && existingSuggestion.status != CollectionSuggestionStatus.SUGGESTED) {
                // Suppress if ACCEPTED or REJECTED
                continue
            }

            // Calculate confidence
            val similarity = collectionVectorStore.cosineSimilarity(itemVector, neighbor.embedding ?: continue)
            
            if (similarity >= THRESHOLD) {
                repository.upsertSuggestion(item.id, collectionId, similarity)
            }
        }
    }

    private fun buildItemSemanticText(item: VaultItem): String = buildString {
        append("Title: ").append(item.title).append("\n")
        if (!item.summary.isNullOrBlank()) append("Summary: ").append(item.summary).append("\n")
        if (!item.subtype.isNullOrBlank()) append("Subtype: ").append(item.subtype).append("\n")
        if (item.topics.isNotEmpty()) append("Topics: ").append(item.topics.joinToString(", ")).append("\n")
        if (item.entities.isNotEmpty()) append("Entities: ").append(item.entities.joinToString(", ")).append("\n")
        if (item.tags.isNotEmpty()) append("Tags: ").append(item.tags.joinToString(", ")).append("\n")
    }.trim().take(MAX_ITEM_SEMANTIC_CHARS)

    companion object {
        const val THRESHOLD = 0.75f // Conservative confidence threshold
        const val MAX_ITEM_SEMANTIC_CHARS = 1000
    }
}
