package com.vaultbrain.shared.storage

/**
 * Cross-platform abstraction for storing files (images, PDFs) encrypted at-rest using AES-256-GCM.
 * This replaces the Android-only EncryptedFile wrapper.
 */
interface MediaVaultStorage {
    
    /**
     * Stores a file payload into the encrypted vault storage.
     * @param fileName The unique filename/ID for the stored media.
     * @param payload The raw byte array to encrypt and store.
     * @return The absolute path or URI of the securely stored file.
     */
    suspend fun saveMedia(fileName: String, payload: ByteArray): String
    
    /**
     * Retrieves and decrypts a file from the vault storage.
     * @param fileName The unique filename/ID of the media.
     * @return The decrypted raw byte array, or null if it doesn't exist or failed to decrypt.
     */
    suspend fun loadMedia(fileName: String): ByteArray?
    
    /**
     * Permanently deletes the encrypted file from storage.
     */
    suspend fun deleteMedia(fileName: String): Boolean
}

/** CompositionLocal for providing the platform-specific MediaVaultStorage. */
val LocalMediaVaultStorage = androidx.compose.runtime.staticCompositionLocalOf<MediaVaultStorage?> { null }
