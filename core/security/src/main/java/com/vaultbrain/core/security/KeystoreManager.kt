package com.vaultbrain.core.security

import android.content.Context
import android.util.AtomicFile
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
        private val keyLock = Any()
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
    fun getOrCreateDatabasePassphrase(): ByteArray = synchronized(keyLock) {
        val file = File(context.filesDir, PASSPHRASE_FILE_NAME)
        val atomicFile = AtomicFile(file)
        if (file.exists() || File(file.path + ".bak").exists()) {
            // Recover an interrupted atomic write before EncryptedFile opens the final filename.
            atomicFile.openRead().close()
            try {
                readPassphrase(file)
            } catch (e: Exception) {
                // Older builds encrypted using the .tmp basename, then renamed the ciphertext.
                // Try that exact associated filename without changing the original key file.
                val directory = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "key_recovery_").toFile()
                try {
                    val legacy = File(directory, file.name + ".tmp")
                    file.copyTo(legacy)
                    val recovered = try {
                        readPassphrase(legacy)
                    } catch (failure: Exception) {
                        throw java.io.IOException("Unable to unlock the existing database key", e)
                    }
                    writePassphrase(file, recovered)
                    recovered
                } finally {
                    directory.deleteRecursively()
                }
            }
        } else {
            check(!context.getDatabasePath("vaultbrain.db").exists()) {
                "The existing database key is missing; refusing to replace it"
            }
            val passphrase = generatePassphrase()
            writePassphrase(file, passphrase)
            passphrase
        }
    }

    /**
     * Re-wraps an imported SQLCipher key with this device's Android Keystore key.
     * Callers MUST have closed the Room database first (restore flow only) —
     * overwriting the passphrase while the database is open orphans live data.
     */
    fun replaceDatabasePassphrase(passphrase: ByteArray) = synchronized(keyLock) {
        require(passphrase.size == PASSPHRASE_LENGTH_BYTES) { "Invalid database passphrase" }
        writePassphrase(File(context.filesDir, PASSPHRASE_FILE_NAME), passphrase)
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
        return encryptedFile.openFileInput().use { it.readBytes() }.also {
            require(it.size == PASSPHRASE_LENGTH_BYTES) { "Invalid stored database key" }
        }
    }

    private fun writePassphrase(file: File, passphrase: ByteArray) {
        // EncryptedFile authenticates the basename. Stage under the SAME basename.
        val directory = java.nio.file.Files.createTempDirectory(context.filesDir.toPath(), "key_write_").toFile()
        val temp = File(directory, file.name)
        try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                temp,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use { output ->
                output.write(passphrase)
            }
            val verified = readPassphrase(temp)
            try {
                check(verified.contentEquals(passphrase)) { "Database key verification failed" }
            } finally {
                verified.fill(0)
            }
            val atomicFile = AtomicFile(file)
            val output = atomicFile.startWrite()
            try {
                temp.inputStream().use { it.copyTo(output) }
                atomicFile.finishWrite(output)
            } catch (failure: Throwable) {
                atomicFile.failWrite(output)
                throw failure
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
