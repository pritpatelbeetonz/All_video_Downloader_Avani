package app.messages.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written Room migrations. There must be one for EVERY shipped schema bump —
 * the [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration] in
 * `DatabaseModule` is a last-resort safety net, NOT a substitute. A known upgrade
 * that falls through to it silently wipes the user's app data: pins/archives/
 * mutes, starred messages, and (worst) queued scheduled sends that then never fire.
 */

/**
 * v2 → v3: adds the per-thread `starred` flag for conversation starring.
 *
 * `ADD COLUMN ... NOT NULL` requires a default in SQLite, so we supply `DEFAULT 0`;
 * existing rows become `starred = 0`. The matching `@ColumnInfo(defaultValue = "0")`
 * on `ThreadFlagsEntity.starred` keeps the migrated and fresh-install schemas
 * identical so Room's post-migration validation passes.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE thread_flags ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
    }
}
