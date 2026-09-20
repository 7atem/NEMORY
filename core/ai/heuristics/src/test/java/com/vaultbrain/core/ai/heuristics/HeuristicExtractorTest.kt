package com.vaultbrain.core.ai.heuristics

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.domain.LensId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class HeuristicExtractorTest {

    private lateinit var extractor: HeuristicExtractor

    @Before
    fun setup() {
        extractor = HeuristicExtractor()
    }

    @Test
    fun testExtractReceiptAccurately() {
        val ocrText = """
            TARGET STORE #1234
            123 Main St
            Date: 2026-08-23
            
            1x Apples     $2.99
            1x Milk       $3.49
            
            TAX:          $0.51
            TOTAL:        $6.99
            Visa ending in 4242
        """.trimIndent()

        val result = extractor.extract(
            text = ocrText,
            visionObjects = listOf("Paper", "Text", "Receipt"),
            neuralClassification = null,
            neuralConfidence = 0f
        )

        assertEquals(Classification.RECEIPT, result.inferredClassification)
        assertTrue(result.lensTags.contains(LensId.MONEY))
        assertEquals("TARGET STORE #1234", result.title) // Target heuristic handles it well
        assertEquals("4242", result.metadata["card_last4"])
        assertEquals("6.99", result.metadata["total"])
    }

    @Test
    fun testExtractBusinessCardAccurately() {
        val ocrText = """
            John Doe
            Software Engineer
            
            john.doe@example.com
            +1 555-0198
            https://johndoe.dev
        """.trimIndent()

        val result = extractor.extract(
            text = ocrText,
            visionObjects = listOf("Business card"),
            neuralClassification = null,
            neuralConfidence = 0f
        )

        assertEquals(Classification.BUSINESS_CARD, result.inferredClassification)
        assertTrue(result.lensTags.contains(LensId.BUREAUCRACY))
        assertEquals("John Doe", result.metadata["contact_name"])
        assertEquals("Software Engineer", result.metadata["job_title"])
        assertEquals("john.doe@example.com", result.metadata["email"])
    }

    @Test
    fun testExtractPrescriptionAccurately() {
        val ocrText = """
            Dr. Smith Clinic
            Patient: Jane Doe
            
            Rx: Amoxicillin 500mg
            Take 1 capsule twice daily
            Dosage: 500mg
            Refills: 3
        """.trimIndent()

        val result = extractor.extract(
            text = ocrText,
            visionObjects = listOf("Pill"),
            neuralClassification = null,
            neuralConfidence = 0f
        )

        assertEquals(Classification.PRESCRIPTION, result.inferredClassification)
        assertTrue(result.lensTags.contains(LensId.HEALTH))
        assertEquals("3", result.metadata["refills"])
        assertNotNull(result.alertDate) // Proactive 25-day reminder
    }

    @Test
    fun testExtractWithNoisyOcrText() {
        val ocrText = """
            T@RGET ST0RE #1234
            123 Main ${'$'}t
            D@te: 2026-08-23
            
            1x Appl3s     $2.99
            
            T0TAL:        $2.99
        """.trimIndent()

        val result = extractor.extract(
            text = ocrText,
            visionObjects = listOf("Receipt"),
            neuralClassification = Classification.RECEIPT,
            neuralConfidence = 0.9f
        )

        assertEquals(Classification.RECEIPT, result.inferredClassification)
        assertEquals("2.99", result.metadata["total"]) // Should fallback or extract correctly
    }

    @Test
    fun testExtractTicketAccurately() {
        val ocrText = """
            BOARDING PASS
            Passenger: Doe/John
            Flight: BA 123
            Date: 2026-10-15
            Depart: 14:30
            Gate: A12
            Seat: 14B
        """.trimIndent()

        val result = extractor.extract(
            text = ocrText,
            visionObjects = listOf("Ticket"),
            neuralClassification = null,
            neuralConfidence = 0f
        )

        assertEquals(Classification.TICKET, result.inferredClassification)
        assertTrue(result.lensTags.contains(LensId.TRAVEL))
        assertEquals("BA123", result.metadata["flight_number"])
        assertEquals("A12", result.metadata["gate"])
        assertEquals("14B", result.metadata["seat"])
        assertEquals("14:30", result.metadata["time"])
        assertNotNull(result.alertDate) // 24h before
    }

    @Test
    fun testExtractPassportAccurately() {
        val ocrText = """
            PASSPORT
            Type: P
            Country Code: USA
            Passport No: 123456789
            Date of Birth: 1980-05-15
            Expiry Date: 2030-01-01
        """.trimIndent()

        val result = extractor.extract(
            text = ocrText,
            visionObjects = listOf("Passport"),
            neuralClassification = null,
            neuralConfidence = 0f
        )

        assertEquals(Classification.PASSPORT, result.inferredClassification)
        assertTrue(result.lensTags.contains(LensId.BUREAUCRACY))
        assertEquals("123456789", result.metadata["document_number"])
        assertEquals("1980-05-15", result.metadata["dob"])
        assertNotNull(result.expiryDate)
        assertNotNull(result.alertDate) // 1 week before
    }

    @Test
    fun `national identity card is separate from passport and remains bureaucracy data`() {
        val result = extractor.extract(
            text = """
                NATIONAL IDENTITY CARD
                National ID: 29801011234567
                Date of Birth: 1998-01-01
                Expiry Date: 2030-06-30
            """.trimIndent(),
            visionObjects = listOf("Identity card")
        )

        assertEquals(Classification.IDENTITY_DOCUMENT, result.inferredClassification)
        assertTrue(result.lensTags.contains(LensId.BUREAUCRACY))
        assertEquals("29801011234567", result.metadata["document_number"])
        assertEquals("national_id", result.metadata["document_type"])
        assertEquals("1998-01-01", result.metadata["dob"])
        assertEquals("2030-06-30", result.metadata["expiry_date"])
        assertNotNull(result.expiryDate)
        assertNotNull(result.alertDate)
    }

    @Test
    fun `driver license gets its own typed identity subtype`() {
        val result = extractor.extract(
            """
                DRIVER LICENSE
                License Number: D12345678
                Expiry: 2031-12-10
            """.trimIndent()
        )

        assertEquals(Classification.IDENTITY_DOCUMENT, result.inferredClassification)
        assertEquals("driver_license", result.metadata["document_type"])
        assertEquals("D12345678", result.metadata["document_number"])
    }

    @Test
    fun `receipt amount uses locale-independent canonical decimal`() {
        val result = extractor.extract(
            """
                RECEIPT
                Date: 2026-08-23
                TOTAL: 1.234,56 EUR
            """.trimIndent()
        )

        assertEquals(Classification.RECEIPT, result.inferredClassification)
        assertEquals("1234.56", result.metadata["total"])
        assertEquals("EUR", result.metadata["currency"])
    }

    @Test
    fun testExtractInvoiceAccurately() {
        val ocrText = """
            INVOICE #9988
            Services Rendered
            
            Amount Due: $150.00
            Due Date: 2026-09-01
        """.trimIndent()

        val result = extractor.extract(
            text = ocrText,
            visionObjects = listOf("Document"),
            neuralClassification = null,
            neuralConfidence = 0f
        )

        assertEquals(Classification.INVOICE, result.inferredClassification)
        assertTrue(result.lensTags.contains(LensId.MONEY))
        assertEquals("150", result.metadata["total"])
        assertNotNull(result.expiryDate) // Due date mapped to expiry
        assertNotNull(result.alertDate) // 3 days before due date
    }

    @Test
    fun movieReviewLinkGoesToWatchlist() {
        val result = extractor.extract(
            "Dune: Part Two review\nhttps://www.imdb.com/title/tt15239678/"
        )

        assertEquals(Classification.MOVIE, result.inferredClassification)
        assertEquals("Dune: Part Two review", result.title)
        assertEquals("movie", result.metadata["media_type"])
        assertEquals("want_to_watch", result.metadata["media_status"])
        assertEquals("https://www.imdb.com/title/tt15239678/", result.metadata["provider_url"])
        assertTrue(result.lensTags.contains(LensId.MEDIA))
    }

    @Test
    fun tvSeriesScreenshotExtractsSeasonAndStatus() {
        val result = extractor.extract(
            "TV SERIES\nSeverance\nSeason 2 Episode 3\nSeries status watching"
        )

        assertEquals(Classification.TV_SERIES, result.inferredClassification)
        assertEquals("Severance", result.title)
        assertEquals("2", result.metadata["season"])
        assertEquals("3", result.metadata["episode"])
        assertEquals("watching", result.metadata["media_status"])
        assertTrue(result.lensTags.contains(LensId.MEDIA))
    }

    @Test
    fun bookReviewGoesToReadingList() {
        val result = extractor.extract(
            "BOOK REVIEW\nProject Hail Mary\nAuthor: Andy Weir"
        )

        assertEquals(Classification.BOOK, result.inferredClassification)
        assertEquals("Project Hail Mary", result.title)
        assertEquals("book", result.metadata["media_type"])
        assertEquals("want_to_read", result.metadata["media_status"])
        assertEquals("Andy Weir", result.metadata["author"])
        assertTrue(result.lensTags.contains(LensId.MEDIA))
    }

    @Test
    fun `smart document expiry extracts expiration dates from cards`() {
        val result = extractor.extract(
            """
                MEMBER NAME
                JOHN DOE
                VALID THRU 10/30
                4242 4242 4242 4534
            """.trimIndent()
        )
        
        assertNotNull(result.metadata["expiry_date"])
        assertEquals("2030-10-01", result.metadata["expiry_date"])
    }
    
    @Test
    fun `smart document expiry extracts expiration dates from IDs`() {
        val result = extractor.extract(
            """
                STATE OF CA
                DRIVER LICENSE
                DOB 01/01/1990
                EXP 12/28
            """.trimIndent()
        )
        
        assertNotNull(result.metadata["expiry_date"])
        assertEquals("2028-12-01", result.metadata["expiry_date"])
    }
}

