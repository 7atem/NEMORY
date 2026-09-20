package com.vaultbrain.shared.security

/**
 * Result of a biometric authentication attempt.
 */
enum class AuthResult {
    SUCCESS,
    ERROR,
    CANCELLED,
    NOT_AVAILABLE
}

/**
 * Cross-platform biometric authentication manager.
 * - Android: Uses BiometricPrompt.
 * - iOS: Uses LocalAuthentication (FaceID/TouchID).
 */
interface AuthManager {
    /** Returns true if biometrics are enrolled and available. */
    fun canAuthenticate(): Boolean

    /**
     * Triggers the biometric prompt and suspends until a result is returned.
     * @param reason The reason displayed to the user (e.g. "Unlock Nemory").
     */
    suspend fun authenticate(reason: String): AuthResult
}

/** CompositionLocal for providing the platform-specific AuthManager. */
val LocalAuthManager = androidx.compose.runtime.staticCompositionLocalOf<AuthManager?> { null }
