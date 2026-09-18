package com.vaultbrain.feature.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.security.BiometricPromptHelper

/**
 * Full-screen auth gate — the first screen users see on app launch.
 *
 * - If no PIN is set → show first-time PIN setup
 * - If biometric enabled → fire prompt automatically
 * - Otherwise → show PIN entry
 */
@Composable
fun AuthGateScreen(
    onUnlocked: (isDecoyMode: Boolean) -> Unit,
    viewModel: AuthGateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricPromptHelper() }

    // Auto-fire biometric on first composition if available
    LaunchedEffect(state.isBiometricEnabled) {
        if (state.isPinSet && state.isBiometricEnabled && !state.isUnlocked && activity != null) {
            if (biometricHelper.canAuthenticate(activity)) {
                biometricHelper.showPrompt(
                    activity = activity,
                    title = context.getString(R.string.auth_unlock_title),
                    subtitle = context.getString(R.string.auth_unlock_subtitle),
                    negativeButtonText = context.getString(R.string.auth_use_pin),
                    onSuccess = { viewModel.onBiometricSuccess() },
                    onError = { _, _ -> /* user cancelled, show PIN form */ }
                )
            }
        }
    }

    // Navigate away when unlocked
    LaunchedEffect(state.isUnlocked) {
        if (state.isUnlocked) {
            onUnlocked(state.isDecoyMode)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background
                    ),
                    center = Offset(0.5f, 0.3f),
                    radius = 800f
                )
            )
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo area
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Nemory",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = stringResource(R.string.auth_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (state.showPinSetup) {
                FirstTimePinSetup(
                    onPinCreated = { pin -> viewModel.setupPin(pin) }
                )
            } else {
                PinEntryForm(
                    error = state.pinError,
                    isBiometricEnabled = state.isBiometricEnabled,
                    onPinSubmit = { pin -> viewModel.verifyPin(pin) },
                    onBiometricClick = {
                        if (activity != null && biometricHelper.canAuthenticate(activity)) {
                            biometricHelper.showPrompt(
                                activity = activity,
                                title = context.getString(R.string.auth_unlock_title),
                                subtitle = context.getString(R.string.auth_unlock_subtitle),
                                negativeButtonText = context.getString(R.string.auth_use_pin),
                                onSuccess = { viewModel.onBiometricSuccess() },
                                onError = { _, _ -> }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PinEntryForm(
    error: String?,
    isBiometricEnabled: Boolean,
    onPinSubmit: (String) -> Unit,
    onBiometricClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 8 && it.all(Char::isDigit)) {
                    pin = it
                    if (it.length == 4) {
                        onPinSubmit(it)
                    }
                }
            },
            label = { Text(stringResource(R.string.auth_enter_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (pin.length >= 4) onPinSubmit(pin)
                }
            ),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { if (pin.length >= 4) onPinSubmit(pin) },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.auth_unlock))
        }

        if (isBiometricEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(
                onClick = onBiometricClick,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = stringResource(R.string.auth_use_biometrics),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FirstTimePinSetup(
    onPinCreated: (String) -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (step == 1) stringResource(R.string.auth_create_pin) else stringResource(R.string.auth_confirm_pin),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = stringResource(R.string.auth_pin_requirement),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        OutlinedTextField(
            value = if (step == 1) pin else confirmPin,
            onValueChange = {
                if (it.length <= 8 && it.all(Char::isDigit)) {
                    error = null
                    if (step == 1) pin = it else confirmPin = it
                }
            },
            label = { Text(if (step == 1) stringResource(R.string.auth_new_pin) else stringResource(R.string.auth_confirm_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (step == 1 && pin.length >= 4) {
                        step = 2
                    } else if (step == 2) {
                        if (confirmPin == pin) {
                            onPinCreated(pin)
                        } else {
                            error = context.getString(R.string.auth_pin_mismatch)
                            confirmPin = ""
                        }
                    }
                }
            ),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (step == 1 && pin.length >= 4) {
                    step = 2
                } else if (step == 2) {
                    if (confirmPin == pin) {
                        onPinCreated(pin)
                    } else {
                        error = context.getString(R.string.auth_pin_mismatch)
                        confirmPin = ""
                    }
                }
            },
            enabled = if (step == 1) pin.length >= 4 else confirmPin.length >= 4,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (step == 1) stringResource(R.string.auth_continue) else stringResource(R.string.auth_create_unlock))
        }

        if (step == 2) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { step = 1; confirmPin = ""; error = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.detail_back))
            }
        }
    }
}
