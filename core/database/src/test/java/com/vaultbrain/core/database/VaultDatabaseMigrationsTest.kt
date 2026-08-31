package com.vaultbrain.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class VaultDatabaseMigrationsTest {

    @Test
    fun `migration 1 to 2 preserves rows and defaults enrichment to pending`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_1_2.migrate(database)

        verify(exactly = 1) {
            database.execSQL(
                "ALTER TABLE vault_items ADD COLUMN enrichment_state TEXT NOT NULL DEFAULT 'PENDING'"
            )
        }
    }

    @Test
    fun `migration 2 to 3 adds processing provenance and retry metadata`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_2_3.migrate(database)

        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN user_classification_override TEXT") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN user_edited_at INTEGER") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN extraction_state TEXT NOT NULL DEFAULT 'COMPLETE'") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN indexing_state TEXT NOT NULL DEFAULT 'COMPLETE'") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_attempt_count INTEGER NOT NULL DEFAULT 0") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_claimed_at INTEGER") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_last_attempt_at INTEGER") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN enrichment_error_code TEXT") }
        verify(exactly = 1) {
            database.execSQL(
                "UPDATE vault_items SET enrichment_state = 'FAILED_RETRYABLE', " +
                    "enrichment_error_code = 'UPGRADE_RECOVERY' WHERE enrichment_state = 'RUNNING'"
            )
        }
    }

    @Test
    fun `migration 3 to 4 adds durable duplicate suggestion fields`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_3_4.migrate(database)

        verify(exactly = 1) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN possible_duplicate_of_item_id TEXT")
        }
        verify(exactly = 1) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN duplicate_similarity REAL")
        }
    }

    @Test
    fun `migration 4 to 5 adds encrypted local Brain history`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_4_5.migrate(database)

        verify(exactly = 1) {
            database.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `brain_messages`") })
        }
        verify(exactly = 1) {
            database.execSQL(match { it.contains("index_brain_messages_created_at") })
        }
    }

    @Test
    fun `migration 5 to 6 adds structured Brain evidence`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_5_6.migrate(database)

        verify(exactly = 1) { database.execSQL("ALTER TABLE brain_messages ADD COLUMN evidence_kind TEXT") }
        verify(exactly = 1) { database.execSQL("ALTER TABLE brain_messages ADD COLUMN evidence_headline TEXT") }
        verify(exactly = 1) {
            database.execSQL("ALTER TABLE brain_messages ADD COLUMN evidence_facts TEXT NOT NULL DEFAULT '[]'")
        }
    }

    @Test
    fun `migration 6 to 7 adds Brain response provenance`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_6_7.migrate(database)

        verify(exactly = 1) {
            database.execSQL("ALTER TABLE brain_messages ADD COLUMN response_origin TEXT")
        }
    }

    @Test
    fun `migration 8 to 9 adds primary lens and backfills from first lens tag`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_8_9.migrate(database)

        verify(exactly = 1) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN primary_lens_id TEXT")
        }
        verify(exactly = 1) {
            database.execSQL(match { it.startsWith("UPDATE vault_items SET primary_lens_id =") })
        }
    }

    @Test
    fun `migration 9 to 10 adds open semantic enrichment fields`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_9_10.migrate(database)

        verify(exactly = 1) { database.execSQL("ALTER TABLE vault_items ADD COLUMN subtype TEXT") }
        verify(exactly = 1) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN topics TEXT NOT NULL DEFAULT '[]'")
        }
        verify(exactly = 1) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN entities TEXT NOT NULL DEFAULT '[]'")
        }
        verify(exactly = 1) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
        }
        verify(exactly = 1) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN suggestions TEXT NOT NULL DEFAULT '[]'")
        }
    }

    @Test
    fun `migration 10 to 11 adds manual personal collections without rewriting items`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_10_11.migrate(database)

        verify(exactly = 1) {
            database.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `personal_collections`") })
        }
        verify(exactly = 1) {
            database.execSQL(match {
                it.contains("CREATE TABLE IF NOT EXISTS `personal_collection_memberships`") &&
                    it.contains("ON DELETE CASCADE") &&
                    it.contains("REFERENCES `vault_items`(`id`)")
            })
        }
        verify(exactly = 1) {
            database.execSQL(match { it.contains("index_personal_collection_memberships_item_id") })
        }
        verify(exactly = 0) { database.execSQL(match { it.startsWith("UPDATE vault_items") }) }
    }

    @Test
    fun `migration 12 to 13 adds external context tables`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_12_13.migrate(database)

        verify(exactly = 1) {
            database.execSQL(match {
                it.contains("CREATE TABLE IF NOT EXISTS `external_records`") &&
                    it.contains("PRIMARY KEY(`connector_id`, `account_id`, `external_id`)")
            })
        }
        verify(exactly = 1) {
            database.execSQL(match {
                it.contains("CREATE TABLE IF NOT EXISTS `external_connections`") &&
                    it.contains("PRIMARY KEY(`connector_id`, `account_id`)")
            })
        }
        verify(atLeast = 1) {
            database.execSQL(match { it.contains("index_external_records") })
        }
        verify(atLeast = 1) {
            database.execSQL(match { it.contains("index_external_connections") })
        }
    }

    @Test
    fun `migration 13 to 14 adds linked vault reminders`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_13_14.migrate(database)

        verify(exactly = 1) {
            database.execSQL(match {
                it.contains("CREATE TABLE IF NOT EXISTS `vault_reminders`") &&
                    it.contains("REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL") &&
                    it.contains("REFERENCES `personal_collections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL") &&
                    it.contains("REFERENCES `external_records`(`connector_id`, `account_id`, `external_id`)")
            })
        }
        verify(atLeast = 1) {
            database.execSQL(match { it.contains("index_vault_reminders") })
        }
    }

    @Test
    fun `migration 11 to 12 adds collection suggestions and extends item FTS`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        VaultDatabaseMigrations.MIGRATION_11_12.migrate(database)

        verify(exactly = 1) {
            database.execSQL(match {
                it.contains("CREATE TABLE IF NOT EXISTS `personal_collection_suggestions`") &&
                    it.contains("ON DELETE CASCADE") &&
                    it.contains("REFERENCES `vault_items`(`id`)") &&
                    it.contains("REFERENCES `personal_collections`(`id`)")
            })
        }
        verify(exactly = 1) {
            database.execSQL(match { it.contains("index_personal_collection_suggestions_item_id") })
        }
        // FTS rebuild: old table dropped, recreated with semantic columns, rebuilt.
        verify(exactly = 1) { database.execSQL("DROP TABLE IF EXISTS `vault_items_fts`") }
        verify(exactly = 1) {
            database.execSQL(match {
                it.contains("USING FTS4") && it.contains("`subtype` TEXT") &&
                    it.contains("`topics` TEXT") && it.contains("`entities` TEXT") &&
                    it.contains("`tags` TEXT")
            })
        }
        verify(exactly = 1) {
            database.execSQL("INSERT INTO `vault_items_fts`(`vault_items_fts`) VALUES('rebuild')")
        }
        // Sync triggers recreated (2 deletes + 2 inserts).
        verify(exactly = 4) {
            database.execSQL(match { it.startsWith("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync") })
        }
        // Item rows are never rewritten.
        verify(exactly = 0) { database.execSQL(match { it.startsWith("UPDATE vault_items") }) }
    }
}
