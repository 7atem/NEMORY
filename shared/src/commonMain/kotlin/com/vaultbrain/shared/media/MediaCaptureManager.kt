package com.vaultbrain.shared.media

/**
 * Result from a media capture or picker operation.
 */
sealed class MediaCaptureResult {
    data class Success(val filePaths: List<String>) : MediaCaptureResult()
    data class Error(val exception: Exception) : MediaCaptureResult()
    data object Cancelled : MediaCaptureResult()
}

/**
 * Cross-platform media capture manager for picking photos or taking pictures.
 */
interface MediaCaptureManager {
    /**
     * Launches the native camera and returns the captured image file path.
     */
    suspend fun takePicture(): MediaCaptureResult

    /**
     * Launches the native gallery picker.
     * @param allowMultiple If true, allows selecting multiple images.
     */
    suspend fun pickImages(allowMultiple: Boolean): MediaCaptureResult
}

/** CompositionLocal for providing the platform-specific MediaCaptureManager. */
val LocalMediaCaptureManager = androidx.compose.runtime.staticCompositionLocalOf<MediaCaptureManager?> { null }
