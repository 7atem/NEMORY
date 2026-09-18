package com.vaultbrain.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class KeystoreManagerInstrumentedTest {
    private val base: Context = ApplicationProvider.getApplicationContext()
    private val root = Files.createTempDirectory(base.cacheDir.toPath(), "key_test_").toFile()
    private val context = object : ContextWrapper(base) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, name)
    }
    private val keyFile get() = File(context.filesDir, "vaultbrain_passphrase.bin")

    @After fun cleanUp() { root.deleteRecursively() }

    @Test fun keySurvivesNewManagerAndReplacement() {
        val original = KeystoreManager(context).getOrCreateDatabasePassphrase()
        assertTrue(original.contentEquals(KeystoreManager(context).getOrCreateDatabasePassphrase()))
        val replacement = ByteArray(32) { (it + 1).toByte() }
        KeystoreManager(context).replaceDatabasePassphrase(replacement)
        assertTrue(replacement.contentEquals(KeystoreManager(context).getOrCreateDatabasePassphrase()))
    }

    @Test fun renamedLegacyCiphertextIsRecoveredWithoutRotatingKey() {
        val expected = ByteArray(32) { (it + 5).toByte() }
        val temporary = File(context.filesDir, keyFile.name + ".tmp")
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedFile.Builder(context, temporary, masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
            .openFileOutput().use { it.write(expected) }
        assertTrue(temporary.renameTo(keyFile))
        assertTrue(expected.contentEquals(KeystoreManager(context).getOrCreateDatabasePassphrase()))
        assertTrue(expected.contentEquals(KeystoreManager(context).getOrCreateDatabasePassphrase()))
    }

    @Test fun corruptKeyIsPreservedAndNeverRegenerated() {
        keyFile.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(runCatching { KeystoreManager(context).getOrCreateDatabasePassphrase() }.isFailure)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(keyFile.readBytes()))
    }

    @Test fun existingDatabaseWithoutKeyFailsClosed() {
        context.getDatabasePath("vaultbrain.db").writeBytes(byteArrayOf(1))
        assertTrue(runCatching { KeystoreManager(context).getOrCreateDatabasePassphrase() }.isFailure)
        assertFalse(keyFile.exists())
    }

    @Test fun interruptedAtomicWriteRetainsCommittedKey() {
        val expected = KeystoreManager(context).getOrCreateDatabasePassphrase()
        android.util.AtomicFile(keyFile).startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
        assertTrue(expected.contentEquals(KeystoreManager(context).getOrCreateDatabasePassphrase()))
    }
}
