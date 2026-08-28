package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.VaultItem

/** Extracts flight ticket fields. */
class FlightTicketParser : ExperienceParser {
    override val experienceId = ExperienceId.FLIGHT_TICKET

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("flight", ignoreCase = true) ||
            text.contains("boarding pass", ignoreCase = true) ||
            text.contains("PNR", ignoreCase = true) ||
            text.contains("e-ticket", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "flight_ticket")
            extractFlightNumber(text)?.let { put("flight_number", it) }
            extractPnr(text)?.let { put("pnr", it) }
            extractAirportCodes(text).takeIf { it.isNotEmpty() }?.let { put("route", it.joinToString(" → ")) }
            extractDate(text)?.let { put("departure_date", it) }
            extractTime(text)?.let { put("departure_time", it) }
            extractSeat(text)?.let { put("seat", it) }
            extractPassenger(text)?.let { put("passenger", it) }
            extractCarrier(text)?.let { put("carrier", it) }
            extractGate(text)?.let { put("gate", it) }
        }
    }
}

/** Extracts hotel booking fields. */
class HotelBookingParser : ExperienceParser {
    override val experienceId = ExperienceId.HOTEL_BOOKING

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("hotel", ignoreCase = true) ||
            text.contains("reservation", ignoreCase = true) ||
            text.contains("booking confirmation", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "hotel_booking")
            extractHotelName(text)?.let { put("hotel_name", it) }
            extractDate(text)?.let { put("check_in", it) }
            extractConfirmationNumber(text)?.let { put("confirmation", it) }
            extractAmount(text, text.lines())?.let { put("total", it) }
        }
    }
}

// Travel helpers

private val flightNumberRegex = """(?i)([A-Z]{2,3}\s*\d{2,4})""".toRegex()
private val pnrRegex = """(?i)(?:PNR|booking reference|reservation code|confirmation)[\s:]*([A-Z0-9]{5,8})""".toRegex()
private val airportCodeRegex = """\b([A-Z]{3})\b""".toRegex()
private val timeRegex = """\b(\d{1,2}:\d{2}(?:\s*[AaPp][Mm])?)""".toRegex()
private val seatRegex = """(?i)(?:seat|seat number)[\s:]*([A-Z0-9]+)""".toRegex()
private val hotelNameRegex = """(?i)(?:hotel|property)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+){0,3})""".toRegex()
private val confirmationRegex = """(?i)(?:confirmation|reference|booking)[\s#:]*([A-Z0-9]{6,10})""".toRegex()
private val passengerRegex = """(?i)(?:passenger|name|mr|mrs|ms)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+){1,2})""".toRegex()
private val carrierRegex = """(?i)(?:airlines|airways|airline|operated by)[\s:]*([A-Z][a-zA-Z\s]+)""".toRegex()
private val gateRegex = """(?i)(?:gate)[\s:]*([A-Z0-9]{1,4})""".toRegex()

internal fun extractPassenger(text: String): String? {
    return passengerRegex.find(text)?.groups?.get(1)?.value?.trim()
}

internal fun extractCarrier(text: String): String? {
    return carrierRegex.find(text)?.groups?.get(1)?.value?.trim()
}

internal fun extractGate(text: String): String? {
    return gateRegex.find(text)?.groups?.get(1)?.value?.trim()
}

internal fun extractFlightNumber(text: String): String? {
    return flightNumberRegex.find(text)?.value?.replace(" ", "")
}

internal fun extractPnr(text: String): String? {
    return pnrRegex.find(text)?.groups?.get(1)?.value?.uppercase()
}

internal fun extractAirportCodes(text: String): List<String> {
    return airportCodeRegex.findAll(text).map { it.value }.take(4).toList()
}

internal fun extractTime(text: String): String? {
    return timeRegex.find(text)?.value
}

internal fun extractSeat(text: String): String? {
    return seatRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractHotelName(text: String): String? {
    return hotelNameRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractConfirmationNumber(text: String): String? {
    return confirmationRegex.find(text)?.groups?.get(1)?.value?.uppercase()
}


// Extended travel parsers (ground/sea transport and trip extras).

/** Extracts train ticket fields (fare, departure date, operator, booking ref). */
class TrainTicketParser : KeywordDocumentParser(
    ExperienceId.TRAIN_TICKET,
    typeName = "train_ticket",
    keywords = listOf("train", "railway", "rail ticket", "قطار", "تذكرة قطار", "platform"),
    amountKey = "fare",
    dateKey = "departure_date",
    providerKey = "operator",
    withReference = true
)

/** Extracts bus ticket fields (fare, departure date, operator). */
class BusTicketParser : KeywordDocumentParser(
    ExperienceId.BUS_TICKET,
    typeName = "bus_ticket",
    keywords = listOf("bus ticket", "coach", "باص", "حافلة", "أتوبيس", "go bus", "flixbus"),
    amountKey = "fare",
    dateKey = "departure_date",
    providerKey = "operator",
    withReference = true
)

/** Extracts ferry ticket fields (fare, sailing date, operator). */
class FerryTicketParser : KeywordDocumentParser(
    ExperienceId.FERRY_TICKET,
    typeName = "ferry_ticket",
    keywords = listOf("ferry", "عبارة", "معدية", "sailing", "port"),
    amountKey = "fare",
    dateKey = "sailing_date",
    providerKey = "operator",
    withReference = true
)

/** Extracts travel insurance fields (insurer, validity). */
class TravelInsuranceParser : KeywordDocumentParser(
    ExperienceId.TRAVEL_INSURANCE,
    typeName = "travel_insurance",
    keywords = listOf("travel insurance", "trip insurance", "تأمين السفر", "schengen insurance"),
    dateKey = "expiry_date",
    providerKey = "insurer",
    withReference = true
)

/** Matches travel checklists; extracts trip date if present. */
class TravelChecklistParser : KeywordDocumentParser(
    ExperienceId.TRAVEL_CHECKLIST,
    typeName = "travel_checklist",
    keywords = listOf("travel checklist", "packing list", "قائمة السفر", "passport", "charger"),
    dateKey = "trip_date"
)

/** Extracts airport lounge access details (lounge name, date). */
class AirportLoungeParser : KeywordDocumentParser(
    ExperienceId.AIRPORT_LOUNGE,
    typeName = "airport_lounge",
    keywords = listOf("lounge", "priority pass", "صالة", "loungekey", "marhaba"),
    dateKey = "visit_date",
    providerKey = "lounge",
    withReference = true
)

