package app.messages.data.star

import app.messages.data.db.StarredMessageDao
import app.messages.data.db.entity.StarredMessageEntity
import app.messages.data.telephony.model.SmsMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Star / unstar messages and observe the starred set (§F20). */
@Singleton
class StarRepository @Inject constructor(
    private val dao: StarredMessageDao,
) {
    fun observeAll(): Flow<List<StarredMessageEntity>> = dao.observeAll()

    fun observeStarredIdsForThread(threadId: String): Flow<List<String>> =
        dao.observeStarredIdsForThread(threadId)

    suspend fun isStarred(messageId: String): Boolean = dao.isStarred(messageId)

    suspend fun star(message: SmsMessage) {
        dao.star(
            StarredMessageEntity(
                messageId = message.id,
                threadId = message.threadId,
                address = message.address,
                body = message.body,
                dateEpochMs = message.date.time,
            ),
        )
    }

    suspend fun setStarred(message: SmsMessage, starred: Boolean) {
        if (starred) star(message) else dao.unstar(message.id)
    }

    suspend fun unstar(messageId: String) = dao.unstar(messageId)
}
