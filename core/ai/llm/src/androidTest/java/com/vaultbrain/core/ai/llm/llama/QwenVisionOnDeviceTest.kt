package com.vaultbrain.core.ai.llm.llama

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * On-device benchmark for Qwen3-VL vision processing.
 *
 * This test iterates over a directory of representative images and feeds them
 * through the native llama.cpp bridge to measure performance and quality metrics
 * for the Nemory Intelligence V1 vision path.
 *
 * Preparation:
 * 1. Push model artifacts to: /sdcard/Android/data/com.vaultbrain.core.ai.llm.test/files/models/
 * 2. Push test images (JPEGs) to: /sdcard/Android/data/com.vaultbrain.core.ai.llm.test/files/test_images/
 *    adb push test_images/ /sdcard/Android/data/com.vaultbrain.core.ai.llm.test/files/
 */
@RunWith(AndroidJUnit4::class)
class QwenVisionOnDeviceTest {

    @Test
    fun benchmarkVisionPath() {
        if (!LlamaBridge.isAvailable()) {
            Log.i(TAG, "nemory_llama native library not packaged; skipping vision benchmark")
            return
        }
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelsDir = File(context.getExternalFilesDir("models"), "")
        val imagesDir = File(context.getExternalFilesDir("test_images"), "")
        
        val modelFile = File(modelsDir, MODEL_FILE)
        val mmprojFile = File(modelsDir, MMPROJ_FILE)
        
        if (!modelFile.exists() || !mmprojFile.exists()) {
            Log.i(TAG, "model artifacts not pushed to ${modelsDir.absolutePath}; skipping")
            return
        }

        if (!imagesDir.exists() || imagesDir.listFiles().isNullOrEmpty()) {
            Log.i(TAG, "No test images found in ${imagesDir.absolutePath}. Please push 20-50 representative JPEGs.")
            return
        }

        val images = imagesDir.listFiles { _, name -> 
            name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true)
        } ?: emptyArray()

        Log.i(TAG, "Starting vision benchmark with ${images.size} images.")

        // Load the model once for the entire batch
        val tLoad = System.currentTimeMillis()
        val handle = LlamaBridge.nativeLoad(
            modelFile.absolutePath,
            mmprojFile.absolutePath,
            4096, // Context size
            4     // Threads
        )
        assertTrue("nativeLoad returned 0", handle != 0L)
        Log.i(TAG, "Model and projector loaded in ${System.currentTimeMillis() - tLoad} ms")

        try {
            for (imageFile in images) {
                Log.i(TAG, "--- Processing: ${imageFile.name} (${imageFile.length() / 1024} KB) ---")
                
                val jpegBytes = normalizeImageToJpeg(imageFile)
                if (jpegBytes == null) {
                    Log.e(TAG, "Failed to load/normalize image: ${imageFile.name}")
                    continue
                }

                val prompt = "Extract the document type, any dates, monetary amounts, names, and IDs. Provide a full transcription of both Arabic and English text."
                
                val tGen = System.currentTimeMillis()
                val out = LlamaBridge.nativeGenerate(
                    handle = handle,
                    prompt = prompt,
                    jpegBytes = jpegBytes,
                    maxTokens = 512, // Allow enough tokens for a full transcription
                    temperature = 0.1f, // Low temp for extraction tasks
                    topK = 40,
                    callback = null
                )
                val latencyMs = System.currentTimeMillis() - tGen
                
                Log.i(TAG, "Result for ${imageFile.name}:")
                Log.i(TAG, "Latency: $latencyMs ms")
                Log.i(TAG, "Output:\n$out")
                Log.i(TAG, "---------------------------------------------------")
            }
        } finally {
            LlamaBridge.nativeFree(handle)
            Log.i(TAG, "Benchmark complete and native resources freed.")
        }
    }

    /**
     * Reads the image and ensures it's a valid JPEG byte array for the bridge.
     * Optionally scales down massive images to fit within sensible memory limits.
     */
    private fun normalizeImageToJpeg(file: File): ByteArray? {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val outputStream = ByteArrayOutputStream()
            // Compress to JPEG at 85% quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            bitmap.recycle()
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error normalizing image", e)
            null
        }
    }

    private companion object {
        const val TAG = "QwenVisionBenchmark"
        const val MODEL_FILE = "Qwen3VL-2B-Instruct-Q4_K_M.gguf"
        const val MMPROJ_FILE = "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf"
    }
}
