package com.vaultbrain.core.ai.llm

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over on-device large language model providers.
 */
interface LlmClient {

    /**
     * Optionally prepares a local provider before its first request. Implementations that do
     * not need preparation keep the default no-op behavior.
     */
    suspend fun warmup(): Boolean = false

    /**
     * Generates a response for the given [prompt]. Returns null if the model is unavailable
     * or generation fails.
     */
    suspend fun generate(prompt: String): String?

    /**
     * Generates from combined image and text evidence when the provider supports it. The default
     * preserves the text-only behavior for non-multimodal providers.
     */
    suspend fun generate(prompt: String, image: LlmImageInput): String? = null

    /**
     * Streams a response for the given [prompt]. Returns a Flow of string chunks.
     */
    fun generateStream(prompt: String): Flow<String>

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
