package com.vaultbrain.feature.brain

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.VaultItem
import org.junit.Test

class BrainSuggestionProviderTest {
    private val provider = BrainSuggestionProvider()

    @Test
    fun `empty vault teaches no unavailable capabilities`() {
        assertThat(provider.forItems(emptyList())).isEmpty()
    }

    @Test
    fun `suggestions follow present categories and remain bounded`() {
        val items = listOf(
            item("receipt", Classification.RECEIPT),
            item("trip", Classification.TICKET),
            item("movie", Classification.MOVIE),
            item("health", Classification.PRESCRIPTION),
            item("warranty", Classification.WARRANTY_CARD)
        )

        val result = provider.forItems(items, now = 1L)

        assertThat(result).hasSize(4)
        assertThat(result).containsAtLeast(
            BrainSuggestion.RECENT,
            BrainSuggestion.TRAVEL,
            BrainSuggestion.WARRANTIES,
            BrainSuggestion.RECEIPTS
        )
    }

    @Test
    fun `recurring comparison is suggested only when a provider has a comparable pair`() {
        val items = listOf(
            item("old", Classification.RECEIPT).copy(
                recurringRule = "monthly",
                parsedMetadata = mapOf("merchant" to "Internet", "currency" to "EGP")
            ),
            item("new", Classification.RECEIPT).copy(
                recurringRule = "monthly",
                parsedMetadata = mapOf("merchant" to "Internet", "currency" to "EGP")
            )
        )

        assertThat(provider.forItems(items)).contains(BrainSuggestion.RECURRING_SPEND)
        assertThat(provider.forItems(items)).doesNotContain(BrainSuggestion.RECEIPTS)
    }

    private fun item(id: String, classification: Classification) = VaultItem(
        id = id,
        title = id,
        aiClassification = classification
    )
}
