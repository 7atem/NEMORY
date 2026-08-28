package com.vaultbrain.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central registry for Room database migrations.
 *
 * Add a new [Migration] here whenever the schema version is bumped. Migrations are
 * applied in order by [VaultDatabase.build].
 */
object VaultDatabaseMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE vault_items ADD COLUMN enrichment_state TEXT NOT NULL DEFAULT 'PENDING'"
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vault_items ADD COLUMN user_classification_override TEXT")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN user_edited_at INTEGER")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN extraction_state TEXT NOT NULL DEFAULT 'COMPLETE'")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN indexing_state TEXT NOT NULL DEFAULT 'COMPLETE'")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_attempt_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_claimed_at INTEGER")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_last_attempt_at INTEGER")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_error_code TEXT")
            db.execSQL(
                "UPDATE vault_items SET enrichment_state = 'FAILED_RETRYABLE', " +
                    "enrichment_error_code = 'UPGRADE_RECOVERY' WHERE enrichment_state = 'RUNNING'"
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vault_items ADD COLUMN possible_duplicate_of_item_id TEXT")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN duplicate_similarity REAL")
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `brain_messages` (`id` TEXT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `source_item_ids` TEXT NOT NULL, `confidence` REAL NOT NULL, `is_error` INTEGER NOT NULL, `original_query` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_brain_messages_created_at` ON `brain_messages` (`created_at`)"
            )
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE brain_messages ADD COLUMN evidence_kind TEXT")
            db.execSQL("ALTER TABLE brain_messages ADD COLUMN evidence_headline TEXT")
            db.execSQL("ALTER TABLE brain_messages ADD COLUMN evidence_facts TEXT NOT NULL DEFAULT '[]'")
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE brain_messages ADD COLUMN response_origin TEXT")
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vault_items ADD COLUMN experience_id TEXT")
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vault_items ADD COLUMN primary_lens_id TEXT")
            // Backfill from the first element of the JSON lens_tags array ('["MONEY",...]').
            db.execSQL(
                "UPDATE vault_items SET primary_lens_id = " +
                    "CASE WHEN lens_tags IS NULL OR length(lens_tags) < 5 THEN NULL " +
                    "ELSE substr(lens_tags, 3, instr(substr(lens_tags, 3), '\"') - 1) END"
            )
        }
    }

    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vault_items ADD COLUMN subtype TEXT")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN topics TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN entities TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE vault_items ADD COLUMN suggestions TEXT NOT NULL DEFAULT '[]'")
        }
    }

    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `personal_collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `archived_at` INTEGER, `is_pinned` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_personal_collections_archived_at` ON `personal_collections` (`archived_at`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_personal_collections_is_pinned` ON `personal_collections` (`is_pinned`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_personal_collections_updated_at` ON `personal_collections` (`updated_at`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `personal_collection_memberships` (`collection_id` TEXT NOT NULL, `item_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`collection_id`, `item_id`), FOREIGN KEY(`collection_id`) REFERENCES `personal_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`item_id`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_personal_collection_memberships_item_id` ON `personal_collection_memberships` (`item_id`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11
    )
}
