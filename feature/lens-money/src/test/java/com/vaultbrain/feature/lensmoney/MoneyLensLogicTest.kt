package com.vaultbrain.feature.lensmoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyLensLogicTest {

    @Test
    fun `validates IBAN with ISO mod 97 checksum`() {
        assertTrue(isValidIban("DE89 3704 0044 0532 0130 00"))
        assertTrue(isValidIban("GB82 WEST 1234 5698 7654 32"))
        assertFalse(isValidIban("DE89 3704 0044 0532 0130 01"))
        assertFalse(isValidIban("not-an-iban"))
    }
}
