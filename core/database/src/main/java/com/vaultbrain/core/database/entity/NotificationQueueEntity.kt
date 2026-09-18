package com.vaultbrain.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pending notification scheduled for future delivery.
 */
@Entity(tableName = "notification_queue")
data class NotificationQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "target_id")
    val targetId: String,

    @ColumnInfo(name = "target_type")
    val targetType: String, // "VAULT_ITEM" or "EXTERNAL_RECORD"

    @ColumnInfo(name = "trigger_at")
    val triggerAt: Long,

    @ColumnInfo(name = "channel_id")
    val channelId: String, // CRITICAL, REMINDER, BACKGROUND, SHOPPING

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "is_delivered")
    val isDelivered: Boolean = false
)
