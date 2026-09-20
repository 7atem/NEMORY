package com.vaultbrain.shared.connectors

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val location: String?
)

/**
 * Cross-platform abstraction for accessing device calendars.
 * Replaces direct Android ContentResolver queries.
 */
interface VaultCalendarManager {
    /**
     * Requests calendar read permissions from the user.
     */
    suspend fun requestPermission(): Boolean
    
    /**
     * Checks if permission is granted.
     */
    fun hasPermission(): Boolean

    /**
     * Fetches calendar events between the given time range.
     */
    suspend fun getEvents(startTimeMs: Long, endTimeMs: Long): List<CalendarEvent>
}

/** CompositionLocal for providing the platform-specific VaultCalendarManager. */
val LocalVaultCalendarManager = androidx.compose.runtime.staticCompositionLocalOf<VaultCalendarManager?> { null }
