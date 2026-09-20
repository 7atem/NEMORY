package com.vaultbrain.shared.model

/** A normalized, evidence-bearing label emitted by an on-device visual model. */
data class ScoredLabel(
    val label: String,
    val confidence: Float,
    val source: String = SOURCE_ML_KIT
) {
    init {
        require(label.isNotBlank()) { "Visual label must not be blank" }
        require(confidence in 0f..1f) { "Visual label confidence must be between 0 and 1" }
    }

    companion object {
        const val SOURCE_ML_KIT = "ml_kit"
        const val SOURCE_ZERO_SHOT = "zero_shot"
        const val SOURCE_NEURAL_CLASSIFIER = "neural_classifier"
    }
}
