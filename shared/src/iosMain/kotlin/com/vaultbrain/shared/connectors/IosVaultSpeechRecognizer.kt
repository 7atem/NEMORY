package com.vaultbrain.shared.connectors

import platform.Speech.*
import platform.Foundation.NSURL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi

class IosVaultSpeechRecognizer : VaultSpeechRecognizer {
    
    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        SFSpeechRecognizer.requestAuthorization { status ->
            continuation.resume(status.value == 3L)
        }
    }

    override fun hasPermission(): Boolean {
        return SFSpeechRecognizer.authorizationStatus().value == 3L
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun transcribeAudioFile(filePath: String): String? = suspendCoroutine { continuation ->
        if (!hasPermission()) {
            continuation.resume(null)
            return@suspendCoroutine
        }
        
        val recognizer = SFSpeechRecognizer()
        if (recognizer == null || !recognizer.isAvailable()) {
            continuation.resume(null)
            return@suspendCoroutine
        }

        val url = NSURL.fileURLWithPath(filePath)
        val request = SFSpeechURLRecognitionRequest(url)
        
        recognizer.recognitionTaskWithRequest(request) { result, error ->
            if (error != null) {
                continuation.resume(null)
            } else if (result != null && result.isFinal()) {
                continuation.resume(result.bestTranscription.formattedString)
            }
        }
    }
}
