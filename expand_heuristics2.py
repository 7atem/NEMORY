import re

with open(r'd:\Vault Brain\core\ai\heuristics\src\main\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractor.kt', 'r', encoding='utf-8') as f:
    content = f.read()

new_patterns = '''        private val PAYMENT_METHOD_PATTERN = """(?i)\\b(?:visa|mastercard|amex|card|ending in)[\\s:#*-]*(\\d{4})\\b""".toRegex()
        private val REFILL_PATTERN = """(?i)\\b(?:refills?|qty|quantity)[\\s:#*-]*(\\d+)\\b""".toRegex()
        private val SEAT_PATTERN = """(?i)\\b(?:seat)[\\s:#*-]*([A-Z0-9]{1,4})\\b""".toRegex()
        private val TIME_PATTERN = """(?i)\\b(?:time|board|depart|departure)[\\s:#*-]*(\\d{1,2}:\\d{2})\\b""".toRegex()
        private val DOB_PATTERN = """(?i)\\b(?:dob|date of birth|born)[\\s:#*-]*(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})\\b""".toRegex()
        private val DUE_DATE_PATTERN = """(?i)\\bdue\\s+(?:date)?[\\s:#*-]*(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})\\b""".toRegex()
'''

content = content.replace('        private val TAX_PATTERN', new_patterns + '        private val TAX_PATTERN')

new_metadata = '''
            when (classification) {
                Classification.RECEIPT, Classification.INVOICE -> text.lineSequence().firstOrNull { it.isNotBlank() }
                    ?.take(80)?.let { merchant ->
                        put("merchant", merchant.trim())
                        TAX_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("tax", it.replace(",", "")) }
                        PAYMENT_METHOD_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("card_last4", it) }
                        val lineItems = RECEIPT_LINE_PATTERN.findAll(text)
                            .map { it.value.trim() }
                            .filterNot { it.contains("total", true) || it.contains("tax", true) }
                            .take(20)
                            .toList()
                        if (lineItems.isNotEmpty()) put("items", lineItems.joinToString(" | "))
                    }
                Classification.PRESCRIPTION -> {
                    DOSAGE_PATTERN.find(text)?.value?.let { put("dosage", it) }
                    FREQUENCY_PATTERN.find(text)?.value?.let { put("frequency", it) }
                    DOCTOR_PATTERN.find(text)?.value?.let { put("doctor", it.trim()) }
                    REFILL_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("refills", it) }
                }
                Classification.LAB_RESULT -> {
                    LAB_VALUE_PATTERN.find(text)?.let { match ->
                        put("lab_metric", match.groupValues[1].trim())
                        put("lab_value", match.groupValues[2])
                    }
                }
                Classification.PASSPORT -> {
                    PASSPORT_NUMBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")?.let { put("document_number", it) }
                    DOB_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("dob", it) }
                }
                Classification.TICKET -> {
                    FLIGHT_PATTERN.find(text)?.value?.let { put("flight_number", it.replace(" ", "")) }
                    GATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("gate", it) }
                    SEAT_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("seat", it) }
                    TIME_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("time", it) }
                }
                Classification.BUSINESS_CARD -> {
                    val lines = text.lineSequence().filter { it.isNotBlank() && it.length < 50 }.take(2).toList()
                    if (lines.size >= 1) put("contact_name", lines[0].trim())
                    if (lines.size >= 2) put("job_title", lines[1].trim())
                }
                else -> Unit
            }'''

# Extract out the old block to strictly replace it
old_metadata_start = content.find('            when (classification) {')
old_metadata_end = content.find('            }', old_metadata_start) + 13
if old_metadata_start != -1 and old_metadata_end != -1:
    content = content[:old_metadata_start] + new_metadata + content[old_metadata_end:]

new_parse_dates = '''
        val now = System.currentTimeMillis()
        
        // Find explicitly labeled due dates for invoices
        val dueDateMatch = DUE_DATE_PATTERN.find(text)
        val explicitDueDate = dueDateMatch?.groupValues?.getOrNull(1)?.let { dateStr ->
            parsers.firstNotNullOfOrNull { parser -> runCatching { parser.parse(dateStr) }.getOrNull() }?.time
        }
        
        val future = parsed.firstOrNull { it.time >= now }
        val futureDate = explicitDueDate ?: future?.time
        
        val expiry = futureDate.takeIf {
            classification in setOf(Classification.PASSPORT, Classification.WARRANTY_CARD, Classification.SERIAL_PLATE, Classification.INVOICE)
        }

        val alert = when (classification) {
            Classification.PASSPORT, Classification.WARRANTY_CARD, Classification.SERIAL_PLATE ->
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
'''

old_parse_dates_start = content.find('        val now = System.currentTimeMillis()')
old_parse_dates_end = content.find('        }', old_parse_dates_start) + 9
if old_parse_dates_start != -1 and old_parse_dates_end != -1:
    content = content[:old_parse_dates_start] + new_parse_dates + content[old_parse_dates_end:]

content = content.replace('parseDates(dates: List<String>, classification: Classification)', 'parseDates(dates: List<String>, classification: Classification, text: String)')
content = content.replace('parseDates(dates, fusedClassification)', 'parseDates(dates, fusedClassification, text)')

with open(r'd:\Vault Brain\core\ai\heuristics\src\main\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractor.kt', 'w', encoding='utf-8') as f:
    f.write(content)
