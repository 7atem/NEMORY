package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.shared.model.VaultItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DailyIntelligenceTest {
    @Test fun `computes score and rejects low confidence and invented record indices`() = runTest {
        val model = mockk<LlmClient>()
        val tools = mockk<DeterministicToolRegistry>()
        every { model.isAvailable() } returns true
        coEvery { tools.execute(any(), any()) } returns null
        coEvery { model.generateForTask(any(), any()) } returns """{"insights":[
            {"record":0,"evidence":"Useful suggestion","relevance":1,"confidence":0.9,"urgency":1,"novelty":1},
            {"record":0,"evidence":"Uncertain","score":100,"relevance":1,"confidence":0.6,"urgency":1,"novelty":1},
            {"record":42,"evidence":"Invented source","relevance":1,"confidence":1,"urgency":1,"novelty":1},
            {"record":0,"evidence":"Low value","score":100,"relevance":0,"confidence":1,"urgency":0,"novelty":0}
        ]}"""
        val result = DailyIntelligence(model, tools).select(
            listOf(VaultItem(id = "one", title = "Document")), emptyList(), emptyList())
        assertThat(result.map { it.evidence }).containsExactly("Useful suggestion")
    }

    @Test fun `prompt contains document details needed to reason about deadlines`() = runTest {
        val model = mockk<LlmClient>()
        val tools = mockk<DeterministicToolRegistry>()
        every { model.isAvailable() } returns true
        coEvery { tools.execute(any(), any()) } returns null
        var prompt = ""
        coEvery { model.generateForTask(any(), any()) } answers {
            prompt = firstArg()
            """{"insights":[]}"""
        }
        DailyIntelligence(model, tools).select(listOf(VaultItem(
            id = "one", title = "Insurance", summary = "Annual renewal",
            expiryDate = 123456789L, parsedMetadata = mapOf("provider" to "Example insurer")
        )), emptyList(), emptyList())
        assertThat(prompt).contains("Annual renewal")
        assertThat(prompt).contains("123456789")
        assertThat(prompt).contains("Example insurer")
        assertThat(prompt).contains("now_epoch_ms")
    }
}
