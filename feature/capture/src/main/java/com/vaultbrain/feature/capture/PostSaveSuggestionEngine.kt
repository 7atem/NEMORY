package com.vaultbrain.feature.capture

import com.vaultbrain.shared.model.ExperienceId

/**
 * Deterministic engine that suggests contextual follow-up captures after saving.
 *
 * After a user saves an item, this suggests related items they might want to capture next.
 * All suggestions are deterministic (no AI needed) — based purely on experience type relationships.
 */
object PostSaveSuggestionEngine {

    /**
     * Returns a list of follow-up suggestions for the given experience ID.
     * Each suggestion contains the experience ID, a user-facing prompt, and an icon hint.
     */
    fun suggestFollowUps(experienceId: String?): List<FollowUpSuggestion> {
        if (experienceId == null) return emptyList()
        return FOLLOW_UP_MAP[experienceId] ?: emptyList()
    }

    data class FollowUpSuggestion(
        val experienceId: String,
        val promptRes: Int,
        val iconHint: String // Material icon name hint for the UI
    )

    private val FOLLOW_UP_MAP: Map<String, List<FollowUpSuggestion>> = mapOf(
        // Travel chain: flight → hotel → car rental → travel insurance → itinerary
        ExperienceId.FLIGHT_TICKET to listOf(
            FollowUpSuggestion(ExperienceId.HOTEL_BOOKING, R.string.followup_add_hotel, "hotel"),
            FollowUpSuggestion(ExperienceId.CAR_RENTAL, R.string.followup_add_car_rental, "directions_car"),
            FollowUpSuggestion(ExperienceId.TRAVEL_INSURANCE, R.string.followup_add_travel_insurance, "health_and_safety")
        ),
        ExperienceId.HOTEL_BOOKING to listOf(
            FollowUpSuggestion(ExperienceId.ITINERARY, R.string.followup_add_itinerary, "map"),
            FollowUpSuggestion(ExperienceId.TRAVEL_EXPENSE, R.string.followup_add_travel_expense, "receipt")
        ),
        ExperienceId.BOARDING_PASS to listOf(
            FollowUpSuggestion(ExperienceId.HOTEL_BOOKING, R.string.followup_add_hotel, "hotel"),
            FollowUpSuggestion(ExperienceId.AIRPORT_LOUNGE, R.string.followup_add_lounge, "airline_seat_flat")
        ),

        // Health chain: prescription → medication schedule, lab → doctor note
        ExperienceId.PRESCRIPTION to listOf(
            FollowUpSuggestion(ExperienceId.MEDICATION_SCHEDULE, R.string.followup_set_med_reminder, "alarm"),
            FollowUpSuggestion(ExperienceId.HEALTH_INSURANCE_CLAIM, R.string.followup_file_insurance_claim, "request_quote")
        ),
        ExperienceId.LAB_RESULT to listOf(
            FollowUpSuggestion(ExperienceId.DOCTOR_NOTE, R.string.followup_add_doctor_note, "medical_services"),
            FollowUpSuggestion(ExperienceId.APPOINTMENT, R.string.followup_book_followup, "event")
        ),
        ExperienceId.DOCTOR_NOTE to listOf(
            FollowUpSuggestion(ExperienceId.PRESCRIPTION, R.string.followup_add_prescription, "medication"),
            FollowUpSuggestion(ExperienceId.APPOINTMENT, R.string.followup_book_followup, "event")
        ),

        // Finance chain: expense → budget, bill → bill
        ExperienceId.EXPENSE to listOf(
            FollowUpSuggestion(ExperienceId.WARRANTY, R.string.followup_save_warranty, "verified_user"),
            FollowUpSuggestion(ExperienceId.RETURN_POLICY, R.string.followup_save_return_policy, "assignment_return")
        ),
        ExperienceId.SUBSCRIPTION to listOf(
            FollowUpSuggestion(ExperienceId.BUDGET_TRACKER, R.string.followup_track_budget, "account_balance_wallet")
        ),

        // Car chain: service → next service reminder
        ExperienceId.CAR_SERVICE to listOf(
            FollowUpSuggestion(ExperienceId.CAR_INSURANCE, R.string.followup_check_car_insurance, "security"),
            FollowUpSuggestion(ExperienceId.CAR_INSPECTION, R.string.followup_check_car_inspection, "build")
        ),
        ExperienceId.CAR_INSURANCE to listOf(
            FollowUpSuggestion(ExperienceId.CAR_REGISTRATION, R.string.followup_check_car_registration, "badge")
        ),

        // Shopping chain: order → tracking
        ExperienceId.ORDER_CONFIRMATION to listOf(
            FollowUpSuggestion(ExperienceId.SHIPPING_TRACKING, R.string.followup_add_tracking, "local_shipping"),
            FollowUpSuggestion(ExperienceId.RETURN_POLICY, R.string.followup_save_return_policy, "assignment_return")
        ),

        // Home chain: warranty → appliance registration
        ExperienceId.WARRANTY to listOf(
            FollowUpSuggestion(ExperienceId.APPLIANCE_REGISTRATION, R.string.followup_register_product, "app_registration"),
            FollowUpSuggestion(ExperienceId.APPLIANCE_MANUAL, R.string.followup_save_manual, "menu_book")
        ),

        // Bureaucracy chain: passport → visa
        ExperienceId.PASSPORT to listOf(
            FollowUpSuggestion(ExperienceId.VISA, R.string.followup_add_visa, "flight_takeoff")
        ),

        // Work chain: invoice → tax document
        ExperienceId.INVOICE_FOR_WORK to listOf(
            FollowUpSuggestion(ExperienceId.TAX_DOCUMENT, R.string.followup_save_for_taxes, "receipt_long")
        )
    )
}
