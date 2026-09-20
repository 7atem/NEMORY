package com.vaultbrain.shared.model

import kotlinx.serialization.Serializable

/**
 * Domain model for a single item stored in the vault.
 *
 * This is the canonical object used across modules. The database layer maps
 * it to and from [VaultItemEntity].
 */
@Serializable
data class VaultItem(
    val id: String,
    val title: String,
    val summary: String? = null,
    val rawOcrText: String? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val createdAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),
    val updatedAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),
    val capturedImageUri: String? = null,
    val parsedMetadata: Map<String, String> = emptyMap(),
    val lensTags: Set<String> = emptySet(),
    /**
     * The lens that owns this item for dashboards and counts, or null for "General"
     * items (e.g. saved via "Just save"). Multi [lensTags] still drive search/browse.
     * Null on legacy rows is backfilled from the first lens tag on read.
     */
    val primaryLensId: String? = null,
    val expiryDate: Long? = null,
    val secondaryAlertDate: Long? = null,
    val recurringRule: String? = null,
    val captureLatitude: Double? = null,
    val captureLongitude: Double? = null,
    val locationName: String? = null,
    val aiConfidence: Float = 0f,
    val aiClassification: Classification? = null,
    val userClassificationOverride: Classification? = null,
    val userEditedAt: Long? = null,
    val extractionState: ProcessingState = ProcessingState.COMPLETE,
    val enrichmentState: EnrichmentState = EnrichmentState.PENDING,
    val indexingState: ProcessingState = ProcessingState.PENDING,
    val enrichmentAttemptCount: Int = 0,
    val enrichmentClaimedAt: Long? = null,
    val enrichmentLastAttemptAt: Long? = null,
    val enrichmentErrorCode: String? = null,
    val needsReview: Boolean = false,
    val possibleDuplicateOfItemId: String? = null,
    val duplicateSimilarity: Float? = null,
    val dominantColors: List<String> = emptyList(),
    val detectedObjects: List<String> = emptyList(),
    /** Open semantic enrichment fields. They are deliberately not lens or classification enums. */
    val subtype: String? = null,
    val topics: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val aiScratchpad: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isStealth: Boolean = false,
    val userNotes: String? = null,
    val customFields: Map<String, String> = emptyMap(),
    val targetPrice: Double? = null,
    val affiliateUrl: String? = null,
    val driveBackupId: String? = null,
    val lastSyncAt: Long? = null,
    val requiredLensTier: Tier = Tier.FREE,
    val experienceId: String? = null
) {
    /** A user's explicit correction always wins over machine classification. */
    val effectiveClassification: Classification?
        get() = userClassificationOverride ?: aiClassification

    val processingStatus: ItemProcessingStatus
        get() = when {
            extractionState == ProcessingState.PENDING -> ItemProcessingStatus.SAVED
            indexingState == ProcessingState.PENDING -> ItemProcessingStatus.ORGANIZING
            extractionState == ProcessingState.FAILED_RETRYABLE || indexingState == ProcessingState.FAILED_RETRYABLE -> ItemProcessingStatus.NEEDS_REVIEW
            else -> ItemProcessingStatus.READY
        }
}

