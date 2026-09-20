package com.vaultbrain.shared.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultItemProcessingStatusTest {
    @Test
    fun `new shared media is saved before organization starts`() {
        val item = VaultItem(
            id = "shared",
            title = "Captured item",
            extractionState = ProcessingState.PENDING,
            enrichmentState = EnrichmentState.PENDING
        )

        assertEquals(ItemProcessingStatus.SAVED, item.processingStatus)
    }

    @Test
    fun `active pipeline wins over provisional review state`() {
        val item = VaultItem(
            id = "active",
            title = "Captured item",
            extractionState = ProcessingState.COMPLETE,
            enrichmentState = EnrichmentState.RUNNING,
            needsReview = true
        )

        assertEquals(ItemProcessingStatus.ORGANIZING, item.processingStatus)
    }

    @Test
    fun `manual classification is authoritative`() {
        val item = VaultItem(
            id = "override",
            title = "Document",
            aiClassification = Classification.RECEIPT,
            userClassificationOverride = Classification.INVOICE
        )

        assertEquals(Classification.INVOICE, item.effectiveClassification)
    }
}
