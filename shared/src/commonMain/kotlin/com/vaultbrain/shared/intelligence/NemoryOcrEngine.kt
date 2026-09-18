package com.vaultbrain.shared.intelligence

/**
 * Result data class for OCR text extraction.
 */
data class OcrResult(
    val rawText: String,
    val confidence: Float,
    val detectedLanguage: String = "en"
)

/**
 * Common OCR engine contract.
 * On iOS: Bridges Apple Vision Framework (VNRecognizeTextRequest).
 * On Android: Bridges ML Kit Text Recognition.
 */
interface NemoryOcrEngine {
    /** Performs text recognition on raw image bytes. */
    suspend fun recognizeText(imageBytes: ByteArray): Result<OcrResult>
}
