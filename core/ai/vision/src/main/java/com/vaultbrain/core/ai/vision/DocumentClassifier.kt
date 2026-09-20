package com.vaultbrain.core.ai.vision

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.vaultbrain.shared.model.Classification
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context
) {

    /**
     * Lazily loaded TFLite interpreter. Returns null if the model asset
     * is missing, so the rest of the app degrades gracefully.
     */
    private val interpreterMutex = kotlinx.coroutines.sync.Mutex()
    private val interpreter: org.tensorflow.lite.Interpreter? by lazy {
        loadInterpreter()
    }

    /** Whether the classifier model is available on this device. */
    fun isAvailable(): Boolean = interpreter != null

    /**
     * Classifies [bitmap] into one of the 16 categories supported by the deployed model.
     *
     * @return a pair of (predicted classification, confidence) or null if unavailable.
     */
    suspend fun classify(bitmap: Bitmap): Pair<Classification, Float>? = withContext(Dispatchers.IO) {
        interpreterMutex.withLock {
            val interp = interpreter ?: return@withLock null

            // 1. Preprocess: Resize to 224x224 and normalize to [0, 1].
            val resized = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
                .order(java.nio.ByteOrder.nativeOrder())

            // Bulk-read pixels instead of per-pixel Bitmap.get() calls.
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((pixel and 0xFF) / 255f)
            }
            if (resized !== bitmap) resized.recycle()

            // 2. Run inference.
            val outputBuffer = Array(1) { FloatArray(NUM_CLASSES) }
            inputBuffer.rewind()
            interp.run(inputBuffer, outputBuffer)

            // 3. Find the class with the highest probability.
            val probabilities = outputBuffer[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return@withLock null
            val confidence = probabilities[maxIndex]

            // 4. Map index to Classification enum.
            val classification = LABEL_MAP.getOrNull(maxIndex) ?: Classification.UNKNOWN
            classification to confidence
        }
    }

    private fun loadInterpreter(): org.tensorflow.lite.Interpreter? {
        return try {
            val assetFileDescriptor = context.assets.openFd(MODEL_ASSET_PATH)
            val inputStream = assetFileDescriptor.createInputStream()
            val modelBytes = inputStream.readBytes()
            inputStream.close()
            val buffer = java.nio.ByteBuffer.allocateDirect(modelBytes.size)
                .order(java.nio.ByteOrder.nativeOrder())
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

    companion object {
        private const val MODEL_ASSET_PATH = "document_classifier.tflite"
        private const val INPUT_SIZE = 224
        private const val NUM_CLASSES = 16

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
