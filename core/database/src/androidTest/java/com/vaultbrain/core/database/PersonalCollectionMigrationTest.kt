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
class PersonalCollectionMigrationTest {
    private val databaseName = "personal-collection-migration"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration10To11PreservesItemsAndEnforcesSafeMembershipCascades() {
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
            11,
            true,
            VaultDatabaseMigrations.MIGRATION_10_11
        )
        // MigrationTestHelper does not apply the app's onConfigure; production
        // enables FK enforcement in VaultDatabase.build's callback.
        db.setForeignKeyConstraintsEnabled(true)
        db.execSQL(
            "INSERT INTO personal_collections (id,name,created_at,updated_at,archived_at,is_pinned,source) VALUES ('c1','My Italy Adventure',2,2,NULL,0,'USER')"
        )
        db.execSQL(
            "INSERT INTO personal_collection_memberships (collection_id,item_id,created_at) VALUES ('c1','item-1',3)"
        )

        val duplicateFailed = runCatching {
            db.execSQL(
                "INSERT INTO personal_collection_memberships (collection_id,item_id,created_at) VALUES ('c1','item-1',4)"
            )
        }.isFailure
        assertEquals(true, duplicateFailed)

        db.execSQL("DELETE FROM personal_collections WHERE id = 'c1'")
        db.query("SELECT COUNT(*) FROM personal_collection_memberships").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT title, subtype, topics FROM vault_items WHERE id = 'item-1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("Existing memory", it.getString(0))
            assertEquals(null, it.getString(1))
            assertEquals("[]", it.getString(2))
        }
        assertFalse(db.isReadOnly)
        db.close()
    }

    @Test
    fun migration11To12AddsSuggestionsAndSemanticFts() {
        helper.createDatabase(databaseName, 11).apply {
            execSQL(
                """INSERT INTO vault_items (
                    id,title,source_type,created_at,updated_at,parsed_metadata,lens_tags,
                    ai_confidence,extraction_state,enrichment_state,indexing_state,
                    enrichment_attempt_count,needs_review,topics,entities,tags,suggestions,
                    is_pinned,is_archived,is_stealth,required_lens_tier
                ) VALUES (
                    'item-1','Spool order','TEXT_PASTE',1,1,'{}','[]',
                    0.0,'COMPLETE','COMPLETE','COMPLETE',0,0,'["filament","PLA"]','["Bambu Lab"]','["3d-printing"]','[]',0,0,0,'FREE'
                )"""
            )
            execSQL(
                "INSERT INTO personal_collections (id,name,created_at,updated_at,archived_at,is_pinned,source) VALUES ('c1','Bambu H2D',2,2,NULL,0,'USER')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            databaseName,
            12,
            true,
            VaultDatabaseMigrations.MIGRATION_11_12
        )
        // MigrationTestHelper does not apply the app's onConfigure; production
        // enables FK enforcement in VaultDatabase.build's callback.
        db.setForeignKeyConstraintsEnabled(true)

        // Suggestion table: persists, duplicate pair rejected by composite PK.
        db.execSQL(
            "INSERT INTO personal_collection_suggestions (collection_id,item_id,confidence,status,created_at,updated_at) VALUES ('c1','item-1',0.9,'SUGGESTED',3,3)"
        )
        val duplicateFailed = runCatching {
            db.execSQL(
                "INSERT INTO personal_collection_suggestions (collection_id,item_id,confidence,status,created_at,updated_at) VALUES ('c1','item-1',0.7,'SUGGESTED',4,4)"
            )
        }.isFailure
        assertTrue("duplicate suggestion pair must be rejected", duplicateFailed)

        // Rejected rows persist as suppression evidence.
        db.execSQL(
            "UPDATE personal_collection_suggestions SET status = 'REJECTED', updated_at = 5 WHERE collection_id = 'c1' AND item_id = 'item-1'"
        )
        db.query("SELECT status FROM personal_collection_suggestions WHERE collection_id = 'c1' AND item_id = 'item-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("REJECTED", it.getString(0))
        }

        // FTS rebuild: semantic columns from pre-migration rows are searchable.
        db.query("SELECT docid FROM vault_items_fts WHERE vault_items_fts MATCH ?", arrayOf("filament")).use {
            assertTrue("rebuilt FTS must index topics of existing rows", it.moveToFirst())
        }
        db.query("SELECT docid FROM vault_items_fts WHERE vault_items_fts MATCH ?", arrayOf("PLA")).use {
            assertTrue(it.moveToFirst())
        }

        // FTS sync triggers work after migration: updates re-index semantic fields.
        db.execSQL("UPDATE vault_items SET subtype = 'filament spool' WHERE id = 'item-1'")
        db.query("SELECT docid FROM vault_items_fts WHERE vault_items_fts MATCH ?", arrayOf("spool")).use {
            assertTrue("trigger must re-index updated subtype", it.moveToFirst())
        }

        // The DAO join must resolve FTS hits back to items (rowid/docid mapping).
        db.query(
            "SELECT v.title FROM vault_items v INNER JOIN vault_items_fts ON v.rowid = vault_items_fts.docid WHERE vault_items_fts MATCH ?",
            arrayOf("filament")
        ).use {
            assertTrue("FTS hit must join back to the vault item", it.moveToFirst())
            assertEquals("Spool order", it.getString(0))
        }

        // Deleting the collection cascades suggestions (and memberships), item survives.
        db.execSQL("DELETE FROM personal_collections WHERE id = 'c1'")
        db.query("SELECT COUNT(*) FROM personal_collection_suggestions").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT title FROM vault_items WHERE id = 'item-1'").use {
            assertTrue("VaultItems must survive collection deletion", it.moveToFirst())
        }
        db.close()
    }

    @Test
    fun migration10To12RunsFullChain() {
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
            12,
            true,
            VaultDatabaseMigrations.MIGRATION_10_11,
            VaultDatabaseMigrations.MIGRATION_11_12
        )
        db.query("SELECT title FROM vault_items WHERE id = 'item-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Existing memory", it.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE name = 'personal_collection_suggestions'").use {
            assertTrue(it.moveToFirst())
        }
        db.close()
    }
}
