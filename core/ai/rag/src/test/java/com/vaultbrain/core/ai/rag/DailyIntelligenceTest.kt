package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.VaultItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DailyIntelligenceTest {
    private val fixedNow = Instant.parse("2026-09-14T12:00:00Z").toEpochMilli()
    private val dayMs = 86_400_000L

    private fun modelReturning(json: String): LlmClient {
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        coEvery { model.generateForTask(any(), any()) } returns json
        return model
    }

    private fun emptyTools(): DeterministicToolRegistry {
        val tools = mockk<DeterministicToolRegistry>()
        coEvery { tools.execute(any(), any()) } returns null
        return tools
    }

    @Test fun `computes score and rejects low confidence and invented record indices`() = runTest {
        val model = modelReturning("""{"insights":[
            {"record":0,"evidence":"Useful suggestion","relevance":1,"confidence":0.9,"urgency":1,"novelty":1},
            {"record":0,"evidence":"Uncertain","score":100,"relevance":1,"confidence":0.6,"urgency":1,"novelty":1},
            {"record":42,"evidence":"Invented source","relevance":1,"confidence":1,"urgency":1,"novelty":1},
            {"record":0,"evidence":"Low value","score":100,"relevance":0,"confidence":1,"urgency":0,"novelty":0}
        ]}""")
        val result = DailyIntelligence(model, emptyTools(), clock = { fixedNow }).select(
            listOf(VaultItem(id = "one", title = "Document")), emptyList(), emptyList())
        assertThat(result.map { it.evidence }).containsExactly("Useful suggestion")
    }

    @Test fun `prompt contains document details needed to reason about deadlines`() = runTest {
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        var prompt = ""
        coEvery { model.generateForTask(any(), any()) } answers {
            prompt = firstArg()
            """{"insights":[]}"""
        }
        DailyIntelligence(model, emptyTools(), clock = { fixedNow }).select(listOf(VaultItem(
            id = "one", title = "Insurance", summary = "Annual renewal",
            expiryDate = 123456789L, parsedMetadata = mapOf("provider" to "Example insurer")
        )), emptyList(), emptyList())
        assertThat(prompt).contains("Annual renewal")
        assertThat(prompt).contains("123456789")
        assertThat(prompt).contains("Example insurer")
        assertThat(prompt).contains("now_epoch_ms")
    }

    @Test fun `expiring in 3 days outranks expiring in 180 days with identical llm dimensions`() = runTest {
        val model = modelReturning("""{"insights":[
            {"record":0,"evidence":"Near expiry insight","relevance":0.9,"confidence":0.9,"urgency":0.5,"novelty":0.6},
            {"record":1,"evidence":"Far expiry insight","relevance":0.9,"confidence":0.9,"urgency":0.5,"novelty":0.6}
        ]}""")
        val result = DailyIntelligence(model, emptyTools(), clock = { fixedNow }).select(
            listOf(
                VaultItem(id = "far", title = "Annual permit", expiryDate = fixedNow + 180 * dayMs),
                VaultItem(id = "near", title = "Visa", expiryDate = fixedNow + 3 * dayMs)
            ), emptyList(), emptyList())
        assertThat(result.map { it.evidence })
            .containsExactly("Near expiry insight", "Far expiry insight").inOrder()
    }

    @Test fun `missing hotel trip outranks trivial note despite lower llm urgency`() = runTest {
        val tools = mockk<DeterministicToolRegistry>()
        coEvery { tools.execute("missing documents", any()) } returns RagResponse(answer = "a",
            evidence = RagEvidence(RagEvidenceKind.MISSING_DOCUMENTS, "h",
                listOf("Trip (Rome trip) missing hotel booking")))
        coEvery { tools.execute("recurring spend increases", any()) } returns null
        val model = modelReturning("""{"insights":[
            {"record":0,"evidence":"Rome trip lacks a hotel booking","relevance":0.9,"confidence":0.9,"urgency":0.2,"novelty":0.6},
            {"record":1,"evidence":"Trivial archive observation","relevance":0.85,"confidence":0.9,"urgency":0.5,"novelty":0.6}
        ]}""")
        val result = DailyIntelligence(model, tools, clock = { fixedNow }).select(
            listOf(
                VaultItem(id = "trip", title = "Rome trip"),
                VaultItem(id = "note", title = "Old note")
            ), emptyList(), emptyList())
        assertThat(result.map { it.evidence }).containsExactly(
            "Rome trip lacks a hotel booking", "Trivial archive observation").inOrder()
    }

    @Test fun `utility bill gap boosts urgency and feeds prompt`() = runTest {
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        var prompt = ""
        coEvery { model.generateForTask(any(), any()) } answers {
            prompt = firstArg()
            """{"insights":[
                {"record":0,"evidence":"Power bill seems late","relevance":0.9,"confidence":0.9,"urgency":0.1,"novelty":0.6},
                {"record":1,"evidence":"Random document note","relevance":0.9,"confidence":0.9,"urgency":0.5,"novelty":0.6}
            ]}"""
        }
        val result = DailyIntelligence(model, emptyTools(), clock = { fixedNow }).select(
            listOf(
                VaultItem(id = "bill", title = "Electricity", aiClassification = Classification.UTILITY_BILL,
                    parsedMetadata = mapOf("merchant" to "North Power", "billing_period" to "2026-06")),
                VaultItem(id = "doc", title = "Random document")
            ), emptyList(), emptyList())
        assertThat(prompt).contains("utility_bill_gaps")
        assertThat(prompt).contains("utility bill for")
        assertThat(result.map { it.evidence }).containsExactly(
            "Power bill seems late", "Random document note").inOrder()
    }

    @Test fun `gates still filter after urgency blend`() = runTest {
        val model = modelReturning("""{"insights":[
            {"record":0,"evidence":"Below gate after blend","relevance":0.7,"confidence":0.9,"urgency":0.2,"novelty":0.4},
            {"record":1,"evidence":"Confidence at gate boundary","relevance":1,"confidence":0.6,"urgency":1,"novelty":1}
        ]}""")
        val result = DailyIntelligence(model, emptyTools(), clock = { fixedNow }).select(
            listOf(
                VaultItem(id = "a", title = "Document A"),
                VaultItem(id = "b", title = "Document B")
            ), emptyList(), emptyList())
        assertThat(result).isEmpty()
    }
}
