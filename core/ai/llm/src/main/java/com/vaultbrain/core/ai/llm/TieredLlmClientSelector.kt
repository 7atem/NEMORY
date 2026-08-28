package com.vaultbrain.core.ai.llm

import com.vaultbrain.core.ai.llm.di.Gemma
import com.vaultbrain.core.ai.llm.di.Nano
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [LlmClient]: per-call tier selection between Gemini Nano (Tier 1, foreground-gated)
 * and Gemma (Tier 2, background-safe).
 *
 * Delegation happens per call rather than once at injection because Nano can become READY
 * mid-session (AICore download finishing) and Gemma can become READY after a model download.
 */
@Singleton
class TieredLlmClientSelector @Inject constructor(
    @Nano private val nano: LlmClient,
    @Gemma private val gemma: LlmClient
) : LlmClient {

    /** A user-downloaded Gemma model becomes the primary text engine across the app. */
    private fun activeTextClient(): LlmClient? = when {
        gemma.isAvailable() -> gemma
        nano.isAvailable() -> nano
        else -> null
    }

    /** Gemma is text-only, so retain Nano multimodal input where the device supports it. */
    private fun activeImageClient(): LlmClient? = when {
        nano.isAvailable() && nano.supportsImageInput() -> nano
        gemma.isAvailable() && gemma.supportsImageInput() -> gemma
        else -> null
    }

    override suspend fun warmup(): Boolean =
        gemma.warmup() || nano.warmup()

    override suspend fun generate(prompt: String): String? =
        activeTextClient()?.generate(prompt)

    override suspend fun generate(prompt: String, image: LlmImageInput): String? =
        activeImageClient()?.generate(prompt, image)

    override fun generateStream(prompt: String): Flow<String> =
        activeTextClient()?.generateStream(prompt) ?: emptyFlow()

    override fun isAvailable(): Boolean = nano.isAvailable() || gemma.isAvailable()

    override fun supportsImageInput(): Boolean =
        activeImageClient() != null
}
