package com.vaultbrain.core.common.model

import java.util.UUID

/** A local, user-owned reminder optionally linked back to vault or external context. */
data class VaultReminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val dueAt: Long,
    val status: VaultReminderStatus = VaultReminderStatus.SCHEDULED,
    val vaultItemId: String? = null,
    val externalConnectorId: String? = null,
    val externalAccountId: String? = null,
    val externalRecordId: String? = null,
    val personalCollectionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class VaultReminderStatus {
    SCHEDULED,
    SNOOZED,
    COMPLETED,
    DISMISSED
}
