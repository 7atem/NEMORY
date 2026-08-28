package com.vaultbrain.core.common.metadata

import com.vaultbrain.core.common.model.Classification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MetadataValueNormalizerTest {
    @Test
    fun `normalizes locale-specific decimal values`() {
        assertEquals("1284.5", MetadataValueNormalizer.normalize("total", "1,284.50"))
        assertEquals("1284.5", MetadataValueNormalizer.normalize("total", "1.284,50"))
        assertEquals("1284.5", MetadataValueNormalizer.normalize("total", "١٬٢٨٤٫٥٠"))
        assertEquals("1234567", MetadataValueNormalizer.normalize("total", "1,234,567"))
    }

    @Test
    fun `normalizes recognized currencies and rejects invalid codes`() {
        assertEquals("USD", MetadataValueNormalizer.normalize("currency", "$"))
        assertEquals("EUR", MetadataValueNormalizer.normalize("currency", "€"))
        assertEquals("EGP", MetadataValueNormalizer.normalize("currency", "ج.م"))
        assertNull(MetadataValueNormalizer.normalize("currency", "ABC"))
    }

    @Test
    fun `accepts strict dates and rejects ambiguous or invalid dates`() {
        assertEquals("2027-04-13", MetadataValueNormalizer.normalize("due_date", "13/04/2027"))
        assertEquals("2027-04-03", MetadataValueNormalizer.normalize("due_date", "2027/4/3"))
        assertEquals("2027-04-13", MetadataValueNormalizer.normalize("due_date", "13 Apr 2027"))
        assertNull(MetadataValueNormalizer.normalize("due_date", "03/04/2027"))
        assertNull(MetadataValueNormalizer.normalize("due_date", "2027-02-30"))
    }

    @Test
    fun `typed output drops invalid fields while legacy normalization preserves visible data`() {
        val values = mapOf("due_date" to "03/04/2027", "total" to "42.00")

        assertEquals(mapOf("total" to "42"), MetadataValueNormalizer.normalizeTypedValues(values))
        assertEquals("03/04/2027", MetadataValueNormalizer.normalizeExisting(values)["due_date"])
    }

    @Test
    fun `projects identity expiry into indexed and reminder dates`() {
        val expiry = epoch("2030-01-01")
        val projection = MetadataDateProjector.project(
            classification = Classification.IDENTITY_DOCUMENT,
            metadata = mapOf("expiry_date" to "2030-01-01"),
            now = epoch("2029-01-01")
        )

        assertEquals(expiry, projection.expiryDate)
        assertEquals(expiry - 7 * DAY_MILLIS, projection.secondaryAlertDate)
        assertTrue(checkNotNull(projection.secondaryAlertDate) > epoch("2029-01-01"))
    }

    private fun epoch(date: String): Long = LocalDate.parse(date)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1_000
    }
}
