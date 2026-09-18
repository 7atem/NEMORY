package com.vaultbrain.core.ai.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingOutputTest {
    @Test fun `thinking tags split across callbacks are never displayed`() {
        listOf("<", "<thi", "<think>", "<think>private", "<think>private</thi").forEach {
            assertEquals("", ModelOutput.streamingVisible(it))
        }
        assertEquals("Answer", ModelOutput.streamingVisible("<think>private</think>Answer"))
    }
}
