package com.vaultbrain.core.vectorstore.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

/**
 * ObjectBox entity storing a vector fingerprint for a PersonalCollection.
 *
 * Privacy note: ObjectBox is not encrypted at rest (Android sandbox only), so this
 * entity deliberately stores NO user text — only the opaque collection id, the vector,
 * and a content hash used for change detection. Collection names and semantic source
 * text stay in the SQLCipher-encrypted Room database.
 */
@Entity
data class CollectionEmbedding(
    @Id
    var id: Long = 0,
    var collectionId: String = "",
    @HnswIndex(dimensions = 100)
    var embedding: FloatArray? = null, // unified 100-dim for HNSW
    var contentHash: String = "",
    var updatedAt: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CollectionEmbedding

        if (id != other.id) return false
        if (collectionId != other.collectionId) return false
        if (contentHash != other.contentHash) return false
        if (updatedAt != other.updatedAt) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + collectionId.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
