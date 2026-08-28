package com.vaultbrain.sync.gmail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GmailParserTest {

    private val parser = GmailParser()

    @Test
    fun `extracts order confirmation details from email body`() {
        val subject = "Your Amazon.eg order #408-1234567-8901234 has shipped"
        val body = """
            Hello Ahmed,
            Thank you for shopping with Amazon.
            Order #408-1234567-8901234
            Tracking Number: AWB9876543210
            Grand Total: EGP 1,450.00
        """.trimIndent()

        val receipt = parser.parseEmailContent(subject, body)

        assertThat(receipt.merchant).isEqualTo("Amazon")
        assertThat(receipt.orderNumber).isEqualTo("408-1234567-8901234")
        assertThat(receipt.trackingNumber).isEqualTo("AWB9876543210")
        assertThat(receipt.totalAmount).isEqualTo(1450.00)
        assertThat(receipt.currency).isEqualTo("EGP")
    }

    @Test
    fun `extracts Noon order receipt`() {
        val subject = "Noon invoice for order N-8849201"
        val body = "Total: 350.00 SAR. Delivered to Riyadh."

        val receipt = parser.parseEmailContent(subject, body)

        assertThat(receipt.merchant).isEqualTo("Noon")
        assertThat(receipt.orderNumber).isEqualTo("N-8849201")
        assertThat(receipt.totalAmount).isEqualTo(350.00)
        assertThat(receipt.currency).isEqualTo("SAR")
    }
}
