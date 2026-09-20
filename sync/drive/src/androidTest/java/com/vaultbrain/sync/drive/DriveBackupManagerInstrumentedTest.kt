package com.vaultbrain.sync.drive

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.core.database.VaultDatabase
import com.vaultbrain.shared.database.entity.VaultItemEntity
import com.vaultbrain.core.security.KeystoreManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DriveBackupManagerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: VaultDatabase? = null
    private val backupFile: File get() = File(context.cacheDir, "portable-backup-test.nemory")

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
        backupFile.delete()
    }

    @Test
    fun exportedSnapshotRestoresAnOpenEncryptedDatabase() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        backupFile.delete()
        val keystoreManager = KeystoreManager(context)
        val databaseKey = keystoreManager.getOrCreateDatabasePassphrase()
        val expectedDatabaseKey = databaseKey.copyOf()
        database = VaultDatabase.build(context, databaseKey)

        val item = VaultItemEntity(
            id = "backup-test-item",
            title = "Backup test",
            sourceType = SourceType.MANUAL,
            parsedMetadata = "{}",
            lensTags = "[]"
        )
        database!!.vaultItemDao().insert(item)
        val manager = DriveBackupManager(context, database!!, keystoreManager)
        val wasWalEnabled = database!!.openHelper.writableDatabase.isWriteAheadLoggingEnabled

        assertTrue(
            manager.exportToUri(Uri.fromFile(backupFile), "test-passphrase".toCharArray()).getOrThrow()
        )
        assertEquals(wasWalEnabled, database!!.openHelper.writableDatabase.isWriteAheadLoggingEnabled)
        database!!.vaultItemDao().deleteById(item.id)
        assertNull(database!!.vaultItemDao().getById(item.id))

        assertTrue(
            manager.importFromUri(Uri.fromFile(backupFile), "test-passphrase".toCharArray()).getOrThrow()
        )
        val restoredDatabaseKey = keystoreManager.getOrCreateDatabasePassphrase()
        assertArrayEquals(expectedDatabaseKey, restoredDatabaseKey)
        database = VaultDatabase.build(context, restoredDatabaseKey)
        assertEquals(item.title, database!!.vaultItemDao().getById(item.id)?.title)
    }

    @Test
    fun wrongPassphraseAndTruncatedArchiveLeaveLiveDatabaseUntouched() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        val keystore = KeystoreManager(context)
        val originalKey = keystore.getOrCreateDatabasePassphrase()
        database = VaultDatabase.build(context, originalKey)
        val item = VaultItemEntity(id = "preserved", title = "Keep me", sourceType = SourceType.MANUAL,
            parsedMetadata = "{}", lensTags = "[]")
        database!!.vaultItemDao().insert(item)
        val manager = DriveBackupManager(context, database!!, keystore)
        manager.exportToUri(Uri.fromFile(backupFile), "test-passphrase".toCharArray()).getOrThrow()
        assertTrue(manager.importFromUri(Uri.fromFile(backupFile), "wrong-passphrase".toCharArray()).isFailure)
        assertEquals(item.title, database!!.vaultItemDao().getById(item.id)?.title)
        java.io.RandomAccessFile(backupFile, "rw").use { it.setLength(it.length() - 16) }
        assertTrue(manager.importFromUri(Uri.fromFile(backupFile), "test-passphrase".toCharArray()).isFailure)
        assertEquals(item.title, database!!.vaultItemDao().getById(item.id)?.title)
        assertTrue(originalKey.contentEquals(keystore.getOrCreateDatabasePassphrase()))
    }

    private companion object {
        const val DATABASE_NAME = "vaultbrain.db"
    }
}
