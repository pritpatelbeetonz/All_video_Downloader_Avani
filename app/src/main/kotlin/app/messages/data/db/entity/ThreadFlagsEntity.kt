package app.messages.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-thread app-only flags. The Telephony provider has no columns for pin /
 * mute, and its `archived` column is unreliable across OEMs — so all three
 * live here and are merged into the conversation list at read time (§7.3).
 */
@Entity(tableName = "thread_flags")
data class ThreadFlagsEntity(
    @PrimaryKey val threadId: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    // Default MUST match the migration's `DEFAULT 0`, or Room's schema validation
    // fails when comparing a migrated DB against a freshly-created one.
    @ColumnInfo(defaultValue = "0") val starred: Boolean = false,
)
