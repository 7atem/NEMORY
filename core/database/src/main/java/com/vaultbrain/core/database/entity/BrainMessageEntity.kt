package com.vaultbrain.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A locally persisted Brain turn. Source IDs are bound by app code, never parsed from model text. */
@Entity(
    tableName = "brain_messages",
    indices = [Index(value = ["created_at"])]
)
data class BrainMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val text: String,
    @ColumnInfo(name = "source_item_ids") val sourceItemIds: List<String> = emptyList(),
    val confidence: Float = 0f,
    @ColumnInfo(name = "is_error") val isError: Boolean = false,
    @ColumnInfo(name = "original_query") val originalQuery: String? = null,
    @ColumnInfo(name = "evidence_kind") val evidenceKind: String? = null,
    @ColumnInfo(name = "evidence_headline") val evidenceHeadline: String? = null,
    @ColumnInfo(name = "evidence_facts") val evidenceFacts: List<String> = emptyList(),
    @ColumnInfo(name = "response_origin") val responseOrigin: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
