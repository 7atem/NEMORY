package com.vaultbrain.feature.brain

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.TokenBudget
import com.vaultbrain.feature.brain.model.ChatMessage
import org.junit.Test

class ConversationMemoryBuilderTest {
    private val tokenBudget = TokenBudget()
    private val builder = ConversationMemoryBuilder(tokenBudget)

    @Test
    fun `memory is bounded and excludes failed assistant messages`() {
        val messages = buildList {
            repeat(10) { index ->
                add(ChatMessage.User("u$index", "question $index " + "x".repeat(200)))
                add(ChatMessage.Assistant("a$index", "answer $index " + "y".repeat(200)))
            }
            add(ChatMessage.Assistant("error", "network failed", isError = true))
        }

        val context = builder.build(messages, maxTokens = 160).orEmpty()

        assertThat(tokenBudget.estimateTokens(context)).isAtMost(160)
        assertThat(context).doesNotContain("network failed")
        assertThat(context).contains("Recent turns")
    }
}
