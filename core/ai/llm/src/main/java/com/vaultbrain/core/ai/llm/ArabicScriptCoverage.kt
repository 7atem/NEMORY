package com.vaultbrain.core.ai.llm

/**
 * Script coverage helpers for the experimental Arabic VL path. The installed ML Kit
 * recognizer is Latin-script only, so Arabic pages come back as empty or symbol garbage;
 * these functions decide when a vision-language transcription attempt is warranted.
 */
object ArabicScriptCoverage {

    /** Unicode blocks that carry Arabic script. */
    private val ARABIC_RANGES = listOf(
        0x0600..0x06FF, // Arabic
        0x0750..0x077F, // Arabic Supplement
        0x08A0..0x08FF, // Arabic Extended-A
        0xFB50..0xFDFF, // Arabic Presentation Forms-A
        0xFE70..0xFEFF  // Arabic Presentation Forms-B
    )

    /** Letters and digits only; symbol garbage from a failed OCR pass does not count. */
    fun meaningfulLength(text: String): Int = text.count(Char::isLetterOrDigit)

    /** Share of non-whitespace characters that are Arabic script. Empty text is 0. */
    fun arabicRatio(text: String): Double {
        val nonWhitespace = text.count { !it.isWhitespace() }
        if (nonWhitespace == 0) return 0.0
        val arabic = text.count { char -> ARABIC_RANGES.any { char.code in it } }
        return arabic.toDouble() / nonWhitespace
    }

    /**
     * True when OCR captured too little meaningful text, i.e. the page content (typically
     * Arabic) never made it into the OCR string. Text that already carries Arabic script
     * (e.g. pasted input) is treated as captured and does not trigger a VL pass.
     */
    fun needsVlTranscription(
        ocrText: String,
        minMeaningfulChars: Int = 40,
        maxArabicRatioForLatinOcr: Double = 0.05
    ): Boolean =
        meaningfulLength(ocrText) < minMeaningfulChars &&
            arabicRatio(ocrText) <= maxArabicRatioForLatinOcr
}
