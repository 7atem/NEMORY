package com.vaultbrain.shared.database

import androidx.room.RoomDatabaseConstructor

/**
 * Android stub for SharedVaultDatabaseConstructor.
 * On Android, the app uses [VaultDatabase] in :core:database with SQLCipher encryption,
 * so SharedVaultDatabase is never initialized or used on Android.
 */
public actual object SharedVaultDatabaseConstructor : RoomDatabaseConstructor<SharedVaultDatabase> {
    actual override fun initialize(): SharedVaultDatabase = error("SharedVaultDatabase is not used on Android. Use VaultDatabase in :core:database.")
}
