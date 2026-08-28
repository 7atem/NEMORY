package com.vaultbrain.core.ai.heuristics.di

import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HeuristicsModule {

    @Provides
    @Singleton
    fun provideHeuristicExtractor(): HeuristicExtractor = HeuristicExtractor()
}
