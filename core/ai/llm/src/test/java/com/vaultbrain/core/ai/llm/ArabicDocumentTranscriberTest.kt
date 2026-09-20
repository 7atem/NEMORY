package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArabicDocumentTranscriberTest {

    private val transcription = "إيصال استلام نقدي المبلغ ٤٥٠ جنيه مصري التاريخ ١٥ يناير ٢٠٢٥"
    private val extractionJson =
        """{"facts":[{"kind":"money","field":"amount","value":"٤٥٠","evidence":"المبلغ ٤٥٠ جنيه"}],"uncertainties":[]}"""

    @Test
    fun `verified transcription and verbatim facts are returned`() = runTest {
        val client = FakeClient(imageResponse = transcription, textResponse = extractionJson)

        val result = ArabicDocumentTranscriber(client).transcribe(image())

        assertThat(result).isNotNull()
        assertThat(result!!.transcription).isEqualTo(transcription)
        assertThat(result.understanding.facts).hasSize(1)
        assertThat(result.understanding.facts.single().value).isEqualTo("٤٥٠")
        assertThat(client.imagePrompts).hasSize(1)
        assertThat(client.textPrompts).hasSize(1)
    }

    @Test
    fun `malformed extraction json is rejected`() = runTest {
        val client = FakeClient(imageResponse = transcription, textResponse = "not json")

        assertThat(ArabicDocumentTranscriber(client).transcribe(image())).isNull()
    }

    @Test
    fun `fact values missing from the transcription are rejected`() = runTest {
        val fabricated =
            """{"facts":[{"kind":"money","field":"amount","value":"٩٩٩","evidence":"المبلغ ٤٥٠ جنيه"}],"uncertainties":[]}"""
        val client = FakeClient(imageResponse = transcription, textResponse = fabricated)

        assertThat(ArabicDocumentTranscriber(client).transcribe(image())).isNull()
    }

    @Test
    fun `empty or garbage transcription is rejected`() = runTest {
        val client = FakeClient(imageResponse = "…", textResponse = extractionJson)

        assertThat(ArabicDocumentTranscriber(client).transcribe(image())).isNull()
        assertThat(client.textPrompts).isEmpty()
    }

    @Test
    fun `unavailable client returns null without generating`() = runTest {
        val client = FakeClient(imageResponse = transcription, textResponse = extractionJson, available = false)

        assertThat(ArabicDocumentTranscriber(client).transcribe(image())).isNull()
        assertThat(client.imagePrompts).isEmpty()
    }

    @Test
    fun `client without image support returns null`() = runTest {
        val client = FakeClient(
            imageResponse = transcription,
            textResponse = extractionJson,
            imageSupported = false
        )

        assertThat(ArabicDocumentTranscriber(client).transcribe(image())).isNull()
        assertThat(client.imagePrompts).isEmpty()
    }

    @Test
    fun `provider failure is swallowed into a null result`() = runTest {
        val client = FakeClient(throwOnImage = true)

        assertThat(ArabicDocumentTranscriber(client).transcribe(image())).isNull()
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never swallowed`() = runTest {
        val client = FakeClient(cancelImage = true)
        ArabicDocumentTranscriber(client).transcribe(image())
    }

    @Test
    fun `applyTo replaces garbage ocr evidence and marks the item for review`() {
        val item = VaultItem(
            id = "item-1",
            title = "Captured item",
            summary = "||",
            rawOcrText = "||",
            sourceType = SourceType.CAMERA,
            parsedMetadata = mapOf("media_mime_type" to "image/jpeg"),
            needsReview = false
        )
        val result = ArabicVlTranscription(
            transcription = transcription,
            understanding = checkNotNull(DocumentUnderstandingV2.parse(extractionJson, transcription))
        )

        val merged = result.applyTo(item)

        assertThat(merged.rawOcrText).isEqualTo(transcription)
        assertThat(merged.summary).isEqualTo(transcription)
        assertThat(merged.title).isEqualTo(transcription.lineSequence().first())
        assertThat(merged.parsedMetadata["amount"]).isEqualTo("٤٥٠")
        // App-owned pipeline metadata is never overwritten by model output.
        assertThat(merged.parsedMetadata["media_mime_type"]).isEqualTo("image/jpeg")
        assertThat(merged.needsReview).isTrue()
    }

    private fun image() = checkNotNull(LlmImageInput.fromEncodedBytes(byteArrayOf(1, 2, 3)))

    private class FakeClient(
        private val imageResponse: String? = null,
        private val textResponse: String? = null,
        private val available: Boolean = true,
        private val imageSupported: Boolean = true,
        private val throwOnImage: Boolean = false,
        private val cancelImage: Boolean = false
    ) : LlmClient {
        val textPrompts = mutableListOf<String>()
        val imagePrompts = mutableListOf<String>()

        override suspend fun generate(prompt: String, creative: Boolean): String? {
            textPrompts += prompt
            return textResponse
        }

        override suspend fun generate(prompt: String, image: LlmImageInput, creative: Boolean): String? {
            imagePrompts += prompt
            if (cancelImage) throw CancellationException("foreground stopped")
            if (throwOnImage) throw RuntimeException("native bridge failed")
            return imageResponse
        }

        override fun generateStream(prompt: String, creative: Boolean): Flow<String> = emptyFlow()
        override fun isAvailable(): Boolean = available
        override fun supportsImageInput(): Boolean = imageSupported
    }
}
