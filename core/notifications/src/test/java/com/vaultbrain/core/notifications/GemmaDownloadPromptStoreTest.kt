package com.vaultbrain.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaDownloadPromptStoreTest {

    @Test
    fun `never dismissed shows immediately`() {
        assertTrue(GemmaDownloadPromptStore.shouldShowPrompt(dismissCount = 0, lastShownAt = 0L, now = NOW))
    }

    @Test
    fun `dismissed once stays hidden before seven days`() {
        assertFalse(
            GemmaDownloadPromptStore.shouldShowPrompt(
                dismissCount = 1,
                lastShownAt = NOW - GemmaDownloadPromptStore.RESHOW_DELAY_MS + 1,
                now = NOW
            )
        )
    }

    @Test
    fun `dismissed once re-shows after seven days`() {
        assertTrue(
            GemmaDownloadPromptStore.shouldShowPrompt(
                dismissCount = 1,
                lastShownAt = NOW - GemmaDownloadPromptStore.RESHOW_DELAY_MS,
                now = NOW
            )
        )
    }

    @Test
    fun `dismissed twice never shows again`() {
        assertFalse(
            GemmaDownloadPromptStore.shouldShowPrompt(
                dismissCount = 2,
                lastShownAt = 0L,
                now = NOW
            )
        )
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
