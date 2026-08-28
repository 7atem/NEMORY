package com.vaultbrain.core.integrations.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the integrations layer.
 *
 * Most integrations dependencies have @Inject constructors; this module is the
 * extension point for bindings that require configuration (e.g. registering
 * concrete connectors at build time).
 */
@Module
@InstallIn(SingletonComponent::class)
object IntegrationsModule
