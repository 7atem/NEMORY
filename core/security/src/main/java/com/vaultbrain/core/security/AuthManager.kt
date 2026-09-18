package com.vaultbrain.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app unlock state, PIN storage (hashed), decoy vault passphrase, and auto-lock timer.
 */
import com.vaultbrain.core.common.security.DecoySessionState

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "vaultbrain_auth"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DECOY_ENABLED = "decoy_enabled"
        private const val KEY_DECOY_PIN_HASH = "decoy_pin_hash"
        private const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_LEVEL = "lockout_level"
        private const val KEY_LOCKOUT_UNTIL_MILLIS = "lockout_until_millis"
        private const val DEFAULT_AUTO_LOCK_MINUTES = 5
        private const val SALT_LENGTH_BYTES = 16
        private const val HASH_SEPARATOR = ":"
        private const val HASH_SCHEME = "pbkdf2-sha256"
        private const val PBKDF2_ITERATIONS = 210_000
        private const val HASH_LENGTH_BITS = 256
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val INITIAL_LOCKOUT_MILLIS = 30_000L
        private const val MAX_LOCKOUT_MILLIS = 60 * 60 * 1_000L
    }

    val isPinSet: Boolean
        get() = prefs.contains(KEY_PIN_HASH)

    val isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    val autoLockMinutes: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_MINUTES, DEFAULT_AUTO_LOCK_MINUTES)

    val isDecoyPinSet: Boolean
        get() = prefs.contains(KEY_DECOY_PIN_HASH)

    fun setDecoySession(active: Boolean) {
        DecoySessionState.setDecoyMode(active)
    }

    fun setPin(pin: String) {
        prefs.edit { putString(KEY_PIN_HASH, hashPin(pin)) }
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val matched = verifyStoredPin(stored, pin)
        if (matched) {
            migrateLegacyPinIfNeeded(KEY_PIN_HASH, stored, pin)
            DecoySessionState.setDecoyMode(false)
        }
        return matched
    }

    fun enableBiometric(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    }

    fun setDecoyPin(pin: String) {
        prefs.edit { putString(KEY_DECOY_PIN_HASH, hashPin(pin)) }
    }

    fun verifyDecoyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_DECOY_PIN_HASH, null) ?: return false
        val matched = verifyStoredPin(stored, pin)
        if (matched) {
            migrateLegacyPinIfNeeded(KEY_DECOY_PIN_HASH, stored, pin)
            DecoySessionState.setDecoyMode(true)
        }
        return matched
    }

    private fun verifyPinRaw(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return verifyStoredPin(stored, pin)
    }

    private fun verifyDecoyPinRaw(pin: String): Boolean {
        val stored = prefs.getString(KEY_DECOY_PIN_HASH, null) ?: return false
        return verifyStoredPin(stored, pin)
    }

    /**
     * Verifies either configured PIN and enforces a process-independent,
     * escalating cooldown after repeated failures.
     */
    fun verifyForUnlock(pin: String, nowMillis: Long = System.currentTimeMillis()): PinVerificationResult {
        val lockoutRemaining = lockoutRemainingMillis(nowMillis)
        if (lockoutRemaining > 0L) {
            return PinVerificationResult.Locked(lockoutRemaining)
        }

        // Compute both hashes unconditionally to prevent timing attacks
        val isDecoyMatch = verifyDecoyPinRaw(pin)
        val isRealMatch = verifyPinRaw(pin)

        if (isDecoyMatch) {
            val stored = prefs.getString(KEY_DECOY_PIN_HASH, null)
            if (stored != null) migrateLegacyPinIfNeeded(KEY_DECOY_PIN_HASH, stored, pin)
            DecoySessionState.setDecoyMode(true)
            clearFailedAttempts()
            return PinVerificationResult.Success(isDecoyMode = true)
        }
        if (isRealMatch) {
            val stored = prefs.getString(KEY_PIN_HASH, null)
            if (stored != null) migrateLegacyPinIfNeeded(KEY_PIN_HASH, stored, pin)
            DecoySessionState.setDecoyMode(false)
            clearFailedAttempts()
            return PinVerificationResult.Success(isDecoyMode = false)
        }

        val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (failedAttempts < MAX_FAILED_ATTEMPTS) {
            prefs.edit { putInt(KEY_FAILED_ATTEMPTS, failedAttempts) }
            return PinVerificationResult.Invalid(MAX_FAILED_ATTEMPTS - failedAttempts)
        }

        val lockoutLevel = prefs.getInt(KEY_LOCKOUT_LEVEL, 0).coerceAtMost(7)
        val duration = (INITIAL_LOCKOUT_MILLIS shl lockoutLevel).coerceAtMost(MAX_LOCKOUT_MILLIS)
        prefs.edit {
            putInt(KEY_FAILED_ATTEMPTS, 0)
            putInt(KEY_LOCKOUT_LEVEL, lockoutLevel + 1)
            putLong(KEY_LOCKOUT_UNTIL_MILLIS, nowMillis + duration)
        }
        return PinVerificationResult.Locked(duration)
    }

    fun lockoutRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_LOCKOUT_UNTIL_MILLIS, 0L) - nowMillis).coerceAtLeast(0L)

    fun setAutoLockMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_AUTO_LOCK_MINUTES, minutes) }
    }

    /**
     * Hashes a PIN with a unique random salt.
     * Format: "<hex salt>:<hex hash>".
     */
    private fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
        val hash = pbkdf2(pin, salt, PBKDF2_ITERATIONS)
        return listOf(HASH_SCHEME, PBKDF2_ITERATIONS, salt.toHex(), hash.toHex())
            .joinToString(HASH_SEPARATOR)
    }

    /**
     * Verifies a PIN against a stored "salt:hash" value.
     */
    private fun verifyStoredPin(stored: String, pin: String): Boolean {
        val parts = stored.split(HASH_SEPARATOR, limit = 2)
        return if (stored.startsWith("$HASH_SCHEME$HASH_SEPARATOR")) {
            val versionedParts = stored.split(HASH_SEPARATOR)
            if (versionedParts.size != 4) return false
            val iterations = versionedParts[1].toIntOrNull() ?: return false
            val salt = versionedParts[2].hexToBytesOrNull() ?: return false
            val expectedHash = versionedParts[3].hexToBytesOrNull() ?: return false
            constantTimeEquals(expectedHash, pbkdf2(pin, salt, iterations))
        } else {
            // Legacy v1 format: <salt>:<single SHA-256 hash>.
            if (parts.size != 2) return false
            val salt = parts[0].hexToBytesOrNull() ?: return false
            val expectedHash = parts[1].hexToBytesOrNull() ?: return false
            val actualHash = sha256(salt, pin.toByteArray(Charsets.UTF_8))
            constantTimeEquals(expectedHash, actualHash)
        }
    }

    private fun migrateLegacyPinIfNeeded(key: String, stored: String, pin: String) {
        if (!stored.startsWith("$HASH_SCHEME$HASH_SEPARATOR")) {
            prefs.edit { putString(key, hashPin(pin)) }
        }
    }

    private fun clearFailedAttempts() {
        prefs.edit {
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKOUT_LEVEL)
            remove(KEY_LOCKOUT_UNTIL_MILLIS)
        }
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun sha256(salt: ByteArray, password: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").run {
            update(salt)
            update(password)
            digest()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? = runCatching {
        require(length % 2 == 0) { "Hex string must have even length" }
        ByteArray(length / 2) { i ->
            (substring(i * 2, i * 2 + 2).toInt(16) and 0xFF).toByte()
        }
    }.getOrNull()

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}

sealed interface PinVerificationResult {
    data class Success(val isDecoyMode: Boolean) : PinVerificationResult
    data class Invalid(val attemptsRemaining: Int) : PinVerificationResult
    data class Locked(val retryAfterMillis: Long) : PinVerificationResult
}
