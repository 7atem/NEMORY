package com.vaultbrain.core.ai.llm.di

import android.content.Context
import com.vaultbrain.core.ai.llm.AppForegroundChecker
import com.vaultbrain.core.ai.llm.CloudAiRuntime
import com.vaultbrain.core.ai.llm.CloudConsentStore
import com.vaultbrain.core.ai.llm.DisabledCloudAiRuntime
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.MlKitNanoRuntime
import com.vaultbrain.core.ai.llm.NanoPromptClient
import com.vaultbrain.core.ai.llm.NanoRuntime
import com.vaultbrain.core.ai.llm.QnnLlmClient
import com.vaultbrain.core.ai.llm.QwenLlmClient
import com.vaultbrain.core.ai.llm.SharedPreferencesCloudConsentStore
import com.vaultbrain.core.ai.llm.TieredLlmClientSelector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Nano

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Qnn

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Onnx

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Gemma

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnDevice

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Cloud

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    @Nano
    abstract fun bindNanoClient(client: NanoPromptClient): LlmClient

    @Binds
    @Singleton
    abstract fun bindNanoRuntime(runtime: MlKitNanoRuntime): NanoRuntime

    @Binds
    @Singleton
    @Qnn
    abstract fun bindQnnClient(client: QnnLlmClient): LlmClient

    @Binds
    @Singleton
    @Onnx
    abstract fun bindOnnxClient(client: com.vaultbrain.core.ai.llm.OnnxLlmClient): LlmClient

    @Binds
    @Singleton
    @Gemma
    abstract fun bindGemmaClient(client: QwenLlmClient): LlmClient

    @Binds
    @Singleton
    @Cloud
    abstract fun bindCloudRuntime(runtime: DisabledCloudAiRuntime): CloudAiRuntime

    @Binds
    @Singleton
    abstract fun bindCloudConsentStore(
        store: SharedPreferencesCloudConsentStore
    ): CloudConsentStore

    companion object {
        @Provides
        @Singleton
        fun provideDefaultLlmClient(
            selector: TieredLlmClientSelector
        ): LlmClient = selector

        @Provides
        @Singleton
        fun provideAppForegroundChecker(
            @ApplicationContext context: Context
        ): AppForegroundChecker = AppForegroundChecker {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
    }
}
