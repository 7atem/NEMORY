package com.vaultbrain.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultItemIndexMigrationTest {
    private val databaseName = "vault-item-index-migration"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration14To15PreservesDataAndCreatesVaultItemIndices() {
        helper.createDatabase(databaseName, 14).apply {
            execSQL(
                """INSERT INTO vault_items (
                    id,title,source_type,created_at,updated_at,parsed_metadata,lens_tags,
                    ai_confidence,extraction_state,enrichment_state,indexing_state,
                    enrichment_attempt_count,needs_review,topics,entities,tags,suggestions,
                    is_pinned,is_archived,is_stealth,required_lens_tier
                ) VALUES (
                    'item-1','Existing memory','TEXT_PASTE',1,1,'{}','[]',
                    0.0,'COMPLETE','COMPLETE','COMPLETE',0,0,'[]','[]','[]','[]',0,0,0,'FREE'
                )"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            databaseName,
            15,
            true,
            VaultDatabaseMigrations.MIGRATION_14_15
        )

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'vault_items'").use { cursor ->
            val indices = generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
            assertTrue(indices.contains("index_vault_items_primary_lens_id"))
            assertTrue(indices.contains("index_vault_items_is_archived"))
            assertTrue(indices.contains("index_vault_items_is_stealth"))
            assertTrue(indices.contains("index_vault_items_created_at"))
        }
        db.query("SELECT title FROM vault_items WHERE id = 'item-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }

    @Test
    fun migration15To16PreservesDataAndCreatesQueueIndices() {
        helper.createDatabase(databaseName, 15).apply {
            execSQL(
                """INSERT INTO vault_items (
                    id,title,source_type,created_at,updated_at,parsed_metadata,lens_tags,
                    ai_confidence,extraction_state,enrichment_state,indexing_state,
                    enrichment_attempt_count,needs_review,topics,entities,tags,suggestions,
                    is_pinned,is_archived,is_stealth,required_lens_tier
                ) VALUES (
                    'item-1','Existing memory','TEXT_PASTE',1,1,'{}','[]',
                    0.0,'COMPLETE','COMPLETE','COMPLETE',0,0,'[]','[]','[]','[]',0,0,0,'FREE'
                )"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            databaseName,
            16,
            true,
            VaultDatabaseMigrations.MIGRATION_15_16
        )

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'vault_items'").use { cursor ->
            val indices = generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
            assertTrue(indices.contains("index_vault_items_enrichment_state"))
            assertTrue(indices.contains("index_vault_items_indexing_state"))
            assertTrue(indices.contains("index_vault_items_needs_review"))
            assertTrue(indices.contains("index_vault_items_expiry_date"))
        }
        db.query("SELECT title FROM vault_items WHERE id = 'item-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }
}
