package com.vaultbrain.core.vectorstore.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

/**
 * ObjectBox entity storing a vector fingerprint for a [VaultItem].
 *
 * ObjectBox HNSW indexes the [embedding] property for approximate nearest-neighbor search.
 */
@Entity
 data class VaultEmbedding(
    @Id
    var id: Long = 0,
    var itemId: String = "",
    @HnswIndex(dimensions = 100)
    var embedding: FloatArray? = null, // unified 100-dim for HNSW
    var contentType: String = "", // "ocr_text", "image", "summary", "visual"
    var chunkIndex: Int = 0,
    var createdAt: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultEmbedding

        if (id != other.id) return false
        if (itemId != other.itemId) return false
        if (contentType != other.contentType) return false
        if (chunkIndex != other.chunkIndex) return false
        if (createdAt != other.createdAt) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + itemId.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + contentType.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
