package com.vaultbrain.core.ai.llm

import com.vaultbrain.core.ai.llm.di.Gemma
import com.vaultbrain.core.ai.llm.di.Nano
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [LlmClient]: per-call tier selection between Gemini Nano (Tier 1, foreground-gated)
 * and Qwen3-VL On-Device VLM (Tier 2, background-safe).
 */
@Singleton
class TieredLlmClientSelector @Inject constructor(
    @Nano private val nano: LlmClient,
    @Gemma private val onDevice: LlmClient
) : LlmClient {
    override val modelVersion: String get() = activeTextClient()?.modelVersion ?: "unavailable"

    private fun activeTextClient(): LlmClient? = when {
        onDevice.isAvailable() -> onDevice
        nano.isAvailable() -> nano
        else -> null
    }

    private fun activeImageClient(): LlmClient? = when {
        onDevice.isAvailable() && onDevice.supportsImageInput() -> onDevice
        nano.isAvailable() && nano.supportsImageInput() -> nano
        else -> null
    }

    override suspend fun warmup(): Boolean =
        onDevice.warmup() || nano.warmup()

    override suspend fun generate(prompt: String, creative: Boolean): String? =
        activeTextClient()?.generate(prompt, creative)

    override suspend fun generateForTask(prompt: String, budget: ReasoningBudget): String? =
        activeTextClient()?.generateForTask(prompt, budget)

    override suspend fun generateForTask(prompt: String, image: LlmImageInput, budget: ReasoningBudget): String? =
        activeImageClient()?.generateForTask(prompt, image, budget)

    override suspend fun generate(prompt: String, image: LlmImageInput, creative: Boolean): String? =
        activeImageClient()?.generate(prompt, image, creative)

    override fun generateStream(prompt: String, creative: Boolean): Flow<String> =
        activeTextClient()?.generateStream(prompt, creative) ?: emptyFlow()

    override fun isAvailable(): Boolean = nano.isAvailable() || onDevice.isAvailable()

    override fun supportsImageInput(): Boolean =
        activeImageClient() != null
}
