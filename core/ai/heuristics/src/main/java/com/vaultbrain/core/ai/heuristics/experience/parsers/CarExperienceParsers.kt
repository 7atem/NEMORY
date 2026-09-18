package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.VaultItem

/** Extracts car service / maintenance fields. */
class CarServiceParser : ExperienceParser {
    override val experienceId = ExperienceId.CAR_SERVICE

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("service", ignoreCase = true) &&
            (text.contains("car", ignoreCase = true) || text.contains("vehicle", ignoreCase = true)) ||
            text.contains("maintenance", ignoreCase = true) ||
            text.contains("oil change", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "car_service")
            extractMileage(text)?.let { put("mileage", it) }
            extractDate(text)?.let { put("service_date", it) }
            extractServiceType(text)?.let { put("service_type", it) }
            extractAmount(text, text.lines())?.let { put("cost", it) }
        }
    }
}

/** Extracts fuel receipt fields. */
class FuelReceiptParser : ExperienceParser {
    override val experienceId = ExperienceId.FUEL_RECEIPT

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("fuel", ignoreCase = true) ||
            text.contains("gas", ignoreCase = true) ||
            text.contains("petrol", ignoreCase = true) ||
            text.contains("diesel", ignoreCase = true) ||
            text.contains("liter", ignoreCase = true) ||
            text.contains("gallon", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "fuel_receipt")
            extractFuelType(text)?.let { put("fuel_type", it) }
            extractVolume(text)?.let { put("volume", it) }
            extractAmount(text, text.lines())?.let { put("total", it) }
            extractDate(text)?.let { put("date", it) }
            extractStationName(text)?.let { put("station", it) }
        }
    }
}

// Car helpers

private val mileageRegex = """(?i)(?:mileage|km|odometer|km reading)[\s:]*([\d.,]+\s*(?:km|miles|mi)?)""".toRegex()
private val serviceTypeRegex = """(?i)(oil change|tire|brake|battery|inspection|service|maintenance|filter)""".toRegex()
private val fuelTypeRegex = """(?i)(petrol|diesel|gasoline|premium|regular|electric|hybrid)""".toRegex()
private val volumeRegex = """(?i)(\d+\.?\d*)\s*(?:liter|litre|gal|gallon|l)""".toRegex()
private val stationRegex = """(?i)(?:station|gas station|petrol station)[\s:]*([A-Z][a-zA-Z0-9\s]{2,30})""".toRegex()

internal fun extractMileage(text: String): String? {
    return mileageRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractServiceType(text: String): String? {
    return serviceTypeRegex.find(text)?.value?.lowercase()?.replaceFirstChar { it.titlecase() }
}

internal fun extractFuelType(text: String): String? {
    return fuelTypeRegex.find(text)?.value?.lowercase()?.replaceFirstChar { it.titlecase() }
}

internal fun extractVolume(text: String): String? {
    return volumeRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractStationName(text: String): String? {
    return stationRegex.find(text)?.groups?.get(1)?.value?.trim()
}


// Extended car parsers (registration, financing, tolls, permits, inspection).

/** Extracts car registration fields (expiry, owner authority, plate). */
class CarRegistrationParser : KeywordDocumentParser(
    ExperienceId.CAR_REGISTRATION,
    typeName = "car_registration",
    keywords = listOf("vehicle registration", "car registration", "رخصة سير", "استمارة", "registration card"),
    dateKey = "expiry_date",
    providerKey = "issuing_authority",
    withReference = true
)

/** Extracts car loan fields (installment, due date, lender). */
class CarLoanParser : KeywordDocumentParser(
    ExperienceId.CAR_LOAN,
    typeName = "car_loan",
    keywords = listOf("car loan", "auto loan", "قرض سيارة", "تمويل سيارة", "car finance"),
    amountKey = "installment",
    dateKey = "due_date",
    providerKey = "lender",
    withReference = true
)

/** Extracts toll receipt fields (amount, date). */
class TollReceiptParser : KeywordDocumentParser(
    ExperienceId.TOLL_RECEIPT,
    typeName = "toll_receipt",
    keywords = listOf("toll", "رسوم طريق", "toll gate", "salik", "سالك"),
    amountKey = "amount",
    dateKey = "date",
    providerKey = "operator"
)

/** Extracts parking permit fields (expiry, zone reference). */
class ParkingPermitParser : KeywordDocumentParser(
    ExperienceId.PARKING_PERMIT,
    typeName = "parking_permit",
    keywords = listOf("parking permit", "resident permit", "تصريح انتظار", "تصريح موقف"),
    dateKey = "expiry_date",
    providerKey = "authority",
    withReference = true
)

/** Extracts car inspection fields (expiry/next due, station). */
class CarInspectionParser : KeywordDocumentParser(
    ExperienceId.CAR_INSPECTION,
    typeName = "car_inspection",
    keywords = listOf("vehicle inspection", "car inspection", "فحص السيارة", "الفحص الدوري", "technical inspection"),
    dateKey = "expiry_date",
    providerKey = "station",
    withReference = true
)
