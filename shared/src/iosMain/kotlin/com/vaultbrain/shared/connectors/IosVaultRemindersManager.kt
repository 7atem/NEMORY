package com.vaultbrain.shared.connectors

import platform.EventKit.EKEventStore
import platform.EventKit.EKEntityType
import platform.EventKit.EKReminder
import platform.EventKit.EKAuthorizationStatusAuthorized
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi

class IosVaultRemindersManager : VaultRemindersManager {

    private val eventStore = EKEventStore()

    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeReminder) { granted, _ ->
            continuation.resume(granted)
        }
    }

    override fun hasPermission(): Boolean {
        return EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeReminder) == EKAuthorizationStatusAuthorized
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getReminders(): List<ReminderItem> = suspendCoroutine { continuation ->
        if (!hasPermission()) {
            continuation.resume(emptyList())
            return@suspendCoroutine
        }

        val predicate = eventStore.predicateForRemindersInCalendars(null)
        
        eventStore.fetchRemindersMatchingPredicate(predicate) { reminders ->
            val items = reminders?.mapNotNull { it as? EKReminder }?.map { reminder ->
                // Approximate dueDate since NSDateComponents conversion requires calendar mapping
                val isCompleted = reminder.completed
                ReminderItem(
                    id = reminder.calendarItemIdentifier,
                    title = reminder.title ?: "",
                    isCompleted = isCompleted,
                    dueDateMs = null // Requires more complex NSDateComponents parsing, omitted for brevity
                )
            } ?: emptyList()
            
            continuation.resume(items)
        }
    }
}
