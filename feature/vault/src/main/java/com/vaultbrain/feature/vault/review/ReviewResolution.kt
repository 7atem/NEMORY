package com.vaultbrain.feature.vault.review

import com.vaultbrain.shared.model.EnrichmentState
import com.vaultbrain.shared.model.VaultItem

enum class ReviewReason { POSSIBLE_DUPLICATE, CATEGORY, ENRICHMENT_FAILED, DETAILS }

object ReviewResolution {
    fun reason(item: VaultItem): ReviewReason = when {
        item.possibleDuplicateOfItemId != null -> ReviewReason.POSSIBLE_DUPLICATE
        item.enrichmentState == EnrichmentState.FAILED_FINAL -> ReviewReason.ENRICHMENT_FAILED
        item.userClassificationOverride != null || item.aiClassification != null -> ReviewReason.CATEGORY
        else -> ReviewReason.DETAILS
    }

    fun accept(item: VaultItem, now: Long = System.currentTimeMillis()): VaultItem = item.copy(
        userClassificationOverride = item.userClassificationOverride ?: item.aiClassification,
        enrichmentState = if (item.enrichmentState == EnrichmentState.FAILED_FINAL) {
            EnrichmentState.USER_ACCEPTED
        } else {
            item.enrichmentState
        },
        needsReview = false,
        possibleDuplicateOfItemId = null,
        duplicateSimilarity = null,
        userEditedAt = now,
        updatedAt = now
    )
}
