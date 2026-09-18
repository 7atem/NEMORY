package com.vaultbrain.core.ai.embeddings

import android.graphics.Bitmap

/**
 * Encodes an image into a dense vector suitable for visual similarity search.
 */
interface VisionEmbeddingModel {

    /**
     * True when the underlying model is loaded and ready to encode.
     */
    fun isAvailable(): Boolean

    /**
     * Produces an embedding vector for the given [image].
     */
    suspend fun encode(image: Bitmap): FloatArray
}
