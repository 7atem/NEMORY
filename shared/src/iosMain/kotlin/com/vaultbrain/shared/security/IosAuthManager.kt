package com.vaultbrain.shared.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Foundation.NSError
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosAuthManager : AuthManager {
    @OptIn(ExperimentalForeignApi::class)
    override fun canAuthenticate(): Boolean {
        val context = LAContext()
        return context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun authenticate(reason: String): AuthResult = suspendCoroutine { continuation ->
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)) {
            continuation.resume(AuthResult.NOT_AVAILABLE)
            return@suspendCoroutine
        }

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = reason
        ) { success, error: NSError? ->
            if (success) {
                continuation.resume(AuthResult.SUCCESS)
            } else {
                // Determine if it was cancelled or an error
                // LAErrorUserCancel is -2
                if (error?.code?.toLong() == -2L) {
                    continuation.resume(AuthResult.CANCELLED)
                } else {
                    continuation.resume(AuthResult.ERROR)
                }
            }
        }
    }
}
