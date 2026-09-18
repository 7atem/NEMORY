package com.vaultbrain.core.common.model

/** Suggested next steps are always presented for confirmation; none execute automatically. */
enum class ProactiveAction(val code: String) {
    COPY_TOTAL("COPY_TOTAL"),
    ADD_CONTACT("ADD_CONTACT"),
    FIND_MANUAL("FIND_MANUAL"),
    ADD_TO_CALENDAR("ADD_TO_CALENDAR"),
    REFILL_REMINDER("REFILL_REMINDER"),
    OPEN_SOURCE("OPEN_SOURCE"),
    REMIND_LATER("REMIND_LATER"),
    TRACK_PRICE("TRACK_PRICE"),
    REVIEW_EXPIRY("REVIEW_EXPIRY");

    companion object {
        fun fromCode(value: String?): ProactiveAction? = entries.firstOrNull {
            it.code.equals(value?.trim(), ignoreCase = true)
        }
    }
}

object ProactiveActionResolver {
    fun resolve(item: VaultItem): List<ProactiveAction> {
        val metadata = item.parsedMetadata + item.customFields
        val requested = item.parsedMetadata[METADATA_KEY]
            ?.split(',')
            ?.mapNotNull(ProactiveAction::fromCode)
            .orEmpty()
        val deterministic = buildList {
            when (item.effectiveClassification) {
                Classification.RECEIPT,
                Classification.INVOICE -> if (metadata.hasAny("total", "amount")) add(ProactiveAction.COPY_TOTAL)
                Classification.PRESCRIPTION -> add(ProactiveAction.REFILL_REMINDER)
                Classification.PASSPORT,
                Classification.IDENTITY_DOCUMENT -> if (
                    item.expiryDate != null || metadata.hasAny("expiry_date")
                ) {
                    add(ProactiveAction.REVIEW_EXPIRY)
                }
                Classification.TICKET,
                Classification.HOTEL -> add(ProactiveAction.ADD_TO_CALENDAR)
                Classification.WARRANTY_CARD,
                Classification.SERIAL_PLATE -> if (metadata.hasAny("model_number")) add(ProactiveAction.FIND_MANUAL)
                Classification.BUSINESS_CARD -> if (metadata.hasAny("contact_name", "email", "phone")) {
                    add(ProactiveAction.ADD_CONTACT)
                }
                Classification.PRODUCT_PHOTO -> add(ProactiveAction.TRACK_PRICE)
                Classification.MOVIE,
                Classification.TV_SERIES,
                Classification.BOOK -> {
                    if (metadata["provider_url"].isHttpUrl()) add(ProactiveAction.OPEN_SOURCE)
                    add(ProactiveAction.REMIND_LATER)
                }
                else -> Unit
            }
        }

        return (deterministic + requested)
            .distinct()
            .filter { it.isEligible(item, metadata) }
    }

    private fun ProactiveAction.isEligible(item: VaultItem, metadata: Map<String, String>): Boolean = when (this) {
        ProactiveAction.COPY_TOTAL -> metadata.hasAny("total", "amount")
        ProactiveAction.ADD_CONTACT -> metadata.hasAny("contact_name", "email", "phone")
        ProactiveAction.FIND_MANUAL -> metadata.hasAny("model_number")
        ProactiveAction.ADD_TO_CALENDAR -> item.effectiveClassification in setOf(Classification.TICKET, Classification.HOTEL)
        ProactiveAction.REFILL_REMINDER -> item.effectiveClassification == Classification.PRESCRIPTION
        ProactiveAction.OPEN_SOURCE -> metadata["provider_url"].isHttpUrl()
        ProactiveAction.REMIND_LATER -> item.effectiveClassification in setOf(
            Classification.MOVIE,
            Classification.TV_SERIES,
            Classification.BOOK
        )
        ProactiveAction.TRACK_PRICE -> item.effectiveClassification == Classification.PRODUCT_PHOTO
        ProactiveAction.REVIEW_EXPIRY -> item.expiryDate != null || metadata.hasAny("expiry_date")
    }

    private fun Map<String, String>.hasAny(vararg keys: String): Boolean =
        keys.any { get(it)?.isNotBlank() == true }

    private fun String?.isHttpUrl(): Boolean =
        this?.startsWith("https://", ignoreCase = true) == true ||
            this?.startsWith("http://", ignoreCase = true) == true

    const val METADATA_KEY = "suggested_action_codes"
}
