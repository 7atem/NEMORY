package com.vaultbrain.feature.vault.components

import com.vaultbrain.feature.vault.R
import com.vaultbrain.shared.domain.LensId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensLabelsTest {

    @Test
    fun `every specialized lens has a localized name resource`() {
        val expected = mapOf(
            LensId.MONEY to R.string.lens_name_money,
            LensId.HEALTH to R.string.lens_name_health,
            LensId.TRAVEL to R.string.lens_name_travel,
            LensId.BUREAUCRACY to R.string.lens_name_bureaucracy,
            LensId.MEDIA to R.string.lens_name_media
        )
        expected.forEach { (lens, res) -> assertEquals(lens, res, lensTitleRes(lens)) }
        assertEquals(expected.size, expected.values.toSet().size)
    }

    @Test
    fun `unknown lens falls back to the non-localized title`() {
        assertNull(lensTitleRes("custom_lens"))
        assertEquals("Custom_lens", lensTitle("custom_lens"))
    }
}
