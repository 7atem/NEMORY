package com.vaultbrain.core.common.metadata

import com.vaultbrain.shared.model.Classification
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Currency
import java.util.Locale

/** Stable domain types used between SDK adapters, validation, and persistence. */
enum class MetadataFieldType {
    TEXT,
    DECIMAL,
    CURRENCY,
    DATE,
    NON_NEGATIVE_INTEGER,
    HTTP_URL,
    ENUM
}

/**
 * Strict, locale-tolerant normalization for metadata that is safe to persist and query.
 * Ambiguous numeric dates such as 03/04/2027 are rejected rather than guessed.
 */
object MetadataValueNormalizer {
    private val decimalKeys = setOf("total", "tax", "amount", "price")
    private val currencyKeys = setOf("currency")
    private val dateKeys = setOf(
        "date", "purchase_date", "due_date", "prescription_date", "result_date", "dob",
        "expiry_date", "check_in", "check_out", "warranty_expiry", "deadline", "return_by"
    )
    private val integerKeys = setOf("refills", "release_year", "season", "episode")
    private val urlKeys = setOf("url", "website", "provider_url")

    fun typeForKey(key: String): MetadataFieldType = when (key) {
        in decimalKeys -> MetadataFieldType.DECIMAL
        in currencyKeys -> MetadataFieldType.CURRENCY
        in dateKeys -> MetadataFieldType.DATE
        in integerKeys -> MetadataFieldType.NON_NEGATIVE_INTEGER
        in urlKeys -> MetadataFieldType.HTTP_URL
        else -> MetadataFieldType.TEXT
    }

    fun normalize(
        key: String,
        rawValue: String,
        fieldType: MetadataFieldType = typeForKey(key)
    ): String? {
        val value = rawValue
            .filter { it >= ' ' || it == '\n' || it == '\t' }
            .trim()
            .take(MAX_VALUE_CHARS)
        if (value.isBlank()) return null

        return when (fieldType) {
            MetadataFieldType.TEXT -> value
            MetadataFieldType.DECIMAL -> normalizeDecimal(value)
            MetadataFieldType.CURRENCY -> normalizeCurrency(value)
            MetadataFieldType.DATE -> parseDate(value)?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            MetadataFieldType.NON_NEGATIVE_INTEGER -> normalizeDigits(value)
                .toIntOrNull()
                ?.takeIf { it >= 0 }
                ?.toString()
            MetadataFieldType.HTTP_URL -> value.takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("http://", ignoreCase = true)
            }
            MetadataFieldType.ENUM -> value.lowercase(Locale.ROOT)
        }
    }

    /** Existing deterministic/user-visible values are normalized when valid and preserved otherwise. */
    fun normalizeExisting(values: Map<String, String>): Map<String, String> = values.mapValues { (key, value) ->
        normalize(key, value) ?: value.trim().take(MAX_VALUE_CHARS)
    }

    /** New extractor/model output is admitted only when it satisfies its declared field type. */
    fun normalizeTypedValues(values: Map<String, String>): Map<String, String> = values.mapNotNull { (key, value) ->
        normalize(key, value)?.let { key to it }
    }.toMap()

    fun parseDateToEpochMillis(value: String): Long? = parseDate(value)
        ?.atStartOfDay(ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()

    private fun normalizeDecimal(raw: String): String? {
        var value = normalizeDigits(raw)
            .replace(Regex("[^0-9,.'+-]"), "")
            .replace("'", "")
        if (value.count { it == '+' || it == '-' } > 1) return null
        if ((value.contains('+') || value.contains('-')) && value.first() !in setOf('+', '-')) return null
        value = value.removePrefix("+")
        if (value.none(Char::isDigit)) return null

        val dot = value.lastIndexOf('.')
        val comma = value.lastIndexOf(',')
        val decimalSeparator = when {
            dot >= 0 && comma >= 0 -> if (dot > comma) '.' else ','
            dot >= 0 -> inferSingleSeparator(value, '.')
            comma >= 0 -> inferSingleSeparator(value, ',')
            else -> null
        }
        val canonical = buildString {
            value.forEachIndexed { index, char ->
                when {
                    char.isDigit() || (index == 0 && char == '-') -> append(char)
                    char == decimalSeparator && index == value.lastIndexOf(decimalSeparator) -> append('.')
                }
            }
        }
        return runCatching { BigDecimal(canonical).stripTrailingZeros().toPlainString() }
            .getOrNull()
            ?.takeIf { it.length <= MAX_DECIMAL_CHARS }
    }

    private fun inferSingleSeparator(value: String, separator: Char): Char? {
        val occurrences = value.count { it == separator }
        val digitsAfter = value.length - value.lastIndexOf(separator) - 1
        return when {
            digitsAfter in 1..2 -> separator
            occurrences > 1 && digitsAfter != 3 -> null
            else -> null // A single/grouped three-digit suffix is treated as a thousands separator.
        }
    }

    private fun normalizeCurrency(raw: String): String? {
        val value = normalizeDigits(raw).trim().uppercase(Locale.ROOT).replace(" ", "")
        return when (value) {
            "$", "US$", "USD" -> "USD"
            "€", "EUR" -> "EUR"
            "£", "GBP" -> "GBP"
            "EGP", "LE", "L.E", "ج.م", "جنيه", "جنيهات" -> "EGP"
            "SAR", "ر.س", "ريال", "ريالسعودي" -> "SAR"
            "AED", "د.إ", "درهم" -> "AED"
            else -> value.takeIf { candidate ->
                ISO_CURRENCY.matches(candidate) && runCatching {
                    Currency.getInstance(candidate)
                }.isSuccess
            }
        }
    }

    private fun parseDate(raw: String): LocalDate? {
        val value = normalizeDigits(raw).trim()
        parseWithFormatters(value)?.let { return it }

        val numeric = NUMERIC_DATE.matchEntire(value)
        if (numeric != null) {
            val first = numeric.groupValues[1].toIntOrNull() ?: return null
            val second = numeric.groupValues[2].toIntOrNull() ?: return null
            val year = numeric.groupValues[3].toIntOrNull()?.takeIf { it in MIN_YEAR..MAX_YEAR }
                ?: return null
            val (day, month) = when {
                first > 12 && second in 1..12 -> first to second
                second > 12 && first in 1..12 -> second to first
                else -> return null
            }
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }

        val mmYy = MM_YY_DATE.matchEntire(value)
        if (mmYy != null) {
            val month = mmYy.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            val year = mmYy.groupValues[2].toInt() + 2000
            return runCatching { LocalDate.of(year, month, 1) }.getOrNull()
        }

        return null
    }

    private fun parseWithFormatters(value: String): LocalDate? = STRICT_DATE_FORMATTERS.firstNotNullOfOrNull {
        runCatching { LocalDate.parse(value, it) }.getOrNull()
    }

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
                    in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
                    '٫' -> '.'
                    '٬' -> ','
                    else -> char
                }
            )
        }
    }

    private const val MAX_VALUE_CHARS = 200
    private const val MAX_DECIMAL_CHARS = 40
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2200
    private val ISO_CURRENCY = Regex("[A-Z]{3}")
    private val NUMERIC_DATE = Regex("(\\d{1,2})[/.](\\d{1,2})[/.](\\d{4})")
    private val MM_YY_DATE = Regex("(\\d{1,2})[-/](\\d{2})")
    private val STRICT_DATE_FORMATTERS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu/M/d", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu")
            .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM d, uuuu")
            .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    )
}

data class MetadataDateProjection(
    val expiryDate: Long? = null,
    val secondaryAlertDate: Long? = null
)

/** Maps canonical metadata dates into the existing indexed/reminder domain fields. */
object MetadataDateProjector {
    fun project(
        classification: Classification?,
        metadata: Map<String, String>,
        now: Long = System.currentTimeMillis()
    ): MetadataDateProjection {
        val expiry = when (classification) {
            Classification.PASSPORT,
            Classification.IDENTITY_DOCUMENT -> metadata.date("expiry_date")
            Classification.INVOICE -> metadata.date("due_date")
            Classification.WARRANTY_CARD -> metadata.date("warranty_expiry")
            else -> null
        }
        val alertBase = when (classification) {
            Classification.TICKET -> metadata.date("date")?.minus(DAY_MILLIS)
            Classification.HOTEL -> metadata.date("check_in")?.minus(DAY_MILLIS)
            Classification.INVOICE -> expiry?.minus(3 * DAY_MILLIS)
            Classification.PASSPORT,
            Classification.IDENTITY_DOCUMENT,
            Classification.WARRANTY_CARD -> expiry?.minus(7 * DAY_MILLIS)
            else -> null
        }
        return MetadataDateProjection(
            expiryDate = expiry,
            secondaryAlertDate = alertBase?.takeIf { it > now }
        )
    }

    private fun Map<String, String>.date(key: String): Long? =
        get(key)?.let(MetadataValueNormalizer::parseDateToEpochMillis)

    private const val DAY_MILLIS = 24L * 60 * 60 * 1_000
}
