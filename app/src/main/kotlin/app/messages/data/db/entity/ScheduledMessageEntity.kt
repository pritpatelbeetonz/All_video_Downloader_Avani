package app.messages.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A queued scheduled SMS (§F13). This row is the source of truth for the send:
 * an exact [android.app.AlarmManager] alarm keyed on [id] fires even if the app
 * process is dead, and the receiver reads this row to perform the actual send.
 *
 * [status] holds a [app.messages.data.scheduling.ScheduledStatus] name. We keep
 * SENT/FAILED rows (rather than deleting on fire) so the queue can show a result
 * and a failed send can be retried.
 */
@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: String,
    val address: String,
    val body: String,
    val simSlot: Int?,
    val scheduledAtMs: Long,
    val status: String,
    val createdAtMs: Long,
)
