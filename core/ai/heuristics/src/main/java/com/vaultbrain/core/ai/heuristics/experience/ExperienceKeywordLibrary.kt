package com.vaultbrain.core.ai.heuristics.experience

import com.vaultbrain.shared.intelligence.KeywordDictionary
import com.vaultbrain.shared.model.ExperienceId

/**
 * Keyword dictionary used to rank experiences against OCR text.
 *
 * Each experience maps to a list of keyword groups. A group is a set of synonymous
 * or closely-related words; matching any single word in the group counts once for that
 * group. This keeps the dictionary compact while supporting English, Arabic, and
 * common shorthand.
 *
 * Scoring is intentionally simple and runs fully on-device:
 *   - Each matched group adds +1 to the experience score.
 *   - A group that appears multiple times does not stack.
 *   - The registry can boost scores when a parser's hard rules also match.
 */
object ExperienceKeywordLibrary {

    /** Pre-compiled regexes for each experience group for fast, boundary-aware matching. */
    private val COMPILED_GROUPS: Map<String, List<Regex>> by lazy {
        KEYWORDS.mapValues { (_, groups) ->
            groups.map { group ->
                // Sort by length descending so longer phrases match before their substrings if there is overlap
                val sorted = group.sortedByDescending { it.length }
                val pattern = sorted.joinToString("|") { Regex.escape(it) }
                // Use explicit lookaround word boundaries that work across all Android versions
                Regex("(?i)(?<=^|[^\\p{L}\\p{N}])(?:$pattern)(?=[^\\p{L}\\p{N}]|$)")
            }
        }
    }

    /** Returns the keyword groups for a given experience id. */
    fun keywordsFor(experienceId: String): List<Set<String>> = KEYWORDS[experienceId].orEmpty()

    /**
     * Forces compilation of the keyword regexes ([COMPILED_GROUPS]) and the lens keyword
     * hierarchy (`KeywordDictionary.COMPILED_HIERARCHY`). Call from a background thread at
     * app start so the first capture does not pay the regex compilation cost.
     */
    fun warmUp() {
        COMPILED_GROUPS.size
        KeywordDictionary.detectHierarchy("warmup")
    }

    /**
     * Score all experiences against OCR text, returning ranked results.
     *
     * Scoring:
     * - Each matched keyword group adds +1
     * - A [frequencyBonus] map can boost frequently-used experiences (+2 for top-3, +1 for top-6)
     * - Results are sorted by score descending; only positive scores are returned
     *
     * @param ocrText The raw OCR text to score against
     * @param frequencyBonus Map of experienceId to save-count (from [UserExperienceFrequency])
     * @param topN Maximum number of results to return
     */
    fun quickScore(
        ocrText: String,
        frequencyBonus: Map<String, Int> = emptyMap(),
        topN: Int = 5
    ): List<ScoredExperience> {
        if (ocrText.isBlank()) return emptyList()

        // Pre-rank frequency entries to determine boost tiers
        val frequencyRank = frequencyBonus.entries
            .sortedByDescending { it.value }
            .mapIndexed { idx, entry -> entry.key to idx }
            .toMap()

        return COMPILED_GROUPS.entries
            .map { (experienceId, groupRegexes) ->
                val matchedGroups = mutableListOf<String>()
                groupRegexes.forEach { regex ->
                    val match = regex.find(ocrText)
                    if (match != null) {
                        matchedGroups.add(match.value)
                    }
                }

                val keywordScore = matchedGroups.size.toFloat()
                val freqBoost = when (frequencyRank[experienceId]) {
                    in 0..2 -> 2f   // top-3 most used
                    in 3..5 -> 1f   // top 4-6 most used
                    else -> 0f
                }

                ScoredExperience(
                    experienceId = experienceId,
                    score = keywordScore + freqBoost,
                    matchedKeywords = matchedGroups.map { it.lowercase() }.distinct()
                )
            }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .take(topN)
    }

    /**
     * Returns the specific keywords that matched for a given experience against the OCR text.
     * Useful for displaying highlighted "detected" terms to the user.
     */
    fun matchedKeywords(
        experienceId: String,
        ocrText: String
    ): List<DetectedKeyword> {
        if (ocrText.isBlank()) return emptyList()
        val groupRegexes = COMPILED_GROUPS[experienceId] ?: return emptyList()
        val results = mutableListOf<DetectedKeyword>()

        groupRegexes.forEachIndexed { groupIndex, regex ->
            // Use findAll to get all occurrences of this group's keywords
            regex.findAll(ocrText).forEach { matchResult ->
                results.add(
                    DetectedKeyword(
                        keyword = matchResult.value,
                        groupIndex = groupIndex,
                        category = classifyKeywordGroup(experienceId, groupIndex),
                        startIndex = matchResult.range.first,
                        length = matchResult.value.length
                    )
                )
            }
        }
        return results.distinctBy { it.keyword.lowercase() }
    }

    /** Classify a keyword group into a visual category for UI display. */
    private fun classifyKeywordGroup(experienceId: String, groupIndex: Int): KeywordCategory {
        // Group 0 is typically the document type identifier
        if (groupIndex == 0) return KeywordCategory.DOCUMENT_TYPE
        // Heuristic: look at common patterns across experience groups
        return when {
            groupIndex <= 1 -> KeywordCategory.IDENTIFIER
            groupIndex <= 3 -> KeywordCategory.DETAIL
            else -> KeywordCategory.CONTEXT
        }
    }

    /** A scored experience result from [quickScore]. */
    data class ScoredExperience(
        val experienceId: String,
        val score: Float,
        val matchedKeywords: List<String> = emptyList()
    )

    /** A keyword detected in OCR text, with position and category info. */
    data class DetectedKeyword(
        val keyword: String,
        val groupIndex: Int,
        val category: KeywordCategory,
        val startIndex: Int = -1,
        val length: Int = 0
    )

    /** Visual category for grouping detected keywords in the UI. */
    enum class KeywordCategory {
        DOCUMENT_TYPE,  // e.g., "receipt", "passport", "prescription"
        IDENTIFIER,     // e.g., amounts, IDs, reference numbers
        DETAIL,         // e.g., merchant, doctor, airline
        CONTEXT         // e.g., date stamps, status words
    }

    private val KEYWORDS: Map<String, List<Set<String>>> = mapOf(
        ExperienceId.EXPENSE to listOf(
            setOf("expense", "receipt", "فاتورة", "إيصال", "recibo", "ticket", "bon", "receip", "reciept", "scontrino", "quittung", "reçu", "سند"),
            setOf("total", "المجموع", "الإجمالي", "sum", "amount", "المبلغ", "grand total", "totale", "gesamt", "subtotal", "المجموع الكلي"),
            setOf("paid", "payment", "دفع", "cash", "credit card", "bank card", "credit", "visa", "mastercard", "debit", "amex", "نقدا", "بطاقة ائتمان", "بطاقة بنكية", "change", "tender"),
            setOf("merchant", "store", "shop", "سوق", "متجر", "supermarket", "restaurant", "مطعم", "cafe", "bakery", "pharmacy store", "hypermarket", "ميني ماركت"),
            setOf("cashier", "register", "pos", "terminal", "order no", "كاشير"),
            setOf("qty", "quantity", "price", "unit", "item", "items", "سعر", "كمية", "خصم", "discount"),
            setOf("thank you", "thanks for shopping", "welcome", "شكرا", "شكراً", "come again"),
            setOf("vat", "tax", "gst", "ضريبة", "service charge", "tip", "إكرامية"),
            setOf("change due", "receipt no", "till", "فاتورة ضريبية مبسطة", "فاتورة مبسطة")
        ),
        ExperienceId.INCOME to listOf(
            setOf("income", "salary", "wage", "payroll", "راتب", "مداخيل", "payslip", "pay stub", "paycheck", "مرتب", "أجر"),
            setOf("deposit", "transfer", "received", "credit note", "إيداع", "تحويل", "incoming transfer", "bank credit", "حوالة"),
            setOf("refund", "cashback", "استرداد", "إرجاع", "reimbursement", "reversal"),
            setOf("employer", "company", "employee", "net pay", "gross pay", "basic salary", "الراتب الأساسي"),
            setOf("allowance", "bonus", "overtime", "commission", "بدل", "مكافأة", "حافز"),
            setOf("deductions", "social security", "insurance contribution", "tax withheld", "استقطاعات"),
            setOf("payment date", "pay period", "month of", "شهر", "تاريخ الصرف"),
            setOf("dividend income", "rental income", "freelance income", "دخل إضافي")
        ),
        ExperienceId.SUBSCRIPTION to listOf(
            setOf("subscription", "subscribe", "اشتراك", "اشتراكات", "abonnement", "suscripción", "subsciption", "subscribtion"),
            setOf("monthly", "yearly", "annual", "renew", "renewal", "auto-renew", "recurring", "شهري", "سنوي", "تجديد", "تلقائي"),
            setOf("plan", "membership", "premium", "billing cycle", "فترة", "free trial", "trial ends", "خطة", "باقة"),
            setOf("netflix", "spotify", "youtube", "apple", "google", "prime", "chatgpt", "disney", "hulu", "hbo", "shahid", "starzplay", "osn", "amazon", "icloud", "openai", "microsoft 365", "adobe", "canva", "notion", "dropbox"),
            setOf("next billing", "billing date", "charged on", "payment method", "invoice for subscription", "الفاتورة"),
            setOf("cancel anytime", "manage subscription", "unsubscribe", "إلغاء الاشتراك"),
            setOf("family plan", "student plan", "individual plan", "duo", "bundle"),
            setOf("per month", "per year", "/mo", "/yr", "شهرياً", "سنوياً")
        ),
        ExperienceId.BILL to listOf(
            setOf("bill", "invoice", "فاتورة", "فاتوره", "facture", "factura", "rechnung", "fatura", "bill no", "bil"),
            setOf("due", "due date", "payment due", "مستحق", "deadline", "pay by", "before", "تاريخ الاستحقاق", "استحقاق"),
            setOf("account number", "invoice number", "reference", "رقم", "customer number", "رقم الحساب", "رقم الفاتورة", "رقم المرجع"),
            setOf("please pay", "amount due", "total due", "المبلغ المستحق", "balance", "outstanding", "payable"),
            setOf("billing period", "statement period", "from date", "to date", "فترة الفاتورة", "usage", "consumption", "استهلاك"),
            setOf("meter reading", "previous reading", "current reading", "قراءة العداد", "kwh"),
            setOf("late fee", "penalty", "غرامة تأخير", "disconnect", "reconnection"),
            setOf("pay online", "qr code", "barcode", "mobile payment", "vodafone cash", "instapay", "فوري", "fawry")
        ),
        ExperienceId.BUDGET_TRACKER to listOf(
            setOf("budget", "allowance", "limit", "allocation", "ميزانية", "ميزانيه", "budgeting", "spending plan"),
            setOf("spending limit", "target", "goal", "savings goal", "هدف", "توفير", "save"),
            setOf("category", "categories", "food", "transport", "entertainment", "housing", "فئات"),
            setOf("weekly budget", "monthly budget", "daily limit", "ميزانية شهرية"),
            setOf("spent", "remaining", "left", "over budget", "under budget", "متبقي"),
            setOf("envelope", "50/30/20", "zero based", "tracker")
        ),
        ExperienceId.INVOICE_FOR_WORK to listOf(
            setOf("invoice", "invoiced", "فاتورة", "فاتوره", "tax invoice", "commercial invoice", "proforma", "pro-forma", "فاتورة ضريبية"),
            setOf("client", "customer", "project", "services rendered", "consulting", "freelance", "عميل", "زبون", "خدمات", "استشارات"),
            setOf("tax id", "vat number", "company", "بيانات", "الرقم الضريبي", "سجل تجاري", "commercial registration", "cr number"),
            setOf("hours", "rate", "quantity", "qty", "unit price", "hourly rate", "ساعات", "سعر الوحدة"),
            setOf("bill to", "ship to", "invoice to", "issued to", "فاتورة إلى"),
            setOf("subtotal", "discount", "total amount", "net amount", "صافي", "الخصم"),
            setOf("payment terms", "net 30", "net 15", "due upon receipt", "شروط الدفع"),
            setOf("invoice date", "invoice no", "po number", "purchase order", "أمر شراء"),
            setOf("iban", "swift", "bank details", "due date", "تفاصيل البنك", "تاريخ الاستحقاق", "آيبان")
        ),
        ExperienceId.TAX_DOCUMENT to listOf(
            setOf("tax", "taxes", "vat", "gst", "ضريبة", "ضرائب", "القيمة المضافة", "tax return", "tax form", "withholding", "ضريبة الدخل"),
            setOf("deduction", "refund", "return", "declaration", "form", "إقرار", "إقرار ضريبي", "استقطاع", "exemption", "إعفاء"),
            setOf("fiscal year", "tax year", "taxpayer", "authority", "revenue", "السنة المالية", "مصلحة الضرائب", "هيئة الضرائب", "irs", "hmrc", "zatca", "eta"),
            setOf("w-2", "w2", "1099", "1040", "w-9", "tax slip", "t4", "p60", "p45"),
            setOf("taxable income", "gross income", "adjusted", "الدخل الخاضع"),
            setOf("tax identification", "tin", "ssn", "national tax number", "الرقم الضريبي"),
            setOf("filed", "filing date", "submission", "e-file", "تقديم"),
            setOf("assessment", "notice", "penalty", "interest", "غرامة ضريبية")
        ),
        ExperienceId.INVESTMENT_RECORD to listOf(
            setOf("investment", "portfolio", "stock", "shares", "equity", "stocks", "استثمار", "أسهم", "سهم", "محفظة", "securities", "brokerage"),
            setOf("crypto", "bitcoin", "ethereum", "wallet", "cryptocurrency", "btc", "eth", "usdt", "binance", "coinbase", "عملات رقمية"),
            setOf("dividend", "yield", "return", "profit", "loss", "balance", "أرباح", "عائد", "خسارة", "ربح"),
            setOf("buy", "sell", "trade", "order executed", "شراء", "بيع", "صفقة"),
            setOf("etf", "mutual fund", "index fund", "bond", "sukuk", "صندوق", "سندات", "صكوك"),
            setOf("ticker", "symbol", "isin", "cusip", "market", "exchange", "بورصة"),
            setOf("quantity", "price per share", "market value", "cost basis", "القيمة السوقية"),
            setOf("broker", "custodian", "account statement", "وسيط", "صندوق استثمار")
        ),
        ExperienceId.INSURANCE_PAYMENT to listOf(
            setOf("insurance", "premium", "policy", "تأمين", "بوليصة", "وثيقة تأمين", "insurer", "insured", "coverage"),
            setOf("payment", "receipt", "installment", "قسط", "دفعة", "قسط تأمين", "renewal premium"),
            setOf("life", "health", "car", "home", "travel", "medical", "تأمين الحياة", "تأمين صحي"),
            setOf("policy number", "certificate number", "رقم الوثيقة", "رقم البوليصة"),
            setOf("beneficiary", "مستفيد", "sum insured", "coverage amount", "مبلغ التأمين"),
            setOf("effective date", "expiry date", "valid until", "تاريخ السريان", "تاريخ الانتهاء"),
            setOf("claim", "مطالبة", "deductible", "copay", "تحمل"),
            setOf("axa", "allianz", "metlife", "aig", "bupa", "cigna", "tawuniya", "التعاونية", "medgulf", "globmed", "nextcare")
        ),
        ExperienceId.BANK_STATEMENT to listOf(
            setOf("bank statement", "account statement", "statement of account", "كشف حساب", "كشف حساب بنكي", "e-statement"),
            setOf("opening balance", "closing balance", "available balance", "الرصيد", "الرصيد الافتتاحي", "الرصيد الختامي"),
            setOf("iban", "swift", "bic", "account number", "رقم الحساب", "آيبان", "سويفت"),
            setOf("debit", "credit", "withdrawal", "deposit", "مدين", "دائن", "سحب", "إيداع"),
            setOf("transaction", "transactions", "transaction date", "value date", "حركة", "عمليات"),
            setOf("bank", "branch", "بنك", "فرع", "nbe", "cbe", "al ahly", "rajhi", "alahli", "snb", "qnb", "hsbc", "emirates nbd"),
            setOf("statement period", "from", "to", "فترة الكشف"),
            setOf("interest", "charges", "fees", "رسوم", "فوائد")
        ),
        ExperienceId.CREDIT_CARD_STATEMENT to listOf(
            setOf("credit card statement", "card statement", "كشف بطاقة", "كشف بطاقة ائتمان"),
            setOf("credit card", "بطاقة ائتمان", "visa", "mastercard", "amex", "card ending", "card number"),
            setOf("minimum payment", "الحد الأدنى للدفع", "minimum due", "min payment"),
            setOf("payment due date", "due date", "تاريخ الاستحقاق", "سداد"),
            setOf("credit limit", "available credit", "الحد الائتماني", "الرصيد المتاح"),
            setOf("purchases", "cash advance", "fees", "interest charge", "finance charge", "رسوم", "فوائد"),
            setOf("previous balance", "new balance", "total due", "الرصيد السابق", "الرصيد الجديد"),
            setOf("reward points", "cashback", "miles", "نقاط", "استرداد نقدي")
        ),
        ExperienceId.LOAN_PAYMENT to listOf(
            setOf("loan", "loan payment", "قرض", "قسط القرض", "personal loan", "car loan", "home loan", "mortgage payment"),
            setOf("installment", "instalment", "emi", "قسط", "أقساط", "monthly installment"),
            setOf("principal", "interest", "أصل المبلغ", "فائدة", "outstanding principal"),
            setOf("loan number", "loan account", "رقم القرض", "agreement number"),
            setOf("lender", "borrower", "مقرض", "مقترض", "bank", "finance company"),
            setOf("due date", "تاريخ الاستحقاق", "payment schedule", "جدول السداد", "amortization"),
            setOf("remaining balance", "outstanding balance", "الرصيد المتبقي", "payoff"),
            setOf("late payment", "penalty", "غرامة", "overdue")
        ),
        ExperienceId.GROCERY_LIST to listOf(
            setOf("grocery list", "groceries", "قائمة مشتريات", "قائمة التسوق", "مشتريات", "grocery", "to buy", "shopping list"),
            setOf("milk", "bread", "eggs", "rice", "sugar", "حليب", "خبز", "بيض", "أرز", "سكر", "cheese", "juice", "oil", "زيت"),
            setOf("fruits", "vegetables", "خضار", "فاكهة", "فواكه", "meat", "chicken", "لحم", "دجاج", "fish"),
            setOf("shampoo", "شامبو", "soap", "صابون", "detergent", "منظف", "tissues", "مناديل", "cleaning", "تنظيف"),
            setOf("supermarket", "بقالة", "سوبر ماركت", "سوق", "متجر", "محل", "carrefour", "كارفور", "lulu", "لولو", "spinneys", "سبينس", "metro market", "kazyon", "panda", "بنده", "tamimi", "التميمي", "costco", "walmart", "tesco"),
            setOf("kg", "grams", "liters", "pack", "pieces", "كيلو", "جرام", "علبة", "كيس")
        ),
        ExperienceId.GROCERY_RECEIPT to listOf(
            setOf("grocery", "groceries", "supermarket", "بقالة", "سوبر ماركت", "hypermarket", "carrefour", "lulu", "spinneys", "metro market", "kazyon", "panda", "tamimi", "walmart", "target", "costco", "aldi", "whole foods"),
            setOf("milk", "bread", "eggs", "rice", "sugar", "حليب", "خبز", "بيض", "أرز", "سكر", "cheese", "juice", "yogurt", "butter", "chicken", "beef", "meat", "fish", "coffee", "tea", "soda", "fruits", "vegetables", "خضار", "فواكه"),
            setOf("receipt", "فاتورة", "إيصال", "cashier", "register", "pos", "terminal", "كاشير"),
            setOf("total", "المجموع", "الإجمالي", "sum", "amount", "المبلغ", "paid", "payment", "دفع", "cash", "credit card", "bank card", "visa", "mastercard", "change", "نقدا", "بطاقة ائتمان", "بطاقة بنكية"),
            setOf("qty", "quantity", "price", "unit", "item", "items", "سعر", "كمية", "خصم", "discount", "kg", "grams", "كيلو", "vat", "tax", "ضريبة", "tax invoice", "فاتورة ضريبية")
        ),
        ExperienceId.PET_EXPENSE to listOf(
            setOf("pet", "dog", "cat", "حيوان أليف", "كلب", "قطة", "puppy", "kitten", "bird"),
            setOf("vet", "veterinary", "veterinarian", "طبيب بيطري", "عيادة بيطرية", "animal clinic", "pet clinic"),
            setOf("vaccination", "vaccine", "deworming", "لقاح", "تطعيم", "rabies"),
            setOf("pet food", "food", "litter", "طعام حيوانات", "grooming", "حلاقة", "boarding"),
            setOf("microchip", "chip number", "شريحة", "pet passport"),
            setOf("total", "amount", "receipt", "invoice", "فاتورة")
        ),
        ExperienceId.TUITION_FEE to listOf(
            setOf("tuition", "tuition fee", "school fee", "رسوم دراسية", "مصاريف دراسية", "مصروفات", "university fee"),
            setOf("school", "university", "college", "academy", "مدرسة", "جامعة", "كلية", "معهد"),
            setOf("student", "student id", "student name", "طالب", "رقم الطالب", "grade", "class", "semester", "term"),
            setOf("academic year", "العام الدراسي", "registration", "تسجيل", "enrollment"),
            setOf("installment", "قسط", "due date", "تاريخ الاستحقاق", "payment receipt"),
            setOf("amount", "total", "receipt", "paid", "إيصال", "سداد"),
            setOf("bus fee", "books", "uniform", "activities fee", "رسوم أتوبيس")
        ),
        ExperienceId.PRESCRIPTION to listOf(
            setOf("prescription", "prescribed", "script", "وصفة", "وصفة طبية", "روشتة", "receta", "ordonnance", "rezept"),
            setOf("medication", "medicine", "drug", "dosage", "dose", "tablet", "capsule", "دواء", "أقراص", "كبسولة", "شراب", "syrup", "cream", "ointment", "drops", "inhaler", "injection"),
            setOf("doctor", "physician", "pharmacy", "pharmacist", "صيدلية", "طبيب", "doctor signature", "dr.", "دكتور", "md"),
            setOf("rx", "℞", "refill", "take one", "times a day", "mg", "ml", "mcg", "iu", "مرة يوميا", "مرات"),
            setOf("morning", "evening", "before food", "after food", "صباحا", "مساء", "قبل الأكل", "بعد الأكل"),
            setOf("antibiotic", "painkiller", "مضاد حيوي", "مسكن", "vitamin", "فيتامين", "insulin", "أنسولين"),
            setOf("patient", "age", "gender", "المريض", "العمر", "date", "diagnosis"),
            setOf("dispense", "quantity", "30 tablets", "كورس", "duration", "مدة العلاج"),
            setOf("sig", "tab", "cap", "q8h", "عند اللزوم", "قبل النوم")
        ),
        ExperienceId.MEDICATION_SCHEDULE to listOf(
            setOf("medication schedule", "medication reminder", "pill reminder", "موعد دواء", "جرعات", "جدول الأدوية", "dose schedule"),
            setOf("morning", "afternoon", "evening", "night", "before meals", "after meals", "صباح", "ظهر", "مساء", "ليل", "قبل الطعام", "بعد الطعام"),
            setOf("daily", "weekly", "every", "hours", "tablets", "pills", "يوميا", "أسبوعيا", "كل", "ساعة", "قرص"),
            setOf("twice a day", "three times", "once daily", "bid", "tid", "qid", "od", "مرتين", "ثلاث مرات"),
            setOf("start date", "end date", "course", "duration", "تاريخ البدء", "مدة"),
            setOf("alarm", "reminder", "تذكير", "منبه", "pill box", "علبة أدوية"),
            setOf("mg", "ml", "units", "وحدة", "ملجم", "dose", "dosage", "جرعة")
        ),
        ExperienceId.LAB_RESULT to listOf(
            setOf("lab", "laboratory", "lab result", "test result", "analysis", "تحليل", "معمل", "مختبر", "نتيجة التحليل", "pathology", "lab report"),
            setOf("blood", "urine", "sugar", "cholesterol", "cbc", "wbc", "hemoglobin", "glucose", "دم", "بول", "سكر", "كوليسترول", "صورة دم", "platelets", "rbc", "hct", "creatinine", "urea", "alt", "ast", "tsh", "hba1c", "lipid", "triglycerides", "hdl", "ldl", "vitamin d", "b12", "ferritin", "psa", "uric acid"),
            setOf("reference range", "value", "high", "low", "positive", "negative", "normal", "النتيجة", "المعدل الطبيعي", "مرتفع", "منخفض", "طبيعي", "range", "units"),
            setOf("specimen", "sample", "fasting", "صائم", "عينة", "collected", "received"),
            setOf("patient name", "age", "gender", "accession", "lab number", "رقم العينة"),
            setOf("requested by", "referring doctor", "الطبيب المعالج", "dr."),
            setOf("report date", "collection date", "تاريخ التقرير", "printed"),
            setOf("lab name", "megalab", "alfa lab", "el mokhtabar", "المختبر", "البرج", "speed lab", "biolab")
        ),
        ExperienceId.HEALTH_INSURANCE_CLAIM to listOf(
            setOf("claim", "insurance claim", "reimbursement", "تعويض", "مطالبة", "مطالبة تأمين", "claim form", "claim number"),
            setOf("health insurance", "medical insurance", "provider", "member id", "group number", "تأمين صحي", "تأمين طبي", "رقم العضوية"),
            setOf("approved", "denied", "pending", "benefit", "copay", "deductible", "موافقة", "مرفوض", "قيد المراجعة", "تحمل", "نسبة التحمل"),
            setOf("pre-approval", "preauthorization", "pre auth", "موافقة مسبقة", "approval number"),
            setOf("network", "in-network", "out of network", "شبكة", "provider network"),
            setOf("explanation of benefits", "eob", "coverage", "تغطية", "beneficiary"),
            setOf("tpa", "third party", "globmed", "mednet", "nextcare", "daman", "thiqa", "bupa", "التعاونية", "tawuniya"),
            setOf("diagnosis", "icd", "procedure", "service date", "تاريخ الخدمة")
        ),
        ExperienceId.HEALTH_INSURANCE_CARD to listOf(
            setOf("insurance card", "بطاقة تأمين", "بطاقة التأمين", "health insurance card", "بطاقة التأمين الصحي", "التأمين الصحي"),
            setOf("member id", "رقم العضوية", "policy number", "رقم البوليصة", "group number", "رقم المجموعة"),
            setOf("copay", "co-pay", "تحمل", "نسبة التحمل", "deductible", "network", "شبكة"),
            setOf("insurer", "شركة التأمين", "tpa", "bupa", "tawuniya", "التعاونية", "medgulf", "nextcare", "globmed")
        ),
        ExperienceId.VACCINATION_RECORD to listOf(
            setOf("vaccination", "vaccine", "immunization", "injection", "shot", "لقاح", "تطعيم", "شهادة تطعيم", "vaccination card", "بطاقة تطعيم"),
            setOf("covid", "hepatitis", "flu", "mmr", "tdap", "hpv", "dose", "booster", "كورونا", "كوفيد", "التهاب الكبد", "الانفلونزا", "إنفلونزا", "الحصبة", "جرعة", "جرعة تنشيطية"),
            setOf("first dose", "second dose", "third dose", "الجرعة الأولى", "الجرعة الثانية"),
            setOf("batch number", "lot number", "رقم التشغيلة", "manufacturer", "pfizer", "astrazeneca", "moderna", "sinopharm"),
            setOf("vaccination center", "health center", "مركز صحي", "clinic", "ministry of health", "وزارة الصحة"),
            setOf("date", "next dose due", "تاريخ", "الجرعة القادمة", "valid until"),
            setOf("child", "infant", "طفل", "schedule", "national immunization")
        ),
        ExperienceId.DOCTOR_NOTE to listOf(
            setOf("doctor note", "medical note", "clinical note", "consultation", "زيارة طبيب", "تقرير طبي", "progress note", "visit summary", "discharge summary"),
            setOf("diagnosis", "symptoms", "treatment", "advice", "follow up", "diagnostic", "تشخيص", "أعراض", "علاج", "توصيات", "متابعة"),
            setOf("clinic", "hospital", "medical center", "physician", "specialist", "عيادة", "مستشفى", "مركز طبي", "استشاري", "أخصائي"),
            setOf("patient", "chief complaint", "history", "examination", "المريض", "الشكوى", "الفحص"),
            setOf("vitals", "blood pressure", "bp", "pulse", "temperature", "ضغط الدم", "النبض", "الحرارة"),
            setOf("prescribed", "medication", "lab ordered", "x-ray", "mri", "أشعة", "تحاليل"),
            setOf("sick leave", "اجازة مرضية", "إجازة مرضية", "rest for", "days off work"),
            setOf("next visit", "review in", "weeks", "الموعد القادم")
        ),
        ExperienceId.FITNESS_TRACKER to listOf(
            setOf("fitness", "workout", "gym", "exercise", "training", "steps", "calories", "burned", "لياقة", "تمارين", "جيم", "نادي", "خطوات", "سعرات"),
            setOf("weight", "height", "bmi", "body fat", "muscle", "reps", "sets", "وزن", "طول", "كتلة الجسم", "عضلات", "عدات"),
            setOf("running", "cycling", "swimming", "yoga", "diet", "nutrition", "جري", "سباحة", "يوجا", "دايت", "تغذية", "walking", "مشي"),
            setOf("cardio", "strength", "hiit", "crossfit", "pilates", "كارديو"),
            setOf("personal trainer", "coach", "مدرب", "program", "routine", "plan"),
            setOf("heart rate", "bpm", "resting", "active minutes", "نبض"),
            setOf("protein", "carbs", "macros", "بروتين", "كربوهيدرات", "calorie intake"),
            setOf("progress", "goal weight", "target", "هدف", "measurements", "قياسات")
        ),
        ExperienceId.DENTAL_VISIT to listOf(
            setOf("dental", "dentist", "tooth", "teeth", "أسنان", "طبيب أسنان", "عيادة أسنان", "dental clinic"),
            setOf("filling", "crown", "root canal", "extraction", "حشو", "تلبيس", "تاج", "خلع", "عصب", "سحب عصب"),
            setOf("cleaning", "scaling", "polishing", "تنظيف", "جير", "whitening", "تبييض"),
            setOf("braces", "orthodontic", "aligner", "invisalign", "تقويم", "تقويم الأسنان"),
            setOf("implant", "زراعة", "denture", "طقم", "bridge", "تركيبة"),
            setOf("x-ray", "panoramic", "أشعة", "cavity", "تسوس", "gum", "لثة"),
            setOf("next appointment", "follow up", "موعد", "review"),
            setOf("cost", "estimate", "treatment plan", "خطة علاج")
        ),
        ExperienceId.OPTICAL_PRESCRIPTION to listOf(
            setOf("optical", "glasses", "spectacles", "نظارة", "نظارات", "عيون", "eye", "eyes"),
            setOf("prescription", "rx", "وصفة نظارة", "وصفة", "lens prescription"),
            setOf("sphere", "sph", "cylinder", "cyl", "axis", "add", "prism"),
            setOf("od", "os", "right eye", "left eye", "العين اليمنى", "العين اليسرى", "re", "le"),
            setOf("myopia", "hyperopia", "astigmatism", "قصر نظر", "طول نظر", "استجماتيزم", "presbyopia"),
            setOf("contact lenses", "عدسات", "عدسات لاصقة", "frames", "إطار"),
            setOf("optician", "optometrist", "أخصائي بصريات", "optics", "بصريات", "eye exam", "فحص نظر"),
            setOf("pd", "pupillary distance", "vision", "20/20", "6/6", "visual acuity")
        ),
        ExperienceId.ALLERGY_RECORD to listOf(
            setOf("allergy", "allergies", "allergic", "حساسية", "allergen", "allergens", "مسببات الحساسية"),
            setOf("peanut", "nuts", "shellfish", "dairy", "gluten", "فول سوداني", "مكسرات", "لبن", "جلوتين", "dust", "غبار", "pollen", "حبوب اللقاح"),
            setOf("penicillin", "بنسلين", "drug allergy", "حساسية دواء", "medication allergy"),
            setOf("reaction", "anaphylaxis", "hives", "rash", "طفح", "شرى", "صدمة", "swelling", "تورم"),
            setOf("antihistamine", "epipen", "epinephrine", "مضاد الهيستامين", "أدرينالين", "adrenaline"),
            setOf("skin test", "ige", "اختبار الحساسية", "allergy test", "patch test"),
            setOf("severe", "mild", "moderate", "شديدة", "خفيفة", "avoid", "تجنب"),
            setOf("immunologist", "allergist", "أخصائي حساسية")
        ),
        ExperienceId.PHYSIOTHERAPY_PLAN to listOf(
            setOf("physiotherapy", "physical therapy", "علاج طبيعي", "physio", "pt", "rehabilitation", "تأهيل", "rehab"),
            setOf("session", "sessions", "جلسة", "جلسات", "treatment plan", "خطة علاج"),
            setOf("exercise", "exercises", "stretching", "تمارين", "تمدد", "strengthening", "تقوية"),
            setOf("back pain", "neck", "shoulder", "knee", "ألم الظهر", "الرقبة", "الكتف", "الركبة", "spine", "عمود فقري"),
            setOf("injury", "sprain", "strain", "إصابة", "التواء", "fracture", "كسر", "post-surgery", "بعد العملية"),
            setOf("ultrasound", "electrotherapy", "heat", "ice", "موجات", "تحفيز كهربائي", "كمادات"),
            setOf("therapist", "physiotherapist", "أخصائي", "معالج"),
            setOf("range of motion", "mobility", "مدى الحركة", "posture", "وضعية")
        ),
        ExperienceId.MENTAL_HEALTH_NOTE to listOf(
            setOf("mental health", "صحة نفسية", "الصحة النفسية", "therapy", "therapist", "معالج نفسي"),
            setOf("psychologist", "psychiatrist", "طبيب نفسي", "أخصائي نفسي", "counselor", "مرشد"),
            setOf("counseling", "إرشاد", "psychotherapy", "cbt", "علاج معرفي", "علاج سلوكي"),
            setOf("anxiety", "depression", "قلق", "اكتئاب", "stress", "ضغط نفسي", "panic", "هلع"),
            setOf("mood", "sleep", "مزاج", "نوم", "insomnia", "أرق", "journal", "feelings", "مشاعر"),
            setOf("session", "جلسة", "appointment", "موعد", "progress", "تقدم"),
            setOf("medication", "antidepressant", "مضاد الاكتئاب", "ssri", "dose"),
            setOf("self care", "mindfulness", "meditation", "تأمل", "breathing", "تنفس")
        ),
        ExperienceId.BLOOD_DONATION to listOf(
            setOf("blood donation", "donate blood", "تبرع بالدم", "التبرع بالدم", "donor", "متبرع", "donation"),
            setOf("blood bank", "بنك الدم", "blood center", "مركز التبرع", "red cross", "الهلال الأحمر", "red crescent"),
            setOf("blood type", "blood group", "فصيلة الدم", "زمرة الدم", "a+", "b+", "o+", "ab+", "a-", "b-", "o-", "ab-", "rh"),
            setOf("hemoglobin", "hb", "هيموجلوبين", "screening", "فحص"),
            setOf("pint", "unit", "وحدة دم", "450ml", "450 ml", "bag"),
            setOf("donation date", "تاريخ التبرع", "next eligible", "eligible again", "التبرع القادم"),
            setOf("donor card", "بطاقة متبرع", "donor id", "certificate of appreciation", "شهادة شكر"),
            setOf("plasma", "بلازما", "platelets", "صفائح", "apheresis")
        ),
        ExperienceId.FLIGHT_TICKET to listOf(
            setOf("flight", "airline", "ticket", "plane", "boarding", "رحلة", "طيران", "تذكرة", "airways", "air lines", "تذكرة طيران"),
            setOf("departure", "arrival", "from", "to", "terminal", "gate", "pnr", "e-ticket", "المغادرة", "الوصول", "من", "إلى", "البوابة", "مبنى"),
            setOf("passenger", "booking reference", "flight number", "economy", "business", "مسافر", "رقم الحجز", "رقم الرحلة", "الدرجة السياحية", "رجال الأعمال", "first class"),
            setOf("baggage", "cabin", "check-in", "itinerary", "أمتعة", "شنط", "تسجيل", "وزن", "kg", "23kg", "carry on", "hand luggage"),
            setOf("airport", "مطار", "iata", "jfk", "lhr", "dxb", "cai", "ruh", "jed", "cdg", "fra", "ist"),
            setOf("emirates", "qatar airways", "egyptair", "saudia", "turkish airlines", "lufthansa", "british airways", "air france", "etihad", "flydubai", "air arabia", "flynas", "ryanair", "easyjet", "wizz"),
            setOf("fare", "taxes", "total amount", "الأجرة", "السعر", "reissue", "refundable", "non-refundable"),
            setOf("stopover", "layover", "transit", "direct", "مباشرة", "ترانزيت", "توقف")
        ),
        ExperienceId.HOTEL_BOOKING to listOf(
            setOf("hotel", "booking", "reservation", "فندق", "حجز", "resort", "منتجع", "hostel", "apartment", "airbnb", "شقة"),
            setOf("check in", "check out", "nights", "room", "guest", "confirmation", "تسجيل الوصول", "المغادرة", "ليال", "غرفة", "نزيل", "تأكيد"),
            setOf("booking reference", "reservation number", "address", "total", "رقم الحجز", "العنوان", "الإجمالي", "confirmation number", "pin"),
            setOf("single", "double", "twin", "suite", "king", "queen", "مفردة", "مزدوجة", "جناح", "standard", "deluxe"),
            setOf("breakfast", "half board", "all inclusive", "إفطار", "شامل", "meal plan"),
            setOf("free cancellation", "non-refundable", "cancellation policy", "إلغاء مجاني", "سياسة الإلغاء"),
            setOf("adults", "children", "guests", "بالغين", "أطفال", "2 adults"),
            setOf("booking.com", "expedia", "hotels.com", "agoda", "trivago", "almosafer", "flyin", "rehlat", "المواساة"),
            setOf("star", "5 star", "rating", "نجوم", "property"),
            setOf("voucher", "no show", "city tax")
        ),
        ExperienceId.BOARDING_PASS to listOf(
            setOf("boarding pass", "boarding", "صعود الطائرة", "بطاقة صعود", "بطاقة الصعود", "mobile boarding pass"),
            setOf("gate", "seat", "boarding time", "zone", "group", "barcode", "البوابة", "المقعد", "وقت الصعود", "منطقة"),
            setOf("sequence", "seq", "boards at", "gate closes", "يغلق الباب"),
            setOf("flight", "رحلة", "date", "departure", "from", "to"),
            setOf("name", "passenger", "مسافر", "mr", "ms", "mrs"),
            setOf("qr", "scan", "security", "أمن", "تفتيش"),
            setOf("airport", "مطار", "iata", "jfk", "lhr", "dxb", "cai", "ruh", "jed", "cdg", "fra", "ist"),
            setOf("e-ticket", "etkt", "seq no", "class")
        ),
        ExperienceId.TRAVEL_EXPENSE to listOf(
            setOf("travel expense", "taxi", "uber", "transport", "train", "bus", "metro", "fuel", "مصاريف سفر", "تاكسي", "أوبر", "مواصلات", "مترو", "careem", "كريم", "bolt", "indriver", "lyft"),
            setOf("trip", "journey", "fare", "ticket", "receipt", "مشوار", "رحلة", "أجرة", "تذكرة", "إيصال"),
            setOf("driver", "سائق", "captain", "كابتن", "plate", "car"),
            setOf("pickup", "dropoff", "drop off", "destination", "الوجهة", "انطلاق", "وصول"),
            setOf("distance", "km", "miles", "duration", "المسافة", "المدة"),
            setOf("toll", "رسوم", "surge", "promo", "discount", "خصم"),
            setOf("tourism", "سياحة", "per diem", "بدل سفر", "expense report", "reimbursement")
        ),
        ExperienceId.VISA to listOf(
            setOf("visa", "entry visa", "residence visa", "تأشيرة", "فيزا", "e-visa", "evisa", "schengen", "شنغن", "tourist visa", "visit visa"),
            setOf("passport number", "valid until", "issue date", "embassy", "consulate", "رقم الجواز", "صالحة حتى", "تاريخ الإصدار", "سفارة", "قنصلية"),
            setOf("visa number", "type", "single entry", "multiple entry", "رقم التأشيرة", "دخول لمرة واحدة", "دخول متعدد", "duration of stay"),
            setOf("nationality", "جنسية", "date of birth", "تاريخ الميلاد", "surname", "given names"),
            setOf("visa fee", "رسوم", "application", "طلب", "reference number"),
            setOf("port of entry", "ميناء الدخول", "border", "immigration", "جوازات", "الهجرة"),
            setOf("sponsor", "كفيل", "host", "invitation", "دعوة"),
            setOf("b1/b2", "b1", "b2", "category c", "work visa", "student visa")
        ),
        ExperienceId.ITINERARY to listOf(
            setOf("itinerary", "schedule", "plan", "program", "برنامج الرحلة", "خطة السفر", "trip plan", "travel plan"),
            setOf("day 1", "day 2", "visit", "tour", "activity", "sightseeing", "اليوم الأول", "زيارة", "جولة", "نشاط", "معالم"),
            setOf("morning", "afternoon", "evening", "صباحا", "مساء", "free time", "وقت حر"),
            setOf("museum", "متحف", "beach", "شاطئ", "shopping", "تسوق", "restaurant", "مطعم"),
            setOf("transfer", "pickup", "الانتقال", "guide", "مرشد", "meeting point"),
            setOf("departure", "arrival", "flight", "hotel", "الفندق", "الطيران"),
            setOf("destination", "city", "الوجهة", "المدينة", "country")
        ),
        ExperienceId.CAR_RENTAL to listOf(
            setOf("car rental", "rental agreement", "vehicle rental", "تأجير سيارة", "إيجار سيارة", "rent a car", "rent-a-car"),
            setOf("pick up", "drop off", "rental period", "driver", "license plate", "الاستلام", "التسليم", "فترة الإيجار", "السائق", "لوحة"),
            setOf("hertz", "avis", "europcar", "sixt", "budget", "enterprise", "yelo", "theeb", "ذيب", "يلو", "lumirental"),
            setOf("deposit", "تأمين مسترد", "insurance", "cdw", "excess", "الوديعة"),
            setOf("mileage", "unlimited km", "كيلومترات", "fuel policy", "full to full"),
            setOf("vehicle", "model", "sedan", "suv", "المركبة", "الموديل", "economy"),
            setOf("daily rate", "السعر اليومي", "total", "additional driver", "سائق إضافي"),
            setOf("return date", "تاريخ الإرجاع", "airport pickup", "station")
        ),
        ExperienceId.TRAIN_TICKET to listOf(
            setOf("train", "train ticket", "railway", "rail", "قطار", "تذكرة قطار", "سكة حديد", "eurostar", "amtrak", "sncf", "deutsche bahn", "trenitalia", "renfe"),
            setOf("departure", "arrival", "المغادرة", "الوصول", "platform", "رصيف", "track"),
            setOf("coach", "carriage", "عربة", "seat", "مقعد", "berth", "cabin", "مقصورة"),
            setOf("first class", "second class", "درجة أولى", "درجة ثانية", "sleeper", "نوم"),
            setOf("station", "محطة", "from", "to", "من", "إلى"),
            setOf("booking reference", "pnr", "رقم الحجز", "ticket number", "رقم التذكرة"),
            setOf("fare", "أجرة", "price", "adult", "child", "بالغ", "طفل")
        ),
        ExperienceId.BUS_TICKET to listOf(
            setOf("bus", "bus ticket", "coach", "حافلة", "باص", "اتوبيس", "أتوبيس", "تذكرة باص", "go bus", "go-bus", "blue bus", "super jet", "flixbus", "greyhound"),
            setOf("departure", "arrival", "المغادرة", "الوصول", "station", "terminal", "محطة", "موقف"),
            setOf("seat", "مقعد", "seat number", "رقم المقعد"),
            setOf("from", "to", "من", "إلى", "route", "خط السير"),
            setOf("ticket number", "رقم التذكرة", "booking", "حجز", "reference"),
            setOf("passenger", "مسافر", "date", "time", "التاريخ", "الوقت"),
            setOf("fare", "price", "أجرة", "السعر", "paid")
        ),
        ExperienceId.FERRY_TICKET to listOf(
            setOf("ferry", "ferry ticket", "عبارة", "معدية", "تذكرة عبارة", "boat", "قارب", "ship", "سفينة", "cruise ferry"),
            setOf("departure", "arrival", "المغادرة", "الوصول", "port", "ميناء", "harbor", "harbour"),
            setOf("vehicle", "car", "مركبة", "passenger", "راكب", "foot passenger"),
            setOf("deck", "سطح", "cabin", "مقصورة", "seat", "مقعد"),
            setOf("sailing", "رحلة بحرية", "schedule", "مواعيد", "time"),
            setOf("ticket number", "رقم التذكرة", "booking", "حجز", "reference"),
            setOf("fare", "أجرة", "price", "route", "خط")
        ),
        ExperienceId.TRAVEL_INSURANCE to listOf(
            setOf("travel insurance", "trip insurance", "تأمين السفر", "تأمين سفر", "schengen insurance", "تأمين شنغن"),
            setOf("policy", "وثيقة", "بوليصة", "policy number", "رقم الوثيقة", "certificate", "شهادة تأمين"),
            setOf("coverage", "التغطية", "medical", "طبي", "emergency", "طوارئ", "evacuation", "إخلاء"),
            setOf("trip cancellation", "إلغاء الرحلة", "lost baggage", "فقدان الأمتعة", "delay", "تأخير"),
            setOf("destination", "الوجهة", "countries", "دول", "schengen", "europe"),
            setOf("valid from", "valid until", "سارية من", "صالحة حتى", "travel dates", "تواريخ السفر"),
            setOf("insured", "المؤمن عليه", "beneficiary", "مستفيد", "passport"),
            setOf("premium", "القسط", "amount", "claim", "مطالبة", "assistance", "مساعدة")
        ),
        ExperienceId.TRAVEL_CHECKLIST to listOf(
            setOf("travel checklist", "checklist", "قائمة السفر", "قائمة تجهيزات", "packing list", "قائمة الحقيبة"),
            setOf("passport", "جواز", "visa", "تأشيرة", "tickets", "تذاكر", "booking", "حجوزات"),
            setOf("charger", "شاحن", "adapter", "محول", "power bank", "باور بانك", "headphones", "سماعات"),
            setOf("clothes", "ملابس", "jacket", "جاكيت", "shoes", "حذاء", "umbrella", "مظلة"),
            setOf("medications", "أدوية", "first aid", "إسعافات", "toiletries", "أدوات نظافة"),
            setOf("documents", "مستندات", "copies", "نسخ", "insurance", "تأمين", "money", "نقود", "currency", "عملة"),
            setOf("pack", "suitcase", "حقيبة", "شنطة", "luggage", "أمتعة")
        ),
        ExperienceId.AIRPORT_LOUNGE to listOf(
            setOf("lounge", "airport lounge", "صالة", "صالة المطار", "vip lounge", "صالة كبار"),
            setOf("priority pass", "loungekey", "dragonpass", "plaza premium", "marhaba", "مرحبا", "ahlan", "أهلاً"),
            setOf("access", "دخول", "entry", "complimentary", "مجاني", "guest", "ضيف"),
            setOf("terminal", "مبنى", "gate", "البوابة", "airport", "مطار"),
            setOf("membership", "عضوية", "card", "بطاقة", "visits remaining", "زيارات"),
            setOf("wifi", "واي فاي", "shower", "دش", "buffet", "بوفيه", "refreshments", "مرطبات"),
            setOf("flight", "departure", "المغادرة", "boarding")
        ),
        ExperienceId.PASSPORT to listOf(
            setOf("passport", "جواز", "جواز سفر", "passeport", "pasaporte", "reisepass", "passport no", "جواز السفر"),
            setOf("nationality", "date of birth", "place of birth", "expiry", "issue date", "الجنسية", "تاريخ الميلاد", "مكان الميلاد", "تاريخ الانتهاء", "تاريخ الإصدار"),
            setOf("passport number", "mrz", "country code", "authority", "رقم الجواز", "رمز الدولة", "جهة الإصدار", "سلطة الإصدار"),
            setOf("surname", "given name", "name", "اللقب", "الاسم", "middle name", "father name", "full name", "الاسم الكامل"),
            setOf("sex", "gender", "الجنس", "male", "female", "ذكر", "أنثى"),
            setOf("p<", "passport type", "personal number", "الرقم الشخصي"),
            setOf("signature", "التوقيع", "photo", "صورة", "biometric"),
            setOf("renewal", "تجديد", "valid", "صالح", "expires")
        ),
        ExperienceId.ID_CARD to listOf(
            setOf("id card", "identity card", "identity", "national id", "بطاقة هوية", "هوية", "البطاقة الشخصية", "بطاقة الأحوال", "الهوية الوطنية", "emirates id", "الهوية الإماراتية"),
            setOf("id number", "personal number", "citizen", "national number", "الرقم القومي", "رقم الهوية", "رقم البطاقة", "الرقم الشخصي"),
            setOf("name", "الاسم", "full name", "الاسم الكامل", "address", "العنوان"),
            setOf("date of birth", "تاريخ الميلاد", "gender", "الجنس", "nationality", "الجنسية"),
            setOf("expiry", "issue", "تاريخ الانتهاء", "تاريخ الإصدار", "valid until", "صالحة حتى"),
            setOf("civil registry", "السجل المدني", "الأحوال المدنية", "civil affairs"),
            setOf("photo", "صورة", "signature", "توقيع", "chip", "شريحة")
        ),
        ExperienceId.DRIVERS_LICENSE to listOf(
            setOf("driver license", "driving licence", "رخصة قيادة", "رخصة", "drivers license", "driving license", "رخصة السياقة", "رخصة القيادة"),
            setOf("license number", "class", "category", "valid from", "valid until", "رقم الرخصة", "الفئة", "صالحة من", "صالحة حتى", "سارية"),
            setOf("traffic", "motor vehicle", "issuing authority", "المرور", "إدارة المرور", "جهة الإصدار", "dmv", "rta"),
            setOf("name", "date of birth", "الاسم", "تاريخ الميلاد", "address", "العنوان"),
            setOf("private", "professional", "خاصة", "مهنية", "motorcycle", "دراجة نارية", "heavy", "ثقيل"),
            setOf("restrictions", "conditions", "قيود", "glasses", "نظارات", "endorsement"),
            setOf("renewal", "تجديد", "test", "اختبار", "expiry", "انتهاء")
        ),
        ExperienceId.RESIDENCE_PERMIT to listOf(
            setOf("residence permit", "residency", "iqama", "إقامة", "تصريح إقامة", "residence card", "بطاقة إقامة", "permesso di soggiorno", "aufenthaltstitel"),
            setOf("permit number", "valid until", "sponsor", "employer", "رقم الإقامة", "صالحة حتى", "كفيل", "صاحب العمل", "جهة العمل"),
            setOf("profession", "المهنة", "occupation", "job title", "المسمى الوظيفي"),
            setOf("passport number", "رقم الجواز", "border number", "رقم الحدود", "nationality", "الجنسية"),
            setOf("expiry", "renewal", "انتهاء", "تجديد", "absher", "أبشر", "muqeem", "مقيم"),
            setOf("work permit", "تصريح عمل", "labor", "العمل", "ministry"),
            setOf("dependent", "مرافق", "تابع", "family", "عائلة")
        ),
        ExperienceId.BUSINESS_CARD to listOf(
            setOf("business card", "name card", "carte de visite", "كارت", "بطاقة عمل", "كرت شخصي", "كارت شخصي", "visiting card"),
            setOf("phone", "email", "mobile", "company", "title", "position", "website", "هاتف", "جوال", "بريد", "شركة", "منصب", "موقع", "tel", "mob", "fax"),
            setOf("www", "linkedin", "ceo", "manager", "engineer", "مدير", "مهندس", "director", "consultant", "مستشار"),
            setOf("address", "العنوان", "office", "مكتب", "po box", "صندوق بريد"),
            setOf("co.", "ltd", "llc", "inc", "gmbh", "ذ.م.م", "شركة", "est.", "مؤسسة"),
            setOf("sales", "marketing", "مبيعات", "تسويق", "department", "قسم"),
            setOf("ext", "مباشر", "direct", "landline", "أرضي")
        ),
        ExperienceId.BIRTH_CERTIFICATE to listOf(
            setOf("birth certificate", "certificate of birth", "شهادة ميلاد", "قيد ميلاد", "سجل ميلاد", "acte de naissance", "geburtsurkunde"),
            setOf("newborn", "مولود", "مولودة", "child name", "اسم الطفل", "baby", "name", "الاسم", "full name", "الاسم الكامل"),
            setOf("date of birth", "تاريخ الميلاد", "place of birth", "مكان الميلاد", "born on", "born in"),
            setOf("father", "الأب", "mother", "الأم", "parents", "الوالدان"),
            setOf("registration number", "رقم القيد", "registry", "السجل", "civil registry", "الأحوال المدنية", "السجل المدني"),
            setOf("certificate number", "رقم الشهادة", "issue date", "تاريخ الإصدار"),
            setOf("sex", "gender", "الجنس", "male", "female", "ذكر", "أنثى", "nationality", "الجنسية")
        ),
        ExperienceId.MARRIAGE_CERTIFICATE to listOf(
            setOf("marriage certificate", "marriage contract", "عقد زواج", "قسيمة زواج", "شهادة زواج", "wedding certificate", "acte de mariage"),
            setOf("groom", "العريس", "bride", "العروس", "husband", "الزوج", "wife", "الزوجة"),
            setOf("marriage date", "تاريخ الزواج", "date of marriage", "place", "مكان", "solemnized"),
            setOf("witness", "شاهد", "شهود", "witnesses", "officiant", "مأذون", "المأذون"),
            setOf("certificate number", "رقم العقد", "registration", "قيد", "سجل"),
            setOf("dowry", "مهر", "صداق", "mahr"),
            setOf("civil registry", "الأحوال المدنية", "court", "محكمة", "ministry of justice", "وزارة العدل")
        ),
        ExperienceId.NOTARIZED_DOCUMENT to listOf(
            setOf("notary", "notarized", "notarization", "كاتب عدل", "توثيق", "موثق", "notary public", "التصديق"),
            setOf("power of attorney", "توكيل", "وكالة", "poa", "authorization", "تفويض"),
            setOf("sworn", "affidavit", "إقرار", "تعهد", "declaration", "acknowledgment", "إشهاد"),
            setOf("seal", "ختم", "stamp", "طابع", "signature", "توقيع", "signed before me"),
            setOf("apostille", "أبوستيل", "authentication", "تصديق", "attestation", "مصادقة"),
            setOf("document number", "رقم المحرر", "register", "سجل", "volume", "page"),
            setOf("date", "تاريخ", "appeared", "حضر", "parties", "أطراف")
        ),
        ExperienceId.TAX_RESIDENCY_CERTIFICATE to listOf(
            setOf("tax residency", "tax residence", "شهادة الإقامة الضريبية", "إقامة ضريبية", "tax residence certificate", "trc"),
            setOf("certificate", "شهادة", "certificate number", "رقم الشهادة"),
            setOf("tax authority", "مصلحة الضرائب", "هيئة الضرائب", "zatca", "fta", "irs", "hmrc", "الهيئة العامة للزكاة"),
            setOf("resident", "مقيم", "domicile", "موطن", "for tax purposes", "لأغراض ضريبية"),
            setOf("tax year", "السنة الضريبية", "period", "فترة", "calendar year"),
            setOf("tin", "tax identification", "الرقم الضريبي", "رقم التعريف الضريبي"),
            setOf("double taxation", "ازدواج ضريبي", "treaty", "اتفاقية", "convention"),
            setOf("issue date", "تاريخ الإصدار", "valid until", "صالحة حتى")
        ),
        ExperienceId.PROFESSIONAL_LICENSE to listOf(
            setOf("professional license", "practice license", "ترخيص مهني", "رخصة مزاولة", "مزاولة المهنة", "licence to practice"),
            setOf("license number", "رقم الترخيص", "registration number", "رقم القيد", "membership number"),
            setOf("engineer", "مهندس", "doctor", "طبيب", "lawyer", "محامي", "accountant", "محاسب", "nurse", "ممرض", "pharmacist", "صيدلي"),
            setOf("syndicate", "نقابة", "association", "جمعية", "board", "مجلس", "council", "هيئة", "sce", "scm"),
            setOf("issue date", "تاريخ الإصدار", "expiry", "انتهاء", "renewal", "تجديد", "valid until", "سارية حتى"),
            setOf("specialty", "تخصص", "classification", "تصنيف", "grade", "درجة"),
            setOf("holder", "حامل الرخصة", "name", "الاسم", "id number", "رقم الهوية")
        ),
        ExperienceId.WARRANTY to listOf(
            setOf("warranty", "guarantee", "ضمان", "garantie", "garantía", "ضمان المنتج", "warrantly", "warrany"),
            setOf("warranty card", "warranty certificate", "valid until", "warranty period", "بطاقة الضمان", "شهادة الضمان", "فترة الضمان", "ساري حتى", "ضمان لمدة"),
            setOf("serial number", "model", "product", "purchase date", "repair", "الرقم التسلسلي", "سيريال", "الموديل", "المنتج", "تاريخ الشراء", "صيانة", "إصلاح"),
            setOf("year warranty", "1 year", "2 years", "سنة", "سنتين", "lifetime", "مدى الحياة", "limited warranty"),
            setOf("manufacturer", "الشركة المصنعة", "brand", "الماركة", "samsung", "lg", "sony", "apple", "hp", "dell", "bosch", "philips", "tornado", "unionaire", "carrier"),
            setOf("authorized service", "مركز الصيانة", "خدمة العملاء", "customer service", "hotline", "الخط الساخن"),
            setOf("defect", "عيب", "malfunction", "عطل", "replacement", "استبدال", "exchange"),
            setOf("terms and conditions", "الشروط والأحكام", "void", "يلغي الضمان", "exclusions")
        ),
        ExperienceId.APPLIANCE_MANUAL to listOf(
            setOf("manual", "user guide", "instructions", "دليل", "تعليمات", "دليل المستخدم", "دليل الاستخدام", "instruction manual", "owner's manual", "كتيب"),
            setOf("model number", "serial number", "safety", "operation", "troubleshooting", "رقم الموديل", "الرقم التسلسلي", "السلامة", "التشغيل", "استكشاف الأعطال"),
            setOf("appliance", "refrigerator", "washer", "tv", "air conditioner", "oven", "جهاز", "ثلاجة", "غسالة", "تلفزيون", "تكييف", "فرن", "microwave", "ميكروويف", "dishwasher", "غسالة أطباق", "vacuum", "مكنسة", "heater", "سخان"),
            setOf("warranty", "ضمان", "specifications", "المواصفات", "voltage", "الجهد", "220v", "power", "القدرة"),
            setOf("installation", "التركيب", "setup", "الإعداد", "assembly", "التجميع"),
            setOf("warning", "تحذير", "caution", "تنبيه", "important", "هام", "do not", "لا تقم"),
            setOf("maintenance", "الصيانة", "cleaning", "التنظيف", "filter", "الفلتر"),
            setOf("remote control", "ريموت", "buttons", "أزرار", "display", "شاشة")
        ),
        ExperienceId.HOME_INVENTORY to listOf(
            setOf("inventory", "home inventory", "assets", "property", "list of items", "جرد", "قائمة المقتنيات", "ممتلكات", "أصول", "عفش"),
            setOf("purchase date", "price", "condition", "location", "room", "تاريخ الشراء", "السعر", "الحالة", "الموقع", "الغرفة"),
            setOf("furniture", "أثاث", "sofa", "كنبة", "bed", "سرير", "table", "طاولة", "wardrobe", "دولاب"),
            setOf("electronics", "إلكترونيات", "appliance", "جهاز", "jewelry", "مجوهرات", "ذهب", "gold"),
            setOf("value", "القيمة", "estimated", "تقديري", "insurance", "تأمين", "serial", "سيريال"),
            setOf("bedroom", "غرفة النوم", "kitchen", "مطبخ", "living room", "صالة", "garage", "جراج"),
            setOf("photo", "صورة", "documented", "موثق", "receipt", "إيصال")
        ),
        ExperienceId.RENT_CONTRACT to listOf(
            setOf("rent", "rental", "lease", "contract", "عقد إيجار", "إيجار", "عقد", "lease agreement", "tenancy agreement", "عقد تأجير"),
            setOf("landlord", "tenant", "lessor", "lessee", "property", "unit", "المؤجر", "المستأجر", "العقار", "الوحدة", "الشقة", "apartment", "شقة", "villa", "فيلا"),
            setOf("monthly rent", "deposit", "lease term", "start date", "end date", "الإيجار الشهري", "التأمين", "الوديعة", "مدة العقد", "تاريخ البدء", "تاريخ الانتهاء"),
            setOf("rent amount", "قيمة الإيجار", "قسط", "payment", "الدفع", "annually", "سنويا", "monthly", "شهريا"),
            setOf("renewal", "تجديد", "termination", "إنهاء", "notice period", "فترة الإخطار", "eviction", "إخلاء"),
            setOf("utilities included", "شامل المرافق", "furnished", "مفروشة", "unfurnished", "maintenance", "الصيانة"),
            setOf("ejari", "إيجاري", "registered", "موثق", "notarized", "توثيق", "signature", "توقيع")
        ),
        ExperienceId.UTILITY_BILL to listOf(
            setOf("utility bill", "فاتورة خدمات"),
            setOf("consumption", "kwh", "meter", "billing period", "due date", "الاستهلاك", "كيلووات", "العداد", "فترة الفوترة", "تاريخ الاستحقاق", "قراءة العداد"),
            setOf("utility company", "شركة خدمات"),
            setOf("account number", "رقم الحساب", "subscription number", "رقم الاشتراك", "customer id", "رقم العميل"),
            setOf("amount due", "المبلغ المستحق", "total", "الإجمالي", "arrears", "متأخرات"),
            setOf("payment", "السداد", "pay before", "ادفع قبل", "fawry", "فوري")
        ),
        ExperienceId.ELECTRICITY_BILL to listOf(
            setOf("electricity bill", "فاتورة كهرباء", "electricity company", "شركة الكهرباء"),
            setOf("kwh", "كيلووات", "electricity meter", "عداد الكهرباء", "reading", "قراءة"),
            setOf("power consumption", "استهلاك الطاقة")
        ),
        ExperienceId.WATER_BILL to listOf(
            setOf("water bill", "فاتورة مياه", "فاتورة ماء", "water company", "شركة المياه"),
            setOf("cubic meters", "متر مكعب", "water meter", "عداد المياه"),
            setOf("water consumption", "استهلاك المياه")
        ),
        ExperienceId.GAS_BILL to listOf(
            setOf("gas bill", "فاتورة غاز", "gas company", "شركة الغاز"),
            setOf("gas meter", "عداد الغاز", "natural gas", "الغاز الطبيعي")
        ),
        ExperienceId.INTERNET_BILL to listOf(
            setOf("internet bill", "فاتورة إنترنت", "broadband", "fiber", "router", "راوتر", "wifi", "واي فاي", "modem", "مودم"),
            setOf("data cap", "سعة", "quota", "الكوتا", "unlimited", "غير محدود", "fair usage", "الاستخدام العادل", "fup"),
            setOf("te data", "تي اي داتا", "we internet", "وي إنترنت", "internet provider", "مزود الإنترنت")
        ),
        ExperienceId.TELECOM_BILL to listOf(
            setOf("phone bill", "فاتورة تليفون", "فاتورة هاتف", "فاتورة محمول", "telecom", "اتصالات"),
            setOf("vodafone", "فودافون", "orange", "أورانج", "etisalat", "اتصالات", "stc", "اس تي سي", "ooredoo", "zain", "زين"),
            setOf("landline", "أرضي", "mobile", "موبايل", "roaming", "تجوال", "minutes", "دقائق")
        ),
        ExperienceId.PROPERTY_DEED to listOf(
            setOf("deed", "title deed", "property deed", "صك", "صك ملكية", "عقد ملكية", "سند ملكية", "title", "ownership", "ملكية"),
            setOf("property", "العقار", "plot", "قطعة أرض", "land", "أرض", "parcel", "real estate", "عقارات"),
            setOf("owner", "المالك", "buyer", "المشتري", "seller", "البائع", "grantor", "grantee"),
            setOf("deed number", "رقم الصك", "registration number", "رقم القيد", "شهر عقاري", "land registry", "الشهر العقاري"),
            setOf("area", "المساحة", "square meters", "متر مربع", "م٢", "sqm", "boundaries", "الحدود"),
            setOf("address", "العنوان", "district", "الحي", "city", "المدينة", "block", "حوض"),
            setOf("notarized", "موثق", "registered", "مسجل", "date", "التاريخ", "ministry of justice", "وزارة العدل")
        ),
        ExperienceId.MORTGAGE_STATEMENT to listOf(
            setOf("mortgage", "mortgage statement", "قرض عقاري", "رهن عقاري", "كشف قرض", "home loan", "housing loan", "قرض إسكان"),
            setOf("principal balance", "outstanding balance", "الرصيد المتبقي", "أصل القرض", "remaining", "payoff amount"),
            setOf("monthly payment", "القسط الشهري", "installment", "قسط", "emi"),
            setOf("interest rate", "معدل الفائدة", "apr", "fixed", "ثابتة", "variable", "متغيرة", "escrow"),
            setOf("loan number", "رقم القرض", "account", "حساب", "lender", "المقرض", "bank", "بنك"),
            setOf("property", "العقار", "address", "العنوان", "collateral", "الضمان"),
            setOf("next payment", "الدفعة القادمة", "due date", "تاريخ الاستحقاق", "maturity", "الاستحقاق النهائي"),
            setOf("term", "المدة", "years", "سنة", "30 year", "15 year", "amortization", "جدول السداد")
        ),
        ExperienceId.HOME_INSURANCE_POLICY to listOf(
            setOf("home insurance", "homeowners insurance", "house insurance", "تأمين المنزل", "تأمين منزلي", "property insurance", "تأمين العقار"),
            setOf("policy", "policy number", "وثيقة", "رقم الوثيقة", "بوليصة", "certificate", "شهادة"),
            setOf("coverage", "التغطية", "dwelling", "المسكن", "contents", "المحتويات", "liability", "المسؤولية"),
            setOf("fire", "حريق", "theft", "سرقة", "flood", "فيضان", "water damage", "تسرب", "natural disaster", "كوارث"),
            setOf("insured", "المؤمن عليه", "insured value", "مبلغ التأمين", "sum insured", "deductible", "التحمل"),
            setOf("premium", "القسط", "annual", "سنوي", "renewal", "تجديد"),
            setOf("effective", "سارية من", "expiry", "انتهاء", "valid until", "صالحة حتى", "policy period", "فترة الوثيقة"),
            setOf("insurer", "شركة التأمين", "agent", "الوكيل", "claim", "مطالبة")
        ),
        ExperienceId.APPLIANCE_REGISTRATION to listOf(
            setOf("product registration", "register your product", "تسجيل المنتج", "تسجيل الجهاز", "appliance registration", "registration card"),
            setOf("serial number", "الرقم التسلسلي", "سيريال", "model number", "رقم الموديل", "model", "الموديل"),
            setOf("purchase date", "تاريخ الشراء", "date of purchase", "retailer", "البائع", "store", "المتجر"),
            setOf("warranty activation", "تفعيل الضمان", "activate warranty", "warranty", "الضمان"),
            setOf("brand", "الماركة", "manufacturer", "الشركة المصنعة", "samsung", "lg", "sony", "bosch", "philips", "beko", "electrolux"),
            setOf("qr code", "scan", "امسح", "register online", "سجل عبر", "website", "الموقع"),
            setOf("name", "الاسم", "email", "البريد", "phone", "الهاتف", "owner", "المالك")
        ),
        ExperienceId.UTILITY_SETUP to listOf(
            setOf("new connection", "توصيل جديد", "utility setup", "service activation", "تفعيل الخدمة", "تركيب", "installation", "اشتراك جديد"),
            setOf("electricity", "كهرباء", "water", "مياه", "ماء", "gas", "غاز", "internet", "انترنت", "إنترنت", "fiber", "فايبر"),
            setOf("application", "طلب", "request number", "رقم الطلب", "account number", "رقم الحساب", "subscription", "اشتراك"),
            setOf("meter", "عداد", "meter number", "رقم العداد", "installation date", "تاريخ التركيب"),
            setOf("activation date", "تاريخ التفعيل", "start date", "تاريخ البدء", "appointment", "موعد"),
            setOf("deposit", "تأمين", "وديعة", "connection fee", "رسوم التوصيل", "setup fee", "رسوم التركيب"),
            setOf("address", "العنوان", "premises", "العقار", "apartment", "شقة", "floor", "الدور"),
            setOf("technician", "فني", "visit", "زيارة", "confirmation", "تأكيد")
        ),
        ExperienceId.INSURANCE_POLICY to listOf(
            setOf("insurance policy", "policy", "بوليصة", "وثيقة تأمين", "وثيقة", "تأمين", "insurance"),
            setOf("premium", "القسط", "قسط", "coverage", "تغطية", "التغطية", "insured", "المؤمن عليه", "المؤمن له"),
            setOf("policy number", "رقم الوثيقة", "رقم البوليصة", "insurer", "شركة التأمين", "renewal", "تجديد"),
            setOf("effective date", "تاريخ السريان", "expiry date", "تاريخ الانتهاء", "beneficiary", "مستفيد", "sum insured", "مبلغ التأمين"),
            setOf("claim", "مطالبة", "deductible", "التحمل", "exclusions", "الاستثناءات")
        ),
        ExperienceId.CAR_INSURANCE to listOf(
            setOf("car insurance", "auto insurance", "vehicle insurance", "تأمين سيارة", "تأمين مركبة", "تأمين السيارة", "motor insurance", "comprehensive", "شامل", "third party", "طرف ثالث", "إلزامي"),
            setOf("policy number", "coverage", "premium", "insurer", "claim", "رقم الوثيقة", "التغطية", "القسط", "شركة التأمين", "مطالبة", "حادث"),
            setOf("vehicle", "المركبة", "make", "الماركة", "model", "الموديل", "year", "سنة الصنع", "plate number", "رقم اللوحة", "chassis", "شاسيه", "vin"),
            setOf("insured value", "قيمة التأمين", "sum insured", "deductible", "التحمل", "excess"),
            setOf("valid from", "valid until", "سارية من", "حتى", "expiry", "انتهاء", "renewal", "تجديد"),
            setOf("driver", "السائق", "named driver", "age", "العمر", "license", "رخصة"),
            setOf("agency repair", "إصلاح الوكالة", "workshop", "ورشة", "roadside assistance", "المساعدة على الطريق")
        ),
        ExperienceId.CAR_SERVICE to listOf(
            setOf("car service", "maintenance", "repair", "workshop", "garage", "صيانة", "تصليح", "ورشة", "مركز صيانة", "service center", "مركز الخدمة"),
            setOf("oil change", "filter", "brake", "tire", "mileage", "km", "spare parts", "تغيير زيت", "فلتر", "فرامل", "إطار", "كاوتش", "عداد", "كم", "قطع غيار"),
            setOf("service history", "warranty", "appointment", "estimate", "سجل الصيانة", "ضمان", "موعد", "تقدير", "quotation", "عرض سعر"),
            setOf("battery", "بطارية", "air filter", "فلتر هواء", "spark plugs", "بواجي", "coolant", "مياه ريديير", "transmission", "فتيس", "ناقل الحركة"),
            setOf("inspection", "فحص", "diagnostic", "كمبيوتر", "check engine", "alignment", "وزن أذرعة", "balancing", "ترصيص"),
            setOf("labor", "أجرة", "مصنعية", "parts", "قطع", "total", "الإجمالي", "invoice", "فاتورة"),
            setOf("next service", "الصيانة القادمة", "due at", "عند", "km", "sticker", "ملصق")
        ),
        ExperienceId.PARKING_TICKET to listOf(
            setOf("parking", "parking ticket", "parking receipt", "موقف", "مواقف", "باركن", "car park", "garage", "جراج", "park"),
            setOf("meter", "عداد", "parking zone", "منطقة الانتظار", "duration", "المدة", "hourly", "بالساعة", "entry time", "exit time", "وقت الدخول", "وقت الخروج"),
            setOf("plate number", "رقم اللوحة", "vehicle", "المركبة", "location", "الموقع", "street", "شارع"),
            setOf("time", "الوقت", "date", "التاريخ", "level", "الدور", "bay", "space", "مكان الوقوف"),
            setOf("payment", "السداد", "paid", "مدفوع", "amount", "المبلغ", "tariff", "التعرفة"),
            setOf("ticket number", "رقم الإيصال", "receipt number", "رقم التذكرة", "barcode", "qr code")
        ),
        ExperienceId.FINE_TICKET to listOf(
            setOf("fine", "violation", "traffic ticket", "speeding", "مخالفة", "غرامة", "مخالفة مرورية", "سرعة", "تجاوز السرعة", "traffic fine"),
            setOf("traffic police", "violation number", "fine amount", "payment deadline", "المرور", "رقم المخالفة", "قيمة الغرامة", "آخر موعد للسداد"),
            setOf("plate number", "رقم اللوحة", "license", "رخصة", "driver", "السائق"),
            setOf("radar", "رادار", "camera", "كاميرا", "red light", "إشارة حمراء", "seat belt", "حزام الأمان", "phone", "تليفون", "هاتف"),
            setOf("points", "نقاط", "black points", "النقاط السوداء", "deduction", "خصم"),
            setOf("location", "الموقع", "road", "طريق", "date", "التاريخ", "time", "الوقت"),
            setOf("pay", "سداد", "دفع", "saher", "ساهر", "absher", "أبشر", "50% discount", "خصم")
        ),
        ExperienceId.FUEL_RECEIPT to listOf(
            setOf("fuel", "gasoline", "petrol", "diesel", "station", "بنزين", "وقود", "محطة", "محطة وقود", "سولار", "ديزل", "gas station", "filling station"),
            setOf("liters", "gallons", "price per liter", "pump", "لتر", "لترات", "جالون", "سعر اللتر", "طلمبة", "مضخة"),
            setOf("92", "95", "91", "80", "octane", "أوكتان", "premium", "ممتاز", "super", "سوبر", "regular", "عادي"),
            setOf("shell", "شل", "emarat", "الإمارات", "adnoc", "أدنوك", "mobil", "موبيل", "esso", "bp fuel", "petromin", "البترول"),
            setOf("odometer", "عداد", "km", "كم", "full tank", "فل", "تفويلة"),
            setOf("attendant", "العامل", "shift", "وردية"),
            setOf("car wash", "غسيل", "oil", "زيت", "air", "هواء")
        ),
        ExperienceId.CAR_REGISTRATION to listOf(
            setOf("vehicle registration", "car registration", "registration card", "رخصة سير", "استمارة", "تسجيل مركبة", "ملكية سيارة", "istimara", "mulkiya", "ملكية"),
            setOf("plate number", "رقم اللوحة", "لوحة", "registration number", "رقم التسجيل", "license plate"),
            setOf("owner", "المالك", "registered owner", "name", "الاسم", "id number", "رقم الهوية"),
            setOf("make", "الماركة", "model", "الموديل", "year", "سنة الصنع", "color", "اللون", "type", "النوع"),
            setOf("chassis number", "رقم الشاسيه", "vin", "رقم الهيكل", "engine number", "رقم الموتور"),
            setOf("expiry", "انتهاء", "valid until", "سارية حتى", "issue date", "تاريخ الإصدار", "renewal", "تجديد"),
            setOf("traffic department", "إدارة المرور", "rta", "muroor", "مرور", "issuing authority", "جهة الإصدار")
        ),
        ExperienceId.CAR_LOAN to listOf(
            setOf("car loan", "auto loan", "vehicle loan", "قرض سيارة", "تمويل سيارة", "car finance", "auto finance", "تمويل مركبة"),
            setOf("installment", "القسط", "قسط", "monthly payment", "القسط الشهري", "emi"),
            setOf("loan amount", "مبلغ القرض", "principal", "أصل", "down payment", "مقدم", "الدفعة الأولى"),
            setOf("interest", "فائدة", "profit rate", "هامش ربح", "apr", "rate", "المعدل"),
            setOf("loan term", "مدة القرض", "months", "شهر", "60 months", "48", "balloon", "دفعة أخيرة"),
            setOf("lender", "الممول", "bank", "بنك", "finance company", "شركة تمويل", "dealership", "المعرض", "الوكيل"),
            setOf("loan number", "رقم التمويل", "agreement", "اتفاقية", "contract", "عقد"),
            setOf("due date", "تاريخ الاستحقاق", "remaining", "المتبقي", "early settlement", "سداد مبكر")
        ),
        ExperienceId.TOLL_RECEIPT to listOf(
            setOf("toll", "toll receipt", "رسوم طريق", "رسوم المرور", "بوابة", "toll gate", "toll plaza", "تحصيل"),
            setOf("salik", "سالك", "darb", "درب", "tag", "ممر", "e-zpass", "fastrak", "telepass"),
            setOf("gate", "البوابة", "location", "الموقع", "entry", "دخول", "exit", "خروج"),
            setOf("vehicle", "المركبة", "plate", "اللوحة", "class", "فئة", "axles", "محاور"),
            setOf("amount", "المبلغ", "fare", "الأجرة", "charge", "الرسم", "balance", "الرصيد"),
            setOf("date", "التاريخ", "time", "الوقت", "trip", "رحلة"),
            setOf("account", "حساب", "recharge", "شحن", "top up", "إعادة شحن")
        ),
        ExperienceId.PARKING_PERMIT to listOf(
            setOf("parking permit", "resident permit", "تصريح انتظار", "تصريح موقف", "parking pass", "resident parking", "zone permit"),
            setOf("permit number", "رقم التصريح", "zone", "المنطقة", "area", "الحي"),
            setOf("valid from", "valid until", "ساري من", "صالح حتى", "expiry", "انتهاء", "renewal", "تجديد"),
            setOf("vehicle", "المركبة", "plate number", "رقم اللوحة", "registered", "مسجلة"),
            setOf("address", "العنوان", "resident", "مقيم", "قاطن", "proof of residence", "إثبات سكن"),
            setOf("visitor", "زائر", "guest", "ضيف", "scratch card", "كرت"),
            setOf("municipality", "البلدية", "council", "المجلس", "fee", "رسوم")
        ),
        ExperienceId.CAR_INSPECTION to listOf(
            setOf("vehicle inspection", "car inspection", "فحص السيارة", "الفحص الدوري", "فحص دوري", "technical inspection", "فحص فني", "mot test", "tuv", "itv"),
            setOf("inspection result", "نتيجة الفحص", "pass", "ناجح", "اجتاز", "fail", "راسب", "لم يجتز"),
            setOf("brakes", "الفرامل", "lights", "الأنوار", "emissions", "الانبعاثات", "عادم", "suspension", "التعليق", "steering", "الدركسيون"),
            setOf("plate number", "رقم اللوحة", "vehicle", "المركبة", "odometer", "العداد", "mileage", "المسافة"),
            setOf("inspection center", "مركز الفحص", "station", "المحطة", "inspector", "الفاحص", "فني"),
            setOf("valid until", "صالح حتى", "expiry", "انتهاء", "next inspection", "الفحص القادم", "due", "يستحق"),
            setOf("certificate", "شهادة", "sticker", "ملصق", "report", "تقرير", "defects", "عيوب", "ملاحظات")
        ),
        ExperienceId.PRICE_COMPARE to listOf(
            setOf("price compare", "cheaper", "best price", "discount", "offer", "compare", "مقارنة أسعار", "أرخص", "أفضل سعر", "خصم", "عرض", "قارن"),
            setOf("price", "was", "now", "save", "currency", "السعر", "كان", "أصبح", "وفر", "توفير", "عملة"),
            setOf("old price", "new price", "السعر القديم", "السعر الجديد", "before", "after", "قبل", "بعد"),
            setOf("sale", "تخفيضات", "clearance", "تصفية", "deal", "صفقة", "promo", "برومو", "off"),
            setOf("store a", "store b", "online", "أونلاين", "amazon", "أمازون", "noon", "نون", "jarir", "جرير", "extra", "اكسترا"),
            setOf("per unit", "للوحدة", "per kg", "للكيلو", "value", "قيمة", "worth it", "يستحق"),
            setOf("price match", "مطابقة السعر", "lowest", "أدنى", "highest", "أعلى")
        ),
        ExperienceId.GIFT_IDEA to listOf(
            setOf("gift", "present", "birthday", "anniversary", "idea", "هدايا", "هدية", "عيد ميلاد", "ذكرى", "فكرة", "مناسبة", "occasion"),
            setOf("budget", "wishlist", "recipient", "ميزانية", "قائمة أمنيات", "المتلقي", "إلى"),
            setOf("wedding", "زفاف", "زواج", "graduation", "تخرج", "baby shower", "سبوع", "eid", "عيد", "christmas", "valentine", "فلانتين"),
            setOf("wrap", "تغليف", "card", "كارت معايدة", "surprise", "مفاجأة", "gift box", "علبة هدية"),
            setOf("perfume", "عطر", "watch", "ساعة", "flowers", "ورد", "زهور", "chocolate", "شوكولاتة", "jewelry", "مجوهرات"),
            setOf("him", "her", "له", "لها", "kids", "أطفال", "mom", "ماما", "dad", "بابا", "friend", "صديق"),
            setOf("price range", "فئة سعرية", "under", "أقل من", "around", "حوالي")
        ),
        ExperienceId.COUPON to listOf(
            setOf("coupon", "voucher", "promo code", "discount code", "كوبون", "خصم", "قسيمة", "برومو كود", "كود خصم", "كوبون خصم", "gift voucher"),
            setOf("valid until", "terms", "conditions", "offer", "redeem", "صالح حتى", "الشروط", "الأحكام", "عرض", "استبدال", "استخدام"),
            setOf("%", "percent", "بالمئة", "off", "خصم", "save", "وفر", "10%", "20%", "50%"),
            setOf("code", "الكود", "enter code", "أدخل الكود", "apply", "تطبيق", "at checkout", "عند الدفع"),
            setOf("minimum purchase", "حد أدنى", "minimum order", "أقل قيمة", "one-time", "لمرة واحدة", "single use"),
            setOf("expires", "ينتهي", "expiry", "انتهاء الصلاحية", "expired", "منتهي"),
            setOf("free shipping", "شحن مجاني", "buy one get one", "bogo", "اشتري واحصل", "free gift", "هدية مجانية")
        ),
        ExperienceId.RETURN_POLICY to listOf(
            setOf("return policy", "return window", "returns", "exchange", "استبدال", "استرجاع", "سياسة الاسترجاع", "سياسة الإرجاع", "إرجاع", "استبدال واسترجاع"),
            setOf("refund", "within", "days", "receipt required", "conditions", "استرداد", "خلال", "يوم", "أيام", "الإيصال مطلوب", "الشروط"),
            setOf("14 days", "30 days", "7 days", "١٤ يوم", "٣٠ يوم", "no questions asked", "بدون أسئلة"),
            setOf("original packaging", "العبوة الأصلية", "unopened", "غير مفتوح", "unused", "غير مستعمل", "tags attached", "الملصقات"),
            setOf("final sale", "بيع نهائي", "non-refundable", "غير قابل للاسترجاع", "no returns", "لا يقبل الإرجاع", "excluded", "مستثنى"),
            setOf("store credit", "رصيد بالمتجر", "voucher", "قسيمة", "exchange only", "استبدال فقط"),
            setOf("defective", "معيب", "damaged", "تالف", "wrong item", "منتج خاطئ", "warranty", "الضمان")
        ),
        ExperienceId.ORDER_CONFIRMATION to listOf(
            setOf("order confirmation", "order confirmed", "your order", "تأكيد الطلب", "تم تأكيد طلبك", "طلبك", "order placed", "thank you for your order", "شكراً لطلبك"),
            setOf("order number", "رقم الطلب", "order id", "order #", "confirmation number", "رقم التأكيد"),
            setOf("amazon", "أمازون", "noon", "نون", "shein", "شي إن", "aliexpress", "ebay", "etsy", "souq", "jarir", "extra", "temu"),
            setOf("shipping address", "عنوان الشحن", "delivery address", "عنوان التوصيل", "ship to", "deliver to"),
            setOf("estimated delivery", "التوصيل المتوقع", "arriving", "يصل", "delivery date", "تاريخ التوصيل", "expected"),
            setOf("items", "المنتجات", "الأصناف", "qty", "الكمية", "subtotal", "المجموع الفرعي", "shipping", "الشحن", "total", "الإجمالي"),
            setOf("payment method", "طريقة الدفع", "cash on delivery", "الدفع عند الاستلام", "cod", "card", "بطاقة"),
            setOf("track your order", "تتبع طلبك", "track", "تتبع", "view order", "عرض الطلب")
        ),
        ExperienceId.SHIPPING_TRACKING to listOf(
            setOf("tracking", "tracking number", "رقم التتبع", "تتبع الشحنة", "shipment", "شحنة", "parcel", "طرد", "package", "طرد"),
            setOf("in transit", "في الطريق", "قيد الشحن", "out for delivery", "خرج للتوصيل", "delivered", "تم التوصيل", "تم التسليم"),
            setOf("courier", "شركة الشحن", "aramex", "أرامكس", "dhl", "fedex", "ups", "smsa", "سمسا", "naqel", "ناقل", "j&t", "imile", "fetchr", "mylerz", "bosta", "بوسطة"),
            setOf("origin", "المصدر", "destination", "الوجهة", "from", "من", "to", "إلى"),
            setOf("estimated delivery", "التسليم المتوقع", "expected", "متوقع", "eta", "delivery date", "تاريخ التسليم"),
            setOf("status", "الحالة", "update", "تحديث", "history", "سجل", "exception", "تعذر", "attempted", "محاولة توصيل"),
            setOf("signature required", "التوقيع مطلوب", "pickup point", "نقطة استلام", "locker", "خزانة", "cod", "تحصيل")
        ),
        ExperienceId.LOYALTY_CARD to listOf(
            setOf("loyalty", "loyalty card", "بطاقة ولاء", "برنامج الولاء", "rewards", "مكافآت", "membership card", "بطاقة عضوية", "club", "نادي"),
            setOf("points", "نقاط", "points balance", "رصيد النقاط", "earn", "اكسب", "redeem", "استبدل"),
            setOf("miles", "أميال", "airmiles", "frequent flyer", "المسافر الدائم", "skywards", "alfursan", "الفرسان", "etihad guest", "qmiles", "privilege club"),
            setOf("tier", "الفئة", "silver", "فضي", "gold", "ذهبي", "platinum", "بلاتيني", "elite", "status", "المستوى"),
            setOf("member since", "عضو منذ", "member id", "رقم العضوية", "card number", "رقم البطاقة"),
            setOf("expiry", "انتهاء", "points expire", "تنتهي النقاط", "valid", "سارية"),
            setOf("carrefour myclub", "share", "shukran", "شكرنز", "amber", "nuqati", "نقاطي", "mokafa", "مكافآت", "cut", "qitaf", "قطاف")
        ),
        ExperienceId.GIFT_RECEIPT to listOf(
            setOf("gift receipt", "إيصال هدية", "gift slip", "gift proof", "proof of purchase gift"),
            setOf("gift", "هدية", "present", "for", "إلى", "لـ", "recipient", "المستلم"),
            setOf("exchange", "استبدال", "return", "إرجاع", "without receipt", "بدون إيصال", "exchange within", "الاستبدال خلال"),
            setOf("no price", "بدون سعر", "price removed", "prices hidden"),
            setOf("store", "المتجر", "merchant", "البائع", "branch", "الفرع"),
            setOf("date", "التاريخ", "purchase date", "تاريخ الشراء", "item", "الصنف", "items", "الأصناف"),
            setOf("thank you", "شكراً", "happy gifting", "happy holidays", "أعياد سعيدة")
        ),
        ExperienceId.WISHLIST to listOf(
            setOf("wishlist", "wish list", "قائمة الأمنيات", "قائمة الرغبات", "أمنيات", "save for later", "حفظ لوقت لاحق", "want", "أريد"),
            setOf("add to wishlist", "أضف للمفضلة", "favorites", "المفضلة", "saved items", "العناصر المحفوظة"),
            setOf("buy later", "شراء لاحقاً", "want to buy", "أرغب بالشراء", "dream", "حلم"),
            setOf("price", "السعر", "watch price", "راقب السعر", "price drop", "انخفاض السعر", "alert", "تنبيه"),
            setOf("in stock", "متوفر", "out of stock", "غير متوفر", "notify me", "أبلغني", "back in stock"),
            setOf("priority", "أولوية", "must have", "ضروري", "nice to have", "لو أمكن"),
            setOf("amazon wishlist", "قائمة أمازون", "registry", "قائمة هدايا")
        ),
        ExperienceId.WATCHLIST to listOf(
            setOf("watchlist", "to watch", "movie", "series", "show", "film", "مشاهدة", "أفلام", "فيلم", "مسلسل", "مسلسلات", "قائمة المشاهدة", "watch list"),
            setOf("cinema", "netflix", "imdb", "rating", "trailer", "recommend", "سينما", "نتفليكس", "تقييم", "إعلان", "ترشيح", "نجوم"),
            setOf("season", "episode", "موسم", "حلقة", "s01", "e01", "episodes", "حلقات"),
            setOf("actor", "ممثل", "actress", "ممثلة", "director", "مخرج", "starring", "بطولة", "cast", "طاقم"),
            setOf("genre", "النوع", "action", "أكشن", "comedy", "كوميدي", "drama", "دراما", "horror", "رعب", "thriller", "إثارة", "documentary", "وثائقي"),
            setOf("release", "الإصدار", "2024", "2025", "2026", "new", "جديد", "coming soon", "قريباً"),
            setOf("watch", "شاهد", "streaming", "بث", "disney", "hbo", "shahid", "شاهد", "prime video"),
            setOf("review", "مراجعة", "critics", "النقاد", "rotten tomatoes", "metacritic")
        ),
        ExperienceId.ALREADY_WATCHED to listOf(
            setOf("watched", "seen", "already watched", "finished series", "شاهدت", "شاهدته", "خلصت", "انتهيت من", "تمت المشاهدة"),
            setOf("rating", "review", "recommend", "date watched", "تقييم", "مراجعة", "أنصح به", "تاريخ المشاهدة", "رأيي"),
            setOf("stars", "نجوم", "out of 10", "من 10", "8/10", "9/10", "loved it", "أعجبني", "boring", "ممل"),
            setOf("movie", "فيلم", "series", "مسلسل", "episode", "حلقة", "season", "موسم", "finale", "النهاية"),
            setOf("cinema", "سينما", "watched at", "شاهدت في", "netflix", "theater", "صالة"),
            setOf("rewatch", "إعادة مشاهدة", "would watch again", "أشاهده مرة أخرى", "favorite", "مفضل"),
            setOf("ending", "النهاية", "plot twist", "مفاجأة", "spoiler", "حرق", "acting", "التمثيل")
        ),
        ExperienceId.READING_LIST to listOf(
            setOf("reading list", "to read", "book", "novel", "author", "reading", "قراءة", "كتب", "كتاب", "رواية", "مؤلف", "قائمة القراءة", "tbr"),
            setOf("publisher", "isbn", "pages", "recommend", "library", "دار نشر", "صفحة", "صفحات", "ترشيح", "مكتبة"),
            setOf("chapter", "فصل", "chapters", "فصول", "volume", "جزء", "edition", "طبعة", "translation", "ترجمة", "translated", "مترجم"),
            setOf("fiction", "خيالي", "non-fiction", "واقعي", "self-help", "تطوير ذات", "biography", "سيرة", "history", "تاريخ", "science", "علوم"),
            setOf("goodreads", "جودريدز", "kindle", "كيندل", "audible", "bookstore", "مكتبة جرير", "jarir"),
            setOf("want to read", "أريد قراءته", "currently reading", "أقرأ حالياً", "next", "التالي"),
            setOf("bestseller", "الأكثر مبيعاً", "award", "جائزة", "booker", "pulitzer", "classic", "كلاسيكي"),
            setOf("hardcover", "غلاف مقوى", "paperback", "غلاف عادي", "ebook", "إلكتروني", "pdf")
        ),
        ExperienceId.ALREADY_READ to listOf(
            setOf("already read", "finished book", "read", "review", "rating", "قرأت", "قرأته", "خلصت الكتاب", "أنهيت", "تمت القراءة"),
            setOf("stars", "نجوم", "out of 5", "من 5", "4/5", "5/5", "loved", "أحببته", "masterpiece", "تحفة"),
            setOf("book", "كتاب", "novel", "رواية", "author", "المؤلف", "pages", "صفحة"),
            setOf("date finished", "تاريخ الانتهاء", "finished on", "أنهيته في", "read in", "قرأته في", "days", "أيام"),
            setOf("review", "مراجعة", "notes", "ملاحظات", "quotes", "اقتباسات", "highlights", "مقتطفات"),
            setOf("recommend", "أنصح به", "must read", "يجب قراءته", "reread", "إعادة قراءة"),
            setOf("book club", "نادي القراءة", "challenge", "تحدي القراءة", "reading challenge", "books this year", "كتب هذا العام")
        ),
        ExperienceId.MUSIC_PLAYLIST to listOf(
            setOf("playlist", "song", "album", "artist", "music", "spotify", "أغاني", "موسيقى", "قائمة تشغيل", "أغنية", "ألبوم", "فنان", "مطرب"),
            setOf("track", "genre", "concert", "lyrics", "مقطع", "نوع", "حفلة", "كلمات", "أغنية", "single", "سينجل"),
            setOf("singer", "مغني", "مطربة", "band", "فرقة", "rapper", "راب", "dj", "دي جي", "producer", "منتج"),
            setOf("pop", "بوب", "rock", "روك", "rap", "راب", "jazz", "جاز", "classical", "كلاسيكي", "tarab", "طرب", "khaleeji", "خليجي"),
            setOf("apple music", "آبل ميوزك", "youtube music", "anghami", "أنغامي", "deezer", "soundcloud", "ساوندكلاود"),
            setOf("feat", "featuring", "بالتعاون مع", "remix", "ريمكس", "cover", "كوفر", "live", "لايف", "acoustic", "أكوستيك"),
            setOf("minutes", "دقائق", "duration", "المدة", "release date", "تاريخ الإصدار", "new album", "ألبوم جديد")
        ),
        ExperienceId.GAME_WISHLIST to listOf(
            setOf("game", "video game", "wishlist", "playstation", "xbox", "steam", "pc", "ألعاب", "لعبة", "بلايستيشن", "إكس بوكس", "ستيم", "قائمة الأمنيات"),
            setOf("release date", "platform", "rating", "multiplayer", "تاريخ الإصدار", "منصة", "تقييم", "جماعي", "أونلاين"),
            setOf("ps5", "ps4", "nintendo", "switch", "نينتندو", "سويتش", "epic games", "epic", "origin", "ea app"),
            setOf("rpg", "fps", "shooter", "تصويب", "adventure", "مغامرة", "open world", "عالم مفتوح", "sports", "رياضة", "fifa", "فيفا", "fc 25"),
            setOf("pre-order", "طلب مسبق", "coming soon", "قريباً", "early access", "وصول مبكر", "beta", "بيتا"),
            setOf("dlc", "إضافة", "expansion", "توسعة", "season pass", "تذكرة الموسم", "bundle", "حزمة"),
            setOf("sale", "تخفيض", "discount", "خصم", "free", "مجاني", "price", "السعر"),
            setOf("fortnite", "فورتنايت", "minecraft", "ماينكرافت", "gta", "call of duty", "كود", "pubg", "ببجي", "roblox", "روبلوكس")
        ),
        ExperienceId.RECIPE to listOf(
            setOf("recipe", "ingredients", "instructions", "cook", "bake", "preparation", "وصفة", "مقادير", "طريقة", "تحضير", "طبخ", "وصفة طعام"),
            setOf("servings", "minutes", "oven", "temperature", "kitchen", "حصص", "أشخاص", "دقيقة", "دقائق", "فرن", "درجة حرارة", "مطبخ"),
            setOf("cup", "كوب", "tablespoon", "ملعقة كبيرة", "teaspoon", "ملعقة صغيرة", "grams", "جرام", "ml", "مل", "pinch", "رشة"),
            setOf("flour", "دقيق", "طحين", "sugar", "سكر", "butter", "زبدة", "oil", "زيت", "eggs", "بيض", "milk", "حليب", "salt", "ملح", "pepper", "فلفل"),
            setOf("chicken", "دجاج", "meat", "لحم", "rice", "أرز", "pasta", "مكرونة", "vegetables", "خضروات", "onion", "بصل", "garlic", "ثوم"),
            setOf("preheat", "سخن الفرن", "mix", "اخلط", "stir", "قلب", "boil", "اسلق", "fry", "اقل", "simmer", "اتركه على نار هادئة"),
            setOf("prep time", "وقت التحضير", "cook time", "وقت الطبخ", "total time", "الوقت الكلي", "easy", "سهلة", "quick", "سريعة"),
            setOf("cake", "كيك", "cookies", "كوكيز", "bread", "خبز", "soup", "شوربة", "salad", "سلطة", "dessert", "حلويات", "kabsa", "كبسة", "molokhia", "ملوخية")
        ),
        ExperienceId.PODCAST to listOf(
            setOf("podcast", "بودكاست", "episode", "حلقة", "pod cast", "show notes", "ملاحظات الحلقة"),
            setOf("host", "المضيف", "مقدم", "guest", "ضيف", "interview", "مقابلة", "لقاء"),
            setOf("spotify", "apple podcasts", "youtube", "يوتيوب", "anghami", "أنغامي", "podbean", "castbox"),
            setOf("season", "موسم", "ep", "ح", "episode number", "رقم الحلقة", "weekly", "أسبوعي", "daily", "يومي"),
            setOf("listen", "استمع", "استماع", "subscribe", "اشترك", "follow", "تابع"),
            setOf("duration", "المدة", "minutes", "دقيقة", "transcript", "نص الحلقة"),
            setOf("true crime", "جريمة", "business", "أعمال", "comedy", "كوميديا", "news", "أخبار", "tech", "تقنية", "self development", "تطوير الذات")
        ),
        ExperienceId.AUDIOBOOK to listOf(
            setOf("audiobook", "كتاب صوتي", "كتاب مسموع", "audio book", "narrated", "بصوت", "narration", "السرد الصوتي"),
            setOf("audible", "أوديبل", "storytel", "iqraaly", "اقرألي", "dhad", "ضاد", "kitab sawti", "كتاب صوتي"),
            setOf("narrator", "الراوي", "voice", "الصوت", "read by", "قراءة"),
            setOf("chapter", "فصل", "part", "جزء", "duration", "المدة", "hours", "ساعة", "ساعات"),
            setOf("author", "المؤلف", "book", "كتاب", "novel", "رواية", "title", "العنوان"),
            setOf("unabridged", "كامل", "abridged", "مختصر", "version", "نسخة"),
            setOf("listen", "استمع", "speed", "السرعة", "1.5x", "2x", "bookmark", "علامة")
        ),
        ExperienceId.EBOOK to listOf(
            setOf("ebook", "e-book", "كتاب إلكتروني", "كتاب الكتروني", "kindle", "كيندل", "digital book", "نسخة إلكترونية"),
            setOf("epub", "mobi", "pdf", "azw", "format", "صيغة", "download", "تحميل"),
            setOf("author", "المؤلف", "title", "العنوان", "publisher", "الناشر", "isbn"),
            setOf("kindle unlimited", "kobo", "google play books", "كتب جوجل", "apple books", "كتب آبل", "abjjad", "أبجد"),
            setOf("pages", "صفحة", "page", "location", "موقع", "progress", "التقدم", "% read", "percent"),
            setOf("highlight", "تمييز", "note", "ملاحظة", "bookmark", "إشارة", "dictionary", "قاموس"),
            setOf("price", "السعر", "free", "مجاني", "sample", "عينة", "buy", "شراء")
        ),
        ExperienceId.ONLINE_COURSE to listOf(
            setOf("online course", "دورة تدريبية", "دورة", "كورس", "course", "e-learning", "تعلم إلكتروني", "تعليم عن بعد"),
            setOf("udemy", "يوديمي", "coursera", "كورسيرا", "edx", "skillshare", "linkedin learning", "رواق", "roaaq", "إدراك", "edraak", "منصة معارف"),
            setOf("lesson", "درس", "lessons", "دروس", "module", "وحدة", "lecture", "محاضرة", "section", "قسم"),
            setOf("instructor", "المدرب", "المحاضر", "teacher", "المعلم", "taught by"),
            setOf("certificate", "شهادة", "completion", "إتمام", "certificate of completion", "شهادة إتمام"),
            setOf("progress", "التقدم", "completed", "مكتمل", "quiz", "اختبار", "assignment", "واجب"),
            setOf("hours", "ساعة", "ساعات", "video", "فيديو", "lifetime access", "وصول مدى الحياة"),
            setOf("enroll", "سجل", "التحاق", "price", "السعر", "free", "مجاني", "discount", "خصم")
        ),
        ExperienceId.EVENT_TICKET to listOf(
            setOf("event ticket", "تذكرة فعالية", "event", "فعالية", "ticket", "تذكرة", "admission", "دخول"),
            setOf("venue", "المكان", "date", "التاريخ", "time", "الوقت", "doors open", "تفتح الأبواب"),
            setOf("seat", "مقعد", "row", "صف", "section", "منطقة", "gate", "البوابة"),
            setOf("ticketmaster", "eventbrite", "platinumlist", "webook", "bookmyshow", "تكت ماستر"),
            setOf("organizer", "المنظم", "presented by", "تقديم", "sponsored by", "برعاية")
        ),
        ExperienceId.CONCERT_TICKET to listOf(
            setOf("concert", "حفلة", "حفل", "live music", "موسيقى حية", "gig", "show", "عرض", "festival", "مهرجان"),
            setOf("ticket", "تذكرة", "tickets", "تذاكر", "admission", "دخول", "entry", "gate"),
            setOf("artist", "الفنان", "performer", "المؤدي", "band", "الفرقة", "singer", "المطرب", "tour", "جولة"),
            setOf("venue", "المكان", "المسرح", "arena", "أرينا", "stadium", "استاد", "hall", "قاعة", "theater"),
            setOf("date", "التاريخ", "time", "الوقت", "doors open", "تفتح الأبواب", "starts at", "يبدأ"),
            setOf("seat", "مقعد", "section", "منطقة", "row", "صف", "general admission", "دخول عام", "vip", "كبار الزوار", "standing", "وقوف"),
            setOf("ticketmaster", "تكت ماستر", "eventbrite", "platinumlist", "بلاتينيوم ليست", "webook", "bookmyshow", "price", "السعر")
        ),
        ExperienceId.MUSEUM_TICKET to listOf(
            setOf("museum", "متحف", "exhibition", "معرض", "gallery", "جاليري", "معرض فني", "exhibit", "جناح"),
            setOf("ticket", "تذكرة", "admission", "دخول", "entry", "الدخول", "day pass", "تذكرة يومية"),
            setOf("art", "فن", "artifacts", "آثار", "antiquities", "مقتنيات", "history", "تاريخ", "pharaonic", "فرعوني", "islamic art", "فن إسلامي"),
            setOf("louvre", "اللوفر", "british museum", "المتحف البريطاني", "the met", "المتروبوليتان", "egyptian museum", "المتحف المصري", "grand egyptian museum", "المتحف الكبير", "national museum", "المتحف الوطني"),
            setOf("opening hours", "ساعات العمل", "مواعيد", "open", "مفتوح", "closed", "مغلق", "guided tour", "جولة إرشادية", "tour", "جولة"),
            setOf("audio guide", "الدليل الصوتي", "map", "خريطة", "floor", "دور", "wing", "جناح"),
            setOf("adult", "بالغ", "child", "طفل", "student", "طالب", "free entry", "دخول مجاني", "discount", "خصم", "price", "السعر")
        ),
        ExperienceId.THEATER_TICKET to listOf(
            setOf("theater", "theatre", "مسرح", "مسرحية", "play", "عرض مسرحي", "opera", "أوبرا", "ballet", "باليه"),
            setOf("ticket", "تذكرة", "tickets", "تذاكر", "box office", "شباك التذاكر", "admission", "دخول"),
            setOf("show", "العرض", "performance", "الأداء", "act", "فصل", "scene", "مشهد", "intermission", "استراحة"),
            setOf("cast", "طاقم التمثيل", "الممثلون", "director", "المخرج", "playwright", "المؤلف", "starring", "بطولة"),
            setOf("seat", "مقعد", "row", "صف", "balcony", "شرفة", "orchestra", "صالة", "stalls", "مقاعد", "box", "جناح"),
            setOf("date", "التاريخ", "evening", "مساء", "matinee", "نهاري", "curtain", "الستارة", "starts", "يبدأ"),
            setOf("venue", "المكان", "hall", "القاعة", "national theater", "المسرح الوطني", "opera house", "دار الأوبرا", "price", "السعر")
        ),
        ExperienceId.NOTE to listOf(
            setOf("note", "notes", "idea", "memo", "notepad", "ملاحظة", "ملاحظات", "فكرة", "مذكرة", "مفكرة", "تدوينة"),
            setOf("remember", "reminder", "thought", "draft", "تذكر", "تذكير", "خاطرة", "مسودة", "لا تنسى"),
            setOf("important", "مهم", "هام", "urgent", "عاجل", "later", "لاحقاً", "someday", "يوماً ما"),
            setOf("list", "قائمة", "bullet", "نقطة", "checklist", "قائمة مهام"),
            setOf("meeting", "اجتماع", "call", "مكالمة", "follow up", "متابعة", "action", "إجراء"),
            setOf("quote", "اقتباس", "inspiration", "إلهام", "brainstorm", "عصف ذهني", "idea", "فكرة"),
            setOf("quick note", "ملاحظة سريعة", "jot", "دون", "scribble", "خربشة")
        ),
        ExperienceId.REMINDER to listOf(
            setOf("reminder", "don't forget", "remember to", "تذكير", "تذكر", "لا تنسى", "لا تنس", "منبه", "alarm"),
            setOf("due", "schedule", "alert", "موعد", "قبل", "جدولة", "تنبيه", "deadline", "الموعد النهائي"),
            setOf("tomorrow", "غداً", "غدا", "today", "اليوم", "next week", "الأسبوع القادم", "tonight", "الليلة", "morning", "الصباح"),
            setOf("call", "اتصل", "email", "أرسل بريد", "pay", "ادفع", "buy", "اشتر", "pick up", "أحضر", "book", "احجز"),
            setOf("appointment", "موعد", "meeting", "اجتماع", "birthday", "عيد ميلاد", "anniversary", "ذكرى", "renewal", "تجديد"),
            setOf("repeat", "تكرار", "daily", "يومي", "weekly", "أسبوعي", "monthly", "شهري", "every", "كل"),
            setOf("snooze", "تأجيل", "postpone", "أجل", "reschedule", "إعادة جدولة", "cancel", "إلغاء")
        ),
        ExperienceId.MEETING_NOTES to listOf(
            setOf("meeting", "agenda", "minutes", "attendees", "action items", "اجتماع", "محضر اجتماع", "جدول أعمال", "الحضور", "إجراءات", "meeting notes"),
            setOf("discussed", "decided", "follow up", "next meeting", "conference", "تمت مناقشة", "تم الاتفاق", "متابعة", "الاجتماع القادم", "مؤتمر"),
            setOf("attendees", "الحاضرون", "participants", "المشاركون", "present", "حاضر", "absent", "غائب", "apologies", "اعتذار"),
            setOf("action", "إجراء", "owner", "المسؤول", "assigned to", "مكلف", "due", "الاستحقاق", "deadline", "الموعد"),
            setOf("decision", "قرار", "agreed", "اتفقنا", "proposal", "مقترح", "approved", "تمت الموافقة", "rejected", "مرفوض"),
            setOf("zoom", "teams", "google meet", "زوم", "تيمز", "meet", "webex", "online", "أونلاين", "call", "مكالمة"),
            setOf("kickoff", "انطلاق", "standup", "وقفة", "retrospective", "استرجاع", "review", "مراجعة", "sync", "تزامن"),
            setOf("q1", "q2", "q3", "q4", "kpi", "مؤشرات", "targets", "المستهدفات", "update", "تحديث")
        ),
        ExperienceId.STUDY_MATERIAL to listOf(
            setOf("study", "exam", "course", "lecture", "subject", "lesson", "دراسة", "امتحان", "اختبار", "مادة", "محاضرة", "درس", "مقرر"),
            setOf("notes", "summary", "flashcards", "quiz", "assignment", "homework", "ملخص", "ملاحظات", "بطاقات", "واجب", "تكليف", "اختبار قصير"),
            setOf("chapter", "فصل", "unit", "وحدة", "page", "صفحة", "syllabus", "المنهج", "curriculum", "المقرر الدراسي"),
            setOf("university", "جامعة", "school", "مدرسة", "college", "كلية", "professor", "دكتور", "أستاذ", "teacher", "مدرس"),
            setOf("revision", "مراجعة", "memorize", "حفظ", "understand", "فهم", "practice", "تدريب", "solve", "حل", "questions", "أسئلة"),
            setOf("final", "نهائي", "midterm", "منتصف الفصل", "grade", "الدرجة", "gpa", "المعدل", "marks", "علامات", "score", "النتيجة"),
            setOf("math", "رياضيات", "physics", "فيزياء", "chemistry", "كيمياء", "biology", "أحياء", "history", "تاريخ", "english", "إنجليزي", "arabic", "عربي"),
            setOf("semester", "الفصل الدراسي", "term", "الترم", "academic year", "العام الدراسي", "deadline", "موعد التسليم")
        ),
        ExperienceId.BOOKMARK to listOf(
            setOf("bookmark", "link", "url", "website", "article", "read later", "إشارة مرجعية", "رابط", "موقع", "مقال", "قراءة لاحقاً", "احفظ الرابط"),
            setOf("saved link", "رابط محفوظ"),
            setOf("article", "مقال", "blog", "مدونة", "post", "منشور", "thread", "ثريد", "tweet", "تغريدة"),
            setOf("tutorial", "شرح", "guide", "دليل", "how to", "كيف", "documentation", "توثيق", "reference", "مرجع"),
            setOf("video", "فيديو", "youtube", "يوتيوب", "watch later", "مشاهدة لاحقاً"),
            setOf("saved", "محفوظ", "collection", "مجموعة", "folder", "مجلد", "tag", "وسم"),
            setOf("archive", "أرشيف", "pocket", "بوكيت", "instapaper", "raindrop")
        ),
        ExperienceId.TODO_LIST to listOf(
            setOf("to do", "todo", "to-do", "مهام", "قائمة مهام", "قائمة المهام", "tasks", "المهام", "task list", "to do list"),
            setOf("done", "تم", "تمت", "completed", "مكتمل", "pending", "معلق", "قيد الانتظار", "in progress", "قيد التنفيذ"),
            setOf("checkbox", "✓", "✔", "[ ]", "[x]", "علم", "check", "tick"),
            setOf("priority", "أولوية", "high", "عالية", "medium", "متوسطة", "low", "منخفضة", "urgent", "عاجل"),
            setOf("today", "اليوم", "tomorrow", "غداً", "this week", "هذا الأسبوع", "someday", "لاحقاً"),
            setOf("call", "اتصال", "buy", "شراء", "send", "إرسال", "finish", "إنهاء", "clean", "تنظيف", "fix", "إصلاح"),
            setOf("project", "مشروع", "work", "عمل", "home", "البيت", "personal", "شخصي", "errands", "مشاوير")
        ),
        ExperienceId.HABIT_TRACKER to listOf(
            setOf("habit", "habits", "عادة", "عادات", "habit tracker", "متتبع العادات", "daily habits", "العادات اليومية"),
            setOf("streak", "سلسلة", "أيام متتالية", "consecutive", "متتالي", "day streak"),
            setOf("daily", "يومي", "everyday", "كل يوم", "weekly", "أسبوعي", "frequency", "التكرار"),
            setOf("water", "ماء", "شرب الماء", "exercise", "رياضة", "تمارين", "reading", "قراءة", "meditation", "تأمل", "prayer", "صلاة"),
            setOf("wake up", "استيقاظ", "sleep", "نوم", "no sugar", "بدون سكر", "steps", "خطوات", "walk", "مشي"),
            setOf("goal", "هدف", "target", "مستهدف", "days", "يوم", "أيام", "21 days", "30 days", "66 days"),
            setOf("progress", "التقدم", "calendar", "التقويم", "check in", "تسجيل", "✓", "completed", "منجز")
        ),
        ExperienceId.GOAL to listOf(
            setOf("goal", "goals", "هدف", "أهداف", "objective", "objectives", "الغاية", "target", "مستهدف"),
            setOf("achieve", "تحقيق", "حقق", "accomplish", "إنجاز", "reach", "الوصول", "milestone", "محطة"),
            setOf("deadline", "الموعد النهائي", "target date", "التاريخ المستهدف", "by", "بحلول", "before", "قبل", "end of year", "نهاية العام"),
            setOf("short term", "قصير المدى", "long term", "طويل المدى", "5 year", "خمس سنوات", "vision", "رؤية"),
            setOf("smart", "specific", "محدد", "measurable", "قابل للقياس", "achievable", "قابل للتحقيق", "okr", "kpis"),
            setOf("progress", "التقدم", "percent", "بالمئة", "on track", "على المسار", "behind", "متأخر"),
            setOf("save money", "توفير", "lose weight", "إنقاص الوزن", "learn", "تعلم", "career", "المسار المهني", "business", "مشروع")
        ),
        ExperienceId.PROJECT_PLAN to listOf(
            setOf("project plan", "خطة المشروع", "خطة مشروع", "project", "مشروع", "plan", "خطة", "roadmap", "خارطة الطريق"),
            setOf("milestone", "محطة", "معالم", "deliverable", "deliverables", "مخرجات", "التسليمات", "scope", "النطاق"),
            setOf("timeline", "الجدول الزمني", "gantt", "جانت", "phase", "مرحلة", "stages", "مراحل", "sprint", "سباق"),
            setOf("start date", "تاريخ البدء", "end date", "تاريخ الانتهاء", "due", "الاستحقاق", "deadline", "الموعد النهائي", "eta"),
            setOf("task", "مهمة", "tasks", "مهام", "assigned", "مكلف", "owner", "المسؤول", "team", "الفريق", "resources", "الموارد"),
            setOf("status", "الحالة", "on track", "على المسار", "at risk", "معرض للخطر", "delayed", "متأخر", "blocked", "متعثر"),
            setOf("budget", "الميزانية", "cost", "التكلفة", "estimate", "التقدير", "hours", "ساعات"),
            setOf("kickoff", "الانطلاق", "launch", "الإطلاق", "go live", "التشغيل", "review", "المراجعة", "sign off", "الاعتماد")
        ),
        ExperienceId.JOURNAL to listOf(
            setOf("journal", "diary", "يوميات", "مذكرات", "دفتر اليومية", "daily journal", "journal entry", "خواطر"),
            setOf("dear diary", "عزيزتي المذكرة", "today i", "اليوم", "today was", "كان اليوم", "felt", "شعرت"),
            setOf("mood", "المزاج", "happy", "سعيد", "sad", "حزين", "anxious", "قلقان", "grateful", "ممتن", "tired", "متعب"),
            setOf("morning pages", "الصفحات الصباحية", "evening reflection", "تأمل مسائي", "reflection", "تأمل", "reflect", "فكرت"),
            setOf("entry", "مدخلة", "خاطرة", "date", "التاريخ", "written", "كتبت"),
            setOf("thoughts", "أفكار", "خواطر", "feelings", "مشاعر", "memories", "ذكريات", "dreams", "أحلام"),
            setOf("weekly review", "مراجعة أسبوعية", "highlights", "أبرز الأحداث", "lessons", "دروس", "learned", "تعلمت")
        ),
        ExperienceId.GRATITUDE_LOG to listOf(
            setOf("gratitude", "grateful", "thankful", "امتنان", "ممتن", "شكر", "gratitude journal", "دفتر الامتنان", "gratitude log"),
            setOf("thank god", "الحمد لله", "alhamdulillah", "blessed", "محظوظ", "blessing", "نعمة", "نعم"),
            setOf("three things", "ثلاثة أشياء", "things i am grateful", "أشياء أنا ممتن لها", "today i am grateful", "اليوم أنا ممتن"),
            setOf("family", "العائلة", "أهلي", "health", "الصحة", "friends", "الأصدقاء", "home", "البيت", "food", "الطعام"),
            setOf("appreciate", "أقدر", "thank you", "شكراً", "thanks", "شكر", "appreciation", "تقدير"),
            setOf("moment", "لحظة", "small things", "الأشياء الصغيرة", "kindness", "لطف", "smile", "ابتسامة"),
            setOf("daily", "يومي", "morning", "صباح", "evening", "مساء", "reflection", "تأمل", "positive", "إيجابي")
        ),
        ExperienceId.SERVICE_CONTRACT to listOf(
            setOf("service contract", "agreement", "terms of service", "service level", "عقد خدمة", "اتفاقية", "شروط الخدمة", "مستوى الخدمة", "service agreement", "عقد اتفاق"),
            setOf("provider", "client", "scope", "payment terms", "duration", "renewal", "مقدم الخدمة", "العميل", "النطاق", "شروط الدفع", "المدة", "التجديد"),
            setOf("sla", "uptime", "توافر", "response time", "زمن الاستجابة", "support", "الدعم", "maintenance", "الصيانة"),
            setOf("party", "الطرف الأول", "الطرف الثاني", "first party", "second party", "between", "بين"),
            setOf("effective date", "تاريخ السريان", "start date", "تاريخ البدء", "termination", "الإنهاء", "notice", "إخطار"),
            setOf("fees", "الرسوم", "monthly fee", "الرسوم الشهرية", "invoice", "فاتورة", "payment", "الدفع"),
            setOf("liability", "المسؤولية", "indemnity", "التعويض", "confidentiality", "السرية", "governing law", "القانون الواجب التطبيق"),
            setOf("signature", "التوقيع", "witness", "الشاهد", "stamp", "الختم", "seal")
        ),
        ExperienceId.APPOINTMENT to listOf(
            setOf("appointment", "booking", "scheduled", "موعد", "حجز", "حجز موعد", "ميعاد", "reservation", "booked"),
            setOf("date", "time", "location", "confirmed", "cancellation", "التاريخ", "الوقت", "المكان", "مؤكد", "إلغاء", "تأكيد"),
            setOf("doctor", "طبيب", "دكتور", "dentist", "أسنان", "salon", "صالون", "spa", "سبا", "barber", "حلاق", "clinic", "عيادة"),
            setOf("reminder", "تذكير", "confirmation", "تأكيد", "reference", "مرجع", "booking id", "رقم الحجز"),
            setOf("arrive", "الوصول", "early", "مبكراً", "minutes before", "دقيقة قبل", "late", "تأخير", "reschedule", "إعادة جدولة"),
            setOf("waitlist", "قائمة الانتظار", "walk in", "بدون موعد", "queue", "دور", "ticket number", "رقم الدور"),
            setOf("fee", "رسوم", "consultation fee", "رسوم الكشف", "deposit", "عربون", "paid", "مدفوع")
        ),
        ExperienceId.QUOTE to listOf(
            setOf("quote", "estimate", "quotation", "proposal", "عرض سعر", "تقدير", "عرض أسعار", "مقايسة", "price quote", "quotation no"),
            setOf("valid until", "price", "scope", "materials", "labor", "صالح حتى", "السعر", "النطاق", "المواد", "الأجور", "المصنعية"),
            setOf("item", "بند", "بنود", "description", "الوصف", "quantity", "الكمية", "unit price", "سعر الوحدة", "total", "الإجمالي"),
            setOf("subtotal", "المجموع الفرعي", "tax", "الضريبة", "vat", "القيمة المضافة", "discount", "الخصم", "grand total", "الإجمالي الكلي"),
            setOf("terms", "الشروط", "payment terms", "شروط الدفع", "advance", "دفعة مقدمة", "50%", "delivery", "التسليم", "weeks", "أسابيع"),
            setOf("prepared by", "إعداد", "company", "الشركة", "contractor", "المقاول", "supplier", "المورد", "vendor"),
            setOf("approve", "اعتماد", "accept", "قبول", "decline", "رفض", "signature", "التوقيع", "reference", "المرجع")
        ),
        ExperienceId.PHONE_PLAN to listOf(
            setOf("phone plan", "mobile plan", "باقة الموبايل", "باقة الهاتف", "شريحة", "sim", "mobile", "موبايل", "cellular", "خط"),
            setOf("vodafone", "فودافون", "orange", "أورانج", "etisalat", "اتصالات", "e&", "stc", "موبايلي", "mobily", "zain", "زين", "ooredoo", "أوريدو", "du", "دو"),
            setOf("data", "بيانات", "إنترنت", "gb", "جيجا", "mb", "ميجا", "minutes", "دقائق", "sms", "رسائل"),
            setOf("monthly", "شهري", "renewal", "تجديد", "auto renew", "تجديد تلقائي", "prepaid", "مسبق الدفع", "postpaid", "فاتورة"),
            setOf("balance", "الرصيد", "recharge", "شحن", "top up", "إعادة شحن", "credit", "رصيد"),
            setOf("bundle", "باقة", "flex", "فلكس", "units", "وحدات", "rollover", "ترحيل"),
            setOf("4g", "5g", "lte", "roaming", "تجوال", "international", "دولي", "local", "محلي"),
            setOf("number", "الرقم", "phone number", "رقم الهاتف", "iccid", "puk", "pin")
        ),
        ExperienceId.INTERNET_PLAN to listOf(
            setOf("internet plan", "باقة الإنترنت", "اشتراك الإنترنت", "internet subscription", "broadband", "برودباند", "نطاق عريض"),
            setOf("fiber", "فايبر", "ftth", "adsl", "vdsl", "dsl", "cable", "كابل", "5g home", "انترنت منزلي"),
            setOf("speed", "السرعة", "mbps", "ميجا", "gbps", "جيجا", "download", "تحميل", "upload", "رفع", "ping"),
            setOf("data cap", "سعة", "quota", "الكوتا", "unlimited", "غير محدود", "fair usage", "الاستخدام العادل", "fup"),
            setOf("router", "راوتر", "wifi", "واي فاي", "modem", "مودم", "installation", "تركيب", "technician", "فني"),
            setOf("وي", "te data", "تي اي داتا", "vodafone", "فودافون", "orange", "أورانج", "etisalat", "اتصالات", "stc", "زين", "ooredoo"),
            setOf("monthly", "شهري", "bill", "فاتورة", "renewal", "تجديد", "subscription", "اشتراك", "amount", "المبلغ"),
            setOf("account number", "رقم الحساب", "landline", "أرضي", "service id", "رقم الخدمة")
        ),
        ExperienceId.GYM_MEMBERSHIP to listOf(
            setOf("gym membership", "عضوية الجيم", "اشتراك الجيم", "gym", "جيم", "fitness club", "نادي رياضي", "health club", "نادي صحي"),
            setOf("membership", "عضوية", "member", "عضو", "membership number", "رقم العضوية", "card", "بطاقة"),
            setOf("monthly", "شهري", "annual", "سنوي", "yearly", "quarterly", "ربع سنوي", "subscription", "اشتراك", "renewal", "تجديد"),
            setOf("access", "دخول", "24/7", "24 ساعة", "hours", "ساعات", "ladies only", "سيدات", "mixed", "مشترك"),
            setOf("classes", "حصص", "class", "حصة", "personal trainer", "مدرب شخصي", "pt", "coach", "مدرب", "session", "جلسة"),
            setOf("pool", "مسبح", "سباحة", "sauna", "ساونا", "spa", "سبا", "locker", "خزانة", "towel", "منشفة"),
            setOf("freeze", "تجميد", "pause", "إيقاف", "cancel", "إلغاء", "transfer", "تحويل", "guest pass", "دعوة"),
            setOf("gold's gym", "fitness first", "fitness time", "فتنس تايم", "be gym", "anytime fitness", "planet fitness")
        ),
        ExperienceId.STREAMING_SERVICE to listOf(
            setOf("streaming", "بث", "streaming service", "خدمة بث", "watch online", "مشاهدة أونلاين"),
            setOf("netflix", "نتفليكس", "disney+", "ديزني", "hbo max", "hulu", "prime video", "برايم", "apple tv+", "shahid", "شاهد", "starzplay", "ستارزبلاي", "osn+", "tod", "تود"),
            setOf("subscription", "اشتراك", "monthly", "شهري", "annual", "سنوي", "plan", "خطة", "باقة"),
            setOf("basic", "أساسية", "standard", "قياسية", "premium", "بريميوم", "4k", "hd", "جودة", "screens", "شاشات"),
            setOf("profile", "بروفايل", "ملف شخصي", "kids", "أطفال", "family", "عائلي", "shared", "مشترك"),
            setOf("billing", "الفوترة", "payment", "الدفع", "next payment", "الدفعة القادمة", "renewal", "تجديد", "card", "بطاقة"),
            setOf("cancel", "إلغاء", "free trial", "تجربة مجانية", "trial", "تجريبي", "offer", "عرض")
        ),
        ExperienceId.CLOUD_STORAGE to listOf(
            setOf("cloud storage", "تخزين سحابي", "التخزين السحابي", "cloud", "سحابة", "storage plan", "خطة التخزين"),
            setOf("icloud", "آي كلاود", "google drive", "جوجل درايف", "google one", "onedrive", "ون درايف", "dropbox", "دروب بوكس", "mega", "ميجا", "box", "pcloud"),
            setOf("gb", "جيجا", "tb", "تيرا", "50gb", "200gb", "2tb", "15 gb", "free", "مجاني", "space", "مساحة"),
            setOf("storage full", "المساحة ممتلئة", "almost full", "تكاد تمتلئ", "upgrade", "ترقية", "more storage", "مساحة إضافية"),
            setOf("backup", "نسخ احتياطي", "باك اب", "sync", "مزامنة", "photos", "الصور", "files", "الملفات", "documents", "المستندات"),
            setOf("monthly", "شهري", "annual", "سنوي", "subscription", "اشتراك", "price", "السعر", "renewal", "تجديد"),
            setOf("family", "عائلي", "shared", "مشترك", "sharing", "مشاركة", "access", "وصول")
        ),
        ExperienceId.CLEANING_SERVICE to listOf(
            setOf("cleaning", "cleaning service", "تنظيف", "خدمة تنظيف", "شركة تنظيف", "housekeeping", "تدبير منزلي", "maid", "عاملة"),
            setOf("deep cleaning", "تنظيف عميق", "regular cleaning", "تنظيف دوري", "move in", "move out", "انتقال", "post construction", "بعد التشطيب"),
            setOf("hourly", "بالساعة", "hours", "ساعات", "rate", "السعر", "per hour", "للساعة", "visit", "زيارة"),
            setOf("carpet", "سجاد", "sofa", "كنب", "curtains", "ستائر", "windows", "شبابيك", "نوافذ", "kitchen", "مطبخ", "bathroom", "حمام"),
            setOf("booking", "حجز", "appointment", "موعد", "schedule", "جدولة", "date", "التاريخ", "time", "الوقت"),
            setOf("team", "فريق", "cleaner", "عامل النظافة", "supervisor", "مشرف", "supplies", "مستلزمات", "equipment", "معدات"),
            setOf("justmop", "خدمة", "urban company", "خدمات", "app", "تطبيق", "invoice", "فاتورة", "total", "الإجمالي")
        ),
        ExperienceId.SUSPICIOUS_MESSAGE to listOf(
            setOf("suspicious", "spam", "scam", "fraud", "phishing", "message", "رسالة مشبوهة", "احتيال", "نصب", "رسالة احتيالية", "مريب"),
            setOf("click here", "verify account", "urgent", "winner", "congratulations", "free", "اضغط هنا", "تحقق من حسابك", "عاجل", "فائز", "مبروك", "مجاناً", "مجاني"),
            setOf("limited time", "لفترة محدودة", "act now", "تصرف الآن", "immediately", "فوراً", "expires today", "ينتهي اليوم", "last chance", "آخر فرصة"),
            setOf("account blocked", "تم حظر حسابك", "suspended", "موقوف", "unusual activity", "نشاط غير معتاد", "security alert", "تنبيه أمني"),
            setOf("prize", "جائزة", "lottery", "يانصيب", "won", "فزت", "million", "مليون", "claim", "استلم", "reward", "مكافأة"),
            setOf("كلمة المرور", "رمز التحقق", "pin", "الرقم السري", "cvv", "card details", "بيانات البطاقة", "ssn"),
            setOf("wire transfer", "تحويل بنكي", "western union", "وسترن يونيون", "gift card", "بطاقة هدية", "itunes", "bitcoin", "بيتكوين", "usdt"),
            setOf("irs", "tax refund", "استرداد ضريبي", "customs", "الجمارك", "package", "طرد", "delivery failed", "فشل التوصيل", "redelivery", "إعادة التوصيل")
        ),
        ExperienceId.PHISHING_REPORT to listOf(
            setOf("phishing", "fake website", "spoof", "login attempt", "credential", "تصيد", "موقع مزيف", "محاولة دخول", "بيانات الدخول", "انتحال"),
            setOf("verify", "link", "كلمة المرور", "الحساب", "تحقق", "رابط", "تسجيل الدخول"),
            setOf("sign in", "تسجيل الدخول", "log in", "دخول", "username", "اسم المستخدم", "email", "البريد الإلكتروني", "confirm", "تأكيد"),
            setOf("secure", "آمن", "security", "الأمان", "ssl", "certificate", "شهادة", "padlock", "قفل"),
            setOf("update your information", "تحديث بياناتك", "confirm your identity", "تأكيد هويتك", "reactivate", "إعادة تفعيل", "unusual sign-in", "دخول غير معتاد"),
            setOf("paypal", "باي بال", "apple id", "آبل", "netflix", "نتفليكس", "amazon", "أمازون", "microsoft", "مايكروسوفت", "facebook", "فيسبوك", "instagram", "انستقرام"),
            setOf(".xyz", ".top", ".click", "bit.ly", "shortened", "مختصر"),
            setOf("report", "إبلاغ", "reported", "تم الإبلاغ", "block", "حظر", "do not click", "لا تضغط")
        ),
        ExperienceId.FRAUD_RECORD to listOf(
            setOf("fraud", "identity theft", "unauthorized", "chargeback", "dispute", "احتيال", "سرقة الهوية", "غير مصرح", "اعتراض", "نزاع", "عملية احتيالية"),
            setOf("police", "report", "case number", "incident", "victim", "الشرطة", "بلاغ", "رقم البلاغ", "محضر", "حادثة", "ضحية"),
            setOf("transaction", "عملية", "معاملة", "charged", "تم الخصم", "withdrawal", "سحب", "purchase", "شراء", "did not authorize", "لم أصرح"),
            setOf("card", "بطاقة", "stolen", "مسروقة", "lost", "مفقودة", "cloned", "مستنسخة", "skimming", "نسخ البطاقة", "compromised", "مخترقة"),
            setOf("bank", "البنك", "called", "اتصلت", "hotline", "الخط الساخن", "freeze", "تجميد", "blocked", "حظر", "replaced", "استبدال"),
            setOf("refund", "استرداد", "reimbursement", "تعويض", "investigation", "تحقيق", "under review", "قيد المراجعة", "resolved", "تم الحل"),
            setOf("evidence", "دليل", "أدلة", "screenshot", "لقطة شاشة", "statement", "كشف حساب", "documents", "مستندات"),
            setOf("scammer", "نصاب", "محتال", "fraudster", "phone number", "رقم الهاتف", "account number", "رقم الحساب", "transferred to", "حولت إلى")
        ),
        ExperienceId.FAKE_INVOICE to listOf(
            setOf("fake invoice", "فاتورة مزيفة", "فاتورة وهمية", "invoice scam", "unpaid invoice", "فاتورة غير مدفوعة", "outstanding invoice"),
            setOf("pay immediately", "ادفع فوراً", "ادفع فورا", "immediate payment", "دفع فوري", "urgent payment", "دفع عاجل", "overdue", "متأخرة"),
            setOf("wire transfer", "تحويل بنكي", "bank transfer", "حوالة بنكية", "new account details", "بيانات حساب جديدة", "updated bank", "حساب محدث", "iban changed", "تغيير الآيبان"),
            setOf("final notice", "إشعار نهائي", "last warning", "تحذير أخير", "legal action", "إجراء قانوني", "collections", "تحصيل", "debt collector", "محصل ديون"),
            setOf("invoice number", "رقم الفاتورة", "amount due", "المبلغ المستحق", "balance", "الرصيد", "remit", "حول المبلغ"),
            setOf("vendor", "مورد", "supplier", "المورد", "accounting", "المحاسبة", "billing department", "قسم الفواتير", "account manager", "مدير الحساب"),
            setOf("kindly", "يرجى التكرم", "asap", "في أقرب وقت", "attached invoice", "الفاتورة مرفقة", "see attached", "انظر المرفق"),
            setOf("penalty", "غرامة", "late fee", "رسوم تأخير", "interest", "فوائد", "suspend service", "إيقاف الخدمة", "terminate", "إنهاء")
        ),
        ExperienceId.FAKE_CHECK to listOf(
            setOf("fake check", "شيك مزيف", "شيك وهمي", "cashier's check", "cashiers check", "شيك مصرفي", "certified check", "شيك معتمد", "money order", "حوالة بريدية"),
            setOf("deposit this check", "أودع هذا الشيك", "cash this check", "صرف الشيك", "enclosed check", "الشيك مرفق", "check enclosed", "مرفق شيك"),
            setOf("overpayment", "دفع زائد", "extra amount", "مبلغ إضافي", "keep the difference", "احتفظ بالفرق", "send the rest", "أرسل الباقي", "wire back", "أعد تحويل"),
            setOf("mystery shopper", "متسوق خفي", "secret shopper", "متسوق سري", "assignment", "مهمة", "evaluation", "تقييم", "first task", "المهمة الأولى"),
            setOf("bank of america", "wells fargo", "chase", "citibank", "routing number", "رقم التوجيه", "account number", "رقم الحساب", "signature"),
            setOf("clears", "يتم صرفه", "cleared", "تم الصرف", "bounce", "مرتد", "returned", "مرتجع", "insufficient", "رصيد غير كافي", "hold", "حجز"),
            setOf("courier", "بريد سريع", "fedex", "ups", "usps", "priority mail", "بريد مستعجل", "delivered", "تم التسليم"),
            setOf("payment for", "دفعة لـ", "advance payment", "دفعة مقدمة", "first payment", "الدفعة الأولى", "commission", "عمولة")
        ),
        ExperienceId.ADVANCE_FEE_FRAUD to listOf(
            setOf("advance fee", "رسوم مقدمة", "دفعة مقدمة", "processing fee", "رسوم معالجة", "upfront fee", "رسوم مسبقة", "419", "nigerian prince", "الأمير النيجيري"),
            setOf("inheritance", "ميراث", "تركة", "next of kin", "أقرب الأقارب", "deceased", "المتوفى", "late relative", "قريبك الراحل", "estate", "تركة"),
            setOf("release your funds", "تحرير أموالك", "release the funds", "صرف الأموال", "transfer your inheritance", "تحويل الميراث", "unclaimed", "غير مطالب بها", "dormant account", "حساب خامد"),
            setOf("million dollars", "مليون دولار", "usd", "million", "مليون", "$10,000,000", "$5,000,000", "million united states dollars"),
            setOf("barrister", "محامي", "attorney", "المحامي", "law firm", "مكتب محاماة", "esq", "chambers", "مكتب المحاماة", "legal representative", "الممثل القانوني"),
            setOf("confidential", "سري", "strictly confidential", "سري للغاية", "do not tell", "لا تخبر", "trust", "ثقة", "honest", "أمين", "god fearing", "تقي"),
            setOf("diplomat", "دبلوماسي", "consignment", "شحنة", "trunk box", "صندوق", "security company", "شركة أمن", "customs", "الجمارك", "clearance", "تخليص"),
            setOf("beneficiary", "المستفيد", "your share", "نصيبك", "percentage", "نسبة", "40%", "30%", "split", "تقسيم", "for your assistance", "لمساعدتك")
        ),
        ExperienceId.LOTTERY_SCAM to listOf(
            setOf("lottery", "يانصيب", "lotto", "لوتو", "jackpot", "جائزة كبرى", "sweepstakes", "سحب", "prize draw", "سحب جوائز", "raffle"),
            setOf("you have won", "لقد فزت", "فزت بـ", "congratulations", "مبروك", "تهانينا", "winner", "الفائز", "lucky winner", "الفائز المحظوظ", "selected", "تم اختيارك"),
            setOf("claim your prize", "استلم جائزتك", "claim now", "استلم الآن", "claim within", "استلم خلال", "unclaimed prize", "جائزة غير مستلمة", "before it expires", "قبل انتهاء"),
            setOf("prize money", "قيمة الجائزة", "cash prize", "جائزة نقدية", "million", "مليون", "usd", "euro", "يورو", "gbp"),
            setOf("ticket number", "رقم التذكرة", "winning number", "الرقم الفائز", "reference number", "الرقم المرجعي", "batch number", "رقم الدفعة", "serial number", "الرقم التسلسلي"),
            setOf("processing fee", "رسوم المعالجة", "tax", "الضريبة", "insurance", "التأمين", "delivery fee", "رسوم التوصيل", "clearance", "التخليص", "transfer charges", "رسوم التحويل"),
            setOf("claims agent", "وكيل المطالبات", "fiduciary agent", "الوكيل المعتمد", "contact immediately", "تواصل فوراً", "call now", "اتصل الآن", "whatsapp", "واتساب"),
            setOf("euro millions", "powerball", "mega millions", "el gordo", "uk lottery", "اليانصيب البريطاني", "international lottery", "اليانصيب الدولي", "promotion", "الحملة الترويجية")
        ),
        ExperienceId.TECH_SUPPORT_SCAM to listOf(
            setOf("tech support", "الدعم الفني", "technical support", "support scam", "computer support", "دعم الكمبيوتر", "microsoft support", "دعم مايكروسوفت"),
            setOf("virus detected", "تم اكتشاف فيروس", "virus", "فيروس", "malware", "برمجية خبيثة", "infected", "مصاب", "trojan", "حصان طروادة", "ransomware", "فدية"),
            setOf("your computer", "جهازك", "جهاز الكمبيوتر الخاص بك", "windows", "ويندوز", "apple", "آبل", "mac", "ماك", "pc", "device compromised", "تم اختراق جهازك"),
            setOf("call this number", "اتصل بهذا الرقم", "toll free", "رقم مجاني", "helpline", "خط المساعدة", "immediately", "فوراً", "do not turn off", "لا تغلق الجهاز", "do not restart", "لا تعيد التشغيل"),
            setOf("error", "خطأ", "warning", "تحذير", "critical", "حرج", "alert", "تنبيه", "security warning", "تحذير أمني", "system blocked", "تم حظر النظام", "error code", "رمز الخطأ"),
            setOf("remote access", "وصول عن بعد", "anydesk", "teamviewer", "تيم فيور", "install", "تثبيت", "download", "تحميل", "allow access", "السماح بالوصول"),
            setOf("refund", "استرداد", "subscription renewal", "تجديد الاشتراك", "charged", "تم خصم", "$299", "$399", "$499", "antivirus", "مضاد الفيروسات", "norton", "mcafee"),
            setOf("gift card", "بطاقة هدية", "payment", "الدفع", "google play", "جوجل بلاي", "itunes", "آيتونز", "steam card", "بطاقة ستيم", "wire", "تحويل")
        ),
        ExperienceId.ROMANCE_SCAM to listOf(
            setOf("romance scam", "احتيال عاطفي", "نصب عاطفي", "dating scam", "احتيال المواعدة", "catfish", "catfishing", "انتحال شخصية"),
            setOf("my dear", "عزيزي", "عزيزتي", "my love", "حبيبي", "حبيبتي", "darling", "حبيبي", "soulmate", "توأم روحي", "my queen", "ملكتي", "my king"),
            setOf("lonely", "وحيد", "وحيدة", "widow", "أرمل", "أرملة", "widower", "divorced", "مطلق", "مطلقة", "single", "أعزب", "عزباء", "looking for love", "أبحث عن الحب"),
            setOf("send money", "أرسل المال", "أرسلي المال", "send me money", "need money", "أحتاج المال", "emergency", "طارئ", "hospital", "مستشفى", "sick", "مريض", "surgery", "عملية جراحية"),
            setOf("plane ticket", "تذكرة طيران", "come to you", "آتي إليك", "visit you", "أزورك", "visa", "تأشيرة", "travel expenses", "مصاريف السفر", "customs", "الجمارك", "stuck", "عالق"),
            setOf("soldier", "جندي", "military", "عسكري", "army", "الجيش", "deployment", "منتشر", "syria", "سوريا", "afghanistan", "أفغانستان", "peacekeeping", "حفظ السلام", "doctor abroad", "طبيب في الخارج", "oil rig", "منصة نفط", "engineer abroad", "مهندس في الخارج"),
            setOf("gift card", "بطاقة هدية", "itunes", "آيتونز", "google play", "جوجل بلاي", "steam", "ستيم", "western union", "وسترن يونيون", "moneygram", "موني جرام", "bitcoin", "بيتكوين"),
            setOf("whatsapp", "واتساب", "telegram", "تيليجرام", "hangouts", "kik", "video call broken", "الكاميرا لا تعمل", "camera not working", "الكاميرا معطلة", "cannot call", "لا أستطيع الاتصال")
        )
    )
}
