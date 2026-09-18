"""Generic heuristic extraction fixes for Nemory. Applied once, then deleted."""
import io

p = 'core/ai/heuristics/src/main/java/com/vaultbrain/core/ai/heuristics/HeuristicExtractor.kt'
s = io.open(p, encoding='utf-8').read()

def rep(old, new):
    global s
    assert old in s, 'MISSING: ' + old[:90]
    s = s.replace(old, new, 1)

# ─── Fix 1a: label-aware best-total selection ───
rep('''            val explicitTotalMatch = EXPLICIT_TOTAL_PATTERN.find(text)
            if (explicitTotalMatch != null) {
                val rawTotal = explicitTotalMatch.groupValues[2]
                MetadataValueNormalizer.normalize("total", rawTotal, MetadataFieldType.DECIMAL)?.let { put("total", it) } ?: put("total", rawTotal)
            } else {
                // Don't unconditionally grab the biggest number for total
            }''',
'''            // Don't unconditionally grab the biggest number for total
            findBestTotal(text)?.let { put("total", it) }''')

# ─── Fix 1b: helpers (best-total, value guards, flight, written expiry, labeled doc number) ───
rep('''    private fun inferIdentityDocumentType(text: String): String {''',
'''    /**
     * Label-aware total selection. Every line carrying a total-like label contributes its
     * trailing amount; the strongest label rank wins in document order. Accounting-context
     * lines (subtotal, previous balance, change, tendered, tax) are never eligible, and the
     * amount must look like money (decimal cents or a currency marker on the same line), so
     * chart axes, counters, and serial numbers cannot win.
     */
    private fun findBestTotal(text: String): String? {
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        text.lineSequence().forEach { line ->
            val lower = line.lowercase().trim()
            if (lower.isEmpty()) return@forEach
            if (NEGATIVE_TOTAL_LABELS.any(lower::contains)) return@forEach
            val rank = when {
                STRONG_TOTAL_LABELS.any(lower::contains) -> 0
                TOTAL_WORD.containsMatchIn(lower) -> 1
                else -> return@forEach
            }
            if (rank >= bestRank) return@forEach
            val amount = AMOUNT_PATTERN.findAll(line).lastOrNull()?.value ?: return@forEach
            val moneyShape = amount.contains('.') || amount.contains(',') || CURRENCY_PATTERN.containsMatchIn(line)
            if (!moneyShape) return@forEach
            val normalized = MetadataValueNormalizer.normalize("total", amount, MetadataFieldType.DECIMAL)
                ?: return@forEach
            best = normalized
            bestRank = rank
        }
        return best
    }

    /** A value mined after a label must not itself be (only) label vocabulary. */
    private fun isUsableValue(value: String): Boolean {
        val words = value.trim().lowercase().split(WHITESPACE_PATTERN).filter { it.isNotBlank() }
        return words.isNotEmpty() && words.any { it !in LABEL_STOPWORDS }
    }

    /** When a labeled line carries no usable value, look at the following lines. */
    private fun valueAfterLabel(text: String, labelRegex: Regex, valueRegex: Regex): String? {
        val lines = text.lines()
        val index = lines.indexOfFirst { labelRegex.containsMatchIn(it) }
        if (index < 0) return null
        return (listOf(lines[index]) + lines.drop(index + 1).take(2)).firstNotNullOfOrNull { line ->
            valueRegex.find(line.trim())?.value?.takeIf(::isUsableValue)
        }
    }

    private fun isPlausiblePhone(value: String): Boolean {
        if (ZIP_CODE_PATTERN.matches(value)) return false
        val digits = value.count(Char::isDigit)
        if (digits !in 7..15) return false
        val hasStructure = value.any { it == ' ' || it == '-' || it == '(' || it == '+' }
        return hasStructure || digits >= 10
    }

    /** IATA-shaped flight codes: month abbreviations and barcode noise are not flights. */
    private fun findFlightNumber(text: String): String? {
        fun wellFormed(code: String): Boolean {
            val letters = code.takeWhile(Char::isLetter)
            val digits = code.dropWhile(Char::isLetter)
            return letters.length in 2..3 && letters !in FLIGHT_MONTH_TOKENS &&
                digits.length in 1..4 && digits.all(Char::isDigit)
        }
        val labelled = text.lineSequence().filter { it.contains("flight", true) }.toList()
        fun codesIn(lines: List<String>) = lines.flatMap { line ->
            FLIGHT_PATTERN.findAll(line).map { it.value.replace(" ", "") }
        }.filter(::wellFormed).distinct()
        val candidates = codesIn(labelled).ifEmpty { codesIn(text.lines()) }
        // Prefer realistic codes (2+ digits) in document order.
        return candidates.sortedBy { if (it.dropWhile(Char::isLetter).length >= 2) 0 else 1 }.firstOrNull()
    }

    /** Expiry dates written as "13 MAR 27", "13 MAR /MARS 27", or "29 NOV2019". */
    private fun findWrittenExpiry(text: String): String? {
        val lines = text.lines()
        val labelIndex = lines.indexOfFirst { EXPIRY_LABEL_PATTERN.containsMatchIn(it) }
        if (labelIndex < 0) return null
        val searchSpace = (listOf(lines[labelIndex]) + lines.drop(labelIndex + 1).take(2)).joinToString(" ")
        val match = WRITTEN_COMPACT_DATE.find(searchSpace) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = MONTH_CODES[match.groupValues[2].uppercase()] ?: return null
        val year = match.groupValues[3].let { if (it.length == 2) "20" + it else it }
        return "%04d-%02d-%02d".format(year.toInt(), month, day)
    }

    /** Passport/document number printed on or right after its label line. */
    private fun findLabeledDocumentNumber(text: String): String? {
        val lines = text.lines()
        val index = lines.indexOfFirst { DOC_NUMBER_LABEL_PATTERN.containsMatchIn(it) }
        if (index < 0) return null
        return (listOf(lines[index]) + lines.drop(index + 1).take(2)).firstNotNullOfOrNull { line ->
            DOC_NUMBER_TOKEN.findAll(line)
                .map { it.value }
                .firstOrNull { it !in DOC_NUMBER_REJECT && it.toSet().size > 1 }
        }
    }

    private fun inferIdentityDocumentType(text: String): String {''')

# ─── Fix 2a: phone shape guard ───
rep('''            if (classification == Classification.BUSINESS_CARD || classification == Classification.INVOICE) phones.firstOrNull()?.let { put("phone", it) }''',
'''            if (classification == Classification.BUSINESS_CARD || classification == Classification.INVOICE) {
                phones.firstOrNull(::isPlausiblePhone)?.let { put("phone", it) }
            }''')

# ─── Fix 2b/3: travel fields with guards + fallbacks ───
rep('''                Classification.TICKET -> {
                    PNR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("pnr", it.replace(" ", "")) }
                    PASSENGER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("passenger", it.trim()) }
                    FLIGHT_PATTERN.find(text)?.value?.let { put("flight_number", it.replace(" ", "")) }
                    GATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("gate", it) }
                    SEAT_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("seat", it) }''',
'''                Classification.TICKET -> {
                    PNR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.takeIf(::isUsableValue)?.let { put("pnr", it.replace(" ", "")) }
                    (PASSENGER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf(::isUsableValue)
                        ?: valueAfterLabel(text, PASSENGER_LINE_LABEL, NAME_VALUE_PATTERN))
                        ?.let { put("passenger", it) }
                    findFlightNumber(text)?.let { put("flight_number", it) }
                    GATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.takeIf(::isUsableValue)?.let { put("gate", it) }
                    SEAT_PATTERN.find(text)?.groupValues?.getOrNull(1)?.takeIf(::isUsableValue)?.let { put("seat", it) }''')

rep('''                Classification.HOTEL -> {
                    PNR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("confirmation", it.replace(" ", "")) }
                    PASSENGER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("name", it.trim()) }''',
'''                Classification.HOTEL -> {
                    PNR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.takeIf(::isUsableValue)?.let { put("confirmation", it.replace(" ", "")) }
                    (PASSENGER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf(::isUsableValue)
                        ?: valueAfterLabel(text, PASSENGER_LINE_LABEL, NAME_VALUE_PATTERN)
                        ?: GUEST_NAME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf(::isUsableValue))
                        ?.let { put("name", it) }''')

# passport labeled number + written expiry fallback
rep('''                    } else {
                        PASSPORT_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")?.let { put("document_number", it) }
                        DOB_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("dob", it) }
                    }
                    put("document_type", "passport")''',
'''                    } else {
                        (PASSPORT_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")?.takeIf(::isUsableValue)
                            ?: findLabeledDocumentNumber(text))
                            ?.let { put("document_number", it) }
                        DOB_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("dob", it) }
                    }
                    put("document_type", "passport")''')

# identity: name/number echo guards
rep('''                Classification.IDENTITY_DOCUMENT -> {
                    ID_NAME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("name", it.trim()) }
                    IDENTITY_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")
                        ?.let { put("document_number", it) }''',
'''                Classification.IDENTITY_DOCUMENT -> {
                    (ID_NAME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf(::isUsableValue)
                        ?: valueAfterLabel(text, ID_NAME_LINE_LABEL, NAME_VALUE_PATTERN))
                        ?.let { put("name", it) }
                    IDENTITY_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")
                        ?.takeIf(::isUsableValue)?.let { put("document_number", it) }''')

# reference/invoice-number echoes
rep('''                    REF_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("reference_number", it) }''',
'''                    REF_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.takeIf(::isUsableValue)?.let { put("reference_number", it) }''')
rep('''                        INVOICE_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)
                            ?.let { put("invoice_number", it) }''',
'''                        INVOICE_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)
                            ?.takeIf(::isUsableValue)?.let { put("invoice_number", it) }''')

# patient name guard + fallback
rep('''                    PATIENT_NAME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("patient_name", it.trim()) }''',
'''                    (PATIENT_NAME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf(::isUsableValue)
                        ?: valueAfterLabel(text, PATIENT_LINE_LABEL, NAME_VALUE_PATTERN))
                        ?.let { put("patient_name", it) }''')

# plate requires a digit
rep('''            PLATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { put("plate", it) }''',
'''            PLATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { value -> isUsableValue(value) && value.any(Char::isDigit) }?.let { put("plate", it) }''')

# written-expiry wiring for passports/IDs (generic, after the labeled expiry attempt)
rep('''            EXPIRY_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { 
                if (it.contains("/") && it.length <= 5) {''',
'''            (EXPIRY_DATE_PATTERN.find(text)?.groupValues?.getOrNull(1) ?: findWrittenExpiry(text))?.let { 
                if (it.contains("/") && it.length <= 5) {''')

# ─── Fix 5: drop the loose receipt-line block (strict ReceiptFieldExtractor items fill in) ───
rep('''                    val lineItems = RECEIPT_LINE_PATTERN.findAll(text)
                        .map { it.value.trim() }
                        .filterNot { it.contains("total", true) || it.contains("tax", true) }
                        .take(20)
                        .toList()
                    if (lineItems.isNotEmpty()) put("items", lineItems.joinToString(" | "))
''', '')

# merchant fallback: skip generic document headers
rep('''                    val merchant = receiptFields.merchant
                        ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80)?.trim()''',
'''                    val merchant = receiptFields.merchant
                        ?: text.lineSequence()
                            .map { it.trim() }
                            .firstOrNull { it.isNotBlank() && !GENERIC_DOC_HEADER.containsMatchIn(it) }
                            ?.take(80)''')

# ─── Fix 4a: classification — birth certificate, parking citation, identification card ───
rep('''            lower.containsAny("transcript", "academic record", "diploma", "degree certificate") -> Classification.ACADEMIC_RECORD''',
'''            lower.containsAny(
                "birth certificate", "certification of birth", "vital records", "record of birth",
                "شهادة ميلاد"
            ) -> Classification.IDENTITY_DOCUMENT
            lower.containsAny(
                "parking violation", "parking ticket", "notice of violation", "traffic citation",
                "citation #", "citation no"
            ) -> Classification.GENERAL_DOCUMENT
            lower.containsAny("transcript", "academic record", "diploma", "degree certificate") -> Classification.ACADEMIC_RECORD''')

rep('''                "identity card", "id card", "national id", "national identity", "driver license",''',
'''                "identity card", "identification card", "id card", "national id", "national identity", "driver license",''')

# ─── Fix 4b: lens tags — utility bills are money; fines are money; word-boundary "car" ───
rep('''            Classification.RECEIPT, Classification.INVOICE, Classification.PRODUCT_PHOTO, Classification.MENU_PHOTO, Classification.BANK_STATEMENT, Classification.CREDIT_CARD -> tags += LensId.MONEY''',
'''            Classification.RECEIPT, Classification.INVOICE, Classification.PRODUCT_PHOTO, Classification.MENU_PHOTO, Classification.BANK_STATEMENT, Classification.CREDIT_CARD, Classification.UTILITY_BILL -> tags += LensId.MONEY''')

rep('''        if (lower.contains("car") || lower.contains("vehicle") || lower.contains("fuel") || lowerVision.contains("car") || lowerVision.contains("vehicle") || lowerVision.contains("tire")) tags += LensId.TRAVEL''',
'''        if (CAR_WORD.containsMatchIn(lower) || lower.contains("vehicle") || lower.contains("fuel") || lowerVision.contains("car") || lowerVision.contains("vehicle") || lowerVision.contains("tire")) tags += LensId.TRAVEL
        if (lower.containsAny("violation", "citation", "fine", "penalty")) tags += LensId.MONEY''')

# ─── companion: new precompiled patterns + stopwords; drop unused ones ───
rep('''        private val EXPLICIT_TOTAL_PATTERN = "(?i)(?:t[o0]tal|amount due|sum|balance|price)[\\\\s\\\\:]*([^\\\\d\\\\n\\\\r]{0,3})([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?|[0-9]+[.,][0-9]{2})".toRegex()''',
'''        private val TOTAL_WORD = "(?i)\\\\bt[o0]tal\\\\b".toRegex()
        private val STRONG_TOTAL_LABELS = setOf(
            "grand total", "total due", "total amount due", "amount due", "total amount",
            "pay this amount", "amount to pay", "balance due", "total price"
        )
        private val NEGATIVE_TOTAL_LABELS = setOf(
            "subtotal", "sub total", "sub-total", "previous balance", "past due",
            "balance forward", "payments received", "payment received", "change due",
            "change", "tendered", "tend", "tax", "vat", "gst", "price to compare"
        )
        private val LABEL_STOPWORDS = setOf(
            "of", "the", "a", "an", "name", "date", "dates", "number", "no", "state",
            "plate", "passenger", "passanger", "information", "address", "location",
            "from", "to", "total", "mr", "mrs", "ms", "dr", "value", "type", "code", "id"
        )
        private val ZIP_CODE_PATTERN = "\\\\d{5}-\\\\d{4}".toRegex()
        private val CAR_WORD = "(?i)\\\\bcar\\\\b".toRegex()
        private val GENERIC_DOC_HEADER = "(?i)^\\\\s*(?:invoice|receipt|bill|sale)\\\\b".toRegex()
        private val NAME_VALUE_PATTERN = "^[A-Z][A-Za-z.\\u0027-]+(?:\\\\s+[A-Z][A-Za-z.\\u0027-]+){1,2}".toRegex()
        private val PASSENGER_LINE_LABEL = "(?i)pass(?:e|a)nger|\\\\bname\\\\b".toRegex()
        private val PATIENT_LINE_LABEL = "(?i)patient\\\\s*name".toRegex()
        private val ID_NAME_LINE_LABEL = "(?i)\\\\b(?:name|surname|given name)\\\\b".toRegex()
        private val GUEST_NAME_PATTERN = "(?i)\\\\b(?:guest|dear)\\\\s+(?:mr\\\\.?|mrs\\\\.?|ms\\\\.?)?\\\\s*([A-Z][A-Za-z.\\u0027-]+(?:\\\\s+[A-Z][A-Za-z.\\u0027-]+){0,2})".toRegex()
        private val FLIGHT_MONTH_TOKENS = setOf(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
        )
        private val EXPIRY_LABEL_PATTERN = "(?i)expiry|expires|expiration|exp\\\\b|valid\\\\s+(?:until|thru)".toRegex()
        private val WRITTEN_COMPACT_DATE =
            "(\\\\d{1,2})\\\\s*(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*(?:\\\\s*/\\\\s*[A-Z]*)?\\\\s*(\\\\d{2,4})".toRegex(RegexOption.IGNORE_CASE)
        private val MONTH_CODES = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
            "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
        )
        private val DOC_NUMBER_LABEL_PATTERN = "(?i)passport\\\\s*(?:card\\\\s*)?(?:no|number)|document\\\\s*(?:no|number)".toRegex()
        private val DOC_NUMBER_TOKEN = "\\\\b(?=[A-Z0-9]*\\\\d)[A-Z0-9]{6,12}\\\\b".toRegex()
        private val DOC_NUMBER_REJECT = setOf("GBR", "USA", "CAN", "AUS", "FRA", "DEU", "NO", "NO.")''')

rep('''        private val RECEIPT_LINE_PATTERN = """(?m)^\\s*.+?\\s+[0-9][0-9,.]*\\s*(?:EGP|USD|EUR|SAR|AED)?\\s*$""".toRegex(RegexOption.IGNORE_CASE)
''', '')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('HeuristicExtractor edits applied')
