package com.vaultbrain.feature.lenstravel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PnrParserTest {

    @Test
    fun `extracts labeled PNR and flight number`() {
        val ocr = """
            BOARDING PASS
            Passenger: John Doe
            Flight: EK 927
            PNR: AB12CD
            Gate: B22 Seat: 14A
            From: DXB To: CAI
        """.trimIndent()

        val result = PnrParser.parse(ocr)

        assertThat(result.pnr).isEqualTo("AB12CD")
        assertThat(result.flightNumber).isEqualTo("EK 927")
        assertThat(result.seat).isEqualTo("14A")
        assertThat(result.gate).isEqualTo("B22")
        assertThat(result.originIata).isEqualTo("DXB")
        assertThat(result.destinationIata).isEqualTo("CAI")
    }

    @Test
    fun `extracts standalone PNR containing digits and letters`() {
        val ocr = """
            E-Ticket Receipt
            Airline: British Airways BA123
            Booking Ref: K7X9P2
            LHR to JFK
        """.trimIndent()

        val result = PnrParser.parse(ocr)

        assertThat(result.pnr).isEqualTo("K7X9P2")
        assertThat(result.flightNumber).isEqualTo("BA123")
        assertThat(result.originIata).isEqualTo("LHR")
        assertThat(result.destinationIata).isEqualTo("JFK")
    }

    @Test
    fun `returns empty info on unstructured non-travel text`() {
        val result = PnrParser.parse("Just a grocery store receipt with milk and bread")

        assertThat(result.pnr).isNull()
        assertThat(result.flightNumber).isNull()
    }
}
