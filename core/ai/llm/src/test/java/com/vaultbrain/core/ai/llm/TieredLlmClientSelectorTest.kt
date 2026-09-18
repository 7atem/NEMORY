package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TieredLlmClientSelectorTest {

    @Test
    fun `downloaded gemma becomes primary text engine`() = runTest {
        val nano = FakeLlmClient(name = "nano", available = true)
        val gemma = FakeLlmClient(name = "gemma", available = true)
        val selector = TieredLlmClientSelector(nano, gemma)

        assertThat(selector.isAvailable()).isTrue()
        assertThat(selector.generate("prompt")).isEqualTo("gemma: prompt")
        assertThat(nano.generateCalls).isEqualTo(0)
        assertThat(gemma.generateCalls).isEqualTo(1)
    }

    @Test
    fun `nano unavailable falls back to ready gemma`() = runTest {
        val nano = FakeLlmClient(name = "nano", available = false)
        val gemma = FakeLlmClient(name = "gemma", available = true)
        val selector = TieredLlmClientSelector(nano, gemma)

        assertThat(selector.isAvailable()).isTrue()
        assertThat(selector.generate("prompt")).isEqualTo("gemma: prompt")
        assertThat(selector.generateStream("prompt").toList()).containsExactly("gemma: prompt")
    }

    @Test
    fun `both unavailable yields null and empty stream`() = runTest {
        val selector = TieredLlmClientSelector(
            FakeLlmClient(name = "nano", available = false),
            FakeLlmClient(name = "gemma", available = false)
        )

        assertThat(selector.isAvailable()).isFalse()
        assertThat(selector.generate("prompt")).isNull()
        assertThat(selector.generateStream("prompt").toList()).isEmpty()
    }

    @Test
    fun `gemma unavailable uses ready nano`() = runTest {
        val nano = FakeLlmClient(name = "nano", available = true)
        val gemma = FakeLlmClient(name = "gemma", available = false)
        val selector = TieredLlmClientSelector(nano, gemma)

        assertThat(selector.generate("prompt")).isEqualTo("nano: prompt")
        assertThat(gemma.generateCalls).isEqualTo(0)
    }

    @Test
    fun `nano remains the image engine while gemma is primary for text`() = runTest {
        val nano = FakeLlmClient(name = "nano", available = true, imageInput = true)
        val gemma = FakeLlmClient(name = "gemma", available = true)
        val selector = TieredLlmClientSelector(nano, gemma)
        val image = checkNotNull(LlmImageInput.fromEncodedBytes(byteArrayOf(1, 2, 3)))

        assertThat(selector.generate("text")).isEqualTo("gemma: text")
        assertThat(selector.generate("photo", image)).isEqualTo("nano-image: photo")
        assertThat(selector.supportsImageInput()).isTrue()
    }

    @Test
    fun `availability is evaluated per call`() = runTest {
        val nano = FakeLlmClient(name = "nano", available = false)
        val gemma = FakeLlmClient(name = "gemma", available = false)
        val selector = TieredLlmClientSelector(nano, gemma)

        assertThat(selector.generate("prompt")).isNull()
        gemma.available = true
        assertThat(selector.generate("prompt")).isEqualTo("gemma: prompt")
        nano.available = true
        assertThat(selector.generate("prompt")).isEqualTo("gemma: prompt")
    }

    private class FakeLlmClient(
        private val name: String,
        var available: Boolean,
        private val imageInput: Boolean = false
    ) : LlmClient {
        var generateCalls = 0

        override suspend fun generate(prompt: String, creative: Boolean): String? {
            generateCalls++
            return "$name: $prompt"
        }

        override suspend fun generate(prompt: String, image: LlmImageInput, creative: Boolean): String? =
            if (imageInput) "$name-image: $prompt" else null

        override fun generateStream(prompt: String, creative: Boolean): Flow<String> =
            if (available) flowOf("$name: $prompt") else emptyFlow()

        override fun isAvailable(): Boolean = available

        override fun supportsImageInput(): Boolean = imageInput
    }
}
