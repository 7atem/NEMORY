package com.vaultbrain.core.ai.vision

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.vaultbrain.shared.model.Classification
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device document type classifier.
 *
 * Wraps a TFLite MobileNetV3 / EfficientNet-Lite0 model trained on
 * document categories matching [Classification]. When the model asset
 * is not present, [isAvailable] returns `false` and callers should
 * fall back to heuristic-only classification.
 *
 * Expected model input: 224×224 RGB bitmap, normalized to [0, 1].
 * Expected model output: the legacy 16-class softmax in [LABEL_MAP] order.
 * Text heuristics can refine its passport/document result into newer domain categories such as
 * [Classification.IDENTITY_DOCUMENT] without changing the deployed model's output dimensions.
 */
@Singleton
class DocumentClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runnerProvider: () -> TfliteRunner? = { createRunner(context) }
) {

    private val interpreterMutex = kotlinx.coroutines.sync.Mutex()
    private val runner: TfliteRunner? by lazy { runnerProvider() }

    /** Whether the classifier model is available on this device. */
    fun isAvailable(): Boolean = runner != null

    /**
     * Classifies [bitmap] into one of the 16 categories supported by the deployed model.
     *
     * @return a pair of (predicted classification, confidence) or null if unavailable.
     */
    suspend fun classify(bitmap: Bitmap): Pair<Classification, Float>? = withContext(Dispatchers.IO) {
        interpreterMutex.withLock {
            val activeRunner = runner ?: return@withLock null

            // Preprocess: resize to 224x224, then bulk-read pixels for normalization.
            val resized = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            if (resized !== bitmap) resized.recycle()

            runInference(activeRunner, pixels)
        }
    }

    /**
     * Feeds [pixels] (packed ARGB, row-major 224×224) through [runner] and maps the
     * argmax probability to [Classification]. Returns null on shape mismatch or when
     * every probability is NaN.
     */
    internal fun runInference(runner: TfliteRunner, pixels: IntArray): Pair<Classification, Float>? {
        if (pixels.size != INPUT_SIZE * INPUT_SIZE) return null
        val inputBuffer = ByteBuffer.allocateDirect(pixels.size * 3 * 4)
            .order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }

        val outputBuffer = Array(1) { FloatArray(NUM_CLASSES) }
        inputBuffer.rewind()
        runner.run(inputBuffer, outputBuffer)

        val top = topClassification(outputBuffer[0]) ?: return null
        return LABEL_MAP.getOrNull(top.first)?.let { it to top.second }
    }

    /** Argmax over a probability vector; NaN entries never win. */
    internal fun topClassification(probabilities: FloatArray): Pair<Int, Float>? {
        if (probabilities.isEmpty()) return null
        var bestIndex = -1
        var best = Float.NEGATIVE_INFINITY
        for (i in probabilities.indices) {
            val p = probabilities[i]
            if (p.isNaN()) continue
            if (bestIndex < 0 || p > best) {
                best = p
                bestIndex = i
            }
        }
        return if (bestIndex < 0) null else bestIndex to best
    }

    /** Abstraction over the TFLite interpreter so inference is testable on the JVM. */
    interface TfliteRunner {
        fun run(input: ByteBuffer, output: Array<FloatArray>)
    }

    private class InterpreterRunner(
        private val interpreter: org.tensorflow.lite.Interpreter
    ) : TfliteRunner {
        override fun run(input: ByteBuffer, output: Array<FloatArray>) {
            interpreter.run(input, output)
        }
    }

    companion object {
        private const val MODEL_ASSET_PATH = "document_classifier.tflite"
        private const val INPUT_SIZE = 224
        private const val NUM_CLASSES = 16

        internal fun createRunner(context: Context): TfliteRunner? =
            loadInterpreter(context)?.let(::InterpreterRunner)

        private fun loadInterpreter(context: Context): org.tensorflow.lite.Interpreter? {
            return try {
                val assetFileDescriptor = context.assets.openFd(MODEL_ASSET_PATH)
                val inputStream = assetFileDescriptor.createInputStream()
                val modelBytes = inputStream.readBytes()
                inputStream.close()
                val buffer = ByteBuffer.allocateDirect(modelBytes.size)
                    .order(ByteOrder.nativeOrder())
                buffer.put(modelBytes)
                buffer.rewind()

                val options = org.tensorflow.lite.Interpreter.Options().apply {
                    setNumThreads(2)
                }
                org.tensorflow.lite.Interpreter(buffer, options)
            } catch (e: Exception) {
                // Model asset not bundled yet — degrade gracefully.
                null
            }
        }

        /**
         * Maps model output indices to [Classification] enum values.
         * Order must match the training label file.
         */
        internal val LABEL_MAP = listOf(
            Classification.RECEIPT,
            Classification.PRESCRIPTION,
            Classification.LAB_RESULT,
            Classification.PASSPORT,
            Classification.TICKET,
            Classification.HOTEL,
            Classification.INVOICE,
            Classification.WARRANTY_CARD,
            Classification.BUSINESS_CARD,
            Classification.PRODUCT_PHOTO,
            Classification.MENU_PHOTO,
            Classification.SERIAL_PLATE,
            Classification.GENERAL_DOCUMENT,
            Classification.UNKNOWN,
            Classification.UNKNOWN,
            Classification.UNKNOWN
        )
    }
}
