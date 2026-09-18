package com.vaultbrain.core.ai.embeddings

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * True on-device implementation of [TextEmbeddingModel] using MediaPipe Universal Sentence Encoder.
 */
@Singleton
class AllMiniLmEmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context
) : TextEmbeddingModel {

    private val embedder: TextEmbedder by lazy {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("universal_sentence_encoder.tflite")
            .build()
        val options = TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .setL2Normalize(true)
            .build()
        TextEmbedder.createFromOptions(context, options)
    }

    override fun isAvailable(): Boolean = true

    override suspend fun encode(text: String): FloatArray = withContext(Dispatchers.IO) {
        val result = embedder.embed(text)
        result.embeddingResult().embeddings().first().floatEmbedding()
    }

    companion object {
        const val DIMENSION = 100 // USE is 100-dim
    }
}
