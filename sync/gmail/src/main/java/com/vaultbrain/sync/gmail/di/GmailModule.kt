package com.vaultbrain.sync.gmail.di

import com.vaultbrain.sync.gmail.GmailParser
import com.vaultbrain.sync.gmail.GmailDataSource
import com.vaultbrain.sync.gmail.UnconfiguredGmailDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GmailModule {

    @Provides
    @Singleton
    fun provideGmailDataSource(): GmailDataSource = UnconfiguredGmailDataSource()

    @Provides
    @Singleton
    fun provideGmailParser(): GmailParser = GmailParser()
}
