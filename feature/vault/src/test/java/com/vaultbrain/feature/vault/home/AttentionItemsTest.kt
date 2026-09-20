package com.vaultbrain.feature.vault.home

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.VaultReminder
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import org.junit.Test

class AttentionItemsTest {

    private fun reminder(id: String, dueAt: Long) = VaultReminder(id = id, title = "R$id", dueAt = dueAt)

    private fun event(id: String, startAt: Long) = ExternalRecord(
        connectorId = "calendar",
        accountId = "device",
        externalId = id,
        source = ExternalSource.CALENDAR,
        recordType = ExternalRecordType.EVENT,
        title = "E$id",
        startAt = startAt,
        createdAt = 0L
    )

    private fun expiring(id: String, dueAt: Long) = ExpiringDocument(id = id, title = "X$id", dueAt = dueAt)

    @Test
    fun `dated entries are ordered by due time across kinds`() {
        val items = buildAttentionItems(
            todayReminders = listOf(reminder("r2", 300)),
            todayEvents = listOf(event("e1", 100)),
            expiringDocuments = listOf(expiring("x3", 200)),
            needsReviewCount = 0
        )
        assertThat(items.map { it.id }).containsExactly("event:e1", "expiring:x3", "reminder:r2").inOrder()
    }

    @Test
    fun `review backlog row comes last and reflects the count`() {
        val items = buildAttentionItems(
            todayReminders = listOf(reminder("r1", 100)),
            todayEvents = emptyList(),
            expiringDocuments = emptyList(),
            needsReviewCount = 7
        )
        assertThat(items.last().kind).isEqualTo(AttentionItem.Kind.REVIEW)
        assertThat(items).hasSize(2)
    }

    @Test
    fun `no review row when backlog is empty`() {
        val items = buildAttentionItems(
            todayReminders = emptyList(),
            todayEvents = emptyList(),
            expiringDocuments = emptyList(),
            needsReviewCount = 0
        )
        assertThat(items).isEmpty()
    }

    @Test
    fun `output is capped`() {
        val items = buildAttentionItems(
            todayReminders = (1..4).map { reminder("r$it", it.toLong()) },
            todayEvents = (1..4).map { event("e$it", it.toLong() + 10) },
            expiringDocuments = emptyList(),
            needsReviewCount = 3,
            maxItems = 5
        )
        assertThat(items).hasSize(5)
    }
}
