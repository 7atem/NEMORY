package com.vaultbrain.core.ai.llm

/** Hard generation limits; raw reasoning is never a user-facing artifact. */
enum class ReasoningBudget(val maxTokens: Int, val timeoutMillis: Long, val maxRounds: Int) {
    FAST(64, 15_000, 1),
    NORMAL(192, 45_000, 2),
    DEEP(512, 90_000, 3)
}

object ModelOutput {
    fun streamingVisible(raw: String): String {
        val partialTag = raw.lastIndexOf('<')
        val safe = if (partialTag >= 0 && "<think>".startsWith(raw.substring(partialTag))) raw.substring(0, partialTag) else raw
        return visible(safe)
    }
    fun visible(raw: String): String {
        val open = raw.indexOf("<think>")
        if (open < 0) return raw.trim()
        val close = raw.indexOf("</think>", open)
        if (close < 0) return raw.substring(0, open).trim()
        return visible(raw.substring(0, open) + raw.substring(close + 8))
    }
}
