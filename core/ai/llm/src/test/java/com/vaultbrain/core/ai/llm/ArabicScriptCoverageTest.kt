package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArabicScriptCoverageTest {

    @Test
    fun `arabic heavy text has near full script coverage`() {
        val text = "فاتورة الكهرباء لشهر يناير ٢٠٢٥"
        assertThat(ArabicScriptCoverage.arabicRatio(text)).isGreaterThan(0.9)
    }

    @Test
    fun `latin text has zero arabic coverage`() {
        assertThat(ArabicScriptCoverage.arabicRatio("Carrefour Receipt TOTAL 42 USD"))
            .isEqualTo(0.0)
    }

    @Test
    fun `mixed text reports the arabic share of non whitespace characters`() {
        // 6 Arabic letters + 6 Latin letters around one space.
        assertThat(ArabicScriptCoverage.arabicRatio("فاتورة abcdef")).isWithin(0.001).of(0.5)
    }

    @Test
    fun `empty and whitespace only text have zero coverage`() {
        assertThat(ArabicScriptCoverage.arabicRatio("")).isEqualTo(0.0)
        assertThat(ArabicScriptCoverage.arabicRatio("   \n\t  ")).isEqualTo(0.0)
    }

    @Test
    fun `empty or symbol garbage ocr triggers vl transcription`() {
        assertThat(ArabicScriptCoverage.needsVlTranscription("")).isTrue()
        assertThat(ArabicScriptCoverage.needsVlTranscription("  || — • __ ")).isTrue()
        assertThat(ArabicScriptCoverage.needsVlTranscription(".:;")).isTrue()
    }

    @Test
    fun `healthy latin ocr does not trigger vl transcription`() {
        val ocr = "Carrefour Hypermarket Maadi Cairo Total 4850 EGP paid by Visa receipt"
        assertThat(ArabicScriptCoverage.needsVlTranscription(ocr)).isFalse()
    }

    @Test
    fun `already captured arabic text does not trigger vl transcription`() {
        // Short but genuinely Arabic (e.g. pasted text): the script was captured, so the
        // Latin OCR failure hypothesis does not hold.
        assertThat(ArabicScriptCoverage.needsVlTranscription("فاتورة كهرباء قصيرة")).isFalse()
    }

    @Test
    fun `thresholds are configurable`() {
        val shortLatin = "Total 42"
        assertThat(ArabicScriptCoverage.needsVlTranscription(shortLatin)).isTrue()
        assertThat(
            ArabicScriptCoverage.needsVlTranscription(shortLatin, minMeaningfulChars = 4)
        ).isFalse()
    }
}
