package com.vaultbrain.shared.ui.sample

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleLanguageStateTest {

    @Test
    fun defaultsToEnglish() {
        assertFalse(SampleLanguageState.isArabic.value)
    }

    @Test
    fun setSampleLanguage_updatesState() {
        try {
            setSampleLanguage(true)
            assertTrue(SampleLanguageState.isArabic.value)
            setSampleLanguage(false)
            assertFalse(SampleLanguageState.isArabic.value)
        } finally {
            setSampleLanguage(false)
        }
    }
}
