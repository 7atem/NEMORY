package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TokenBudgetTest {
    private val budget = TokenBudget()

    @Test
    fun `ascii estimate uses conservative four character ratio`() {
        assertThat(budget.estimateTokens("12345678")).isEqualTo(2)
    }

    @Test
    fun `arabic and mixed text count non ascii code points conservatively`() {
        assertThat(budget.estimateTokens("فاتورة 1234")).isAtLeast(8)
    }

    @Test
    fun `truncation never exceeds requested token budget or splits surrogate pair`() {
        val result = budget.truncateToTokens("Receipt 😀 فاتورة and a long description", 9)

        assertThat(budget.estimateTokens(result)).isAtMost(9)
        assertThat(result).doesNotContain("�")
    }
}
