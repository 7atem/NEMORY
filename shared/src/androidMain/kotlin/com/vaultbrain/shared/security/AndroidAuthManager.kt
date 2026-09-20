package com.vaultbrain.shared.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAuthManager(private val activity: FragmentActivity) : AuthManager {

    override fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(reason: String): AuthResult = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        continuation.resume(AuthResult.SUCCESS)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // BIOMETRIC_ERROR_USER_CANCELED = 10, BIOMETRIC_ERROR_CANCELED = 5, BIOMETRIC_ERROR_NEGATIVE_BUTTON = 13
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            continuation.resume(AuthResult.CANCELLED)
                        } else {
                            continuation.resume(AuthResult.ERROR)
                        }
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("VaultBrain")
                .setSubtitle(reason)
                .setNegativeButtonText("Cancel")
                .build()
                
            try {
                prompt.authenticate(info)
            } catch (e: Exception) {
                continuation.resume(AuthResult.ERROR)
            }
        }
    }
}
