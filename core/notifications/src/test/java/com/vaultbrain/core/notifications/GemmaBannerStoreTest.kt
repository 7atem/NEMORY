package com.vaultbrain.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaBannerStoreTest {

    @Test
    fun `never dismissed shows immediately`() {
        assertTrue(GemmaBannerStore.shouldShowBanner(dismissCount = 0, lastDismissedAt = 0L, now = NOW))
    }

    @Test
    fun `dismissed stays hidden before seven days`() {
        assertFalse(
            GemmaBannerStore.shouldShowBanner(
                dismissCount = 1,
                lastDismissedAt = NOW - GemmaBannerStore.RESHOW_DELAY_MS + 1,
                now = NOW
            )
        )
    }

    @Test
    fun `dismissed once re-shows after seven days`() {
        assertTrue(
            GemmaBannerStore.shouldShowBanner(
                dismissCount = 1,
                lastDismissedAt = NOW - GemmaBannerStore.RESHOW_DELAY_MS,
                now = NOW
            )
        )
    }

    @Test
    fun `dismissed twice re-shows after seven days`() {
        assertTrue(
            GemmaBannerStore.shouldShowBanner(
                dismissCount = 2,
                lastDismissedAt = NOW - GemmaBannerStore.RESHOW_DELAY_MS,
                now = NOW
            )
        )
    }

    @Test
    fun `dismissed three times never shows again`() {
        assertFalse(
            GemmaBannerStore.shouldShowBanner(
                dismissCount = GemmaBannerStore.MAX_DISMISSES,
                lastDismissedAt = 0L,
                now = NOW
            )
        )
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
