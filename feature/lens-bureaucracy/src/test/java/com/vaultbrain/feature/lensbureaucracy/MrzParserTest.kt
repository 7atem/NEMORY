package com.vaultbrain.feature.lensbureaucracy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MrzParserTest {
    @Test
    fun `parses and validates ICAO passport sample`() {
        val result = MrzParser.parse(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n" +
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        )

        assertThat(result?.documentNumber).isEqualTo("L898902C3")
        assertThat(result?.nationality).isEqualTo("UTO")
        assertThat(result?.isChecksumValid).isTrue()
    }
}
