package com.vaultbrain.core.ai.llm.gemma

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vaultbrain.core.ai.llm.BuildConfig
import com.vaultbrain.core.ai.llm.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the Qwen3-VL Tier 2 model package (language model + vision projector) to `filesDir/models/`.
 */
@HiltWorker
class OnDeviceModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!GemmaDownloadConfig.isValid(
                url = BuildConfig.QWEN_MODEL_URL,
                sha256 = BuildConfig.QWEN_MODEL_SHA256,
                sizeBytes = BuildConfig.QWEN_MODEL_SIZE_BYTES
            ) || !GemmaDownloadConfig.isValid(
                url = BuildConfig.QWEN_MMPROJ_URL,
                sha256 = BuildConfig.QWEN_MMPROJ_SHA256,
                sizeBytes = BuildConfig.QWEN_MMPROJ_SIZE_BYTES
            )
        ) {
            return Result.failure(workDataOf(KEY_ERROR to ERROR_INVALID_CONFIG))
        }

        setForeground(createForegroundInfo(progress = 0))
        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }

        val modelPartial = File(modelsDir, "${OnDeviceModelManager.MODEL_FILE_NAME}.partial")
        val modelTarget = File(modelsDir, OnDeviceModelManager.MODEL_FILE_NAME)

        val mmprojPartial = File(modelsDir, "${OnDeviceModelManager.MMPROJ_FILE_NAME}.partial")
        val mmprojTarget = File(modelsDir, OnDeviceModelManager.MMPROJ_FILE_NAME)

        val totalBytesCombined = BuildConfig.QWEN_MODEL_SIZE_BYTES + BuildConfig.QWEN_MMPROJ_SIZE_BYTES

        return try {
            // 1. Download language model if not target existing
            if (!modelTarget.exists()) {
                downloadFile(
                    urlStr = BuildConfig.QWEN_MODEL_URL,
                    expectedSizeBytes = BuildConfig.QWEN_MODEL_SIZE_BYTES,
                    partialFile = modelPartial,
                    bytesCompletedPrior = 0L,
                    totalBytesCombined = totalBytesCombined
                )
                val actualSha = GemmaModelFileVerifier.sha256(modelPartial)
                if (!actualSha.equals(BuildConfig.QWEN_MODEL_SHA256, ignoreCase = true)) {
                    Log.w(TAG, "Model SHA-256 mismatch: expected ${BuildConfig.QWEN_MODEL_SHA256}, got $actualSha")
                    modelPartial.delete()
                    return Result.failure(workDataOf(KEY_ERROR to ERROR_HASH_MISMATCH))
                }
                atomicRename(modelPartial, modelTarget)
            }

            // 2. Download mmproj vision projector if not target existing
            if (!mmprojTarget.exists()) {
                downloadFile(
                    urlStr = BuildConfig.QWEN_MMPROJ_URL,
                    expectedSizeBytes = BuildConfig.QWEN_MMPROJ_SIZE_BYTES,
                    partialFile = mmprojPartial,
                    bytesCompletedPrior = BuildConfig.QWEN_MODEL_SIZE_BYTES,
                    totalBytesCombined = totalBytesCombined
                )
                val actualSha = GemmaModelFileVerifier.sha256(mmprojPartial)
                if (!actualSha.equals(BuildConfig.QWEN_MMPROJ_SHA256, ignoreCase = true)) {
                    Log.w(TAG, "Mmproj SHA-256 mismatch: expected ${BuildConfig.QWEN_MMPROJ_SHA256}, got $actualSha")
                    mmprojPartial.delete()
                    return Result.failure(workDataOf(KEY_ERROR to ERROR_HASH_MISMATCH))
                }
                atomicRename(mmprojPartial, mmprojTarget)
            }

            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Model download failed (attempt ${runAttemptCount + 1})", e)
            if (runAttemptCount < MAX_RUN_ATTEMPTS - 1) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to ERROR_NETWORK))
            }
        } finally {
            applicationContext.getSystemService(NotificationManager::class.java)
                ?.cancel(NOTIFICATION_ID)
        }
    }

    private suspend fun downloadFile(
        urlStr: String,
        expectedSizeBytes: Long,
        partialFile: File,
        bytesCompletedPrior: Long,
        totalBytesCombined: Long
    ) = withContext(Dispatchers.IO) {
        var existingBytes = partialFile.length()
        if (existingBytes > expectedSizeBytes) {
            partialFile.delete()
            existingBytes = 0L
        }
        if (existingBytes == expectedSizeBytes) return@withContext

        val connection = URL(urlStr).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            if (existingBytes > 0L) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code downloading model artifact")

            val append = existingBytes > 0L && code == HttpURLConnection.HTTP_PARTIAL
            var downloadedInFile = if (append) existingBytes else 0L

            publishProgress(bytesCompletedPrior + downloadedInFile, totalBytesCombined)

            connection.inputStream.buffered().use { input ->
                FileOutputStream(partialFile, append).buffered().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastProgress = progressOf(bytesCompletedPrior + downloadedInFile, totalBytesCombined)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedInFile += read
                        val progress = progressOf(bytesCompletedPrior + downloadedInFile, totalBytesCombined)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            publishProgress(bytesCompletedPrior + downloadedInFile, totalBytesCombined)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun atomicRename(source: File, target: File) {
        if (target.exists() && !target.delete()) {
            throw IOException("Could not replace existing model file: ${target.name}")
        }
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private suspend fun publishProgress(downloadedTotal: Long, totalCombined: Long) {
        val progress = progressOf(downloadedTotal, totalCombined)
        setProgress(workDataOf(KEY_PROGRESS to progress))
        setForeground(createForegroundInfo(progress))
    }

    private fun progressOf(downloaded: Long, total: Long): Int =
        if (total <= 0L) 0 else ((downloaded * 100) / total).toInt().coerceIn(0, 100)

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (notificationManager?.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.gemma_download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = applicationContext.getString(R.string.gemma_download_channel_description)
                    setShowBadge(false)
                }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.gemma_download_notification_title))
            .setProgress(100, progress, progress == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        const val WORK_NAME = "gemma_model_download"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "gemma_model_download"
        private const val NOTIFICATION_ID = 4244
        private const val TAG = "OnDeviceDownload"
        private const val MAX_RUN_ATTEMPTS = 3
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val ERROR_INVALID_CONFIG = "INVALID_CONFIG"
        private const val ERROR_HASH_MISMATCH = "HASH_MISMATCH"
        private const val ERROR_NETWORK = "NETWORK"
    }
}

typealias GemmaModelDownloadWorker = OnDeviceModelDownloadWorker
