import re

with open(r'd:\Vault Brain\core\ai\heuristics\src\main\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractor.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: The duplicated parsing block for metadata
bad_metadata_pattern = r'                Classification\.PRESCRIPTION -> \{[\s\S]*?else -> Unit\s*\}'
# We only want to remove the SECOND occurrence. Or we can just remove all occurrences of this exact bad block 
# because the first one is the GOOD one which has much more fields (like REFILL). Wait, the bad one doesn't have REFILL.
# The bad one starts with Classification.PRESCRIPTION -> { \n DOSAGE_PATTERN... } without REFILL_PATTERN.
bad_block_exact = '''                Classification.PRESCRIPTION -> {
                    DOSAGE_PATTERN.find(text)?.value?.let { put("dosage", it) }
                    FREQUENCY_PATTERN.find(text)?.value?.let { put("frequency", it) }
                    DOCTOR_PATTERN.find(text)?.value?.let { put("doctor", it.trim()) }
                }
                Classification.LAB_RESULT -> {
                    LAB_VALUE_PATTERN.find(text)?.let { match ->
                        put("lab_metric", match.groupValues[1].trim())
                        put("lab_value", match.groupValues[2])
                    }
                }
                Classification.PASSPORT -> PASSPORT_NUMBER_PATTERN.find(text)
                    ?.groupValues?.getOrNull(1)
                    ?.replace(" ", "")
                    ?.let { put("document_number", it) }
                Classification.TICKET -> {
                    FLIGHT_PATTERN.find(text)?.value?.let { put("flight_number", it.replace(" ", "")) }
                    GATE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { put("gate", it) }
                }
                Classification.SERIAL_PLATE -> PLATE_PATTERN.find(text)
                    ?.groupValues?.getOrNull(1)?.let { put("plate", it.trim()) }
                else -> Unit
            }'''
content = content.replace(bad_block_exact, '')

# Fix 2: The duplicated alert block in parseDates
bad_alert_exact = '''
        val alert = when (classification) {
            Classification.PASSPORT, Classification.WARRANTY_CARD, Classification.SERIAL_PLATE ->
                expiry?.minus(7L * 24 * 60 * 60 * 1000)
            Classification.TICKET, Classification.HOTEL ->
                futureDate?.minus(24L * 60 * 60 * 1000)
            else -> null
        }'''
content = content.replace(bad_alert_exact, '')

with open(r'd:\Vault Brain\core\ai\heuristics\src\main\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractor.kt', 'w', encoding='utf-8') as f:
    f.write(content)
