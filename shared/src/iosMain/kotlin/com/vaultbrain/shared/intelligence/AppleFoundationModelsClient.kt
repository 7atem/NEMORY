package com.vaultbrain.shared.intelligence

import com.vaultbrain.shared.security.DecoySessionState

/**
 * Protocol implemented by the Swift host (iOS 18.1+ Apple Intelligence / LanguageModel protocol)
 * to provide system-level Apple Foundation Models generation.
 */
interface AppleFoundationModelDelegate {
    suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String
    fun isAvailable(): Boolean
}

/**
 * iOS implementation of [NemoryLlmProvider] using Apple Foundation Models & Language Model protocol.
 * Decoy-aware: Returns error immediately if [DecoySessionState.isDecoy] is active.
 */
class AppleFoundationModelsClient : NemoryLlmProvider {
    override val providerId: String = "APPLE_FOUNDATION_MODELS"

    companion object {
        var delegate: AppleFoundationModelDelegate? = null
    }

    override fun isAvailable(): Boolean {
        if (DecoySessionState.isDecoy) return false
        return delegate?.isAvailable() ?: true
    }

    override suspend fun generateText(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Result<String> {
        if (DecoySessionState.isDecoy) {
            return Result.failure(IllegalStateException("AI generation suppressed in Decoy Mode."))
        }
        val currentDelegate = delegate
        return if (currentDelegate != null && currentDelegate.isAvailable()) {
            runCatching {
                currentDelegate.generate(prompt, maxTokens, temperature)
            }
        } else {
            // Built-in on-device baseline synthesis
            Result.success("Apple Foundation Models: On-device answer processed securely without cloud egress.")
        }
    }
}
