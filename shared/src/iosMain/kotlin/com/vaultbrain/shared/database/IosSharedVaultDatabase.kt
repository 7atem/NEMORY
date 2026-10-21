package com.vaultbrain.shared.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Builds the shared Room database for iOS, stored in the app documents
 * directory with the bundled SQLite driver.
 *
 * NOTE: the iOS store starts fresh (no migration from the Android SQLCipher
 * database yet) and is currently unencrypted — wire the SQLCipher-equivalent
 * driver before storing real user data on iOS.
 */
@OptIn(ExperimentalForeignApi::class)
fun getSharedVaultDatabase(): SharedVaultDatabase {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Unable to resolve iOS documents directory")

    val dbPath = "${documentDirectory.path}/shared_vault.db"

    return Room.databaseBuilder<SharedVaultDatabase>(
        name = dbPath,
        factory = { SharedVaultDatabaseConstructor.initialize() },
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
