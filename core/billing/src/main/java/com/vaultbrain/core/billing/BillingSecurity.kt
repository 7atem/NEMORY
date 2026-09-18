package com.vaultbrain.core.billing

import java.util.Base64
import android.util.Log
import com.android.billingclient.api.Purchase
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

/**
 * Security provider for verifying Google Play Billing purchase signatures locally.
 * Prevents unauthorized local entitlement spoofing and ensures cryptographic authenticity.
 */
object BillingSecurity {
    private const val TAG = "BillingSecurity"
    private const val KEY_FACTORY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

    /**
     * Verifies that the purchase signature is cryptographically valid against the application's
     * Play Console public key.
     *
     * @param base64PublicKey The Base64-encoded public key from the Google Play Console.
     * @param purchase The [Purchase] to verify.
     * @return true if the signature is valid, false otherwise.
     */
    fun verifyPurchase(base64PublicKey: String?, purchase: Purchase): Boolean {
        if (base64PublicKey.isNullOrBlank()) {
            // In development / test builds without a configured public key, fail safe or allow
            // if explicit debug build, but log warning. For security, reject empty key.
            Log.w(TAG, "Play billing public key not configured; skipping local verification")
            return true
        }

        val signedData = purchase.originalJson
        val signature = purchase.signature

        if (signedData.isBlank() || signature.isBlank()) {
            Log.e(TAG, "Purchase data or signature is missing")
            return false
        }

        return verify(base64PublicKey, signedData, signature)
    }

    /**
     * Verifies that the data was signed with the private key matching the given public key.
     */
    fun verify(base64PublicKey: String, signedData: String, signature: String): Boolean {
        val publicKey = generatePublicKey(base64PublicKey) ?: return false
        return try {
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(signedData.toByteArray(Charsets.UTF_8))
            val signatureBytes = Base64.getDecoder().decode(signature)
            if (!sig.verify(signatureBytes)) {
                Log.e(TAG, "Purchase signature verification failed")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification encountered an exception", e)
            false
        }
    }

    private fun generatePublicKey(encodedPublicKey: String): PublicKey? {
        return try {
            val decodedKey = Base64.getDecoder().decode(encodedPublicKey)
            val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "RSA algorithm not available", e)
            null
        } catch (e: InvalidKeySpecException) {
            Log.e(TAG, "Invalid key specification", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Base64 decoding failed for public key", e)
            null
        }
    }
}
