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
class Schema20MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun schema19To20AddsRelationshipEvidenceColumns() {
        val name = "schema-19-to-20"
        helper.createDatabase(name, 19).apply {
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
            val insert = "INSERT INTO vault_items (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})"
            execSQL(insert, values.toTypedArray())
            values[values.indexOf("a")] = "b"
            execSQL(insert, values.toTypedArray())
            execSQL("INSERT INTO relationships VALUES ('r','a','b','SAME_ENTITY')")
            close()
        }
        helper.runMigrationsAndValidate(name, 20, true, *VaultDatabaseMigrations.ALL).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            db.query("SELECT evidence, confidence, createdAt FROM relationships WHERE id='r'").use {
                assertTrue(it.moveToFirst())
                assertEquals("", it.getString(0))
                assertEquals(1.0f, it.getFloat(1), 0.0f)
                assertEquals(0L, it.getLong(2))
            }
            db.execSQL("INSERT INTO relationships VALUES ('r2','b','a','REPLACES','Policy #POL-99201',0.75,1757000000000)")
            db.query("SELECT evidence, confidence, createdAt FROM relationships WHERE id='r2'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Policy #POL-99201", it.getString(0))
                assertEquals(0.75f, it.getFloat(1), 0.001f)
                assertEquals(1757000000000L, it.getLong(2))
            }
            db.execSQL("DELETE FROM vault_items WHERE id='a'")
            db.query("SELECT COUNT(*) FROM relationships").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}
