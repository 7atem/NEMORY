package com.vaultbrain.core.ai.rag

import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.llm.CloudConsentDisclosure
import com.vaultbrain.core.common.model.VaultItem

/**
 * Result of a retrieval-augmented generation query.
 */
data class RagResponse(
    val answer: String? = null,
    val sources: List<VaultItem> = emptyList(),
    val confidence: Float = 0f,
    val evidence: RagEvidence? = null,
    val responseOrigin: AiResponseOrigin? = null,
    val cloudConsent: CloudConsentDisclosure? = null,
    val cloudFailure: RagCloudFailure? = null
)

enum class RagCloudFailure {
    NOT_CONFIGURED,
    GENERATION_FAILED
}

enum class RagEvidenceKind {
    TOTAL,
    EXPIRING,
    UPCOMING_TRAVEL,
    TRIP_BRIEFING,
    RECURRING_SPEND,
    MEDIA_BACKLOG,
    FILTERED_ITEMS
}

/** Authoritative Kotlin-computed result rendered before any explanatory prose. */
data class RagEvidence(
    val kind: RagEvidenceKind,
    val headline: String,
    val supportingFacts: List<String> = emptyList()
)
