package com.vaultbrain.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DerivedFactMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrationPreservesItemsAndCascadesFacts() {
        val name = "derived-fact-migration"
        helper.createDatabase(name, 17).apply {
            execSQL("""INSERT INTO vault_items (
                id,title,source_type,created_at,updated_at,parsed_metadata,lens_tags,
                ai_confidence,extraction_state,enrichment_state,indexing_state,
                enrichment_attempt_count,needs_review,topics,entities,tags,suggestions,
                is_pinned,is_archived,is_stealth,required_lens_tier
            ) VALUES ('a','Policy','TEXT_PASTE',1,1,'{}','[]',0.0,'COMPLETE','COMPLETE',
                'COMPLETE',0,0,'[]','[]','[]','[]',0,0,0,'FREE')""")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 18, true, VaultDatabaseMigrations.MIGRATION_17_18)
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("INSERT INTO derived_facts VALUES ('a','entity','provider','Allianz','Allianz',1,1,'test')")
        db.query("SELECT COUNT(*) FROM derived_facts").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("DELETE FROM vault_items WHERE id='a'")
        db.query("SELECT COUNT(*) FROM derived_facts").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }
}
