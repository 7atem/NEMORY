package com.vaultbrain.sync.gmail

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracted entity representing a structured email receipt or confirmation.
 */
data class EmailReceipt(
    val subject: String,
    val merchant: String?,
    val orderNumber: String?,
    val trackingNumber: String?,
    val totalAmount: Double?,
    val currency: String?,
    val bodySnippet: String
)

/**
 * Parser for confirmation emails (e-commerce orders, shipments, receipts, flights).
 *
 * Extracts order IDs, courier tracking numbers, merchant names, and monetary totals.
 */
@Singleton
class GmailParser @Inject constructor() {

    private val ORDER_REGEX =
        """(?i)(?:order|invoice|receipt)\s*(?:#|no\.?|id|number)?\s*[:\s]?\s*([A-Z0-9-]{5,20})\b""".toRegex()

    private val TRACKING_REGEX =
        """(?i)(?:tracking|shipment|waybill|awb)\s*(?:#|no\.?|id|number)?\s*[:\s]?\s*([A-Z0-9]{8,30})\b""".toRegex()

    private val TOTAL_AMOUNT_REGEX =
        """(?i)(?:total|amount paid|grand total)\s*[:\s]?\s*(?:([$€£¥]|USD|EUR|GBP|EGP|SAR|AED|ج\.م|ر\.س)\s*)?([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2})(?:\s*(USD|EUR|GBP|EGP|SAR|AED|ج\.م|ر\.س))?""".toRegex()

    private val KNOWN_MERCHANTS = listOf(
        "Amazon", "Noon", "Carrefour", "Apple", "Google", "Uber", "Careem",
        "Talabat", "IKEA", "Zara", "eBay", "AliExpress", "Booking.com", "Airbnb"
    )

    /**
     * Parses a single email body text into a structured [EmailReceipt].
     */
    fun parseEmailContent(subject: String, body: String): EmailReceipt {
        val orderNumber = ORDER_REGEX.find(subject)?.groupValues?.get(1)
            ?: ORDER_REGEX.find(body)?.groupValues?.get(1)

        val trackingNumber = TRACKING_REGEX.find(subject)?.groupValues?.get(1)
            ?: TRACKING_REGEX.find(body)?.groupValues?.get(1)

        val totalMatch = TOTAL_AMOUNT_REGEX.find(body)
        val currency = totalMatch?.let { match ->
            match.groupValues[1].takeIf { it.isNotBlank() }
                ?: match.groupValues[3].takeIf { it.isNotBlank() }
        }
        val amount = totalMatch?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()

        val fullText = "$subject\n$body"
        val merchant = KNOWN_MERCHANTS.firstOrNull { fullText.contains(it, ignoreCase = true) }

        return EmailReceipt(
            subject = subject,
            merchant = merchant,
            orderNumber = orderNumber,
            trackingNumber = trackingNumber,
            totalAmount = amount,
            currency = currency,
            bodySnippet = body.take(400)
        )
    }

}
