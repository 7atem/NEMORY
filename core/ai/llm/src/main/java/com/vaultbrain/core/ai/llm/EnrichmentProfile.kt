package com.vaultbrain.core.ai.llm

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.LensId
import com.vaultbrain.core.common.model.ProactiveAction
import com.vaultbrain.core.common.metadata.MetadataFieldType
import com.vaultbrain.core.common.metadata.MetadataValueNormalizer

/** Compact category hints for the local model. Metadata fields are preferred, not exhaustive. */
data class EnrichmentProfile(
    val classification: Classification,
    val primaryLens: String?,
    val titleGuidance: String,
    val summaryGuidance: String,
    val preferredMetadataKeys: Set<String>,
    val metadataFieldTypes: Map<String, MetadataFieldType>,
    val enumMetadataValues: Map<String, Set<String>> = emptyMap(),
    val supportedActions: Set<ProactiveAction> = emptySet(),
    val defaultMetadata: Map<String, String> = emptyMap()
)

object EnrichmentProfileRegistry {
    private val profiles = listOf(
        profile(
            Classification.RECEIPT, LensId.MONEY,
            "Use the merchant name followed by Receipt; never put a card or account number in the title.",
            "State the visible total, currency, and purchase date when available.",
            "merchant", "total", "tax", "currency", "purchase_date", "date", "payment_method", "category", "items",
            actions = setOf(ProactiveAction.COPY_TOTAL)
        ),
        profile(
            Classification.INVOICE, LensId.MONEY,
            "Use the supplier name and invoice label or visible invoice number.",
            "State the amount due, currency, and due date without implying payment occurred.",
            "supplier", "biller", "invoice_number", "total", "amount_due", "tax", "currency", "due_date", "date", "status", "iban",
            actions = setOf(ProactiveAction.COPY_TOTAL)
        ),
        profile(
            Classification.PRESCRIPTION, LensId.HEALTH,
            "Use Prescription plus the visible medication or clinic name; do not expose patient identifiers.",
            "Summarize only visible medication, dosage, frequency, and refill facts; give no medical advice.",
            "medication", "dosage", "frequency", "doctor", "refills", "pharmacy", "prescription_date",
            actions = setOf(ProactiveAction.REFILL_REMINDER)
        ),
        profile(
            Classification.LAB_RESULT, LensId.HEALTH,
            "Use the visible test or lab name; do not include patient identifiers.",
            "Report visible result values and ranges factually; do not diagnose or interpret clinically.",
            "lab_name", "test_name", "lab_metric", "lab_value", "unit", "reference_range", "result_date"
        ),
        profile(
            Classification.PASSPORT, LensId.BUREAUCRACY,
            "Use Passport plus the visible holder name; never include the document number in the title.",
            "State nationality and expiry when visible; omit sensitive numbers from the summary.",
            "full_name", "name", "document_number", "passport_number", "nationality", "dob", "expiry_date", "document_type", "issuing_country",
            actions = setOf(ProactiveAction.REVIEW_EXPIRY)
        ),
        profile(
            Classification.IDENTITY_DOCUMENT, LensId.BUREAUCRACY,
            "Use the visible identity-document type plus holder name; never include the document number in the title.",
            "State only the visible document type, issuing country, and expiry; omit sensitive numbers from the summary.",
            "full_name", "name", "document_number", "nationality", "issuing_country", "dob", "expiry_date", "document_type",
            enums = mapOf(
                "document_type" to setOf("national_id", "identity_card", "driver_license", "residence_card", "other_id")
            ),
            actions = setOf(ProactiveAction.REVIEW_EXPIRY)
        ),
        profile(
            Classification.TICKET, LensId.TRAVEL,
            "Use the event or route and visible date.",
            "State origin/destination or event, date/time, gate, and seat when visible.",
            "event_name", "carrier", "route", "flight_number", "train_number", "origin", "destination", "departure_date", "date", "time", "gate", "seat", "pnr", "confirmation", "passenger",
            actions = setOf(ProactiveAction.ADD_TO_CALENDAR)
        ),
        profile(
            Classification.HOTEL, LensId.TRAVEL,
            "Use the hotel name and city.",
            "State check-in, check-out, city, and booking status when visible.",
            "hotel", "hotel_name", "city", "location", "check_in", "check_out", "booking_reference", "confirmation", "status",
            actions = setOf(ProactiveAction.ADD_TO_CALENDAR)
        ),
        profile(
            Classification.WARRANTY_CARD, LensId.BUREAUCRACY,
            "Use the product or manufacturer followed by Warranty.",
            "State the product, purchase date, and visible warranty expiry.",
            "manufacturer", "product", "model_number", "serial_number", "purchase_date", "warranty_expiry",
            actions = setOf(ProactiveAction.FIND_MANUAL)
        ),
        profile(
            Classification.BUSINESS_CARD, LensId.BUREAUCRACY,
            "Use the person's visible name and company.",
            "State their visible role and company; do not infer missing contact details.",
            "contact_name", "job_title", "company", "email", "phone", "website",
            actions = setOf(ProactiveAction.ADD_CONTACT)
        ),
        profile(
            Classification.PRODUCT_PHOTO, LensId.MONEY,
            "Use the visible product and brand or model.",
            "State the visible price, model, and retailer/source without inventing specifications.",
            "brand", "product_name", "model_number", "quantity", "size", "price", "currency", "barcode", "provider_url",
            actions = setOf(ProactiveAction.TRACK_PRICE)
        ),
        profile(
            Classification.MENU_PHOTO, LensId.MONEY,
            "Use the restaurant or menu section name.",
            "Mention visible dishes, prices, or cuisine only; do not infer ingredients or allergens.",
            "restaurant", "item_name", "price", "currency", "cuisine"
        ),
        profile(
            Classification.SERIAL_PLATE, LensId.TRAVEL,
            "Use the visible manufacturer and product/model followed by Serial plate.",
            "State visible model and serial identifiers exactly.",
            "manufacturer", "product", "model_number", "serial_number",
            actions = setOf(ProactiveAction.FIND_MANUAL)
        ),
        profile(
            Classification.GENERAL_DOCUMENT, LensId.BUREAUCRACY,
            "Use the visible document subject or sender.",
            "State the document purpose, sender, date, and deadline when explicit.",
            "document_type", "subject", "sender", "date", "deadline"
        ),
        profile(
            Classification.WEB_ARTICLE, LensId.MEDIA,
            "Use the exact visible article headline.",
            "State the visible source, author, topic, and publication date; do not invent publication facts.",
            "author", "provider", "date", "url", "topic",
            actions = setOf(ProactiveAction.OPEN_SOURCE, ProactiveAction.REMIND_LATER)
        ),
        profile(
            Classification.SCENE_PHOTO, null,
            "Use a short title based only on visible objects, text, and setting.",
            "Describe only the visible scene and text; do not infer a location, people, or event.",
            "location", "date"
        ),
        profile(
            Classification.MEME_JUNK, LensId.MEDIA,
            "Use a short description of the visible meme or saved image.",
            "Summarize only the visible joke, caption, and topic; do not invent its origin.",
            "provider", "url", "topic",
            actions = setOf(ProactiveAction.OPEN_SOURCE)
        ),
        profile(
            Classification.OTHER, null,
            "Use the most specific evidence-supported name for the captured item.",
            "Describe the useful visible facts without forcing a document category."
        ),

        mediaProfile(
            Classification.MOVIE,
            "Use the exact movie title and visible release year.",
            "Describe why it was saved and its watch status; do not invent plot or ratings.",
            metadata = arrayOf("release_year", "director"),
            type = "movie",
            status = "want_to_watch"
        ),
        mediaProfile(
            Classification.TV_SERIES,
            "Use the exact series title plus season when visible.",
            "State the saved season/episode and watch status; do not invent plot or ratings.",
            metadata = arrayOf("release_year", "season", "episode"),
            type = "tv_series",
            status = "want_to_watch"
        ),
        mediaProfile(
            Classification.BOOK,
            "Use the exact book title and visible author.",
            "State the author and reading status; do not invent themes, reviews, or ratings.",
            metadata = arrayOf("author", "isbn", "release_year"),
            type = "book",
            status = "want_to_read"
        ),

        profile(
            Classification.UNKNOWN, null,
            "Use the most specific visible heading; otherwise use Captured item.",
            "Summarize only directly visible facts and say when the content is unclear.",
            "document_type", "date", "url"
        )
    ).associateBy(EnrichmentProfile::classification)

    fun forClassification(classification: Classification?): EnrichmentProfile =
        profiles[classification] ?: checkNotNull(profiles[Classification.UNKNOWN])

    val supportedClassifications: Set<Classification> = profiles.keys

    private fun mediaProfile(
        classification: Classification,
        title: String,
        summary: String,
        metadata: Array<String>,
        type: String,
        status: String
    ) = profile(
        classification, LensId.MEDIA, title, summary,
        *arrayOf("provider_url", *metadata),
        actions = setOf(ProactiveAction.OPEN_SOURCE, ProactiveAction.REMIND_LATER),
        defaults = emptyMap()
    )

    private fun profile(
        classification: Classification,
        primaryLens: String?,
        title: String,
        summary: String,
        vararg metadata: String,
        enums: Map<String, Set<String>> = emptyMap(),
        actions: Set<ProactiveAction> = emptySet(),
        defaults: Map<String, String> = emptyMap()
    ) = EnrichmentProfile(
        classification = classification,
        primaryLens = primaryLens,
        titleGuidance = title,
        summaryGuidance = summary,
        preferredMetadataKeys = metadata.toSet(),
        metadataFieldTypes = metadata.associateWith { key ->
            if (key in enums) MetadataFieldType.ENUM else MetadataValueNormalizer.typeForKey(key)
        },
        enumMetadataValues = enums,
        supportedActions = actions,
        defaultMetadata = defaults
    )
}
