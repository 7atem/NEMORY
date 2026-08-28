package com.vaultbrain.core.ai.llm

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitNanoRuntime @Inject constructor() : NanoRuntime {
    private val model by lazy { Generation.getClient() }

    override suspend fun status(): OnDeviceModelStatus = when (model.checkStatus()) {
        FeatureStatus.UNAVAILABLE -> OnDeviceModelStatus.UNAVAILABLE
        FeatureStatus.DOWNLOADABLE -> OnDeviceModelStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> OnDeviceModelStatus.DOWNLOADING
        FeatureStatus.AVAILABLE -> OnDeviceModelStatus.READY
        else -> OnDeviceModelStatus.ERROR
    }

    override suspend fun baseModelName(): String? =
        runCatching { model.getBaseModelName() }.getOrNull()?.takeIf(String::isNotBlank)

    override suspend fun tokenLimit(): Int? =
        runCatching { model.getTokenLimit() }.getOrNull()?.takeIf { it > 0 }

    override suspend fun warmup() {
        model.warmup()
    }

    override suspend fun generate(prompt: String): String? = model
        .generateContent(prompt)
        .candidates
        .firstOrNull()
        ?.text
        ?.takeIf(String::isNotBlank)

    override suspend fun generate(prompt: String, image: LlmImageInput): String? {
        val request = GenerateContentRequest.Builder(
            ImagePart(image.encodedBytes),
            TextPart(prompt)
        ).build()
        return model.generateContent(request)
            .candidates
            .firstOrNull()
            ?.text
            ?.takeIf(String::isNotBlank)
    }

    override fun supportsImageInput(): Boolean = true

    override fun generateStream(prompt: String): Flow<String> = model
        .generateContentStream(prompt)
        .mapNotNull { response ->
            response.candidates.firstOrNull()?.text?.takeIf(String::isNotBlank)
        }

    override fun download(): Flow<OnDeviceModelStatus> = model.download().map { status ->
        when (status) {
            is DownloadStatus.DownloadStarted -> OnDeviceModelStatus.DOWNLOADING
            is DownloadStatus.DownloadProgress -> OnDeviceModelStatus.DOWNLOADING
            is DownloadStatus.DownloadCompleted -> OnDeviceModelStatus.READY
            is DownloadStatus.DownloadFailed -> OnDeviceModelStatus.ERROR
        }
    }
}
