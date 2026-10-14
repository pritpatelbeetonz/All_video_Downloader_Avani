package app.messages.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.messages.data.db.entity.StarredMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StarredMessageDao {

    @Query("SELECT * FROM starred_messages ORDER BY dateEpochMs DESC")
    fun observeAll(): Flow<List<StarredMessageEntity>>

    @Query("SELECT messageId FROM starred_messages WHERE threadId = :threadId")
    fun observeStarredIdsForThread(threadId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM starred_messages WHERE messageId = :messageId)")
    suspend fun isStarred(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun star(entity: StarredMessageEntity)

    @Query("DELETE FROM starred_messages WHERE messageId = :messageId")
    suspend fun unstar(messageId: String)
}
