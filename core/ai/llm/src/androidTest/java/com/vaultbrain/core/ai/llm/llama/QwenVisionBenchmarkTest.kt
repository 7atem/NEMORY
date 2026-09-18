package com.vaultbrain.core.ai.llm.llama

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device Qwen3-VL vision benchmark: loads the model once, then runs a fixed
 * document-extraction prompt over every image in `benchmark_images/` and records
 * per-image latency and output to `vision_benchmark.jsonl`.
 *
 * Setup:
 *   adb push <images> /sdcard/Android/data/com.vaultbrain.core.ai.llm.test/files/benchmark_images/
 *
 * Model artifacts must already be present (see LlamaBridgeOnDeviceTest). Quality is
 * judged from the report, not asserted here; only hard failures (no lib, no model,
 * load failure) fail the test.
 */
@RunWith(AndroidJUnit4::class)
class QwenVisionBenchmarkTest {

    @Test
    fun benchmarkDocumentImages() {
        if (!LlamaBridge.isAvailable()) {
            Log.i(TAG, "nemory_llama native library not packaged; skipping")
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = context.getExternalFilesDir("")!!
        val model = File(File(root, "models"), MODEL_FILE)
        val mmproj = File(File(root, "models"), MMPROJ_FILE)
        val imageDir = File(root, "benchmark_images")
        val images = imageDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        if (!model.exists() || !mmproj.exists() || images.isEmpty()) {
            Log.i(TAG, "models or benchmark_images missing (images=${images.size}); skipping")
            return
        }

        val results = File(root, RESULTS_FILE)
        results.writeText("")
        Log.i(TAG, "loading model for ${images.size} images")
        val handle = LlamaBridge.nativeLoad(model.absolutePath, mmproj.absolutePath, 4096, 4)
        assertTrue("nativeLoad returned 0", handle != 0L)
        try {
            images.forEach { image ->
                val t0 = System.currentTimeMillis()
                val output = try {
                    LlamaBridge.nativeGenerate(
                        handle, PROMPT, image.readBytes(), MAX_TOKENS, 0f, 1, null
                    )
                } catch (t: Throwable) {
                    "ERROR: ${t.message}"
                }
                val latencyMs = System.currentTimeMillis() - t0
                val row = JSONObject()
                    .put("file", image.name)
                    .put("latency_ms", latencyMs)
                    .put("output_len", output?.length ?: 0)
                    .put("output", output ?: JSONObject.NULL)
                results.appendText(row.toString() + "\n")
                Log.i(TAG, "${image.name}: ${latencyMs}ms, ${output?.length ?: 0} chars")
            }
        } finally {
            LlamaBridge.nativeFree(handle)
        }
        Log.i(TAG, "wrote ${images.size} rows to ${results.absolutePath}")
    }

    private companion object {
        const val TAG = "QwenVisionBench"
        const val MODEL_FILE = "Qwen3VL-2B-Instruct-Q4_K_M.gguf"
        const val MMPROJ_FILE = "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf"
        const val RESULTS_FILE = "vision_benchmark.jsonl"
        const val MAX_TOKENS = 128
        const val PROMPT =
            "Extract from this document image and reply as JSON only: " +
                "{\"document_type\": \"\", \"languages\": [], \"dates\": [], " +
                "\"amounts\": [], \"names\": [], \"id_numbers\": [], " +
                "\"transcription_excerpt\": \"\"}"
    }
}
