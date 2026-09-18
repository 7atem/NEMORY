package com.vaultbrain.core.billing.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for billing bindings.
 *
 * [BillingManager] and [FeatureGate] are constructor-injected singletons and do
 * not require explicit provider methods.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule
