package app.messages.di

import app.messages.data.telephony.ConversationRepository
import app.messages.data.telephony.MessageRepository
import app.messages.data.telephony.SearchRepository
import app.messages.data.telephony.TelephonyConversationRepository
import app.messages.data.telephony.TelephonyMessageRepository
import app.messages.data.telephony.TelephonySearchRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Marks a [CoroutineScope] that lives for the whole application process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Binds the real telephony repository at runtime. The [FakeConversationRepository]
 * is intentionally NOT registered with Hilt — previews and tests construct it
 * directly so production builds can never accidentally surface fake data.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: TelephonyConversationRepository,
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: TelephonyMessageRepository,
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: TelephonySearchRepository,
    ): SearchRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
