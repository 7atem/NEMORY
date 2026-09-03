package com.vaultbrain.core.ai.vision

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.ScoredLabel

/**
 * Lightweight visual analysis result for a captured image.
 *
 * Contains color, object, barcode, and document-type classification signals from
 * on-device vision models.
 */
data class VisionResult(
    val dominantColors: List<String> = emptyList(),
    val detectedObjects: List<String> = emptyList(),
    /** Labels with their original model confidence. Prefer this for evidence fusion. */
    val scoredLabels: List<ScoredLabel> = emptyList(),
    val barcodes: List<String> = emptyList(),
    /** Classification predicted by the on-device document classifier, or null if unavailable. */
    val documentClassification: Classification? = null,
    /** Confidence of the document classifier prediction, in [0, 1]. */
    val documentClassificationConfidence: Float = 0f
)
