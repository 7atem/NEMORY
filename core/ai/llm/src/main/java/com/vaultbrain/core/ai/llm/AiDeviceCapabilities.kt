package com.vaultbrain.core.ai.llm

/** Stable app-owned view of the on-device GenAI runtime. */
data class AiDeviceCapabilities(
    val localPromptAvailable: Boolean = false,
    val modelStatus: OnDeviceModelStatus = OnDeviceModelStatus.UNKNOWN,
    val modelName: String? = null,
    val tokenLimit: Int? = null,
    val imageInputAvailable: Boolean = false,
    val structuredOutputAvailable: Boolean = false
)

enum class OnDeviceModelStatus {
    UNKNOWN,
    UNAVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    READY,
    ERROR
}
