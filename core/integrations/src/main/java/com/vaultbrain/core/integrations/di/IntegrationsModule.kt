package com.vaultbrain.core.integrations.di

import com.vaultbrain.core.integrations.calendar.AndroidCalendarDataSource
import com.vaultbrain.core.integrations.calendar.CalendarDataSource
import dagger.Binds
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
abstract class IntegrationsModule {
    @Binds
    abstract fun bindCalendarDataSource(implementation: AndroidCalendarDataSource): CalendarDataSource
}
