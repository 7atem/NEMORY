package com.vaultbrain.core.ai.llm.gemma

/** Validates build-time model configuration before WorkManager accepts a download. */
internal object GemmaDownloadConfig {
    fun isValid(url: String, sha256: String, sizeBytes: Long): Boolean =
        url.startsWith("https://") &&
            !url.contains("example.com", ignoreCase = true) &&
            sha256.matches(Regex("[0-9a-fA-F]{64}")) &&
            sha256.any { it != '0' } &&
            sizeBytes > 0L
}
