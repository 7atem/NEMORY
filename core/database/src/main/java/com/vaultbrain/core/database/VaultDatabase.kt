package com.vaultbrain.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vaultbrain.shared.database.dao.AuditLogDao
import com.vaultbrain.shared.database.dao.BrainMessageDao
import com.vaultbrain.shared.database.dao.ExternalConnectionDao
import com.vaultbrain.shared.database.dao.ExternalRecordDao
import com.vaultbrain.shared.database.dao.NotificationQueueDao
import com.vaultbrain.shared.database.dao.PersonalCollectionDao
import com.vaultbrain.shared.database.dao.PersonalCollectionSuggestionDao
import com.vaultbrain.shared.database.dao.VaultItemDao
import com.vaultbrain.shared.database.dao.VaultReminderDao
import com.vaultbrain.shared.database.entity.AuditLogEntity
import com.vaultbrain.shared.database.entity.BrainMessageEntity
import com.vaultbrain.shared.database.entity.ExternalConnectionEntity
import com.vaultbrain.shared.database.entity.ExternalRecordEntity
import com.vaultbrain.shared.database.entity.NotificationQueueEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionMembershipEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionSuggestionEntity
import com.vaultbrain.shared.database.entity.VaultItemEntity
import com.vaultbrain.shared.database.entity.VaultItemFts
import com.vaultbrain.shared.database.entity.VaultReminderEntity
import com.vaultbrain.shared.database.util.RoomTypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Encrypted Room database for VaultBrain structured data.
 *
 * The database file is encrypted with SQLCipher using a key derived from
 * the Android Keystore (see [com.vaultbrain.core.security.KeystoreManager]).
 *
 * Migration baseline: schema version 1. `exportSchema = true` writes JSON schema files
 * to `schemas/` (see `room.schemaLocation` in the module build file). Add future
 * migrations to [VaultDatabaseMigrations] and register them in [build].
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
        com.vaultbrain.shared.database.entity.DerivedFactEntity::class,
        com.vaultbrain.shared.database.entity.KnowledgeEntityEntity::class,
        com.vaultbrain.shared.database.entity.ItemEntityCrossRef::class,
        com.vaultbrain.shared.database.entity.RelationshipEntity::class
    ],
    version = 20,
    exportSchema = true
)
@TypeConverters(RoomTypeConverters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun knowledgeGraphDao(): com.vaultbrain.shared.database.dao.KnowledgeGraphDao

    abstract fun derivedFactDao(): com.vaultbrain.shared.database.dao.DerivedFactDao

    abstract fun vaultItemDao(): VaultItemDao

    abstract fun auditLogDao(): AuditLogDao

    abstract fun notificationQueueDao(): NotificationQueueDao

    abstract fun brainMessageDao(): BrainMessageDao

    abstract fun personalCollectionDao(): PersonalCollectionDao

    abstract fun personalCollectionSuggestionDao(): PersonalCollectionSuggestionDao

    abstract fun externalRecordDao(): ExternalRecordDao

    abstract fun externalConnectionDao(): ExternalConnectionDao

    abstract fun vaultReminderDao(): VaultReminderDao

    companion object {
        private const val DATABASE_NAME = "vaultbrain.db"

        init {
            runCatching { System.loadLibrary("sqlcipher") }
        }

        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(*VaultDatabaseMigrations.ALL)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // SQLite FK enforcement is per-connection and OFF by default.
                        // Membership/suggestion cascades rely on it.
                        db.execSQL("PRAGMA foreign_keys=ON;")
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Room automatically creates FTS triggers for Fts4 contentEntity.
                    }
                })
                .build()
        }
    }
}
