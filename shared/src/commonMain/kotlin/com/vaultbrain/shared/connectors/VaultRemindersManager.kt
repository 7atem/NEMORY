package com.vaultbrain.shared.connectors

data class ReminderItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val dueDateMs: Long?
)

/**
 * Cross-platform abstraction for accessing device reminders/tasks.
 */
interface VaultRemindersManager {
    suspend fun requestPermission(): Boolean
    fun hasPermission(): Boolean
    suspend fun getReminders(): List<ReminderItem>
}

val LocalVaultRemindersManager = androidx.compose.runtime.staticCompositionLocalOf<VaultRemindersManager?> { null }
