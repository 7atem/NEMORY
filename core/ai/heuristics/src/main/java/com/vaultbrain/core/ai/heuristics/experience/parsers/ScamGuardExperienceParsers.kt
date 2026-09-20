package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.shared.model.ExperienceId
import com.vaultbrain.shared.model.VaultItem

/** Extracts suspicious message / phishing fields. */
class SuspiciousMessageParser : ExperienceParser {
    override val experienceId = ExperienceId.SUSPICIOUS_MESSAGE

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("urgent", ignoreCase = true) ||
            text.contains("suspicious", ignoreCase = true) ||
            text.contains("verify your account", ignoreCase = true) ||
            text.contains("click here", ignoreCase = true) ||
            text.contains("limited time", ignoreCase = true) && text.contains("account", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "suspicious_message")
            extractSender(text)?.let { put("sender", it) }
            extractLinks(text).takeIf { it.isNotEmpty() }?.let { put("links", it.joinToString("\n")) }
            extractPhone(text)?.let { put("contact_phone", it) }
            put("risk_score", estimateRiskScore(text).toString())
        }
    }
}

// Scam guard helpers

private val senderRegex = """(?i)(?:from|sender)[\s:]*([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}|[A-Z][a-zA-Z0-9\s]{2,30})""".toRegex()
private val linkRegex = """(?i)(?:https?://|www\.)[a-zA-Z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""".toRegex()
private val riskKeywords = setOf(
    "urgent", "verify your account", "suspended", "click here", "limited time",
    "free gift", "winner", "lottery", "claim now", "password", "ssn", "social security"
)

internal fun extractSender(text: String): String? {
    return senderRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractLinks(text: String): List<String> {
    return linkRegex.findAll(text).map { it.value }.take(5).toList()
}

internal fun estimateRiskScore(text: String): Int {
    val lower = text.lowercase()
    val hits = riskKeywords.count { lower.contains(it) }
    return (hits * 20).coerceAtMost(100)
}


// Extended scam guard parsers (common fraud patterns).

/** Detects fake invoices / suspicious payment demands. */
class FakeInvoiceParser : KeywordDocumentParser(
    ExperienceId.FAKE_INVOICE,
    typeName = "fake_invoice",
    keywords = listOf("pay immediately", "wire transfer", "overdue invoice", "final notice", "فاتورة مزيفة", "ادفع فوراً"),
    amountKey = "amount_demanded",
    dateKey = "deadline",
    providerKey = "sender",
    withReference = true,
    withRiskScore = true
)

/** Detects fake check / overpayment scams. */
class FakeCheckParser : KeywordDocumentParser(
    ExperienceId.FAKE_CHECK,
    typeName = "fake_check",
    keywords = listOf("cashier's check", "certified check", "deposit this check", "overpayment", "شيك مزيف", "mystery shopper"),
    amountKey = "check_amount",
    providerKey = "sender",
    withReference = true,
    withRiskScore = true
)

/** Detects advance-fee / inheritance fraud. */
class AdvanceFeeFraudParser : KeywordDocumentParser(
    ExperienceId.ADVANCE_FEE_FRAUD,
    typeName = "advance_fee_fraud",
    keywords = listOf("advance fee", "processing fee", "inheritance", "release your funds", "ميراث", "رسوم مقدمة", "next of kin"),
    amountKey = "promised_amount",
    providerKey = "sender",
    withRiskScore = true
)

/** Detects lottery / prize scams. */
class LotteryScamParser : KeywordDocumentParser(
    ExperienceId.LOTTERY_SCAM,
    typeName = "lottery_scam",
    keywords = listOf("lottery", "you have won", "claim your prize", "jackpot", "يانصيب", "لقد فزت", "sweepstakes"),
    amountKey = "prize_amount",
    providerKey = "sender",
    withReference = true,
    withRiskScore = true
)

/** Detects fake tech support scams. */
class TechSupportScamParser : KeywordDocumentParser(
    ExperienceId.TECH_SUPPORT_SCAM,
    typeName = "tech_support_scam",
    keywords = listOf("tech support", "virus detected", "your computer", "microsoft support", "الدعم الفني", "تم اكتشاف فيروس", "remote access"),
    providerKey = "sender",
    withRiskScore = true
)

/** Detects romance scams. */
class RomanceScamParser : KeywordDocumentParser(
    ExperienceId.ROMANCE_SCAM,
    typeName = "romance_scam",
    keywords = listOf("my dear", "soulmate", "send money", "حبيبي", "أرسل المال", "plane ticket", "widow", "oil rig"),
    amountKey = "amount_requested",
    providerKey = "sender",
    withRiskScore = true
)
