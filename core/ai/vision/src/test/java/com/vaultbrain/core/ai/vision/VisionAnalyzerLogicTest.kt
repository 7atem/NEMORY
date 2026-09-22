package com.vaultbrain.core.ai.vision

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.ScoredLabel
import org.junit.Test

class VisionAnalyzerLogicTest {

    @Test fun `filter labels drops entries at or below confidence threshold`() {
        val labels = listOf(
            ScoredLabel("high", 0.9f),
            ScoredLabel("at threshold", 0.3f),
            ScoredLabel("below", 0.1f)
        )
        assertThat(VisionAnalyzer.filterLabels(labels).map { it.label }).containsExactly("high")
    }

    @Test fun `filter labels keeps confidence strictly above 0 3`() {
        val result = VisionAnalyzer.filterLabels(listOf(ScoredLabel("just above", 0.3001f)))
        assertThat(result.map { it.label }).containsExactly("just above")
    }

    @Test fun `filter labels preserves labels sources and valid confidence`() {
        val result = VisionAnalyzer.filterLabels(
            listOf(ScoredLabel("doc", 0.9f, "semantic"), ScoredLabel("page", 0.5f))
        )
        assertThat(result.map { it.label }).containsExactly("doc", "page").inOrder()
        assertThat(result.first().source).isEqualTo("semantic")
        assertThat(result.map { it.confidence }).containsExactly(0.9f, 0.5f).inOrder()
    }

    @Test fun `filter labels dedupes case insensitively keeping first occurrence`() {
        val result = VisionAnalyzer.filterLabels(
            listOf(ScoredLabel("Receipt", 0.9f), ScoredLabel("receipt", 0.5f))
        )
        assertThat(result).hasSize(1)
        assertThat(result.first().label).isEqualTo("Receipt")
    }

    @Test fun `filter labels sorts by descending confidence`() {
        val result = VisionAnalyzer.filterLabels(
            listOf(ScoredLabel("low", 0.4f), ScoredLabel("high", 0.95f), ScoredLabel("mid", 0.6f))
        )
        assertThat(result.map { it.label }).containsExactly("high", "mid", "low").inOrder()
    }

    @Test fun `merge labels collapses duplicates across sources by max confidence`() {
        val mlKit = listOf(ScoredLabel("Document", 0.7f, ScoredLabel.SOURCE_ML_KIT))
        val semantic = listOf(ScoredLabel("document", 0.55f, "semantic"))
        val result = VisionAnalyzer.mergeLabels(listOf(mlKit, semantic))
        assertThat(result).hasSize(1)
        assertThat(result.first().label).isEqualTo("Document")
        assertThat(result.first().confidence).isEqualTo(0.7f)
    }

    @Test fun `merge labels keeps distinct labels sorted descending`() {
        val result = VisionAnalyzer.mergeLabels(
            listOf(
                listOf(ScoredLabel("alpha", 0.6f)),
                listOf(ScoredLabel("beta", 0.9f), ScoredLabel("gamma", 0.3f))
            )
        )
        assertThat(result.map { it.label }).containsExactly("beta", "alpha", "gamma").inOrder()
    }

    @Test fun `merge labels on empty sources yields empty list`() {
        assertThat(VisionAnalyzer.mergeLabels(emptyList())).isEmpty()
        assertThat(VisionAnalyzer.mergeLabels(listOf(emptyList(), emptyList()))).isEmpty()
    }

    @Test fun `dominant colors buckets majority color first`() {
        // 3 white pixels, 1 black: white bucket (7,7,7) dominates; centers quantize to 240.
        val pixels = intArrayOf(-0x1, -0x1, -0x1, -0x1000000)
        assertThat(VisionAnalyzer.dominantColorsFromPixels(pixels).first()).isEqualTo("#F0F0F0")
    }

    @Test fun `dominant colors returns up to five bucket centers as hex`() {
        val pixels = intArrayOf(
            -0x1000000,           // black -> #101010
            -0x10000,             // red -> #F02020
            -0x1000000 or 0xFF00, // green -> #20F020
            -0x1000000 or 0xFF,   // blue -> #2020F0
            -0x1000000 or 0x808080, // mid-gray -> #909090
            -0x1000000 or 0x400000  // dark red -> #501010 (sixth, dropped)
        )
        val colors = VisionAnalyzer.dominantColorsFromPixels(pixels)
        assertThat(colors).hasSize(5)
        assertThat(colors.first()).isEqualTo("#101010")
        assertThat(colors).containsAtLeast("#F01010", "#10F010", "#1010F0")
    }

    @Test fun `dominant colors handles empty input`() {
        assertThat(VisionAnalyzer.dominantColorsFromPixels(IntArray(0))).isEmpty()
        assertThat(VisionAnalyzer.dominantColorsFromPixels(intArrayOf(-0x1), sampleCount = 0)).isEmpty()
    }

    @Test fun `dominant colors quantizes similar colors into one bucket`() {
        // 250 and 240 both land in bucket 7; center renders as 240 (#F0).
        val pixels = intArrayOf(-0x1000000 or (250 shl 16), -0x1000000 or (240 shl 16))
        val colors = VisionAnalyzer.dominantColorsFromPixels(pixels)
        assertThat(colors).hasSize(1)
        assertThat(colors.first()).isEqualTo("#F01010")
    }
}
