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
 * Downloads the Gemma Tier 2 model to `filesDir/models/` over HttpURLConnection.
 *
 * The model streams to a `.partial` file with progress published via [KEY_PROGRESS], the
 * SHA-256 is verified, and the file is atomically renamed into place. Transient IO failures
 * retry (up to [MAX_RUN_ATTEMPTS] runs with WorkManager backoff); a hash mismatch or HTTP
 * error is a hard failure and deletes the partial file.
 */
@HiltWorker
class GemmaModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!GemmaDownloadConfig.isValid(
                url = BuildConfig.GEMMA_MODEL_URL,
                sha256 = BuildConfig.GEMMA_MODEL_SHA256,
                sizeBytes = BuildConfig.GEMMA_MODEL_SIZE_BYTES
            )
        ) {
            return Result.failure(workDataOf(KEY_ERROR to ERROR_INVALID_CONFIG))
        }
        setForeground(createForegroundInfo(progress = 0))
        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val partial = File(modelsDir, "${GemmaModelManager.MODEL_FILE_NAME}.partial")
        val target = File(modelsDir, GemmaModelManager.MODEL_FILE_NAME)
        return try {
            download(partial)
            val actualSha = GemmaModelFileVerifier.sha256(partial)
            if (!actualSha.equals(BuildConfig.GEMMA_MODEL_SHA256, ignoreCase = true)) {
                Log.w(TAG, "SHA-256 mismatch: expected ${BuildConfig.GEMMA_MODEL_SHA256}, got $actualSha")
                partial.delete()
                Result.failure(workDataOf(KEY_ERROR to ERROR_HASH_MISMATCH))
            } else {
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not replace existing model file")
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                Result.success()
            }
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

    private suspend fun download(partial: File) = withContext(Dispatchers.IO) {
        var existingBytes = partial.length()
        if (existingBytes > BuildConfig.GEMMA_MODEL_SIZE_BYTES) {
            partial.delete()
            existingBytes = 0L
        }
        if (existingBytes == BuildConfig.GEMMA_MODEL_SIZE_BYTES) return@withContext

        val connection = URL(BuildConfig.GEMMA_MODEL_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            if (existingBytes > 0L) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code downloading Gemma model")
            val append = existingBytes > 0L && code == HttpURLConnection.HTTP_PARTIAL
            var downloaded = if (append) existingBytes else 0L
            val total = BuildConfig.GEMMA_MODEL_SIZE_BYTES
            publishProgress(downloaded, total)
            connection.inputStream.buffered().use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastProgress = progressOf(downloaded, total)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = progressOf(downloaded, total)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            publishProgress(downloaded, total)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun publishProgress(downloaded: Long, total: Long) {
        val progress = progressOf(downloaded, total)
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
        private const val TAG = "GemmaDownload"
        private const val MAX_RUN_ATTEMPTS = 3
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val ERROR_INVALID_CONFIG = "INVALID_CONFIG"
        private const val ERROR_HASH_MISMATCH = "HASH_MISMATCH"
        private const val ERROR_NETWORK = "NETWORK"
    }
}
