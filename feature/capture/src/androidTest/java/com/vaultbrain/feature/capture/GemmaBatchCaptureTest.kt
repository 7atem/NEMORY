package com.vaultbrain.feature.capture

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.core.ai.llm.QwenLlmClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resumeWithException
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaBatchCaptureTest {

    @Test
    fun testGemmaExtraction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Log.i(TAG, "Files Dir: ${context.filesDir.absolutePath}")

        val gemmaManager = OnDeviceModelManager(context)
        Log.i(TAG, "Gemma initial status: ${gemmaManager.status.value}")

        // Wait up to 60 seconds for status to become READY (since hash verification takes ~25 seconds)
        try {
            withTimeout(60_000) {
                gemmaManager.status.first {
                    Log.i(TAG, "Status observed: $it")
                    it == OnDeviceModelStatus.READY || it == OnDeviceModelStatus.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timed out waiting for Gemma initialization")
        }

        if (gemmaManager.status.value != OnDeviceModelStatus.READY) {
            Log.e(TAG, "Failed to initialize Gemma. Exiting test.")
            return@runBlocking
        }

        val gemmaClient = QwenLlmClient(context, gemmaManager)
        val generator = CaptureEnrichmentGenerator(gemmaClient)

        // Warm up Gemma
        Log.i(TAG, "Warming up Gemma...")
        generator.warmup()
        Log.i(TAG, "Gemma warmed up.")

        val testImages = context.assets.list("test_images/Download")?.toList()?.filter {
            it.endsWith(".jpg", ignoreCase = true) ||
            it.endsWith(".jpeg", ignoreCase = true) ||
            it.endsWith(".png", ignoreCase = true)
        } ?: emptyList()

        val assets = context.assets
        for (imageName in testImages) {
            Log.i(TAG, "\n=========================================\nPROCESSING: $imageName\n=========================================")
            val inputStream = assets.open("test_images/Download/$imageName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode $imageName")
                continue
            }

            try {
                // Ensure we are calling the extension function recognizeBestDocument which we assume exists from the codebase
                val ocrText = recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)).awaitExt().text
                val heuristicResult = extractor.extract(ocrText)

                Log.i(TAG, "--- HEURISTIC EXTRACTION ---")
                Log.i(TAG, "Classification: ${heuristicResult.inferredClassification}")
                Log.i(TAG, "Tags: ${heuristicResult.lensTags}")
                heuristicResult.metadata.forEach { (key, value) -> Log.i(TAG, "  $key: $value") }

                Log.i(TAG, "--- GEMMA EXTRACTION ---")
                val evidence = CapturePromptEvidence(
                    ocrText = ocrText,
                    classificationHint = heuristicResult.inferredClassification,
                    systemFacetHints = heuristicResult.lensTags,
                    existingMetadata = heuristicResult.metadata
                )
                val gemmaResult = generator.generate(evidence, null)
                if (gemmaResult != null) {
                    Log.i(TAG, "Title: ${gemmaResult.title}")
                    Log.i(TAG, "Summary: ${gemmaResult.summary}")
                    gemmaResult.metadata.forEach { (key, value) -> Log.i(TAG, "  $key: $value") }
                } else {
                    Log.e(TAG, "Gemma enrichment returned null.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $imageName", e)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitExt(): T =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) {} }
            addOnFailureListener { cont.resumeWithException(it) }
        }

    companion object {
        private const val TAG = "GemmaBatchTest"
        private val extractor = HeuristicExtractor()
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        @JvmStatic
        @AfterClass
        fun teardown() {
            recognizer.close()
        }
    }
}
