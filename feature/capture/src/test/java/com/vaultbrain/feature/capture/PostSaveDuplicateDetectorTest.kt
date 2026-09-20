package com.vaultbrain.feature.capture

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.vectorstore.VectorStore
import com.vaultbrain.core.vectorstore.entity.VaultEmbedding
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PostSaveDuplicateDetectorTest {
    private val repository = mockk<VaultRepository>()
    private val vectorStore = mockk<VectorStore>()
    private val detector = PostSaveDuplicateDetector(repository, vectorStore)

    @Test
    fun `finds exact OCR duplicate and excludes current item from candidates`() = runTest {
        val text = "Fresh Market receipt date 2026-08-24 total 42.50 EGP transaction 902145"
        val current = item(id = "new", text = text)
        val earlier = item(id = "old", text = text.uppercase())
        val query = embedding("new", "ocr_text", floatArrayOf(1f, 0f))
        everyNeighbors(
            query,
            embedding("new", "ocr_text", floatArrayOf(1f, 0f)),
            embedding("old", "ocr_text", floatArrayOf(1f, 0f))
        )
        coEvery { repository.getById("old") } returns earlier

        val result = detector.findSuggestion(current, listOf(query))

        assertThat(result).isEqualTo(DuplicateSuggestion("old", 1f))
        io.mockk.coVerify(exactly = 0) { repository.getById("new") }
    }

    @Test
    fun `does not use visual similarity alone`() = runTest {
        val current = item(id = "new", text = null)
        val image = embedding("new", "image", floatArrayOf(1f, 0f))

        val result = detector.findSuggestion(current, listOf(image))

        assertThat(result).isNull()
        verify(exactly = 0) { vectorStore.nearestNeighbors(any(), any(), any()) }
    }

    @Test
    fun `stable identifiers corroborate a retrieved candidate`() {
        val current = item(
            id = "new",
            text = "Passport scan front side",
            classification = Classification.PASSPORT,
            metadata = mapOf("document_number" to "A 123-45678")
        )
        val earlier = item(
            id = "old",
            text = "Arab Republic travel document",
            classification = Classification.PASSPORT,
            metadata = mapOf("document_number" to "A12345678")
        )

        val result = detector.evaluate(current, earlier, 0.91f)

        assertThat(result).isEqualTo(DuplicateSuggestion("old", 1f))
    }

    @Test
    fun `high vector score without lexical corroboration is not enough`() {
        val first = (1..30).joinToString(" ") { "alpha$it" }
        val second = (1..30).joinToString(" ") { "beta$it" }

        val result = detector.evaluate(
            item(id = "new", text = first),
            item(id = "old", text = second),
            vectorSimilarity = 0.999f
        )

        assertThat(result).isNull()
    }

    @Test
    fun `conflicting categories and stealth boundaries suppress suggestions`() {
        val metadata = mapOf("document_number" to "ABC123456")
        val current = item("new", "document one", Classification.PASSPORT, metadata)

        assertThat(
            detector.evaluate(
                current,
                item("old", "document two", Classification.INVOICE, metadata),
                1f
            )
        ).isNull()
        assertThat(
            detector.evaluate(
                current,
                item("secret", "document two", Classification.PASSPORT, metadata, isStealth = true),
                1f
            )
        ).isNull()
    }

    private fun everyNeighbors(query: VaultEmbedding, vararg results: VaultEmbedding) {
        io.mockk.every {
            vectorStore.nearestNeighbors(query.embedding!!, 12, query.contentType)
        } returns results.toList()
    }

    private fun embedding(itemId: String, type: String, vector: FloatArray) = VaultEmbedding(
        itemId = itemId,
        contentType = type,
        embedding = vector
    )

    private fun item(
        id: String,
        text: String?,
        classification: Classification = Classification.RECEIPT,
        metadata: Map<String, String> = emptyMap(),
        isStealth: Boolean = false
    ) = VaultItem(
        id = id,
        title = id,
        rawOcrText = text,
        aiClassification = classification,
        parsedMetadata = metadata,
        isStealth = isStealth
    )
}
