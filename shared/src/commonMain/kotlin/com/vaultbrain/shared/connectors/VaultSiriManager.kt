package com.vaultbrain.shared.connectors

/**
 * Cross-platform abstraction for System Voice Assistants (Siri / Google Assistant).
 */
interface VaultSiriManager {
    fun donateSearchIntent(query: String)
    fun donateCaptureIntent(type: String)
}

val LocalVaultSiriManager = androidx.compose.runtime.staticCompositionLocalOf<VaultSiriManager?> { null }
