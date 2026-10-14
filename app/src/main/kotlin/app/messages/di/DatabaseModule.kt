package app.messages.di

import android.content.Context
import androidx.room.Room
import app.messages.data.db.AppDatabase
import app.messages.data.db.MIGRATION_2_3
import app.messages.data.db.ScheduledMessageDao
import app.messages.data.db.StarredMessageDao
import app.messages.data.db.ThreadFlagsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "messages.db")
            // Real migrations preserve user data (pins/stars/queued scheduled
            // sends) across schema bumps — see AppMigrations. The destructive
            // fallback below is a LAST RESORT for unknown older versions /
            // downgrades only, never the path for a known upgrade.
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideThreadFlagsDao(db: AppDatabase): ThreadFlagsDao = db.threadFlagsDao()

    @Provides
    fun provideStarredMessageDao(db: AppDatabase): StarredMessageDao = db.starredMessageDao()

    @Provides
    fun provideScheduledMessageDao(db: AppDatabase): ScheduledMessageDao = db.scheduledMessageDao()
}
