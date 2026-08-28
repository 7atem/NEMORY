package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.VaultItem

/** Extracts gift idea fields. */
class GiftIdeaParser : ExperienceParser {
    override val experienceId = ExperienceId.GIFT_IDEA

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("gift", ignoreCase = true) ||
            text.contains("present", ignoreCase = true) ||
            text.contains("birthday", ignoreCase = true) && text.contains("idea", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "gift_idea")
            extractRecipient(text)?.let { put("recipient", it) }
            extractOccasion(text)?.let { put("occasion", it) }
            extractAmount(text, text.lines())?.let { put("budget", it) }
        }
    }
}

// Shopping helpers

private val recipientRegex = """(?i)(?:for|recipient|to)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+)?)""".toRegex()
private val occasionRegex = """(?i)(?:birthday|wedding|anniversary|christmas|holiday|graduation|mother's day|father's day|valentine)""".toRegex()

internal fun extractRecipient(text: String): String? {
    return recipientRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractOccasion(text: String): String? {
    return occasionRegex.find(text)?.value?.lowercase()?.replaceFirstChar { it.titlecase() }
}


// Extended shopping parsers (orders, shipping, loyalty, gifting).

/** Extracts order confirmation fields (total, order number, store). */
class OrderConfirmationParser : KeywordDocumentParser(
    ExperienceId.ORDER_CONFIRMATION,
    typeName = "order_confirmation",
    keywords = listOf("order confirmation", "order confirmed", "order number", "تأكيد الطلب", "your order"),
    amountKey = "total",
    dateKey = "order_date",
    providerKey = "store",
    withReference = true
)

/** Extracts shipping tracking fields (tracking number, carrier, ETA). */
class ShippingTrackingParser : KeywordDocumentParser(
    ExperienceId.SHIPPING_TRACKING,
    typeName = "shipping_tracking",
    keywords = listOf("tracking number", "shipment", "رقم التتبع", "شحنة", "in transit"),
    dateKey = "estimated_delivery",
    providerKey = "carrier",
    withReference = true
)

/** Extracts loyalty card fields (program name, expiry). */
class LoyaltyCardParser : KeywordDocumentParser(
    ExperienceId.LOYALTY_CARD,
    typeName = "loyalty_card",
    keywords = listOf("loyalty", "reward points", "points balance", "بطاقة ولاء", "نقاط"),
    dateKey = "expiry_date",
    providerKey = "program",
    withReference = true
)

/** Extracts gift receipt fields (total, date, store). */
class GiftReceiptParser : KeywordDocumentParser(
    ExperienceId.GIFT_RECEIPT,
    typeName = "gift_receipt",
    keywords = listOf("gift receipt", "إيصال هدية", "gift slip"),
    amountKey = "total",
    dateKey = "purchase_date",
    providerKey = "store"
)

/** Matches wishlists; extracts store or platform if present. */
class WishlistParser : KeywordDocumentParser(
    ExperienceId.WISHLIST,
    typeName = "wishlist",
    keywords = listOf("wishlist", "wish list", "قائمة الأمنيات", "save for later", "want to buy"),
    providerKey = "store"
)
