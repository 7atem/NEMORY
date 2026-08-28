package com.vaultbrain.core.ai.llm

import javax.inject.Inject
import javax.inject.Singleton

/** Operations that may benefit from cloud AI after local generation is unavailable. */
enum class CloudAiOperation {
    TRANSLATE_DOCUMENT,
    EXPLAIN_RETRIEVED_EVIDENCE
}

enum class AiResponseOrigin {
    ON_DEVICE_AI,
    CLOUD_AI
}

enum class CloudRuntimeStatus {
    NOT_CONFIGURED,
    READY
}

/**
 * The complete, already-redacted request that a cloud provider is allowed to receive.
 *
 * Callers must not put item IDs, image bytes, hidden metadata, or unrelated vault context here.
 */
data class CloudAiRequest(
    val operation: CloudAiOperation,
    val prompt: String
)

/** Narrow boundary around the optional cloud SDK. */
interface CloudAiRuntime {
    val status: CloudRuntimeStatus

    suspend fun generate(request: CloudAiRequest): String?
}

/**
 * Production-safe default until Firebase AI Logic, App Check, quotas, and Play Console
 * registration are configured. This implementation never opens a network connection.
 */
@Singleton
class DisabledCloudAiRuntime @Inject constructor() : CloudAiRuntime {
    override val status: CloudRuntimeStatus = CloudRuntimeStatus.NOT_CONFIGURED

    override suspend fun generate(request: CloudAiRequest): String? = null
}
