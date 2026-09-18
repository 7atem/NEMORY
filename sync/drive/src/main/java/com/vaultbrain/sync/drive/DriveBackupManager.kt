package com.vaultbrain.sync.drive

import android.content.Context
import android.net.Uri
import com.vaultbrain.core.database.VaultDatabase
import com.vaultbrain.core.security.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.*
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Portable, user-passphrase encrypted backup of the SQLCipher database and private media. */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val keystoreManager: KeystoreManager
) {
    private val backupMutex = Mutex()

    fun isSignedIn(): Boolean = false

    suspend fun exportToUri(destinationUri: Uri, passphrase: CharArray): Result<Boolean> =
        withContext(Dispatchers.IO) {
            backupMutex.withLock {
                runCatching {
                    require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
                        "Use at least $MIN_PASSPHRASE_LENGTH characters"
                    }
                    context.contentResolver.openOutputStream(destinationUri, "w")?.use {
                        writePortableBackup(it, passphrase)
                    } ?: error("Unable to open backup destination")
                    true
                }.also { passphrase.fill('\u0000') }
            }
        }

    /** Restores a validated archive. The app process must be restarted after success. */
    suspend fun importFromUri(sourceUri: Uri, passphrase: CharArray): Result<Boolean> =
        withContext(Dispatchers.IO) {
            backupMutex.withLock {
                runCatching {
                    require(passphrase.isNotEmpty()) { "Backup passphrase is required" }
                    context.contentResolver.openInputStream(sourceUri)?.use {
                        restorePortableBackup(it, passphrase)
                    } ?: error("Unable to open backup")
                    true
                }.also { passphrase.fill('\u0000') }
            }
        }

    @Deprecated("A user passphrase is required")
    suspend fun exportToUri(destinationUri: Uri): Result<Boolean> =
        Result.failure(IllegalArgumentException("A backup passphrase is required"))

    @Deprecated("A user passphrase is required")
    suspend fun importFromUri(sourceUri: Uri): Result<Boolean> =
        Result.failure(IllegalArgumentException("A backup passphrase is required"))

    suspend fun requestBackup(): Result<Boolean> = Result.failure(
        UnsupportedOperationException("Google Drive upload is not configured")
    )

    suspend fun requestRestore(): Result<Boolean> = Result.failure(
        UnsupportedOperationException("Google Drive restore is not configured")
    )

    suspend fun signOut(): Result<Boolean> = Result.failure(
        UnsupportedOperationException("Google Drive sign-in is not configured")
    )

    private fun writePortableBackup(output: OutputStream, passphrase: CharArray) {
        val databaseSnapshot = File.createTempFile("nemory_backup_", ".db", context.cacheDir)
        val databaseKey = keystoreManager.getOrCreateDatabasePassphrase()
        try {
            // Ensure Room has created/migrated the source, then use an independent connection
            // so ATTACH does not alter Room's connection pool or WAL configuration.
            database.openHelper.writableDatabase
            SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, databaseKey,
                null, SQLiteDatabase.OPEN_READWRITE, null).use { writableDatabase ->
                writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                    check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Database checkpoint is busy" }
                }
                var attached = false
                try {
                    writableDatabase.execSQL(
                        "ATTACH DATABASE ? AS nemory_backup KEY ?",
                        arrayOf(databaseSnapshot.absolutePath, databaseKey)
                    )
                    attached = true
                    writableDatabase.beginTransaction()
                    try {
                        writableDatabase.query("SELECT sqlcipher_export('nemory_backup')").use { it.moveToFirst() }
                        writableDatabase.execSQL("PRAGMA nemory_backup.user_version = ${writableDatabase.version}")
                        writableDatabase.setTransactionSuccessful()
                    } finally {
                        writableDatabase.endTransaction()
                    }
                } finally {
                    if (attached) writableDatabase.execSQL("DETACH DATABASE nemory_backup")
                }
            }
            require(databaseSnapshot.isFile) { "Unable to create database snapshot" }
            validateDatabase(databaseSnapshot, databaseKey)

            val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
            val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
            val data = DataOutputStream(BufferedOutputStream(output))
            data.write(MAGIC)
            data.writeInt(FORMAT_VERSION)
            data.writeInt(PBKDF2_ITERATIONS)
            data.write(salt)
            data.write(iv)
            data.flush()

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(MAGIC)
            }
            CipherOutputStream(data, cipher).use { encrypted ->
                ZipOutputStream(BufferedOutputStream(encrypted)).use { zip ->
                    putBytes(zip, MANIFEST_ENTRY, """{"format":1,"app":"Nemory","createdAt":${System.currentTimeMillis()}}""".toByteArray())
                    putBytes(zip, DATABASE_KEY_ENTRY, databaseKey)
                    putFile(zip, DATABASE_ENTRY, databaseSnapshot)
                    mediaDirectory.listFiles()?.filter(File::isFile)?.forEach { file ->
                        putFile(zip, "$MEDIA_PREFIX${file.name}", file)
                    }
                }
            }
        } finally {
            databaseKey.fill(0)
            databaseSnapshot.delete()
        }
    }

    private fun restorePortableBackup(input: InputStream, passphrase: CharArray) {
        val staging = File(context.cacheDir, "portable_restore_staging").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val data = DataInputStream(BufferedInputStream(input))
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            require(magic.contentEquals(MAGIC)) { "Not a Nemory portable backup" }
            require(data.readInt() == FORMAT_VERSION) { "Unsupported backup version" }
            val iterations = data.readInt()
            require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) { "Invalid key parameters" }
            val salt = ByteArray(SALT_BYTES).also(data::readFully)
            val iv = ByteArray(IV_BYTES).also(data::readFully)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(MAGIC)
            }

            var totalBytes = 0L
            val extracted = mutableSetOf<String>()
            ZipInputStream(CipherInputStream(data, cipher)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(!entry.isDirectory && isAllowedEntry(entry.name)) { "Unsupported backup entry" }
                    require(extracted.add(entry.name)) { "Duplicate backup entry" }
                    val target = File(staging, entry.name).canonicalFile
                    require(target.path.startsWith(staging.canonicalPath + File.separator)) { "Unsafe backup path" }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { totalBytes += copyLimited(zip, it, MAX_ARCHIVE_BYTES - totalBytes) }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(setOf(MANIFEST_ENTRY, DATABASE_ENTRY, DATABASE_KEY_ENTRY).all(extracted::contains)) {
                "Backup is incomplete"
            }
            val importedKey = File(staging, DATABASE_KEY_ENTRY).readBytes()
            try {
                require(importedKey.size == DATABASE_KEY_BYTES) { "Invalid database key" }
                validateDatabase(File(staging, DATABASE_ENTRY), importedKey)
                installStagedBackup(staging, importedKey)
            } finally {
                importedKey.fill(0)
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun validateDatabase(file: File, key: ByteArray) {
        val expected = database.openHelper.writableDatabase
        val expectedIdentity = expected.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
            check(it.moveToFirst())
            it.getString(0)
        }
        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READONLY, null).use {
            require(it.isDatabaseIntegrityOk) { "Database integrity check failed" }
            require(it.version == expected.version) { "Unsupported database schema" }
            it.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", emptyArray<String>()).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == expectedIdentity) { "Invalid Room database identity" }
            }
        }
    }

    private fun installStagedBackup(staging: File, importedKey: ByteArray) {
        val dbTarget = context.getDatabasePath(DB_NAME)
        dbTarget.parentFile?.mkdirs()
        val walFile = File(dbTarget.parentFile, "$DB_NAME-wal")
        val shmFile = File(dbTarget.parentFile, "$DB_NAME-shm")
        val rollback = File(context.cacheDir, "vaultbrain_restore_rollback.db")
        val rollbackWal = File(context.cacheDir, "vaultbrain_restore_rollback.db-wal")
        val rollbackShm = File(context.cacheDir, "vaultbrain_restore_rollback.db-shm")
        val mediaRollback = File(context.cacheDir, "vault_media_restore_rollback")
        val oldKey = keystoreManager.getOrCreateDatabasePassphrase()
        database.close()
        val hadExistingDatabase = dbTarget.exists()
        rollback.delete()
        rollbackWal.delete()
        rollbackShm.delete()
        mediaRollback.deleteRecursively()
        // Back up the main DB and any WAL/SHM files so rollback can fully restore them.
        if (hadExistingDatabase) dbTarget.copyTo(rollback, overwrite = true)
        if (walFile.exists()) walFile.copyTo(rollbackWal, overwrite = true)
        if (shmFile.exists()) shmFile.copyTo(rollbackShm, overwrite = true)
        if (mediaDirectory.exists()) mediaDirectory.copyRecursively(mediaRollback, overwrite = true)
        try {
            File(staging, DATABASE_ENTRY).copyTo(dbTarget, overwrite = true)
            walFile.delete()
            shmFile.delete()
            keystoreManager.replaceDatabasePassphrase(importedKey)
            mediaDirectory.deleteRecursively()
            val stagedMedia = File(staging, MEDIA_PREFIX)
            if (stagedMedia.exists()) stagedMedia.copyRecursively(mediaDirectory, overwrite = true)
            rollback.delete()
            rollbackWal.delete()
            rollbackShm.delete()
            mediaRollback.deleteRecursively()
        } catch (failure: Throwable) {
            // Restore DB, WAL, and SHM together to prevent corruption from partial rollback.
            if (rollback.exists()) rollback.copyTo(dbTarget, overwrite = true) else if (!hadExistingDatabase) dbTarget.delete()
            if (rollbackWal.exists()) rollbackWal.copyTo(walFile, overwrite = true) else walFile.delete()
            if (rollbackShm.exists()) rollbackShm.copyTo(shmFile, overwrite = true) else shmFile.delete()
            mediaDirectory.deleteRecursively()
            if (mediaRollback.exists()) mediaRollback.copyRecursively(mediaDirectory, overwrite = true)
            keystoreManager.replaceDatabasePassphrase(oldKey)
            throw failure
        } finally {
            oldKey.fill(0)
            importedKey.fill(0)
        }
    }

    private fun isAllowedEntry(name: String): Boolean =
        name == MANIFEST_ENTRY || name == DATABASE_ENTRY || name == DATABASE_KEY_ENTRY ||
            (name.startsWith(MEDIA_PREFIX) && name.removePrefix(MEDIA_PREFIX).matches(SAFE_MEDIA_NAME))

    private fun copyLimited(input: InputStream, output: OutputStream, remaining: Long): Long {
        require(remaining > 0) { "Backup is too large" }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read
            require(copied <= remaining) { "Backup is too large" }
            output.write(buffer, 0, read)
        }
        return copied
    }

    private fun putBytes(zip: ZipOutputStream, name: String, value: ByteArray) {
        zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry()
    }

    private fun putFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name)); FileInputStream(file).use { it.copyTo(zip) }; zip.closeEntry()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, AES_KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES").also { encoded.fill(0) }
        } finally { spec.clearPassword() }
    }

    private val mediaDirectory: File get() = File(context.noBackupFilesDir, "vault_media")

    private companion object {
        const val DB_NAME = "vaultbrain.db"
        const val FORMAT_VERSION = 1
        const val PBKDF2_ITERATIONS = 310_000
        const val MIN_ACCEPTED_ITERATIONS = 210_000
        const val MAX_ACCEPTED_ITERATIONS = 2_000_000
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val DATABASE_KEY_BYTES = 32
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val MIN_PASSPHRASE_LENGTH = 12
        const val MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATABASE_ENTRY = "database/vaultbrain.db"
        const val DATABASE_KEY_ENTRY = "key/database.key"
        const val MEDIA_PREFIX = "media/"
        val MAGIC = byteArrayOf(0x4E, 0x45, 0x4D, 0x4F, 0x52, 0x59, 0x42, 0x4B)
        val SAFE_MEDIA_NAME = Regex("[A-Za-z0-9._-]{1,180}")
        val secureRandom = SecureRandom()
    }
}
