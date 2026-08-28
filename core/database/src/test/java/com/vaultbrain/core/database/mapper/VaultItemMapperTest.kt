package com.vaultbrain.core.database.mapper

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.common.model.ProcessingState
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.Tier
import com.vaultbrain.core.common.model.VaultItem
import org.junit.Test

class VaultItemMapperTest {

    @Test
    fun `maps domain to entity and back`() {
        val original = VaultItem(
            id = "test-id",
            title = "Carrefour Receipt",
            summary = "Groceries",
            rawOcrText = "Total: 4850",
            sourceType = SourceType.CAMERA,
            parsedMetadata = mapOf("merchant" to "Carrefour"),
            lensTags = setOf("MONEY"),
            aiClassification = Classification.RECEIPT,
            userClassificationOverride = Classification.INVOICE,
            userEditedAt = 987L,
            extractionState = ProcessingState.COMPLETE,
            enrichmentState = EnrichmentState.COMPLETE,
            indexingState = ProcessingState.FAILED_RETRYABLE,
            enrichmentAttemptCount = 2,
            enrichmentLastAttemptAt = 1234L,
            enrichmentErrorCode = "TEST_FAILURE",
            possibleDuplicateOfItemId = "earlier-item",
            duplicateSimilarity = 0.993f,
            aiConfidence = 0.92f,
            dominantColors = listOf("#FFFFFF"),
            detectedObjects = listOf("receipt"),
            subtype = "grocery receipt",
            topics = listOf("groceries", "shopping"),
            entities = listOf("Carrefour"),
            tags = listOf("Carrefour"),
            suggestions = listOf("Keep for returns"),
            requiredLensTier = Tier.PRO
        )

        val entity = VaultItemMapper.toEntity(original)
        assertThat(entity.title).isEqualTo("Carrefour Receipt")
        assertThat(entity.lensTags).contains("MONEY")

        val restored = VaultItemMapper.toDomain(entity)
        assertThat(restored.id).isEqualTo(original.id)
        assertThat(restored.parsedMetadata).isEqualTo(original.parsedMetadata)
        assertThat(restored.lensTags).isEqualTo(original.lensTags)
        assertThat(restored.aiClassification).isEqualTo(Classification.RECEIPT)
        assertThat(restored.enrichmentState).isEqualTo(EnrichmentState.COMPLETE)
        assertThat(restored.userClassificationOverride).isEqualTo(Classification.INVOICE)
        assertThat(restored.effectiveClassification).isEqualTo(Classification.INVOICE)
        assertThat(restored.userEditedAt).isEqualTo(987L)
        assertThat(restored.indexingState).isEqualTo(ProcessingState.FAILED_RETRYABLE)
        assertThat(restored.enrichmentAttemptCount).isEqualTo(2)
        assertThat(restored.enrichmentErrorCode).isEqualTo("TEST_FAILURE")
        assertThat(restored.possibleDuplicateOfItemId).isEqualTo("earlier-item")
        assertThat(restored.duplicateSimilarity).isEqualTo(0.993f)
        assertThat(restored.subtype).isEqualTo("grocery receipt")
        assertThat(restored.topics).containsExactly("groceries", "shopping").inOrder()
        assertThat(restored.entities).containsExactly("Carrefour")
        assertThat(restored.tags).containsExactly("Carrefour")
        assertThat(restored.suggestions).containsExactly("Keep for returns")
    }
}
