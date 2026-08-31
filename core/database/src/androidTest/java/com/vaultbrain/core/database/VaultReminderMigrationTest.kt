package com.vaultbrain.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultReminderMigrationTest {
    private val databaseName = "vault-reminder-migration"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration13To14PreservesDataAndCreatesLinkedReminders() {
        helper.createDatabase(databaseName, 13).apply {
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
                "INSERT INTO personal_collections (id,name,created_at,updated_at,archived_at,is_pinned,source) " +
                    "VALUES ('collection-1','Trip',1,1,NULL,0,'USER')"
            )
            execSQL(
                """INSERT INTO external_records (
                    connector_id,account_id,external_id,source,record_type,retention,
                    sensitivity,created_at,updated_at,is_resolved
                ) VALUES ('calendar','device','event-1','CALENDAR','EVENT','INDEXED_REFERENCE',
                    'NORMAL',1,1,0)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            databaseName,
            14,
            true,
            VaultDatabaseMigrations.MIGRATION_13_14
        )
        db.setForeignKeyConstraintsEnabled(true)

        db.execSQL(
            """INSERT INTO vault_reminders (
                id,title,due_at,status,vault_item_id,external_connector_id,
                external_account_id,external_record_id,personal_collection_id,created_at,updated_at
            ) VALUES ('r1','Check booking',1000,'SCHEDULED','item-1','calendar','device',
                'event-1','collection-1',1,1)"""
        )
        db.query("SELECT title,status FROM vault_reminders WHERE id='r1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Check booking", it.getString(0))
            assertEquals("SCHEDULED", it.getString(1))
        }

        db.execSQL("DELETE FROM vault_items WHERE id='item-1'")
        db.execSQL("DELETE FROM personal_collections WHERE id='collection-1'")
        db.execSQL(
            "DELETE FROM external_records WHERE connector_id='calendar' AND account_id='device' AND external_id='event-1'"
        )
        db.query(
            "SELECT vault_item_id,personal_collection_id,external_connector_id,external_account_id,external_record_id " +
                "FROM vault_reminders WHERE id='r1'"
        ).use {
            assertTrue(it.moveToFirst())
            for (index in 0..4) assertTrue(it.isNull(index))
        }
        db.close()
    }
}
