package com.vaultbrain.feature.capture

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.security.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes ownership of incoming media before temporary URI grants expire.
 * Files live in private, no-backup app storage, encrypted at rest with AES-256-GCM.
 * All media queries and streams are isolated and fail closed during decoy mode sessions.
 */
@Singleton
class MediaVaultStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager
) {
    val mediaDirectory: File
        get() = if (DecoySessionState.isDecoy.value) {
            File(context.noBackupFilesDir, "vault_media_decoy").apply { mkdirs() }
        } else {
            File(context.noBackupFilesDir, "vault_media").apply { mkdirs() }
        }

    private val realMediaDirectory: File
        get() = File(context.noBackupFilesDir, "vault_media")

    /**
     * Imports external media into encrypted vault storage.
     * Fails closed if invoked while in a decoy session.
     */
    suspend fun import(uri: Uri): Uri = withContext(Dispatchers.IO) {
        if (DecoySessionState.isDecoy.value) {
            error("Cannot import media during a decoy session")
        }

        val sourceFile = uri.takeIf { it.scheme == "file" }?.path?.let(::File)
        if (sourceFile != null && sourceFile.parentFile?.canonicalFile == mediaDirectory.canonicalFile) {
            return@withContext uri
        }

        val displayName = queryDisplayName(uri)
        val extension = displayName.substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: extensionFor(context.contentResolver.getType(uri))
        val destination = File(
            mediaDirectory,
            buildString {
                append(UUID.randomUUID())
                if (extension.isNotBlank()) append('.').append(extension.lowercase())
            }
        )

        val rawBytes = if (sourceFile != null) {
            sourceFile.readBytes()
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected media")
        }

        val encryptedBytes = encryptBytes(rawBytes)
        destination.writeBytes(encryptedBytes)

        val sourceInCache = sourceFile != null &&
            sourceFile.parentFile?.canonicalFile == context.cacheDir.canonicalFile
        if (sourceInCache) sourceFile!!.delete()

        Uri.fromFile(destination)
    }

    /**
     * Checks whether a URI belongs to the vault media store.
     */
    fun isVaultMedia(uri: Uri): Boolean {
        val file = uri.takeIf { it.scheme == "file" }?.path?.let(::File) ?: return false
        val realCanonical = runCatching { realMediaDirectory.canonicalFile }.getOrNull() ?: return false
        val fileCanonical = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return fileCanonical.startsWith(realCanonical)
    }

    /**
     * Safely opens an input stream for media. Decrypts AES-256-GCM data at rest.
     * Automatically fails closed if decoy mode is active and the media belongs to the vault.
     */
    fun openInputStream(uri: Uri): InputStream? {
        if (isVaultMedia(uri) && DecoySessionState.isDecoy.value) {
            return null
        }

        val file = uri.takeIf { it.scheme == "file" }?.path?.let(::File)
        if (file != null && file.exists()) {
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            if (isEncrypted(bytes)) {
                return runCatching {
                    val decrypted = decryptBytes(bytes)
                    ByteArrayInputStream(decrypted)
                }.getOrNull()
            }
            // Legacy unencrypted file
            return ByteArrayInputStream(bytes)
        }

        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    fun mimeType(uri: Uri): String? {
        if (isVaultMedia(uri) && DecoySessionState.isDecoy.value) return null

        context.contentResolver.getType(uri)?.let { return it }
        return when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heif"
            "pdf" -> "application/pdf"
            else -> null
        }
    }

    private fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_SIZE) return false
        return bytes[0] == MAGIC[0] && bytes[1] == MAGIC[1] && bytes[2] == MAGIC[2] && bytes[3] == MAGIC[3]
    }

    private fun encryptBytes(plaintext: ByteArray): ByteArray {
        val key = keystoreManager.getOrCreateDatabasePassphrase()
        val iv = ByteArray(IV_BYTES).apply { secureRandom.nextBytes(this) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val output = ByteArray(HEADER_SIZE + ciphertext.size)
        System.arraycopy(MAGIC, 0, output, 0, MAGIC.size)
        output[4] = FORMAT_VERSION
        System.arraycopy(iv, 0, output, 5, IV_BYTES)
        System.arraycopy(ciphertext, 0, output, HEADER_SIZE, ciphertext.size)
        return output
    }

    private fun decryptBytes(encrypted: ByteArray): ByteArray {
        require(isEncrypted(encrypted)) { "Payload does not contain valid encrypted media header" }
        val key = keystoreManager.getOrCreateDatabasePassphrase()
        val iv = Arrays.copyOfRange(encrypted, 5, 5 + IV_BYTES)
        val ciphertext = Arrays.copyOfRange(encrypted, HEADER_SIZE, encrypted.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun queryDisplayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment.orEmpty()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull().orEmpty()
    }

    private fun extensionFor(mimeType: String?): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heif", "image/heic" -> "heic"
        "application/pdf" -> "pdf"
        else -> "bin"
    }

    companion object {
        private val MAGIC = byteArrayOf(0x4E, 0x4D, 0x45, 0x44) // NMED
        private const val FORMAT_VERSION: Byte = 1
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val HEADER_SIZE = 5 + IV_BYTES // 4 magic + 1 version + 12 iv = 17 bytes
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private val secureRandom = SecureRandom()
    }
}
