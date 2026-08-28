package com.vaultbrain.feature.brain

import com.vaultbrain.core.ai.llm.TokenBudget
import com.vaultbrain.feature.brain.model.ChatMessage
import javax.inject.Inject

/** Builds bounded, extractive context for a single-turn on-device request. */
class ConversationMemoryBuilder @Inject constructor(
    private val tokenBudget: TokenBudget
) {
    fun build(messages: List<ChatMessage>, maxTokens: Int = DEFAULT_MEMORY_TOKENS): String? {
        val usable = messages.filterNot { it is ChatMessage.Assistant && (it.isError || it.text.isBlank()) }
        if (usable.isEmpty()) return null

        val older = usable.dropLast(RECENT_MESSAGE_COUNT)
        val recent = usable.takeLast(RECENT_MESSAGE_COUNT)
        val recentText = buildString {
            appendLine("Recent turns:")
            recent.forEach { appendLine(lineFor(it)) }
        }.trim()
        val boundedRecent = tokenBudget.truncateToTokens(
            recentText,
            (maxTokens * RECENT_BUDGET_PERCENT / 100).coerceAtLeast(1)
        )
        val olderBudget = (
            maxTokens - tokenBudget.estimateTokens(boundedRecent) - SECTION_MARGIN_TOKENS
        ).coerceAtLeast(0)
        val boundedOlder = if (older.isEmpty() || olderBudget == 0) "" else {
            tokenBudget.truncateToTokens(
                buildString {
                    appendLine("Earlier turns (extractive summary):")
                    older.takeLast(MAX_OLDER_MESSAGES).forEach { appendLine("- ${lineFor(it)}") }
                }.trim(),
                olderBudget
            )
        }
        return listOf(boundedOlder, boundedRecent)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .takeIf(String::isNotBlank)
    }

    private fun lineFor(message: ChatMessage): String {
        val role = if (message is ChatMessage.User) "User" else "Nemory"
        return "$role: ${message.text.replace(Regex("\\s+"), " ").take(MAX_MESSAGE_CHARS)}"
    }

    private companion object {
        const val DEFAULT_MEMORY_TOKENS = 480
        const val RECENT_MESSAGE_COUNT = 6
        const val MAX_OLDER_MESSAGES = 4
        const val MAX_MESSAGE_CHARS = 320
        const val RECENT_BUDGET_PERCENT = 75
        const val SECTION_MARGIN_TOKENS = 2
    }
}
