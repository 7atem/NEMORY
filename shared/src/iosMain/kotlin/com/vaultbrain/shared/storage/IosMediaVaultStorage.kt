package com.vaultbrain.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.posix.memcpy

/**
 * iOS Implementation of MediaVaultStorage.
 * Instead of manually rolling AES-256-GCM (which requires Swift CryptoKit bridging),
 * we utilize Apple's hardware-backed Data Protection API (`NSFileProtectionComplete`).
 * 
 * This ensures the file is encrypted by the Secure Enclave and is strictly inaccessible
 * when the iOS device is locked, fulfilling our fail-closed security guarantees.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMediaVaultStorage : MediaVaultStorage {

    private val fileManager = NSFileManager.defaultManager
    
    private val vaultDirectory: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val documentDirectory = paths.first() as String
        val vaultDir = "$documentDirectory/nemory_vault"
        
        if (!fileManager.fileExistsAtPath(vaultDir)) {
            fileManager.createDirectoryAtPath(vaultDir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        vaultDir
    }

    override suspend fun saveMedia(fileName: String, payload: ByteArray): String {
        val filePath = "$vaultDirectory/$fileName"
        
        // Convert ByteArray to NSData
        val nsData = payload.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = payload.size.toULong())
        }
        
        // Write file with hardware-backed encryption (NSFileProtectionComplete)
        val attributes = mapOf<Any?, Any?>(
            NSFileProtectionKey to NSFileProtectionComplete
        )
        
        val success = fileManager.createFileAtPath(filePath, contents = nsData, attributes = attributes)
        if (!success) {
            throw Exception("Failed to save and encrypt media at path: $filePath")
        }
        
        return filePath
    }

    override suspend fun loadMedia(fileName: String): ByteArray? {
        val filePath = "$vaultDirectory/$fileName"
        
        if (!fileManager.fileExistsAtPath(filePath)) {
            return null
        }
        
        val nsData = NSData.dataWithContentsOfFile(filePath) ?: return null
        
        // Convert NSData to ByteArray
        val byteArray = ByteArray(nsData.length.toInt())
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
        }
        
        return byteArray
    }

    override suspend fun deleteMedia(fileName: String): Boolean {
        val filePath = "$vaultDirectory/$fileName"
        if (fileManager.fileExistsAtPath(filePath)) {
            return fileManager.removeItemAtPath(filePath, error = null)
        }
        return false
    }
}
