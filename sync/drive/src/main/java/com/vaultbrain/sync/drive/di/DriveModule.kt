package com.vaultbrain.sync.drive.di

import android.content.Context
import com.vaultbrain.sync.drive.DriveBackupManager
import com.vaultbrain.core.database.VaultDatabase
import com.vaultbrain.core.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DriveModule {

    @Provides
    @Singleton
    fun provideDriveBackupManager(
        @ApplicationContext context: Context,
        database: VaultDatabase,
        keystoreManager: KeystoreManager
    ): DriveBackupManager = DriveBackupManager(context, database, keystoreManager)
}
