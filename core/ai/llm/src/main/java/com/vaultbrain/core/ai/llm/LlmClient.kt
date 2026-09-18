package com.vaultbrain.core.ai.llm

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over on-device large language model providers.
 */
interface LlmClient {
    val modelVersion: String get() = "local-provider-unspecified"
    suspend fun generateForTask(prompt: String, budget: ReasoningBudget): String? =
        kotlinx.coroutines.withTimeoutOrNull(budget.timeoutMillis) {
            generate(prompt, false)?.let(ModelOutput::visible)
        }

    suspend fun generateForTask(prompt: String, image: LlmImageInput, budget: ReasoningBudget): String? =
        kotlinx.coroutines.withTimeoutOrNull(budget.timeoutMillis) {
            generate(prompt, image, false)?.let(ModelOutput::visible)
        }

    suspend fun warmup(): Boolean = false

    suspend fun generate(prompt: String, creative: Boolean = false): String?

    suspend fun generate(prompt: String, image: LlmImageInput, creative: Boolean = false): String? = null

    /**
     * Streams a response for the given [prompt]. Returns a Flow of string chunks.
     */
    fun generateStream(prompt: String, creative: Boolean = false): Flow<String>

    /**
     * Returns true if this client can be used on the current device right now.
     */
    fun isAvailable(): Boolean

    /** Whether this provider has an image-input request path. Runtime failures still fall back. */
    fun supportsImageInput(): Boolean = false
}

/** Encoded in-memory image evidence. It is never serialized, logged, or sent by this type. */
class LlmImageInput private constructor(bytes: ByteArray) {
    internal val encodedBytes: ByteArray = bytes.copyOf()
    val byteCount: Int get() = encodedBytes.size

    companion object {
        const val MAX_BYTES: Int = 4 * 1024 * 1024

        fun fromEncodedBytes(bytes: ByteArray): LlmImageInput? = bytes
            .takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
            ?.let(::LlmImageInput)
    }
}
