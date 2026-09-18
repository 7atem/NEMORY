package com.vaultbrain.feature.lensbureaucracy

data class MrzInfo(
    val documentNumber: String,
    val nationality: String?,
    val birthDate: String?,
    val expiryDate: String?,
    val isChecksumValid: Boolean
)

/** Minimal ICAO 9303 TD3 passport MRZ parser with check-digit validation. */
object MrzParser {
    fun parse(text: String): MrzInfo? {
        val lines = text.lineSequence()
            .map { it.uppercase().filter { char -> char.isLetterOrDigit() || char == '<' } }
            .filter { it.length >= 28 }
            .toList()
        val data = lines.firstOrNull { line ->
            line.length >= 28 && line.take(9).any(Char::isDigit) && line[9].isDigit()
        } ?: return null

        val documentNumber = data.substring(0, 9).replace("<", "").trim()
        if (documentNumber.isBlank()) return null
        val documentValid = checkDigit(data.substring(0, 9)) == data[9].digitToIntOrNull()
        val birthValid = data.getOrNull(19)?.digitToIntOrNull()?.let {
            checkDigit(data.substring(13, 19)) == it
        } ?: false
        val expiryValid = data.getOrNull(27)?.digitToIntOrNull()?.let {
            checkDigit(data.substring(21, 27)) == it
        } ?: false
        return MrzInfo(
            documentNumber = documentNumber,
            nationality = data.substring(10, 13).replace("<", "").takeIf(String::isNotBlank),
            birthDate = data.substring(13, 19).takeIf { it.all(Char::isDigit) },
            expiryDate = data.substring(21, 27).takeIf { it.all(Char::isDigit) },
            isChecksumValid = documentValid && birthValid && expiryValid
        )
    }

    internal fun checkDigit(value: String): Int {
        val weights = intArrayOf(7, 3, 1)
        return value.mapIndexed { index, char ->
            val number = when {
                char.isDigit() -> char.digitToInt()
                char in 'A'..'Z' -> char.code - 'A'.code + 10
                else -> 0
            }
            number * weights[index % weights.size]
        }.sum() % 10
    }
}
