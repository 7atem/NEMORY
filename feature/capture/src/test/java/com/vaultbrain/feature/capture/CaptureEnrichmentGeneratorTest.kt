package com.vaultbrain.feature.capture

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.LlmImageInput
import com.vaultbrain.shared.model.Classification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CaptureEnrichmentGeneratorTest {
    @Test
    fun `valid multimodal response completes without a text retry`() = runTest {
        val client = FakeClient(imageSupported = true, imageResponse = VALID_RESPONSE)
        val generator = CaptureEnrichmentGenerator(client)

        val result = generator.generate(evidence(), image())

        assertThat(result?.classification).isEqualTo(Classification.RECEIPT)
        assertThat(client.imagePrompts).hasSize(1)
        assertThat(client.textPrompts).isEmpty()
        assertThat(client.imagePrompts.single()).contains("An image is attached")
    }

    @Test
    fun `invalid image response retries with truthful text-only prompt`() = runTest {
        val client = FakeClient(
            imageSupported = true,
            imageResponse = "not json",
            textResponse = VALID_RESPONSE
        )
        val generator = CaptureEnrichmentGenerator(client)

        val result = generator.generate(evidence(), image())

        assertThat(result?.classification).isEqualTo(Classification.RECEIPT)
        assertThat(client.imagePrompts).hasSize(1)
        assertThat(client.textPrompts).hasSize(1)
        assertThat(client.textPrompts.single()).contains("No image is attached")
    }

    @Test
    fun `unsupported image capability goes directly to OCR-only generation`() = runTest {
        val client = FakeClient(imageSupported = false, textResponse = VALID_RESPONSE)
        val generator = CaptureEnrichmentGenerator(client)

        val result = generator.generate(evidence(), image())

        assertThat(result).isNotNull()
        assertThat(client.imagePrompts).isEmpty()
        assertThat(client.textPrompts).hasSize(1)
    }

    @Test
    fun `corrected classification completes in one bounded pass`() = runTest {
        val response = """{"classification":"BOOK","subtype":"self-help book","system_facets":["MEDIA"],"title":"Atomic Habits","summary":"Book by James Clear","highlights":["Author: James Clear"],"topics":["habits"],"entities":["James Clear"],"tags":["James Clear"],"metadata":{"author":"James Clear"},"supported_actions":[],"suggestions":[],"confidence":0.95}"""
        val client = FakeClient(
            imageSupported = false,
            textResponse = response
        )
        val generator = CaptureEnrichmentGenerator(client)

        val result = generator.generate(
            CapturePromptEvidence(
                ocrText = "Atomic Habits by James Clear",
                classificationHint = Classification.GENERAL_DOCUMENT
            ),
            image = null
        )

        assertThat(client.textPrompts).hasSize(1)
        assertThat(result?.metadata?.get("author")).isEqualTo("James Clear")
        assertThat(result?.highlights).containsExactly("Author: James Clear")
    }

    @Test(expected = CancellationException::class)
    fun `foreground cancellation is never converted into fallback work`() = runTest {
        val client = FakeClient(imageSupported = true, cancelImage = true)
        CaptureEnrichmentGenerator(client).generate(evidence(), image())
    }

    private fun evidence() = CapturePromptEvidence(
        ocrText = "Fresh Mart TOTAL 42 USD",
        classificationHint = Classification.RECEIPT
    )

    private fun image() = checkNotNull(LlmImageInput.fromEncodedBytes(byteArrayOf(1, 2, 3)))

    private class FakeClient(
        private val imageSupported: Boolean,
        private val imageResponse: String? = null,
        private val textResponse: String? = null,
        private val cancelImage: Boolean = false,
        private val textResponses: List<String?>? = null
    ) : LlmClient {
        val textPrompts = mutableListOf<String>()
        val imagePrompts = mutableListOf<String>()

        override suspend fun generate(prompt: String, creative: Boolean): String? {
            textPrompts += prompt
            return textResponses?.getOrNull(textPrompts.lastIndex) ?: textResponse
        }

        override suspend fun generate(prompt: String, image: LlmImageInput, creative: Boolean): String? {
            imagePrompts += prompt
            if (cancelImage) throw CancellationException("foreground stopped")
            return imageResponse
        }

        override fun generateStream(prompt: String, creative: Boolean): Flow<String> = emptyFlow()
        override fun isAvailable(): Boolean = true
        override fun supportsImageInput(): Boolean = imageSupported
    }

    private companion object {
        const val VALID_RESPONSE =
            "{\"classification\":\"RECEIPT\",\"subtype\":\"store receipt\",\"system_facets\":[\"MONEY\"],\"title\":\"Fresh Mart Receipt\",\"summary\":\"USD 42 total\",\"metadata\":{\"total\":\"42\",\"currency\":\"USD\"},\"supported_actions\":[],\"suggestions\":[],\"confidence\":0.9}"
    }
}
