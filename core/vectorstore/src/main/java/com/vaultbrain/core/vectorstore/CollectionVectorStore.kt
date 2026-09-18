package com.vaultbrain.core.vectorstore

import com.vaultbrain.core.vectorstore.entity.CollectionEmbedding

/**
 * Abstraction over the collection-embedding portion of the vector store so matchers
 * can be unit-tested without ObjectBox.
 */
interface CollectionVectorStore {
    /** Replaces the embedding row for [collectionId] (at most one row per collection). */
    fun putCollectionEmbedding(embedding: CollectionEmbedding)

    fun deleteCollectionEmbedding(collectionId: String)

    fun getCollectionEmbedding(collectionId: String): CollectionEmbedding?

    fun getAllCollectionEmbeddings(): List<CollectionEmbedding>

    /** Approximate nearest collections to [queryVector]. */
    fun nearestCollectionNeighbors(queryVector: FloatArray, topK: Int): List<CollectionEmbedding>

    /** Exact cosine similarity for reranking candidate pairs. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float
}
