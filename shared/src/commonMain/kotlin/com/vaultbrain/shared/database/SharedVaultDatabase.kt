package com.vaultbrain.shared.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.vaultbrain.shared.database.dao.AuditLogDao
import com.vaultbrain.shared.database.dao.BrainMessageDao
import com.vaultbrain.shared.database.dao.DerivedFactDao
import com.vaultbrain.shared.database.dao.ExternalConnectionDao
import com.vaultbrain.shared.database.dao.ExternalRecordDao
import com.vaultbrain.shared.database.dao.KnowledgeGraphDao
import com.vaultbrain.shared.database.dao.NotificationQueueDao
import com.vaultbrain.shared.database.dao.PersonalCollectionDao
import com.vaultbrain.shared.database.dao.PersonalCollectionSuggestionDao
import com.vaultbrain.shared.database.dao.VaultItemDao
import com.vaultbrain.shared.database.dao.VaultReminderDao
import com.vaultbrain.shared.database.entity.AuditLogEntity
import com.vaultbrain.shared.database.entity.BrainMessageEntity
import com.vaultbrain.shared.database.entity.DerivedFactEntity
import com.vaultbrain.shared.database.entity.ExternalConnectionEntity
import com.vaultbrain.shared.database.entity.ExternalRecordEntity
import com.vaultbrain.shared.database.entity.ItemEntityCrossRef
import com.vaultbrain.shared.database.entity.KnowledgeEntityEntity
import com.vaultbrain.shared.database.entity.NotificationQueueEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionMembershipEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionSuggestionEntity
import com.vaultbrain.shared.database.entity.RelationshipEntity
import com.vaultbrain.shared.database.entity.VaultItemEntity
import com.vaultbrain.shared.database.entity.VaultItemFts
import com.vaultbrain.shared.database.entity.VaultReminderEntity

@Database(
    entities = [
        VaultItemEntity::class,
        VaultItemFts::class,
        AuditLogEntity::class,
        BrainMessageEntity::class,
        ExternalConnectionEntity::class,
        ExternalRecordEntity::class,
        PersonalCollectionEntity::class,
        PersonalCollectionMembershipEntity::class,
        PersonalCollectionSuggestionEntity::class,
        VaultReminderEntity::class,
        DerivedFactEntity::class,
        KnowledgeEntityEntity::class,
        ItemEntityCrossRef::class,
        RelationshipEntity::class
    ],
    version = 20,
    exportSchema = false // Handled by Android
)
@ConstructedBy(SharedVaultDatabaseConstructor::class)
@androidx.room.TypeConverters(com.vaultbrain.shared.database.util.RoomTypeConverters::class)
abstract class SharedVaultDatabase : RoomDatabase() {
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

/**
 * Room KSP generates the actual implementation per platform target (iOS).
 * Declared expect here so common code (and @ConstructedBy) can reference it.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SharedVaultDatabaseConstructor : RoomDatabaseConstructor<SharedVaultDatabase> {
    override fun initialize(): SharedVaultDatabase
}
