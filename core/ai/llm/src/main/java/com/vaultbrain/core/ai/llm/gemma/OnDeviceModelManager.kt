package com.vaultbrain.core.ai.llm.gemma

import android.app.ActivityManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Lifecycle of the optional on-device Tier 2 model package. */
sealed interface OnDeviceModelStatus {
    data object NOT_SUPPORTED : OnDeviceModelStatus
    data object NOT_DOWNLOADED : OnDeviceModelStatus
    data object QUEUED : OnDeviceModelStatus
    data class DOWNLOADING(val progress: Int) : OnDeviceModelStatus
    data object READY : OnDeviceModelStatus
    data object ERROR : OnDeviceModelStatus
}

typealias GemmaModelStatus = OnDeviceModelStatus

/**
 * Owns the Qwen3-VL-2B model package (language model + vision projector) in `filesDir/models/`.
 */
@Singleton
class OnDeviceModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val modelFile = File(context.filesDir, "models/$MODEL_FILE_NAME")
    private val mmprojFile = File(context.filesDir, "models/$MMPROJ_FILE_NAME")

    private val legacyGemmaFile = File(context.filesDir, "models/gemma3-1b-it-int4.task")
    private val legacyGemmaPartial = File(context.filesDir, "models/gemma3-1b-it-int4.task.partial")

    private val supported: Boolean by lazy {
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return@lazy false
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        info.totalMem >= MIN_TOTAL_RAM_BYTES
    }

    private val _status = MutableStateFlow<OnDeviceModelStatus>(
        if (supported) OnDeviceModelStatus.NOT_DOWNLOADED else OnDeviceModelStatus.NOT_SUPPORTED
    )
    val status: StateFlow<OnDeviceModelStatus> = _status.asStateFlow()

    init {
        if (supported) {
            scope.launch { refresh() }
            observeDownloadWork()
        }
    }

    fun isSupported(): Boolean = supported

    /** Absolute path of the verified language model file. */
    val modelPath: String
        get() = modelFile.absolutePath

    /** Absolute path of the verified vision projector file. */
    val mmprojPath: String
        get() = mmprojFile.absolutePath

    /** Re-derives the status from on-disk files. Also cleans up obsolete legacy Gemma files. */
    fun refresh(force: Boolean = false) {
        if (!force && (_status.value == OnDeviceModelStatus.QUEUED ||
                _status.value is OnDeviceModelStatus.DOWNLOADING)
        ) return

        // Cleanup obsolete legacy Gemma downloads
        if (legacyGemmaFile.exists()) legacyGemmaFile.delete()
        if (legacyGemmaPartial.exists()) legacyGemmaPartial.delete()

        _status.value = GemmaModelFileVerifier.resolveStatus(
            supported = supported,
            modelFile = modelFile,
            expectedModelSha256 = BuildConfig.QWEN_MODEL_SHA256,
            mmprojFile = mmprojFile,
            expectedMmprojSha256 = BuildConfig.QWEN_MMPROJ_SHA256
        )
    }

    fun markError() {
        _status.value = OnDeviceModelStatus.ERROR
    }

    /** Enqueues the model download after an explicit user request. */
    fun downloadModel() {
        if (!supported) return
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
            _status.value = OnDeviceModelStatus.ERROR
            return
        }

        if (modelFile.exists() && mmprojFile.exists()) {
            _status.value = OnDeviceModelStatus.QUEUED
            scope.launch {
                val diskStatus = GemmaModelFileVerifier.resolveStatus(
                    supported = supported,
                    modelFile = modelFile,
                    expectedModelSha256 = BuildConfig.QWEN_MODEL_SHA256,
                    mmprojFile = mmprojFile,
                    expectedMmprojSha256 = BuildConfig.QWEN_MMPROJ_SHA256
                )
                if (diskStatus == OnDeviceModelStatus.READY) {
                    _status.value = OnDeviceModelStatus.READY
                } else {
                    modelFile.delete()
                    mmprojFile.delete()
                    enqueueDownloadRequest()
                }
            }
            return
        }
        enqueueDownloadRequest()
    }

    private fun enqueueDownloadRequest() {
        val request = OneTimeWorkRequestBuilder<OnDeviceModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        _status.value = OnDeviceModelStatus.QUEUED
        WorkManager.getInstance(context).enqueueUniqueWork(
            OnDeviceModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Removes the model files and frees ~1.6 GB of user storage. */
    fun deleteModel() {
        WorkManager.getInstance(context).cancelUniqueWork(OnDeviceModelDownloadWorker.WORK_NAME)
        File(context.filesDir, "models/$MODEL_FILE_NAME.partial").delete()
        File(context.filesDir, "models/$MMPROJ_FILE_NAME.partial").delete()
        modelFile.delete()
        mmprojFile.delete()
        refresh(force = true)
    }

    private fun observeDownloadWork() {
        scope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(OnDeviceModelDownloadWorker.WORK_NAME)
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> _status.value = OnDeviceModelStatus.QUEUED
                        WorkInfo.State.RUNNING -> _status.value = OnDeviceModelStatus.DOWNLOADING(
                            info.progress.getInt(OnDeviceModelDownloadWorker.KEY_PROGRESS, 0)
                                .coerceIn(0, 100)
                        )
                        WorkInfo.State.SUCCEEDED,
                        WorkInfo.State.CANCELLED -> refresh(force = true)
                        WorkInfo.State.FAILED -> _status.value = OnDeviceModelStatus.ERROR
                    }
                }
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "Qwen3VL-2B-Instruct-Q4_K_M.gguf"
        const val MMPROJ_FILE_NAME = "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf"

        private const val MIN_TOTAL_RAM_BYTES = 4L * 1024 * 1024 * 1024
    }
}

typealias GemmaModelManager = OnDeviceModelManager
