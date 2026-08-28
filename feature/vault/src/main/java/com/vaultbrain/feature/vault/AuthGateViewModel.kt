package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import com.vaultbrain.core.security.AuthManager
import com.vaultbrain.core.security.PinVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * UI state for the auth gate / lock screen.
 */
data class AuthGateUiState(
    val isPinSet: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isUnlocked: Boolean = false,
    val isDecoyMode: Boolean = false,
    val pinError: String? = null,
    val showPinSetup: Boolean = false
)

/**
 * ViewModel for the auth gate.
 *
 * If no PIN is configured yet, [showPinSetup] is true so first-time users
 * can create one. On subsequent launches the biometric prompt fires automatically.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthGateUiState(
            isPinSet = authManager.isPinSet,
            isBiometricEnabled = authManager.isBiometricEnabled,
            showPinSetup = !authManager.isPinSet
        )
    )
    val uiState: StateFlow<AuthGateUiState> = _uiState.asStateFlow()

    /**
     * Called when biometric authentication succeeds.
     */
    fun onBiometricSuccess() {
        _uiState.value = _uiState.value.copy(isUnlocked = true, isDecoyMode = false, pinError = null)
    }

    /**
     * Called when the user submits a PIN.
     */
    fun verifyPin(pin: String) {
        when (val result = authManager.verifyForUnlock(pin)) {
            is PinVerificationResult.Success -> _uiState.value = _uiState.value.copy(
                isUnlocked = true,
                isDecoyMode = result.isDecoyMode,
                pinError = null
            )
            is PinVerificationResult.Invalid -> _uiState.value = _uiState.value.copy(
                pinError = "Incorrect PIN. Try again. (${result.attemptsRemaining} attempts left)"
            )
            is PinVerificationResult.Locked -> {
                val seconds = (result.retryAfterMillis + 999L) / 1_000L
                _uiState.value = _uiState.value.copy(
                    pinError = "Too many failed attempts. Try again in $seconds seconds."
                )
            }
        }
    }

    /**
     * First-time PIN setup.
     */
    fun setupPin(pin: String) {
        authManager.setPin(pin)
        _uiState.value = _uiState.value.copy(
            isPinSet = true,
            isUnlocked = true,
            isDecoyMode = false,
            showPinSetup = false,
            pinError = null
        )
    }

    /**
     * Reset unlock state (called by auto-lock).
     */
    fun lock() {
        _uiState.value = _uiState.value.copy(isUnlocked = false, isDecoyMode = false, pinError = null)
    }
}
