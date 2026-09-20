package com.vaultbrain.shared.connectors

class AndroidVaultRemindersManager : VaultRemindersManager {
    override suspend fun requestPermission(): Boolean {
        // Android does not have a unified local reminders API. 
        // Tasks are usually synced via Google Tasks API (OAuth).
        return false
    }

    override fun hasPermission(): Boolean {
        return false
    }

    override suspend fun getReminders(): List<ReminderItem> {
        return emptyList()
    }
}
