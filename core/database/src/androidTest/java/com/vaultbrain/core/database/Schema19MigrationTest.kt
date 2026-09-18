package com.vaultbrain.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Schema19MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun oldestSchemaTo19PreservesRecordsAndFts() = migrate(1)
    @Test fun schema18To19PreservesRecordsAndCascadesGraph() = migrate(18)

    private fun migrate(from: Int) {
        val name = "assessment-migration-$from"
        helper.createDatabase(name, from).apply {
            // Common required fields across the supported schema history; later fields have defaults.
            val columns = mutableListOf<String>()
            val values = mutableListOf<Any>()
            query("PRAGMA table_info(vault_items)").use { cursor ->
                while (cursor.moveToNext()) {
                    val field = cursor.getString(1)
                    if (field in setOf("id", "title") || (cursor.getInt(3) == 1 && cursor.isNull(4))) {
                        columns += field
                        values += when {
                            field == "id" -> "a"
                            field == "title" -> "MigrationEvidence"
                            field == "source_type" -> "MANUAL"
                            field == "required_lens_tier" -> "FREE"
                            cursor.getString(2) in setOf("INTEGER", "REAL") -> 0
                            else -> "[]"
                        }
                    }
                }
            }
            execSQL("INSERT INTO vault_items (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})", values.toTypedArray())
            close()
        }
        helper.runMigrationsAndValidate(name, 19, true, *VaultDatabaseMigrations.ALL).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            db.query("SELECT title FROM vault_items WHERE id='a'").use { assertTrue(it.moveToFirst()); assertEquals("MigrationEvidence", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM vault_items_fts WHERE vault_items_fts MATCH 'MigrationEvidence'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.execSQL("INSERT INTO knowledge_entities VALUES ('entity','Example','provider','[]')")
            db.execSQL("INSERT INTO item_entities VALUES ('a','entity')")
            db.execSQL("INSERT INTO relationships VALUES ('r','a','a','SAME_ENTITY')")
            db.execSQL("DELETE FROM vault_items WHERE id='a'")
            for (table in listOf("relationships", "item_entities")) {
                db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}
