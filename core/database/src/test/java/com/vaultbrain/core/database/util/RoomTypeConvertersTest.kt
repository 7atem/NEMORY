package com.vaultbrain.core.database.util

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.Tier
import org.junit.Test

class RoomTypeConvertersTest {

    private val converters = RoomTypeConverters()

    @Test
    fun `sourceType round trips`() {
        assertThat(converters.stringToSourceType(converters.sourceTypeToString(SourceType.CAMERA)))
            .isEqualTo(SourceType.CAMERA)
        assertThat(converters.stringToSourceType(converters.sourceTypeToString(SourceType.APP_SHARE)))
            .isEqualTo(SourceType.APP_SHARE)
    }

    @Test
    fun `classification round trips`() {
        assertThat(converters.stringToClassification(converters.classificationToString(Classification.RECEIPT)))
            .isEqualTo(Classification.RECEIPT)
        assertThat(
            converters.stringToClassification(
                converters.classificationToString(Classification.IDENTITY_DOCUMENT)
            )
        ).isEqualTo(Classification.IDENTITY_DOCUMENT)
    }

    @Test
    fun `tier round trips`() {
        assertThat(converters.stringToTier(converters.tierToString(Tier.PRO)))
            .isEqualTo(Tier.PRO)
    }

    @Test
    fun `string map round trips`() {
        val map = mapOf("merchant" to "Carrefour", "total" to "4850")
        val json = converters.stringMapToJson(map)
        assertThat(json).isNotNull()
        assertThat(converters.jsonToStringMap(json)).isEqualTo(map)
    }

    @Test
    fun `string set round trips`() {
        val set = setOf("MONEY", "SHOPPING")
        val json = converters.stringSetToJson(set)
        assertThat(converters.jsonToStringSet(json)).isEqualTo(set)
    }

    @Test
    fun `string list round trips`() {
        val list = listOf("shoe", "logo", "text")
        val json = converters.stringListToJson(list)
        assertThat(converters.jsonToStringList(json)).isEqualTo(list)
    }
}
