package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import android.content.Context
import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.ai.embeddings.VisionEmbeddingModel
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.llm.CloudAiOperation
import com.vaultbrain.core.ai.llm.CloudConsentDisclosure
import com.vaultbrain.core.ai.llm.HybridAiCoordinator
import com.vaultbrain.core.ai.llm.HybridAiResult
import com.vaultbrain.core.ai.llm.TokenBudget
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.vectorstore.VectorStore
import com.vaultbrain.core.vectorstore.entity.VaultEmbedding
import com.vaultbrain.core.integrations.context.PersonalContextEngine
import com.vaultbrain.core.integrations.model.ContextResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class RagEngineTest {

    private val textEmbeddingModel = mockk<TextEmbeddingModel>()
    private val visionEmbeddingModel = mockk<VisionEmbeddingModel>()
    private val vectorStore = mockk<VectorStore>()
    private val vaultRepository = mockk<VaultRepository>()
    private val llmClient = mockk<LlmClient>()
    private val hybridAiCoordinator = mockk<HybridAiCoordinator>()
    private val personalContextEngine = mockk<PersonalContextEngine>()
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        coEvery { vaultRepository.getActiveCollections() } returns emptyList()
        coEvery { personalContextEngine.assembleContext(any()) } returns ContextResult()
    }

    private val engine = RagEngine(
        context = context,
        textEmbeddingModel = textEmbeddingModel,
        visionEmbeddingModel = visionEmbeddingModel,
        vectorStore = vectorStore,
        vaultRepository = vaultRepository,
        llmClient = llmClient,
        hybridAiCoordinator = hybridAiCoordinator,
        personalContextEngine = personalContextEngine
    )

    private fun sampleItem(id: String, title: String, createdAt: Long = System.currentTimeMillis()) =
        VaultItem(
            id = id,
            title = title,
            sourceType = SourceType.CAMERA,
            createdAt = createdAt,
            aiClassification = Classification.RECEIPT
        )

    private fun embeddingFor(itemId: String, vector: FloatArray) =
        VaultEmbedding(
            itemId = itemId,
            embedding = vector,
            contentType = "ocr_text",
            createdAt = System.currentTimeMillis()
        )

    @Test
    fun `returns empty response when embedding model unavailable`() = runTest {
        every { textEmbeddingModel.isAvailable() } returns false

        val result = engine.query("receipt", SearchFilters())

        assertThat(result.answer).isNull()
        assertThat(result.sources).isEmpty()
        assertThat(result.confidence).isEqualTo(0f)
    }

    @Test
    fun `returns empty response when no vector candidates`() = runTest {
        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode("receipt") } returns FloatArray(384) { 0.1f }
        every { vectorStore.nearestNeighbors(any(), any(), any()) } returns emptyList()

        val result = engine.query("receipt", SearchFilters())

        assertThat(result.answer).isNull()
        assertThat(result.sources).isEmpty()
    }

    @Test
    fun `returns sources confidence and local summary for valid search`() = runTest {
        val item = sampleItem("1", "Carrefour Receipt")
        val vector = FloatArray(384) { 0.2f }
        val embedding = embeddingFor("1", vector)

        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode("receipt") } returns vector
        every { vectorStore.nearestNeighbors(vector, 50, null) } returns listOf(embedding)
        every { vectorStore.cosineSimilarity(vector, vector) } returns 1f
        coEvery { vaultRepository.filterIds(listOf("1"), null, null, null, null) } returns listOf("1")
        coEvery { vaultRepository.getById("1") } returns item
        coEvery { llmClient.generate(any()) } returns null

        val result = engine.query("receipt", SearchFilters())

        assertThat(result.sources).hasSize(1)
        assertThat(result.sources.first().id).isEqualTo("1")
        assertThat(result.confidence).isGreaterThan(0f)
        assertThat(result.answer).doesNotContain("Item ID")
        assertThat(result.answer).contains("Verify with official financial records")
    }

    @Test
    fun `generates answer when LLM is available`() = runTest {
        val item = sampleItem("1", "Carrefour Receipt")
        val vector = FloatArray(384) { 0.2f }
        val embedding = embeddingFor("1", vector)

        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode("receipt") } returns vector
        every { vectorStore.nearestNeighbors(vector, 50, null) } returns listOf(embedding)
        every { vectorStore.cosineSimilarity(vector, vector) } returns 1f
        coEvery { vaultRepository.filterIds(listOf("1"), null, null, null, null) } returns listOf("1")
        coEvery { vaultRepository.getById("1") } returns item
        coEvery { llmClient.generate(any()) } returns "You spent EGP 4850 at Carrefour. [Item ID: invented]"

        val result = engine.query("receipt", SearchFilters())

        assertThat(result.answer).contains("You spent EGP 4850 at Carrefour.")
        assertThat(result.answer).doesNotContain("invented")
        assertThat(result.answer).contains("Verify with official financial records")
        assertThat(result.sources).hasSize(1)
    }

    @Test
    fun `prompt is bounded and never exposes database ids to the model`() {
        val item = sampleItem("private-database-id", "Long receipt").copy(
            rawOcrText = "فاتورة " + "very long content ".repeat(2_000),
            parsedMetadata = mapOf("merchant" to "Store")
        )

        val prompt = engine.buildPrompt(
            query = "What was this? " + "question ".repeat(500),
            sources = listOf(item),
            conversationContext = "previous ".repeat(1_000)
        )

        assertThat(TokenBudget().estimateTokens(prompt)).isAtMost(TokenBudget.MAX_INPUT_TOKENS)
        assertThat(prompt).doesNotContain("private-database-id")
        assertThat(prompt).contains("untrusted data")
    }

    @Test
    fun `filters by lens tag`() = runTest {
        val vector = FloatArray(384) { 0.1f }
        val embedding = embeddingFor("1", vector)

        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode("receipt") } returns vector
        every { vectorStore.nearestNeighbors(vector, 50, null) } returns listOf(embedding)
        coEvery { vaultRepository.filterIds(listOf("1"), "MONEY", null, null, null) } returns emptyList()

        val result = engine.query("receipt", SearchFilters(lensTag = "MONEY"))

        assertThat(result.sources).isEmpty()
    }

    @Test
    fun `deterministic totals bypass embeddings and language model`() = runTest {
        coEvery { vaultRepository.getActive() } returns listOf(
            sampleItem("1", "Carrefour Receipt").copy(
                parsedMetadata = mapOf("total" to "4850", "currency" to "EGP")
            )
        )

        val result = engine.query("How much did I spend?", SearchFilters())

        assertThat(result.evidence?.headline).isEqualTo("EGP 4850")
        assertThat(result.sources).hasSize(1)
        coVerify(exactly = 0) { textEmbeddingModel.encode(any()) }
        coVerify(exactly = 0) { llmClient.generate(any()) }
    }

    @Test
    fun `streaming Brain path emits deterministic result without generation`() = runTest {
        coEvery { vaultRepository.getActive() } returns listOf(
            sampleItem("1", "Invoice").copy(
                parsedMetadata = mapOf("amount" to "75", "currency" to "USD")
            )
        )

        val results = engine.queryStream("What is my total?", SearchFilters()).toList()

        assertThat(results).hasSize(1)
        assertThat(results.single().evidence?.headline).isEqualTo("USD 75")
        coVerify(exactly = 0) { textEmbeddingModel.encode(any()) }
        verify(exactly = 0) { llmClient.generateStream(any()) }
        coVerify(exactly = 0) { hybridAiCoordinator.generate(any()) }
    }

    @Test
    fun `eligible retrieved evidence returns exact one-time cloud disclosure`() = runTest {
        val query = "Describe the visual style"
        val item = sampleItem("private-id", "Saved poster").copy(
            aiClassification = Classification.MOVIE,
            rawOcrText = "A quiet science-fiction film"
        )
        prepareRetrieval(query, item)
        val disclosure = CloudConsentDisclosure(
            operation = CloudAiOperation.EXPLAIN_RETRIEVED_EVIDENCE,
            provider = "Cloud provider",
            exactText = "Exact payload",
            canRememberForCategory = true
        )
        coEvery { hybridAiCoordinator.generate(any()) } returns
            HybridAiResult.ConsentRequired(disclosure)

        val results = engine.queryStream(query, SearchFilters()).toList()

        assertThat(results.last().cloudConsent).isEqualTo(disclosure)
        assertThat(results.last().answer).contains("Saved poster")
        coVerify(exactly = 1) {
            hybridAiCoordinator.generate(match {
                it.item.id == "private-id" &&
                    it.operation == CloudAiOperation.EXPLAIN_RETRIEVED_EVIDENCE &&
                    it.sharedTextPreview == it.cloudPrompt &&
                    !it.cloudPrompt.contains("private-id") &&
                    !it.explicitConsent
            })
        }
    }

    @Test
    fun `approved cloud retry is explicit and keeps local fallback when runtime is absent`() = runTest {
        val query = "Describe the visual style"
        val item = sampleItem("1", "Saved poster").copy(aiClassification = Classification.MOVIE)
        prepareRetrieval(query, item)
        coEvery { hybridAiCoordinator.generate(any()) } returns HybridAiResult.CloudNotConfigured

        val results = engine.queryStream(
            query,
            SearchFilters(),
            explicitCloudConsent = true
        ).toList()

        assertThat(results.last().cloudFailure).isEqualTo(RagCloudFailure.NOT_CONFIGURED)
        assertThat(results.last().answer).contains("Saved poster")
        coVerify { hybridAiCoordinator.generate(match { it.explicitConsent }) }
    }

    @Test
    fun `authorized cloud answer is guarded and reports provenance`() = runTest {
        val query = "Describe the visual style"
        val item = sampleItem("1", "Saved poster").copy(aiClassification = Classification.MOVIE)
        prepareRetrieval(query, item)
        coEvery { hybridAiCoordinator.generate(any()) } returns HybridAiResult.Success(
            text = "A quiet film. [record_1]",
            origin = AiResponseOrigin.CLOUD_AI
        )

        val result = engine.queryStream(
            query,
            SearchFilters(),
            explicitCloudConsent = true
        ).toList().last()

        assertThat(result.answer).isEqualTo("A quiet film. [1]")
        assertThat(result.responseOrigin).isEqualTo(AiResponseOrigin.CLOUD_AI)
        assertThat(result.sources.single().id).isEqualTo("1")
    }

    private fun prepareRetrieval(query: String, item: VaultItem) {
        val vector = FloatArray(384) { 0.2f }
        val embedding = embeddingFor(item.id, vector)
        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode(query) } returns vector
        every { vectorStore.nearestNeighbors(vector, 50, null) } returns listOf(embedding)
        every { vectorStore.cosineSimilarity(vector, vector) } returns 1f
        coEvery { vaultRepository.filterIds(listOf(item.id), null, null, null, null) } returns
            listOf(item.id)
        coEvery { vaultRepository.getById(item.id) } returns item
    }
}
