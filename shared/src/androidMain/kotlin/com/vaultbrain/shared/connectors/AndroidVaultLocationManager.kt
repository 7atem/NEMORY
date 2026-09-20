package com.vaultbrain.shared.connectors

class AndroidVaultLocationManager : VaultLocationManager {
    override suspend fun requestPermission(): Boolean = false
    override fun hasPermission(): Boolean = false
    override suspend fun getCurrentLocation(): VaultLocation? = null
}
