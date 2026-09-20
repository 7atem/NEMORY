package com.vaultbrain.feature.brain

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.VaultItem
import javax.inject.Inject

enum class BrainSuggestion {
    RECENT, EXPIRING, TRAVEL, WARRANTIES, RECURRING_SPEND, RECEIPTS, MEDIA, HEALTH
}

/** Offers only questions supported by categories and metadata that are actually present. */
class BrainSuggestionProvider @Inject constructor() {
    fun forItems(items: List<VaultItem>, now: Long = System.currentTimeMillis()): List<BrainSuggestion> {
        if (items.isEmpty()) return emptyList()
        return buildList {
            add(BrainSuggestion.RECENT)
            if (items.any { it.expiryDate?.let { date -> date >= now } == true }) add(BrainSuggestion.EXPIRING)
            if (items.any { LensId.TRAVEL in it.lensTags || it.effectiveClassification in TRAVEL }) add(BrainSuggestion.TRAVEL)
            if (items.any { it.effectiveClassification == Classification.WARRANTY_CARD }) add(BrainSuggestion.WARRANTIES)
            if (hasRecurringPair(items)) add(BrainSuggestion.RECURRING_SPEND)
            else if (items.any { it.effectiveClassification in MONEY }) add(BrainSuggestion.RECEIPTS)
            if (items.any { it.effectiveClassification in MEDIA }) add(BrainSuggestion.MEDIA)
            if (items.any { LensId.HEALTH in it.lensTags || it.effectiveClassification in HEALTH }) add(BrainSuggestion.HEALTH)
        }.distinct().take(MAX_SUGGESTIONS)
    }

    private fun hasRecurringPair(items: List<VaultItem>): Boolean = items.asSequence()
        .filter { item ->
            !item.recurringRule.isNullOrBlank() ||
                item.parsedMetadata.keys.any { it in RECURRING_KEYS }
        }
        .mapNotNull { item ->
            val provider = PROVIDER_KEYS.firstNotNullOfOrNull { item.parsedMetadata[it] }
                ?.lowercase()?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val currency = item.parsedMetadata["currency"]?.uppercase()?.trim().orEmpty()
            "$provider|$currency"
        }
        .groupingBy { it }
        .eachCount()
        .values
        .any { it >= 2 }

    private companion object {
        const val MAX_SUGGESTIONS = 4
        val TRAVEL = setOf(Classification.TICKET, Classification.HOTEL)
        val MONEY = setOf(Classification.RECEIPT, Classification.INVOICE)
        val MEDIA = setOf(Classification.MOVIE, Classification.TV_SERIES, Classification.BOOK)
        val HEALTH = setOf(Classification.PRESCRIPTION, Classification.LAB_RESULT)
        val RECURRING_KEYS = setOf("recurring", "subscription", "billing_period", "frequency")
        val PROVIDER_KEYS = listOf("merchant", "supplier", "biller", "service_provider", "provider")
    }
}
