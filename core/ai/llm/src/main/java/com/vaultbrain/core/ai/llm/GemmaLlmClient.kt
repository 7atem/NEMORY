package com.vaultbrain.core.ai.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.GemmaModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 2 on-device LLM: Gemma 3 1B (int4) via MediaPipe [LlmInference].
 *
 * The model is downloaded on demand by [GemmaModelManager]; this client only loads it lazily
 * on first use. Unlike [NanoPromptClient] there is deliberately no foreground gate — this is
 * the tier usable from background workers such as `DeferredAnalysisWorker`.
 *
 * Any failure (including native crashes surfacing as errors) returns null and flips the
 * manager to ERROR so callers fall back to heuristic-only items silently.
 */
@Singleton
class GemmaLlmClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: GemmaModelManager
) : LlmClient {

    private val inferenceMutex = Mutex()

    @Volatile
    private var inference: LlmInference? = null

    override fun isAvailable(): Boolean =
        modelManager.status.value == GemmaModelStatus.READY

    override suspend fun warmup(): Boolean {
        if (!isAvailable()) return false
        return try {
            ensureLoaded()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.w(TAG, "Gemma warmup failed", t)
            modelManager.markError()
            false
        }
    }

    override suspend fun generate(prompt: String): String? {
        if (prompt.isBlank()) return null
        if (!warmup()) return null
        return withContext(Dispatchers.Default) {
            try {
                inferenceMutex.withLock { generateLocked(prompt) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.w(TAG, "Gemma generation failed", t)
                modelManager.markError()
                null
            }
        }
    }

    override fun generateStream(prompt: String): Flow<String> {
        if (prompt.isBlank()) return emptyFlow()
        return flow {
            generate(prompt)?.let { emit(it) }
        }
    }

    /** The Gemma 3 1B `.task` bundle is text-only. */
    override fun supportsImageInput(): Boolean = false

    private fun ensureLoaded() {
        inference?.let { return }
        synchronized(this) {
            inference?.let { return }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelManager.modelPath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            inference = LlmInference.createFromOptions(context, options)
        }
    }

    /** Runs one prompt in a fresh session so context never accumulates across captures. */
    private fun generateLocked(prompt: String): String? {
        val engine = inference ?: return null
        val session = LlmInferenceSession.createFromOptions(
            engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(TEMPERATURE)
                .setTopK(TOP_K)
                .build()
        )
        return try {
            session.addQueryChunk(prompt)
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    private companion object {
        const val TAG = "GemmaLlmClient"
        const val MAX_TOKENS = 2048

        /** Near-deterministic output for the JSON extraction contract. */
        const val TEMPERATURE = 0.1f
        const val TOP_K = 40
    }
}
