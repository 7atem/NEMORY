package com.vaultbrain.core.ai.llm.gemma

import java.io.File
import java.security.MessageDigest

/**
 * Pure file/hash logic for the on-device model files.
 */
internal object GemmaModelFileVerifier {

    private data class CacheKey(
        val path1: String, val mod1: Long, val len1: Long,
        val path2: String, val mod2: Long, val len2: Long
    )

    @Volatile
    private var cache: Pair<CacheKey, OnDeviceModelStatus>? = null

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
     * Resolves on-disk model package status. READY requires both files to exist and match expected SHA-256 hashes.
     */
    @Synchronized
    fun resolveStatus(
        supported: Boolean,
        modelFile: File,
        expectedModelSha256: String,
        mmprojFile: File? = null,
        expectedMmprojSha256: String? = null
    ): OnDeviceModelStatus {
        if (!supported) return OnDeviceModelStatus.NOT_SUPPORTED
        if (!modelFile.exists() || modelFile.length() == 0L) return OnDeviceModelStatus.NOT_DOWNLOADED
        if (mmprojFile != null && (!mmprojFile.exists() || mmprojFile.length() == 0L)) return OnDeviceModelStatus.NOT_DOWNLOADED

        val key = CacheKey(
            modelFile.absolutePath, modelFile.lastModified(), modelFile.length(),
            mmprojFile?.absolutePath ?: "", mmprojFile?.lastModified() ?: 0L, mmprojFile?.length() ?: 0L
        )
        cache?.let { (cachedKey, cachedStatus) ->
            if (cachedKey == key) return cachedStatus
        }

        // SHA-256 verification is mandatory. The CacheKey (path + mtime + length) ensures
        // that this cost is paid only once per file change; subsequent calls hit the cache above.
        val modelValid = sha256(modelFile).equals(expectedModelSha256, ignoreCase = true)
        val mmprojValid = mmprojFile == null || expectedMmprojSha256 == null ||
                sha256(mmprojFile).equals(expectedMmprojSha256, ignoreCase = true)

        val status = if (modelValid && mmprojValid) {
            OnDeviceModelStatus.READY
        } else {
            OnDeviceModelStatus.ERROR
        }
        cache = key to status
        return status
    }

    private const val BUFFER_BYTES = 64 * 1024
}
