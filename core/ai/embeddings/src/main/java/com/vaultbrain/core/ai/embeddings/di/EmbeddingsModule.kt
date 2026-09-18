package com.vaultbrain.core.ai.embeddings.di

import com.vaultbrain.core.ai.embeddings.AllMiniLmEmbeddingModel
import com.vaultbrain.core.ai.embeddings.MobileClipEmbeddingModel
import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.ai.embeddings.VisionEmbeddingModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbeddingsModule {

    @Binds
    @Singleton
    abstract fun bindTextEmbeddingModel(
        impl: AllMiniLmEmbeddingModel
    ): TextEmbeddingModel

    @Binds
    @Singleton
    abstract fun bindVisionEmbeddingModel(
        impl: MobileClipEmbeddingModel
    ): VisionEmbeddingModel
}
