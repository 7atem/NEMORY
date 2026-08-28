package com.vaultbrain.core.ai.llm.gemma

import android.app.ActivityManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Constraints
import com.vaultbrain.core.ai.llm.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

/** Lifecycle of the optional on-device Gemma Tier 2 model. */
sealed interface GemmaModelStatus {
    data object NOT_SUPPORTED : GemmaModelStatus
    data object NOT_DOWNLOADED : GemmaModelStatus
    data object QUEUED : GemmaModelStatus
    data class DOWNLOADING(val progress: Int) : GemmaModelStatus
    data object READY : GemmaModelStatus
    data object ERROR : GemmaModelStatus
}

/**
 * Owns the Gemma 3 1B model file in `filesDir/models/` and its download work.
 *
 * READY requires the file to exist and match the configured SHA-256 (computed once per file
 * version via [GemmaModelFileVerifier]). Devices with less than 4 GB of RAM report
 * NOT_SUPPORTED and never attempt a download.
 */
@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val modelFile = File(context.filesDir, "models/$MODEL_FILE_NAME")
    private val partialFile = File(context.filesDir, "models/$MODEL_FILE_NAME.partial")

    private val supported: Boolean by lazy {
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return@lazy false
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        info.totalMem >= MIN_TOTAL_RAM_BYTES
    }

    private val _status = MutableStateFlow(
        if (supported) GemmaModelStatus.NOT_DOWNLOADED else GemmaModelStatus.NOT_SUPPORTED
    )
    val status: StateFlow<GemmaModelStatus> = _status.asStateFlow()

    init {
        if (supported) {
            scope.launch { refresh() }
            observeDownloadWork()
        }
    }

    fun isSupported(): Boolean = supported

    /** Absolute path of the verified model file; only valid when [status] is READY. */
    val modelPath: String
        get() = modelFile.absolutePath

    /** Re-derives the status from the on-disk file. */
    fun refresh(force: Boolean = false) {
        if (!force && (_status.value == GemmaModelStatus.QUEUED ||
                _status.value is GemmaModelStatus.DOWNLOADING)
        ) return
        _status.value = GemmaModelFileVerifier.resolveStatus(
            supported = supported,
            modelFile = modelFile,
            expectedSha256 = BuildConfig.GEMMA_MODEL_SHA256
        )
    }

    /** Flips the status to ERROR after a runtime/native inference failure. */
    fun markError() {
        _status.value = GemmaModelStatus.ERROR
    }

    /** Enqueues the ~530 MB model download after an explicit user request. */
    fun downloadModel() {
        if (!supported) return
        if (!GemmaDownloadConfig.isValid(
                url = BuildConfig.GEMMA_MODEL_URL,
                sha256 = BuildConfig.GEMMA_MODEL_SHA256,
                sizeBytes = BuildConfig.GEMMA_MODEL_SIZE_BYTES
            )
        ) {
            _status.value = GemmaModelStatus.ERROR
            return
        }
        // A runtime activation error should not force another ~530 MB transfer. Re-verify the
        // existing file off the main thread and emit READY again so application activation retries.
        if (modelFile.exists()) {
            _status.value = GemmaModelStatus.QUEUED
            scope.launch {
                val diskStatus = GemmaModelFileVerifier.resolveStatus(
                    supported = supported,
                    modelFile = modelFile,
                    expectedSha256 = BuildConfig.GEMMA_MODEL_SHA256
                )
                if (diskStatus == GemmaModelStatus.READY) {
                    _status.value = GemmaModelStatus.READY
                } else {
                    modelFile.delete()
                    enqueueDownloadRequest()
                }
            }
            return
        }
        enqueueDownloadRequest()
    }

    private fun enqueueDownloadRequest() {
        val request = OneTimeWorkRequestBuilder<GemmaModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        _status.value = GemmaModelStatus.QUEUED
        WorkManager.getInstance(context).enqueueUniqueWork(
            GemmaModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Removes the model and any partial download, freeing ~530 MB of user storage. */
    fun deleteModel() {
        WorkManager.getInstance(context).cancelUniqueWork(GemmaModelDownloadWorker.WORK_NAME)
        partialFile.delete()
        modelFile.delete()
        refresh(force = true)
    }

    private fun observeDownloadWork() {
        scope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(GemmaModelDownloadWorker.WORK_NAME)
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> _status.value = GemmaModelStatus.QUEUED
                        WorkInfo.State.RUNNING -> _status.value = GemmaModelStatus.DOWNLOADING(
                            info.progress.getInt(GemmaModelDownloadWorker.KEY_PROGRESS, 0)
                                .coerceIn(0, 100)
                        )
                        WorkInfo.State.SUCCEEDED,
                        WorkInfo.State.CANCELLED -> refresh(force = true)
                        WorkInfo.State.FAILED -> _status.value = GemmaModelStatus.ERROR
                    }
                }
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma3-1b-it-int4.task"

        /** Gemma 3 1B int4 needs ~2 GB free at inference; below 4 GB total we stay heuristic-only. */
        private const val MIN_TOTAL_RAM_BYTES = 4L * 1024 * 1024 * 1024
    }
}
