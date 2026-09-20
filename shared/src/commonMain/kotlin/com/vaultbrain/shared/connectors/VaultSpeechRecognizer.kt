package com.vaultbrain.shared.connectors

/**
 * Cross-platform abstraction for on-device live audio transcription.
 */
interface VaultSpeechRecognizer {
    suspend fun requestPermission(): Boolean
    fun hasPermission(): Boolean
    suspend fun transcribeAudioFile(filePath: String): String?
}

val LocalVaultSpeechRecognizer = androidx.compose.runtime.staticCompositionLocalOf<VaultSpeechRecognizer?> { null }
