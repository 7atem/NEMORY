package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.core.common.model.VaultItem
import org.junit.Test

class PrivacyEngineTest {
    private val engine = PrivacyEngine()

    @Test
    fun `passport is local only even with explicit consent`() {
        val item = item(Classification.PASSPORT)

        assertThat(engine.ruleFor(item)).isEqualTo(CloudProcessingRule.NEVER)
        assertThat(
            engine.decide(
                item = item,
                mode = AiPrivacyMode.SMART_HYBRID,
                explicitConsent = true,
                categoryOptIn = true
            )
        ).isEqualTo(CloudDecision.LOCAL_ONLY)
    }

    @Test
    fun `identity document is local only even with explicit consent`() {
        val item = item(Classification.IDENTITY_DOCUMENT)

        assertThat(engine.ruleFor(item)).isEqualTo(CloudProcessingRule.NEVER)
        assertThat(
            engine.decide(
                item = item,
                mode = AiPrivacyMode.SMART_HYBRID,
                explicitConsent = true,
                categoryOptIn = true
            )
        ).isEqualTo(CloudDecision.LOCAL_ONLY)
    }

    @Test
    fun `health lens is local only when classification is unknown`() {
        val item = item(Classification.UNKNOWN, setOf(LensId.HEALTH))

        assertThat(engine.ruleFor(item)).isEqualTo(CloudProcessingRule.NEVER)
    }

    @Test
    fun `unknown item stays local even with explicit cloud consent`() {
        val item = item(Classification.UNKNOWN)

        assertThat(
            engine.decide(
                item = item,
                mode = AiPrivacyMode.SMART_HYBRID,
                explicitConsent = true,
                categoryOptIn = true
            )
        ).isEqualTo(CloudDecision.LOCAL_ONLY)
    }

    @Test
    fun `missing classification stays local`() {
        val item = VaultItem(id = "item-unclassified", title = "Unclassified")

        assertThat(engine.ruleFor(item)).isEqualTo(CloudProcessingRule.NEVER)
    }

    @Test
    fun `low confidence document stays local until classification is resolved`() {
        val item = item(Classification.RECEIPT, confidence = 0.49f)

        assertThat(engine.ruleFor(item)).isEqualTo(CloudProcessingRule.NEVER)
        assertThat(
            engine.decide(item, AiPrivacyMode.SMART_HYBRID, explicitConsent = true)
        ).isEqualTo(CloudDecision.LOCAL_ONLY)
    }

    @Test
    fun `user override resolves unknown item using selected category policy`() {
        val item = item(
            classification = Classification.UNKNOWN,
            confidence = 0.1f,
            userOverride = Classification.BOOK
        )

        assertThat(engine.ruleFor(item)).isEqualTo(CloudProcessingRule.ALLOWED_IF_OPTED_IN)
    }

    @Test
    fun `low confidence media remains eligible for remembered category opt in`() {
        val item = item(Classification.MOVIE, confidence = 0.1f)

        assertThat(engine.ruleFor(item)).isEqualTo(CloudProcessingRule.ALLOWED_IF_OPTED_IN)
    }

    @Test
    fun `financial item requires explicit consent`() {
        val item = item(Classification.RECEIPT, setOf(LensId.MONEY))

        assertThat(engine.decide(item, AiPrivacyMode.SMART_HYBRID))
            .isEqualTo(CloudDecision.REQUIRES_CONSENT)
        assertThat(engine.decide(item, AiPrivacyMode.SMART_HYBRID, explicitConsent = true))
            .isEqualTo(CloudDecision.CLOUD_ALLOWED)
    }

    @Test
    fun `media category can use remembered opt in only in smart hybrid mode`() {
        val item = item(Classification.MOVIE, setOf(LensId.MEDIA))

        assertThat(
            engine.decide(item, AiPrivacyMode.ASK_BEFORE_CLOUD, categoryOptIn = true)
        ).isEqualTo(CloudDecision.REQUIRES_CONSENT)
        assertThat(
            engine.decide(item, AiPrivacyMode.SMART_HYBRID, categoryOptIn = true)
        ).isEqualTo(CloudDecision.CLOUD_ALLOWED)
    }

    @Test
    fun `private mode never permits cloud`() {
        val item = item(Classification.BOOK, setOf(LensId.MEDIA))

        assertThat(
            engine.decide(item, AiPrivacyMode.PRIVATE, explicitConsent = true, categoryOptIn = true)
        ).isEqualTo(CloudDecision.LOCAL_ONLY)
    }

    private fun item(
        classification: Classification,
        lenses: Set<String> = emptySet(),
        confidence: Float = 0.9f,
        userOverride: Classification? = null,
        needsReview: Boolean = false
    ) = VaultItem(
        id = "item-1",
        title = "Test",
        aiClassification = classification,
        aiConfidence = confidence,
        userClassificationOverride = userOverride,
        lensTags = lenses,
        needsReview = needsReview
    )
}
