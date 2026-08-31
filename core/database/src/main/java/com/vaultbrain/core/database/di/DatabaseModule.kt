package com.vaultbrain.core.database.di

import android.content.Context
import com.vaultbrain.core.database.VaultDatabase
import com.vaultbrain.core.database.dao.AuditLogDao
import com.vaultbrain.core.database.dao.BrainMessageDao
import com.vaultbrain.core.database.dao.ExternalConnectionDao
import com.vaultbrain.core.database.dao.ExternalRecordDao
import com.vaultbrain.core.database.dao.NotificationQueueDao
import com.vaultbrain.core.database.dao.PersonalCollectionDao
import com.vaultbrain.core.database.dao.PersonalCollectionSuggestionDao
import com.vaultbrain.core.database.dao.VaultItemDao
import com.vaultbrain.core.database.dao.VaultReminderDao
import com.vaultbrain.core.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the encrypted Room database and its DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager
    ): VaultDatabase {
        val passphrase = keystoreManager.getOrCreateDatabasePassphrase()
        return VaultDatabase.build(context, passphrase)
    }

    @Provides
    @Singleton
    fun provideVaultItemDao(database: VaultDatabase): VaultItemDao = database.vaultItemDao()

    @Provides
    @Singleton
    fun provideAuditLogDao(database: VaultDatabase): AuditLogDao = database.auditLogDao()

    @Provides
    @Singleton
    fun provideNotificationQueueDao(database: VaultDatabase): NotificationQueueDao =
        database.notificationQueueDao()

    @Provides
    @Singleton
    fun provideBrainMessageDao(database: VaultDatabase): BrainMessageDao = database.brainMessageDao()

    @Provides
    @Singleton
    fun providePersonalCollectionDao(database: VaultDatabase): PersonalCollectionDao =
        database.personalCollectionDao()

    @Provides
    @Singleton
    fun providePersonalCollectionSuggestionDao(database: VaultDatabase): PersonalCollectionSuggestionDao =
        database.personalCollectionSuggestionDao()

    @Provides
    @Singleton
    fun provideExternalRecordDao(database: VaultDatabase): ExternalRecordDao =
        database.externalRecordDao()

    @Provides
    @Singleton
    fun provideExternalConnectionDao(database: VaultDatabase): ExternalConnectionDao =
        database.externalConnectionDao()

    @Provides
    @Singleton
    fun provideVaultReminderDao(database: VaultDatabase): VaultReminderDao =
        database.vaultReminderDao()
}
