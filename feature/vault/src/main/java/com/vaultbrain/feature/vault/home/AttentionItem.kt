package com.vaultbrain.feature.vault.home

import com.vaultbrain.core.common.model.VaultReminder
import com.vaultbrain.core.integrations.model.ExternalRecord

/** A document with an approaching expiry/alert date, surfaced in "Needs attention". */
data class ExpiringDocument(
    val id: String,
    val title: String,
    val dueAt: Long
)

/** A single prioritized row in the Today "Needs attention" section. */
data class AttentionItem(
    val id: String,
    val kind: Kind,
    val title: String,
    val dueAt: Long?,
    val targetId: String?
) {
    enum class Kind { REMINDER, EVENT, EXPIRING, REVIEW }
}

/**
 * Deterministically merges today's reminders/events, soon-expiring documents and the
 * review backlog into one capped, ordered list. Dated entries come first ordered by
 * due time; the review backlog row comes last. Pure: all inputs are pre-filtered.
 */
fun buildAttentionItems(
    todayReminders: List<VaultReminder>,
    todayEvents: List<ExternalRecord>,
    expiringDocuments: List<ExpiringDocument>,
    needsReviewCount: Int,
    maxItems: Int = 5
): List<AttentionItem> {
    val dated = buildList {
        todayReminders.forEach { reminder ->
            add(
                AttentionItem(
                    id = "reminder:${reminder.id}",
                    kind = AttentionItem.Kind.REMINDER,
                    title = reminder.title,
                    dueAt = reminder.dueAt,
                    targetId = reminder.id
                )
            )
        }
        todayEvents.forEach { record ->
            add(
                AttentionItem(
                    id = "event:${record.externalId}",
                    kind = AttentionItem.Kind.EVENT,
                    title = record.title.orEmpty(),
                    dueAt = record.startAt,
                    targetId = record.externalId
                )
            )
        }
        expiringDocuments.forEach { document ->
            add(
                AttentionItem(
                    id = "expiring:${document.id}",
                    kind = AttentionItem.Kind.EXPIRING,
                    title = document.title,
                    dueAt = document.dueAt,
                    targetId = document.id
                )
            )
        }
    }.sortedBy { it.dueAt ?: Long.MAX_VALUE }

    val review = if (needsReviewCount > 0) {
        listOf(AttentionItem(id = "review", kind = AttentionItem.Kind.REVIEW, title = "", dueAt = null, targetId = null))
    } else {
        emptyList()
    }

    return (dated + review).take(maxItems)
}
