package com.vaultbrain.core.common.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

class DesignTokensTest {

    @Test
    fun `animations removed by the user means reduced motion`() {
        assertTrue(isReducedMotion(0f))
    }

    @Test
    fun `normal and slowed animation scales keep motion`() {
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(10f))
    }

    @Test
    fun `touch target token meets the 48dp accessibility minimum`() {
        assertEquals(48.dp, VaultSpacing.minTouchTarget)
    }

    @Test
    fun `spacing scale is strictly increasing`() {
        val scale = listOf(
            VaultSpacing.xxs, VaultSpacing.xs, VaultSpacing.sm, VaultSpacing.md,
            VaultSpacing.lg, VaultSpacing.xl, VaultSpacing.xxl
        )
        assertEquals(scale.sorted(), scale)
        assertEquals(scale.size, scale.toSet().size)
    }

    @Test
    fun `responsive width tokens are positive and ordered`() {
        assertTrue(VaultSpacing.maxFocusedWidth > 0.dp)
        assertTrue(VaultSpacing.maxReadableWidth > VaultSpacing.maxFocusedWidth)
    }
}
