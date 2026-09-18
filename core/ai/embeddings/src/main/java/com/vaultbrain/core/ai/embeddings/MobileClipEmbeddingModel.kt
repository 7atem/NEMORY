package com.vaultbrain.core.ai.embeddings

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MobileCLIP model wrapper — placeholder.
 *
 * Disabled on purpose: the shared ObjectBox index holds 100-dim MiniLM text
 * embeddings, and mixing in non-semantic image vectors degrades retrieval.
 * A real, licensed semantic image encoder must ship with a compatible
 * separate index before this is enabled. Do not re-enable the pseudo
 * (color-layout) embedding below.
 */
@Singleton
class MobileClipEmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context
) : VisionEmbeddingModel {

    override fun isAvailable(): Boolean = false

    override suspend fun encode(image: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(image, 32, 32, true)
        val intValues = IntArray(32 * 32)
        try {
            scaled.getPixels(intValues, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        } finally {
            scaled.recycle()
        }
        
        val output = FloatArray(100)
        
        // Generate a deterministic 100-dim vector based on the image's color layout
        for (i in 0 until 100) {
            var sum = 0f
            for (j in 0 until 10) {
                val pixelIndex = (i * 10 + j) % intValues.size
                val pixelValue = intValues[pixelIndex]
                val r = (pixelValue shr 16 and 0xFF) / 255.0f
                val g = (pixelValue shr 8 and 0xFF) / 255.0f
                val b = (pixelValue and 0xFF) / 255.0f
                sum += (r + g + b) / 3f
            }
            output[i] = (sum / 10f) - 0.5f // Normalize somewhat around 0
        }
        
        // L2 normalize
        var magnitude = 0f
        for (v in output) magnitude += v * v
        magnitude = kotlin.math.sqrt(magnitude)
        if (magnitude > 0) {
            for (i in output.indices) output[i] /= magnitude
        }
        
        return output
    }
}
