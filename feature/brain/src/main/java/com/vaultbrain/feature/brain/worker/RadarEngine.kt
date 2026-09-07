package com.vaultbrain.feature.brain.worker

import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RadarEngine evaluates newly inserted ExternalRecords and returns a list of actionable insights.
 */
@Singleton
class RadarEngine @Inject constructor() {

    data class ActionableInsight(
        val title: String,
        val body: String,
        val triggerAt: Long,
        val type: String // "BILL", "FLIGHT", "PACKAGE", etc.
    )

    fun evaluate(record: ExternalRecord): ActionableInsight? {
        // High Urgency: Bills and Invoices
        if (record.payload.containsKey("total") || record.title?.contains("invoice", true) == true || record.title?.contains("receipt", true) == true) {
            val merchant = record.payload["merchant"]
            val total = record.payload["total"]
            return ActionableInsight(
                title = if (merchant != null) "New Bill: $merchant" else "New Bill",
                body = when {
                    merchant != null && total != null -> "You received an invoice from $merchant for $total."
                    total != null -> "You received an invoice for $total."
                    else -> "You received a new bill or invoice."
                },
                triggerAt = System.currentTimeMillis() + 5000L, // Slight delay for UX
                type = "BILL"
            )
        }

        // High Urgency: Packages
        if (record.payload.containsKey("tracking_number") || record.title?.contains("shipped", true) == true) {
            val merchant = record.payload["merchant"]
            return ActionableInsight(
                title = "Package Update",
                body = if (merchant != null) "Your order from $merchant has an update."
                    else "One of your orders has an update.",
                triggerAt = System.currentTimeMillis() + 5000L,
                type = "PACKAGE"
            )
        }

        // High Urgency: Flights / Travel
        if (record.payload.containsKey("flight_number") || record.title?.contains("flight", true) == true || record.title?.contains("boarding pass", true) == true) {
            val destination = record.payload["destination"]
            val time = record.payload["departure_time"]
            return ActionableInsight(
                title = "Upcoming Flight",
                body = when {
                    destination != null && time != null -> "Your flight to $destination departs at $time."
                    destination != null -> "Your flight to $destination is coming up."
                    else -> "You have an upcoming flight."
                },
                triggerAt = System.currentTimeMillis() + 5000L,
                type = "FLIGHT"
            )
        }

        // Medium Urgency: Appointments & Meetings
        if (record.payload.containsKey("appointment_time") || record.title?.contains("appointment", true) == true || record.title?.contains("reservation", true) == true) {
            val location = record.payload["location"]
            return ActionableInsight(
                title = "Upcoming Appointment",
                body = if (location != null) "You have an appointment at $location scheduled."
                    else "You have an appointment scheduled.",
                triggerAt = System.currentTimeMillis() + 5000L,
                type = "APPOINTMENT"
            )
        }

        // Medium Urgency: Event Tickets
        if (record.payload.containsKey("event_name") || record.title?.contains("ticket", true) == true || record.title?.contains("concert", true) == true) {
            val eventName = record.payload["event_name"]
            return ActionableInsight(
                title = "Event Reminder",
                body = if (eventName != null) "You have tickets for $eventName."
                    else "You have tickets for an upcoming event.",
                triggerAt = System.currentTimeMillis() + 5000L,
                type = "TICKET"
            )
        }

        // Information: Hotels and Lodging
        if (record.title?.contains("hotel", true) == true || record.title?.contains("booking confirmation", true) == true) {
            val hotelName = record.payload["hotel_name"]
            return ActionableInsight(
                title = "Check-in Reminder",
                body = if (hotelName != null) "Your stay at $hotelName is coming up."
                    else "Your hotel stay is coming up.",
                triggerAt = System.currentTimeMillis() + 5000L,
                type = "HOTEL"
            )
        }

        return null
    }
}
