package com.vaultbrain.core.ai.rag

import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.llm.CloudConsentDisclosure
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.integrations.model.ExternalRecord

/**
 * Result of a retrieval-augmented generation query.
 */
data class RagResponse(
    val answer: String? = null,
    val status: String? = null,
    val sources: List<VaultItem> = emptyList(),
    val externalSources: List<ExternalRecord> = emptyList(),
    val proposals: List<LocalAgent.ActionProposal> = emptyList(),
    val confidence: Float = 0f,
    val evidence: RagEvidence? = null,
    val sourcedFacts: List<SourcedFact> = emptyList(),
    val deterministicCalculations: List<String> = emptyList(),
    val modelSuggestions: List<String> = emptyList(),
    val responseOrigin: AiResponseOrigin? = null,
    val cloudConsent: CloudConsentDisclosure? = null,
    val cloudFailure: RagCloudFailure? = null
)

/** A factual assertion verified in code against a specific retrieved record. */
data class SourcedFact(
    val sourceIndex: Int,
    val sourceId: String,
    val factText: String,
    val dates: List<String> = emptyList(),
    val amounts: List<String> = emptyList()
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
    FILTERED_ITEMS,
    MISSING_DOCUMENTS,
    REPLACEMENT_CHAIN
}

/** Authoritative Kotlin-computed result rendered before any explanatory prose. */
data class RagEvidence(
    val kind: RagEvidenceKind,
    val headline: String,
    val supportingFacts: List<String> = emptyList()
)
