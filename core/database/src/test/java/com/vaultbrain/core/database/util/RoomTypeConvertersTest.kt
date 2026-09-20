package com.vaultbrain.core.database.util

import com.vaultbrain.shared.database.util.RoomTypeConverters

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoomTypeConvertersTest {

    private val converters = RoomTypeConverters()

    @Test
    fun `sourceType round trips`() {
        assertEquals(
            SourceType.CAMERA,
            converters.stringToSourceType(converters.sourceTypeToString(SourceType.CAMERA))
        )
        assertEquals(
            SourceType.APP_SHARE,
            converters.stringToSourceType(converters.sourceTypeToString(SourceType.APP_SHARE))
        )
    }

    @Test
    fun `classification round trips`() {
        assertEquals(
            Classification.RECEIPT,
            converters.stringToClassification(converters.classificationToString(Classification.RECEIPT))
        )
        assertEquals(
            Classification.IDENTITY_DOCUMENT,
            converters.stringToClassification(
                converters.classificationToString(Classification.IDENTITY_DOCUMENT)
            )
        )
    }

    @Test
    fun `tier round trips`() {
        assertEquals(
            Tier.PRO,
            converters.stringToTier(converters.tierToString(Tier.PRO))
        )
    }

    @Test
    fun `string map round trips`() {
        val map = mapOf("merchant" to "Carrefour", "total" to "4850")
        val json = converters.stringMapToJson(map)
        assertNotNull(json)
        assertEquals(map, converters.jsonToStringMap(json))
    }

    @Test
    fun `string set round trips`() {
        val set = setOf("MONEY", "SHOPPING")
        val json = converters.stringSetToJson(set)
        assertEquals(set, converters.jsonToStringSet(json))
    }

    @Test
    fun `string list round trips`() {
        val list = listOf("shoe", "logo", "text")
        val json = converters.stringListToJson(list)
        assertEquals(list, converters.jsonToStringList(json))
    }
}
