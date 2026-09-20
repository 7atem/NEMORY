package com.vaultbrain.shared.connectors

/**
 * Cross-platform abstraction for Home Screen Widgets / App Widgets.
 */
interface VaultWidgetManager {
    fun requestWidgetUpdate()
    fun clearLegacyState()
}

val LocalVaultWidgetManager = androidx.compose.runtime.staticCompositionLocalOf<VaultWidgetManager?> { null }
