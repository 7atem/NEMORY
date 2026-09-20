package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.shared.model.ExperienceId
import com.vaultbrain.shared.model.VaultItem

/** Extracts prescription-specific fields. */
class PrescriptionParser : ExperienceParser {
    override val experienceId = ExperienceId.PRESCRIPTION

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("prescription", ignoreCase = true) ||
            text.contains("rx", ignoreCase = true) ||
            text.contains("medication", ignoreCase = true) ||
            text.contains("dosage", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "prescription")
            extractMedicationName(text)?.let { put("medication", it) }
            extractDiagnosis(text)?.let { put("diagnosis", it) }
            extractDosage(text)?.let { put("dosage", it) }
            extractFrequency(text)?.let { put("frequency", it) }
            extractDate(text)?.let { put("prescription_date", it) }
            extractDoctor(text)?.let { put("doctor", it) }
            extractPharmacy(text)?.let { put("pharmacy", it) }
        }
    }
}

/** Extracts medication schedule fields. */
class MedicationScheduleParser : ExperienceParser {
    override val experienceId = ExperienceId.MEDICATION_SCHEDULE

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("medication", ignoreCase = true) ||
            text.contains("take", ignoreCase = true) && text.contains("times", ignoreCase = true) ||
            text.contains("dosage", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "medication_schedule")
            extractMedicationName(text)?.let { put("medication", it) }
            extractDiagnosis(text)?.let { put("diagnosis", it) }
            extractDosage(text)?.let { put("dosage", it) }
            extractFrequency(text)?.let { put("schedule", it) }
            extractStartDate(text)?.let { put("start_date", it) }
            extractEndDate(text)?.let { put("end_date", it) }
        }
    }
}

/** Extracts lab result-specific fields. */
class LabResultParser : ExperienceParser {
    override val experienceId = ExperienceId.LAB_RESULT

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("lab", ignoreCase = true) ||
            text.contains("laboratory", ignoreCase = true) ||
            text.contains("test result", ignoreCase = true) ||
            text.contains("blood", ignoreCase = true) && text.contains("result", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "lab_result")
            extractLabType(text)?.let { put("test_type", it) }
            extractBloodType(text)?.let { put("blood_type", it) }
            extractDate(text)?.let { put("test_date", it) }
            extractLabValues(text).takeIf { it.isNotEmpty() }?.let { put("values", it.joinToString(" | ")) }
        }
    }
}

/** Extracts health insurance claim fields. */
class HealthInsuranceClaimParser : ExperienceParser {
    override val experienceId = ExperienceId.HEALTH_INSURANCE_CLAIM

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("claim", ignoreCase = true) &&
            (text.contains("insurance", ignoreCase = true) || text.contains("health", ignoreCase = true)) ||
            text.contains("medical claim", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "health_insurance_claim")
            extractClaimNumber(text)?.let { put("claim_number", it) }
            extractProvider(text)?.let { put("provider", it) }
            extractAmount(text, text.lines())?.let { put("claim_amount", it) }
            extractDate(text)?.let { put("claim_date", it) }
        }
    }
}

// Health helpers

private val medicationRegex = """(?i)(?:medication|medicine|drug|rx)[\s:]*([A-Z][a-z]+(?:\s[A-Z][a-z]+){0,2})""".toRegex()
private val dosageRegex = """(?i)(\d+\s*(?:mg|mcg|g|ml|iu|tablet|tab|capsule|cap))""".toRegex()
private val frequencyRegex = """(?i)(once|twice|three times|four times|daily|weekly|monthly|every\s+\d+\s+\w+)""".toRegex()
private val doctorRegex = """(?i)(?:dr|doctor|physician)[.\s]+([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+)?)""".toRegex()
private val pharmacyRegex = """(?i)(?:pharmacy|dispensed by|prepared by)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+)?)""".toRegex()
private val labValueRegex = """(?i)([a-z\s()]+)\s+[:\s]+\s*(\d+\.?\d*)\s*([a-z/%0-9]+)""".toRegex()
private val labTypeKeywords = setOf("blood", "urine", "glucose", "cholesterol", "cbc", "lipid", "thyroid", "liver", "kidney")
private val claimNumberRegex = """(?i)(?:claim[\s#:]*|claim number|claim no)[\s:]*([A-Z0-9]{5,12})""".toRegex()
private val startDateRegex = """(?i)(?:start|from|begin)[\s:]*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})""".toRegex()
private val endDateRegex = """(?i)(?:end|until|to)[\s:]*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})""".toRegex()
private val diagnosisRegex = """(?i)(?:diagnosis|dx|indication|assessment)[\s:]*([A-Za-z0-9\s-]+)(?:\r?\n|$)""".toRegex()
private val bloodTypeRegex = """(?i)(?:blood type|blood group|abo/rh)[\s:]*([A-Z]{1,2}[\+\-])""".toRegex()

internal fun extractDiagnosis(text: String): String? {
    return diagnosisRegex.find(text)?.groups?.get(1)?.value?.trim()
}

internal fun extractBloodType(text: String): String? {
    return bloodTypeRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractMedicationName(text: String): String? {
    return medicationRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractDosage(text: String): String? {
    return dosageRegex.find(text)?.value
}

internal fun extractFrequency(text: String): String? {
    return frequencyRegex.find(text)?.value?.lowercase()
}

internal fun extractDoctor(text: String): String? {
    return doctorRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractPharmacy(text: String): String? {
    return pharmacyRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractLabType(text: String): String? {
    return labTypeKeywords.firstOrNull { text.contains(it, ignoreCase = true) }
}

internal fun extractLabValues(text: String): List<String> {
    return labValueRegex.findAll(text).map { it.value.trim() }.take(10).toList()
}

internal fun extractClaimNumber(text: String): String? {
    return claimNumberRegex.find(text)?.groups?.get(1)?.value?.uppercase()
}

internal fun extractStartDate(text: String): String? {
    return startDateRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractEndDate(text: String): String? {
    return endDateRegex.find(text)?.groups?.get(1)?.value
}


// Extended health parsers (dental, optical, wellness).

/** Extracts dental visit fields (visit date, dentist, cost). */
class DentalVisitParser : KeywordDocumentParser(
    ExperienceId.DENTAL_VISIT,
    typeName = "dental_visit",
    keywords = listOf("dental", "dentist", "tooth", "أسنان", "طبيب أسنان"),
    amountKey = "total",
    dateKey = "visit_date",
    providerKey = "dentist"
)

/** Extracts optical prescription fields (exam date, optician). */
class OpticalPrescriptionParser : KeywordDocumentParser(
    ExperienceId.OPTICAL_PRESCRIPTION,
    typeName = "optical_prescription",
    keywords = listOf("optical", "glasses", "sphere", "cylinder", "نظارة", "optician"),
    dateKey = "exam_date",
    providerKey = "optician"
)

/** Matches allergy records; extracts clinic and test date. */
class AllergyRecordParser : KeywordDocumentParser(
    ExperienceId.ALLERGY_RECORD,
    typeName = "allergy_record",
    keywords = listOf("allergy", "allergen", "allergic", "حساسية"),
    dateKey = "test_date",
    providerKey = "clinic"
)

/** Extracts physiotherapy plan fields (start date, therapist). */
class PhysiotherapyPlanParser : KeywordDocumentParser(
    ExperienceId.PHYSIOTHERAPY_PLAN,
    typeName = "physiotherapy_plan",
    keywords = listOf("physiotherapy", "physical therapy", "علاج طبيعي", "rehabilitation"),
    dateKey = "start_date",
    providerKey = "therapist"
)

/** Matches mental health notes; extracts session date and therapist. */
class MentalHealthNoteParser : KeywordDocumentParser(
    ExperienceId.MENTAL_HEALTH_NOTE,
    typeName = "mental_health_note",
    keywords = listOf("mental health", "therapy", "counseling", "psycholog", "صحة نفسية"),
    dateKey = "session_date",
    providerKey = "therapist"
)

/** Extracts blood donation fields (donation date, center). */
class BloodDonationParser : KeywordDocumentParser(
    ExperienceId.BLOOD_DONATION,
    typeName = "blood_donation",
    keywords = listOf("blood donation", "donor", "blood bank", "تبرع بالدم", "بنك الدم"),
    dateKey = "donation_date",
    providerKey = "center",
    withReference = true
)

