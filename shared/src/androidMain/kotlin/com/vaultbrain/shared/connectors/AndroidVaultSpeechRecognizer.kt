package com.vaultbrain.shared.connectors

class AndroidVaultSpeechRecognizer : VaultSpeechRecognizer {
    override suspend fun requestPermission(): Boolean = false
    override fun hasPermission(): Boolean = false
    override suspend fun transcribeAudioFile(filePath: String): String? = null
}
