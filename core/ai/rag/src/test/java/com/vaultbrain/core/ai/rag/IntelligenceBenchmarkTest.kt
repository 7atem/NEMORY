package com.vaultbrain.core.ai.rag

import android.content.Context
import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 100 fixed engine contracts, not an estimate of real-device model accuracy. */
@RunWith(Parameterized::class)
class IntelligenceBenchmarkTest(private val mode: String, private val fixture: JsonObject) {
    private fun value(key: String) = fixture.getValue(key).jsonPrimitive.content
    private fun item(id: String) = VaultItem(id = id, title = "${value("merchant")} receipt", sourceType = SourceType.MANUAL,
        aiClassification = Classification.RECEIPT, rawOcrText = value("quote"),
        parsedMetadata = mapOf("merchant" to value("merchant"), "total" to value("amount"), "currency" to "EGP"))

    @Test fun fixedContract() = runTest {
        val repository = mockk<VaultRepository>(relaxed = true)
        val model = mockk<LlmClient>()
        val sources = listOf(item("a"), item("b"))
        coEvery { repository.getActive() } returns sources
        val verifier = ClaimVerifier(model)
        when (mode) {
            "retrieval" -> {
                val embedding = mockk<TextEmbeddingModel>()
                every { embedding.isAvailable() } returns false
                coEvery { repository.search(value("merchant")) } returns sources
                coEvery { repository.filterIds(any(), any(), any(), any(), any()) } returns listOf("a", "b")
                coEvery { repository.getByIds(listOf("a", "b")) } returns sources
                val engine = RagEngine(mockk<Context>(relaxed = true), embedding, mockk(), mockk(), repository, model, mockk(), mockk())
                assertEquals(listOf("a", "b"), engine.retrieveHybrid(value("merchant"), SearchFilters()).map { it.id })
            }
            "arithmetic" -> {
                val result = DeterministicToolRegistry(repository, QueryIntentParser()).execute("How much did I spend at ${value("merchant")}?", 0)!!
                assertEquals("EGP ${value("expectedTotal")}", result.evidence!!.headline)
                assertEquals(setOf("a", "b"), result.sources.map { it.id }.toSet())
            }
            "grounding" -> {
                val source = sources.first().copy(rawOcrText = "irrelevant ".repeat(100) + value("quote"))
                assertEquals("> ${value("quote")} [1]", verifier.verifyAndRepair("${value("quote")} [1]", listOf(source)))
            }
            "hallucination" -> {
                val result = verifier.verifyAndRepair("A fabricated balance of 999999 is due [1]", sources)
                assertFalse(result.contains("999999"))
                assertTrue(result.contains(value("quote")))
            }
            "cross_document" -> {
                val conflicting = sources[1].copy(rawOcrText = "The policy was cancelled in 2029.")
                val result = verifier.verifyAndRepair("${value("quote")} [1][2]", listOf(sources[0], conflicting))
                assertTrue(result.contains("[1]"))
                assertTrue(result.contains("cancelled in 2029. [2]"))
                assertFalse(result.contains("[1][2]"))
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): Collection<Array<Any>> {
            val stream = requireNotNull(IntelligenceBenchmarkTest::class.java.getResourceAsStream("/assessment_benchmark.json"))
            val fixtures = stream.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonArray }
            require(fixtures.size == 20)
            return fixtures.flatMap { fixture -> listOf("retrieval", "arithmetic", "grounding", "hallucination", "cross_document")
                .map { arrayOf<Any>(it, fixture.jsonObject) } }
        }
    }
}
