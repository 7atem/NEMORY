package com.vaultbrain.feature.capture

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.security.KeystoreManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaVaultStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val keystoreManager = mockk<KeystoreManager>()
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    private val mockMediaPassphrase = ByteArray(32) { (it + 1).toByte() }
    private val mockDatabasePassphrase = ByteArray(32) { it.toByte() }

    private lateinit var noBackupDir: File
    private lateinit var cacheDir: File
    private lateinit var storage: MediaVaultStorage

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            mockk<Uri> {
                every { scheme } returns "file"
                every { path } returns file.absolutePath
                every { lastPathSegment } returns file.name
            }
        }

        DecoySessionState.setDecoyMode(false)
        noBackupDir = tempFolder.newFolder("no_backup")
        cacheDir = tempFolder.newFolder("cache")

        every { context.noBackupFilesDir } returns noBackupDir
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { keystoreManager.getOrCreateMediaPassphrase() } returns mockMediaPassphrase
        every { keystoreManager.getOrCreateDatabasePassphrase() } returns mockDatabasePassphrase

        storage = MediaVaultStorage(context, keystoreManager)
    }

    @After
    fun tearDown() {
        DecoySessionState.setDecoyMode(false)
        unmockkStatic(Uri::class)
    }

    @Test
    fun `encrypted import and openInputStream roundtrip preserves bytes`() = runTest {
        val testData = "CONFIDENTIAL_NATIONAL_ID_PAYLOAD_12345".toByteArray()
        val incomingFile = File(cacheDir, "temp_scan.jpg").apply { writeBytes(testData) }
        val incomingUri = Uri.fromFile(incomingFile)

        val importedUri = storage.import(incomingUri)
        assertNotNull(importedUri)
        assertTrue(storage.isVaultMedia(importedUri))

        // Raw file on disk must NOT contain plaintext
        val importedFile = File(importedUri.path!!)
        assertTrue(importedFile.exists())
        val rawDiskBytes = importedFile.readBytes()
        assertFalse(String(rawDiskBytes).contains("CONFIDENTIAL_NATIONAL_ID"))

        // Decrypted stream must match original bytes exactly
        val decryptedStream = storage.openInputStream(importedUri)
        assertNotNull(decryptedStream)
        val readBytes = decryptedStream!!.readBytes()
        assertArrayEquals(testData, readBytes)
    }

    @Test
    fun `legacy format v1 encrypted file decrypts with database passphrase fallback`() = runTest {
        val legacyData = "LEGACY_FORMAT_V1_PAYLOAD".toByteArray()
        val iv = ByteArray(12).apply { for (i in indices) this[i] = i.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(mockDatabasePassphrase, "AES"),
            GCMParameterSpec(128, iv)
        )
        val ciphertext = cipher.doFinal(legacyData)

        val encryptedBytes = ByteArray(17 + ciphertext.size)
        System.arraycopy(byteArrayOf(0x4E, 0x4D, 0x45, 0x44), 0, encryptedBytes, 0, 4)
        encryptedBytes[4] = 1
        System.arraycopy(iv, 0, encryptedBytes, 5, 12)
        System.arraycopy(ciphertext, 0, encryptedBytes, 17, ciphertext.size)

        val vaultDir = File(noBackupDir, "vault_media").apply { mkdirs() }
        val legacyFile = File(vaultDir, "legacy_v1.jpg").apply { writeBytes(encryptedBytes) }
        val legacyUri = Uri.fromFile(legacyFile)

        val stream = storage.openInputStream(legacyUri)
        assertNotNull(stream)
        assertArrayEquals(legacyData, stream!!.readBytes())
    }

    @Test
    fun `legacy unencrypted file in vault media is read correctly`() = runTest {
        val legacyData = "LEGACY_PLAINTEXT_RECEIPT".toByteArray()
        val vaultDir = File(noBackupDir, "vault_media").apply { mkdirs() }
        val legacyFile = File(vaultDir, "legacy.jpg").apply { writeBytes(legacyData) }
        val legacyUri = Uri.fromFile(legacyFile)

        val stream = storage.openInputStream(legacyUri)
        assertNotNull(stream)
        assertArrayEquals(legacyData, stream!!.readBytes())
    }

    @Test(expected = IllegalStateException::class)
    fun `import throws IllegalStateException in decoy mode`() = runTest {
        DecoySessionState.setDecoyMode(true)
        val incomingFile = File(cacheDir, "temp_scan.jpg").apply { writeBytes("DATA".toByteArray()) }
        storage.import(Uri.fromFile(incomingFile))
    }

    @Test
    fun `openInputStream fails closed in decoy mode for vault media`() = runTest {
        val testData = "SECRET_DOCUMENT".toByteArray()
        val incomingFile = File(cacheDir, "scan.jpg").apply { writeBytes(testData) }
        val importedUri = storage.import(Uri.fromFile(incomingFile))

        // In normal mode, accessible
        assertNotNull(storage.openInputStream(importedUri))

        // Switch to decoy mode
        DecoySessionState.setDecoyMode(true)

        // Must fail closed (return null)
        assertNull(storage.openInputStream(importedUri))
        assertNull(storage.mimeType(importedUri))
    }
}
