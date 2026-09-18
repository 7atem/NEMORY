package com.vaultbrain.shared.intelligence

import com.vaultbrain.shared.domain.LensId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeywordDictionaryTest {

    @Test
    fun detectHierarchy_findsGroceryUnderMoney() {
        val text = "Thanks for shopping! eggs, milk, bread, grocery store"
        val result = KeywordDictionary.detectHierarchy(text)
        assertEquals(mapOf(LensId.MONEY to "grocery"), result)
    }

    @Test
    fun detectHierarchy_findsPharmacyUnderHealth() {
        val text = "CVS pharmacy prescription refill: medication ready"
        val result = KeywordDictionary.detectHierarchy(text)
        assertEquals(mapOf(LensId.HEALTH to "pharmacy"), result)
    }

    @Test
    fun detectHierarchy_findsFuelUnderTravel() {
        val text = "Shell gas station fuel receipt, petrol 40L"
        val result = KeywordDictionary.detectHierarchy(text)
        assertEquals(mapOf(LensId.TRAVEL to "fuel"), result)
    }

    @Test
    fun detectHierarchy_findsMovieUnderMedia() {
        val text = "Cinema showtimes for the new movie tonight"
        val result = KeywordDictionary.detectHierarchy(text)
        assertEquals(mapOf(LensId.MEDIA to "movie"), result)
    }

    @Test
    fun detectHierarchy_findsFurnitureUnderBureaucracy() {
        val text = "IKEA sofa and wardrobe invoice"
        val result = KeywordDictionary.detectHierarchy(text)
        assertEquals(mapOf(LensId.BUREAUCRACY to "furniture"), result)
    }

    @Test
    fun detectHierarchy_returnsEmptyForUnrelatedText() {
        val text = "The quick brown fox jumps over the lazy dog"
        val result = KeywordDictionary.detectHierarchy(text)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectHierarchy_matchesFirstSubModuleOnly() {
        // "electronics" appears after "grocery" in the MONEY hierarchy; with no grocery
        // keyword present, the first matching sub-module is electronics.
        val text = "Best buy electronics and also a laptop"
        val result = KeywordDictionary.detectHierarchy(text)
        assertEquals("electronics", result[LensId.MONEY])
    }
}
