package com.vaultbrain.core.ai.rag

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DateRange(val from: Long, val to: Long, val labelEn: String, val labelAr: String)

sealed interface QueryIntent {
    data class SumAmounts(val merchant: String?, val range: DateRange?) : QueryIntent
    data class FilterByMerchant(val merchant: String, val range: DateRange?) : QueryIntent
    data class FilterByDateRange(val range: DateRange) : QueryIntent
    data class ExpiringSoon(val days: Int = 90) : QueryIntent
    data class UpcomingTravel(val days: Int = 365) : QueryIntent
    data class TripBriefing(val days: Int = 365) : QueryIntent
    data object RecurringSpendIncreases : QueryIntent
    data object MediaBacklog : QueryIntent
    data class MoneyDocuments(val range: DateRange?) : QueryIntent
    data class MissingDocuments(val context: String? = null) : QueryIntent
    data class DocumentReplacementChains(val context: String? = null) : QueryIntent
}

/** Small fail-closed bilingual router. Unrecognized wording falls back to semantic RAG. */
class QueryIntentParser @Inject constructor() {
    fun parse(
        query: String,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): QueryIntent? {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return null
        val range = parseDateRange(normalized, now, zoneId)

        if (EXPIRY_TERMS.any(normalized::contains)) return QueryIntent.ExpiringSoon()
        if (MEDIA_BACKLOG_TERMS.any(normalized::contains)) return QueryIntent.MediaBacklog
        if (RECURRING_TERMS.any(normalized::contains) && CHANGE_TERMS.any(normalized::contains)) {
            return QueryIntent.RecurringSpendIncreases
        }
        if (TRAVEL_TERMS.any(normalized::contains) && BRIEFING_TERMS.any(normalized::contains)) {
            return QueryIntent.TripBriefing()
        }
        if (TRAVEL_TERMS.any(normalized::contains) && UPCOMING_TERMS.any(normalized::contains)) {
            return QueryIntent.UpcomingTravel()
        }
        if (MISSING_TERMS.any(normalized::contains) && DOCUMENT_TERMS.any(normalized::contains)) {
            return QueryIntent.MissingDocuments()
        }
        if (CHAIN_TERMS.any(normalized::contains) || (HISTORY_TERMS.any(normalized::contains) && DOCUMENT_TERMS.any(normalized::contains))) {
            return QueryIntent.DocumentReplacementChains()
        }

        val merchant = extractMerchant(normalized)
        if (TOTAL_TERMS.any(normalized::contains)) return QueryIntent.SumAmounts(merchant, range)
        if (merchant != null) return QueryIntent.FilterByMerchant(merchant, range)
        if (MONEY_TERMS.any(normalized::contains)) return QueryIntent.MoneyDocuments(range)
        if (range != null && DOCUMENT_TERMS.any(normalized::contains)) return QueryIntent.FilterByDateRange(range)
        return null
    }

    private fun parseDateRange(query: String, now: Long, zoneId: ZoneId): DateRange? {
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return when {
            query.contains("this month") || query.contains("هذا الشهر") -> {
                val start = today.withDayOfMonth(1)
                range(start, start.plusMonths(1), zoneId, "this month", "هذا الشهر")
            }
            query.contains("last month") || query.contains("previous month") || query.contains("الشهر الماضي") -> {
                val start = today.withDayOfMonth(1).minusMonths(1)
                range(start, start.plusMonths(1), zoneId, "last month", "الشهر الماضي")
            }
            query.contains("this year") || query.contains("هذه السنة") || query.contains("هذا العام") -> {
                val start = today.withDayOfYear(1)
                range(start, start.plusYears(1), zoneId, "this year", "هذا العام")
            }
            else -> LAST_DAYS.find(query)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { days ->
                if (days !in 1..3650) return@let null
                range(today.minusDays(days.toLong() - 1), today.plusDays(1), zoneId, "last $days days", "آخر $days يومًا")
            }
        }
    }

    private fun range(start: LocalDate, exclusiveEnd: LocalDate, zoneId: ZoneId, en: String, ar: String) = DateRange(
        from = start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        to = exclusiveEnd.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1,
        labelEn = en,
        labelAr = ar
    )

    private fun extractMerchant(query: String): String? {
        val match = MERCHANT.find(query) ?: return null
        return match.groupValues[1]
            .replace(TEMPORAL_SUFFIX, "")
            .trim(' ', '.', ',', '?', '!', '"', '\'')
            .takeIf { it.length in 2..60 && it !in GENERIC_MERCHANT_VALUES }
    }

    private companion object {
        val BRIEFING_TERMS = listOf("briefing", "prepare", "plan", "checklist", "ملخص", "استعداد", "خطط")
        val RECURRING_TERMS = listOf("recurring", "subscription", "subscriptions", "monthly charge", "متكرر", "اشتراك", "اشتراكات")
        val CHANGE_TERMS = listOf("increase", "increased", "went up", "compare", "change", "ارتفع", "زيادة", "قارن", "تغير")
        val MEDIA_BACKLOG_TERMS = listOf(
            "watchlist", "reading list", "what should i watch", "what should i read",
            "unfinished movie", "unfinished book", "unfinished media",
            "قائمة المشاهدة", "قائمة القراءة", "ماذا أشاهد", "ماذا أقرأ", "غير مكتمل"
        )
        val EXPIRY_TERMS = listOf("expire", "expiry", "expiring", "تنتهي", "انتهاء", "صلاحية")
        val TRAVEL_TERMS = listOf("travel", "trip", "flight", "hotel", "سفر", "رحلة", "فندق")
        val UPCOMING_TERMS = listOf("upcoming", "next", "soon", "القادمة", "القادم", "قريب")
        val TOTAL_TERMS = listOf("how much", "total", "spent", "spending", "إجمالي", "المجموع", "أنفقت", "مصروف")
        val MONEY_TERMS = listOf("receipts", "invoices", "إيصالات", "فواتير")
        val DOCUMENT_TERMS = listOf("document", "documents", "items", "saved", "مستند", "مستندات", "عناصر", "محفوظ")
        val MISSING_TERMS = listOf("missing", "forgot", "missing documents", "مفقود", "نسيت", "ناقص")
        val CHAIN_TERMS = listOf("replacement chain", "renewals", "سلسلة", "تجديدات")
        val HISTORY_TERMS = listOf("history", "previous", "old", "تاريخ", "سابق", "قديم")
        val LAST_DAYS = Regex("(?:last|past|آخر)\\s+(\\d{1,4})\\s+(?:days?|يوم|أيام|يومًا)")
        val MERCHANT = Regex("(?:\\bat\\b|\\bfrom\\b|\\bmerchant\\b|من|لدى)\\s+[\"']?([\\p{L}\\p{N} .&'_-]{2,80})")
        val TEMPORAL_SUFFIX = Regex("\\s+(?:this month|last month|previous month|this year|هذا الشهر|الشهر الماضي|هذه السنة|هذا العام|last|past|آخر).*$")
        val GENERIC_MERCHANT_VALUES = setOf("my vault", "the vault", "الخزنة", "خزنتي")
    }
}
