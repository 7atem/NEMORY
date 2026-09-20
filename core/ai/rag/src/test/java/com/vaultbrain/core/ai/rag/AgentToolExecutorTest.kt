package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.ReasoningBudget
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.core.integrations.model.ExternalRecord
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentToolExecutorTest {
    @Test fun `arithmetic applies precedence decimals unary signs and bounded parentheses`() {
        assertThat(BoundedArithmetic.evaluate("(100 * 1.11) - 10 / 2")).isEqualTo("106")
        assertThat(BoundedArithmetic.evaluate("-2 * (3 + 4)")).isEqualTo("-14")
        assertThat(BoundedArithmetic.evaluate("0.1 + 0.2")).isEqualTo("0.3")
        listOf("1/0", "1+", "system(1)", "1e99", "(".repeat(20) + "1" + ")".repeat(20)).forEach {
            assertThat(runCatching { BoundedArithmetic.evaluate(it) }.isFailure).isTrue()
        }
    }

    @Test fun `typed external tool results survive planning without vault items`() = runTest {
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        coEvery { model.generateForTask(any(), any()) } returns """{"tool":"search_calendar","query":"appointment"}"""
        val event = ExternalRecord("calendar", "device", "event", ExternalSource.CALENDAR, ExternalRecordType.EVENT, title = "Appointment")
        val result = LocalAgent(model).retrieve("appointment", emptyList(), search = { emptyList() },
            executeTypedTool = { _, _ -> AgentToolResult(records = listOf(event)) }, budget = ReasoningBudget.NORMAL)
        assertThat(result.externalSources).containsExactly(event)
        assertThat(result.sources).isEmpty()
        assertThat(result.trace.single().resultCount).isEqualTo(1)
    }
}
