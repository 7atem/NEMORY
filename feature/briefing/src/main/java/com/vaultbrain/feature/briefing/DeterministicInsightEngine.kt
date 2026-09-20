package com.vaultbrain.feature.briefing

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.VaultItem
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import com.vaultbrain.feature.briefing.R

/** Computes proactive insights exclusively from persisted metadata. */
@Singleton
class DeterministicInsightEngine @Inject constructor() {
    fun generate(items: List<VaultItem>, now: Long = System.currentTimeMillis()): List<TodayInsight> {
        if (items.isEmpty()) return emptyList()
        return buildList {
            addAll(expiryInsights(items, now))
            duplicateInsight(items)?.let(::add)
            spendingIncreaseInsight(items)?.let(::add)
            addAll(travelInsights(items, now))
            mediaBacklogInsight(items, now)?.let(::add)
        }.sortedWith(compareByDescending<TodayInsight> { it.priority }.thenBy(TodayInsight::id))
            .take(MAX_INSIGHTS)
    }

    fun generateWeekAhead(items: List<VaultItem>, now: Long = System.currentTimeMillis()): List<com.vaultbrain.feature.briefing.model.WeekAheadEvent> {
        val todayStart = startOfDay(now)
        val until = todayStart + 7 * DAY_MILLIS
        val events = mutableListOf<com.vaultbrain.feature.briefing.model.WeekAheadEvent>()

        items.forEach { item ->
            val expiry = item.expiryDate
            if (expiry != null && expiry in todayStart..until) {
                val type = if (item.effectiveClassification in TRAVEL_CATEGORIES) {
                    com.vaultbrain.feature.briefing.model.EventType.TRAVEL
                } else if (item.recordedAmount() != null) {
                    com.vaultbrain.feature.briefing.model.EventType.BILL
                } else {
                    com.vaultbrain.feature.briefing.model.EventType.EXPIRY
                }
                
                events.add(
                    com.vaultbrain.feature.briefing.model.WeekAheadEvent(
                        id = "event_${item.id}",
                        itemId = item.id,
                        title = item.title,
                        date = expiry,
                        type = type
                    )
                )
            }
        }
        return events.sortedBy { it.date }
    }

    private fun expiryInsights(items: List<VaultItem>, now: Long): List<TodayInsight.Expiring> {
        val todayStart = startOfDay(now)
        val until = now + EXPIRY_WINDOW_DAYS * DAY_MILLIS
        return items.asSequence()
            .filter { it.effectiveClassification !in TRAVEL_CATEGORIES }
            .mapNotNull { item ->
                item.expiryDate?.takeIf { it in todayStart..until }?.let { expiry ->
                    val days = daysUntil(now, expiry)
                    val actions = buildList {
                        add(InsightAction(R.string.today_action_renew, InsightActionType.VIEW_DETAILS, item.id))
                        add(InsightAction(R.string.today_action_snooze, InsightActionType.SNOOZE, item.id))
                    }
                    TodayInsight.Expiring(
                        primaryItemId = item.id,
                        itemTitle = item.title,
                        expiryAt = expiry,
                        daysRemaining = days,
                        priority = if (days <= 30) 100 else 70,
                        actions = actions
                    )
                }
            }
            .sortedBy(TodayInsight.Expiring::expiryAt)
            .take(MAX_EXPIRY_INSIGHTS)
            .toList()
    }

    private fun duplicateInsight(items: List<VaultItem>): TodayInsight.PossibleDuplicates? {
        val candidates = items.filter { it.possibleDuplicateOfItemId != null }
        if (candidates.isEmpty()) return null
        val primary = candidates.maxBy(VaultItem::createdAt)
        val related = candidates.flatMap { item ->
            listOfNotNull(item.id, item.possibleDuplicateOfItemId)
        }.distinct()
        val actions = listOf(InsightAction(R.string.today_action_view_details, InsightActionType.VIEW_DETAILS, primary.id))
        return TodayInsight.PossibleDuplicates(
            primaryItemId = primary.id,
            captureCount = related.size.coerceAtLeast(2),
            relatedItemIds = related,
            actions = actions
        )
    }

    private fun spendingIncreaseInsight(items: List<VaultItem>): TodayInsight.SpendingIncrease? {
        data class MoneyRow(
            val item: VaultItem,
            val provider: String,
            val currency: String,
            val amount: BigDecimal
        )

        val rows = items.mapNotNull { item ->
            if (item.effectiveClassification !in MONEY_CATEGORIES || !item.isRecurringCharge()) {
                return@mapNotNull null
            }
            val amount = item.recordedAmount() ?: return@mapNotNull null
            val currency = item.metadataValue("currency")?.uppercase()?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val provider = item.metadataValue("merchant", "supplier", "biller", "service_provider", "provider")
                ?: item.title.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            MoneyRow(item, provider, currency, amount)
        }

        return rows.groupBy { "${it.provider.normalized()}|${it.currency}" }
            .values
            .mapNotNull { group ->
                val ordered = group.sortedByDescending { it.item.createdAt }
                if (ordered.size < 2) return@mapNotNull null
                val current = ordered[0]
                val previous = ordered[1]
                if (current.amount <= previous.amount) return@mapNotNull null
                val actions = listOf(InsightAction(R.string.today_action_view_details, InsightActionType.VIEW_DETAILS, current.item.id))
                TodayInsight.SpendingIncrease(
                    primaryItemId = current.item.id,
                    provider = current.provider,
                    currency = current.currency,
                    previousAmount = previous.amount.normalized(),
                    currentAmount = current.amount.normalized(),
                    delta = current.amount.subtract(previous.amount).normalized(),
                    relatedItemIds = listOf(current.item.id, previous.item.id),
                    actions = actions
                )
            }
            .maxByOrNull { insight ->
                if (insight.previousAmount.signum() == 0) MAX_INCREASE_RATIO else {
                    insight.delta.divide(insight.previousAmount, 4, java.math.RoundingMode.HALF_UP)
                }
            }
    }

    private fun travelInsights(items: List<VaultItem>, now: Long): List<TodayInsight.UpcomingTravel> {
        val todayStart = startOfDay(now)
        val until = now + TRAVEL_WINDOW_DAYS * DAY_MILLIS
        return items.asSequence()
            .filter { it.effectiveClassification in TRAVEL_CATEGORIES }
            .mapNotNull { item ->
                val eventAt = (item.secondaryAlertDate ?: item.expiryDate)
                    ?.takeIf { it in todayStart..until }
                    ?: return@mapNotNull null
                val days = daysUntil(now, eventAt)
                val actions = listOf(InsightAction(R.string.today_action_view_details, InsightActionType.VIEW_DETAILS, item.id))
                TodayInsight.UpcomingTravel(
                    primaryItemId = item.id,
                    itemTitle = item.title,
                    eventAt = eventAt,
                    daysRemaining = days,
                    priority = if (days <= 14) 85 else 60,
                    actions = actions
                )
            }
            .sortedBy(TodayInsight.UpcomingTravel::eventAt)
            .take(MAX_TRAVEL_INSIGHTS)
            .toList()
    }

    private fun mediaBacklogInsight(items: List<VaultItem>, now: Long): TodayInsight.MediaBacklog? {
        val oldEnough = now - MEDIA_NUDGE_AFTER_DAYS * DAY_MILLIS
        val backlog = items.filter { item ->
            item.effectiveClassification in MEDIA_CATEGORIES &&
                !item.isMediaComplete() && item.createdAt <= oldEnough
        }.sortedBy(VaultItem::createdAt)
        val sample = backlog.firstOrNull() ?: return null
        val actions = listOf(
            InsightAction(R.string.today_action_done, InsightActionType.DONE, sample.id),
            InsightAction(R.string.today_action_snooze, InsightActionType.SNOOZE, sample.id)
        )
        return TodayInsight.MediaBacklog(
            primaryItemId = sample.id,
            sampleTitle = sample.title,
            itemCount = backlog.size,
            isReading = sample.effectiveClassification == Classification.BOOK,
            relatedItemIds = backlog.map(VaultItem::id).take(MAX_RELATED_IDS),
            actions = actions
        )
    }

    private fun VaultItem.isRecurringCharge(): Boolean {
        if (!recurringRule.isNullOrBlank()) return true
        val value = metadataValue("recurring", "subscription", "billing_period", "frequency")
            ?.normalized()
            ?: return false
        return value !in setOf("false", "no", "none", "one time", "one_time")
    }

    private fun VaultItem.recordedAmount(): BigDecimal? =
        metadataValue("total", "amount")
            ?.replace(",", "")
            ?.trim()
            ?.toBigDecimalOrNull()
            ?.takeIf { it >= BigDecimal.ZERO }

    private fun VaultItem.metadataValue(vararg keys: String): String? {
        val metadata = parsedMetadata + customFields
        return keys.firstNotNullOfOrNull { key -> metadata[key]?.trim()?.takeIf(String::isNotBlank) }
    }

    private fun VaultItem.isMediaComplete(): Boolean =
        metadataValue("media_status", "status")?.normalized() in COMPLETED_MEDIA_STATES

    private fun String.normalized(): String = lowercase().replace('-', ' ').replace('_', ' ').trim()

    private fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros()

    private fun daysUntil(now: Long, target: Long): Long =
        ((target - now).coerceAtLeast(0L) + DAY_MILLIS - 1) / DAY_MILLIS

    private fun startOfDay(timestamp: Long): Long = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private companion object {
        const val MAX_INSIGHTS = 5
        const val MAX_EXPIRY_INSIGHTS = 2
        const val MAX_TRAVEL_INSIGHTS = 1
        const val MAX_RELATED_IDS = 10
        const val EXPIRY_WINDOW_DAYS = 90L
        const val TRAVEL_WINDOW_DAYS = 90L
        const val MEDIA_NUDGE_AFTER_DAYS = 7L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        val MAX_INCREASE_RATIO = BigDecimal("999999")
        val MONEY_CATEGORIES = setOf(Classification.RECEIPT, Classification.INVOICE)
        val TRAVEL_CATEGORIES = setOf(Classification.TICKET, Classification.HOTEL)
        val MEDIA_CATEGORIES = setOf(Classification.MOVIE, Classification.TV_SERIES, Classification.BOOK)
        val COMPLETED_MEDIA_STATES = setOf("watched", "finished", "completed")
    }
}
