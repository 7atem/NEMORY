package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.VaultItem

/** Extracts passport-specific fields. */
class PassportParser : ExperienceParser {
    override val experienceId = ExperienceId.PASSPORT

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("passport", ignoreCase = true) ||
            text.contains("travel document", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "passport")
            extractPassportNumber(text)?.let { put("passport_number", it) }
            extractNationality(text)?.let { put("nationality", it) }
            extractDob(text)?.let { put("dob", it) }
            extractExpiryDate(text)?.let { put("expiry_date", it) }
            extractDocumentType(text)?.let { put("document_type", it) }
            extractMrzInfo(text)?.let { put("mrz", it) }
        }
    }
}

/** Extracts business card fields. */
class BusinessCardParser : ExperienceParser {
    override val experienceId = ExperienceId.BUSINESS_CARD

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("@", ignoreCase = true) &&
            (text.contains("tel", ignoreCase = true) || text.contains("phone", ignoreCase = true) || text.contains("mobile", ignoreCase = true)) ||
            text.contains("business card", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "business_card")
            extractName(text)?.let { put("name", it) }
            extractJobTitle(text)?.let { put("job_title", it) }
            extractCompany(text)?.let { put("company", it) }
            extractEmail(text)?.let { put("email", it) }
            extractPhone(text)?.let { put("phone", it) }
            extractWebsite(text)?.let { put("website", it) }
        }
    }
}

// Bureaucracy helpers

private val passportNumberRegex = """(?i)(?:passport[\s#:]*|pp no|pp#)[\s:]*([A-Z0-9]{6,12})""".toRegex()
private val nationalityRegex = """(?i)(?:nationality|citizen|country)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+)?)""".toRegex()
private val mrzRegex = """[A-Z0-9<]{36,44}""".toRegex()
private val emailRegex = """[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""".toRegex()
private val phoneRegex = """[\+]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,4}[-\s.]?[0-9]{1,4}[-\s.]?[0-9]{1,9}""".toRegex()
private val websiteRegex = """(?i)(?:www\.|https?://)[a-zA-Z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""".toRegex()
private val jobTitleRegex = """(?i)(?:title|position)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+){0,3})""".toRegex()
private val companyRegex = """(?i)(?:company|firm|org)[\s:]*([A-Z][a-zA-Z0-9\s]{2,30})""".toRegex()
private val personNameRegex = """[A-Z][a-zA-Z]+\s+[A-Z][a-zA-Z]+""".toRegex()
private val dobRegex = """(?i)(?:dob|date of birth|birth|born)[\s:]*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2}|\d{1,2}\s+[a-zA-Z]{3}\s+\d{2,4})""".toRegex()
private val expiryDateRegex = """(?i)(?:expiry|expires|valid until|date of expiry)[\s:]*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2}|\d{1,2}\s+[a-zA-Z]{3}\s+\d{2,4})""".toRegex()
private val documentTypeRegex = """(?i)(passport|id card|driving license|national id|residence permit)""".toRegex()

internal fun extractDob(text: String): String? {
    return dobRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractExpiryDate(text: String): String? {
    return expiryDateRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractDocumentType(text: String): String? {
    return documentTypeRegex.find(text)?.groups?.get(1)?.value?.lowercase()?.replaceFirstChar { it.titlecase() }
}

internal fun extractPassportNumber(text: String): String? {
    return passportNumberRegex.find(text)?.groups?.get(1)?.value?.uppercase()
}

internal fun extractNationality(text: String): String? {
    return nationalityRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractMrzInfo(text: String): String? {
    return mrzRegex.find(text)?.value
}

internal fun extractEmail(text: String): String? {
    return emailRegex.find(text)?.value?.lowercase()
}

internal fun extractPhone(text: String): String? {
    return phoneRegex.find(text)?.value
}

internal fun extractWebsite(text: String): String? {
    return websiteRegex.find(text)?.value?.lowercase()
}

internal fun extractJobTitle(text: String): String? {
    return jobTitleRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractCompany(text: String): String? {
    return companyRegex.find(text)?.groups?.get(1)?.value?.trim()
}

internal fun extractName(text: String): String? {
    // First line that looks like a person name (two capitalized words, no obvious labels).
    return text.lines().map { it.trim() }
        .firstOrNull { line ->
            line.matches(personNameRegex) &&
                !line.contains("@", ignoreCase = true) &&
                !line.contains("www", ignoreCase = true) &&
                !line.contains("company", ignoreCase = true)
        }
}


// Extended bureaucracy parsers (certificates and licenses).

/** Extracts birth certificate fields (date of birth, registry reference). */
class BirthCertificateParser : KeywordDocumentParser(
    ExperienceId.BIRTH_CERTIFICATE,
    typeName = "birth_certificate",
    keywords = listOf("birth certificate", "certificate of birth", "شهادة ميلاد", "قيد ميلاد"),
    dateKey = "date_of_birth",
    providerKey = "issuing_authority",
    withReference = true
)

/** Extracts marriage certificate fields (marriage date, reference). */
class MarriageCertificateParser : KeywordDocumentParser(
    ExperienceId.MARRIAGE_CERTIFICATE,
    typeName = "marriage_certificate",
    keywords = listOf("marriage certificate", "marriage contract", "عقد زواج", "قسيمة زواج"),
    dateKey = "marriage_date",
    providerKey = "issuing_authority",
    withReference = true
)

/** Extracts notarized document fields (notarization date, reference). */
class NotarizedDocumentParser : KeywordDocumentParser(
    ExperienceId.NOTARIZED_DOCUMENT,
    typeName = "notarized_document",
    keywords = listOf("notary", "notarized", "كاتب عدل", "توثيق", "power of attorney", "affidavit"),
    dateKey = "notarization_date",
    providerKey = "notary",
    withReference = true
)

/** Extracts tax residency certificate fields (issue/validity, authority). */
class TaxResidencyCertificateParser : KeywordDocumentParser(
    ExperienceId.TAX_RESIDENCY_CERTIFICATE,
    typeName = "tax_residency_certificate",
    keywords = listOf("tax residency", "tax residence", "إقامة ضريبية", "شهادة الإقامة الضريبية"),
    dateKey = "expiry_date",
    providerKey = "tax_authority",
    withReference = true
)

/** Extracts professional license fields (expiry, issuing body, license number). */
class ProfessionalLicenseParser : KeywordDocumentParser(
    ExperienceId.PROFESSIONAL_LICENSE,
    typeName = "professional_license",
    keywords = listOf("professional license", "practice license", "رخصة مزاولة", "ترخيص مهني", "syndicate", "نقابة"),
    dateKey = "expiry_date",
    providerKey = "issuing_body",
    withReference = true
)

