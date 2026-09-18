package com.vaultbrain.feature.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

private sealed interface VoiceDialogState {
    data object Checking : VoiceDialogState
    data object RequestingPermission : VoiceDialogState
    data object Listening : VoiceDialogState
    data object Processing : VoiceDialogState
    data object Unavailable : VoiceDialogState
    data class PreparingLanguageModel(val progress: Int? = null) : VoiceDialogState
    data object LanguageModelScheduled : VoiceDialogState
    data object LanguageModelPreparationFailed : VoiceDialogState
    data class Error(val error: VoiceCaptureError) : VoiceDialogState
}

/**
 * One-shot, on-device-only speech capture. It never creates the generic recognizer and never
 * treats EXTRA_PREFER_OFFLINE as a privacy boundary.
 */
@Composable
fun OnDeviceVoiceCaptureDialog(
    onDismiss: () -> Unit,
    onCaptured: (String) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<VoiceDialogState>(VoiceDialogState.Checking) }
    var partialText by remember { mutableStateOf("") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var startAttempt by remember { mutableStateOf(0) }
    var isDisposed by remember { mutableStateOf(false) }

    fun destroyRecognizer() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    fun prepareLanguageModel() {
        val mode = VoiceCapturePolicy.modelPreparationMode(Build.VERSION.SDK_INT)
        if (mode == VoiceModelPreparationMode.UNAVAILABLE) {
            state = VoiceDialogState.Error(VoiceCaptureError.LANGUAGE_UNAVAILABLE)
            return
        }
        destroyRecognizer()
        val created = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }.getOrNull()
        if (created == null) {
            state = VoiceDialogState.LanguageModelPreparationFailed
            return
        }
        recognizer = created
        state = VoiceDialogState.PreparingLanguageModel()
        val intent = recognitionIntent()
        runCatching {
            if (Build.VERSION.SDK_INT >= VoiceCapturePolicy.MIN_OBSERVABLE_MODEL_DOWNLOAD_API) {
                created.triggerModelDownload(
                    intent,
                    ContextCompat.getMainExecutor(context),
                    object : ModelDownloadListener {
                        override fun onProgress(completedPercent: Int) {
                            if (!isDisposed) {
                                state = VoiceDialogState.PreparingLanguageModel(
                                    completedPercent.coerceIn(0, 100)
                                )
                            }
                        }

                        override fun onSuccess() {
                            destroyRecognizer()
                            if (!isDisposed) startAttempt += 1
                        }

                        override fun onScheduled() {
                            destroyRecognizer()
                            if (!isDisposed) state = VoiceDialogState.LanguageModelScheduled
                        }

                        override fun onError(error: Int) {
                            destroyRecognizer()
                            if (!isDisposed) {
                                state = VoiceDialogState.LanguageModelPreparationFailed
                            }
                        }
                    }
                )
            } else {
                created.triggerModelDownload(intent)
                state = VoiceDialogState.LanguageModelScheduled
            }
        }.onFailure {
            destroyRecognizer()
            state = VoiceDialogState.LanguageModelPreparationFailed
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startAttempt += 1
        else state = VoiceDialogState.Error(VoiceCaptureError.PERMISSION_DENIED)
    }

    DisposableEffect(Unit) {
        isDisposed = false
        onDispose {
            isDisposed = true
            destroyRecognizer()
        }
    }

    LaunchedEffect(startAttempt) {
        destroyRecognizer()
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasOnDeviceRecognizer = Build.VERSION.SDK_INT >= VoiceCapturePolicy.MIN_ON_DEVICE_API &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        when (
            VoiceCapturePolicy.availability(
                sdkInt = Build.VERSION.SDK_INT,
                hasRecordAudioPermission = hasPermission,
                hasOnDeviceRecognizer = hasOnDeviceRecognizer
            )
        ) {
            VoiceCaptureAvailability.NEEDS_PERMISSION -> {
                state = VoiceDialogState.RequestingPermission
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            VoiceCaptureAvailability.ON_DEVICE_UNAVAILABLE -> {
                state = VoiceDialogState.Unavailable
            }
            VoiceCaptureAvailability.READY_ON_DEVICE -> {
                val created = runCatching {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }.getOrNull()
                if (created == null) {
                    state = VoiceDialogState.Unavailable
                    return@LaunchedEffect
                }
                recognizer = created
                created.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        state = VoiceDialogState.Listening
                    }

                    override fun onBeginningOfSpeech() {
                        state = VoiceDialogState.Listening
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        state = VoiceDialogState.Processing
                    }

                    override fun onError(error: Int) {
                        destroyRecognizer()
                        state = VoiceDialogState.Error(voiceErrorMessage(error))
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull(String::isNotBlank)
                            ?.trim()
                        destroyRecognizer()
                        if (text == null) {
                            state = VoiceDialogState.Error(VoiceCaptureError.NO_SPEECH)
                        } else {
                            onCaptured(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        partialText = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                state = VoiceDialogState.Listening
                runCatching { created.startListening(recognitionIntent()) }
                    .onFailure {
                        destroyRecognizer()
                        state = VoiceDialogState.Error(VoiceCaptureError.FAILED)
                    }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            destroyRecognizer()
            onDismiss()
        },
        icon = {
            Icon(
                if (state == VoiceDialogState.Unavailable) Icons.Default.Shield else Icons.Default.Mic,
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.voice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(messageFor(state)),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state is VoiceDialogState.PreparingLanguageModel || state in setOf(
                        VoiceDialogState.Checking,
                        VoiceDialogState.RequestingPermission,
                        VoiceDialogState.Listening,
                        VoiceDialogState.Processing
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        partialText.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                (state as? VoiceDialogState.PreparingLanguageModel)?.progress?.let { progress ->
                    Text(
                        stringResource(R.string.voice_model_download_progress, progress),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    stringResource(R.string.voice_privacy_receipt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            when (state) {
                is VoiceDialogState.Error -> {
                    val error = (state as VoiceDialogState.Error).error
                    if (VoiceCapturePolicy.canPrepareLanguageModel(Build.VERSION.SDK_INT, error)) {
                        TextButton(onClick = ::prepareLanguageModel) {
                            Text(stringResource(R.string.voice_prepare_language))
                        }
                    } else {
                        TextButton(onClick = onManualEntry) {
                            Text(stringResource(R.string.voice_type_instead))
                        }
                    }
                }
                VoiceDialogState.LanguageModelPreparationFailed ->
                    TextButton(onClick = ::prepareLanguageModel) {
                        Text(stringResource(R.string.voice_prepare_try_again))
                    }
                VoiceDialogState.LanguageModelScheduled ->
                    TextButton(onClick = { startAttempt += 1 }) {
                        Text(stringResource(R.string.voice_try_again))
                    }
                VoiceDialogState.Unavailable -> TextButton(onClick = onManualEntry) {
                    Text(stringResource(R.string.voice_type_instead))
                }
                else -> Unit
            }
        },
        dismissButton = {
            when (state) {
                is VoiceDialogState.Error -> {
                    val error = (state as VoiceDialogState.Error).error
                    if (VoiceCapturePolicy.canPrepareLanguageModel(Build.VERSION.SDK_INT, error)) {
                        TextButton(onClick = onManualEntry) {
                            Text(stringResource(R.string.voice_type_instead))
                        }
                    } else {
                        TextButton(onClick = { startAttempt += 1 }) {
                            Text(stringResource(R.string.voice_try_again))
                        }
                    }
                }
                VoiceDialogState.LanguageModelPreparationFailed,
                VoiceDialogState.LanguageModelScheduled -> TextButton(onClick = onManualEntry) {
                    Text(stringResource(R.string.voice_type_instead))
                }
                VoiceDialogState.Unavailable -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.voice_cancel))
                }
                else -> TextButton(onClick = {
                    destroyRecognizer()
                    onDismiss()
                }) { Text(stringResource(R.string.voice_cancel)) }
            }
        }
    )
}

private fun messageFor(state: VoiceDialogState): Int = when (state) {
    VoiceDialogState.Checking -> R.string.voice_checking
    VoiceDialogState.RequestingPermission -> R.string.voice_permission
    VoiceDialogState.Listening -> R.string.voice_listening
    VoiceDialogState.Processing -> R.string.voice_processing
    VoiceDialogState.Unavailable -> R.string.voice_unavailable
    is VoiceDialogState.PreparingLanguageModel -> R.string.voice_model_downloading
    VoiceDialogState.LanguageModelScheduled -> R.string.voice_model_scheduled
    VoiceDialogState.LanguageModelPreparationFailed -> R.string.voice_model_download_failed
    is VoiceDialogState.Error -> when (state.error) {
        VoiceCaptureError.NO_SPEECH -> R.string.voice_error_no_speech
        VoiceCaptureError.PERMISSION_DENIED -> R.string.voice_error_permission
        VoiceCaptureError.LANGUAGE_NOT_SUPPORTED -> R.string.voice_error_language_not_supported
        VoiceCaptureError.LANGUAGE_UNAVAILABLE -> R.string.voice_error_language
        VoiceCaptureError.BUSY -> R.string.voice_error_busy
        VoiceCaptureError.FAILED -> R.string.voice_error_failed
    }
}
