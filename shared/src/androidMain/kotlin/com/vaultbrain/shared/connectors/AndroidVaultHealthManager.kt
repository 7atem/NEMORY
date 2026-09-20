package com.vaultbrain.shared.connectors

class AndroidVaultHealthManager : VaultHealthManager {
    override suspend fun requestPermission(): Boolean {
        // Requires Android Health Connect implementation
        return false
    }

    override fun hasPermission(): Boolean {
        return false
    }

    override suspend fun getRecentWorkouts(): List<WorkoutData> {
        return emptyList()
    }
}
