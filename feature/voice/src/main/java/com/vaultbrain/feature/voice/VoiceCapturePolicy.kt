package com.vaultbrain.feature.voice

enum class VoiceCaptureAvailability {
    NEEDS_PERMISSION,
    READY_ON_DEVICE,
    ON_DEVICE_UNAVAILABLE
}

/** Pure policy used before any microphone or recognizer API is opened. */
object VoiceCapturePolicy {
    const val MIN_ON_DEVICE_API = 31
    const val MIN_MODEL_DOWNLOAD_API = 33
    const val MIN_OBSERVABLE_MODEL_DOWNLOAD_API = 34

    fun availability(
        sdkInt: Int,
        hasRecordAudioPermission: Boolean,
        hasOnDeviceRecognizer: Boolean
    ): VoiceCaptureAvailability = when {
        sdkInt < MIN_ON_DEVICE_API -> VoiceCaptureAvailability.ON_DEVICE_UNAVAILABLE
        !hasOnDeviceRecognizer -> VoiceCaptureAvailability.ON_DEVICE_UNAVAILABLE
        !hasRecordAudioPermission -> VoiceCaptureAvailability.NEEDS_PERMISSION
        else -> VoiceCaptureAvailability.READY_ON_DEVICE
    }

    fun modelPreparationMode(sdkInt: Int): VoiceModelPreparationMode = when {
        sdkInt >= MIN_OBSERVABLE_MODEL_DOWNLOAD_API -> VoiceModelPreparationMode.OBSERVABLE
        sdkInt >= MIN_MODEL_DOWNLOAD_API -> VoiceModelPreparationMode.REQUEST_ONLY
        else -> VoiceModelPreparationMode.UNAVAILABLE
    }

    fun canPrepareLanguageModel(sdkInt: Int, error: VoiceCaptureError): Boolean =
        error == VoiceCaptureError.LANGUAGE_UNAVAILABLE &&
            modelPreparationMode(sdkInt) != VoiceModelPreparationMode.UNAVAILABLE
}

enum class VoiceModelPreparationMode {
    UNAVAILABLE,
    REQUEST_ONLY,
    OBSERVABLE
}

internal fun voiceErrorMessage(error: Int): VoiceCaptureError = when (error) {
    android.speech.SpeechRecognizer.ERROR_NO_MATCH,
    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceCaptureError.NO_SPEECH
    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceCaptureError.PERMISSION_DENIED
    android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
        VoiceCaptureError.LANGUAGE_NOT_SUPPORTED
    android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        VoiceCaptureError.LANGUAGE_UNAVAILABLE
    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceCaptureError.BUSY
    else -> VoiceCaptureError.FAILED
}

enum class VoiceCaptureError {
    NO_SPEECH,
    PERMISSION_DENIED,
    LANGUAGE_NOT_SUPPORTED,
    LANGUAGE_UNAVAILABLE,
    BUSY,
    FAILED
}
