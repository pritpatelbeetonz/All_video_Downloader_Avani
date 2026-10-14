package app.messages.data.db

import androidx.room.Dao
import androidx.room.Query
import app.messages.data.db.entity.ThreadFlagsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadFlagsDao {

    @Query("SELECT * FROM thread_flags")
    fun observeAll(): Flow<List<ThreadFlagsEntity>>

    @Query("SELECT * FROM thread_flags WHERE threadId = :threadId")
    suspend fun get(threadId: String): ThreadFlagsEntity?

    /** Ensure a row exists before a targeted UPDATE so we never lose sibling flags. */
    @Query(
        "INSERT OR IGNORE INTO thread_flags(threadId, pinned, archived, muted, starred) " +
            "VALUES(:threadId, 0, 0, 0, 0)",
    )
    suspend fun ensure(threadId: String)

    @Query("UPDATE thread_flags SET pinned = :value WHERE threadId = :threadId")
    suspend fun updatePinned(threadId: String, value: Boolean)

    @Query("UPDATE thread_flags SET archived = :value WHERE threadId = :threadId")
    suspend fun updateArchived(threadId: String, value: Boolean)

    @Query("UPDATE thread_flags SET muted = :value WHERE threadId = :threadId")
    suspend fun updateMuted(threadId: String, value: Boolean)

    @Query("UPDATE thread_flags SET starred = :value WHERE threadId = :threadId")
    suspend fun updateStarred(threadId: String, value: Boolean)

    @Query("DELETE FROM thread_flags WHERE threadId = :threadId")
    suspend fun clear(threadId: String)
}
