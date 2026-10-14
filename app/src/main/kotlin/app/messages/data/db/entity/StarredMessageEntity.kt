package app.messages.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A starred message (§F20). We snapshot enough of the message to render the
 * Starred screen without re-querying the provider per row; the provider stays
 * the source of truth for the message itself.
 */
@Entity(tableName = "starred_messages")
data class StarredMessageEntity(
    @PrimaryKey val messageId: String,
    val threadId: String,
    val address: String,
    val body: String,
    val dateEpochMs: Long,
)
