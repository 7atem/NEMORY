package com.vaultbrain.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
