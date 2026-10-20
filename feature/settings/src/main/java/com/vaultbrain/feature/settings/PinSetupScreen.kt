package com.vaultbrain.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val MIN_PIN_LENGTH = 6
private const val MAX_PIN_LENGTH = 12

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun PinSetupScreen(
    isDecoy: Boolean,
    onPinSet: (String) -> Unit,
    onCancel: () -> Unit
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentPin = if (isConfirmStep) confirmPin else firstPin
    val title = stringResource(if (isDecoy) R.string.pin_setup_decoy_title else R.string.pin_setup_title)
    val subtitle = stringResource(
        if (isConfirmStep) R.string.pin_setup_confirm_subtitle else R.string.pin_setup_enter_subtitle,
        MIN_PIN_LENGTH
    )
    val tooShortMessage = stringResource(R.string.pin_setup_too_short, MIN_PIN_LENGTH)
    val mismatchMessage = stringResource(R.string.pin_setup_mismatch)

    fun updatePin(newPin: String) {
        errorMessage = null
        if (newPin.length > MAX_PIN_LENGTH) return
        if (isConfirmStep) {
            confirmPin = newPin
        } else {
            firstPin = newPin
        }
    }

    fun moveToConfirm() {
        if (firstPin.length < MIN_PIN_LENGTH) {
            errorMessage = tooShortMessage
            return
        }
        errorMessage = null
        isConfirmStep = true
    }

    fun submit() {
        if (confirmPin.length < MIN_PIN_LENGTH) {
            errorMessage = tooShortMessage
            return
        }
        if (confirmPin != firstPin) {
            errorMessage = mismatchMessage
            confirmPin = ""
            return
        }
        onPinSet(firstPin)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                PinDots(
                    length = currentPin.length,
                    maxDots = MAX_PIN_LENGTH.coerceAtMost(8)
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            NumericPad(
                onDigit = { digit ->
                    if ((isConfirmStep && confirmPin.length < MAX_PIN_LENGTH) ||
                        (!isConfirmStep && firstPin.length < MAX_PIN_LENGTH)
                    ) {
                        updatePin(currentPin + digit)
                    }
                },
                onBackspace = {
                    if (currentPin.isNotEmpty()) {
                        updatePin(currentPin.dropLast(1))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }
                Button(
                    onClick = { if (isConfirmStep) submit() else moveToConfirm() },
                    enabled = currentPin.length >= MIN_PIN_LENGTH,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            if (isConfirmStep) R.string.pin_setup_confirm else R.string.pin_setup_continue
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PinDots(length: Int, maxDots: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxDots) { index ->
            val filled = index < length
            Surface(
                modifier = Modifier.size(16.dp),
                shape = CircleShape,
                color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ) { }
        }
    }
}

@Composable
private fun NumericPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "\u232B")
        )
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    when {
                        key.isEmpty() -> Spacer(modifier = Modifier.weight(1f))
                        key == "\u232B" -> IconButton(
                            onClick = onBackspace,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = stringResource(R.string.pin_setup_backspace)
                            )
                        }
                        else -> NumericKey(
                            digit = key,
                            onClick = { onDigit(key) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericKey(
    digit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.3f)
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
