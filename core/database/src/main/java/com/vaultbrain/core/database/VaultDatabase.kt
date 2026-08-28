package com.vaultbrain.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vaultbrain.core.database.dao.AuditLogDao
import com.vaultbrain.core.database.dao.BrainMessageDao
import com.vaultbrain.core.database.dao.NotificationQueueDao
import com.vaultbrain.core.database.dao.PersonalCollectionDao
import com.vaultbrain.core.database.dao.VaultItemDao
import com.vaultbrain.core.database.entity.AuditLogEntity
import com.vaultbrain.core.database.entity.BrainMessageEntity
import com.vaultbrain.core.database.entity.NotificationQueueEntity
import com.vaultbrain.core.database.entity.PersonalCollectionEntity
import com.vaultbrain.core.database.entity.PersonalCollectionMembershipEntity
import com.vaultbrain.core.database.entity.VaultItemEntity
import com.vaultbrain.core.database.entity.VaultItemFts
import com.vaultbrain.core.database.util.RoomTypeConverters
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
        PersonalCollectionMembershipEntity::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(RoomTypeConverters::class)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun vaultItemDao(): VaultItemDao

    abstract fun auditLogDao(): AuditLogDao

    abstract fun notificationQueueDao(): NotificationQueueDao

    abstract fun brainMessageDao(): BrainMessageDao

    abstract fun personalCollectionDao(): PersonalCollectionDao

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
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Room automatically creates FTS triggers for Fts4 contentEntity.
                    }
                })
                .build()
        }
    }
}
