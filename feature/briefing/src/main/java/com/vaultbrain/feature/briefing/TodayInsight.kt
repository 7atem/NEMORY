package com.vaultbrain.feature.briefing

import java.math.BigDecimal

enum class InsightActionType {
    SNOOZE, DONE, VIEW_DETAILS, OPEN_URL, CALL
}

data class InsightAction(val labelRes: Int, val type: InsightActionType, val data: String? = null)

/** A Kotlin-authoritative proactive fact. Opening an insight is read-only. */
sealed interface TodayInsight {
    val id: String
    val priority: Int
    val primaryItemId: String
    val relatedItemIds: List<String>
    val actions: List<InsightAction>

    data class Expiring(
        override val primaryItemId: String,
        val itemTitle: String,
        val expiryAt: Long,
        val daysRemaining: Long,
        override val priority: Int,
        override val actions: List<InsightAction> = emptyList()
    ) : TodayInsight {
        override val id: String = "expiry:$primaryItemId"
        override val relatedItemIds: List<String> = listOf(primaryItemId)
    }

    data class PossibleDuplicates(
        override val primaryItemId: String,
        val captureCount: Int,
        override val relatedItemIds: List<String>,
        override val priority: Int = 90,
        override val actions: List<InsightAction> = emptyList()
    ) : TodayInsight {
        override val id: String = "duplicates:$primaryItemId"
    }

    data class SpendingIncrease(
        override val primaryItemId: String,
        val provider: String,
        val currency: String,
        val previousAmount: BigDecimal,
        val currentAmount: BigDecimal,
        val delta: BigDecimal,
        override val relatedItemIds: List<String>,
        override val priority: Int = 80,
        override val actions: List<InsightAction> = emptyList()
    ) : TodayInsight {
        override val id: String = "spending:$primaryItemId"
    }

    data class UpcomingTravel(
        override val primaryItemId: String,
        val itemTitle: String,
        val eventAt: Long,
        val daysRemaining: Long,
        override val priority: Int,
        override val actions: List<InsightAction> = emptyList()
    ) : TodayInsight {
        override val id: String = "travel:$primaryItemId"
        override val relatedItemIds: List<String> = listOf(primaryItemId)
    }

    data class MediaBacklog(
        override val primaryItemId: String,
        val sampleTitle: String,
        val itemCount: Int,
        val isReading: Boolean,
        override val relatedItemIds: List<String>,
        override val priority: Int = 40,
        override val actions: List<InsightAction> = emptyList()
    ) : TodayInsight {
        override val id: String = "media:$primaryItemId"
    }

    data class ActionableExternalInsight(
        override val primaryItemId: String,
        val insightTitle: String,
        val insightBody: String,
        val insightType: String,
        override val relatedItemIds: List<String>,
        override val priority: Int = 85,
        override val actions: List<InsightAction> = emptyList()
    ) : TodayInsight {
        override val id: String = "radar:$primaryItemId"
    }
}
