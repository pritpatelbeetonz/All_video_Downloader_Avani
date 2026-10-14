package app.messages.di

import app.messages.monitoring.CrashReporter
import app.messages.monitoring.LocalCrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the crash-reporting seam. Swap [LocalCrashReporter] for a
 * `FirebaseCrashlyticsReporter` here once Firebase is configured.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringModule {

    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: LocalCrashReporter): CrashReporter
}
