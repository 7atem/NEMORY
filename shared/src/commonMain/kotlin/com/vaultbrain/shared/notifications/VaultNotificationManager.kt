package com.vaultbrain.shared.notifications

/**
 * Cross-platform abstraction for scheduling and displaying local notifications.
 */
interface VaultNotificationManager {
    /**
     * Requests permission from the user to show notifications.
     * @return true if granted, false otherwise.
     */
    suspend fun requestPermission(): Boolean
    
    /**
     * Shows an immediate local notification.
     */
    fun showNotification(id: Int, title: String, body: String)
    
    /**
     * Cancels a previously shown notification.
     */
    fun cancelNotification(id: Int)
}

/** CompositionLocal for providing the platform-specific VaultNotificationManager. */
val LocalVaultNotificationManager = androidx.compose.runtime.staticCompositionLocalOf<VaultNotificationManager?> { null }
