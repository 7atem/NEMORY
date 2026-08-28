package com.vaultbrain.core.ai.llm.gemma

import java.io.File
import java.security.MessageDigest

/**
 * Pure file/hash logic for the Gemma Tier 2 model. Kept Android-free so it is unit-testable;
 * [GemmaModelManager] owns device support and WorkManager state.
 */
internal object GemmaModelFileVerifier {

    private data class CacheKey(val path: String, val lastModified: Long, val length: Long)

    @Volatile
    private var cache: Pair<CacheKey, GemmaModelStatus>? = null

    /** Computes the lowercase hex SHA-256 of [file]. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Resolves the on-disk model state. READY requires the file to exist and match
     * [expectedSha256]; the hash is computed once per file version and cached.
     */
    @Synchronized
    fun resolveStatus(
        supported: Boolean,
        modelFile: File,
        expectedSha256: String
    ): GemmaModelStatus {
        if (!supported) return GemmaModelStatus.NOT_SUPPORTED
        if (!modelFile.exists() || modelFile.length() == 0L) return GemmaModelStatus.NOT_DOWNLOADED
        val key = CacheKey(modelFile.absolutePath, modelFile.lastModified(), modelFile.length())
        cache?.let { (cachedKey, cachedStatus) ->
            if (cachedKey == key) return cachedStatus
        }
        val status = if (sha256(modelFile).equals(expectedSha256, ignoreCase = true)) {
            GemmaModelStatus.READY
        } else {
            GemmaModelStatus.ERROR
        }
        cache = key to status
        return status
    }

    private const val BUFFER_BYTES = 64 * 1024
}
