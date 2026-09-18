package com.vaultbrain.feature.capture

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vaultbrain.core.ai.llm.CaptureEnrichmentPrompt
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.llm.QwenLlmClient
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelManager
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmDeepRetest {

    @Test
    fun processAllDownloadImages() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = context.assets
        val images = assets.list("test_images/Download") ?: emptyArray()

        Log.i(TAG, "Found ${images.size} images to process for LLM Benchmarking")

        // Setup the Local LLM Client Bypass (identical to new CaptureViewModel architecture)
        val modelManager = OnDeviceModelManager(targetContext)
        
        if (modelManager.status.value != com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus.READY) {
            Log.e(TAG, "Gemma Model is not downloaded! Test cannot proceed. Please open the VaultBrain app and ensure the 1B model downloads first.")
            return
        }

        val llmClient = QwenLlmClient(targetContext, modelManager)
        
        // This takes ~5-10s to load into memory
        runBlocking { llmClient.warmup() }

        val llmMs = mutableListOf<Long>()

        for (imageName in images) {
            if (!imageName.endsWith(".jpg") && !imageName.endsWith(".jpeg") && !imageName.endsWith(".png")) {
                continue
            }

            Log.i(TAG, "\n=========================================\nLLM PROCESSING: $imageName\n=========================================")

            val inputStream = assets.open("test_images/Download/$imageName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode $imageName")
                continue
            }

            try {
                // 1. OCR (Bypassing Heuristics)
                val recognizedText = runBlocking { recognizer.recognizeBestDocument(bitmap) }
                
                // 2. Generate Evidence for Prompt
                val evidence = CapturePromptEvidence(
                    ocrText = recognizedText,
                    labels = emptyList(),
                    scoredLabels = emptyList(),
                    barcodes = emptyList(),
                    hasImageInput = false
                )
                
                val prompt = CaptureEnrichmentPrompt.build(evidence)

                // 3. Query LLM directly without heuristic pollution
                val start = System.nanoTime()
                val llmResponse = runBlocking { llmClient.generate(prompt) }
                val llmTime = (System.nanoTime() - start) / 1_000_000
                
                Log.i(TAG, "--- LLM GENERATED RESULT ---")
                Log.i(TAG, "\n$llmResponse")

                llmMs += llmTime
                Log.i(TAG, "TIMING $imageName: llmTime=${llmTime}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $imageName through LLM", e)
            } finally {
                bitmap.recycle()
            }
        }

        logTimingSummary("LLM_ENRICHMENT", llmMs)
    }

    private fun logTimingSummary(stage: String, samples: List<Long>) {
        if (samples.isEmpty()) return
        val sorted = samples.sorted()
        Log.i(
            TAG,
            "TIMING_SUMMARY $stage: n=${samples.size} avg=${samples.average().toLong()}ms " +
                "p50=${sorted[sorted.size / 2]}ms max=${sorted.last()}ms"
        )
    }

    companion object {
        private const val TAG = "LlmDeepRetest"
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        @JvmStatic
        @AfterClass
        fun teardown() {
            recognizer.close()
        }
    }
}
