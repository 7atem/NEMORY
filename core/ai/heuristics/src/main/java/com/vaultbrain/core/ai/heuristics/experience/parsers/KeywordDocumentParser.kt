package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.shared.model.VaultItem

/**
 * Base implementation for the lightweight parsers added with the extended experience set.
 *
 * Matches when any trigger keyword appears in the OCR text and extracts a small,
 * consistent set of fields (amount, date, provider, currency, reference number)
 * using the shared helpers from [FinanceExperienceParsers].
 */
open class KeywordDocumentParser(
    override val experienceId: String,
    private val typeName: String,
    private val keywords: List<String>,
    private val amountKey: String? = null,
    private val dateKey: String? = null,
    private val providerKey: String? = null,
    private val withReference: Boolean = false,
    private val withRiskScore: Boolean = false
) : ExperienceParser {

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        val lines = text.lines()
        return buildMap {
            put("experience_type", typeName)
            amountKey?.let { key -> extractAmount(text, lines)?.let { put(key, it) } }
            dateKey?.let { key -> extractDate(text)?.let { put(key, it) } }
            providerKey?.let { key -> extractProvider(text)?.let { put(key, it) } }
            extractCurrency(text)?.let { put("currency", it) }
            if (withReference) extractReferenceNumber(text)?.let { put("reference_number", it) }
            if (withRiskScore) put("risk_score", estimateRiskScore(text).toString())
        }
    }
}

// Shared helper for document / reference numbers (order no, tracking no, permit no, ...).

private val referenceNumberRegex =
    """(?i)(?:reference|ref(?:erence)?|order|tracking|permit|certificate|license|licence|document|booking|confirmation|policy|application|registration)\s*(?:no\.?|number|#|num)?[\s:]*([A-Z0-9][A-Z0-9\-]{4,25})""".toRegex()

internal fun extractReferenceNumber(text: String): String? {
    return referenceNumberRegex.find(text)?.groupValues?.get(1)
}
