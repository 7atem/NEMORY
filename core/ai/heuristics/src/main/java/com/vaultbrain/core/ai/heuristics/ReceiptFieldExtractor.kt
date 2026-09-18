package com.vaultbrain.core.ai.heuristics

import com.vaultbrain.core.common.metadata.MetadataFieldType
import com.vaultbrain.core.common.metadata.MetadataValueNormalizer

/** Structured, deterministic extraction for OCR text whose visual rows have been restored. */
internal object ReceiptFieldExtractor {

    data class Result(
        val merchant: String?,
        val metadata: Map<String, String>
    )

    fun extract(text: String): Result {
        val lines = text.lineSequence()
            .map { it.trim().replace(WHITESPACE, " ") }
            .filter(String::isNotBlank)
            .toList()
        if (lines.isEmpty()) return Result(null, emptyMap())

        val merchant = findMerchant(lines)
        val items = extractItems(lines)
        val purchaseDate = DATE.find(text)?.value
        val purchaseTime = TIME.find(text)?.value?.replace(WHITESPACE, " ")

        val metadata = buildMap {
            merchant?.let { put("merchant", it) }
            labelledAmount(lines, TOTAL_LABEL)?.let { put("total", it) }
            labelledAmount(lines, SUBTOTAL_LABEL)?.let { put("subtotal", it) }
            labelledAmount(lines, TAX_LABEL)?.let { put("tax", it) }
            labelledAmount(lines, TENDER_LABEL)?.let { put("amount_tendered", it) }
            labelledAmount(lines, CHANGE_LABEL)?.let { put("change", it) }
            CASHIER.find(text)?.groupValues?.getOrNull(1)?.cleanValue()?.let { put("cashier", it) }
            ITEM_COUNT.find(text)?.groupValues?.getOrNull(1)?.let { put("item_count", it) }
            purchaseDate?.let { rawDate ->
                put("date_text", rawDate)
                MetadataValueNormalizer.normalize("purchase_date", rawDate, MetadataFieldType.DATE)
                    ?.let { put("purchase_date", it) }
            }
            purchaseTime?.let { put("purchase_time", it) }
            findTransactionNumber(text)?.let { put("transaction_number", it) }
            findAddress(lines, merchant)?.let { put("address", it) }
            if (items.isNotEmpty()) {
                put("items", items.joinToString(" | ") { (name, amount) -> "$name: $amount" })
            }
        }
        return Result(merchant, metadata)
    }

    private fun findMerchant(lines: List<String>): String? {
        val firstControlIndex = lines.indexOfFirst(::isControlLine).let { if (it < 0) lines.size else it }
        return lines.take(MAX_MERCHANT_SCAN_LINES)
            .mapIndexedNotNull { index, line ->
                if (!isMerchantCandidate(line)) return@mapIndexedNotNull null
                val lower = line.lowercase()
                var score = (MAX_MERCHANT_SCAN_LINES - index) * 3
                if (index < firstControlIndex) score += 12
                if (line.any(Char::isLetter) && line.none(Char::isLowerCase)) score += 14
                if (line.count(Char::isWhitespace) >= 1) score += 8
                if (MERCHANT_TERMS.any(lower::contains)) score += 32
                if (line.none(Char::isDigit)) score += 8
                line to score
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun isMerchantCandidate(line: String): Boolean {
        val trimmed = line.trim()
        val lower = trimmed.lowercase()
        return trimmed.length in 3..60 &&
            trimmed.any(Char::isLetter) &&
            !isControlLine(trimmed) &&
            !looksLikeAddress(trimmed) &&
            !looksLikePricedItem(trimmed) &&
            GENERIC_NON_MERCHANT.none { lower == it || lower.startsWith("$it:") }
    }

    private fun isControlLine(line: String): Boolean {
        val lower = line.lowercase().trim()
        return CONTROL_TERMS.any { term -> lower == term || lower.startsWith("$term ") || lower.startsWith("$term:") }
    }

    private fun labelledAmount(lines: List<String>, labels: Set<String>): String? {
        return lines.firstNotNullOfOrNull { line ->
            val lower = line.lowercase().trim()
            val matchesLabel = labels.any { label ->
                lower == label || lower.startsWith("$label ") || lower.startsWith("$label:")
            }
            if (!matchesLabel) return@firstNotNullOfOrNull null
            AMOUNT.findAll(line).lastOrNull()?.groupValues?.getOrNull(1)?.normalizeAmount()
        }
    }

    private fun extractItems(lines: List<String>): List<Pair<String, String>> = lines.mapNotNull { line ->
        val match = PRICED_ITEM_WITH_CURRENCY.matchEntire(line)
            ?: PRICED_ITEM_WITH_COLUMNS.matchEntire(line)
            ?: return@mapNotNull null
        val name = match.groupValues[1].trim().trimEnd(':', '-')
        val amount = match.groupValues[2].normalizeAmount() ?: return@mapNotNull null
        if (name.length !in 2..80 || isControlLine(name) || looksLikeAddress(name)) return@mapNotNull null
        if (name.none(Char::isLetter)) return@mapNotNull null
        if (DATE_FRAGMENT.containsMatchIn(name)) return@mapNotNull null
        if (ITEM_ADMIN_TERMS.containsMatchIn(name)) return@mapNotNull null
        name to amount
    }.distinct().take(MAX_ITEMS)

    private fun findAddress(lines: List<String>, merchant: String?): String? {
        val merchantIndex = merchant?.let(lines::indexOf) ?: return null
        return lines.drop(merchantIndex + 1)
            .takeWhile { !isControlLine(it) && !looksLikePricedItem(it) }
            .take(MAX_ADDRESS_LINES)
            .filter { looksLikeAddress(it) || (it.length in 3..50 && it.none(Char::isDigit)) }
            .map { it.trim().trimEnd(',') }
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(", ")
            ?.take(MAX_ADDRESS_CHARS)
    }

    private fun findTransactionNumber(text: String): String? {
        DIRECT_TRANSACTION.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        // Loose fallback: only trust a long number that sits on a trans/txn-labelled line.
        if (!text.contains("trans", ignoreCase = true) && !text.contains("txn", ignoreCase = true)) return null
        return text.lineSequence()
            .filter { it.contains("trans", ignoreCase = true) || it.contains("txn", ignoreCase = true) }
            .firstNotNullOfOrNull { line -> LONG_NUMBER.find(line)?.value }
    }

    private fun looksLikeAddress(value: String): Boolean =
        ADDRESS_NUMBER.containsMatchIn(value) || ADDRESS_TERMS.containsMatchIn(value)

    private fun looksLikePricedItem(value: String): Boolean =
        PRICED_ITEM_WITH_CURRENCY.matches(value) || PRICED_ITEM_WITH_COLUMNS.matches(value)

    private fun String.normalizeAmount(): String? = MetadataValueNormalizer.normalize(
        key = "total",
        rawValue = this,
        fieldType = MetadataFieldType.DECIMAL
    )

    private fun String.cleanValue(): String? = trim().trimEnd(',', '.', ':').takeIf(String::isNotBlank)

    private val WHITESPACE = Regex("\\s+")
    private val DATE_FRAGMENT = Regex("\\d{1,2}[-/.]\\d{1,2}")
    private val ITEM_ADMIN_TERMS = Regex(
        "(?i)\\b(?:account|approval|ref|reference|trans|txn|terminal|card|visa|mastercard|amex|validation|auth)\\b"
    )
    private val AMOUNT = Regex("([0-9]+(?:[.,][0-9]{1,3})*)")
    private val DATE = Regex("(?<!\\d)\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}(?!\\d)")
    private val TIME = Regex("(?i)\\b\\d{1,2}:\\d{2}(?:\\s*[AP]M)?\\b")
    private val CASHIER = Regex("(?im)^\\s*cashier\\s*[:#-]?\\s*([\\p{L}][\\p{L} .'-]{1,50})\\s*$")
    private val ITEM_COUNT = Regex("(?i)\\bitem\\s*count\\s*[:#-]?\\s*(\\d{1,4})\\b")
    private val DIRECT_TRANSACTION = Regex("(?i)\\b(?:transaction|trans|txn)\\s*(?:no|number|#)?\\s*[:#-]?\\s*([A-Z0-9-]{4,30})\\b")
    private val LONG_NUMBER = Regex("\\b\\d{8,30}\\b")
    private val ADDRESS_NUMBER = Regex("^\\d{1,6}\\s+\\p{L}")
    private val ADDRESS_TERMS = Regex("(?i)\\b(?:street|st\\.?|road|rd\\.?|avenue|ave\\.?|boulevard|blvd\\.?|lane|ln\\.?|drive|dr\\.?|vegas|cairo|القاهرة)\\b")
    private val PRICED_ITEM_WITH_CURRENCY = Regex(
        "^(.+?)\\s+(?:\\p{Sc}|USD\\s*|EUR\\s*|GBP\\s*|EGP\\s*|SAR\\s*|AED\\s*)([0-9]+(?:[.,][0-9]{1,3})*)$",
        RegexOption.IGNORE_CASE
    )
    private val PRICED_ITEM_WITH_COLUMNS = Regex("^(.+?)\\s{2,}([0-9]+(?:[.,][0-9]{1,3})*)$")

    private val TOTAL_LABEL = setOf(
        "total", "grand total", "amount due", "balance due", "total due",
        "total amount due", "total amount", "pay this amount", "amount to pay"
    )
    private val SUBTOTAL_LABEL = setOf("subtotal", "sub total")
    private val TAX_LABEL = setOf("tax", "no tax", "vat", "gst")
    private val TENDER_LABEL = setOf("tend", "tender", "tendered", "cash tendered", "amount tendered")
    private val CHANGE_LABEL = setOf("change", "change due")
    private val CONTROL_TERMS = TOTAL_LABEL + SUBTOTAL_LABEL + TAX_LABEL + TENDER_LABEL + CHANGE_LABEL + setOf(
        "cashier", "item count", "date", "time", "lane", "clerk", "trans", "transaction", "thanks", "thank you"
    )
    private val MERCHANT_TERMS = setOf(
        "store", "market", "mart", "depot", "grocery", "supermarket", "hypermarket", "shop",
        "restaurant", "cafe", "coffee", "pharmacy", "bakery", "retail", "company", "co."
    )
    private val GENERIC_NON_MERCHANT = setOf("receipt", "invoice", "bill", "sale", "customer copy")

    private const val MAX_MERCHANT_SCAN_LINES = 12
    private const val MAX_ADDRESS_LINES = 3
    private const val MAX_ADDRESS_CHARS = 160
    private const val MAX_ITEMS = 30
}
