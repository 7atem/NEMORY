package com.vaultbrain.shared.connectors

data class VaultLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double
)

/**
 * Cross-platform abstraction for accessing device location to spatially tag items.
 */
interface VaultLocationManager {
    suspend fun requestPermission(): Boolean
    fun hasPermission(): Boolean
    suspend fun getCurrentLocation(): VaultLocation?
}

val LocalVaultLocationManager = androidx.compose.runtime.staticCompositionLocalOf<VaultLocationManager?> { null }
