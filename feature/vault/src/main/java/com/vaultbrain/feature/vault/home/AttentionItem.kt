package com.vaultbrain.feature.vault.home

import com.vaultbrain.core.common.model.VaultReminder
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.common.model.ProactiveAction
import com.vaultbrain.core.common.model.ProactiveActionResolver
import com.vaultbrain.core.common.model.VaultItem

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
    val subtitle: String? = null,
    val dueAt: Long?,
    val targetId: String?,
    val proactiveAction: ProactiveAction? = null
) {
    enum class Kind { REMINDER, EVENT, EXPIRING, REVIEW, GMAIL, PROACTIVE_ACTION }
}

/**
 * Deterministically merges today's reminders/events, soon-expiring documents and the
 * review backlog into one capped, ordered list. Dated entries come first ordered by
 * due time; the review backlog row comes last. Pure: all inputs are pre-filtered.
 */
fun buildAttentionItems(
    todayReminders: List<VaultReminder>,
    todayEvents: List<ExternalRecord>,
    todayGmail: List<ExternalRecord> = emptyList(),
    expiringDocuments: List<ExpiringDocument>,
    recentDocuments: List<VaultItem> = emptyList(),
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
                    dueAt = record.startAt ?: record.createdAt, // fallback to createdAt if startAt is null
                    targetId = record.externalId
                )
            )
        }
        todayGmail.forEach { record ->
            val merchant = record.payload["merchant"] ?: record.title?.substringBefore(" ") ?: "Email"
            val total = record.payload["total"]
            val currency = record.payload["currency"] ?: ""
            val track = record.payload["tracking_number"]
            val subtitle = when {
                track != null -> "Arriving soon"
                total != null -> "$currency $total due".trim()
                else -> "Needs your attention"
            }
            add(
                AttentionItem(
                    id = "gmail:${record.externalId}",
                    kind = AttentionItem.Kind.GMAIL,
                    title = merchant.take(25),
                    subtitle = subtitle,
                    dueAt = record.createdAt,
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
        recentDocuments.forEach { doc ->
            val actions = ProactiveActionResolver.resolve(doc)
            actions.forEach { action ->
                add(
                    AttentionItem(
                        id = "action:${doc.id}:${action.code}",
                        kind = AttentionItem.Kind.PROACTIVE_ACTION,
                        title = doc.title,
                        subtitle = "AI Suggestion",
                        dueAt = doc.createdAt,
                        targetId = doc.id,
                        proactiveAction = action
                    )
                )
            }
        }
    }.sortedWith(
        compareByDescending<AttentionItem> { it.kind == AttentionItem.Kind.PROACTIVE_ACTION }
            .thenBy { it.dueAt ?: 0L }
    )
    val review = if (needsReviewCount > 0) {
        listOf(AttentionItem(id = "review", kind = AttentionItem.Kind.REVIEW, title = "", dueAt = null, targetId = null))
    } else {
        emptyList()
    }

    return (dated + review).take(maxItems)
}
