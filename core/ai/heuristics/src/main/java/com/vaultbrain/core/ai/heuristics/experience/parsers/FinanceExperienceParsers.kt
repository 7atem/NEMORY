package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.shared.model.ExperienceId
import com.vaultbrain.shared.model.VaultItem

/** Extracts expense-specific fields from receipts and invoices. */
class ExpenseParser : ExperienceParser {
    override val experienceId = ExperienceId.EXPENSE

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("total", ignoreCase = true) ||
            text.contains("amount", ignoreCase = true) ||
            text.contains("receipt", ignoreCase = true) ||
            text.contains("paid", ignoreCase = true) ||
            text.contains("payment", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        val lines = text.lines()
        return buildMap {
            put("experience_type", "expense")
            extractAmount(text, lines)?.let { put("total", it) }
            extractTax(text, lines)?.let { put("tax", it) }
            extractMerchant(lines)?.let { put("merchant", it) }
            extractDate(text)?.let { put("date", it) }
            extractCurrency(text)?.let { put("currency", it) }
            extractIban(text)?.let { put("iban", it) }
            extractPaymentMethod(text)?.let { put("payment_method", it) }
            extractTaxId(text)?.let { put("tax_id", it) }
            extractInvoiceNumber(text)?.let { put("invoice_number", it) }
        }
    }
}

/** Extracts subscription-specific fields. */
class SubscriptionParser : ExperienceParser {
    override val experienceId = ExperienceId.SUBSCRIPTION

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("subscription", ignoreCase = true) ||
            text.contains("renew", ignoreCase = true) ||
            text.contains("monthly", ignoreCase = true) ||
            text.contains("billing", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "subscription")
            extractAmount(text, text.lines())?.let { put("amount", it) }
            extractDate(text)?.let { put("next_billing", it) }
            extractProvider(text)?.let { put("provider", it) }
            extractCurrency(text)?.let { put("currency", it) }
            extractIban(text)?.let { put("iban", it) }
            detectPeriod(text)?.let { put("period", it) }
        }
    }
}

/** Extracts bill-specific fields. */
class BillParser : ExperienceParser {
    override val experienceId = ExperienceId.BILL

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("bill", ignoreCase = true) ||
            text.contains("invoice", ignoreCase = true) ||
            text.contains("due", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "bill")
            extractAmount(text, text.lines())?.let { put("amount_due", it) }
            extractDate(text)?.let { put("due_date", it) }
            extractProvider(text)?.let { put("biller", it) }
            extractCurrency(text)?.let { put("currency", it) }
            extractIban(text)?.let { put("iban", it) }
        }
    }
}

// Shared helpers for finance parsers.

private val amountRegex = """(?i)(?:total|amount|sum|due|paid)[\s:]*[-\s]*([\d.,]+)""".toRegex()
private val standaloneAmountRegex = """(?<![\w])(?:\$|€|£|¥|EGP|SAR|AED|USD|EUR|GBP)?\s*([\d]{1,3}(?:[,\s]?[\d]{3})*(?:\.[\d]+)?|[\d]+(?:\.[\d]+)?)""".toRegex()
private val taxRegex = """(?i)(?:tax|vat|gst)[\s:]*[-\s]*([\d.,]+)""".toRegex()
private val currencyRegex = """(?i)(\$|€|£|¥|USD|EUR|GBP|JPY|EGP|SAR|AED|INR|CNY)""".toRegex()
private val paymentMethodRegex = """(?i)(?:visa|mastercard|amex|cash|card|paypal|apple pay|google pay)""".toRegex()
private val dateRegex = """(?i)\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+\d{1,2},?\s+\d{2,4})\b""".toRegex()
private val taxIdRegex = """(?i)(?:vat\s*no|vat\s*number|gst\s*no|tax\s*id|trn|tin)[\s:]*([A-Z0-9-]{8,20})""".toRegex()
private val invoiceNumberRegex = """(?i)(?:invoice\s*no|inv|invoice\s*number|receipt\s*no)[\s#:]*([A-Z0-9-]{4,15})""".toRegex()
private val ibanRegex = """(?i)(?:iban)[\s:]*([A-Z]{2}[0-9]{2}(?:\s*[0-9a-zA-Z]{4}){3,7})""".toRegex()

internal fun extractTaxId(text: String): String? {
    return taxIdRegex.find(text)?.groups?.get(1)?.value?.replace(" ", "")
}

internal fun extractInvoiceNumber(text: String): String? {
    return invoiceNumberRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractIban(text: String): String? {
    return ibanRegex.find(text)?.groups?.get(1)?.value?.replace(" ", "")?.uppercase()
}

private val providerBlacklist = setOf("receipt", "invoice", "bill", "total", "amount", "date", "tax")

internal fun extractAmount(text: String, lines: List<String>): String? {
    amountRegex.find(text)?.groups?.get(1)?.value?.let { return normalizeNumber(it) }
    // Fallback: find the largest standalone amount near the bottom of the receipt.
    val candidates = lines.flatMap { line ->
        standaloneAmountRegex.findAll(line).map { it.groupValues[1] }.toList()
    }.map { normalizeNumber(it) }
        .mapNotNull { it.replace(",", "").toDoubleOrNull() }
    return candidates.maxOrNull()?.toString()
}

internal fun extractTax(text: String, lines: List<String>): String? {
    taxRegex.find(text)?.groups?.get(1)?.value?.let { return normalizeNumber(it) }
    return null
}

internal fun extractMerchant(lines: List<String>): String? {
    return lines.firstOrNull { line ->
        line.isNotBlank() &&
            line.length in 3..40 &&
            providerBlacklist.none { line.trim().equals(it, ignoreCase = true) }
    }?.trim()
}

internal fun extractDate(text: String): String? {
    return dateRegex.find(text)?.value
}

internal fun extractCurrency(text: String): String? {
    return currencyRegex.find(text)?.value?.uppercase()
}

internal fun extractPaymentMethod(text: String): String? {
    return paymentMethodRegex.find(text)?.value?.lowercase()?.replaceFirstChar { it.titlecase() }
}

internal fun extractProvider(text: String): String? {
    val lines = text.lines()
    return lines.firstOrNull { line ->
        line.isNotBlank() &&
            line.length in 3..40 &&
            providerBlacklist.none { line.trim().equals(it, ignoreCase = true) }
    }?.trim()
}

internal fun detectPeriod(text: String): String? {
    return when {
        text.contains("monthly", ignoreCase = true) -> "monthly"
        text.contains("yearly", ignoreCase = true) || text.contains("annual", ignoreCase = true) -> "yearly"
        text.contains("weekly", ignoreCase = true) -> "weekly"
        text.contains("daily", ignoreCase = true) -> "daily"
        else -> null
    }
}

internal fun normalizeNumber(value: String): String {
    return value.replace(" ", "").replace(",", "").trim()
}


// Extended money parsers (banking, loans, household spending).

/** Extracts bank statement fields (balance, period, bank). */
class BankStatementParser : KeywordDocumentParser(
    ExperienceId.BANK_STATEMENT,
    typeName = "bank_statement",
    keywords = listOf("bank statement", "account statement", "statement of account", "كشف حساب", "iban", "closing balance"),
    amountKey = "balance",
    dateKey = "statement_date",
    providerKey = "bank",
    withReference = true
)

/** Extracts credit card statement fields (total due, due date, issuer). */
class CreditCardStatementParser : KeywordDocumentParser(
    ExperienceId.CREDIT_CARD_STATEMENT,
    typeName = "credit_card_statement",
    keywords = listOf("credit card statement", "card statement", "minimum payment", "credit limit", "كشف بطاقة"),
    amountKey = "total_due",
    dateKey = "due_date",
    providerKey = "issuer",
    withReference = true
)

/** Extracts loan payment fields (installment amount, due date, lender). */
class LoanPaymentParser : KeywordDocumentParser(
    ExperienceId.LOAN_PAYMENT,
    typeName = "loan_payment",
    keywords = listOf("loan payment", "loan", "installment", "emi", "قسط", "قرض"),
    amountKey = "installment_amount",
    dateKey = "due_date",
    providerKey = "lender",
    withReference = true
)

/** Matches grocery lists; extracts store name and estimated total if present. */
class GroceryListParser : KeywordDocumentParser(
    ExperienceId.GROCERY_LIST,
    typeName = "grocery_list",
    keywords = listOf("grocery list", "groceries", "قائمة مشتريات", "supermarket", "to buy"),
    amountKey = "estimated_total",
    providerKey = "store"
)

/** Extracts pet expense fields (total, clinic). */
class PetExpenseParser : KeywordDocumentParser(
    ExperienceId.PET_EXPENSE,
    typeName = "pet_expense",
    keywords = listOf("vet", "veterinary", "pet", "طبيب بيطري", "حيوان أليف"),
    amountKey = "total",
    dateKey = "date",
    providerKey = "clinic"
)

/** Extracts tuition fee fields (amount due, due date, institution). */
class TuitionFeeParser : KeywordDocumentParser(
    ExperienceId.TUITION_FEE,
    typeName = "tuition_fee",
    keywords = listOf("tuition", "school fee", "university fee", "رسوم دراسية", "مصروفات"),
    amountKey = "amount_due",
    dateKey = "due_date",
    providerKey = "institution",
    withReference = true
)


