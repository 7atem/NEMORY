package com.vaultbrain.shared.model


/** A local, user-owned reminder optionally linked back to vault or external context. */
data class VaultReminder(
    val id: String = com.vaultbrain.shared.util.randomUUIDString(),
    val title: String,
    val dueAt: Long,
    val status: VaultReminderStatus = VaultReminderStatus.SCHEDULED,
    val vaultItemId: String? = null,
    val externalConnectorId: String? = null,
    val externalAccountId: String? = null,
    val externalRecordId: String? = null,
    val personalCollectionId: String? = null,
    val createdAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),
    val updatedAt: Long = com.vaultbrain.shared.util.currentTimeMillis()
)

enum class VaultReminderStatus {
    SCHEDULED,
    SNOOZED,
    COMPLETED,
    DISMISSED
}
