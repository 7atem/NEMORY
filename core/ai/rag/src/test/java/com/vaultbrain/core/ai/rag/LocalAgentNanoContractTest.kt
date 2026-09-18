package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.VaultItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalAgentNanoContractTest {
    /** Mimics Gemini Nano: prose answers before it settles into the JSON-only vocabulary. */
    private fun nanoLikeModel(first: String?, vararg rest: String?): LlmClient {
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        var stub = coEvery { model.generateForTask(any(), any()) } returns first
        rest.forEach { stub = stub andThen it }
        return model
    }

    @Test fun `malformed prose response terminates retrieval safely without tool calls`() = runTest {
        val model = nanoLikeModel(
            "I think your passport was renewed in May, here is what I found...",
            "{\"queries\":[\"passport renewal\"]}",
            "{\"queries\":[]}"
        )
        val passport = VaultItem(
            id = "1",
            title = "Passport renewal receipt",
            sourceType = SourceType.TEXT_PASTE,
            rawOcrText = "Passport renewal fee of 110.00 paid on 2026-05-12."
        )
        val result = LocalAgent(model).retrieve("When did I renew my passport?", listOf(passport), search = {
            listOf(passport)
        }, budget = com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL)
        coVerify(exactly = 1) { model.generateForTask(any(), any()) }
        assertThat(result.trace).isEmpty()
        assertThat(result.sources).containsExactly(passport)
    }

    @Test fun `malformed response after a valid search keeps evidence and stays bounded`() = runTest {
        val model = nanoLikeModel(
            "{\"queries\":[\"passport renewal\"]}",
            "Sure! Your passport was renewed on May 12, 2026."
        )
        val insurance = VaultItem(id = "9", title = "Car insurance", sourceType = SourceType.TEXT_PASTE)
        val passport = VaultItem(id = "1", title = "Passport renewal receipt", sourceType = SourceType.TEXT_PASTE)
        val result = LocalAgent(model).retrieve("passport", listOf(insurance), search = {
            listOf(passport)
        }, budget = com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL)
        assertThat(result.trace).containsExactly(LocalAgent.Trace("search_vault", 1))
        assertThat(result.trace.size).isAtMost(6)
        assertThat(result.sources.map { it.id }).containsExactly("1", "9")
    }

    @Test fun `empty queries plan ends the loop within bounds`() = runTest {
        val model = nanoLikeModel(
            "{\"queries\":[\"passport renewal\"]}",
            "{\"queries\":[]}"
        )
        val passport = VaultItem(id = "1", title = "Passport renewal receipt", sourceType = SourceType.TEXT_PASTE)
        val result = LocalAgent(model).retrieve("passport", emptyList(), search = { listOf(passport) }, budget = com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL)
        coVerify(exactly = 2) { model.generateForTask(any(), any()) }
        assertThat(result.trace).containsExactly(LocalAgent.Trace("search_vault", 1))
        assertThat(result.sources).containsExactly(passport)
    }
}
