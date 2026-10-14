package app.messages.data.telephony

import app.messages.data.telephony.model.SmsMessage
import kotlinx.coroutines.flow.Flow

/** Lightweight header for the thread app bar + reply target. */
data class ThreadHeader(
    val threadId: String,
    val addresses: List<String>,
    val displayName: String,
    val photoUri: String?,
) {
    val isGroup: Boolean get() = addresses.size > 1
    val primaryAddress: String get() = addresses.firstOrNull().orEmpty()
}

/**
 * Read + write access to the messages within a single thread. Separate from
 * [ConversationRepository] (which is the thread-list view) so the thread
 * screen can paginate and refresh independently.
 */
interface MessageRepository {

    /** Hot stream of the newest [limit] messages, refreshing on provider change. */
    fun watchMessages(threadId: String, limit: Int): Flow<List<SmsMessage>>

    suspend fun getHeader(threadId: String): ThreadHeader

    suspend fun send(address: String, body: String, subscriptionId: Int?): String

    /** Sends a media MMS (image/gif/audio, with optional caption) to [address]. */
    suspend fun sendMms(address: String, caption: String, mediaUri: String, mimeType: String)

    /** Thread id for an address, created if needed — for compose navigation. */
    suspend fun resolveThreadId(address: String): String

    suspend fun markThreadRead(threadId: String)

    suspend fun deleteMessage(messageId: String)
}
