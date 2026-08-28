package com.vaultbrain.sync.drive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for local database archive export/import.
 *
 * The SQLCipher database files remain encrypted inside the compressed `.vbbackup` archive.
 * Imported media is stored separately and is not included in this archive.
 */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Cloud sign-in is unavailable in this build.
     */
    fun isSignedIn(): Boolean = false

    companion object {
        private const val DB_NAME = "vaultbrain.db"
        private val ALLOWED_ARCHIVE_ENTRIES = setOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm")
    }

    /**
     * Creates an encrypted local backup file (`vaultbrain_backup.vbbackup`).
     */
    suspend fun createEncryptedBackupFile(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val archiveFile = File(backupDir, "vaultbrain_backup.vbbackup")

            val dbFile = context.getDatabasePath(DB_NAME)
            val dbWal = File(dbFile.parentFile, "$DB_NAME-wal")
            val dbShm = File(dbFile.parentFile, "$DB_NAME-shm")

            ZipOutputStream(FileOutputStream(archiveFile)).use { zos ->
                listOf(dbFile, dbWal, dbShm).forEach { file ->
                    if (file.exists()) {
                        zos.putNextEntry(ZipEntry(file.name))
                        FileInputStream(file).use { fis -> fis.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            archiveFile
        }
    }

    /** Restores only recognized SQLCipher database files from an archive. */
    suspend fun restoreFromBackupFile(archiveFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (!archiveFile.exists()) return@runCatching false

            val dbDir = context.getDatabasePath(DB_NAME).parentFile ?: context.filesDir
            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    require(!entry.isDirectory && entry.name in ALLOWED_ARCHIVE_ENTRIES) {
                        "Unsupported backup entry: ${entry.name}"
                    }
                    val targetFile = File(dbDir, entry.name)
                    FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        }
    }

    /**
     * Requests an encrypted backup to Google Drive.
     */
    suspend fun requestBackup(): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Google Drive upload is not configured"))
    }

    /**
     * Requests an encrypted restore from Google Drive.
     */
    suspend fun requestRestore(): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Google Drive restore is not configured"))
    }

    /**
     * Exports the encrypted backup to a user-selected destination URI via Storage Access Framework (SAF).
     */
    suspend fun exportToUri(destinationUri: android.net.Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val archiveResult = createEncryptedBackupFile()
            if (archiveResult.isFailure) return@runCatching false
            val archiveFile = archiveResult.getOrThrow()

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                FileInputStream(archiveFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@runCatching false
            true
        }
    }

    /**
     * Imports and restores an encrypted backup from a user-selected URI via Storage Access Framework (SAF).
     */
    suspend fun importFromUri(sourceUri: android.net.Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File(context.cacheDir, "temp_import.vbbackup")
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@runCatching false

            val success = restoreFromBackupFile(tempFile).getOrDefault(false)
            tempFile.delete()
            success
        }
    }

    /** Cloud sign-in is unavailable in this build. */
    suspend fun signOut(): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Google Drive sign-in is not configured"))
    }
}
