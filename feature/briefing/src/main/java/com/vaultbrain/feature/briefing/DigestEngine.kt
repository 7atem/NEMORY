package com.vaultbrain.feature.briefing

import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.feature.brain.worker.RadarEngine
import javax.inject.Inject
import javax.inject.Singleton

data class DailyDigest(
    val date: Long,
    val headline: String,
    val items: List<TodayInsight>
)

/**
 * Merges TodayInsight list from internal heuristics + RadarEngine.ActionableInsight list from external records.
 * Ranks by urgency and produces a DailyDigest.
 */
@Singleton
class DigestEngine @Inject constructor(
    private val deterministicInsightEngine: DeterministicInsightEngine,
    private val radarEngine: RadarEngine
) {

    fun generateDigest(
        vaultItems: List<VaultItem>,
        externalRecords: List<ExternalRecord>,
        now: Long = System.currentTimeMillis()
    ): DailyDigest {
        val vaultInsights = deterministicInsightEngine.generate(vaultItems, now)
        
        val radarInsights = externalRecords.mapNotNull { record ->
            val radar = radarEngine.evaluate(record) ?: return@mapNotNull null
            val priority = when (radar.type) {
                "BILL" -> 95
                "FLIGHT", "PACKAGE" -> 85
                "TICKET", "APPOINTMENT" -> 80
                else -> 70
            }
            TodayInsight.ActionableExternalInsight(
                primaryItemId = record.externalId,
                insightTitle = radar.title,
                insightBody = radar.body,
                insightType = radar.type,
                priority = priority,
                relatedItemIds = listOf(record.externalId),
                actions = listOf(
                    InsightAction(R.string.today_action_view_details, InsightActionType.VIEW_DETAILS, record.externalId)
                )
            )
        }

        // Merge and rank by urgency (priority descending). Identical radar insights
        // (same type + title from near-duplicate records) collapse into one card.
        val combinedItems = (vaultInsights + radarInsights.distinctBy { it.insightType + '|' + it.insightTitle })
            .sortedByDescending { it.priority }
            .take(MAX_DIGEST_ITEMS)

        val headline = if (combinedItems.isNotEmpty()) {
            "You have ${combinedItems.size} proactive insight${if (combinedItems.size > 1) "s" else ""} today."
        } else {
            "You're all caught up today."
        }

        return DailyDigest(
            date = now,
            headline = headline,
            items = combinedItems
        )
    }

    companion object {
        const val MAX_DIGEST_ITEMS = 7
    }
}
