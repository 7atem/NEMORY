package com.vaultbrain.feature.vault

data class RedactionResult(
    val sanitizedText: String,
    val redactedCount: Int,
    val sensitiveTypes: List<String>
)

object SmartRedactionEngine {
    fun redact(text: String): RedactionResult {
        var result = text
        var count = 0
        val foundTypes = mutableSetOf<String>()

        // 1. Credit card numbers (16 digits with spaces or hyphens)
        val ccRegex = Regex("""\b(?:\d{4}[ -]?){3}\d{4}\b""")
        result = ccRegex.replace(result) {
            count++
            foundTypes.add("Card Number")
            "████-████-████-████"
        }

        // 2. SSN / National ID (XXX-XX-XXXX)
        val ssnRegex = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
        result = ssnRegex.replace(result) {
            count++
            foundTypes.add("National ID")
            "███-██-████"
        }

        // 3. IBAN accounts
        val ibanRegex = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{12,30}\b""")
        result = ibanRegex.replace(result) {
            count++
            foundTypes.add("Bank IBAN")
            "████████████████████"
        }

        // 4. Passport numbers
        val passportRegex = Regex("""(?i)(?:passport(?:\s*(?:no|number))?|pp\s*no|document\s*no)[\s.:#]+([A-Z0-9]{7,10})""")
        result = passportRegex.replace(result) { mr ->
            count++
            foundTypes.add("Passport Number")
            val num = mr.groupValues[1]
            mr.value.replace(num, "████████")
        }

        return RedactionResult(result, count, foundTypes.toList())
    }
}
