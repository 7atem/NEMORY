package com.vaultbrain.feature.vault.components

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Test
import com.vaultbrain.core.common.ui.VaultItemPresenter
import com.vaultbrain.core.common.ui.VaultCardDateKind

class VaultItemPresenterTest {
    @Test
    fun `receipt card prioritizes merchant and amount`() {
        val result = VaultItemPresenter.present(
            item(
                classification = Classification.RECEIPT,
                metadata = mapOf(
                    "merchant" to "Nile Market",
                    "total" to "145.5",
                    "currency" to "EGP"
                )
            )
        )

        assertEquals("Merchant: Nile Market", result.keyFact)
        assertEquals("Saved item", result.category)
    }

    @Test
    fun `due document uses actionable date instead of capture date`() {
        val result = VaultItemPresenter.present(
            item(
                classification = Classification.INVOICE,
                metadata = mapOf("due_date" to "2026-09-01"),
                expiryDate = 1_800L
            )
        )

        assertEquals(VaultCardDateKind.DUE, result.dateKind)
        assertEquals(1_800L, result.dateMillis)
    }

    @Test
    fun `unknown item falls back to summary and saved date`() {
        val result = VaultItemPresenter.present(
            item(classification = Classification.UNKNOWN, summary = "Remember this", createdAt = 900L)
        )

        assertEquals(null, result.keyFact)
        assertEquals("Remember this", result.summary)
        assertEquals(VaultCardDateKind.SAVED, result.dateKind)
        assertEquals(900L, result.dateMillis)
        assertEquals("Saved item", result.category)
    }

    @Test
    fun `card label uses content subtype instead of internal classification or facet`() {
        val result = VaultItemPresenter.present(
            item(
                classification = Classification.PRODUCT_PHOTO,
                subtype = "Wi-Fi 7 router"
            )
        )

        assertEquals("Wi-Fi 7 router", result.category)
    }

    @Test
    fun `lensId comes from primary lens or first lens tag`() {
        val withPrimary = VaultItemPresenter.present(
            item(classification = Classification.RECEIPT, lensTags = setOf("money"), primaryLensId = "money")
        )
        assertEquals("money", withPrimary.lensId)

        val fallback = VaultItemPresenter.present(
            item(classification = Classification.RECEIPT, lensTags = setOf("travel"), primaryLensId = null)
        )
        assertEquals("travel", fallback.lensId)

        val none = VaultItemPresenter.present(item(classification = Classification.RECEIPT))
        assertEquals(null, none.lensId)
    }

    @Test
    fun `urgent flag is true for expiry within 30 days`() {
        val soon = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000
        val urgent = VaultItemPresenter.present(
            item(classification = Classification.PASSPORT, expiryDate = soon)
        )
        assertEquals(true, urgent.isUrgent)

        val later = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000
        val notUrgent = VaultItemPresenter.present(
            item(classification = Classification.PASSPORT, expiryDate = later)
        )
        assertEquals(false, notUrgent.isUrgent)
    }

    private fun item(
        classification: Classification,
        metadata: Map<String, String> = emptyMap(),
        summary: String? = null,
        createdAt: Long = 100L,
        expiryDate: Long? = null,
        subtype: String? = null,
        lensTags: Set<String> = emptySet(),
        primaryLensId: String? = null
    ) = VaultItem(
        id = "item",
        title = "Title",
        summary = summary,
        createdAt = createdAt,
        aiClassification = classification,
        parsedMetadata = metadata,
        expiryDate = expiryDate,
        subtype = subtype,
        lensTags = lensTags,
        primaryLensId = primaryLensId
    )
}
