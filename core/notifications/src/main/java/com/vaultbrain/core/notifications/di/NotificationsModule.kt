package com.vaultbrain.core.notifications.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing WorkManager for the notifications module.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return runCatching { WorkManager.getInstance(context) }.getOrElse {
            runCatching {
                androidx.work.WorkManager.initialize(
                    context,
                    androidx.work.Configuration.Builder().build()
                )
            }
            WorkManager.getInstance(context)
        }
    }
}
