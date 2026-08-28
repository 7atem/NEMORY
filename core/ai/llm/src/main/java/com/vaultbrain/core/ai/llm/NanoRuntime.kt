package com.vaultbrain.core.ai.llm

import kotlinx.coroutines.flow.Flow

/** Small testable boundary around the beta ML Kit Prompt API. */
interface NanoRuntime {
    suspend fun status(): OnDeviceModelStatus
    suspend fun baseModelName(): String?
    suspend fun tokenLimit(): Int?
    suspend fun warmup()
    suspend fun generate(prompt: String): String?
    suspend fun generate(prompt: String, image: LlmImageInput): String? = null
    fun generateStream(prompt: String): Flow<String>
    fun download(): Flow<OnDeviceModelStatus>
    fun supportsImageInput(): Boolean = false
}

fun interface AppForegroundChecker {
    fun isAppInForeground(): Boolean
}
