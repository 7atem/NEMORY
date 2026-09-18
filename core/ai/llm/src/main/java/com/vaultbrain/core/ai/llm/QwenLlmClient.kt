package com.vaultbrain.core.ai.llm

import android.content.Context
import android.util.Log
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.core.ai.llm.llama.LlamaBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 2 on-device VLM: Qwen3-VL-2B (GGUF Q4_K_M + Q8_0 mmproj) via [LlamaBridge].
 *
 * Supports both text and image input. Uses background-safe thread pools and handles
 * native memory cleanup.
 */
@Singleton
class QwenLlmClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: OnDeviceModelManager
) : LlmClient {
    override val modelVersion: String get() = "qwen3-vl-2b:${BuildConfig.QWEN_MODEL_SHA256}"

    private val nativeMutex = Mutex()

    @Volatile
    private var nativeHandle: Long = 0L

    override fun isAvailable(): Boolean =
        LlamaBridge.isAvailable() && modelManager.status.value == OnDeviceModelStatus.READY

    override fun supportsImageInput(): Boolean = true

    override suspend fun warmup(): Boolean {
        if (!isAvailable()) return false
        return try {
            ensureLoaded()
            nativeHandle != 0L
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.w(TAG, "Qwen3-VL warmup failed", t)
            false
        }
    }

    /**
     * Signals any running background inference to abort early and yield nativeMutex.
     */
    fun preemptBackground() {
        isPreemptRequested = true
    }

    override suspend fun generate(prompt: String, creative: Boolean): String? {
        return generateInternal(prompt = prompt, image = null, creative = creative, isInteractive = true)?.let(ModelOutput::visible)
    }

    override suspend fun generateForTask(prompt: String, budget: ReasoningBudget): String? =
        kotlinx.coroutines.withTimeoutOrNull(budget.timeoutMillis) {
            generateInternal(
                prompt,
                null,
                false,
                budget.maxTokens,
                isInteractive = budget != ReasoningBudget.FAST
            )?.let(ModelOutput::visible)
        }

    override suspend fun generateForTask(prompt: String, image: LlmImageInput, budget: ReasoningBudget): String? =
        kotlinx.coroutines.withTimeoutOrNull(budget.timeoutMillis) {
            generateInternal(
                prompt,
                image,
                false,
                budget.maxTokens,
                isInteractive = budget != ReasoningBudget.FAST
            )?.let(ModelOutput::visible)
        }

    override suspend fun generate(prompt: String, image: LlmImageInput, creative: Boolean): String? {
        return generateInternal(prompt = prompt, image = image, creative = creative, isInteractive = true)?.let(ModelOutput::visible)
    }

    private suspend fun generateInternal(
        prompt: String,
        image: LlmImageInput?,
        creative: Boolean,
        maxTokens: Int = ReasoningBudget.NORMAL.maxTokens,
        isInteractive: Boolean = false,
        onPartial: ((String) -> Unit)? = null
    ): String? {
        if (prompt.isBlank()) return null
        if (!isAvailable()) return null

        if (isInteractive && nativeMutex.isLocked) {
            isPreemptRequested = true
        }

        return withContext(Dispatchers.Default) {
            try {
                if (!warmup()) return@withContext null
                nativeMutex.withLock {
                    if (isInteractive) {
                        isPreemptRequested = false
                    }
                    val handle = nativeHandle
                    if (handle == 0L) return@withLock null

                    val temp = if (creative) CREATIVE_TEMP else STANDARD_TEMP
                    var tokenCount = 0
                    var firstTokenTime = 0L
                    val startTime = System.currentTimeMillis()
                    
                    val result = LlamaBridge.nativeGenerate(
                        handle = handle,
                        prompt = prompt,
                        jpegBytes = image?.encodedBytes,
                        maxTokens = maxTokens,
                        temperature = temp,
                        topK = TOP_K,
                        callback = com.vaultbrain.core.ai.llm.llama.LlamaTokenCallback { piece ->
                            if (piece.isNotEmpty() && tokenCount == 0) {
                                firstTokenTime = System.currentTimeMillis()
                            }
                            if (piece.isNotEmpty()) {
                                tokenCount++
                                onPartial?.invoke(piece)
                            }
                            coroutineContext.isActive && (!isPreemptRequested || isInteractive)
                        }
                    )
                    
                    val endTime = System.currentTimeMillis()
                    val ttft = if (firstTokenTime > 0) firstTokenTime - startTime else 0
                    val genTime = if (firstTokenTime > 0) endTime - firstTokenTime else 0
                    val tps = if (genTime > 0) (tokenCount.toFloat() / genTime) * 1000f else 0f
                    
                    Log.i(TAG, "Qwen Metrics | TTFT: ${ttft}ms | Tokens: $tokenCount | Gen Time: ${genTime}ms | Speed: ${String.format("%.2f", tps)} tok/s")
                    
                    result
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.w(TAG, "Qwen3-VL generation failed", t)
                null
            }
        }
    }

    override fun generateStream(prompt: String, creative: Boolean): Flow<String> {
        return channelFlow {
            var emitted = ""
            fun publish(raw: String, complete: Boolean = false) {
                val visible = if (complete) ModelOutput.visible(raw) else ModelOutput.streamingVisible(raw)
                if (visible.startsWith(emitted) && visible.length > emitted.length) {
                    trySend(visible.substring(emitted.length))
                    emitted = visible
                }
            }
            kotlinx.coroutines.withTimeoutOrNull(ReasoningBudget.NORMAL.timeoutMillis) {
                generateInternal(prompt, null, creative, onPartial = { publish(it) }, isInteractive = true)?.let { publish(it, true) }
            }
        }.buffer(Channel.UNLIMITED)
    }

    private fun ensureLoaded() {
        if (nativeHandle != 0L) return
        synchronized(this) {
            if (nativeHandle != 0L) return
            val modelPath = modelManager.modelPath
            val mmprojPath = modelManager.mmprojPath
            
            val t0 = System.currentTimeMillis()
            if (modelPath.isNotBlank() && java.io.File(modelPath).exists()) {
                val handle = LlamaBridge.nativeLoad(
                    modelPath = modelPath,
                    mmprojPath = mmprojPath,
                    ctxSize = CONTEXT_SIZE,
                    nThreads = DEFAULT_THREADS
                )
                if (handle != 0L) {
                    nativeHandle = handle
                    Log.i(TAG, "Native Qwen3-VL model loaded successfully in ${System.currentTimeMillis() - t0}ms")
                } else {
                    Log.e(TAG, "LlamaBridge.nativeLoad returned 0 after ${System.currentTimeMillis() - t0}ms")
                    modelManager.markError()
                }
            }
        }
    }

    /**
     * Releases native llama.cpp resources (~2 GB). Called when the model is deleted
     * via [OnDeviceModelManager.deleteModel] or when the app no longer needs inference.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun release() {
        synchronized(this) {
            val handle = nativeHandle
            if (handle != 0L) {
                nativeHandle = 0L
                LlamaBridge.nativeFree(handle)
                Log.i(TAG, "Native Qwen3-VL resources released")
            }
        }
    }

    private companion object {
        const val TAG = "QwenLlmClient"
        const val CONTEXT_SIZE = 4096
        const val DEFAULT_THREADS = 4
        const val MAX_TOKENS = 4096

        const val STANDARD_TEMP = 0.1f
        const val CREATIVE_TEMP = 0.8f
        const val TOP_K = 40

        @Volatile
        var isPreemptRequested = false
    }
}
