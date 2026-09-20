package com.vaultbrain.feature.capture

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.ai.embeddings.VisionEmbeddingModel
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.vectorstore.VectorStore
import com.vaultbrain.core.vectorstore.entity.VaultEmbedding
import com.vaultbrain.feature.capture.collections.CollectionSuggestionMatcher
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultEmbeddingGeneratorTest {

    private val context = mockk<Context>(relaxed = true)
    private val textEmbeddingModel = mockk<TextEmbeddingModel>()
    private val visionEmbeddingModel = mockk<VisionEmbeddingModel>()
    private val vectorStore = mockk<VectorStore>()
    private val mediaVaultStorage = mockk<MediaVaultStorage>()
    private val collectionSuggestionMatcher = mockk<CollectionSuggestionMatcher>(relaxed = true)

    private val generator = VaultEmbeddingGenerator(
        context = context,
        textEmbeddingModel = textEmbeddingModel,
        visionEmbeddingModel = visionEmbeddingModel,
        vectorStore = vectorStore,
        mediaVaultStorage = mediaVaultStorage,
        collectionSuggestionMatcher = collectionSuggestionMatcher
    )

    @Test
    fun `indexItem stores ocr and summary embeddings for a text item`() = runTest {
        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode(any()) } returns FloatArray(100) { 0.1f }
        every { visionEmbeddingModel.isAvailable() } returns false
        val stored = slot<List<VaultEmbedding>>()
        every { vectorStore.putEmbeddingsForItem(any(), capture(stored)) } just Runs

        val item = VaultItem(
            id = "item-1",
            title = "Carrefour Receipt",
            summary = "Total: EGP 4850",
            rawOcrText = "Carrefour\nTotal EGP 4850",
            parsedMetadata = mapOf("total" to "4850.0")
        )

        generator.indexItem(item)

        assertThat(stored.captured.map { it.contentType }).containsExactly("ocr_text", "summary")
        assertThat(stored.captured.map { it.itemId }.distinct()).containsExactly("item-1")
        assertThat(stored.captured.mapNotNull { it.embedding?.size }.distinct()).containsExactly(100)
        // No image uri and vision model disabled: no image embedding.
        assertThat(stored.captured.map { it.contentType }).doesNotContain("image")
    }

    @Test
    fun `indexItem runs collection suggestion matcher after storing embeddings`() = runTest {
        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode(any()) } returns FloatArray(100) { 0.1f }
        every { visionEmbeddingModel.isAvailable() } returns false
        every { vectorStore.putEmbeddingsForItem(any(), any()) } just Runs

        val item = VaultItem(id = "item-1", title = "Receipt", rawOcrText = "Total 10")
        generator.indexItem(item)

        coVerify { collectionSuggestionMatcher.match(item) }
    }

    @Test
    fun `indexItem clears embeddings when item has no embeddable content`() = runTest {
        every { textEmbeddingModel.isAvailable() } returns true
        every { visionEmbeddingModel.isAvailable() } returns false
        every { vectorStore.deleteEmbeddingsForItem(any()) } just Runs

        generator.indexItem(VaultItem(id = "empty-1", title = ""))

        verify { vectorStore.deleteEmbeddingsForItem("empty-1") }
        verify(exactly = 0) { vectorStore.putEmbeddingsForItem(any(), any()) }
    }

    @Test
    fun `indexItem propagates embedding failures so the worker can skip them`() {
        every { textEmbeddingModel.isAvailable() } returns true
        coEvery { textEmbeddingModel.encode(any()) } throws IllegalStateException("model unavailable")
        every { visionEmbeddingModel.isAvailable() } returns false

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                generator.indexItem(VaultItem(id = "item-2", title = "Receipt"))
            }
        }
    }
}
