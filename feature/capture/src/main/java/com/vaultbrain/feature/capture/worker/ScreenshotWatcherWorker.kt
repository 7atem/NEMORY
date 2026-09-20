package com.vaultbrain.feature.capture.worker

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.core.notifications.UnifiedAlertManager
import com.vaultbrain.shared.model.Classification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltWorker
class ScreenshotWatcherWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val latinRecognizer: TextRecognizer,
    private val heuristicExtractor: HeuristicExtractor,
    private val alertManager: UnifiedAlertManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val triggerUris = inputData.getStringArray("KEY_TRIGGER_URIS") ?: return Result.success()

        for (uriString in triggerUris) {
            val uri = Uri.parse(uriString)
            if (!isScreenshot(uri)) continue

            try {
                // 1. Vision OCR
                val inputImage = InputImage.fromFilePath(context, uri)
                val visionText = suspendCancellableCoroutine { continuation ->
                    latinRecognizer.process(inputImage)
                        .addOnSuccessListener { continuation.resume(it) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
                val text = visionText.text

                if (text.isBlank()) continue

                // 2. Fast Heuristic Extraction
                val result = heuristicExtractor.extract(text = text)

                // 3. Evaluate actionable intent
                val title = result.metadata["title"] ?: "this screenshot"
                when (result.inferredClassification) {
                    Classification.EVENT -> {
                        alertManager.sendMagicScreenshotAlert(
                            uri = uriString,
                            title = "Add event to Calendar?",
                            body = "Nemory detected an event: $title"
                        )
                    }
                    Classification.RECIPE -> {
                        alertManager.sendMagicScreenshotAlert(
                            uri = uriString,
                            title = "Save ingredients to Groceries?",
                            body = "Nemory detected a recipe: $title"
                        )
                    }
                    Classification.CHAT -> {
                        alertManager.sendMagicScreenshotAlert(
                            uri = uriString,
                            title = "Draft a reply to this?",
                            body = "Nemory detected a conversation"
                        )
                    }
                    Classification.FLIGHT_BOARDING_PASS -> {
                        alertManager.sendMagicScreenshotAlert(
                            uri = uriString,
                            title = "Track this flight?",
                            body = "Nemory detected flight info"
                        )
                    }
                    Classification.MOVIE, Classification.TV_SERIES, Classification.BOOK -> {
                        alertManager.sendMagicScreenshotAlert(
                            uri = uriString,
                            title = "Add to Weekend Watchlist?",
                            body = "Nemory detected a recommendation: $title"
                        )
                    }
                    else -> {
                        // Do not bother user for random unclassified screenshots
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze magic screenshot", e)
            }
        }
        return Result.success()
    }

    private fun isScreenshot(uri: Uri): Boolean {
        var isScreenshot = false
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(0) ?: ""
                isScreenshot = path.contains("Screenshots", ignoreCase = true)
            }
        }
        return isScreenshot
    }

    companion object {
        const val WORK_NAME = "screenshot_watcher"
        private const val TAG = "ScreenshotWatcher"
    }
}
