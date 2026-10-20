package com.vaultbrain.feature.vault.components

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem

object PreviewData {

    private val now = System.currentTimeMillis()

    fun item(
        id: String,
        title: String,
        summary: String? = null,
        lensTags: Set<String> = emptySet(),
        primaryLensId: String? = null,
        expiryDate: Long? = null,
        secondaryAlertDate: Long? = null,
        classification: Classification = Classification.GENERAL_DOCUMENT,
        parsedMetadata: Map<String, String> = emptyMap(),
        capturedImageUri: String? = null,
        createdAt: Long = now
    ): VaultItem = VaultItem(
        id = id,
        title = title,
        summary = summary,
        sourceType = SourceType.CAMERA,
        createdAt = createdAt,
        lensTags = lensTags,
        primaryLensId = primaryLensId ?: lensTags.firstOrNull(),
        expiryDate = expiryDate,
        secondaryAlertDate = secondaryAlertDate,
        capturedImageUri = capturedImageUri,
        aiClassification = classification,
        parsedMetadata = parsedMetadata,
        extractionState = com.vaultbrain.shared.model.ProcessingState.COMPLETE,
        indexingState = com.vaultbrain.shared.model.ProcessingState.COMPLETE,
        needsReview = false
    )

    val sampleItems: List<VaultItem> = listOf(
        item(
            id = "demo_receipt",
            title = "Grocery receipt — Carrefour",
            summary = "Weekly shopping",
            lensTags = setOf(LensId.MONEY),
            primaryLensId = LensId.MONEY,
            classification = Classification.RECEIPT,
            parsedMetadata = mapOf(
                "merchant" to "Carrefour",
                "total" to "142.50",
                "currency" to "AED",
                "date" to "2026-09-12"
            ),
            createdAt = now - 2L * 24 * 60 * 60 * 1000
        ),
        item(
            id = "demo_passport",
            title = "Passport",
            summary = "Front page",
            lensTags = setOf(LensId.BUREAUCRACY),
            primaryLensId = LensId.BUREAUCRACY,
            classification = Classification.PASSPORT,
            parsedMetadata = mapOf(
                "document_number" to "P12345678",
                "nationality" to "United Arab Emirates",
                "expiry_date" to "2027-03-15"
            ),
            expiryDate = now + 180L * 24 * 60 * 60 * 1000,
            createdAt = now - 30L * 24 * 60 * 60 * 1000
        ),
        item(
            id = "demo_flight",
            title = "Flight booking — DXB to NRT",
            summary = "Reservation #8821",
            lensTags = setOf(LensId.TRAVEL),
            primaryLensId = LensId.TRAVEL,
            classification = Classification.TICKET,
            parsedMetadata = mapOf(
                "carrier" to "Emirates",
                "flight_number" to "EK318",
                "passenger" to "Ahmad Hassan",
                "departure_date" to "2026-10-05",
                "route" to "DXB - NRT"
            ),
            secondaryAlertDate = now + 20L * 24 * 60 * 60 * 1000,
            createdAt = now - 5L * 24 * 60 * 60 * 1000
        ),
        item(
            id = "demo_prescription",
            title = "Prescription",
            summary = "Dr. Khalid Al-Farsi",
            lensTags = setOf(LensId.HEALTH),
            primaryLensId = LensId.HEALTH,
            classification = Classification.PRESCRIPTION,
            parsedMetadata = mapOf(
                "patient_name" to "Ahmad Hassan",
                "medication" to "Amoxicillin 500mg",
                "doctor" to "Dr. Khalid Al-Farsi",
                "prescription_date" to "2026-09-10"
            ),
            createdAt = now - 4L * 24 * 60 * 60 * 1000
        ),
        item(
            id = "demo_utility",
            title = "Electricity bill — DEWA",
            summary = "August statement",
            lensTags = setOf(LensId.MONEY),
            primaryLensId = LensId.MONEY,
            classification = Classification.UTILITY_BILL,
            parsedMetadata = mapOf(
                "provider" to "DEWA",
                "amount_due" to "312.00",
                "currency" to "AED",
                "billing_period" to "2026-08",
                "due_date" to "2026-09-20"
            ),
            expiryDate = now + 5L * 24 * 60 * 60 * 1000,
            createdAt = now - 8L * 24 * 60 * 60 * 1000
        ),
        item(
            id = "demo_warranty",
            title = "Warranty card — Bosch dishwasher",
            summary = "2-year manufacturer warranty",
            lensTags = emptySet(),
            primaryLensId = null,
            classification = Classification.WARRANTY_CARD,
            parsedMetadata = mapOf(
                "brand" to "Bosch",
                "product" to "Dishwasher SMS4EKI06E",
                "warranty_expiry" to "2028-09-14",
                "purchase_date" to "2026-09-14"
            ),
            expiryDate = now + 365L * 24 * 60 * 60 * 1000,
            createdAt = now - 1L * 24 * 60 * 60 * 1000
        )
    )

    val demoCollections: List<com.vaultbrain.shared.model.PersonalCollection> = listOf(
        com.vaultbrain.shared.model.PersonalCollection(
            id = "demo_col_trip",
            name = "Japan Trip",
            createdAt = now,
            updatedAt = now,
            isPinned = false,
            itemCount = 1
        ),
        com.vaultbrain.shared.model.PersonalCollection(
            id = "demo_col_home",
            name = "Home Admin",
            createdAt = now,
            updatedAt = now,
            isPinned = false,
            itemCount = 2
        )
    )

    fun lensCount(lensId: String): Int = sampleItems.count { lensId in it.lensTags }
}
