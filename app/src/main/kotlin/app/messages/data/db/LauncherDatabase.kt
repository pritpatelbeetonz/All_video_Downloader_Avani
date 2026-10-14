package app.messages.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConversationEntity::class, MessageEntity::class, ArchivedThreadEntity::class, BlockedContactEntity::class, LauncherScheduledMessageEntity::class, BlockedMessageEntity::class, DeletedConversationEntity::class, DeletedMessageEntity::class], version = 22, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun archivedThreadDao(): ArchivedThreadDao
    abstract fun blockedContactDao(): BlockedContactDao
    abstract fun launcherScheduledMessageDao(): LauncherScheduledMessageDao
    fun scheduledMessageDao(): LauncherScheduledMessageDao = launcherScheduledMessageDao()
    abstract fun blockedMessageDao(): BlockedMessageDao
    abstract fun deletedConversationDao(): DeletedConversationDao
    abstract fun deletedMessageDao(): DeletedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: LauncherDatabase? = null

        fun getLauncherDatabase(context: Context): LauncherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "sms_app_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
