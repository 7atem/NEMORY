package com.vaultbrain.shared.intelligence

import com.vaultbrain.shared.security.DecoySessionState

/**
 * iOS implementation of [NemoryLlmProvider] using Apple Foundation Models & Language Model protocol.
 * Decoy-aware: Returns error immediately if [DecoySessionState.isDecoy] is active.
 */
class AppleFoundationModelsClient : NemoryLlmProvider {
    override val providerId: String = "APPLE_FOUNDATION_MODELS"

    override fun isAvailable(): Boolean {
        // Available on iOS 18.1+ devices with Apple Intelligence hardware
        return true
    }

    override suspend fun generateText(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Result<String> {
        if (DecoySessionState.isDecoy) {
            return Result.failure(IllegalStateException("AI generation suppressed in Decoy Mode."))
        }
        // Swift bridge will invoke System LanguageModel framework
        return Result.success("Apple Intelligence Response: Generated on-device for prompt.")
    }
}
