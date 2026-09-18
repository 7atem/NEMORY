package com.vaultbrain.core.ai.llm

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.core.common.model.VaultItem
import javax.inject.Inject
import javax.inject.Singleton

enum class AiPrivacyMode {
    PRIVATE,
    ASK_BEFORE_CLOUD,
    SMART_HYBRID
}

enum class CloudProcessingRule {
    NEVER,
    ASK,
    ALLOWED_IF_OPTED_IN
}

enum class CloudDecision {
    LOCAL_ONLY,
    REQUIRES_CONSENT,
    CLOUD_ALLOWED
}

/**
 * Pure policy layer for cloud decisions. It deliberately has no networking or document logging.
 */
@Singleton
class PrivacyEngine @Inject constructor() {

    fun isRememberableCategory(classification: Classification): Boolean =
        classification in OPT_IN_CLASSIFICATIONS

    fun ruleFor(item: VaultItem): CloudProcessingRule {
        val classification = item.effectiveClassification
        return when {
            classification == null || classification == Classification.UNKNOWN ->
                CloudProcessingRule.NEVER
            classification in NEVER_CLOUD_CLASSIFICATIONS -> CloudProcessingRule.NEVER
            LensId.HEALTH in item.lensTags -> CloudProcessingRule.NEVER
            item.userClassificationOverride == null &&
                classification in DOCUMENT_CLASSIFICATIONS &&
                (item.aiConfidence < MIN_RESOLVED_CLASSIFICATION_CONFIDENCE || item.needsReview) ->
                CloudProcessingRule.NEVER
            classification in ASK_CLASSIFICATIONS -> CloudProcessingRule.ASK
            item.lensTags.any { it in ASK_LENSES } -> CloudProcessingRule.ASK
            classification in OPT_IN_CLASSIFICATIONS -> CloudProcessingRule.ALLOWED_IF_OPTED_IN
            else -> CloudProcessingRule.ASK
        }
    }

    fun decide(
        item: VaultItem,
        mode: AiPrivacyMode,
        explicitConsent: Boolean = false,
        categoryOptIn: Boolean = false
    ): CloudDecision {
        if (mode == AiPrivacyMode.PRIVATE) return CloudDecision.LOCAL_ONLY

        return when (ruleFor(item)) {
            CloudProcessingRule.NEVER -> CloudDecision.LOCAL_ONLY
            CloudProcessingRule.ASK -> {
                if (explicitConsent) CloudDecision.CLOUD_ALLOWED
                else CloudDecision.REQUIRES_CONSENT
            }
            CloudProcessingRule.ALLOWED_IF_OPTED_IN -> when {
                explicitConsent -> CloudDecision.CLOUD_ALLOWED
                mode == AiPrivacyMode.SMART_HYBRID && categoryOptIn -> CloudDecision.CLOUD_ALLOWED
                else -> CloudDecision.REQUIRES_CONSENT
            }
        }
    }

    private companion object {
        const val MIN_RESOLVED_CLASSIFICATION_CONFIDENCE = 0.5f

        val NEVER_CLOUD_CLASSIFICATIONS = setOf(
            Classification.PASSPORT,
            Classification.IDENTITY_DOCUMENT,
            Classification.PRESCRIPTION,
            Classification.LAB_RESULT
        )

        val ASK_CLASSIFICATIONS = setOf(
            Classification.RECEIPT,
            Classification.INVOICE,
            Classification.TICKET,
            Classification.HOTEL,
            Classification.GENERAL_DOCUMENT,
            Classification.BUSINESS_CARD,
            Classification.WARRANTY_CARD
        )

        val OPT_IN_CLASSIFICATIONS = setOf(
            Classification.MOVIE,
            Classification.TV_SERIES,
            Classification.BOOK,
            Classification.PRODUCT_PHOTO,
            Classification.MENU_PHOTO
        )

        val DOCUMENT_CLASSIFICATIONS = setOf(
            Classification.RECEIPT,
            Classification.PRESCRIPTION,
            Classification.LAB_RESULT,
            Classification.PASSPORT,
            Classification.IDENTITY_DOCUMENT,
            Classification.TICKET,
            Classification.HOTEL,
            Classification.INVOICE,
            Classification.WARRANTY_CARD,
            Classification.BUSINESS_CARD,
            Classification.SERIAL_PLATE,
            Classification.GENERAL_DOCUMENT
        )

        val ASK_LENSES = setOf(
            LensId.MONEY,
            LensId.TRAVEL,
            LensId.BUREAUCRACY
        )
    }
}
