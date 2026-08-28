package com.vaultbrain.core.ai.heuristics

import com.vaultbrain.core.common.model.Classification

/**
 * Structured data extracted from raw text using regex-based heuristics.
 *
 * The result contains both raw extracted fields and derived convenience fields used
 * by the capture pipeline (title, summary, metadata, lens tags, dates).
 */
data class HeuristicExtractionResult(
    val dates: List<String> = emptyList(),
    val amounts: List<Double> = emptyList(),
    val currencies: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val iban: List<String> = emptyList(),
    val inferredClassification: Classification = Classification.UNKNOWN,
    val confidence: Float = 0f,

    // Derived convenience fields for the capture pipeline
    val title: String? = null,
    val summary: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val lensTags: Set<String> = emptySet(),
    val expiryDate: Long? = null,
    val alertDate: Long? = null
)
