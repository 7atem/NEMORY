package com.vaultbrain.core.ai.llm.gemma

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GemmaDownloadConfigTest {
    @Test
    fun `valid pinned https artifact is accepted`() {
        assertThat(
            GemmaDownloadConfig.isValid(
                url = "https://models.example.org/gemma.task",
                sha256 = "a".repeat(64),
                sizeBytes = 554_661_243L
            )
        ).isTrue()
    }

    @Test
    fun `placeholder configuration is rejected`() {
        assertThat(
            GemmaDownloadConfig.isValid(
                url = "https://example.com/gemma.task",
                sha256 = "0".repeat(64),
                sizeBytes = 0L
            )
        ).isFalse()
    }

    @Test
    fun `insecure artifact URL is rejected`() {
        assertThat(
            GemmaDownloadConfig.isValid(
                url = "http://models.example.org/gemma.task",
                sha256 = "a".repeat(64),
                sizeBytes = 1L
            )
        ).isFalse()
    }
}
