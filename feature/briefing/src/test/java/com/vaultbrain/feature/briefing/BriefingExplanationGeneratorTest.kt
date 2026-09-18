package com.vaultbrain.feature.briefing

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.LlmClient
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BriefingExplanationGeneratorTest {
    private val llmClient = mockk<LlmClient>()
    private val generator = BriefingExplanationGenerator(llmClient)
    private val insight = TodayInsight.SpendingIncrease(
        primaryItemId = "new",
        provider = "Internet",
        currency = "EGP",
        previousAmount = BigDecimal("500"),
        currentAmount = BigDecimal("550"),
        delta = BigDecimal("50"),
        relatedItemIds = listOf("new", "old")
    )

    @Test
    fun `accepts one sentence using only deterministic numbers`() = runTest {
        coEvery { llmClient.generate(any()) } returns "Your Internet charge moved from EGP 500 to EGP 550."

        assertThat(generator.explain(insight, "en"))
            .isEqualTo("Your Internet charge moved from EGP 500 to EGP 550.")
    }

    @Test
    fun `rejects invented numbers`() = runTest {
        coEvery { llmClient.generate(any()) } returns "Your charge rose by EGP 75."

        assertThat(generator.explain(insight, "en")).isNull()
    }

    @Test
    fun `rejects claims that an action already happened`() = runTest {
        coEvery { llmClient.generate(any()) } returns "A reminder was scheduled for EGP 550."

        assertThat(generator.explain(insight, "en")).isNull()
    }

    @Test
    fun `Arabic prompt contains only the deterministic fact contract`() = runTest {
        coEvery { llmClient.generate(match { it.contains("ارتفعت دفعة Internet") }) } returns
            "ارتفعت دفعة Internet من EGP 500 إلى EGP 550."

        assertThat(generator.explain(insight, "ar")).isNotNull()
    }
}
