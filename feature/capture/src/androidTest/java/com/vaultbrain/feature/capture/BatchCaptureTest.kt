package com.vaultbrain.feature.capture

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BatchCaptureTest {

    @Test
    fun processAllDownloadImages() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assets = context.assets
        val images = assets.list("test_images/Download") ?: emptyArray()

        Log.i(TAG, "Found ${images.size} images to process")

        for (imageName in images) {
            if (!imageName.endsWith(".jpg") && !imageName.endsWith(".jpeg") && !imageName.endsWith(".png")) {
                continue
            }

            Log.i(TAG, "\n=========================================\nPROCESSING: $imageName\n=========================================")

            val inputStream = assets.open("test_images/Download/$imageName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode $imageName")
                continue
            }

            try {
                val recognizedText = runBlocking { recognizer.recognizeBestDocument(bitmap) }
                Log.i(TAG, "--- OCR RESULT ---")
                Log.i(TAG, "\n$recognizedText")

                val extractionResult = extractor.extract(recognizedText)
                Log.i(TAG, "--- EXTRACTION ---")
                Log.i(TAG, "Tags: ${extractionResult.lensTags}")
                Log.i(TAG, "Metadata:")
                extractionResult.metadata.forEach { (key, value) ->
                    Log.i(TAG, "  $key: $value")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $imageName", e)
            } finally {
                bitmap.recycle()
            }
        }
    }

    companion object {
        private const val TAG = "BatchCaptureTest"
        private val extractor = HeuristicExtractor()
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        @JvmStatic
        @AfterClass
        fun teardown() {
            recognizer.close()
        }
    }
}
