package com.vaultbrain.feature.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceCapturePolicyTest {
    @Test
    fun `permission is requested when an on-device recognizer exists`() {
        assertThat(VoiceCapturePolicy.availability(36, false, true))
            .isEqualTo(VoiceCaptureAvailability.NEEDS_PERMISSION)
    }

    @Test
    fun `unsupported device is reported before requesting microphone permission`() {
        assertThat(VoiceCapturePolicy.availability(30, false, false))
            .isEqualTo(VoiceCaptureAvailability.ON_DEVICE_UNAVAILABLE)
    }

    @Test
    fun `api below 31 never claims on-device recognition`() {
        assertThat(VoiceCapturePolicy.availability(30, true, true))
            .isEqualTo(VoiceCaptureAvailability.ON_DEVICE_UNAVAILABLE)
    }

    @Test
    fun `missing on-device service never falls back to network`() {
        assertThat(VoiceCapturePolicy.availability(36, true, false))
            .isEqualTo(VoiceCaptureAvailability.ON_DEVICE_UNAVAILABLE)
    }

    @Test
    fun `supported api service and permission are ready`() {
        assertThat(VoiceCapturePolicy.availability(31, true, true))
            .isEqualTo(VoiceCaptureAvailability.READY_ON_DEVICE)
    }

    @Test
    fun `speech timeout maps to calm no-speech state`() {
        assertThat(voiceErrorMessage(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
            .isEqualTo(VoiceCaptureError.NO_SPEECH)
    }

    @Test
    fun `language unavailable has a distinct recovery message`() {
        assertThat(voiceErrorMessage(android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
            .isEqualTo(VoiceCaptureError.LANGUAGE_UNAVAILABLE)
    }

    @Test
    fun `unsupported language is distinct from a downloadable language`() {
        assertThat(voiceErrorMessage(android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
            .isEqualTo(VoiceCaptureError.LANGUAGE_NOT_SUPPORTED)
    }

    @Test
    fun `model preparation is unavailable before api 33`() {
        assertThat(VoiceCapturePolicy.modelPreparationMode(32))
            .isEqualTo(VoiceModelPreparationMode.UNAVAILABLE)
    }

    @Test
    fun `api 33 supports a request without progress callbacks`() {
        assertThat(VoiceCapturePolicy.modelPreparationMode(33))
            .isEqualTo(VoiceModelPreparationMode.REQUEST_ONLY)
    }

    @Test
    fun `api 34 and newer support observable model preparation`() {
        assertThat(VoiceCapturePolicy.modelPreparationMode(34))
            .isEqualTo(VoiceModelPreparationMode.OBSERVABLE)
    }

    @Test
    fun `only an unavailable language offers model preparation`() {
        assertThat(
            VoiceCapturePolicy.canPrepareLanguageModel(
                36,
                VoiceCaptureError.LANGUAGE_UNAVAILABLE
            )
        ).isTrue()
        assertThat(
            VoiceCapturePolicy.canPrepareLanguageModel(
                36,
                VoiceCaptureError.LANGUAGE_NOT_SUPPORTED
            )
        ).isFalse()
    }
}
