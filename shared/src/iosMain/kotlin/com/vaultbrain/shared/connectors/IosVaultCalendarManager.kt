package com.vaultbrain.shared.connectors

import platform.EventKit.EKEventStore
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi

class IosVaultCalendarManager : VaultCalendarManager {

    private val eventStore = EKEventStore()

    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, _ ->
            continuation.resume(granted)
        }
    }

    override fun hasPermission(): Boolean {
        return EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent) == EKAuthorizationStatusAuthorized
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getEvents(startTimeMs: Long, endTimeMs: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()

        val startDate = NSDate.dateWithTimeIntervalSince1970((startTimeMs / 1000).toDouble())
        val endDate = NSDate.dateWithTimeIntervalSince1970((endTimeMs / 1000).toDouble())
        val predicate = eventStore.predicateForEventsWithStartDate(startDate, endDate, calendars = null)
        
        val events = eventStore.eventsMatchingPredicate(predicate)
        
        return events.mapNotNull { it as? EKEvent }.map { event ->
            CalendarEvent(
                id = event.eventIdentifier ?: "",
                title = event.title ?: "",
                description = event.notes,
                startTimeMs = (event.startDate?.timeIntervalSince1970?.times(1000))?.toLong() ?: 0L,
                endTimeMs = (event.endDate?.timeIntervalSince1970?.times(1000))?.toLong() ?: 0L,
                location = event.location
            )
        }
    }
}
