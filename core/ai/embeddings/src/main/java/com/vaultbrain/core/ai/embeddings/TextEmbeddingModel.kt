package com.vaultbrain.core.ai.embeddings

/**
 * Encodes text into a dense vector suitable for semantic search.
 */
interface TextEmbeddingModel {

    /**
     * True when the underlying model is loaded and ready to encode.
     */
    fun isAvailable(): Boolean

    /**
     * Produces an embedding vector for the given [text].
     *
     * Implementations must normalize the returned vector if cosine similarity is used
     * downstream (as in [com.vaultbrain.core.vectorstore.VectorStore]).
     */
    suspend fun encode(text: String): FloatArray
}
