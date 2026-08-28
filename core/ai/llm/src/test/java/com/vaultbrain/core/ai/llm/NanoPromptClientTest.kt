package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NanoPromptClientTest {

    @Test
    fun `foreground ready runtime generates locally`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.READY)
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        assertThat(client.generate("Summarize this")).isEqualTo("local: Summarize this")
        assertThat(runtime.warmupCalls).isEqualTo(1)
        assertThat(client.isAvailable()).isTrue()
    }

    @Test
    fun `background app never invokes runtime generation`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.READY)
        val client = NanoPromptClient(runtime, AppForegroundChecker { false })

        assertThat(client.generate("private document")).isNull()
        assertThat(runtime.generateCalls).isEqualTo(0)
    }

    @Test
    fun `unsupported runtime returns empty stream for deterministic fallback`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.UNAVAILABLE)
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        assertThat(client.generateStream("question").toList()).isEmpty()
    }

    @Test
    fun `capability snapshot contains model details only when ready`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.READY)
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        assertThat(client.capabilities()).isEqualTo(
            AiDeviceCapabilities(
                localPromptAvailable = true,
                modelStatus = OnDeviceModelStatus.READY,
                modelName = "nano-v3",
                tokenLimit = 4_000,
                imageInputAvailable = false,
                structuredOutputAvailable = false
            )
        )
    }

    @Test
    fun `ready image runtime advertises multimodal input`() = runTest {
        val runtime = FakeNanoRuntime(
            status = OnDeviceModelStatus.READY,
            imageInputSupported = true
        )
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        assertThat(client.capabilities().imageInputAvailable).isTrue()
        assertThat(client.supportsImageInput()).isTrue()
    }

    @Test
    fun `multimodal generation uses image and text in one runtime request`() = runTest {
        val runtime = FakeNanoRuntime(
            status = OnDeviceModelStatus.READY,
            imageInputSupported = true
        )
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })
        val image = checkNotNull(LlmImageInput.fromEncodedBytes(byteArrayOf(1, 2, 3)))

        assertThat(client.generate("Classify", image)).isEqualTo("image: Classify")
        assertThat(runtime.imageGenerateCalls).isEqualTo(1)
        assertThat(runtime.generateCalls).isEqualTo(0)
    }

    @Test
    fun `image failure returns null so caller can use an honest text prompt`() = runTest {
        val runtime = FakeNanoRuntime(
            status = OnDeviceModelStatus.READY,
            imageInputSupported = true,
            failImageGeneration = true
        )
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })
        val image = checkNotNull(LlmImageInput.fromEncodedBytes(byteArrayOf(1, 2, 3)))

        assertThat(client.generate("Classify", image)).isNull()
        assertThat(runtime.imageGenerateCalls).isEqualTo(1)
        assertThat(runtime.generateCalls).isEqualTo(0)
    }

    @Test
    fun `text-only runtime declines image request for caller fallback`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.READY)
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })
        val image = checkNotNull(LlmImageInput.fromEncodedBytes(byteArrayOf(1, 2, 3)))

        assertThat(client.generate("Classify", image)).isNull()
        assertThat(runtime.imageGenerateCalls).isEqualTo(0)
        assertThat(runtime.generateCalls).isEqualTo(0)
    }

    @Test
    fun `image input enforces bounded in-memory payloads`() {
        assertThat(LlmImageInput.fromEncodedBytes(byteArrayOf())).isNull()
        assertThat(
            LlmImageInput.fromEncodedBytes(ByteArray(LlmImageInput.MAX_BYTES + 1))
        ).isNull()
        assertThat(LlmImageInput.fromEncodedBytes(byteArrayOf(1))).isNotNull()
    }

    @Test
    fun `foreground ready runtime warms only once per process`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.READY)
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        assertThat(client.warmup()).isTrue()
        assertThat(client.warmup()).isTrue()
        assertThat(runtime.warmupCalls).isEqualTo(1)
    }

    @Test
    fun `background app never invokes runtime warmup`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.READY)
        val client = NanoPromptClient(runtime, AppForegroundChecker { false })

        assertThat(client.warmup()).isFalse()
        assertThat(runtime.warmupCalls).isEqualTo(0)
    }

    @Test
    fun `concurrent warmup requests share one runtime call`() = runTest {
        val runtime = FakeNanoRuntime(status = OnDeviceModelStatus.READY)
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        val results = List(5) { async { client.warmup() } }.awaitAll()

        assertThat(results).containsExactly(true, true, true, true, true)
        assertThat(runtime.warmupCalls).isEqualTo(1)
    }

    @Test
    fun `warmup failure is non-blocking and is not retried in the same process`() = runTest {
        val runtime = FakeNanoRuntime(
            status = OnDeviceModelStatus.READY,
            failWarmup = true
        )
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        assertThat(client.generate("first")).isEqualTo("local: first")
        assertThat(client.generate("second")).isEqualTo("local: second")
        assertThat(runtime.warmupCalls).isEqualTo(1)
    }

    @Test
    fun `concurrent consumers are serialized through one Nano request`() = runTest {
        val runtime = FakeNanoRuntime(
            status = OnDeviceModelStatus.READY,
            generationDelayMs = 10
        )
        val client = NanoPromptClient(runtime, AppForegroundChecker { true })

        List(4) { index -> async { client.generate("request $index") } }.awaitAll()

        assertThat(runtime.maxConcurrentGenerations).isEqualTo(1)
        assertThat(runtime.generateCalls).isEqualTo(4)
    }

    private class FakeNanoRuntime(
        private var status: OnDeviceModelStatus,
        private val failWarmup: Boolean = false,
        private val imageInputSupported: Boolean = false,
        private val failImageGeneration: Boolean = false,
        private val generationDelayMs: Long = 0
    ) : NanoRuntime {
        var generateCalls: Int = 0
        var imageGenerateCalls: Int = 0
        var warmupCalls: Int = 0
        var maxConcurrentGenerations: Int = 0
        private var activeGenerations: Int = 0

        override suspend fun status(): OnDeviceModelStatus = status
        override suspend fun baseModelName(): String = "nano-v3"
        override suspend fun tokenLimit(): Int = 4_000

        override suspend fun warmup() {
            warmupCalls += 1
            if (failWarmup) error("warmup failed")
        }

        override suspend fun generate(prompt: String): String {
            generateCalls += 1
            activeGenerations += 1
            maxConcurrentGenerations = maxOf(maxConcurrentGenerations, activeGenerations)
            return try {
                if (generationDelayMs > 0) delay(generationDelayMs)
                "local: $prompt"
            } finally {
                activeGenerations -= 1
            }
        }

        override suspend fun generate(prompt: String, image: LlmImageInput): String {
            imageGenerateCalls += 1
            if (failImageGeneration) error("image generation failed")
            return "image: $prompt"
        }

        override fun supportsImageInput(): Boolean = imageInputSupported

        override fun generateStream(prompt: String): Flow<String> = flowOf("local", " answer")

        override fun download(): Flow<OnDeviceModelStatus> = flowOf(
            OnDeviceModelStatus.DOWNLOADING,
            OnDeviceModelStatus.READY
        )
    }
}
