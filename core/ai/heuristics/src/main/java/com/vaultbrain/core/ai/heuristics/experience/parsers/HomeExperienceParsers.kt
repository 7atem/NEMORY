package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.shared.model.ExperienceId
import com.vaultbrain.shared.model.VaultItem

/** Extracts warranty-specific fields. */
class WarrantyParser : ExperienceParser {
    override val experienceId = ExperienceId.WARRANTY

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("warranty", ignoreCase = true) ||
            text.contains("guarantee", ignoreCase = true) ||
            text.contains("serial number", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "warranty")
            extractProductName(text)?.let { put("product", it) }
            extractSerialNumber(text)?.let { put("serial_number", it) }
            extractDate(text)?.let { put("purchase_date", it) }
            extractWarrantyDuration(text)?.let { put("warranty_duration", it) }
            extractAmount(text, text.lines())?.let { put("purchase_amount", it) }
            extractStoreName(text)?.let { put("store", it) }
        }
    }
}

// Home helpers

private val serialNumberRegex = """(?i)(?:serial[\s#:]*|s/n|serial no)[\s:]*([A-Z0-9\-]{5,20})""".toRegex()
private val warrantyDurationRegex = """(?i)(\d+\s*(?:year|years|month|months|day|days))\s*(?:warranty|guarantee)""".toRegex()
private val productNameRegex = """(?i)(?:product[\s:]*|item[\s:]*|model[\s:]*)([A-Z0-9][A-Za-z0-9\s\-]{2,40})""".toRegex()
private val storeNameRegex = """(?i)(?:sold by|retailer|store|shop)[\s:]*([A-Z][a-zA-Z0-9\s]{2,30})""".toRegex()

internal fun extractSerialNumber(text: String): String? {
    return serialNumberRegex.find(text)?.groups?.get(1)?.value?.uppercase()
}

internal fun extractWarrantyDuration(text: String): String? {
    return warrantyDurationRegex.find(text)?.groups?.get(1)?.value?.lowercase()
}

internal fun extractProductName(text: String): String? {
    return productNameRegex.find(text)?.groups?.get(1)?.value?.trim()
}

internal fun extractStoreName(text: String): String? {
    return storeNameRegex.find(text)?.groups?.get(1)?.value?.trim()
}


// Extended home parsers (property and utilities).

/** Extracts property deed fields (registration date, reference). */
class PropertyDeedParser : KeywordDocumentParser(
    ExperienceId.PROPERTY_DEED,
    typeName = "property_deed",
    keywords = listOf("title deed", "property deed", "صك ملكية", "صك", "land registry"),
    dateKey = "registration_date",
    providerKey = "registry",
    withReference = true
)

/** Extracts mortgage statement fields (balance, due date, lender). */
class MortgageStatementParser : KeywordDocumentParser(
    ExperienceId.MORTGAGE_STATEMENT,
    typeName = "mortgage_statement",
    keywords = listOf("mortgage", "رهن عقاري", "قرض عقاري", "home loan"),
    amountKey = "balance",
    dateKey = "due_date",
    providerKey = "lender",
    withReference = true
)

/** Extracts home insurance policy fields (premium, expiry, insurer). */
class HomeInsurancePolicyParser : KeywordDocumentParser(
    ExperienceId.HOME_INSURANCE_POLICY,
    typeName = "home_insurance_policy",
    keywords = listOf("home insurance", "homeowners insurance", "تأمين المنزل", "تأمين منزلي"),
    amountKey = "premium",
    dateKey = "expiry_date",
    providerKey = "insurer",
    withReference = true
)

/** Extracts appliance registration fields (brand, purchase date, serial). */
class ApplianceRegistrationParser : KeywordDocumentParser(
    ExperienceId.APPLIANCE_REGISTRATION,
    typeName = "appliance_registration",
    keywords = listOf("product registration", "register your product", "تسجيل المنتج", "تسجيل الجهاز"),
    dateKey = "purchase_date",
    providerKey = "brand",
    withReference = true
)

/** Extracts utility setup fields (provider, activation date, request number). */
class UtilitySetupParser : KeywordDocumentParser(
    ExperienceId.UTILITY_SETUP,
    typeName = "utility_setup",
    keywords = listOf("new connection", "توصيل جديد", "service activation", "تفعيل الخدمة", "installation date"),
    dateKey = "activation_date",
    providerKey = "provider",
    withReference = true
)
