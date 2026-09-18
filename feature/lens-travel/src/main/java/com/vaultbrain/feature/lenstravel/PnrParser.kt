package com.vaultbrain.feature.lenstravel

/**
 * Result data class for parsed travel reservation tokens.
 */
data class TravelParsedInfo(
    val pnr: String? = null,
    val flightNumber: String? = null,
    val seat: String? = null,
    val originIata: String? = null,
    val destinationIata: String? = null,
    val gate: String? = null
)

/**
 * Heuristic parser for extracting PNR booking locators, flight numbers,
 * seat assignments, and airport codes from OCR / ticket text.
 */
object PnrParser {

    private val PNR_LABEL_PATTERN =
        """(?i)(?:pnr|booking\s*ref(?:erence)?|record\s*locator|confirmation\s*code)[\s:]+([A-Z0-9]{6})\b""".toRegex()

    private val STANDALONE_PNR_PATTERN =
        """\b([A-Z2-9]{6})\b""".toRegex()

    private val FLIGHT_NUMBER_PATTERN =
        """\b([A-Z]{2,3})\s?(\d{1,4})\b""".toRegex()

    private val SEAT_PATTERN =
        """(?i)(?:seat|st)[\s:]*([0-9]{1,2}[A-K])\b""".toRegex()

    private val GATE_PATTERN =
        """(?i)(?:gate|gt)[\s:]*([A-Z0-9]{1,4})\b""".toRegex()

    private val IATA_PATTERN = """\b[A-Z]{3}\b""".toRegex()

    private val KNOWN_IATAS = setOf(
        "CAI", "DXB", "DWC", "JED", "RUH", "DOH", "AUH", "KWI", "BAH", "MCT",
        "AMM", "MED", "IST", "SAW", "LHR", "LGW", "CDG", "FRA", "MUC", "JFK",
        "EWR", "LAX", "ORD", "YYZ", "SIN", "BKK", "HND", "NRT", "SYD", "MEL"
    )

    private val EXCLUDED_PNR_WORDS = setOf(
        "FLIGHT", "TICKET", "ISSUED", "TRAVEL", "AIRWAY", "BOARDG", "PASSEN",
        "STATUS", "NUMBER", "ONLINE", "DIRECT", "RETURN", "DEPART", "ARRIVE",
        "CLASS", "NOTICE", "SECTOR", "SINGLE", "COUPON", "SYSTEM", "AIRPORT"
    )

    /**
     * Parses the raw OCR text and returns structured [TravelParsedInfo].
     */
    fun parse(text: String): TravelParsedInfo {
        if (text.isBlank()) return TravelParsedInfo()

        // 1. PNR extraction
        val pnrLabeled = PNR_LABEL_PATTERN.find(text)?.groupValues?.get(1)
        val pnr = pnrLabeled ?: STANDALONE_PNR_PATTERN.findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull { it !in EXCLUDED_PNR_WORDS && it.any { char -> char.isDigit() } }

        // 2. Flight number extraction
        val flightNumber = FLIGHT_NUMBER_PATTERN.find(text)?.value

        // 3. Seat extraction
        val seat = SEAT_PATTERN.find(text)?.groupValues?.get(1)

        // 4. Gate extraction
        val gate = GATE_PATTERN.find(text)?.groupValues?.get(1)

        // 5. IATA airport codes
        val detectedIatas = IATA_PATTERN.findAll(text.uppercase())
            .map { it.value }
            .filter { it in KNOWN_IATAS }
            .distinct()
            .toList()
        val originIata = detectedIatas.getOrNull(0)
        val destIata = detectedIatas.getOrNull(1)

        return TravelParsedInfo(
            pnr = pnr,
            flightNumber = flightNumber,
            seat = seat,
            originIata = originIata,
            destinationIata = destIata,
            gate = gate
        )
    }
}
