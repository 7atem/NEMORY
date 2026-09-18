package com.vaultbrain.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveActionResolverTest {
    @Test
    fun `media offers source and reminder only when eligible`() {
        val item = VaultItem(
            id = "media",
            title = "Dune",
            aiClassification = Classification.MOVIE,
            parsedMetadata = mapOf("provider_url" to "https://imdb.com/title/tt1")
        )

        val actions = ProactiveActionResolver.resolve(item)

        assertTrue(ProactiveAction.OPEN_SOURCE in actions)
        assertTrue(ProactiveAction.REMIND_LATER in actions)
    }

    @Test
    fun `model requested action cannot bypass eligibility`() {
        val item = VaultItem(
            id = "general",
            title = "Note",
            aiClassification = Classification.GENERAL_DOCUMENT,
            parsedMetadata = mapOf(
                ProactiveActionResolver.METADATA_KEY to "OPEN_SOURCE,ADD_CONTACT"
            )
        )

        assertFalse(ProactiveActionResolver.resolve(item).isNotEmpty())
    }

    @Test
    fun `manual classification changes proactive routing`() {
        val item = VaultItem(
            id = "manual",
            title = "Book",
            aiClassification = Classification.GENERAL_DOCUMENT,
            userClassificationOverride = Classification.BOOK
        )

        assertEquals(listOf(ProactiveAction.REMIND_LATER), ProactiveActionResolver.resolve(item))
    }

    @Test
    fun `identity expiry offers review without exposing a cloud action`() {
        val item = VaultItem(
            id = "identity",
            title = "Identity document",
            aiClassification = Classification.IDENTITY_DOCUMENT,
            expiryDate = 1_900_000_000_000
        )

        assertEquals(listOf(ProactiveAction.REVIEW_EXPIRY), ProactiveActionResolver.resolve(item))
    }
}
