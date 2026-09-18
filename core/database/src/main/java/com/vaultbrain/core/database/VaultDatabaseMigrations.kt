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

    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vault_reminders` " +
                    "(`id` TEXT NOT NULL, `title` TEXT NOT NULL, `due_at` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `vault_item_id` TEXT, " +
                    "`external_connector_id` TEXT, `external_account_id` TEXT, " +
                    "`external_record_id` TEXT, `personal_collection_id` TEXT, " +
                    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`vault_item_id`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                    "FOREIGN KEY(`personal_collection_id`) REFERENCES `personal_collections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                    "FOREIGN KEY(`external_connector_id`, `external_account_id`, `external_record_id`) " +
                    "REFERENCES `external_records`(`connector_id`, `account_id`, `external_id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_reminders_due_at` ON `vault_reminders` (`due_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_reminders_status` ON `vault_reminders` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_reminders_vault_item_id` ON `vault_reminders` (`vault_item_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_reminders_personal_collection_id` ON `vault_reminders` (`personal_collection_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_vault_reminders_external_connector_id_external_account_id_external_record_id` " +
                    "ON `vault_reminders` (`external_connector_id`, `external_account_id`, `external_record_id`)"
            )
        }
    }

    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_primary_lens_id` ON `vault_items` (`primary_lens_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_is_archived` ON `vault_items` (`is_archived`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_is_stealth` ON `vault_items` (`is_stealth`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_created_at` ON `vault_items` (`created_at`)")
        }
    }

    val MIGRATION_15_16: Migration = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_enrichment_state` ON `vault_items` (`enrichment_state`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_indexing_state` ON `vault_items` (`indexing_state`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_needs_review` ON `vault_items` (`needs_review`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_expiry_date` ON `vault_items` (`expiry_date`)")
        }
    }

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

    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `external_records` " +
                    "(`connector_id` TEXT NOT NULL, " +
                    "`account_id` TEXT NOT NULL, " +
                    "`external_id` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`record_type` TEXT NOT NULL, " +
                    "`retention` TEXT NOT NULL, " +
                    "`title` TEXT, " +
                    "`description` TEXT, " +
                    "`start_at` INTEGER, " +
                    "`end_at` INTEGER, " +
                    "`due_at` INTEGER, " +
                    "`sensitivity` TEXT NOT NULL, " +
                    "`payload` TEXT, " +
                    "`deep_link_uri` TEXT, " +
                    "`hash` TEXT, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "`expires_at` INTEGER, " +
                    "`seen_at` INTEGER, " +
                    "`is_resolved` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`connector_id`, `account_id`, `external_id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_records_source` ON `external_records` (`source`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_records_record_type` ON `external_records` (`record_type`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_records_connector_id` ON `external_records` (`connector_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_records_start_at` ON `external_records` (`start_at`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_records_due_at` ON `external_records` (`due_at`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_records_expires_at` ON `external_records` (`expires_at`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_records_created_at` ON `external_records` (`created_at`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `external_connections` " +
                    "(`connector_id` TEXT NOT NULL, " +
                    "`account_id` TEXT NOT NULL, " +
                    "`state` TEXT NOT NULL, " +
                    "`account_name` TEXT, " +
                    "`account_icon_uri` TEXT, " +
                    "`capabilities` TEXT NOT NULL, " +
                    "`metadata_json` TEXT, " +
                    "`last_sync_at` INTEGER, " +
                    "`last_error` TEXT, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`connector_id`, `account_id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_connections_state` ON `external_connections` (`state`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_external_connections_updated_at` ON `external_connections` (`updated_at`)"
            )
        }
    }

    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Clean up orphan memberships written while FK enforcement was off.
            db.execSQL(
                "DELETE FROM personal_collection_memberships WHERE item_id NOT IN (SELECT id FROM vault_items) OR collection_id NOT IN (SELECT id FROM personal_collections)"
            )

            // Machine-proposed item<->collection associations (see PersonalCollectionSuggestion).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `personal_collection_suggestions` (`collection_id` TEXT NOT NULL, `item_id` TEXT NOT NULL, `confidence` REAL NOT NULL, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`collection_id`, `item_id`), FOREIGN KEY(`collection_id`) REFERENCES `personal_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`item_id`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_personal_collection_suggestions_item_id` ON `personal_collection_suggestions` (`item_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_personal_collection_suggestions_status` ON `personal_collection_suggestions` (`status`)"
            )

            // Extend item FTS with semantic enrichment fields. External-content FTS4
            // tables cannot be altered: drop + recreate + rebuild from vault_items.
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_vault_items_fts_BEFORE_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_vault_items_fts_BEFORE_DELETE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_vault_items_fts_AFTER_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_vault_items_fts_AFTER_INSERT")
            db.execSQL("DROP TABLE IF EXISTS `vault_items_fts`")
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `vault_items_fts` USING FTS4(`title` TEXT NOT NULL, `summary` TEXT, `raw_ocr_text` TEXT, `subtype` TEXT, `topics` TEXT, `entities` TEXT, `tags` TEXT, content=`vault_items`)"
            )
            db.execSQL("INSERT INTO `vault_items_fts`(`vault_items_fts`) VALUES('rebuild')")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_vault_items_fts_BEFORE_UPDATE BEFORE UPDATE ON `vault_items` BEGIN DELETE FROM `vault_items_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_vault_items_fts_BEFORE_DELETE BEFORE DELETE ON `vault_items` BEGIN DELETE FROM `vault_items_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_vault_items_fts_AFTER_UPDATE AFTER UPDATE ON `vault_items` BEGIN INSERT INTO `vault_items_fts`(`docid`, `title`, `summary`, `raw_ocr_text`, `subtype`, `topics`, `entities`, `tags`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`summary`, NEW.`raw_ocr_text`, NEW.`subtype`, NEW.`topics`, NEW.`entities`, NEW.`tags`); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_vault_items_fts_AFTER_INSERT AFTER INSERT ON `vault_items` BEGIN INSERT INTO `vault_items_fts`(`docid`, `title`, `summary`, `raw_ocr_text`, `subtype`, `topics`, `entities`, `tags`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`summary`, NEW.`raw_ocr_text`, NEW.`subtype`, NEW.`topics`, NEW.`entities`, NEW.`tags`); END"
            )
        }
    }

    val MIGRATION_16_17: Migration = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notification_queue_new` (`id` TEXT NOT NULL, `target_id` TEXT NOT NULL, `target_type` TEXT NOT NULL, `trigger_at` INTEGER NOT NULL, `channel_id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `is_delivered` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "INSERT INTO `notification_queue_new` (`id`, `target_id`, `target_type`, `trigger_at`, `channel_id`, `title`, `body`, `is_delivered`) " +
                    "SELECT `id`, `item_id`, 'VAULT_ITEM', `trigger_at`, `channel_id`, `title`, `body`, `is_delivered` FROM `notification_queue`"
            )
            db.execSQL("DROP TABLE `notification_queue`")
            db.execSQL("ALTER TABLE `notification_queue_new` RENAME TO `notification_queue`")
        }
    }

    val MIGRATION_17_18: Migration = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `derived_facts` (`sourceItemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `field` TEXT NOT NULL, `value` TEXT NOT NULL, `evidence` TEXT NOT NULL, `sourceUpdatedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `modelVersion` TEXT NOT NULL, PRIMARY KEY(`sourceItemId`, `kind`, `field`, `value`), FOREIGN KEY(`sourceItemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_derived_facts_sourceItemId` ON `derived_facts` (`sourceItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_derived_facts_kind_value` ON `derived_facts` (`kind`, `value`)")
        }
    }

    val MIGRATION_18_19: Migration = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_entities` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `aliases` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `item_entities` (`itemId` TEXT NOT NULL, `entityId` TEXT NOT NULL, PRIMARY KEY(`itemId`, `entityId`), FOREIGN KEY(`itemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`entityId`) REFERENCES `knowledge_entities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_entities_entityId` ON `item_entities` (`entityId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `relationships` (`id` TEXT NOT NULL, `sourceItemId` TEXT NOT NULL, `targetItemId` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sourceItemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`targetItemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_sourceItemId` ON `relationships` (`sourceItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_targetItemId` ON `relationships` (`targetItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_type` ON `relationships` (`type`)")
        }
    }

    val MIGRATION_19_20: Migration = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE relationships ADD COLUMN evidence TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE relationships ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE relationships ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
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
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20
    )
}
