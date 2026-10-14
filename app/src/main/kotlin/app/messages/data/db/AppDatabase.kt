package app.messages.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.messages.data.db.entity.ScheduledMessageEntity
import app.messages.data.db.entity.StarredMessageEntity
import app.messages.data.db.entity.ThreadFlagsEntity

/**
 * App-only state store (§7.3). The Telephony provider remains the source of
 * truth for messages; this DB holds pins/archive/mute and starred snapshots,
 * plus the M5 scheduled-send queue.
 *
 * v2 (M5): adds [ScheduledMessageEntity]. v3: adds the per-thread `starred`
 * flag. Pre-1.0 we destructively migrate (see DatabaseModule), no Migration yet.
 */
@Database(
    entities = [
        ThreadFlagsEntity::class,
        StarredMessageEntity::class,
        ScheduledMessageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadFlagsDao(): ThreadFlagsDao
    abstract fun starredMessageDao(): StarredMessageDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao

    companion object {
        fun getDatabase(context: android.content.Context): LauncherDatabase {
            return LauncherDatabase.getLauncherDatabase(context)
        }
    }
}
