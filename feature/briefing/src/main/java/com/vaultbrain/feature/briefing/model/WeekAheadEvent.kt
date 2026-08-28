package com.vaultbrain.feature.briefing.model

data class WeekAheadEvent(
    val id: String,
    val itemId: String,
    val title: String,
    val date: Long,
    val type: EventType
)

enum class EventType {
    BILL, EXPIRY, TRAVEL, APPOINTMENT
}
