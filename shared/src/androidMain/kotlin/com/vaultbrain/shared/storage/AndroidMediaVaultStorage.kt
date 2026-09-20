package com.vaultbrain.shared.storage

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Implementation of MediaVaultStorage.
 * Uses AndroidX Security Crypto's EncryptedFile for AES-256-GCM encryption at-rest.
 */
class AndroidMediaVaultStorage(
    private val context: Context
) : MediaVaultStorage {

    private val vaultDirectory: File by lazy {
        val dir = File(context.filesDir, "nemory_vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    override suspend fun saveMedia(fileName: String, payload: ByteArray): String = withContext(Dispatchers.IO) {
        val file = File(vaultDirectory, fileName)
        
        // If file already exists, we must delete it before re-encrypting with EncryptedFile
        if (file.exists()) {
            file.delete()
        }

        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { outputStream ->
            outputStream.write(payload)
            outputStream.flush()
        }
        
        file.absolutePath
    }

    override suspend fun loadMedia(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(vaultDirectory, fileName)
        if (!file.exists()) {
            return@withContext null
        }

        try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileInput().use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun deleteMedia(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(vaultDirectory, fileName)
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
