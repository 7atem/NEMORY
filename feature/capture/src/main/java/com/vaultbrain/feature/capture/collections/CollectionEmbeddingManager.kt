package com.vaultbrain.feature.capture.collections

import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.vectorstore.CollectionVectorStore
import com.vaultbrain.core.vectorstore.entity.CollectionEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionEmbeddingManager @Inject constructor(
    private val repository: VaultRepository,
    private val textEmbeddingModel: TextEmbeddingModel,
    private val collectionVectorStore: CollectionVectorStore
) {

    suspend fun ensureCollectionEmbeddingsCurrent() = withContext(Dispatchers.Default) {
        if (!textEmbeddingModel.isAvailable()) return@withContext

        val activeCollections = repository.getActiveCollections()
        val existingEmbeddings = collectionVectorStore.getAllCollectionEmbeddings().associateBy { it.collectionId }
        val activeIds = activeCollections.map { it.id }.toSet()

        // Remove embeddings for deleted/archived collections
        existingEmbeddings.keys.forEach { id ->
            if (id !in activeIds) {
                collectionVectorStore.deleteCollectionEmbedding(id)
            }
        }

        // Encode/Update active collections
        activeCollections.forEach { collection ->
            val members = repository.getItemsForCollection(collection.id).take(MAX_REPRESENTATIVE_ITEMS)
            val semanticText = buildSemanticText(collection.name, members)
            val contentHash = hash(semanticText)

            val existing = existingEmbeddings[collection.id]
            if (existing == null || existing.contentHash != contentHash) {
                val embeddingVector = textEmbeddingModel.encode(semanticText)
                collectionVectorStore.putCollectionEmbedding(
                    CollectionEmbedding(
                        id = existing?.id ?: 0L,
                        collectionId = collection.id,
                        embedding = embeddingVector,
                        contentHash = contentHash,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun buildSemanticText(collectionName: String, members: List<VaultItem>): String = buildString {
        append("Collection: ").append(collectionName).append("\n")
        
        val allSubtypes = members.mapNotNull { it.subtype }.filter { it.isNotBlank() }.toSet()
        val allTopics = members.flatMap { it.topics }.filter { it.isNotBlank() }.toSet()
        val allEntities = members.flatMap { it.entities }.filter { it.isNotBlank() }.toSet()
        val allTags = members.flatMap { it.tags }.filter { it.isNotBlank() }.toSet()

        if (allSubtypes.isNotEmpty()) append("Subtypes: ").append(allSubtypes.joinToString(", ")).append("\n")
        if (allTopics.isNotEmpty()) append("Topics: ").append(allTopics.joinToString(", ")).append("\n")
        if (allEntities.isNotEmpty()) append("Entities: ").append(allEntities.joinToString(", ")).append("\n")
        if (allTags.isNotEmpty()) append("Tags: ").append(allTags.joinToString(", ")).append("\n")

        members.forEach { item ->
            append("- ").append(item.title)
            if (!item.summary.isNullOrBlank()) {
                append(": ").append(item.summary)
            }
            append("\n")
        }
    }.trim().take(MAX_SEMANTIC_CHARS)

    private fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(text.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_REPRESENTATIVE_ITEMS = 20
        const val MAX_SEMANTIC_CHARS = 2000
    }
}
