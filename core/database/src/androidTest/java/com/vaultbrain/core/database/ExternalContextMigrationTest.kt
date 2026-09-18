package com.vaultbrain.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalContextMigrationTest {
    private val databaseName = "external-context-migration"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration12To13CreatesExternalTablesAndPreservesItems() {
        helper.createDatabase(databaseName, 12).apply {
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
            execSQL(
                "INSERT INTO personal_collections (id,name,created_at,updated_at,archived_at,is_pinned,source) VALUES ('c1','My Italy Adventure',2,2,NULL,0,'USER')"
            )
            execSQL(
                "INSERT INTO personal_collection_memberships (collection_id,item_id,created_at) VALUES ('c1','item-1',3)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            databaseName,
            13,
            true,
            VaultDatabaseMigrations.MIGRATION_12_13
        )
        // MigrationTestHelper does not apply the app's onConfigure; production
        // enables FK enforcement in VaultDatabase.build's callback.
        db.setForeignKeyConstraintsEnabled(true)

        // New external context tables exist.
        fun tableExists(name: String): Boolean {
            return db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(name)
            ).use { it.moveToFirst() }
        }
        assertTrue("external_records must be created", tableExists("external_records"))
        assertTrue("external_connections must be created", tableExists("external_connections"))

        // Expected indexes on external_records.
        val expectedIndexes = listOf(
            "index_external_records_source",
            "index_external_records_record_type",
            "index_external_records_connector_id",
            "index_external_records_start_at",
            "index_external_records_due_at",
            "index_external_records_expires_at",
            "index_external_records_created_at",
            "index_external_connections_state",
            "index_external_connections_updated_at"
        )
        expectedIndexes.forEach { indexName ->
            val exists = db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf(indexName)
            ).use { it.moveToFirst() }
            assertTrue("index $indexName must exist", exists)
        }

        // Composite PK on external_records (connector_id, account_id, external_id) rejects duplicates.
        db.execSQL(
            """INSERT INTO external_records (
                connector_id,account_id,external_id,source,record_type,retention,sensitivity,
                created_at,updated_at,is_resolved
            ) VALUES (
                'calendar','work@example.com','evt-1','CALENDAR','EVENT','INDEXED_REFERENCE',
                'NORMAL',10,10,0
            )"""
        )
        val duplicateRecordFailed = runCatching {
            db.execSQL(
                """INSERT INTO external_records (
                    connector_id,account_id,external_id,source,record_type,retention,sensitivity,
                    created_at,updated_at,is_resolved
                ) VALUES (
                    'calendar','work@example.com','evt-1','CALENDAR','EVENT','INDEXED_REFERENCE',
                    'NORMAL',11,11,0
                )"""
            )
        }.isFailure
        assertTrue("duplicate external_records PK must be rejected", duplicateRecordFailed)

        // Composite PK on external_connections (connector_id, account_id) rejects duplicates.
        db.execSQL(
            """INSERT INTO external_connections (
                connector_id,account_id,state,capabilities,created_at,updated_at
            ) VALUES (
                'calendar','work@example.com','CONNECTED','[]',20,20
            )"""
        )
        val duplicateConnectionFailed = runCatching {
            db.execSQL(
                """INSERT INTO external_connections (
                    connector_id,account_id,state,capabilities,created_at,updated_at
                ) VALUES (
                    'calendar','work@example.com','DISCONNECTED','[]',21,21
                )"""
            )
        }.isFailure
        assertTrue("duplicate external_connections PK must be rejected", duplicateConnectionFailed)

        // Pre-existing vault item, collection, and membership survive.
        db.query("SELECT title FROM vault_items WHERE id = 'item-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Existing memory", it.getString(0))
        }
        db.query("SELECT name FROM personal_collections WHERE id = 'c1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("My Italy Adventure", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM personal_collection_memberships").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }

        // Existing FTS data remains searchable; triggers still work after migration.
        db.query("SELECT docid FROM vault_items_fts WHERE vault_items_fts MATCH ?", arrayOf("Existing")).use {
            assertTrue("FTS must still index pre-migration title", it.moveToFirst())
        }
        db.execSQL("UPDATE vault_items SET title = 'Updated memory' WHERE id = 'item-1'")
        db.query("SELECT docid FROM vault_items_fts WHERE vault_items_fts MATCH ?", arrayOf("Updated")).use {
            assertTrue("FTS trigger must re-index updated title", it.moveToFirst())
        }

        assertFalse(db.isReadOnly)
        db.close()
    }

    @Test
    fun migration10To13RunsFullChainWithoutDestructiveFallback() {
        helper.createDatabase(databaseName, 10).apply {
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
            13,
            true,
            VaultDatabaseMigrations.MIGRATION_10_11,
            VaultDatabaseMigrations.MIGRATION_11_12,
            VaultDatabaseMigrations.MIGRATION_12_13
        )

        db.query("SELECT title FROM vault_items WHERE id = 'item-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Existing memory", it.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE name = 'external_records'").use {
            assertTrue(it.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE name = 'external_connections'").use {
            assertTrue(it.moveToFirst())
        }
        db.close()
    }
}
