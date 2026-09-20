package com.vaultbrain.feature.capture

import com.vaultbrain.shared.model.ExperienceId
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.PersonalExperience

/**
 * Canonical definitions for every personal experience available in the capture flow.
 *
 * Two-tier taxonomy:
 * - Primary experiences ([PersonalExperience.parentId] == null) are the high-frequency,
 *   parser-backed types shown as the main options in the experience picker.
 * - Sub-experiences point to their closest primary via [PersonalExperience.parentId].
 *   They keep full keyword scoring and parser support; when one is detected with high
 *   confidence its specific id is stored on the item, while the picker presents it
 *   through its primary parent.
 */
object ExperienceDefinitions {

    private val experiencesById: Map<String, PersonalExperience> = buildMap {
        // Money
        put(ExperienceId.EXPENSE, exp(R.string.experience_expense, R.string.experience_expense_hint, LensId.MONEY))
        put(ExperienceId.INCOME, expSub(R.string.experience_income, R.string.experience_income_hint, LensId.MONEY, ExperienceId.BANK_STATEMENT))
        put(ExperienceId.SUBSCRIPTION, exp(R.string.experience_subscription, R.string.experience_subscription_hint, LensId.MONEY))
        put(ExperienceId.BILL, exp(R.string.experience_bill, R.string.experience_bill_hint, LensId.MONEY))
        put(ExperienceId.BUDGET_TRACKER, expSub(R.string.experience_budget_tracker, R.string.experience_budget_tracker_hint, null, ExperienceId.EXPENSE))
        put(ExperienceId.INVOICE_FOR_WORK, exp(R.string.experience_invoice_for_work, R.string.experience_invoice_for_work_hint, LensId.MONEY))
        put(ExperienceId.TAX_DOCUMENT, exp(R.string.experience_tax_document, R.string.experience_tax_document_hint, LensId.MONEY))
        put(ExperienceId.INVESTMENT_RECORD, expSub(R.string.experience_investment_record, R.string.experience_investment_record_hint, LensId.MONEY, ExperienceId.BANK_STATEMENT))
        put(ExperienceId.INSURANCE_PAYMENT, expSub(R.string.experience_insurance_payment, R.string.experience_insurance_payment_hint, LensId.MONEY, ExperienceId.INSURANCE_POLICY))
        put(ExperienceId.BANK_STATEMENT, exp(R.string.experience_bank_statement, R.string.experience_bank_statement_hint, LensId.MONEY))
        put(ExperienceId.CREDIT_CARD_STATEMENT, expSub(R.string.experience_credit_card_statement, R.string.experience_credit_card_statement_hint, LensId.MONEY, ExperienceId.BANK_STATEMENT))
        put(ExperienceId.LOAN_PAYMENT, expSub(R.string.experience_loan_payment, R.string.experience_loan_payment_hint, LensId.MONEY, ExperienceId.BILL))
        put(ExperienceId.GROCERY_LIST, expSub(R.string.experience_grocery_list, R.string.experience_grocery_list_hint, null, ExperienceId.NOTE))
        put(ExperienceId.GROCERY_RECEIPT, exp(R.string.experience_grocery_receipt, R.string.experience_grocery_receipt_hint, LensId.MONEY))
        put(ExperienceId.PET_EXPENSE, expSub(R.string.experience_pet_expense, R.string.experience_pet_expense_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.TUITION_FEE, expSub(R.string.experience_tuition_fee, R.string.experience_tuition_fee_hint, LensId.MONEY, ExperienceId.BILL))

        // Health
        put(ExperienceId.PRESCRIPTION, exp(R.string.experience_prescription, R.string.experience_prescription_hint, LensId.HEALTH))
        put(ExperienceId.MEDICATION_SCHEDULE, expSub(R.string.experience_medication_schedule, R.string.experience_medication_schedule_hint, LensId.HEALTH, ExperienceId.PRESCRIPTION))
        put(ExperienceId.LAB_RESULT, exp(R.string.experience_lab_result, R.string.experience_lab_result_hint, LensId.HEALTH))
        put(ExperienceId.HEALTH_INSURANCE_CLAIM, expSub(R.string.experience_health_insurance_claim, R.string.experience_health_insurance_claim_hint, LensId.HEALTH, ExperienceId.INSURANCE_POLICY))
        put(ExperienceId.HEALTH_INSURANCE_CARD, exp(R.string.experience_health_insurance_card, R.string.experience_health_insurance_card_hint, LensId.HEALTH))
        put(ExperienceId.VACCINATION_RECORD, exp(R.string.experience_vaccination_record, R.string.experience_vaccination_record_hint, LensId.HEALTH))
        put(ExperienceId.DOCTOR_NOTE, exp(R.string.experience_doctor_note, R.string.experience_doctor_note_hint, LensId.HEALTH))
        put(ExperienceId.FITNESS_TRACKER, expSub(R.string.experience_fitness_tracker, R.string.experience_fitness_tracker_hint, LensId.HEALTH, ExperienceId.DOCTOR_NOTE))
        put(ExperienceId.DENTAL_VISIT, expSub(R.string.experience_dental_visit, R.string.experience_dental_visit_hint, LensId.HEALTH, ExperienceId.DOCTOR_NOTE))
        put(ExperienceId.OPTICAL_PRESCRIPTION, expSub(R.string.experience_optical_prescription, R.string.experience_optical_prescription_hint, LensId.HEALTH, ExperienceId.PRESCRIPTION))
        put(ExperienceId.ALLERGY_RECORD, expSub(R.string.experience_allergy_record, R.string.experience_allergy_record_hint, LensId.HEALTH, ExperienceId.DOCTOR_NOTE))
        put(ExperienceId.PHYSIOTHERAPY_PLAN, expSub(R.string.experience_physiotherapy_plan, R.string.experience_physiotherapy_plan_hint, LensId.HEALTH, ExperienceId.DOCTOR_NOTE))
        put(ExperienceId.MENTAL_HEALTH_NOTE, expSub(R.string.experience_mental_health_note, R.string.experience_mental_health_note_hint, LensId.HEALTH, ExperienceId.DOCTOR_NOTE))
        put(ExperienceId.BLOOD_DONATION, expSub(R.string.experience_blood_donation, R.string.experience_blood_donation_hint, LensId.HEALTH, ExperienceId.LAB_RESULT))

        // Travel
        put(ExperienceId.FLIGHT_TICKET, exp(R.string.experience_flight_ticket, R.string.experience_flight_ticket_hint, LensId.TRAVEL))
        put(ExperienceId.HOTEL_BOOKING, exp(R.string.experience_hotel_booking, R.string.experience_hotel_booking_hint, LensId.TRAVEL))
        put(ExperienceId.BOARDING_PASS, exp(R.string.experience_boarding_pass, R.string.experience_boarding_pass_hint, LensId.TRAVEL))
        put(ExperienceId.TRAVEL_EXPENSE, expSub(R.string.experience_travel_expense, R.string.experience_travel_expense_hint, LensId.TRAVEL, ExperienceId.EXPENSE))
        put(ExperienceId.VISA, expSub(R.string.experience_visa, R.string.experience_visa_hint, LensId.TRAVEL, ExperienceId.PASSPORT))
        put(ExperienceId.ITINERARY, expSub(R.string.experience_itinerary, R.string.experience_itinerary_hint, LensId.TRAVEL, ExperienceId.FLIGHT_TICKET))
        put(ExperienceId.CAR_RENTAL, expSub(R.string.experience_car_rental, R.string.experience_car_rental_hint, LensId.TRAVEL, ExperienceId.FLIGHT_TICKET))
        put(ExperienceId.TRAIN_TICKET, exp(R.string.experience_train_ticket, R.string.experience_train_ticket_hint, LensId.TRAVEL))
        put(ExperienceId.BUS_TICKET, exp(R.string.experience_bus_ticket, R.string.experience_bus_ticket_hint, LensId.TRAVEL))
        put(ExperienceId.FERRY_TICKET, expSub(R.string.experience_ferry_ticket, R.string.experience_ferry_ticket_hint, LensId.TRAVEL, ExperienceId.TRAIN_TICKET))
        put(ExperienceId.TRAVEL_INSURANCE, expSub(R.string.experience_travel_insurance, R.string.experience_travel_insurance_hint, LensId.TRAVEL, ExperienceId.INSURANCE_POLICY))
        put(ExperienceId.TRAVEL_CHECKLIST, expSub(R.string.experience_travel_checklist, R.string.experience_travel_checklist_hint, LensId.TRAVEL, ExperienceId.FLIGHT_TICKET))
        put(ExperienceId.AIRPORT_LOUNGE, expSub(R.string.experience_airport_lounge, R.string.experience_airport_lounge_hint, LensId.TRAVEL, ExperienceId.BOARDING_PASS))
        put(ExperienceId.EVENT_TICKET, exp(R.string.experience_event_ticket, R.string.experience_event_ticket_hint, LensId.TRAVEL))

        // Bureaucracy
        put(ExperienceId.PASSPORT, exp(R.string.experience_passport, R.string.experience_passport_hint, LensId.BUREAUCRACY))
        put(ExperienceId.ID_CARD, exp(R.string.experience_id_card, R.string.experience_id_card_hint, LensId.BUREAUCRACY))
        put(ExperienceId.DRIVERS_LICENSE, exp(R.string.experience_drivers_license, R.string.experience_drivers_license_hint, LensId.BUREAUCRACY))
        put(ExperienceId.RESIDENCE_PERMIT, expSub(R.string.experience_residence_permit, R.string.experience_residence_permit_hint, LensId.BUREAUCRACY, ExperienceId.PASSPORT))
        put(ExperienceId.BUSINESS_CARD, exp(R.string.experience_business_card, R.string.experience_business_card_hint, LensId.BUREAUCRACY))
        put(ExperienceId.BIRTH_CERTIFICATE, expSub(R.string.experience_birth_certificate, R.string.experience_birth_certificate_hint, LensId.BUREAUCRACY, ExperienceId.ID_CARD))
        put(ExperienceId.MARRIAGE_CERTIFICATE, expSub(R.string.experience_marriage_certificate, R.string.experience_marriage_certificate_hint, LensId.BUREAUCRACY, ExperienceId.ID_CARD))
        put(ExperienceId.NOTARIZED_DOCUMENT, expSub(R.string.experience_notarized_document, R.string.experience_notarized_document_hint, LensId.BUREAUCRACY, ExperienceId.ID_CARD))
        put(ExperienceId.TAX_RESIDENCY_CERTIFICATE, expSub(R.string.experience_tax_residency_certificate, R.string.experience_tax_residency_certificate_hint, LensId.BUREAUCRACY, ExperienceId.ID_CARD))
        put(ExperienceId.PROFESSIONAL_LICENSE, expSub(R.string.experience_professional_license, R.string.experience_professional_license_hint, LensId.BUREAUCRACY, ExperienceId.ID_CARD))

        // Home
        put(ExperienceId.WARRANTY, exp(R.string.experience_warranty, R.string.experience_warranty_hint, LensId.BUREAUCRACY))
        put(ExperienceId.APPLIANCE_MANUAL, expSub(R.string.experience_appliance_manual, R.string.experience_appliance_manual_hint, LensId.BUREAUCRACY, ExperienceId.WARRANTY))
        put(ExperienceId.HOME_INVENTORY, expSub(R.string.experience_home_inventory, R.string.experience_home_inventory_hint, LensId.BUREAUCRACY, ExperienceId.WARRANTY))
        put(ExperienceId.RENT_CONTRACT, expSub(R.string.experience_rent_contract, R.string.experience_rent_contract_hint, LensId.BUREAUCRACY, ExperienceId.SERVICE_CONTRACT))
        put(ExperienceId.UTILITY_BILL, exp(R.string.experience_utility_bill, R.string.experience_utility_bill_hint, LensId.MONEY))
        put(ExperienceId.ELECTRICITY_BILL, expSub(R.string.experience_electricity_bill, R.string.experience_electricity_bill_hint, LensId.MONEY, ExperienceId.UTILITY_BILL))
        put(ExperienceId.WATER_BILL, expSub(R.string.experience_water_bill, R.string.experience_water_bill_hint, LensId.MONEY, ExperienceId.UTILITY_BILL))
        put(ExperienceId.GAS_BILL, expSub(R.string.experience_gas_bill, R.string.experience_gas_bill_hint, LensId.MONEY, ExperienceId.UTILITY_BILL))
        put(ExperienceId.INTERNET_BILL, expSub(R.string.experience_internet_bill, R.string.experience_internet_bill_hint, LensId.MONEY, ExperienceId.UTILITY_BILL))
        put(ExperienceId.TELECOM_BILL, expSub(R.string.experience_telecom_bill, R.string.experience_telecom_bill_hint, LensId.MONEY, ExperienceId.UTILITY_BILL))
        put(ExperienceId.PROPERTY_DEED, expSub(R.string.experience_property_deed, R.string.experience_property_deed_hint, LensId.BUREAUCRACY, ExperienceId.WARRANTY))
        put(ExperienceId.MORTGAGE_STATEMENT, expSub(R.string.experience_mortgage_statement, R.string.experience_mortgage_statement_hint, LensId.BUREAUCRACY, ExperienceId.BANK_STATEMENT))
        put(ExperienceId.INSURANCE_POLICY, exp(R.string.experience_insurance_policy, R.string.experience_insurance_policy_hint, LensId.BUREAUCRACY))
        put(ExperienceId.HOME_INSURANCE_POLICY, expSub(R.string.experience_home_insurance_policy, R.string.experience_home_insurance_policy_hint, LensId.BUREAUCRACY, ExperienceId.INSURANCE_POLICY))
        put(ExperienceId.APPLIANCE_REGISTRATION, expSub(R.string.experience_appliance_registration, R.string.experience_appliance_registration_hint, LensId.BUREAUCRACY, ExperienceId.WARRANTY))
        put(ExperienceId.UTILITY_SETUP, expSub(R.string.experience_utility_setup, R.string.experience_utility_setup_hint, LensId.BUREAUCRACY, ExperienceId.UTILITY_BILL))

        // Car
        put(ExperienceId.CAR_INSURANCE, expSub(R.string.experience_car_insurance, R.string.experience_car_insurance_hint, LensId.BUREAUCRACY, ExperienceId.INSURANCE_POLICY))
        put(ExperienceId.CAR_SERVICE, expSub(R.string.experience_car_service, R.string.experience_car_service_hint, LensId.BUREAUCRACY, ExperienceId.CAR_REGISTRATION))
        put(ExperienceId.PARKING_TICKET, expSub(R.string.experience_parking_ticket, R.string.experience_parking_ticket_hint, LensId.BUREAUCRACY, ExperienceId.CAR_REGISTRATION))
        put(ExperienceId.FINE_TICKET, expSub(R.string.experience_fine_ticket, R.string.experience_fine_ticket_hint, LensId.BUREAUCRACY, ExperienceId.CAR_REGISTRATION))
        put(ExperienceId.FUEL_RECEIPT, expSub(R.string.experience_fuel_receipt, R.string.experience_fuel_receipt_hint, LensId.BUREAUCRACY, ExperienceId.EXPENSE))
        put(ExperienceId.CAR_REGISTRATION, exp(R.string.experience_car_registration, R.string.experience_car_registration_hint, LensId.BUREAUCRACY))
        put(ExperienceId.CAR_LOAN, expSub(R.string.experience_car_loan, R.string.experience_car_loan_hint, LensId.MONEY, ExperienceId.BILL))
        put(ExperienceId.TOLL_RECEIPT, expSub(R.string.experience_toll_receipt, R.string.experience_toll_receipt_hint, LensId.BUREAUCRACY, ExperienceId.EXPENSE))
        put(ExperienceId.PARKING_PERMIT, expSub(R.string.experience_parking_permit, R.string.experience_parking_permit_hint, LensId.BUREAUCRACY, ExperienceId.CAR_REGISTRATION))
        put(ExperienceId.CAR_INSPECTION, expSub(R.string.experience_car_inspection, R.string.experience_car_inspection_hint, LensId.BUREAUCRACY, ExperienceId.CAR_REGISTRATION))

        // Shopping
        put(ExperienceId.PRICE_COMPARE, expSub(R.string.experience_price_compare, R.string.experience_price_compare_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.GIFT_IDEA, expSub(R.string.experience_gift_idea, R.string.experience_gift_idea_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.COUPON, expSub(R.string.experience_coupon, R.string.experience_coupon_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.RETURN_POLICY, expSub(R.string.experience_return_policy, R.string.experience_return_policy_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.ORDER_CONFIRMATION, expSub(R.string.experience_order_confirmation, R.string.experience_order_confirmation_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.SHIPPING_TRACKING, expSub(R.string.experience_shipping_tracking, R.string.experience_shipping_tracking_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.LOYALTY_CARD, expSub(R.string.experience_loyalty_card, R.string.experience_loyalty_card_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.GIFT_RECEIPT, expSub(R.string.experience_gift_receipt, R.string.experience_gift_receipt_hint, LensId.MONEY, ExperienceId.EXPENSE))
        put(ExperienceId.WISHLIST, expSub(R.string.experience_wishlist, R.string.experience_wishlist_hint, LensId.MONEY, ExperienceId.EXPENSE))

        // Media
        put(ExperienceId.WEB_ARTICLE, exp(R.string.experience_web_article, R.string.experience_web_article_hint, LensId.MEDIA))
        put(ExperienceId.WATCHLIST, exp(R.string.experience_watchlist, R.string.experience_watchlist_hint, LensId.MEDIA))
        put(ExperienceId.ALREADY_WATCHED, exp(R.string.experience_already_watched, R.string.experience_already_watched_hint, LensId.MEDIA))
        put(ExperienceId.READING_LIST, expSub(R.string.experience_reading_list, R.string.experience_reading_list_hint, LensId.MEDIA, ExperienceId.ALREADY_READ))
        put(ExperienceId.ALREADY_READ, exp(R.string.experience_already_read, R.string.experience_already_read_hint, LensId.MEDIA))
        put(ExperienceId.MUSIC_PLAYLIST, expSub(R.string.experience_music_playlist, R.string.experience_music_playlist_hint, LensId.MEDIA, ExperienceId.WATCHLIST))
        put(ExperienceId.GAME_WISHLIST, expSub(R.string.experience_game_wishlist, R.string.experience_game_wishlist_hint, LensId.MEDIA, ExperienceId.WATCHLIST))
        put(ExperienceId.RECIPE, exp(R.string.experience_recipe, R.string.experience_recipe_hint, LensId.MEDIA))
        put(ExperienceId.PODCAST, expSub(R.string.experience_podcast, R.string.experience_podcast_hint, LensId.MEDIA, ExperienceId.WATCHLIST))
        put(ExperienceId.AUDIOBOOK, expSub(R.string.experience_audiobook, R.string.experience_audiobook_hint, LensId.MEDIA, ExperienceId.ALREADY_READ))
        put(ExperienceId.EBOOK, expSub(R.string.experience_ebook, R.string.experience_ebook_hint, LensId.MEDIA, ExperienceId.ALREADY_READ))
        put(ExperienceId.ONLINE_COURSE, expSub(R.string.experience_online_course, R.string.experience_online_course_hint, LensId.MEDIA, ExperienceId.WATCHLIST))
        put(ExperienceId.CONCERT_TICKET, expSub(R.string.experience_concert_ticket, R.string.experience_concert_ticket_hint, LensId.TRAVEL, ExperienceId.EVENT_TICKET))
        put(ExperienceId.MUSEUM_TICKET, expSub(R.string.experience_museum_ticket, R.string.experience_museum_ticket_hint, LensId.TRAVEL, ExperienceId.EVENT_TICKET))
        put(ExperienceId.THEATER_TICKET, expSub(R.string.experience_theater_ticket, R.string.experience_theater_ticket_hint, LensId.TRAVEL, ExperienceId.EVENT_TICKET))

        // Productivity
        put(ExperienceId.NOTE, exp(R.string.experience_note, R.string.experience_note_hint, null))
        put(ExperienceId.REMINDER, expSub(R.string.experience_reminder, R.string.experience_reminder_hint, null, ExperienceId.NOTE))
        put(ExperienceId.MEETING_NOTES, expSub(R.string.experience_meeting_notes, R.string.experience_meeting_notes_hint, null, ExperienceId.NOTE))
        put(ExperienceId.STUDY_MATERIAL, expSub(R.string.experience_study_material, R.string.experience_study_material_hint, null, ExperienceId.NOTE))
        put(ExperienceId.BOOKMARK, expSub(R.string.experience_bookmark, R.string.experience_bookmark_hint, null, ExperienceId.NOTE))
        put(ExperienceId.TODO_LIST, expSub(R.string.experience_todo_list, R.string.experience_todo_list_hint, null, ExperienceId.NOTE))
        put(ExperienceId.HABIT_TRACKER, expSub(R.string.experience_habit_tracker, R.string.experience_habit_tracker_hint, null, ExperienceId.NOTE))
        put(ExperienceId.GOAL, expSub(R.string.experience_goal, R.string.experience_goal_hint, null, ExperienceId.NOTE))
        put(ExperienceId.PROJECT_PLAN, expSub(R.string.experience_project_plan, R.string.experience_project_plan_hint, null, ExperienceId.NOTE))
        put(ExperienceId.JOURNAL, expSub(R.string.experience_journal, R.string.experience_journal_hint, null, ExperienceId.NOTE))
        put(ExperienceId.GRATITUDE_LOG, expSub(R.string.experience_gratitude_log, R.string.experience_gratitude_log_hint, null, ExperienceId.NOTE))

        // Services
        put(ExperienceId.SERVICE_CONTRACT, exp(R.string.experience_service_contract, R.string.experience_service_contract_hint, LensId.MONEY))
        put(ExperienceId.APPOINTMENT, expSub(R.string.experience_appointment, R.string.experience_appointment_hint, LensId.MONEY, ExperienceId.SERVICE_CONTRACT))
        put(ExperienceId.QUOTE, expSub(R.string.experience_quote, R.string.experience_quote_hint, LensId.MONEY, ExperienceId.SERVICE_CONTRACT))
        put(ExperienceId.PHONE_PLAN, expSub(R.string.experience_phone_plan, R.string.experience_phone_plan_hint, LensId.MONEY, ExperienceId.SUBSCRIPTION))
        put(ExperienceId.INTERNET_PLAN, expSub(R.string.experience_internet_plan, R.string.experience_internet_plan_hint, LensId.MONEY, ExperienceId.SUBSCRIPTION))
        put(ExperienceId.GYM_MEMBERSHIP, expSub(R.string.experience_gym_membership, R.string.experience_gym_membership_hint, LensId.MONEY, ExperienceId.SUBSCRIPTION))
        put(ExperienceId.STREAMING_SERVICE, expSub(R.string.experience_streaming_service, R.string.experience_streaming_service_hint, LensId.MONEY, ExperienceId.SUBSCRIPTION))
        put(ExperienceId.CLOUD_STORAGE, expSub(R.string.experience_cloud_storage, R.string.experience_cloud_storage_hint, LensId.MONEY, ExperienceId.SUBSCRIPTION))
        put(ExperienceId.CLEANING_SERVICE, expSub(R.string.experience_cleaning_service, R.string.experience_cleaning_service_hint, LensId.MONEY, ExperienceId.SERVICE_CONTRACT))

        // Scam guard
        put(ExperienceId.SUSPICIOUS_MESSAGE, exp(R.string.experience_suspicious_message, R.string.experience_suspicious_message_hint, LensId.BUREAUCRACY))
        put(ExperienceId.PHISHING_REPORT, expSub(R.string.experience_phishing_report, R.string.experience_phishing_report_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))
        put(ExperienceId.FRAUD_RECORD, expSub(R.string.experience_fraud_record, R.string.experience_fraud_record_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))
        put(ExperienceId.FAKE_INVOICE, expSub(R.string.experience_fake_invoice, R.string.experience_fake_invoice_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))
        put(ExperienceId.FAKE_CHECK, expSub(R.string.experience_fake_check, R.string.experience_fake_check_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))
        put(ExperienceId.ADVANCE_FEE_FRAUD, expSub(R.string.experience_advance_fee_fraud, R.string.experience_advance_fee_fraud_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))
        put(ExperienceId.LOTTERY_SCAM, expSub(R.string.experience_lottery_scam, R.string.experience_lottery_scam_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))
        put(ExperienceId.TECH_SUPPORT_SCAM, expSub(R.string.experience_tech_support_scam, R.string.experience_tech_support_scam_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))
        put(ExperienceId.ROMANCE_SCAM, expSub(R.string.experience_romance_scam, R.string.experience_romance_scam_hint, LensId.BUREAUCRACY, ExperienceId.SUSPICIOUS_MESSAGE))

        // Generic fallback - presented in the picker as "Just save".
        // It has no lens: items saved this way are "General" (primaryLensId stays null).
        put(
            ExperienceId.GENERIC,
            PersonalExperience(
                id = ExperienceId.GENERIC,
                lensId = null,
                titleRes = R.string.feature_capture_experience_just_save,
                hintRes = R.string.feature_capture_experience_just_save_hint
            )
        )
    }

    /** Canonical picker order: the five lenses, each at most once. */
    private val lensOrder: List<String> = listOf(
        LensId.MONEY,
        LensId.HEALTH,
        LensId.TRAVEL,
        LensId.BUREAUCRACY,
        LensId.MEDIA
    )

    fun allExperiences(): List<PersonalExperience> = experiencesById.values.toList()

    /** Primary experiences only (what the picker lists as main options). */
    fun primaryExperiences(): List<PersonalExperience> =
        experiencesById.values.filter { it.parentId == null && it.id != ExperienceId.GENERIC }

    fun experiencesForLens(lensId: String): List<PersonalExperience> =
        experiencesById.values.filter { it.lensId == lensId }

    fun experienceById(id: String?): PersonalExperience? = id?.let { experiencesById[it] }

    /**
     * Resolves an experience to its primary: primaries resolve to themselves,
     * sub-experiences to their parent (parents are always primary).
     */
    fun resolvePrimary(id: String?): PersonalExperience? {
        val experience = experienceById(id) ?: return null
        return experience.parentId?.let { experiencesById[it] } ?: experience
    }

    /**
     * Lens that should own an item saved with [id]: the experience lens, walking
     * up to the primary parent when the experience itself has none.
     */
    fun effectiveLensId(id: String?): String? {
        val experience = experienceById(id) ?: return null
        return experience.lensId ?: experience.parentId?.let { experiencesById[it]?.lensId }
    }

    /** Pseudo-lens group key for lens-less ("General") primaries in the picker. */
    private const val GENERAL_GROUP = "GENERAL"

    /**
     * Lens groups for the picker: only primary experiences are listed.
     * Lens-less primaries (lensId == null, e.g. Note) are appended under a
     * "General" group so they stay selectable, like the GENERIC fallback.
     */
    fun lensesWithExperiences(): List<Pair<String, List<PersonalExperience>>> {
        val lensGroups = lensOrder.map { lensId ->
            lensId to experiencesForLens(lensId)
                .filter { it.parentId == null && it.id != ExperienceId.GENERIC }
        }.filter { (_, experiences) -> experiences.isNotEmpty() }
        val general = experiencesById.values.filter {
            it.lensId == null && it.parentId == null && it.id != ExperienceId.GENERIC
        }
        return if (general.isEmpty()) lensGroups else lensGroups + (GENERAL_GROUP to general)
    }

    private fun exp(titleRes: Int, hintRes: Int, lensId: String?): PersonalExperience {
        // Derive the experience id from the title resource name so definitions stay in sync.
        val id = titleNameForResource(titleRes)
        return PersonalExperience(id = id, lensId = lensId, titleRes = titleRes, hintRes = hintRes)
    }

    private fun expSub(titleRes: Int, hintRes: Int, lensId: String?, parentId: String): PersonalExperience {
        val id = titleNameForResource(titleRes)
        return PersonalExperience(id = id, lensId = lensId, titleRes = titleRes, hintRes = hintRes, parentId = parentId)
    }

    /**
     * Returns the canonical [ExperienceId] value that matches the `experience_*` string name.
     *
     * This keeps the map above visually tied to the title string; if a resource is renamed the
     * init block on [PersonalExperience] will catch a mismatch at object construction time.
     */
    private fun titleNameForResource(titleRes: Int): String {
        return when (titleRes) {
            R.string.experience_expense -> ExperienceId.EXPENSE
            R.string.experience_income -> ExperienceId.INCOME
            R.string.experience_subscription -> ExperienceId.SUBSCRIPTION
            R.string.experience_bill -> ExperienceId.BILL
            R.string.experience_budget_tracker -> ExperienceId.BUDGET_TRACKER
            R.string.experience_invoice_for_work -> ExperienceId.INVOICE_FOR_WORK
            R.string.experience_tax_document -> ExperienceId.TAX_DOCUMENT
            R.string.experience_investment_record -> ExperienceId.INVESTMENT_RECORD
            R.string.experience_insurance_payment -> ExperienceId.INSURANCE_PAYMENT
            R.string.experience_bank_statement -> ExperienceId.BANK_STATEMENT
            R.string.experience_credit_card_statement -> ExperienceId.CREDIT_CARD_STATEMENT
            R.string.experience_loan_payment -> ExperienceId.LOAN_PAYMENT
            R.string.experience_grocery_list -> ExperienceId.GROCERY_LIST
            R.string.experience_grocery_receipt -> ExperienceId.GROCERY_RECEIPT
            R.string.experience_pet_expense -> ExperienceId.PET_EXPENSE
            R.string.experience_tuition_fee -> ExperienceId.TUITION_FEE
            R.string.experience_prescription -> ExperienceId.PRESCRIPTION
            R.string.experience_medication_schedule -> ExperienceId.MEDICATION_SCHEDULE
            R.string.experience_lab_result -> ExperienceId.LAB_RESULT
            R.string.experience_health_insurance_claim -> ExperienceId.HEALTH_INSURANCE_CLAIM
            R.string.experience_health_insurance_card -> ExperienceId.HEALTH_INSURANCE_CARD
            R.string.experience_vaccination_record -> ExperienceId.VACCINATION_RECORD
            R.string.experience_doctor_note -> ExperienceId.DOCTOR_NOTE
            R.string.experience_fitness_tracker -> ExperienceId.FITNESS_TRACKER
            R.string.experience_dental_visit -> ExperienceId.DENTAL_VISIT
            R.string.experience_optical_prescription -> ExperienceId.OPTICAL_PRESCRIPTION
            R.string.experience_allergy_record -> ExperienceId.ALLERGY_RECORD
            R.string.experience_physiotherapy_plan -> ExperienceId.PHYSIOTHERAPY_PLAN
            R.string.experience_mental_health_note -> ExperienceId.MENTAL_HEALTH_NOTE
            R.string.experience_blood_donation -> ExperienceId.BLOOD_DONATION
            R.string.experience_flight_ticket -> ExperienceId.FLIGHT_TICKET
            R.string.experience_hotel_booking -> ExperienceId.HOTEL_BOOKING
            R.string.experience_boarding_pass -> ExperienceId.BOARDING_PASS
            R.string.experience_travel_expense -> ExperienceId.TRAVEL_EXPENSE
            R.string.experience_visa -> ExperienceId.VISA
            R.string.experience_itinerary -> ExperienceId.ITINERARY
            R.string.experience_car_rental -> ExperienceId.CAR_RENTAL
            R.string.experience_train_ticket -> ExperienceId.TRAIN_TICKET
            R.string.experience_bus_ticket -> ExperienceId.BUS_TICKET
            R.string.experience_ferry_ticket -> ExperienceId.FERRY_TICKET
            R.string.experience_travel_insurance -> ExperienceId.TRAVEL_INSURANCE
            R.string.experience_travel_checklist -> ExperienceId.TRAVEL_CHECKLIST
            R.string.experience_airport_lounge -> ExperienceId.AIRPORT_LOUNGE
            R.string.experience_event_ticket -> ExperienceId.EVENT_TICKET
            R.string.experience_passport -> ExperienceId.PASSPORT
            R.string.experience_id_card -> ExperienceId.ID_CARD
            R.string.experience_drivers_license -> ExperienceId.DRIVERS_LICENSE
            R.string.experience_residence_permit -> ExperienceId.RESIDENCE_PERMIT
            R.string.experience_business_card -> ExperienceId.BUSINESS_CARD
            R.string.experience_birth_certificate -> ExperienceId.BIRTH_CERTIFICATE
            R.string.experience_marriage_certificate -> ExperienceId.MARRIAGE_CERTIFICATE
            R.string.experience_notarized_document -> ExperienceId.NOTARIZED_DOCUMENT
            R.string.experience_tax_residency_certificate -> ExperienceId.TAX_RESIDENCY_CERTIFICATE
            R.string.experience_professional_license -> ExperienceId.PROFESSIONAL_LICENSE
            R.string.experience_warranty -> ExperienceId.WARRANTY
            R.string.experience_appliance_manual -> ExperienceId.APPLIANCE_MANUAL
            R.string.experience_home_inventory -> ExperienceId.HOME_INVENTORY
            R.string.experience_rent_contract -> ExperienceId.RENT_CONTRACT
            R.string.experience_utility_bill -> ExperienceId.UTILITY_BILL
            R.string.experience_electricity_bill -> ExperienceId.ELECTRICITY_BILL
            R.string.experience_water_bill -> ExperienceId.WATER_BILL
            R.string.experience_gas_bill -> ExperienceId.GAS_BILL
            R.string.experience_internet_bill -> ExperienceId.INTERNET_BILL
            R.string.experience_telecom_bill -> ExperienceId.TELECOM_BILL
            R.string.experience_property_deed -> ExperienceId.PROPERTY_DEED
            R.string.experience_mortgage_statement -> ExperienceId.MORTGAGE_STATEMENT
            R.string.experience_home_insurance_policy -> ExperienceId.HOME_INSURANCE_POLICY
            R.string.experience_appliance_registration -> ExperienceId.APPLIANCE_REGISTRATION
            R.string.experience_utility_setup -> ExperienceId.UTILITY_SETUP
            R.string.experience_insurance_policy -> ExperienceId.INSURANCE_POLICY
            R.string.experience_car_insurance -> ExperienceId.CAR_INSURANCE
            R.string.experience_car_service -> ExperienceId.CAR_SERVICE
            R.string.experience_parking_ticket -> ExperienceId.PARKING_TICKET
            R.string.experience_fine_ticket -> ExperienceId.FINE_TICKET
            R.string.experience_fuel_receipt -> ExperienceId.FUEL_RECEIPT
            R.string.experience_car_registration -> ExperienceId.CAR_REGISTRATION
            R.string.experience_car_loan -> ExperienceId.CAR_LOAN
            R.string.experience_toll_receipt -> ExperienceId.TOLL_RECEIPT
            R.string.experience_parking_permit -> ExperienceId.PARKING_PERMIT
            R.string.experience_car_inspection -> ExperienceId.CAR_INSPECTION
            R.string.experience_price_compare -> ExperienceId.PRICE_COMPARE
            R.string.experience_gift_idea -> ExperienceId.GIFT_IDEA
            R.string.experience_coupon -> ExperienceId.COUPON
            R.string.experience_return_policy -> ExperienceId.RETURN_POLICY
            R.string.experience_order_confirmation -> ExperienceId.ORDER_CONFIRMATION
            R.string.experience_shipping_tracking -> ExperienceId.SHIPPING_TRACKING
            R.string.experience_loyalty_card -> ExperienceId.LOYALTY_CARD
            R.string.experience_gift_receipt -> ExperienceId.GIFT_RECEIPT
            R.string.experience_wishlist -> ExperienceId.WISHLIST
            R.string.experience_web_article -> ExperienceId.WEB_ARTICLE
            R.string.experience_watchlist -> ExperienceId.WATCHLIST
            R.string.experience_already_watched -> ExperienceId.ALREADY_WATCHED
            R.string.experience_reading_list -> ExperienceId.READING_LIST
            R.string.experience_already_read -> ExperienceId.ALREADY_READ
            R.string.experience_music_playlist -> ExperienceId.MUSIC_PLAYLIST
            R.string.experience_game_wishlist -> ExperienceId.GAME_WISHLIST
            R.string.experience_recipe -> ExperienceId.RECIPE
            R.string.experience_podcast -> ExperienceId.PODCAST
            R.string.experience_audiobook -> ExperienceId.AUDIOBOOK
            R.string.experience_ebook -> ExperienceId.EBOOK
            R.string.experience_online_course -> ExperienceId.ONLINE_COURSE
            R.string.experience_concert_ticket -> ExperienceId.CONCERT_TICKET
            R.string.experience_museum_ticket -> ExperienceId.MUSEUM_TICKET
            R.string.experience_theater_ticket -> ExperienceId.THEATER_TICKET
            R.string.experience_note -> ExperienceId.NOTE
            R.string.experience_reminder -> ExperienceId.REMINDER
            R.string.experience_meeting_notes -> ExperienceId.MEETING_NOTES
            R.string.experience_study_material -> ExperienceId.STUDY_MATERIAL
            R.string.experience_bookmark -> ExperienceId.BOOKMARK
            R.string.experience_todo_list -> ExperienceId.TODO_LIST
            R.string.experience_habit_tracker -> ExperienceId.HABIT_TRACKER
            R.string.experience_goal -> ExperienceId.GOAL
            R.string.experience_project_plan -> ExperienceId.PROJECT_PLAN
            R.string.experience_journal -> ExperienceId.JOURNAL
            R.string.experience_gratitude_log -> ExperienceId.GRATITUDE_LOG
            R.string.experience_service_contract -> ExperienceId.SERVICE_CONTRACT
            R.string.experience_appointment -> ExperienceId.APPOINTMENT
            R.string.experience_quote -> ExperienceId.QUOTE
            R.string.experience_phone_plan -> ExperienceId.PHONE_PLAN
            R.string.experience_internet_plan -> ExperienceId.INTERNET_PLAN
            R.string.experience_gym_membership -> ExperienceId.GYM_MEMBERSHIP
            R.string.experience_streaming_service -> ExperienceId.STREAMING_SERVICE
            R.string.experience_cloud_storage -> ExperienceId.CLOUD_STORAGE
            R.string.experience_cleaning_service -> ExperienceId.CLEANING_SERVICE
            R.string.experience_suspicious_message -> ExperienceId.SUSPICIOUS_MESSAGE
            R.string.experience_phishing_report -> ExperienceId.PHISHING_REPORT
            R.string.experience_fraud_record -> ExperienceId.FRAUD_RECORD
            R.string.experience_fake_invoice -> ExperienceId.FAKE_INVOICE
            R.string.experience_fake_check -> ExperienceId.FAKE_CHECK
            R.string.experience_advance_fee_fraud -> ExperienceId.ADVANCE_FEE_FRAUD
            R.string.experience_lottery_scam -> ExperienceId.LOTTERY_SCAM
            R.string.experience_tech_support_scam -> ExperienceId.TECH_SUPPORT_SCAM
            R.string.experience_romance_scam -> ExperienceId.ROMANCE_SCAM

            R.string.feature_capture_experience_just_save -> ExperienceId.GENERIC
            else -> throw IllegalArgumentException("Unknown experience title resource: $titleRes")
        }
    }
}
