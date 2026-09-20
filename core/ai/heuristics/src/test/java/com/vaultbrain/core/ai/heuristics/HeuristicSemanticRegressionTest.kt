package com.vaultbrain.core.ai.heuristics

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.domain.LensId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A semantic regression suite intended to safeguard the heuristic extraction engine.
 *
 * The engine relies on hundreds of regex rules which function as a hand-built classifier.
 * This test suite asserts that known "golden documents" resolve to the exact expected
 * classifications, lenses, and dates without drifting or producing false positives.
 */
class HeuristicSemanticRegressionTest {

    private lateinit var extractor: HeuristicExtractor

    @Before
    fun setup() {
        extractor = HeuristicExtractor()
    }

    data class GoldenDocument(
        val name: String,
        val text: String,
        val expectedLens: String,
        val expectedClassification: Classification,
        val expectedMetadata: Map<String, String> = emptyMap()
    )

    private val goldenCorpus = listOf(
        GoldenDocument(
            name = "Credit Card with MM/YY Expiry",
            text = """
                ACME BANK
                CREDIT CARD
                4242 4242 4242 4534
                VALID THRU 10/30
                JOHN DOE
            """.trimIndent(),
            expectedLens = LensId.MONEY,
            expectedClassification = Classification.CREDIT_CARD,
            expectedMetadata = mapOf("expiry_date" to "2030-10-01")
        ),
        GoldenDocument(
            name = "Passport with MRZ and Explicit Expiry",
            text = """
                PASSPORT
                P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
                L898902C36UTO7408122F1204159ZE184226B<<<<<10
                EXP 15 APR 2012
            """.trimIndent(),
            expectedLens = LensId.BUREAUCRACY,
            expectedClassification = Classification.PASSPORT,
            expectedMetadata = mapOf(
                "expiry_date" to "2012-04-15",
                "dob" to "1974-08-12",
                "document_number" to "L898902C3",
                "issuer" to "UTO",
                "gender" to "F"
            )
        ),
        GoldenDocument(
            name = "Flight Ticket with Dates",
            text = """
                BOARDING PASS
                FLIGHT BA123
                LONDON LHR TO NEW YORK JFK
                DEPARTURE 12 NOV 2026 14:30
                SEAT 12A
            """.trimIndent(),
            expectedLens = LensId.TRAVEL,
            expectedClassification = Classification.TICKET,
            expectedMetadata = mapOf("date" to "2026-11-12")
        ),
        GoldenDocument(
            name = "Generic Bank Statement",
            text = """
                ACCOUNT STATEMENT
                STATEMENT PERIOD 01 JAN 2026 - 31 JAN 2026
                OPENING BALANCE $1,234.56
                CLOSING BALANCE $1,500.00
                MERCHANT: AIRPORT COFFEE SHOP $4.50
            """.trimIndent(),
            expectedLens = LensId.MONEY,
            expectedClassification = Classification.BANK_STATEMENT,
            expectedMetadata = emptyMap() // "date" could be extracted depending on strictness
        ),
        GoldenDocument(
            name = "Prescription with Refills",
            text = """
                DR. SMITH CLINIC
                PATIENT: JOHN DOE
                Rx: AMOXICILLIN 500MG
                TAKE 1 TABLET TWICE DAILY
                REFILLS: 2
                DATE: 05/15/2026
            """.trimIndent(),
            expectedLens = LensId.HEALTH,
            expectedClassification = Classification.PRESCRIPTION,
            expectedMetadata = mapOf("refills" to "2", "prescription_date" to "2026-05-15")
        )
    )

    @Test
    fun `verify golden corpus regressions`() {
        var failures = 0
        val failureMessages = mutableListOf<String>()

        for (doc in goldenCorpus) {
            val result = extractor.extract(doc.text, emptyList())

            val errors = mutableListOf<String>()
            
            if (result.lensTags.firstOrNull() != doc.expectedLens) {
                errors.add("Expected Lens ${doc.expectedLens} but got ${result.lensTags.firstOrNull()}")
            }

            if (result.inferredClassification != doc.expectedClassification) {
                errors.add("Expected Classification ${doc.expectedClassification} but got ${result.inferredClassification}")
            }

            for ((key, expectedValue) in doc.expectedMetadata) {
                val actualValue = result.metadata[key]
                if (actualValue != expectedValue) {
                    errors.add("Expected metadata '$key' = '$expectedValue', but got '$actualValue'")
                }
            }

            if (errors.isNotEmpty()) {
                failures++
                failureMessages.add("Golden Doc '${doc.name}' failed:\n" + errors.joinToString("\n") { "  - $it" })
            }
        }

        assertTrue(
            "Semantic Regression Suite failed with $failures failures:\n" + failureMessages.joinToString("\n\n"),
            failures == 0
        )
    }
}
