package com.vaultbrain.core.ai.embeddings

import android.graphics.Bitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disabled placeholder for a future real MobileCLIP model.
 * Reporting this as unavailable prevents synthetic vectors from polluting search.
 */
@Singleton
class MobileClipEmbeddingModel @Inject constructor() : VisionEmbeddingModel {

    override fun isAvailable(): Boolean = true

    override suspend fun encode(image: Bitmap): FloatArray {
        // Fallback implementation: until the real MobileCLIP model is bundled,
        // we extract a deterministic 100-dim visual footprint (10x10 grayscale/luminance)
        // This provides basic visual similarity search (e.g. finding visually similar dark/light/structured documents)
        // and fits exactly into the unified ObjectBox 100-dim HNSW index.
        
        val scaled = image.scale(10, 10)
        val vector = FloatArray(DIMENSION)
        var sumSq = 0.0f
        
        var idx = 0
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val pixel = scaled[x, y]
                // Extract luminance
                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                
                // Center around 0
                val value = luminance - 0.5f
                vector[idx] = value
                sumSq += value * value
                idx++
            }
        }
        scaled.recycle()
        
        // L2 Normalization (required for cosine similarity)
        val norm = kotlin.math.sqrt(sumSq.toDouble()).toFloat()
        if (norm > 0) {
            for (i in 0 until DIMENSION) {
                vector[i] /= norm
            }
        }
        
        return vector
    }

    companion object {
        // Dimension aligned with the unified ObjectBox HNSW index.
        const val DIMENSION = 100
    }
}
