package com.vaultbrain.core.ai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for runtime Gemini Nano capabilities. */
@Singleton
class AiCapabilityManager @Inject constructor(
    private val nanoClient: NanoPromptClient
) {
    private val _capabilities = MutableStateFlow(AiDeviceCapabilities())
    val capabilities: StateFlow<AiDeviceCapabilities> = _capabilities.asStateFlow()

    suspend fun refresh(): AiDeviceCapabilities = nanoClient.capabilities().also {
        _capabilities.value = it
    }

    fun downloadModel(): Flow<AiDeviceCapabilities> = nanoClient.downloadModel().map { status ->
        val updated = if (status == OnDeviceModelStatus.READY) {
            nanoClient.capabilities()
        } else {
            _capabilities.value.copy(
                localPromptAvailable = false,
                modelStatus = status,
                modelName = null,
                tokenLimit = null,
                imageInputAvailable = false,
                structuredOutputAvailable = false
            )
        }
        _capabilities.value = updated
        updated
    }
}
