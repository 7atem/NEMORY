package com.vaultbrain.core.integrations.calendar

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String? = null,
    val isVisible: Boolean = true
)

data class CalendarEventRow(
    val eventId: Long,
    val calendarId: Long,
    val title: String?,
    val description: String?,
    val location: String?,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean,
    val recurrenceRule: String?,
    val timezone: String?,
    val deleted: Boolean = false
)

data class CalendarEventDraft(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val allDay: Boolean = false
)
