package com.vaultbrain.evaluation.perturbations

import kotlin.random.Random

class OcrNoisePerturber(seed: Long = 42L) {
    private val random = Random(seed)

    fun perturb(text: String, noiseLevel: Double): String {
        if (noiseLevel <= 0.0) return text
        if (text.isEmpty()) return text

        val chars = text.toCharArray()
        val result = StringBuilder()

        for (c in chars) {
            if (random.nextDouble() < noiseLevel) {
                // Apply a random perturbation
                when (random.nextInt(5)) {
                    0 -> { /* Deletion: skip adding character */ }
                    1 -> {
                        // Insertion: add a random alphanumeric character
                        result.append(c)
                        result.append(randomAlphanumeric())
                    }
                    2 -> {
                        // Substitution: replace with a similar character
                        result.append(getConfusion(c))
                    }
                    3 -> {
                        // Whitespace loss
                        if (c.isWhitespace()) {
                            /* skip */
                        } else {
                            result.append(c)
                        }
                    }
                    4 -> {
                        // Case swap
                        if (c.isUpperCase()) result.append(c.lowercaseChar())
                        else if (c.isLowerCase()) result.append(c.uppercaseChar())
                        else result.append(c)
                    }
                }
            } else {
                result.append(c)
            }
        }
        return result.toString()
    }

    private fun randomAlphanumeric(): Char {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return chars[random.nextInt(chars.length)]
    }

    private fun getConfusion(c: Char): Char {
        // Deterministic confusion sets typical of OCR
        return when (c) {
            '0' -> 'O'
            'O' -> '0'
            '1', 'l', 'I' -> listOf('1', 'l', 'I', '|').random(random)
            '5' -> 'S'
            'S' -> '5'
            '8' -> 'B'
            'B' -> '8'
            'Z' -> '2'
            '2' -> 'Z'
            '.' -> ','
            ',' -> '.'
            else -> randomAlphanumeric()
        }
    }
}
