package com.vaultbrain.shared.ui.sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleKeywordDetectorTest {

    @Test
    fun detect_groceryTextReturnsMoneyGroceryLines() {
        val text = "Thanks for shopping! eggs, milk, bread, grocery store"
        val result = SampleKeywordDetector.detect(text)
        assertTrue(result.contains("MONEY"))
        assertTrue(result.contains("grocery"))
    }

    @Test
    fun detect_emptyAndIrrelevantTextReturnsNoLensDetected() {
        assertEquals("No lens detected", SampleKeywordDetector.detect(""))
        assertEquals(
            "No lens detected",
            SampleKeywordDetector.detect("The quick brown fox jumps over the lazy dog")
        )
    }

    @Test
    fun detect_usesLensSubmoduleArrowFormat() {
        val text = "Thanks for shopping! eggs, milk, bread, grocery store"
        val result = SampleKeywordDetector.detect(text)
        assertTrue(result.lines().all { it.contains(" → ") })
        assertTrue(result.lines().any { it == "MONEY → grocery" })
    }
}
