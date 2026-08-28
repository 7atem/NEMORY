package com.vaultbrain.core.ai.llm

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Conservative, provider-independent prompt budgeting for the single-turn on-device model.
 * ASCII text is estimated at four characters per token; non-ASCII code points count as one
 * token so Arabic and mixed-language prompts fail safely below the runtime limit.
 */
@Singleton
class TokenBudget @Inject constructor() {
    fun estimateTokens(text: String): Int {
        var asciiUnits = 0
        var nonAsciiTokens = 0
        text.codePoints().forEach { codePoint ->
            if (codePoint <= 0x7F) asciiUnits++ else nonAsciiTokens++
        }
        return ceil(asciiUnits / ASCII_CHARS_PER_TOKEN).toInt() + nonAsciiTokens
    }

    fun truncateToTokens(text: String, maxTokens: Int): String {
        require(maxTokens >= 0)
        if (estimateTokens(text) <= maxTokens) return text
        if (maxTokens == 0) return ""

        var asciiUnits = 0
        var nonAsciiTokens = 0
        var end = 0
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            val nextAscii = asciiUnits + if (codePoint <= 0x7F) 1 else 0
            val nextNonAscii = nonAsciiTokens + if (codePoint > 0x7F) 1 else 0
            val estimate = ceil(nextAscii / ASCII_CHARS_PER_TOKEN).toInt() + nextNonAscii
            if (estimate > maxTokens) break
            asciiUnits = nextAscii
            nonAsciiTokens = nextNonAscii
            end += Character.charCount(codePoint)
        }
        return text.substring(0, end).trimEnd()
    }

    companion object {
        const val MAX_INPUT_TOKENS = 3_584
        private const val ASCII_CHARS_PER_TOKEN = 4.0
    }
}
