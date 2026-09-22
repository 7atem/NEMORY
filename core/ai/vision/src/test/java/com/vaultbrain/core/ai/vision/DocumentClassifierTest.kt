package com.vaultbrain.core.ai.vision

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

class DocumentClassifierTest {

    private fun classifierWriting(output: FloatArray): DocumentClassifier =
        DocumentClassifier(android.content.ContextWrapper(null)) { FakeRunner(output) }

    private class FakeRunner(private val output: FloatArray) : DocumentClassifier.TfliteRunner {
        var seenInput: ByteBuffer? = null
            private set

        override fun run(input: ByteBuffer, out: Array<FloatArray>) {
            seenInput = input
            System.arraycopy(output, 0, out[0], 0, minOf(output.size, out[0].size))
        }
    }

    private fun pixels(fill: Int): IntArray = IntArray(224 * 224) { fill }

    @Test fun `maps argmax index to classification label`() {
        val output = FloatArray(16).also { it[3] = 0.9f } // index 3 = PASSPORT
        val result = classifierWriting(output).runInference(FakeRunner(output), pixels(0))
        assertThat(result).isEqualTo(Classification.PASSPORT to 0.9f)
    }

    @Test fun `label map covers all sixteen indices in training order`() {
        assertThat(DocumentClassifier.LABEL_MAP).hasSize(16)
        assertThat(DocumentClassifier.LABEL_MAP[0]).isEqualTo(Classification.RECEIPT)
        assertThat(DocumentClassifier.LABEL_MAP[4]).isEqualTo(Classification.TICKET)
        assertThat(DocumentClassifier.LABEL_MAP[11]).isEqualTo(Classification.SERIAL_PLATE)
        assertThat(DocumentClassifier.LABEL_MAP[12]).isEqualTo(Classification.GENERAL_DOCUMENT)
        assertThat(DocumentClassifier.LABEL_MAP.subList(13, 16))
            .containsExactly(Classification.UNKNOWN, Classification.UNKNOWN, Classification.UNKNOWN)
    }

    @Test fun `all zero probabilities fall back to first class with zero confidence`() {
        val runner = FakeRunner(FloatArray(0))
        val classifier = DocumentClassifier(android.content.ContextWrapper(null)) { runner }
        assertThat(classifier.runInference(runner, pixels(0)))
            .isEqualTo(Classification.RECEIPT to 0f)
    }

    @Test fun `wrong pixel count is rejected before inference`() {
        val runner = FakeRunner(FloatArray(16))
        val classifier = DocumentClassifier(android.content.ContextWrapper(null)) { runner }
        assertThat(classifier.runInference(runner, IntArray(10))).isNull()
    }

    @Test fun `all NaN probabilities fail closed to null`() {
        val output = FloatArray(16) { Float.NaN }
        val result = classifierWriting(output).runInference(FakeRunner(output), pixels(0))
        assertThat(result).isNull()
    }

    @Test fun `top classification ignores NaN entries`() {
        val probabilities = FloatArray(16) { Float.NaN }
        probabilities[7] = 0.4f
        probabilities[2] = 0.1f
        val classifier = DocumentClassifier(android.content.ContextWrapper(null)) { null }
        assertThat(classifier.topClassification(probabilities)).isEqualTo(7 to 0.4f)
        assertThat(classifier.topClassification(FloatArray(0))).isNull()
        assertThat(classifier.topClassification(FloatArray(3) { Float.NaN })).isNull()
    }

    @Test fun `input buffer holds rgb floats normalized to 0 1`() {
        // Pure red, pure green, pure blue packed as ARGB ints.
        val px = IntArray(224 * 224)
        px[0] = -0x1000000 or (0xFF shl 16)
        px[1] = -0x1000000 or (0xFF shl 8)
        px[2] = -0x1000000 or 0xFF
        val runner = FakeRunner(FloatArray(16).also { it[0] = 1f })
        val classifier = DocumentClassifier(android.content.ContextWrapper(null)) { runner }
        classifier.runInference(runner, px)

        val buffer = runner.seenInput!!
        buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val floats = FloatArray(9) { buffer.float }
        assertThat(floats[0]).isEqualTo(1f)  // R of pixel 0
        assertThat(floats[1]).isEqualTo(0f)
        assertThat(floats[2]).isEqualTo(0f)
        assertThat(floats[3]).isEqualTo(0f)  // G of pixel 1
        assertThat(floats[4]).isEqualTo(1f)
        assertThat(floats[5]).isEqualTo(0f)
        assertThat(floats[6]).isEqualTo(0f)  // B of pixel 2
        assertThat(floats[7]).isEqualTo(0f)
        assertThat(floats[8]).isEqualTo(1f)
    }

    @Test fun `black pixels normalize to zero floats`() {
        val runner = FakeRunner(FloatArray(16).also { it[0] = 1f })
        val classifier = DocumentClassifier(android.content.ContextWrapper(null)) { runner }
        classifier.runInference(runner, pixels(-0x1000000))
        val buffer = runner.seenInput!!
        buffer.rewind()
        assertThat(buffer.float).isEqualTo(0f)
        assertThat(buffer.float).isEqualTo(0f)
        assertThat(buffer.float).isEqualTo(0f)
    }

    @Test fun `runner provider returning null makes classifier unavailable`() {
        val classifier = DocumentClassifier(android.content.ContextWrapper(null)) { null }
        assertThat(classifier.isAvailable()).isFalse()
    }
}
