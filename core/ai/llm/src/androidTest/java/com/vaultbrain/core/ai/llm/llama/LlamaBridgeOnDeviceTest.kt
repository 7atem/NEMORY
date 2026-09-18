package com.vaultbrain.core.ai.llm.llama

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device smoke test for the Qwen3-VL native bridge: load the real GGUF artifacts
 * and run one short text generation.
 *
 * The artifacts are large, so they are not bundled. Push them once with:
 *   adb push Qwen3VL-2B-Instruct-Q4_K_M.gguf \
 *     /sdcard/Android/data/com.vaultbrain.core.ai.llm.test/files/models/
 *   adb push mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf \
 *     /sdcard/Android/data/com.vaultbrain.core.ai.llm.test/files/models/
 *
 * When the native library or the artifacts are absent the test logs and passes
 * without asserting, so CI hosts without models stay green.
 */
@RunWith(AndroidJUnit4::class)
class LlamaBridgeOnDeviceTest {

    @Test
    fun loadModelAndGenerate() {
        if (!LlamaBridge.isAvailable()) {
            Log.i(TAG, "nemory_llama native library not packaged; skipping")
            assumeTrue("Native runtime and model artifacts are required", false)
            return
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.getExternalFilesDir("models"), "")
        val model = File(dir, MODEL_FILE)
        val mmproj = File(dir, MMPROJ_FILE)
        if (!model.exists()) {
            Log.i(TAG, "model artifacts not pushed to ${dir.absolutePath}; skipping")
            assumeTrue("Native runtime and model artifacts are required", false)
            return
        }
        Log.i(TAG, "loading ${model.name} (${model.length()} bytes)")
        val t0 = System.currentTimeMillis()
        val handle = LlamaBridge.nativeLoad(
            model.absolutePath,
            mmproj.takeIf { it.exists() }?.absolutePath,
            4096,
            4
        )
        assertTrue("nativeLoad returned 0", handle != 0L)
        Log.i(TAG, "model loaded in ${System.currentTimeMillis() - t0} ms")
        try {
            val t1 = System.currentTimeMillis()
            val out = LlamaBridge.nativeGenerate(
                handle,
                "Reply with a single number. What is 2+2?",
                null,
                32,
                0f,
                1,
                null
            )
            Log.i(TAG, "generation took ${System.currentTimeMillis() - t1} ms; output: $out")
            assertNotNull("nativeGenerate returned null", out)
            assertTrue("Expected arithmetic answer 4", out!!.contains("4"))
            val chunks = mutableListOf<String>()
            val streamed = LlamaBridge.nativeGenerate(handle, "Count from 1 to 5. Only output the numbers.", null,
                24, 0f, 1, LlamaTokenCallback { cumulative ->
                    if (cumulative.isNotEmpty()) chunks += cumulative
                    true
                })
            assertTrue("Must receive incremental text", chunks.distinct().size >= 2)
            assertTrue("Final text must include the last streamed prefix", streamed?.startsWith(chunks.last()) == true)
            var cancelledAt = 0L
            val cancelStarted = System.currentTimeMillis()
            LlamaBridge.nativeGenerate(handle, "Count from 1 to 100.", null, 128, 0f, 1,
                LlamaTokenCallback { cumulative ->
                    if (cumulative.isNotEmpty()) cancelledAt = System.currentTimeMillis()
                    cumulative.isEmpty()
                })
            assertTrue("Cancellation callback should run", cancelledAt >= cancelStarted)
            assertTrue("Native generation must stop promptly after cancellation", System.currentTimeMillis() - cancelledAt < 5000)
            Log.i(TAG, "STREAM_AND_CANCELLATION_PASS chunks=${chunks.size}")
        } finally {
            LlamaBridge.nativeFree(handle)
        }
    }

    private companion object {
        const val TAG = "LlamaBridgeTest"
        const val MODEL_FILE = "Qwen3VL-2B-Instruct-Q4_K_M.gguf"
        const val MMPROJ_FILE = "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf"
    }
}
