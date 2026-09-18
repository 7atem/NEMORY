package com.vaultbrain.core.vectorstore.di

import android.content.Context
import com.vaultbrain.core.vectorstore.CollectionVectorStore
import com.vaultbrain.core.vectorstore.VectorStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VectorStoreModule {

    @Provides
    @Singleton
    fun provideVectorStore(@ApplicationContext context: Context): VectorStore {
        return VectorStore(context)
    }

    @Provides
    @Singleton
    fun provideCollectionVectorStore(vectorStore: VectorStore): CollectionVectorStore = vectorStore
}
