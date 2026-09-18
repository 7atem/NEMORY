package com.vaultbrain.core.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class BillingSecurityTest {

    @Test
    fun verify_validSignature_returnsTrue() {
        val keyGen = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }
        val keyPair = keyGen.generateKeyPair()
        val base64PublicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val signedData = """{"productId":"remove_ads","purchaseTime":1234567890}"""
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(signedData.toByteArray(Charsets.UTF_8))
        }
        val signature = Base64.getEncoder().encodeToString(sig.sign())

        val isValid = BillingSecurity.verify(base64PublicKey, signedData, signature)
        assertThat(isValid).isTrue()
    }

    @Test
    fun verify_tamperedData_returnsFalse() {
        val keyGen = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }
        val keyPair = keyGen.generateKeyPair()
        val base64PublicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val originalData = """{"productId":"remove_ads","purchaseTime":1234567890}"""
        val tamperedData = """{"productId":"remove_ads","purchaseTime":9999999999}"""
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(originalData.toByteArray(Charsets.UTF_8))
        }
        val signature = Base64.getEncoder().encodeToString(sig.sign())

        val isValid = BillingSecurity.verify(base64PublicKey, tamperedData, signature)
        assertThat(isValid).isFalse()
    }

    @Test
    fun verify_invalidKey_returnsFalse() {
        val isValid = BillingSecurity.verify("INVALID_BASE_64", "{}", "sig")
        assertThat(isValid).isFalse()
    }
}
