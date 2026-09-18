# Generates feature/capture ExperienceDefinitions.kt with the two-tier taxonomy.
# (ID, lens, parentId or None). Primaries have parent None.
L = {'M': 'LensId.MONEY', 'H': 'LensId.HEALTH', 'T': 'LensId.TRAVEL', 'B': 'LensId.BUREAUCRACY', 'E': 'LensId.MEDIA'}

SPEC = [
    ('Money', [
        ('EXPENSE', 'M', None), ('INCOME', 'M', 'BANK_STATEMENT'), ('SUBSCRIPTION', 'M', None),
        ('BILL', 'M', None), ('BUDGET_TRACKER', 'M', 'EXPENSE'), ('INVOICE_FOR_WORK', 'M', None),
        ('TAX_DOCUMENT', 'M', 'INVOICE_FOR_WORK'), ('INVESTMENT_RECORD', 'M', 'BANK_STATEMENT'),
        ('INSURANCE_PAYMENT', 'M', 'BILL'), ('BANK_STATEMENT', 'M', None),
        ('CREDIT_CARD_STATEMENT', 'M', 'BANK_STATEMENT'), ('LOAN_PAYMENT', 'M', 'BILL'),
        ('GROCERY_LIST', 'M', 'GROCERY_RECEIPT'), ('GROCERY_RECEIPT', 'M', None),
        ('PET_EXPENSE', 'M', 'EXPENSE'), ('TUITION_FEE', 'M', 'BILL'),
    ]),
    ('Health', [
        ('PRESCRIPTION', 'H', None), ('MEDICATION_SCHEDULE', 'H', 'PRESCRIPTION'),
        ('LAB_RESULT', 'H', None), ('HEALTH_INSURANCE_CLAIM', 'H', 'DOCTOR_NOTE'),
        ('VACCINATION_RECORD', 'H', None), ('DOCTOR_NOTE', 'H', None),
        ('FITNESS_TRACKER', 'H', 'DOCTOR_NOTE'), ('DENTAL_VISIT', 'H', 'DOCTOR_NOTE'),
        ('OPTICAL_PRESCRIPTION', 'H', 'PRESCRIPTION'), ('ALLERGY_RECORD', 'H', 'DOCTOR_NOTE'),
        ('PHYSIOTHERAPY_PLAN', 'H', 'DOCTOR_NOTE'), ('MENTAL_HEALTH_NOTE', 'H', 'DOCTOR_NOTE'),
        ('BLOOD_DONATION', 'H', 'LAB_RESULT'),
    ]),
    ('Travel', [
        ('FLIGHT_TICKET', 'T', None), ('HOTEL_BOOKING', 'T', None),
        ('BOARDING_PASS', 'T', None), ('TRAVEL_EXPENSE', 'T', 'FLIGHT_TICKET'),
        ('VISA', 'T', 'PASSPORT'), ('ITINERARY', 'T', 'FLIGHT_TICKET'),
        ('CAR_RENTAL', 'T', 'FLIGHT_TICKET'), ('TRAIN_TICKET', 'T', None),
        ('BUS_TICKET', 'T', None), ('FERRY_TICKET', 'T', 'TRAIN_TICKET'),
        ('TRAVEL_INSURANCE', 'T', 'CAR_INSURANCE'), ('TRAVEL_CHECKLIST', 'T', 'FLIGHT_TICKET'),
        ('AIRPORT_LOUNGE', 'T', 'BOARDING_PASS'),
    ]),
    ('Bureaucracy', [
        ('PASSPORT', 'B', None), ('ID_CARD', 'B', None),
        ('DRIVERS_LICENSE', 'B', None), ('RESIDENCE_PERMIT', 'B', 'PASSPORT'),
        ('BUSINESS_CARD', 'B', None), ('BIRTH_CERTIFICATE', 'B', 'ID_CARD'),
        ('MARRIAGE_CERTIFICATE', 'B', 'ID_CARD'), ('NOTARIZED_DOCUMENT', 'B', 'ID_CARD'),
        ('TAX_RESIDENCY_CERTIFICATE', 'B', 'ID_CARD'), ('PROFESSIONAL_LICENSE', 'B', 'DRIVERS_LICENSE'),
    ]),
    ('Home', [
        ('WARRANTY', 'B', None), ('APPLIANCE_MANUAL', 'B', 'WARRANTY'),
        ('HOME_INVENTORY', 'B', 'WARRANTY'), ('RENT_CONTRACT', 'B', 'SERVICE_CONTRACT'),
        ('UTILITY_BILL', 'B', None), ('PROPERTY_DEED', 'B', 'WARRANTY'),
        ('MORTGAGE_STATEMENT', 'B', 'BANK_STATEMENT'), ('HOME_INSURANCE_POLICY', 'B', 'CAR_INSURANCE'),
        ('APPLIANCE_REGISTRATION', 'B', 'WARRANTY'), ('UTILITY_SETUP', 'B', 'UTILITY_BILL'),
    ]),
    ('Car', [
        ('CAR_INSURANCE', 'T', None), ('CAR_SERVICE', 'T', 'CAR_REGISTRATION'),
        ('PARKING_TICKET', 'T', 'CAR_REGISTRATION'), ('FINE_TICKET', 'T', 'CAR_REGISTRATION'),
        ('FUEL_RECEIPT', 'T', 'EXPENSE'), ('CAR_REGISTRATION', 'T', None),
        ('CAR_LOAN', 'T', 'BILL'), ('TOLL_RECEIPT', 'T', 'EXPENSE'),
        ('PARKING_PERMIT', 'T', 'CAR_REGISTRATION'), ('CAR_INSPECTION', 'T', 'CAR_REGISTRATION'),
    ]),
    ('Shopping', [
        ('SHOPPING_LIST', 'M', 'GROCERY_RECEIPT'), ('PRICE_COMPARE', 'M', 'EXPENSE'),
        ('GIFT_IDEA', 'M', 'EXPENSE'), ('COUPON', 'M', 'GROCERY_RECEIPT'),
        ('RETURN_POLICY', 'M', 'GROCERY_RECEIPT'), ('ORDER_CONFIRMATION', 'M', 'GROCERY_RECEIPT'),
        ('SHIPPING_TRACKING', 'M', 'GROCERY_RECEIPT'), ('LOYALTY_CARD', 'M', 'GROCERY_RECEIPT'),
        ('GIFT_RECEIPT', 'M', 'GROCERY_RECEIPT'), ('WISHLIST', 'M', 'WATCHLIST'),
    ]),
    ('Media', [
        ('WATCHLIST', 'E', None), ('ALREADY_WATCHED', 'E', None),
        ('READING_LIST', 'E', 'ALREADY_READ'), ('ALREADY_READ', 'E', None),
        ('MUSIC_PLAYLIST', 'E', 'WATCHLIST'), ('GAME_WISHLIST', 'E', 'WATCHLIST'),
        ('RECIPE', 'E', 'WATCHLIST'), ('PODCAST', 'E', 'WATCHLIST'),
        ('AUDIOBOOK', 'E', 'ALREADY_READ'), ('EBOOK', 'E', 'ALREADY_READ'),
        ('ONLINE_COURSE', 'E', 'WATCHLIST'), ('CONCERT_TICKET', 'E', 'WATCHLIST'),
        ('MUSEUM_TICKET', 'E', 'WATCHLIST'), ('THEATER_TICKET', 'E', 'WATCHLIST'),
    ]),
    ('Productivity', [
        ('NOTE', 'B', None), ('REMINDER', 'B', 'NOTE'),
        ('MEETING_NOTES', 'B', 'NOTE'), ('STUDY_MATERIAL', 'B', 'NOTE'),
        ('BOOKMARK', 'B', 'NOTE'), ('TODO_LIST', 'B', 'NOTE'),
        ('HABIT_TRACKER', 'B', 'NOTE'), ('GOAL', 'B', 'NOTE'),
        ('PROJECT_PLAN', 'B', 'NOTE'), ('JOURNAL', 'B', 'NOTE'),
        ('GRATITUDE_LOG', 'B', 'NOTE'),
    ]),
    ('Services', [
        ('SERVICE_CONTRACT', 'M', None), ('APPOINTMENT', 'M', 'SERVICE_CONTRACT'),
        ('QUOTE', 'M', 'SERVICE_CONTRACT'), ('PHONE_PLAN', 'M', 'SUBSCRIPTION'),
        ('INTERNET_PLAN', 'M', 'SUBSCRIPTION'), ('GYM_MEMBERSHIP', 'M', 'SUBSCRIPTION'),
        ('STREAMING_SERVICE', 'M', 'SUBSCRIPTION'), ('CLOUD_STORAGE', 'M', 'SUBSCRIPTION'),
        ('CLEANING_SERVICE', 'M', 'SERVICE_CONTRACT'),
    ]),
    ('Scam guard', [
        ('SUSPICIOUS_MESSAGE', 'B', None), ('PHISHING_REPORT', 'B', 'SUSPICIOUS_MESSAGE'),
        ('FRAUD_RECORD', 'B', 'SUSPICIOUS_MESSAGE'), ('FAKE_INVOICE', 'B', 'SUSPICIOUS_MESSAGE'),
        ('FAKE_CHECK', 'B', 'SUSPICIOUS_MESSAGE'), ('ADVANCE_FEE_FRAUD', 'B', 'SUSPICIOUS_MESSAGE'),
        ('LOTTERY_SCAM', 'B', 'SUSPICIOUS_MESSAGE'), ('TECH_SUPPORT_SCAM', 'B', 'SUSPICIOUS_MESSAGE'),
        ('ROMANCE_SCAM', 'B', 'SUSPICIOUS_MESSAGE'),
    ]),
]

def snake(i):
    return i.lower()

lines = []
lines.append('package com.vaultbrain.feature.capture')
lines.append('')
lines.append('import com.vaultbrain.core.common.model.ExperienceId')
lines.append('import com.vaultbrain.core.common.model.LensId')
lines.append('import com.vaultbrain.core.common.model.PersonalExperience')
lines.append('')
lines.append('/**')
lines.append(' * Canonical definitions for every personal experience available in the capture flow.')
lines.append(' *')
lines.append(' * Two-tier taxonomy:')
lines.append(' * - Primary experiences ([PersonalExperience.parentId] == null) are the high-frequency,')
lines.append(' *   parser-backed types shown as the main options in the experience picker.')
lines.append(' * - Sub-experiences point to their closest primary via [PersonalExperience.parentId].')
lines.append(' *   They keep full keyword scoring and parser support; when one is detected with high')
lines.append(' *   confidence its specific id is stored on the item, while the picker presents it')
lines.append(' *   through its primary parent.')
lines.append(' */')
lines.append('object ExperienceDefinitions {')
lines.append('')
lines.append('    private val experiencesById: Map<String, PersonalExperience> = buildMap {')
for section, entries in SPEC:
    lines.append('        // %s' % section)
    for eid, lens, parent in entries:
        s = snake(eid)
        call = 'expSub(R.string.experience_%s, R.string.experience_%s_hint, %s, ExperienceId.%s)' % (s, s, L[lens], parent) if parent else \
               'exp(R.string.experience_%s, R.string.experience_%s_hint, %s)' % (s, s, L[lens])
        lines.append('        put(ExperienceId.%s, %s)' % (eid, call))
    lines.append('')
lines.append('        // Generic fallback - presented in the picker as "Just save".')
lines.append('        // It has no lens: items saved this way are "General" (primaryLensId stays null).')
lines.append('        put(')
lines.append('            ExperienceId.GENERIC,')
lines.append('            PersonalExperience(')
lines.append('                id = ExperienceId.GENERIC,')
lines.append('                lensId = null,')
lines.append('                titleRes = R.string.feature_capture_experience_just_save,')
lines.append('                hintRes = R.string.feature_capture_experience_just_save_hint')
lines.append('            )')
lines.append('        )')
lines.append('    }')
lines.append('')
lines.append('    /** Canonical picker order: the five lenses, each at most once. */')
lines.append('    private val lensOrder: List<String> = listOf(')
lines.append('        LensId.MONEY,')
lines.append('        LensId.HEALTH,')
lines.append('        LensId.TRAVEL,')
lines.append('        LensId.BUREAUCRACY,')
lines.append('        LensId.MEDIA')
lines.append('    )')
lines.append('')
lines.append('    fun allExperiences(): List<PersonalExperience> = experiencesById.values.toList()')
lines.append('')
lines.append('    /** Primary experiences only (what the picker lists as main options). */')
lines.append('    fun primaryExperiences(): List<PersonalExperience> =')
lines.append('        experiencesById.values.filter { it.parentId == null && it.id != ExperienceId.GENERIC }')
lines.append('')
lines.append('    fun experiencesForLens(lensId: String): List<PersonalExperience> =')
lines.append('        experiencesById.values.filter { it.lensId == lensId }')
lines.append('')
lines.append('    fun experienceById(id: String?): PersonalExperience? = id?.let { experiencesById[it] }')
lines.append('')
lines.append('    /**')
lines.append('     * Resolves an experience to its primary: primaries resolve to themselves,')
lines.append('     * sub-experiences to their parent (parents are always primary).')
lines.append('     */')
lines.append('    fun resolvePrimary(id: String?): PersonalExperience? {')
lines.append('        val experience = experienceById(id) ?: return null')
lines.append('        return experience.parentId?.let { experiencesById[it] } ?: experience')
lines.append('    }')
lines.append('')
lines.append('    /**')
lines.append('     * Lens that should own an item saved with [id]: the experience lens, walking')
lines.append('     * up to the primary parent when the experience itself has none.')
lines.append('     */')
lines.append('    fun effectiveLensId(id: String?): String? {')
lines.append('        val experience = experienceById(id) ?: return null')
lines.append('        return experience.lensId ?: experience.parentId?.let { experiencesById[it]?.lensId }')
lines.append('    }')
lines.append('')
lines.append('    /** Lens groups for the picker: only primary experiences are listed. */')
lines.append('    fun lensesWithExperiences(): List<Pair<String, List<PersonalExperience>>> =')
lines.append('        lensOrder.map { lensId ->')
lines.append('            lensId to experiencesForLens(lensId)')
lines.append('                .filter { it.parentId == null && it.id != ExperienceId.GENERIC }')
lines.append('        }.filter { (_, experiences) -> experiences.isNotEmpty() }')
lines.append('')
lines.append('    private fun exp(titleRes: Int, hintRes: Int, lensId: String): PersonalExperience {')
lines.append('        // Derive the experience id from the title resource name so definitions stay in sync.')
lines.append('        val id = titleNameForResource(titleRes)')
lines.append('        return PersonalExperience(id = id, lensId = lensId, titleRes = titleRes, hintRes = hintRes)')
lines.append('    }')
lines.append('')
lines.append('    private fun expSub(titleRes: Int, hintRes: Int, lensId: String, parentId: String): PersonalExperience {')
lines.append('        val id = titleNameForResource(titleRes)')
lines.append('        return PersonalExperience(id = id, lensId = lensId, titleRes = titleRes, hintRes = hintRes, parentId = parentId)')
lines.append('    }')
lines.append('')
lines.append('    /**')
lines.append('     * Returns the canonical [ExperienceId] value that matches the `experience_*` string name.')
lines.append('     *')
lines.append('     * This keeps the map above visually tied to the title string; if a resource is renamed the')
lines.append('     * init block on [PersonalExperience] will catch a mismatch at object construction time.')
lines.append('     */')
lines.append('    private fun titleNameForResource(titleRes: Int): String {')
lines.append('        return when (titleRes) {')
for section, entries in SPEC:
    for eid, lens, parent in entries:
        s = snake(eid)
        lines.append('            R.string.experience_%s -> ExperienceId.%s' % (s, eid))
lines.append('')
lines.append('            R.string.feature_capture_experience_just_save -> ExperienceId.GENERIC')
lines.append('            else -> throw IllegalArgumentException("Unknown experience title resource: $titleRes")')
lines.append('        }')
lines.append('    }')
lines.append('}')
lines.append('')

out = '\n'.join(lines)
p = 'feature/capture/src/main/java/com/vaultbrain/feature/capture/ExperienceDefinitions.kt'
open(p, 'w', encoding='utf-8', newline='\n').write(out)
primaries = sum(1 for _, es in SPEC for _, _, par in es if par is None)
total = sum(len(es) for _, es in SPEC)
print('total:', total, '+GENERIC | primaries:', primaries, '| subs:', total - primaries)
