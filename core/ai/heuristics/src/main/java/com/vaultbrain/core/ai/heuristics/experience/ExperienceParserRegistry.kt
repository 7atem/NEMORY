package com.vaultbrain.core.ai.heuristics.experience

import com.vaultbrain.core.ai.heuristics.experience.parsers.*
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.core.common.model.VaultItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all experience parsers.
 *
 * Provides a parser for a given experience id and can score how well each
 * parser matches an arbitrary item (for auto-suggestion).
 */
@Singleton
class ExperienceParserRegistry @Inject constructor() {

    private val parsers: Map<String, ExperienceParser> = listOf(
        // Finance
        ExpenseParser(),
        SubscriptionParser(),
        BillParser(),
        BankStatementParser(),
        CreditCardStatementParser(),
        LoanPaymentParser(),
        GroceryListParser(),
        PetExpenseParser(),
        TuitionFeeParser(),
        // Health
        PrescriptionParser(),
        MedicationScheduleParser(),
        LabResultParser(),
        HealthInsuranceClaimParser(),
        DentalVisitParser(),
        OpticalPrescriptionParser(),
        AllergyRecordParser(),
        PhysiotherapyPlanParser(),
        MentalHealthNoteParser(),
        BloodDonationParser(),
        // Travel
        FlightTicketParser(),
        HotelBookingParser(),
        TrainTicketParser(),
        BusTicketParser(),
        FerryTicketParser(),
        TravelInsuranceParser(),
        TravelChecklistParser(),
        AirportLoungeParser(),
        // Home
        WarrantyParser(),
        PropertyDeedParser(),
        MortgageStatementParser(),
        HomeInsurancePolicyParser(),
        ApplianceRegistrationParser(),
        UtilitySetupParser(),
        // Car
        CarServiceParser(),
        FuelReceiptParser(),
        CarRegistrationParser(),
        CarLoanParser(),
        TollReceiptParser(),
        ParkingPermitParser(),
        CarInspectionParser(),
        // Bureaucracy
        PassportParser(),
        BusinessCardParser(),
        BirthCertificateParser(),
        MarriageCertificateParser(),
        NotarizedDocumentParser(),
        TaxResidencyCertificateParser(),
        ProfessionalLicenseParser(),
        // Shopping
        GiftIdeaParser(),
        OrderConfirmationParser(),
        ShippingTrackingParser(),
        LoyaltyCardParser(),
        GiftReceiptParser(),
        WishlistParser(),
        // Media
        WatchlistParser(),
        ReadingListParser(),
        RecipeParser(),
        PodcastParser(),
        AudiobookParser(),
        EbookParser(),
        OnlineCourseParser(),
        ConcertTicketParser(),
        MuseumTicketParser(),
        TheaterTicketParser(),
        // Productivity
        NoteParser(),
        ReminderParser(),
        MeetingNotesParser(),
        TodoListParser(),
        HabitTrackerParser(),
        GoalParser(),
        ProjectPlanParser(),
        JournalParser(),
        GratitudeLogParser(),
        // Services
        PhonePlanParser(),
        InternetPlanParser(),
        GymMembershipParser(),
        StreamingServiceParser(),
        CloudStorageParser(),
        CleaningServiceParser(),
        // Scam guard
        SuspiciousMessageParser(),
        FakeInvoiceParser(),
        FakeCheckParser(),
        AdvanceFeeFraudParser(),
        LotteryScamParser(),
        TechSupportScamParser(),
        RomanceScamParser()
    ).associateBy { it.experienceId }

    fun parserFor(experienceId: String): ExperienceParser? {
        return parsers[experienceId]
    }

    /**
     * Returns the experiences that could apply to this item, sorted by match strength.
     * Used to pre-select or suggest experiences in the picker.
     *
     * Scoring combines:
     * 1. Keyword matches from [ExperienceKeywordLibrary].
     * 2. Hard rules from each parser's [ExperienceParser.canApply].
     * 3. A small boost when the item already carries the experience's lens tag.
     *
     * Only experiences with a positive score are returned, up to [topN].
     */
    fun suggestExperiences(item: VaultItem, topN: Int = 5): List<String> {
        val searchableText = buildSearchText(item)
        val scored = parsers.values.map { parser ->
            val keywordScore = scoreKeywords(parser.experienceId, searchableText)
            val ruleScore = if (parser.canApply(item)) 2f else 0f
            val lensBoost = if (parser.experienceId in lensExperienceIds(item.lensTags)) 1f else 0f
            ScoredExperience(parser.experienceId, keywordScore + ruleScore + lensBoost)
        }

        return scored
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(topN)
            .map { it.experienceId }
    }

    /** Score how many keyword groups from the experience are present in the text. */
    private fun scoreKeywords(experienceId: String, text: String): Float {
        val groups = ExperienceKeywordLibrary.keywordsFor(experienceId)
        if (groups.isEmpty()) return 0f
        val normalized = text.lowercase()
        val matches = groups.count { group ->
            group.any { keyword -> normalized.contains(keyword.lowercase()) }
        }
        return matches.toFloat()
    }

    private fun buildSearchText(item: VaultItem): String {
        return buildString {
            append(item.title)
            append(" ")
            item.summary?.let { append(it); append(" ") }
            item.rawOcrText?.let { append(it); append(" ") }
            item.parsedMetadata.values.forEach { append(it); append(" ") }
            item.userNotes?.let { append(it) }
        }.lowercase()
    }

    /**
     * Maps each experience to the lens it belongs to.
     * Keep in sync with [com.vaultbrain.feature.capture.ExperienceDefinitions].
     */
    private val experienceLensMap: Map<String, String> = mapOf(
        // Money
        ExperienceId.EXPENSE to LensId.MONEY,
        ExperienceId.INCOME to LensId.MONEY,
        ExperienceId.SUBSCRIPTION to LensId.MONEY,
        ExperienceId.BILL to LensId.MONEY,
        ExperienceId.INVOICE_FOR_WORK to LensId.MONEY,
        ExperienceId.TAX_DOCUMENT to LensId.MONEY,
        ExperienceId.INVESTMENT_RECORD to LensId.MONEY,
        ExperienceId.INSURANCE_PAYMENT to LensId.MONEY,
        ExperienceId.BANK_STATEMENT to LensId.MONEY,
        ExperienceId.CREDIT_CARD_STATEMENT to LensId.MONEY,
        ExperienceId.LOAN_PAYMENT to LensId.MONEY,
        ExperienceId.GROCERY_RECEIPT to LensId.MONEY,
        ExperienceId.PET_EXPENSE to LensId.MONEY,
        ExperienceId.TUITION_FEE to LensId.MONEY,
        // Health
        ExperienceId.PRESCRIPTION to LensId.HEALTH,
        ExperienceId.MEDICATION_SCHEDULE to LensId.HEALTH,
        ExperienceId.LAB_RESULT to LensId.HEALTH,
        ExperienceId.HEALTH_INSURANCE_CLAIM to LensId.HEALTH,
        ExperienceId.HEALTH_INSURANCE_CARD to LensId.HEALTH,
        ExperienceId.VACCINATION_RECORD to LensId.HEALTH,
        ExperienceId.DOCTOR_NOTE to LensId.HEALTH,
        ExperienceId.FITNESS_TRACKER to LensId.HEALTH,
        ExperienceId.DENTAL_VISIT to LensId.HEALTH,
        ExperienceId.OPTICAL_PRESCRIPTION to LensId.HEALTH,
        ExperienceId.ALLERGY_RECORD to LensId.HEALTH,
        ExperienceId.PHYSIOTHERAPY_PLAN to LensId.HEALTH,
        ExperienceId.MENTAL_HEALTH_NOTE to LensId.HEALTH,
        ExperienceId.BLOOD_DONATION to LensId.HEALTH,
        // Travel
        ExperienceId.FLIGHT_TICKET to LensId.TRAVEL,
        ExperienceId.HOTEL_BOOKING to LensId.TRAVEL,
        ExperienceId.BOARDING_PASS to LensId.TRAVEL,
        ExperienceId.TRAVEL_EXPENSE to LensId.TRAVEL,
        ExperienceId.VISA to LensId.TRAVEL,
        ExperienceId.ITINERARY to LensId.TRAVEL,
        ExperienceId.CAR_RENTAL to LensId.TRAVEL,
        ExperienceId.TRAIN_TICKET to LensId.TRAVEL,
        ExperienceId.BUS_TICKET to LensId.TRAVEL,
        ExperienceId.FERRY_TICKET to LensId.TRAVEL,
        ExperienceId.TRAVEL_INSURANCE to LensId.TRAVEL,
        ExperienceId.TRAVEL_CHECKLIST to LensId.TRAVEL,
        ExperienceId.AIRPORT_LOUNGE to LensId.TRAVEL,
        ExperienceId.EVENT_TICKET to LensId.TRAVEL,
        // Bureaucracy
        ExperienceId.PASSPORT to LensId.BUREAUCRACY,
        ExperienceId.ID_CARD to LensId.BUREAUCRACY,
        ExperienceId.DRIVERS_LICENSE to LensId.BUREAUCRACY,
        ExperienceId.RESIDENCE_PERMIT to LensId.BUREAUCRACY,
        ExperienceId.BUSINESS_CARD to LensId.BUREAUCRACY,
        ExperienceId.BIRTH_CERTIFICATE to LensId.BUREAUCRACY,
        ExperienceId.MARRIAGE_CERTIFICATE to LensId.BUREAUCRACY,
        ExperienceId.NOTARIZED_DOCUMENT to LensId.BUREAUCRACY,
        ExperienceId.TAX_RESIDENCY_CERTIFICATE to LensId.BUREAUCRACY,
        ExperienceId.PROFESSIONAL_LICENSE to LensId.BUREAUCRACY,
        // Home
        ExperienceId.WARRANTY to LensId.BUREAUCRACY,
        ExperienceId.APPLIANCE_MANUAL to LensId.BUREAUCRACY,
        ExperienceId.HOME_INVENTORY to LensId.BUREAUCRACY,
        ExperienceId.RENT_CONTRACT to LensId.BUREAUCRACY,
        ExperienceId.UTILITY_BILL to LensId.MONEY,
        ExperienceId.PROPERTY_DEED to LensId.BUREAUCRACY,
        ExperienceId.MORTGAGE_STATEMENT to LensId.BUREAUCRACY,
        ExperienceId.HOME_INSURANCE_POLICY to LensId.BUREAUCRACY,
        ExperienceId.INSURANCE_POLICY to LensId.BUREAUCRACY,
        ExperienceId.APPLIANCE_REGISTRATION to LensId.BUREAUCRACY,
        ExperienceId.UTILITY_SETUP to LensId.BUREAUCRACY,
        // Car
        ExperienceId.CAR_INSURANCE to LensId.BUREAUCRACY,
        ExperienceId.CAR_SERVICE to LensId.BUREAUCRACY,
        ExperienceId.PARKING_TICKET to LensId.BUREAUCRACY,
        ExperienceId.FINE_TICKET to LensId.BUREAUCRACY,
        ExperienceId.FUEL_RECEIPT to LensId.BUREAUCRACY,
        ExperienceId.CAR_REGISTRATION to LensId.BUREAUCRACY,
        ExperienceId.CAR_LOAN to LensId.MONEY,
        ExperienceId.TOLL_RECEIPT to LensId.BUREAUCRACY,
        ExperienceId.PARKING_PERMIT to LensId.BUREAUCRACY,
        ExperienceId.CAR_INSPECTION to LensId.BUREAUCRACY,
        // Shopping
        ExperienceId.PRICE_COMPARE to LensId.MONEY,
        ExperienceId.GIFT_IDEA to LensId.MONEY,
        ExperienceId.COUPON to LensId.MONEY,
        ExperienceId.RETURN_POLICY to LensId.MONEY,
        ExperienceId.ORDER_CONFIRMATION to LensId.MONEY,
        ExperienceId.SHIPPING_TRACKING to LensId.MONEY,
        ExperienceId.LOYALTY_CARD to LensId.MONEY,
        ExperienceId.GIFT_RECEIPT to LensId.MONEY,
        ExperienceId.WISHLIST to LensId.MONEY,
        // Media
        ExperienceId.WATCHLIST to LensId.MEDIA,
        ExperienceId.ALREADY_WATCHED to LensId.MEDIA,
        ExperienceId.READING_LIST to LensId.MEDIA,
        ExperienceId.ALREADY_READ to LensId.MEDIA,
        ExperienceId.MUSIC_PLAYLIST to LensId.MEDIA,
        ExperienceId.GAME_WISHLIST to LensId.MEDIA,
        ExperienceId.RECIPE to LensId.MEDIA,
        ExperienceId.PODCAST to LensId.MEDIA,
        ExperienceId.AUDIOBOOK to LensId.MEDIA,
        ExperienceId.EBOOK to LensId.MEDIA,
        ExperienceId.ONLINE_COURSE to LensId.MEDIA,
        ExperienceId.CONCERT_TICKET to LensId.MEDIA,
        ExperienceId.MUSEUM_TICKET to LensId.MEDIA,
        ExperienceId.THEATER_TICKET to LensId.MEDIA,
        // Productivity (lens-less "General" experiences: no lens boost)
        // Services
        ExperienceId.SERVICE_CONTRACT to LensId.MONEY,
        ExperienceId.APPOINTMENT to LensId.MONEY,
        ExperienceId.QUOTE to LensId.MONEY,
        ExperienceId.PHONE_PLAN to LensId.MONEY,
        ExperienceId.INTERNET_PLAN to LensId.MONEY,
        ExperienceId.GYM_MEMBERSHIP to LensId.MONEY,
        ExperienceId.STREAMING_SERVICE to LensId.MONEY,
        ExperienceId.CLOUD_STORAGE to LensId.MONEY,
        ExperienceId.CLEANING_SERVICE to LensId.MONEY,
        // Scam guard
        ExperienceId.SUSPICIOUS_MESSAGE to LensId.BUREAUCRACY,
        ExperienceId.PHISHING_REPORT to LensId.BUREAUCRACY,
        ExperienceId.FRAUD_RECORD to LensId.BUREAUCRACY,
        ExperienceId.FAKE_INVOICE to LensId.BUREAUCRACY,
        ExperienceId.FAKE_CHECK to LensId.BUREAUCRACY,
        ExperienceId.ADVANCE_FEE_FRAUD to LensId.BUREAUCRACY,
        ExperienceId.LOTTERY_SCAM to LensId.BUREAUCRACY,
        ExperienceId.TECH_SUPPORT_SCAM to LensId.BUREAUCRACY,
        ExperienceId.ROMANCE_SCAM to LensId.BUREAUCRACY
    )

    private fun lensExperienceIds(lensTags: Set<String>): Set<String> {
        return experienceLensMap.entries
            .filter { it.value in lensTags }
            .map { it.key }
            .toSet()
    }

    private data class ScoredExperience(val experienceId: String, val score: Float)

    fun allExperienceIds(): Set<String> = ExperienceId.ALL_EXPERIENCES
}
