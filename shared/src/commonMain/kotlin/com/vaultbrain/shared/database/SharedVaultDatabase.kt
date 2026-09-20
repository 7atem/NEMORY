package com.vaultbrain.shared.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaultbrain.shared.database.entity.*
import com.vaultbrain.shared.database.dao.*

/**
 * Kotlin Multiplatform Room Database definition for iOS.
 * Android continues to use core:database VaultDatabase with SQLCipher.
 */
@Database(
    entities = [
        VaultItemEntity::class,
        VaultItemFts::class,
        AuditLogEntity::class,
        NotificationQueueEntity::class,
        BrainMessageEntity::class,
        PersonalCollectionEntity::class,
        PersonalCollectionMembershipEntity::class,
        PersonalCollectionSuggestionEntity::class,
        ExternalRecordEntity::class,
        ExternalConnectionEntity::class,
        VaultReminderEntity::class,
        DerivedFactEntity::class,
        KnowledgeEntityEntity::class,
        ItemEntityCrossRef::class,
        RelationshipEntity::class
    ],
    version = 20,
    exportSchema = false // Handled by Android
)
@androidx.room.TypeConverters(com.vaultbrain.shared.database.util.RoomTypeConverters::class)
internal abstract class SharedVaultDatabase : RoomDatabase() {
    abstract fun knowledgeGraphDao(): KnowledgeGraphDao
    abstract fun derivedFactDao(): DerivedFactDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun notificationQueueDao(): NotificationQueueDao
    abstract fun brainMessageDao(): BrainMessageDao
    abstract fun personalCollectionDao(): PersonalCollectionDao
    abstract fun personalCollectionSuggestionDao(): PersonalCollectionSuggestionDao
    abstract fun externalRecordDao(): ExternalRecordDao
    abstract fun externalConnectionDao(): ExternalConnectionDao
    abstract fun vaultReminderDao(): VaultReminderDao
}
