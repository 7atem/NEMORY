package com.vaultbrain.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SQLCipher database passphrase.
 *
 * The passphrase is generated once, encrypted with AES-256 GCM via
 * [EncryptedFile] (backed by Android Keystore), and stored in app private storage.
 */
@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PASSPHRASE_FILE_NAME = "vaultbrain_passphrase.bin"
        private const val PASSPHRASE_LENGTH_BYTES = 32
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(false)
            .build()
    }

    /**
     * Returns the existing database passphrase or generates and persists a new one.
     */
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val file = File(context.filesDir, PASSPHRASE_FILE_NAME)
        return if (file.exists()) {
            try {
                readPassphrase(file)
            } catch (e: Exception) {
                // Keystore corrupted or key invalidated (e.g., after OS update/reinstall)
                file.delete()
                val passphrase = generatePassphrase()
                runCatching { writePassphrase(file, passphrase) }
                passphrase
            }
        } else {
            val passphrase = generatePassphrase()
            runCatching { writePassphrase(file, passphrase) }
            passphrase
        }
    }

    private fun generatePassphrase(): ByteArray {
        val random = SecureRandom()
        return ByteArray(PASSPHRASE_LENGTH_BYTES).apply {
            random.nextBytes(this)
        }
    }

    private fun readPassphrase(file: File): ByteArray {
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        return encryptedFile.openFileInput().use { it.readBytes() }
    }

    private fun writePassphrase(file: File, passphrase: ByteArray) {
        // Delete any stale file before writing.
        if (file.exists()) file.delete()
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        encryptedFile.openFileOutput().use { output ->
            output.write(passphrase)
        }
    }
}
