package com.vaultbrain.feature.vault.components

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem

object PreviewData {

    fun item(
        id: String,
        title: String,
        summary: String? = null,
        lensTags: Set<String> = emptySet(),
        expiryDate: Long? = null,
        capturedImageUri: String? = null
    ): VaultItem = VaultItem(
        id = id,
        title = title,
        summary = summary,
        sourceType = SourceType.CAMERA,
        createdAt = System.currentTimeMillis(),
        lensTags = lensTags,
        expiryDate = expiryDate,
        capturedImageUri = capturedImageUri,
        aiClassification = Classification.GENERAL_DOCUMENT
    )

    val sampleItems: List<VaultItem> = listOf(
        item(
            id = "1",
            title = "Receipt from Grocery",
            summary = "Weekly shopping",
            lensTags = setOf(LensId.MONEY)
        ),
        item(
            id = "2",
            title = "Passport scan",
            summary = "Front page",
            lensTags = setOf(LensId.BUREAUCRACY),
            expiryDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        ),
        item(
            id = "3",
            title = "Hotel booking",
            summary = "Reservation #8821",
            lensTags = setOf(LensId.TRAVEL)
        )
    )

    fun lensCount(lensId: String): Int = sampleItems.count { lensId in it.lensTags }
}
