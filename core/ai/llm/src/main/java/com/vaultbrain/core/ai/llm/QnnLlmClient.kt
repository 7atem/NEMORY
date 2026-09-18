package com.vaultbrain.core.ai.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of [LlmClient] backed by the Qualcomm QNN runtime / Snapdragon NPU.
 *
 * Real integration will load an ONNX model via ONNX Runtime with the QNN execution provider.
 * For now this client reports unavailable.
 */
@Singleton
class QnnLlmClient @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmClient {

    override fun isAvailable(): Boolean {
        // TODO: probe QNN / NPU availability.
        return false
    }

    override suspend fun generate(prompt: String, creative: Boolean): String? {
        if (!isAvailable()) return null
        return ""
    }

    override fun generateStream(prompt: String, creative: Boolean): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val fullResponse = generate(prompt) ?: return@flow
        emit(fullResponse)
    }
}
