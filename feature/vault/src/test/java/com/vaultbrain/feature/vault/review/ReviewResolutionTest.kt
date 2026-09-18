package com.vaultbrain.feature.vault.review

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.common.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewResolutionTest {
    @Test
    fun `manual category remains authoritative when accepting review`() {
        val accepted = ReviewResolution.accept(
            item().copy(
                aiClassification = Classification.INVOICE,
                userClassificationOverride = Classification.RECEIPT
            ),
            now = 500L
        )

        assertEquals(Classification.RECEIPT, accepted.userClassificationOverride)
        assertFalse(accepted.needsReview)
        assertEquals(500L, accepted.userEditedAt)
    }

    @Test
    fun `keeping a possible duplicate clears only the suggestion`() {
        val accepted = ReviewResolution.accept(
            item().copy(possibleDuplicateOfItemId = "old", duplicateSimilarity = 0.99f),
            now = 500L
        )

        assertNull(accepted.possibleDuplicateOfItemId)
        assertNull(accepted.duplicateSimilarity)
        assertFalse(accepted.needsReview)
    }

    @Test
    fun `user can accept a safe item after optional enrichment fails`() {
        val accepted = ReviewResolution.accept(
            item().copy(enrichmentState = EnrichmentState.FAILED_FINAL),
            now = 500L
        )

        assertEquals(EnrichmentState.USER_ACCEPTED, accepted.enrichmentState)
        assertFalse(accepted.needsReview)
    }

    @Test
    fun `terminal enrichment failure is explained before category uncertainty`() {
        val failed = item().copy(
            enrichmentState = EnrichmentState.FAILED_FINAL,
            aiClassification = Classification.INVOICE
        )

        assertEquals(ReviewReason.ENRICHMENT_FAILED, ReviewResolution.reason(failed))
    }

    private fun item() = VaultItem(id = "item", title = "Saved", needsReview = true)
}
