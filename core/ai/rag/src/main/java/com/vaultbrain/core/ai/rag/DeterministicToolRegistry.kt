package com.vaultbrain.core.ai.rag

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only authoritative calculations over active, non-stealth vault records. */
@Singleton
class DeterministicToolRegistry @Inject constructor(
    private val repository: VaultRepository,
    private val intentParser: QueryIntentParser
) {
    suspend fun execute(query: String, now: Long = System.currentTimeMillis()): RagResponse? {
        val intent = intentParser.parse(query, now) ?: return null
        val items = repository.getActive()
        val arabic = ARABIC.containsMatchIn(query)
        return when (intent) {
            is QueryIntent.SumAmounts -> sumAmounts(items, intent, arabic)
            is QueryIntent.FilterByMerchant -> filterByMerchant(items, intent, arabic)
            is QueryIntent.FilterByDateRange -> filterByDateRange(items, intent.range, arabic)
            is QueryIntent.ExpiringSoon -> findExpiringSoon(items, now, intent.days, arabic)
            is QueryIntent.UpcomingTravel -> findUpcomingTravel(items, now, intent.days, arabic)
            is QueryIntent.TripBriefing -> buildTripBriefing(items, now, intent.days, arabic)
            QueryIntent.RecurringSpendIncreases -> recurringSpendIncreases(items, arabic)
            QueryIntent.MediaBacklog -> mediaBacklog(items, arabic)
            is QueryIntent.MoneyDocuments -> moneyDocuments(items, intent.range, arabic)
            is QueryIntent.MissingDocuments -> missingDocuments(items, arabic)
            is QueryIntent.DocumentReplacementChains -> documentReplacementChains(items, arabic)
        }
    }

    private fun sumAmounts(
        items: List<VaultItem>,
        intent: QueryIntent.SumAmounts,
        arabic: Boolean
    ): RagResponse {
        val matching = items.asSequence()
            .filter(::isMoneyDocument)
            .filter { intent.merchant == null || matchesMerchant(it, intent.merchant) }
            .filter { intent.range == null || it.createdAt in intent.range.from..intent.range.to }
            .mapNotNull { item -> amount(item)?.let { value -> Triple(item, currency(item), value) } }
            .toList()
        if (matching.isEmpty()) return noEvidence(
            kind = RagEvidenceKind.TOTAL,
            arabic = arabic,
            en = "No matching recorded amounts",
            ar = "لا توجد مبالغ مسجلة مطابقة"
        )

        val totals = matching.groupBy { it.second }.mapValues { (_, rows) ->
            rows.fold(BigDecimal.ZERO) { total, row -> total.add(row.third) }
        }.toSortedMap()
        val headline = if (totals.size == 1) {
            totals.entries.single().let { "${it.key} ${it.value.normalized()}" }
        } else if (arabic) "الإجماليات حسب العملة" else "Totals by currency"
        val facts = totals.map { (currency, total) ->
            val count = matching.count { it.second == currency }
            if (arabic) "$currency ${total.normalized()} • $count عناصر" else "$currency ${total.normalized()} • $count items"
        } + listOfNotNull(
            intent.merchant?.let { if (arabic) "التاجر: $it" else "Merchant: $it" },
            intent.range?.let { if (arabic) "الفترة: ${it.labelAr}" else "Period: ${it.labelEn}" }
        )
        val answer = if (arabic) {
            "حُسبت النتيجة محليًا من المبالغ المسجلة فقط. تحقّق من السجلات المالية الرسمية."
        } else {
            "Calculated locally from recorded amounts only. Verify with official financial records."
        }
        return RagResponse(
            answer = answer,
            sources = matching.map { it.first }.distinctBy(VaultItem::id).take(MAX_TOOL_SOURCES),
            confidence = 1f,
            evidence = RagEvidence(RagEvidenceKind.TOTAL, headline, facts)
        )
    }

    private fun filterByMerchant(
        items: List<VaultItem>,
        intent: QueryIntent.FilterByMerchant,
        arabic: Boolean
    ): RagResponse {
        val matching = items.filter { item ->
            matchesMerchant(item, intent.merchant) &&
                (intent.range == null || item.createdAt in intent.range.from..intent.range.to)
        }
        val headline = if (arabic) "${matching.size} عناصر مطابقة" else "${matching.size} matching items"
        val facts = listOfNotNull(
            if (arabic) "التاجر: ${intent.merchant}" else "Merchant: ${intent.merchant}",
            intent.range?.let { if (arabic) "الفترة: ${it.labelAr}" else "Period: ${it.labelEn}" }
        )
        return listResponse(matching, headline, facts, arabic)
    }

    private fun filterByDateRange(items: List<VaultItem>, range: DateRange, arabic: Boolean): RagResponse {
        val matching = items.filter { it.createdAt in range.from..range.to }
        val headline = if (arabic) "${matching.size} عناصر محفوظة" else "${matching.size} saved items"
        val facts = listOf(if (arabic) "الفترة: ${range.labelAr}" else "Period: ${range.labelEn}")
        return listResponse(matching, headline, facts, arabic)
    }

    private fun moneyDocuments(items: List<VaultItem>, range: DateRange?, arabic: Boolean): RagResponse {
        val matching = items.filter { item ->
            isMoneyDocument(item) && (range == null || item.createdAt in range.from..range.to)
        }
        val headline = if (arabic) "${matching.size} إيصالات وفواتير" else "${matching.size} receipts and invoices"
        val facts = listOfNotNull(range?.let { if (arabic) "الفترة: ${it.labelAr}" else "Period: ${it.labelEn}" })
        return listResponse(matching, headline, facts, arabic)
    }

    private fun findExpiringSoon(
        items: List<VaultItem>,
        now: Long,
        days: Int,
        arabic: Boolean
    ): RagResponse {
        val until = now + days * MILLIS_PER_DAY
        val matching = items.filter { item ->
            item.expiryDate?.let { it in now..until } == true
        }.sortedBy(VaultItem::expiryDate)
        val headline = if (arabic) "${matching.size} عناصر تنتهي قريبًا" else "${matching.size} items expiring soon"
        val facts = matching.take(MAX_SUPPORTING_FACTS).map { item ->
            "${item.title} • ${formatDate(item.expiryDate!!)}"
        }
        return listResponse(matching, headline, facts, arabic, RagEvidenceKind.EXPIRING)
    }

    private fun findUpcomingTravel(
        items: List<VaultItem>,
        now: Long,
        days: Int,
        arabic: Boolean
    ): RagResponse {
        val until = now + days * MILLIS_PER_DAY
        val matching = items.filter { item ->
            item.effectiveClassification in TRAVEL_CATEGORIES &&
                item.actionDate()?.let { it in now..until } == true
        }.sortedBy { it.actionDate() }
        val headline = if (arabic) "${matching.size} عناصر سفر قادمة" else "${matching.size} upcoming travel items"
        val facts = matching.take(MAX_SUPPORTING_FACTS).map { item ->
            "${item.title} • ${formatDate(item.actionDate()!!)}"
        }
        return listResponse(matching, headline, facts, arabic, RagEvidenceKind.UPCOMING_TRAVEL)
    }

    private fun buildTripBriefing(
        items: List<VaultItem>,
        now: Long,
        days: Int,
        arabic: Boolean
    ): RagResponse {
        val until = now + days * MILLIS_PER_DAY
        val upcoming = items.filter { item ->
            item.effectiveClassification in TRAVEL_CATEGORIES &&
                item.actionDate()?.let { it in now..until } == true
        }.sortedBy { it.actionDate() }
        if (upcoming.isEmpty()) return noEvidence(
            kind = RagEvidenceKind.TRIP_BRIEFING,
            arabic = arabic,
            en = "No upcoming trip evidence",
            ar = "لا توجد أدلة على رحلة قادمة"
        )

        val first = upcoming.first()
        val firstDate = first.actionDate()!!
        val tripId = first.metadataValue("trip_id", "trip", "trip_name")?.normalizedSearch()
        val matching = upcoming.filter { item ->
            if (tripId != null) {
                item.metadataValue("trip_id", "trip", "trip_name")?.normalizedSearch() == tripId
            } else {
                kotlin.math.abs(item.actionDate()!! - firstDate) <= TRIP_CLUSTER_MILLIS
            }
        }
        val facts = matching.take(MAX_SUPPORTING_FACTS).map { item ->
            buildList {
                add(item.title)
                add(formatDate(item.actionDate()!!))
                item.metadataValue("flight_number")?.let { add(if (arabic) "الرحلة $it" else "Flight $it") }
                item.metadataValue("gate")?.let { add(if (arabic) "البوابة $it" else "Gate $it") }
                item.metadataValue("seat")?.let { add(if (arabic) "المقعد $it" else "Seat $it") }
                item.metadataValue("hotel")?.let(::add)
            }.joinToString(" | ")
        }
        return RagResponse(
            answer = if (arabic) {
                "جُمعت التفاصيل محليًا من عناصر الرحلة المحفوظة. راجع المصادر قبل السفر."
            } else {
                "Assembled locally from saved trip details. Review the source items before travel."
            },
            sources = matching.take(MAX_TOOL_SOURCES),
            confidence = 1f,
            evidence = RagEvidence(
                kind = RagEvidenceKind.TRIP_BRIEFING,
                headline = if (arabic) "ملخص الرحلة | ${matching.size} عناصر"
                    else "Trip briefing | ${matching.size} items",
                supportingFacts = facts
            )
        )
    }

    private fun recurringSpendIncreases(items: List<VaultItem>, arabic: Boolean): RagResponse {
        data class Charge(
            val item: VaultItem,
            val provider: String,
            val currency: String,
            val amount: BigDecimal
        )

        val charges = items.mapNotNull { item ->
            if (!item.isRecurringCharge()) return@mapNotNull null
            val provider = item.metadataValue(
                "merchant", "supplier", "biller", "service_provider", "provider"
            ) ?: return@mapNotNull null
            val value = amount(item) ?: return@mapNotNull null
            Charge(item, provider, currency(item), value)
        }
        val increases = charges
            .groupBy { "${it.provider.normalizedSearch()}|${it.currency}" }
            .values
            .mapNotNull { group ->
                val ordered = group.sortedBy { it.item.createdAt }
                if (ordered.size < 2) return@mapNotNull null
                val previous = ordered[ordered.lastIndex - 1]
                val current = ordered.last()
                if (current.amount <= previous.amount) return@mapNotNull null
                Triple(previous, current, current.amount.subtract(previous.amount))
            }
            .sortedByDescending { it.third }

        if (increases.isEmpty()) return noEvidence(
            kind = RagEvidenceKind.RECURRING_SPEND,
            arabic = arabic,
            en = "No recorded recurring-charge increases",
            ar = "لا توجد زيادات مسجلة في الدفعات المتكررة"
        )
        val facts = increases.take(MAX_SUPPORTING_FACTS).map { (previous, current, delta) ->
            val percent = if (previous.amount.signum() == 0) null else delta
                .multiply(BigDecimal(100))
                .divide(previous.amount, 1, RoundingMode.HALF_UP)
            buildString {
                append(current.provider)
                append(" | ${current.currency} ${previous.amount.normalized()} -> ${current.currency} ${current.amount.normalized()}")
                append(" | +${delta.normalized()}")
                percent?.let { append(" (${it.normalized()}%)") }
            }
        }
        val sources = increases.flatMap { listOf(it.second.item, it.first.item) }
            .distinctBy(VaultItem::id)
            .take(MAX_TOOL_SOURCES)
        return RagResponse(
            answer = if (arabic) {
                "قورنت أحدث دفعتين متكررتين لكل مزود وعملة محليًا. تحقق من كشوفك الرسمية."
            } else {
                "Compared the two latest recurring charges per provider and currency locally. Verify with official statements."
            },
            sources = sources,
            confidence = 1f,
            evidence = RagEvidence(
                kind = RagEvidenceKind.RECURRING_SPEND,
                headline = if (arabic) "${increases.size} زيادات متكررة"
                    else "${increases.size} recurring increases",
                supportingFacts = facts
            )
        )
    }

    private fun missingDocuments(items: List<VaultItem>, arabic: Boolean): RagResponse {
        val missing = mutableListOf<String>()
        val trips = items.filter { it.effectiveClassification == Classification.TICKET }
        val hotels = items.filter { it.effectiveClassification == Classification.HOTEL }
        trips.forEach { trip ->
            val tripDate = trip.actionDate()
            if (tripDate != null && hotels.none { Math.abs((it.actionDate() ?: 0) - tripDate) < TRIP_CLUSTER_MILLIS }) {
                missing.add(if (arabic) "رحلة (${trip.title}) بدون حجز فندق" else "Trip (${trip.title}) missing hotel booking")
            }
        }
        val vehicles = items.filter { it.metadataValue("vehicle", "car") != null }
        val insurances = items.filter { it.effectiveClassification == Classification.LEGAL_DOCUMENT || it.metadataValue("insurance") != null }
        vehicles.forEach { vehicle ->
            val vName = vehicle.metadataValue("vehicle", "car")!!
            if (insurances.none { it.metadataValue("vehicle", "car") == vName }) {
                missing.add(if (arabic) "مركبة ($vName) بدون تأمين" else "Vehicle ($vName) missing insurance")
            }
        }
        if (missing.isEmpty()) return noEvidence(RagEvidenceKind.MISSING_DOCUMENTS, arabic, "No obvious missing documents detected.", "لم يتم اكتشاف مستندات مفقودة واضحة.")
        return RagResponse(
            answer = if (arabic) "التحليل استند إلى القواعد الاستدلالية (رحلات بدون فنادق، سيارات بدون تأمين)." else "Analysis based on heuristics (trips without hotels, cars without insurance).",
            sources = items.take(MAX_TOOL_SOURCES),
            confidence = 1f,
            evidence = RagEvidence(RagEvidenceKind.MISSING_DOCUMENTS, if (arabic) "المستندات المفقودة المحتملة" else "Potential Missing Documents", missing.take(MAX_SUPPORTING_FACTS))
        )
    }

    private fun documentReplacementChains(items: List<VaultItem>, arabic: Boolean): RagResponse {
        // We'll rely on the RAG loop to use relationships, but for this deterministic check, 
        // we can group items by identical tags or merchants and find overlapping periods.
        val renewals = mutableListOf<String>()
        val byMerchant = items.filter { it.isRecurringCharge() }.groupBy { it.metadataValue("merchant", "supplier", "biller") ?: "Unknown" }
        byMerchant.forEach { (merchant, docs) ->
            if (docs.size > 1 && merchant != "Unknown") {
                renewals.add(if (arabic) "تجديدات $merchant (${docs.size} مستندات)" else "$merchant renewals (${docs.size} documents)")
            }
        }
        if (renewals.isEmpty()) return noEvidence(RagEvidenceKind.REPLACEMENT_CHAIN, arabic, "No document chains detected.", "لم يتم اكتشاف سلاسل مستندات.")
        return RagResponse(
            answer = if (arabic) "سلاسل المستندات المكتشفة بناءً على الدفعات المتكررة." else "Detected document chains based on recurring payments.",
            sources = items.take(MAX_TOOL_SOURCES),
            confidence = 1f,
            evidence = RagEvidence(RagEvidenceKind.REPLACEMENT_CHAIN, if (arabic) "سلاسل المستندات" else "Document Chains", renewals.take(MAX_SUPPORTING_FACTS))
        )
    }

    private fun mediaBacklog(items: List<VaultItem>, arabic: Boolean): RagResponse {
        val matching = items.filter { item ->
            item.effectiveClassification in MEDIA_CATEGORIES &&
                item.metadataValue("media_status", "status")
                    ?.normalizedSearch() !in COMPLETED_MEDIA_STATUSES
        }.sortedWith(compareByDescending<VaultItem> { it.isPinned }.thenByDescending { it.createdAt })
        val facts = matching.take(MAX_SUPPORTING_FACTS).map { item ->
            val type = when (item.effectiveClassification) {
                Classification.MOVIE -> if (arabic) "فيلم" else "Movie"
                Classification.TV_SERIES -> if (arabic) "مسلسل" else "TV series"
                Classification.BOOK -> if (arabic) "كتاب" else "Book"
                else -> if (arabic) "وسائط" else "Media"
            }
            listOfNotNull(
                item.title,
                type,
                item.metadataValue("media_status", "status")
            ).joinToString(" | ")
        }
        return listResponse(
            items = matching,
            headline = if (arabic) "${matching.size} عناصر للمشاهدة أو القراءة"
                else "${matching.size} items to watch or read",
            facts = facts,
            arabic = arabic,
            kind = RagEvidenceKind.MEDIA_BACKLOG
        )
    }

    private fun listResponse(
        items: List<VaultItem>,
        headline: String,
        facts: List<String>,
        arabic: Boolean,
        kind: RagEvidenceKind = RagEvidenceKind.FILTERED_ITEMS
    ) = RagResponse(
        answer = if (items.isEmpty()) {
            if (arabic) "لم أعثر على دليل مطابق في خزنتك." else "I found no matching evidence in your vault."
        } else {
            if (arabic) "النتيجة مبنية على العناصر المحفوظة المطابقة أدناه." else "This result is based on the matching saved items below."
        },
        sources = items.take(MAX_TOOL_SOURCES),
        confidence = 1f,
        evidence = RagEvidence(kind, headline, facts)
    )

    private fun noEvidence(kind: RagEvidenceKind, arabic: Boolean, en: String, ar: String) = RagResponse(
        answer = if (arabic) "لم أعثر على دليل مطابق في خزنتك." else "I found no matching evidence in your vault.",
        sources = emptyList(),
        confidence = 1f,
        evidence = RagEvidence(kind, if (arabic) ar else en)
    )

    private fun isMoneyDocument(item: VaultItem): Boolean =
        item.effectiveClassification in MONEY_CATEGORIES || amount(item) != null

    private fun matchesMerchant(item: VaultItem, merchant: String): Boolean {
        val needle = merchant.normalizedSearch()
        val candidates = listOfNotNull(
            item.parsedMetadata["merchant"],
            item.parsedMetadata["supplier"],
            item.parsedMetadata["store"],
            item.title
        )
        return candidates.any { needle in it.normalizedSearch() }
    }

    private fun amount(item: VaultItem): BigDecimal? =
        sequenceOf("total", "amount")
            .mapNotNull { item.parsedMetadata[it]?.trim()?.toBigDecimalOrNull() }
            .firstOrNull()

    private fun currency(item: VaultItem): String =
        item.parsedMetadata["currency"]?.trim()?.uppercase()?.takeIf(String::isNotBlank) ?: "—"

    private fun VaultItem.metadataValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        customFields[key]?.trim()?.takeIf(String::isNotBlank)
            ?: parsedMetadata[key]?.trim()?.takeIf(String::isNotBlank)
    }

    private fun VaultItem.isRecurringCharge(): Boolean {
        if (!recurringRule.isNullOrBlank()) return true
        return metadataValue("recurring", "subscription", "billing_period", "frequency")
            ?.normalizedSearch() in RECURRING_MARKERS
    }

    private fun VaultItem.actionDate(): Long? = secondaryAlertDate ?: expiryDate

    private fun String.normalizedSearch(): String = lowercase().replace(Regex("\\s+"), " ").trim()

    private fun BigDecimal.normalized(): String = stripTrailingZeros().toPlainString()

    private fun formatDate(timestamp: Long): String = DateTimeFormatter.ISO_LOCAL_DATE.format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    )

    private companion object {
        const val MAX_TOOL_SOURCES = 10
        const val MAX_SUPPORTING_FACTS = 5
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val TRIP_CLUSTER_MILLIS = 7L * MILLIS_PER_DAY
        val MONEY_CATEGORIES = setOf(Classification.RECEIPT, Classification.INVOICE)
        val TRAVEL_CATEGORIES = setOf(Classification.TICKET, Classification.HOTEL)
        val MEDIA_CATEGORIES = setOf(Classification.MOVIE, Classification.TV_SERIES, Classification.BOOK)
        val COMPLETED_MEDIA_STATUSES = setOf(
            "watched", "read", "finished", "completed", "done",
            "تمت مشاهدته", "تمت قراءته", "مكتمل"
        )
        val RECURRING_MARKERS = setOf(
            "true", "yes", "monthly", "weekly", "yearly", "annual", "subscription",
            "نعم", "شهري", "أسبوعي", "سنوي", "اشتراك"
        )
        val ARABIC = Regex("[\\u0600-\\u06FF]")
    }
}
