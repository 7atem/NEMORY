package com.vaultbrain.shared.intelligence

/**
 * Unified protocol abstraction for on-device and cloud LLM execution.
 *
 * On iOS: Implements Apple Foundation Models Framework & Language Model protocol / PCC.
 * On Android: Implements Gemini Nano / Qwen3-VL on-device GGUF inference.
 */
interface NemoryLlmProvider {
    /** Unique identifier for the provider implementation. */
    val providerId: String

    /** Returns true if the model runtime is available and ready on the device. */
    fun isAvailable(): Boolean

    /** Generates a text response for the given prompt under decoy-aware restrictions. */
    suspend fun generateText(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Result<String>
}
