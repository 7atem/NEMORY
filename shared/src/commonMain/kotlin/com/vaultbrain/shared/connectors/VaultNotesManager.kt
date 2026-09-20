package com.vaultbrain.shared.connectors

/**
 * Apple Notes does not provide a public API for third-party developers on iOS.
 * Notes sync requires the user to manually share the note using the Nemory iOS Share Extension.
 */
interface VaultNotesManager {
    suspend fun requestPermission(): Boolean
    fun hasPermission(): Boolean
    suspend fun getNotes(): List<String>
}

val LocalVaultNotesManager = androidx.compose.runtime.staticCompositionLocalOf<VaultNotesManager?> { null }
