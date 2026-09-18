package com.vaultbrain.core.security

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class AuthManagerTest {

    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val storage = mutableMapOf<String, Any?>()

    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            storage[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            storage[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            storage[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            storage[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.remove(any()) } answers {
            storage.remove(firstArg())
            editor
        }
        every { editor.apply() } returns Unit
        every { prefs.getString(any(), any()) } answers {
            (storage[firstArg()] as? String) ?: secondArg()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            (storage[firstArg()] as? Boolean) ?: secondArg()
        }
        every { prefs.getInt(any(), any()) } answers {
            (storage[firstArg()] as? Int) ?: secondArg()
        }
        every { prefs.getLong(any(), any()) } answers {
            (storage[firstArg()] as? Long) ?: secondArg()
        }
        every { prefs.contains(any()) } answers {
            storage.containsKey(firstArg())
        }

        authManager = AuthManager(context)
    }

    @Test
    fun `sets and verifies master PIN correctly`() {
        authManager.setPin("1234")

        assertThat(authManager.isPinSet).isTrue()
        assertThat(authManager.verifyPin("1234")).isTrue()
        assertThat(authManager.verifyPin("0000")).isFalse()
        assertThat(com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value).isFalse()
    }

    @Test
    fun `sets and verifies decoy PIN activating decoy session`() {
        authManager.setPin("1234")
        authManager.setDecoyPin("9999")

        assertThat(authManager.isDecoyPinSet).isTrue()
        assertThat(authManager.verifyDecoyPin("9999")).isTrue()
        assertThat(com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value).isTrue()

        // Unlocking with master PIN switches decoy session off
        assertThat(authManager.verifyPin("1234")).isTrue()
        assertThat(com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value).isFalse()
    }

    @Test
    fun `persists lockout after five failed attempts and unlocks after cooldown`() {
        authManager.setPin("1234")
        val now = 1_000_000L

        repeat(4) { attempt ->
            assertThat(authManager.verifyForUnlock("0000", now))
                .isEqualTo(PinVerificationResult.Invalid(4 - attempt))
        }
        assertThat(authManager.verifyForUnlock("0000", now))
            .isEqualTo(PinVerificationResult.Locked(30_000L))
        assertThat(authManager.verifyForUnlock("1234", now + 1_000L))
            .isEqualTo(PinVerificationResult.Locked(29_000L))
        assertThat(authManager.verifyForUnlock("1234", now + 30_000L))
            .isEqualTo(PinVerificationResult.Success(isDecoyMode = false))
    }
}
