package com.vaultbrain.core.ai.heuristics

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.LensId
import com.vaultbrain.core.common.metadata.MetadataFieldType
import com.vaultbrain.core.common.metadata.MetadataValueNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast, on-device regex-based extractor for common document fields.
 *
 * This is intentionally simple and deterministic. It is used as a fallback
 * when ML models are unavailable and as a pre-filter for downstream classifiers.
 */
@Singleton
class HeuristicExtractor @Inject constructor() {

    fun extract(
        text: String,
        visionObjects: List<String> = emptyList(),
        neuralClassification: Classification? = null,
        neuralConfidence: Float = 0f
    ): HeuristicExtractionResult {
        val dates = extractDates(text)
        val amounts = extractAmounts(text)
        val currencies = extractCurrencies(text)
        val emails = extractEmails(text)
        val phones = extractPhones(text)
        val urls = extractUrls(text)
        val iban = extractIban(text)

        val inferredClassification = inferClassification(text, dates, amounts, iban, visionObjects)

        // Evidence fusion: combine neural classifier with heuristic classification.
        val fusedClassification = fuseClassifications(
            heuristicClassification = inferredClassification,
            neuralClassification = neuralClassification,
            neuralConfidence = neuralConfidence
        )

        val lensTags = inferLensTags(fusedClassification, text, visionObjects)
        val confidence = computeConfidence(
            dates, amounts, currencies, emails, phones, urls, iban,
            visionObjects, fusedClassification, lensTags,
            neuralClassification, neuralConfidence
        )
        val title = inferTitle(text, fusedClassification)
        val summary = inferSummary(text, fusedClassification, amounts, currencies)
        val metadata = buildMetadata(text, fusedClassification, dates, amounts, currencies, emails, phones, urls, iban)
        val (expiryDate, alertDate) = parseDates(dates, fusedClassification, text)

        return HeuristicExtractionResult(
            dates = dates,
            amounts = amounts,
            currencies = currencies,
            emails = emails,
            phones = phones,
            urls = urls,
            iban = iban,
            inferredClassification = fusedClassification,
            confidence = confidence,
            title = title,
            summary = summary,
            metadata = metadata,
            lensTags = lensTags,
            expiryDate = expiryDate,
            alertDate = alertDate
        )
    }

    private fun extractDates(text: String): List<String> {
        // ISO, common European, US, and written dates.
        val patterns = listOf(
            ISO_DATE,
            SLASH_DATE,
            DOT_DATE,
            WRITTEN_DATE
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value } }.distinct()
    }

    private fun extractAmounts(text: String): List<Double> {
        return AMOUNT_PATTERN.findAll(text)
            .map {
                MetadataValueNormalizer.normalize(
                    key = "total",
                    rawValue = it.groupValues[1],
                    fieldType = MetadataFieldType.DECIMAL
                )?.toDoubleOrNull()
            }
            .filterNotNull()
            .distinct()
            .toList()
    }

    private fun extractCurrencies(text: String): List<String> {
        return CURRENCY_PATTERN.findAll(text)
            .mapNotNull {
                MetadataValueNormalizer.normalize(
                    key = "currency",
                    rawValue = it.value,
                    fieldType = MetadataFieldType.CURRENCY
                )
            }
            .distinct()
            .toList()
    }

    private fun extractEmails(text: String): List<String> {
        return EMAIL_PATTERN.findAll(text)
            .map { it.value }
            .distinct()
            .toList()
    }

    private fun extractPhones(text: String): List<String> {
        return PHONE_PATTERN.findAll(text)
            .map { it.value.trim() }
            .distinct()
            .toList()
    }

    private fun extractUrls(text: String): List<String> {
        return URL_PATTERN.findAll(repairWrappedUrls(text))
            .map { it.value }
            .distinct()
            .toList()
    }

    private fun repairWrappedUrls(text: String): String {
        var repaired = text.lineSequence().joinToString("\n", transform = ::repairUrlLine)
        repeat(3) {
            repaired = repaired.replace(WRAPPED_URL_PATTERN, "\$1\$2")
            repaired = repaired.lineSequence().joinToString("\n", transform = ::repairUrlLine)
        }
        return repaired
    }

    private fun repairUrlLine(line: String): String {
        val start = line.indexOf("http://", ignoreCase = true).takeIf { it >= 0 }
            ?: line.indexOf("https://", ignoreCase = true).takeIf { it >= 0 }
            ?: return line
        val prefix = line.substring(0, start)
        val fragments = line.substring(start).trim().split(Regex("\\s+"))
        if (fragments.size == 1) return line

        var repairedUrl = fragments.first()
        var consumed = 1
        for (index in 1 until fragments.size) {
            val fragment = fragments[index]
            val continuesUrl = repairedUrl.lastOrNull() in URL_CONTINUATION_ENDINGS ||
                fragment.any { it in URL_FRAGMENT_MARKERS }
            if (!continuesUrl) break
            repairedUrl += fragment
            consumed++
        }
        val suffix = fragments.drop(consumed).joinToString(" ").takeIf { it.isNotEmpty() }
        return prefix + repairedUrl + suffix?.let { " $it" }.orEmpty()
    }

    private fun extractIban(text: String): List<String> {
        return IBAN_PATTERN.findAll(text)
            .map { it.value.uppercase().replace(" ", "") }
            .distinct()
            .toList()
    }

    private fun inferClassification(
        text: String,
        dates: List<String>,
        amounts: List<Double>,
        iban: List<String>,
        visionObjects: List<String>
    ): Classification {
        val lower = normalizeForMatching(text)
        val lowerVision = visionObjects.map { it.lowercase() }.joinToString(" ")
        val emails = extractEmails(text)
        val phones = extractPhones(text)

        return when {

            lower.containsAny("passport", "جواز سفر", "رقم الجواز") || lowerVision.contains("passport") -> Classification.PASSPORT
            lower.containsAny(
                "identity card", "id card", "national id", "national identity", "driver license",
                "driver's license", "driving licence", "residence card", "بطاقة هوية",
                "بطاقة رقم قومي", "الرقم القومي", "رخصة قيادة"
            ) || lowerVision.containsAny("identity card", "id card", "driver license") ->
                Classification.IDENTITY_DOCUMENT
            lower.containsAny(
                "tv series", "tv show", "television series", "miniseries", "episode ",
                "season ", "series review"
            ) -> Classification.TV_SERIES
            lower.containsAny(
                "movie", "film", "cinema", "watchlist", "movie review", "film review",
                "movie trailer", "imdb.com/title", "letterboxd.com/film", "netflix.com/title",
                "themoviedb.org", "rottentomatoes.com", "primevideo.com/detail",
                "disneyplus.com", "max.com/", "tv.apple.com"
            )  -> Classification.MOVIE
            lower.containsAny(
                "book", "novel", "reading list", "readlist", "book review", "isbn",
                "goodreads.com/book", "app.thestorygraph.com/books", "books.google."
            ) || lowerVision.contains("book") -> Classification.BOOK
            lower.containsAny(
                "boarding pass", "flight", "airport", "departure", "arrival", "train ticket",
                "bus ticket", "gate ", "رحلة", "تذكرة طيران"
            ) -> Classification.TICKET
            lower.contains("lab result") || lower.contains("laboratory") || lower.contains("cholesterol") || lower.contains("hba1c") -> Classification.LAB_RESULT
            lower.containsAny("prescription", "rx:", "rx ", "medication", "clinic", "روشتة", "دواء") || lowerVision.contains("prescription") -> Classification.PRESCRIPTION
            lower.contains("hotel") || lower.contains("reservation") -> Classification.HOTEL
            lower.contains("warranty") || lower.contains("guarantee") -> Classification.WARRANTY_CARD
            lower.contains("business card") || lowerVision.contains("business card") || (emails.isNotEmpty() && phones.isNotEmpty() && !lower.contains("invoice") && !lower.contains("receipt")) -> Classification.BUSINESS_CARD
            lower.contains("vehicle registration") || lower.contains("license plate") || lower.contains("plate:") ||
                (lower.containsAny("serial", "model:") && lower.containsAny("car", "vehicle", "engine", "odometer")) -> Classification.SERIAL_PLATE
            lower.contains("menu") || lowerVision.contains("menu") -> Classification.MENU_PHOTO
            lower.contains("product") || lower.contains("price") || lowerVision.contains("product") || lowerVision.contains("food") || lowerVision.contains("beverage") || lowerVision.contains("packaged goods") || lowerVision.contains("box") || lowerVision.contains("label") || lowerVision.contains("ingredient") || lower.contains("net wt") -> Classification.PRODUCT_PHOTO
            iban.isNotEmpty() || lower.containsAny("invoice", "rechnung", "bank transfer", "wire transfer") -> Classification.INVOICE
            lower.contains("receipt") || lower.contains("total:") || lowerVision.contains("receipt") || (amounts.isNotEmpty() && dates.isNotEmpty()) -> Classification.RECEIPT
            lower.contains("subscription") || lower.contains("monthly bill") -> Classification.RECEIPT // Categorize as money-related
            lower.containsAny(
                "dry clean", "laundry", "pickup code", "pickup ticket", "repair order", "service order",
                "return window", "return by", "podcast", "course",
                "task", "todo", "to-do", "deadline", "meeting", "project", "verify account", "phishing"
            ) -> Classification.GENERAL_DOCUMENT
            lowerVision.contains("poster") || lowerVision.contains("art") || lowerVision.contains("toy") -> Classification.PRODUCT_PHOTO
            SERIAL_NUMBER_PATTERN.containsMatchIn(lower) && !lower.containsAny("car", "vehicle") -> Classification.SERIAL_PLATE
            INSURANCE_POLICY_PATTERN.containsMatchIn(lower) -> Classification.GENERAL_DOCUMENT
            UPS_TRACKING_PATTERN.containsMatchIn(text) || GENERIC_TRACKING_PATTERN.containsMatchIn(lower) -> Classification.GENERAL_DOCUMENT
            else -> Classification.UNKNOWN
        }
    }

    private fun computeConfidence(
        dates: List<String>,
        amounts: List<Double>,
        currencies: List<String>,
        emails: List<String>,
        phones: List<String>,
        urls: List<String>,
        iban: List<String>,
        visionObjects: List<String>,
        classification: Classification,
        lensTags: Set<String>,
        neuralClassification: Classification? = null,
        neuralConfidence: Float = 0f
    ): Float {
        val fieldCount = listOf(
            dates.size,
            amounts.size,
            currencies.size,
            emails.size,
            phones.size,
            urls.size,
            iban.size,
            if (visionObjects.isNotEmpty()) 2 else 0 // Boost if vision models detected anything
        ).sum()
        
        var confidence = fieldCount * 0.08f
        
        if (classification != Classification.UNKNOWN) {
            confidence += 0.3f // Base boost if we successfully categorized it
        }
        if (lensTags.isNotEmpty()) confidence += 0.15f

        // Neural classifier boost: if neural model agrees with heuristic, boost confidence.
        if (neuralClassification != null && neuralClassification != Classification.UNKNOWN) {
            if (neuralClassification == classification) {
                // Agreement between neural and heuristic — strong signal.
                confidence += NEURAL_AGREEMENT_BOOST
            } else if (neuralConfidence > NEURAL_HIGH_CONFIDENCE_THRESHOLD) {
                // Neural model is highly confident but disagrees — partial boost.
                confidence += neuralConfidence * NEURAL_WEIGHT * 0.5f
            }
        }
        
        return confidence.coerceIn(0f, 0.95f)
    }

    private fun inferTitle(text: String, classification: Classification): String? {
        val firstLine = text.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        return when (classification) {
            Classification.RECEIPT -> firstLine ?: "Receipt"
            Classification.PRESCRIPTION -> "Prescription"
            Classification.LAB_RESULT -> "Lab result"
            Classification.PASSPORT -> "Passport"
            Classification.IDENTITY_DOCUMENT -> "Identity document"
            Classification.INVOICE -> "Invoice"
            Classification.TICKET -> "Ticket"
            Classification.HOTEL -> "Hotel booking"
            Classification.BUSINESS_CARD -> "Business card"
            Classification.PRODUCT_PHOTO -> "Product"
            Classification.MENU_PHOTO -> "Menu"
            Classification.MOVIE -> inferMediaTitle(text, "Movie to watch")
            Classification.TV_SERIES -> inferMediaTitle(text, "Series to watch")
            Classification.BOOK -> inferMediaTitle(text, "Book to read")
            else -> firstLine?.take(80)
        }
    }

    private fun inferMediaTitle(text: String, fallback: String): String {
        val genericHeadings = setOf(
            "movie", "movies", "movie watchlist", "film", "film watchlist", "watchlist",
            "movie review", "tv series", "tv show", "series watchlist", "series review",
            "book", "books", "book review", "reading list", "readlist", "book recommendation"
        )
        return text.lineSequence()
            .map(String::trim)
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("http://", ignoreCase = true) &&
                    !line.startsWith("https://", ignoreCase = true) &&
                    line.lowercase() !in genericHeadings
            }
            ?.take(80)
            ?: fallback
    }

    private fun inferSummary(
        text: String,
        classification: Classification,
        amounts: List<Double>,
        currencies: List<String>
    ): String? {
        return when (classification) {
            Classification.RECEIPT -> {
                val amount = amounts.maxOrNull()
                val currency = currencies.firstOrNull()
                if (amount != null && currency != null) {
                    "Total: $currency $amount"
                } else null
            }
            Classification.PRESCRIPTION -> "Medical prescription"
            Classification.LAB_RESULT -> "Medical lab result"
            Classification.IDENTITY_DOCUMENT -> "Identity document"
            Classification.TICKET -> "Travel ticket"
            Classification.HOTEL -> "Hotel reservation"
            Classification.MOVIE -> "Saved to your watchlist"
            Classification.TV_SERIES -> "Saved to your series watchlist"
            Classification.BOOK -> "Saved to your reading list"
            else -> text.lineSequence().take(2).joinToString(" ").take(200)
        }
    }

    private fun buildMetadata(
        text: String,
        classification: Classification,
        dates: List<String>,
        amounts: List<Double>,
        currencies: List<String>,
        emails: List<String>,
        phones: List<String>,
        urls: List<String>,
        iban: List<String>
    ): Map<String, String> {
        val extracted = buildMap<String, String> {
            dates.firstOrNull()?.let { put("date", it) }
            val explicitTotalMatch = Regex("(?i)(?:total|amount due|sum|balance)[\\s\\:]*([^\\d\\n\\r]{0,3})([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?|[0-9]+[.,][0-9]{2})").find(text)
            if (explicitTotalMatch != null) {
                val rawTotal = explicitTotalMatch.groupValues[2]
                MetadataValueNormalizer.normalize("total", rawTotal, MetadataFieldType.DECIMAL)?.let { put("total", it) } ?: put("total", rawTotal)
            } else {
                // Don't unconditionally grab the biggest number for total
            }
            currencies.firstOrNull()?.let { put("currency", it) }
            emails.firstOrNull()?.let { put("email", it) }
            if (classification == Classification.BUSINESS_CARD || classification == Classification.INVOICE) phones.firstOrNull()?.let { put("phone", it) }
            urls.firstOrNull()?.let { put("url", it) }
            iban.firstOrNull()?.let { put("iban", it) }
            
            if (classification.name.lowercase() != "unknown") put("type", classification.name.lowercase())
            
            // Inject sub-category from hierarchy if detected
            KeywordDictionary.detectHierarchy(text).values.firstOrNull()?.let { subcat ->
                put("sub_category", subcat)
            }

            when (classification) {
                Classification.RECEIPT, Classification.INVOICE -> text.lineSequence().firstOrNull { it.isNotBlank() }
                    ?.take(80)?.let { merchant ->
                        put("merchant", merchant.trim())
                        TAX_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("tax", it) }
                        PAYMENT_METHOD_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("card_last4", it) }
                        REF_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("reference_number", it) }
                        val lineItems = RECEIPT_LINE_PATTERN.findAll(text)
                            .map { it.value.trim() }
                            .filterNot { it.contains("total", true) || it.contains("tax", true) }
                            .take(20)
                            .toList()
                        if (lineItems.isNotEmpty()) put("items", lineItems.joinToString(" | "))
                        if (classification == Classification.RECEIPT) {
                            PURCHASE_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)
                                ?.let { put("purchase_date", it) }
                                ?: dates.firstOrNull()?.let { put("purchase_date", it) }
                        } else {
                            DUE_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)
                                ?.let { put("due_date", it) }
                        }
                    }
                Classification.PRESCRIPTION -> {
                    DOSAGE_PATTERN.find(text)?.value?.let { put("dosage", it) }
                    FREQUENCY_PATTERN.find(text)?.value?.let { put("frequency", it) }
                    DOCTOR_PATTERN.find(text)?.value?.let { put("doctor", it.trim()) }
                    REFILL_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("refills", it) }
                    dates.firstOrNull()?.let { put("prescription_date", it) }
                }
                Classification.LAB_RESULT -> {
                    LAB_VALUE_PATTERN.find(text)?.let { match ->
                        put("lab_metric", match.groupValues[1].trim())
                        put("lab_value", match.groupValues[2])
                    }
                    dates.firstOrNull()?.let { put("result_date", it) }
                }
                Classification.PASSPORT -> {
                    ID_NAME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("name", it.trim()) }
                    PASSPORT_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")?.let { put("document_number", it) }
                    DOB_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("dob", it) }
                    EXPIRY_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("expiry_date", it) }
                    put("document_type", "passport")
                }
                Classification.IDENTITY_DOCUMENT -> {
                    ID_NAME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("name", it.trim()) }
                    IDENTITY_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")
                        ?.let { put("document_number", it) }
                    DOB_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("dob", it) }
                    EXPIRY_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("expiry_date", it) }
                    put("document_type", inferIdentityDocumentType(text))
                }
                Classification.TICKET -> {
                    PNR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("pnr", it.replace(" ", "")) }
                    PASSENGER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("passenger", it.trim()) }
                    FLIGHT_PATTERN.find(text)?.value?.let { put("flight_number", it.replace(" ", "")) }
                    GATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("gate", it) }
                    SEAT_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("seat", it) }
                    TIME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("time", it) }
                    dates.firstOrNull()?.let { put("date", it) }
                }
                Classification.HOTEL -> {
                    PNR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("confirmation", it.replace(" ", "")) }
                    PASSENGER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("name", it.trim()) }
                    CHECK_IN_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("check_in", it) }
                    CHECK_OUT_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("check_out", it) }
                }
                Classification.WARRANTY_CARD -> {
                    EXPIRY_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("warranty_expiry", it) }
                    PURCHASE_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("purchase_date", it) }
                }
                Classification.BUSINESS_CARD -> {
                    val lines = text.lineSequence().filter { it.isNotBlank() && it.length < 50 }.take(2).toList()
                    if (lines.size >= 1) put("contact_name", lines[0].trim())
                    if (lines.size >= 2) put("job_title", lines[1].trim())
                }
                Classification.MOVIE, Classification.TV_SERIES, Classification.BOOK -> {
                    put(
                        "media_type",
                        when (classification) {
                            Classification.MOVIE -> "movie"
                            Classification.TV_SERIES -> "tv_series"
                            Classification.BOOK -> "book"
                            else -> error("Unreachable")
                        }
                    )
                    put(
                        "media_status",
                        MEDIA_STATUS_PATTERN.find(text)?.groupValues?.getOrNull(1)?.lowercase()
                            ?: if (classification == Classification.BOOK) "want_to_read" else "want_to_watch"
                    )
                    urls.firstOrNull()?.let { put("provider_url", it) }
                    YEAR_PATTERN.find(text)?.value?.let { put("release_year", it) }
                    if (classification == Classification.TV_SERIES) {
                        SEASON_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("season", it) }
                        EPISODE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("episode", it) }
                    }
                    if (classification == Classification.BOOK) {
                        AUTHOR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { put("author", it) }
                    }
                }
                else -> Unit
            }


            PICKUP_CODE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("pickup_code", it) }
            RETURN_BY_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("return_by", it) }
            MODEL_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("model_number", it) }
            ODOMETER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.let { put("odometer", it) }
            DEADLINE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("deadline", it) }
            MEDIA_STATUS_PATTERN.find(text)?.groupValues?.getOrNull(1)?.lowercase()?.let { put("media_status", it) }
            LOYALTY_CARD_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("loyalty_card", it) }
            INSURANCE_POLICY_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("policy_number", it) }
            SERIAL_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("serial_number", it) }
            UPS_TRACKING_PATTERN.find(text)?.value?.let { put("tracking_number", it) }
                ?: GENERIC_TRACKING_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("tracking_number", it) }
            LAB_REFERENCE_RANGE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("reference_range", it) }
            VIN_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("vin", it) }
            MIDDLE_EAST_PLATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("license_plate", it) }
        }
        return MetadataValueNormalizer.normalizeTypedValues(extracted)
    }

    private fun inferIdentityDocumentType(text: String): String {
        val lower = normalizeForMatching(text)
        return when {
            lower.containsAny("driver license", "driver's license", "driving licence", "رخصة قيادة") ->
                "driver_license"
            lower.containsAny("national id", "national identity", "بطاقة رقم قومي", "الرقم القومي") ->
                "national_id"
            lower.containsAny("residence card", "residence permit", "بطاقة إقامة") ->
                "residence_card"
            lower.containsAny("identity card", "id card", "بطاقة هوية") -> "identity_card"
            else -> "other_id"
        }
    }

    private fun inferLensTags(classification: Classification, text: String, visionObjects: List<String>): Set<String> {
        val lower = normalizeForMatching(text)
        val lowerVision = visionObjects.map { it.lowercase() }.joinToString(" ")
        val urls = extractUrls(text)
        val tags = mutableSetOf<String>()
        
        tags.addAll(KeywordDictionary.detectHierarchy(text).keys)
        
        when (classification) {
            Classification.RECEIPT, Classification.INVOICE, Classification.PRODUCT_PHOTO, Classification.MENU_PHOTO -> tags += LensId.MONEY
            Classification.PRESCRIPTION, Classification.LAB_RESULT -> tags += LensId.HEALTH
            Classification.TICKET, Classification.HOTEL, Classification.SERIAL_PLATE -> tags += LensId.TRAVEL
            Classification.PASSPORT, Classification.IDENTITY_DOCUMENT, Classification.BUSINESS_CARD, Classification.WARRANTY_CARD -> tags += LensId.BUREAUCRACY
            Classification.MOVIE, Classification.TV_SERIES, Classification.BOOK -> tags += LensId.MEDIA
            else -> Unit
        }
        if (lower.containsAny("warranty", "guarantee", "appliance", "user manual", "serial number", "model number")) tags += LensId.BUREAUCRACY
        if (lower.contains("car") || lower.contains("vehicle") || lower.contains("fuel") || lowerVision.contains("car") || lowerVision.contains("vehicle") || lowerVision.contains("tire")) tags += LensId.TRAVEL
        if (lower.containsAny("movie", "film", "book", "podcast", "watchlist", "course") || lowerVision.containsAny("poster", "film", "art")) tags += LensId.MEDIA
        if (lower.containsAny("task", "todo", "to-do", "deadline", "meeting", "project", "action item")) tags += LensId.BUREAUCRACY
        if (lower.containsAny("dry clean", "laundry", "pickup code", "pickup ticket", "repair order", "service order", "return window", "return by")) tags += LensId.MONEY
        if (looksSuspicious(lower, urls)) tags += LensId.BUREAUCRACY
        if (lower.containsAny("government", "application form", "permit", "visa", "residence card", "birth certificate")) tags += LensId.BUREAUCRACY
        if (lowerVision.contains("tea") || lowerVision.contains("food") || lowerVision.contains("beverage") || lowerVision.contains("bottle") || lowerVision.contains("box") || lowerVision.contains("packaged goods") || lowerVision.contains("ingredient") || lower.contains("net wt")) tags += LensId.MONEY
        if (INSURANCE_POLICY_PATTERN.containsMatchIn(lower)) tags += LensId.BUREAUCRACY
        if (UPS_TRACKING_PATTERN.containsMatchIn(text) || GENERIC_TRACKING_PATTERN.containsMatchIn(lower)) tags += LensId.MONEY
        if (LOYALTY_CARD_PATTERN.containsMatchIn(lower)) tags += LensId.MONEY
        
        // Ensure no deleted tags accidentally remain from KeywordDictionary
        tags.retainAll(LensId.ALL_LENSES)
        
        return tags
    }
    private fun looksSuspicious(lower: String, urls: List<String>): Boolean {
        val securityLanguage = lower.containsAny(
            "verify account", "account suspended", "urgent action", "confirm password", "login now",
            "one-time password", "otp code", "claim prize", "gift card", "phishing", "suspicious link",
            "qr code", "scan qr", "wifi password", "wi-fi password"
        )
        val shortenedLink = urls.any { url ->
            val normalized = url.lowercase()
            SHORTENER_DOMAINS.any { domain -> normalized.contains("://$domain/") }
        }
        return securityLanguage || shortenedLink
    }

    private fun normalizeForMatching(text: String): String = text
        .lowercase()
        .replace('-', '-')
        .replace('-', '-')
        .replace(Regex("[\\t\\r\\n]+"), " ")
        .replace(Regex(" +"), " ")
        .replace("b0arding", "boarding")
        .replace("fl1ght", "flight")
        .replace("passp0rt", "passport")
        .replace("prescripti0n", "prescription")
        .replace("rece1pt", "receipt")
        .replace("1nvoice", "invoice")
        .replace("veh1cle", "vehicle")
        .replace("pr0duct", "product")

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)

    private fun parseDates(dates: List<String>, classification: Classification, text: String): Pair<Long?, Long?> {
        val parsed = dates.mapNotNull(MetadataValueNormalizer::parseDateToEpochMillis).sorted()


        val now = System.currentTimeMillis()
        
        // Find explicitly labeled due dates for invoices
        val dueDateMatch = DUE_DATE_PATTERN.find(text)
        val explicitDueDate = dueDateMatch?.groupValues?.getOrNull(1)
            ?.let(MetadataValueNormalizer::parseDateToEpochMillis)
        val explicitExpiryDate = EXPIRY_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)
            ?.let(MetadataValueNormalizer::parseDateToEpochMillis)
        
        val future = parsed.firstOrNull { it >= now }
        val futureDate = explicitExpiryDate ?: explicitDueDate ?: future
        
        val expiry = futureDate.takeIf {
            classification in setOf(
                Classification.PASSPORT,
                Classification.IDENTITY_DOCUMENT,
                Classification.WARRANTY_CARD,
                Classification.SERIAL_PLATE,
                Classification.INVOICE
            )
        }

        val alert = when (classification) {
            Classification.PASSPORT, Classification.IDENTITY_DOCUMENT,
            Classification.WARRANTY_CARD, Classification.SERIAL_PLATE ->
                expiry?.minus(7L * 24 * 60 * 60 * 1000) // 1 week before
            Classification.INVOICE ->
                expiry?.minus(3L * 24 * 60 * 60 * 1000) // 3 days before due date
            Classification.TICKET, Classification.HOTEL ->
                futureDate?.minus(24L * 60 * 60 * 1000) // 24h before
            Classification.PRESCRIPTION -> {
                // Proactively remind to refill in 25 days if not specified
                now + (25L * 24 * 60 * 60 * 1000)
            }
            else -> null
        }

        return expiry to alert
    }

    /**
     * Fuses the heuristic rule-based classification with the neural model's prediction.
     *
     * Strategy:
     * - If heuristic produced a strong match (not UNKNOWN) and neural agrees → use it.
     * - If heuristic is UNKNOWN but neural is confident → use neural.
     * - If they disagree, prefer the one with stronger signal strength.
     */
    private fun fuseClassifications(
        heuristicClassification: Classification,
        neuralClassification: Classification?,
        neuralConfidence: Float
    ): Classification {
        // No neural result available — use heuristic as-is.
        if (neuralClassification == null || neuralClassification == Classification.UNKNOWN) {
            return heuristicClassification
        }

        // Heuristic couldn't classify — trust the neural model.
        if (heuristicClassification == Classification.UNKNOWN) {
            return neuralClassification
        }

        // Both classified — if they agree, great.
        if (heuristicClassification == neuralClassification) {
            return heuristicClassification
        }

        // Disagreement — trust neural if highly confident, otherwise trust heuristic
        // (text patterns are more reliable for document-heavy content).
        return if (neuralConfidence >= NEURAL_HIGH_CONFIDENCE_THRESHOLD) {
            neuralClassification
        } else {
            heuristicClassification
        }
    }

    companion object {
        // ── Evidence fusion weights ──
        /** Weight for neural classifier score in the fusion formula. */
        private const val NEURAL_WEIGHT = 0.50f
        /** Weight for heuristic score in the fusion formula. */
        private const val HEURISTIC_WEIGHT = 0.35f
        /** Weight for vision label score in the fusion formula. */
        private const val VISION_LABEL_WEIGHT = 0.15f
        /** Confidence boost when neural and heuristic classifications agree. */
        private const val NEURAL_AGREEMENT_BOOST = 0.15f
        /** Threshold above which neural prediction is considered high-confidence. */
        private const val NEURAL_HIGH_CONFIDENCE_THRESHOLD = 0.75f

        private val DOSAGE_PATTERN = """\b\d+(?:\.\d+)?\s?(?:mg|mcg|g|ml|tablet(?:s)?|tab(?:s)?)\b""".toRegex(RegexOption.IGNORE_CASE)
        private val FREQUENCY_PATTERN = """\b(?:every\s+\d+\s+hours?|\d+x\s+daily|once\s+daily|twice\s+daily|daily|at\s+bedtime)\b""".toRegex(RegexOption.IGNORE_CASE)
        private val DOCTOR_PATTERN = """(?im)^\s*(?:dr\.?|doctor)\s+[A-Za-z][A-Za-z .'-]{2,60}""".toRegex()
        private val LAB_VALUE_PATTERN = """(?im)^\s*([A-Za-z][A-Za-z0-9 %/()-]{1,40})\s*:\s*(\d+(?:\.\d+)?)""".toRegex()
        private val PAYMENT_METHOD_PATTERN = """(?i)\b(?:visa|mastercard|amex|card|ending in)[\s:#*-]*(\d{4})\b""".toRegex()
        private val REFILL_PATTERN = """(?i)\b(?:refills?|qty|quantity)[\s:#*-]*(\d+)\b""".toRegex()
        private val SEAT_PATTERN = """(?i)\b(?:seat)[\s:#*-]*([A-Z0-9]{1,4})\b""".toRegex()
        private val TIME_PATTERN = """(?i)\b(?:time|board|depart|departure)[\s:#*-]*(\d{1,2}:\d{2})\b""".toRegex()
        private val DOB_PATTERN = """(?i)\b(?:dob|date of birth|born)[\s:#*-]*(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})\b""".toRegex()
        private val DUE_DATE_PATTERN = """(?i)\bdue\s+(?:date)?[\s:#*-]*(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})\b""".toRegex()
        private val EXPIRY_DATE_PATTERN =
            """(?i)(?:expiry(?:\s+date)?|expires?|expiration(?:\s+date)?|valid\s+until|تاريخ\s+الانتهاء|ساري\s+حتى)[\s:#*-]*(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})""".toRegex()
        private val PURCHASE_DATE_PATTERN =
            """(?i)(?:purchase\s+date|purchased|date)[\s:#*-]*(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})""".toRegex()
        private val CHECK_IN_PATTERN =
            """(?i)(?:check[ -]?in|arrival|الوصول)[\s:#*-]*(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})""".toRegex()
        private val CHECK_OUT_PATTERN =
            """(?i)(?:check[ -]?out|departure|المغادرة)[\s:#*-]*(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})""".toRegex()
        private val TAX_PATTERN = """(?i)\b(?:tax|vat)[\s:$]*([0-9][0-9,.]*)""".toRegex()
        private val RECEIPT_LINE_PATTERN = """(?m)^\s*.+?\s+[0-9][0-9,.]*\s*(?:EGP|USD|EUR|SAR|AED)?\s*$""".toRegex(RegexOption.IGNORE_CASE)
        private val PASSPORT_NUMBER_PATTERN =
            """(?im)(?:passport\s*(?:no|number)?|document\s*(?:no|number)?)[\s:#-]*([A-Z0-9][A-Z0-9 ]{4,16})\s*$""".toRegex()
        private val IDENTITY_NUMBER_PATTERN =
            """(?im)^\s*(?:national\s+(?:id\b|number\b)|identity\s+(?:no\b|number\b)|id\s+(?:no\b|number\b)|license\s+(?:no\b|number\b)|الرقم\s+القومي|رقم\s+الهوية|رقم\s+الرخصة)[\s:#-]*([\p{L}\p{N}][\p{L}\p{N} -]{4,24})\s*$""".toRegex()
        private val FLIGHT_PATTERN = """\b[A-Z]{2,3}\s?\d{1,4}\b""".toRegex()
        private val GATE_PATTERN = """(?i)\bgate[\s:#-]*([A-Z0-9]{1,4})\b""".toRegex()
        private val PLATE_PATTERN = """(?i)\bplate[\s:#-]*([A-Z0-9 -]{3,15})""".toRegex()
        private val PICKUP_CODE_PATTERN = """(?i)\bpickup\s+(?:code|number|no)[\s:#-]*([A-Z0-9-]{3,16})\b""".toRegex()
        private val RETURN_BY_PATTERN = """(?i)\breturn\s+by[\s:#-]*(\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b""".toRegex()

        private val ODOMETER_PATTERN = """(?i)\bodometer[\s:#-]*([0-9][0-9,]{1,10})\b""".toRegex()
        private val DEADLINE_PATTERN = """(?i)\bdeadline[\s:#-]*(\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b""".toRegex()
        private val MEDIA_STATUS_PATTERN = """(?i)\b(?:film|movie|series|book|media)?\s*status[\s:#-]*(want[_ -]to[_ -](?:watch|read)|planned|watching|reading|watched|finished|completed|paused)\b""".toRegex()
        private val YEAR_PATTERN = """\b(?:19|20)\d{2}\b""".toRegex()
        private val SEASON_PATTERN = """(?i)\bseason[\s:#-]*(\d{1,3})\b""".toRegex()
        private val EPISODE_PATTERN = """(?i)\bepisode[\s:#-]*(\d{1,4})\b""".toRegex()
        private val AUTHOR_PATTERN = """(?im)^\s*(?:author|by)[\s:#-]+(.{2,80})$""".toRegex()
        // ── Expanded patterns for better classification coverage ──
        private val LOYALTY_CARD_PATTERN = """(?i)\b(?:frequent\s+flyer|miles|membership|loyalty|rewards?|member)\s*(?:card|no|number|#)?[\s:#-]*([A-Z0-9]{6,20})\b""".toRegex()
        private val INSURANCE_POLICY_PATTERN = """(?i)\b(?:policy|claim|insurance)\s*(?:no|number|#)?[\s:#-]*([A-Z0-9-]{5,25})\b""".toRegex()
        private val SERIAL_NUMBER_PATTERN = """(?i)\b(?:s/n|serial\s*(?:no|number|#)|imei)[\s:#-]*([A-Z0-9-]{6,25})\b""".toRegex()
        private val MODEL_NUMBER_PATTERN = """(?i)\b(?:model|mod\.?)\s*(?:no|number|#)?[\s:#-]*([A-Z0-9-]{4,20})\b""".toRegex()
        private val VIN_PATTERN = """\b([A-HJ-NPR-Z0-9]{17})\b""".toRegex()
        private val UPS_TRACKING_PATTERN = """\b1Z[A-Z0-9]{16}\b""".toRegex()
        private val GENERIC_TRACKING_PATTERN = """(?i)\b(?:tracking|shipment|consignment|awb)\s*(?:no|number|#)?[\s:#-]*([A-Z0-9-]{8,30})\b""".toRegex()
        private val LAB_REFERENCE_RANGE_PATTERN = """(?i)\b(?:normal|reference|ref\.?\s+range|range)[\s:]*([0-9.]+\s*[-–]\s*[0-9.]+)""".toRegex()
        private val MIDDLE_EAST_PLATE_PATTERN = """(?i)\bplate[\s:#-]*(\d{1,4}[- /]?[A-Za-z\u0600-\u06FF]{1,3})\b""".toRegex()
        private val PNR_PATTERN = """(?i)\b(?:pnr|booking ref|booking reference|booking code|confirmation)\s*(?:no|number|#)?[\s:#*-]*([A-Z0-9]{5,8})\b""".toRegex()
        private val PASSENGER_PATTERN = """(?im)^\s*(?:passenger|name|mr\.?|mrs\.?|ms\.?)\s*[:#*-]*\s*([A-Za-z]+[\s/]+[A-Za-z]+)\b""".toRegex()
        private val REF_NUMBER_PATTERN = """(?i)\b(?:invoice|receipt|ref|reference|order|bill)\s*(?:no|number|#)?[\s:#*-]*([A-Z0-9-]{4,20})\b""".toRegex()
        private val ID_NAME_PATTERN = """(?im)^\s*(?:name|surname|full name|given name)\s*[:#*-]*\s*([A-Za-z]+(?:\s+[A-Za-z]+){1,3})\b""".toRegex()
        
        // 2024-01-15, 2024/01/15, etc.
        private val ISO_DATE =
            "(?<!\\d)\\d{4}[-/\\.]\\d{1,2}[-/\\.]\\d{1,2}(?!\\d)".toRegex()

        // 15/01/2024, 01/15/2024, 15-01-2024
        private val SLASH_DATE =
            "(?<!\\d)\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}(?!\\d)".toRegex()

        // 15.01.2024
        private val DOT_DATE =
            "(?<!\\d)\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}(?!\\d)".toRegex()

        // 15 Jan 2024, January 15, 2024
        private val WRITTEN_DATE =
            "(?<!\\d)\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{2,4}(?!\\d)".toRegex(RegexOption.IGNORE_CASE)

        // 1,234.56 or 1.234,56 or 1234.56 or 100 or 5000
        private val AMOUNT_PATTERN =
            "\\b(?!\\d\\b)((?:[0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)(?:[.,][0-9]{1,2})?)\\b".toRegex()

        private val CURRENCY_PATTERN =
            "[\\$£€¥]|\\b(?:USD|EUR|GBP|JPY|CHF|CAD|AUD|CNY|INR|EGP|SAR|AED|KWD|QAR|BHD|OMD|JOD|SR|QR|KD)\\b".toRegex(RegexOption.IGNORE_CASE)
        private val SHORTENER_DOMAINS = setOf("bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy")
        private val URL_CONTINUATION_ENDINGS = setOf('.', '/', '-', '_', '?', '&', '=', '#')
        private val URL_FRAGMENT_MARKERS = setOf('/', '-', '_', '?', '&', '=', '#', '%')

        private val EMAIL_PATTERN =
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}".toRegex()

        // Loose international phone matching.
        private val PHONE_PATTERN =
            "(?:\\+\\d{1,3}[- ]?)?\\(?\\d{2,4}\\)?[- ]?\\d{3,4}[- ]?\\d{3,4}".toRegex()

        private val URL_PATTERN =
            "https?://[^\\s<>\"{}|\\^`\\[\\]]+".toRegex(RegexOption.IGNORE_CASE)

        private val WRAPPED_URL_PATTERN =
            """(https?://[^\s<>"{}|^`\[\]]*[./?&=_-])\s*\r?\n\s*([A-Za-z0-9][^\s<>"{}|^`\[\]]*)"""
                .toRegex(RegexOption.IGNORE_CASE)

        private val IBAN_PATTERN =
            "\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z\\d]{4}){3,7}(?:\\s?[A-Z\\d]{1,4})?\\b".toRegex(RegexOption.IGNORE_CASE)
    }
}






