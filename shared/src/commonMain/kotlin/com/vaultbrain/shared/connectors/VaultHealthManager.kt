package com.vaultbrain.shared.connectors

data class WorkoutData(
    val id: String,
    val type: String,
    val durationMinutes: Int,
    val dateMs: Long
)

/**
 * Cross-platform abstraction for accessing device health/workout data.
 */
interface VaultHealthManager {
    suspend fun requestPermission(): Boolean
    fun hasPermission(): Boolean
    suspend fun getRecentWorkouts(): List<WorkoutData>
}

val LocalVaultHealthManager = androidx.compose.runtime.staticCompositionLocalOf<VaultHealthManager?> { null }
