package com.vaultbrain.core.ai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Foreground-only Gemini Nano client backed by the ML Kit Prompt API. */
@Singleton
class NanoPromptClient @Inject constructor(
    private val runtime: NanoRuntime,
    private val foregroundChecker: AppForegroundChecker
) : LlmClient {

    @Volatile
    private var lastStatus: OnDeviceModelStatus = OnDeviceModelStatus.UNKNOWN

    @Volatile
    private var isWarmedUp: Boolean = false

    @Volatile
    private var warmupAttempted: Boolean = false

    private val warmupMutex = Mutex()
    private val generationMutex = Mutex()

    override fun isAvailable(): Boolean =
        lastStatus == OnDeviceModelStatus.READY && foregroundChecker.isAppInForeground()

    /** Warms Nano at most once per app process and never while Nemory is backgrounded. */
    override suspend fun warmup(): Boolean {
        if (!foregroundChecker.isAppInForeground()) return false
        return warmupMutex.withLock {
            if (warmupAttempted) return@withLock isWarmedUp
            if (!ensureReady()) return@withLock false
            try {
                runtime.warmup()
                warmupAttempted = true
                isWarmedUp = true
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Warmup is an optimization. A failure must not disable normal generation.
                warmupAttempted = true
                false
            }
        }
    }

    override suspend fun generate(prompt: String, creative: Boolean): String? {
        if (prompt.isBlank()) return null
        warmup()
        if (!ensureReady()) return null
        return generationMutex.withLock { generateTextSafely(prompt) }
    }

    /** Attempts one image request. The caller owns an honest OCR-only fallback prompt. */
    override suspend fun generate(prompt: String, image: LlmImageInput, creative: Boolean): String? {
        if (prompt.isBlank()) return null
        warmup()
        if (!ensureReady()) return null

        if (!runtime.supportsImageInput()) return null
        return generationMutex.withLock {
            try {
                runtime.generate(prompt, image)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun generateStream(prompt: String, creative: Boolean): Flow<String> {
        if (prompt.isBlank()) return emptyFlow()
        return flow {
            warmup()
            if (!ensureReady()) return@flow
            generationMutex.withLock {
                runtime.generateStream(prompt).collect(::emit)
            }
        }.catch {
            lastStatus = OnDeviceModelStatus.ERROR
        }
    }

    fun downloadModel(): Flow<OnDeviceModelStatus> {
        if (!foregroundChecker.isAppInForeground()) return emptyFlow()
        return runtime.download().onEach { lastStatus = it }
    }

    suspend fun capabilities(): AiDeviceCapabilities {
        val status = runCatching { runtime.status() }
            .getOrElse { OnDeviceModelStatus.ERROR }
        lastStatus = status
        val ready = status == OnDeviceModelStatus.READY
        return AiDeviceCapabilities(
            localPromptAvailable = ready,
            modelStatus = status,
            modelName = if (ready) runtime.baseModelName() else null,
            tokenLimit = if (ready) runtime.tokenLimit() else null,
            imageInputAvailable = ready && runtime.supportsImageInput(),
            structuredOutputAvailable = false
        )
    }

    override fun supportsImageInput(): Boolean = runtime.supportsImageInput()

    private suspend fun ensureReady(): Boolean {
        if (!foregroundChecker.isAppInForeground()) return false
        lastStatus = runCatching { runtime.status() }
            .getOrElse { OnDeviceModelStatus.ERROR }
        return lastStatus == OnDeviceModelStatus.READY
    }

    private suspend fun generateTextSafely(prompt: String): String? = try {
        runtime.generate(prompt)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
