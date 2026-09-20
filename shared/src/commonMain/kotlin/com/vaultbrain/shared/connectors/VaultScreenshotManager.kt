package com.vaultbrain.shared.connectors

data class ScreenshotItem(
    val id: String,
    val creationDateMs: Long,
    val filePath: String?
)

/**
 * Cross-platform abstraction for accessing device screenshots.
 */
interface VaultScreenshotManager {
    suspend fun requestPermission(): Boolean
    fun hasPermission(): Boolean
    suspend fun getRecentScreenshots(limit: Int): List<ScreenshotItem>
}

val LocalVaultScreenshotManager = androidx.compose.runtime.staticCompositionLocalOf<VaultScreenshotManager?> { null }
