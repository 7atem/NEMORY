package com.vaultbrain.shared.security

/**
 * Interface for platform-specific hardware key management.
 * On Android: Uses Android KeyStore with AES-256-GCM.
 * On iOS: Uses Apple Keychain Services (Security.framework).
 */
interface VaultKeyManager {
    /** Generates or retrieves the 256-bit database encryption key. */
    fun getOrCreatePassphrase(): ByteArray

    /** Deletes all persisted keys (used for decoy wipe or reset). */
    fun clearKeys()
}
