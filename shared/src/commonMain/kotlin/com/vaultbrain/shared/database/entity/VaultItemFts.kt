package com.vaultbrain.shared.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 table for fast full-text search over vault items.
 *
 * Room creates a virtual table backed by SQLite FTS4. The content is kept in
 * sync by triggers when the main [vault_items] table changes.
 */
@Fts4(contentEntity = VaultItemEntity::class)
@Entity(tableName = "vault_items_fts")
data class VaultItemFts(
    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String? = null,

    @ColumnInfo(name = "raw_ocr_text")
    val rawOcrText: String? = null,

    // Semantic fields produced by enrichment (JSON arrays tokenize into their terms).
    @ColumnInfo(name = "subtype")
    val subtype: String? = null,

    @ColumnInfo(name = "topics")
    val topics: String? = null,

    @ColumnInfo(name = "entities")
    val entities: String? = null,

    @ColumnInfo(name = "tags")
    val tags: String? = null
)
