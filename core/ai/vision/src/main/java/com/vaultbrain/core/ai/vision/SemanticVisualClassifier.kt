package com.vaultbrain.core.ai.vision

import android.graphics.Bitmap
import com.vaultbrain.shared.model.ScoredLabel

/**
 * Boundary for a genuine zero-shot/semantic image model (for example MobileCLIP).
 * Implementations must return calibrated labels from their own text/image embedding
 * space and must not write vectors into Nemory's 100-dimensional text index.
 */
interface SemanticVisualClassifier {
    fun isAvailable(): Boolean
    suspend fun classify(bitmap: Bitmap): List<ScoredLabel>
}

/** Safe release default until a licensed, evaluated model asset is supplied. */
class UnavailableSemanticVisualClassifier : SemanticVisualClassifier {
    override fun isAvailable() = false
    override suspend fun classify(bitmap: Bitmap) = emptyList<ScoredLabel>()
}
