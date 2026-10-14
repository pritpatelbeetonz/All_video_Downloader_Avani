package app.messages.data.telephony

import app.messages.data.telephony.model.Conversation
import kotlinx.coroutines.flow.Flow

/**
 * Repository facade over the Android Telephony provider.
 *
 * UI code always sees this interface; concrete implementations are
 * [TelephonyConversationRepository] (real, Hilt-bound singleton) and
 * [FakeConversationRepository] (previews + unit tests).
 */
interface ConversationRepository {

    /** Hot stream — emits the current list on subscribe and every change. */
    fun watchConversations(): Flow<List<Conversation>>

    suspend fun markRead(threadId: String)

    suspend fun setPinned(threadId: String, pinned: Boolean)

    suspend fun setArchived(threadId: String, archived: Boolean)

    suspend fun setMuted(threadId: String, muted: Boolean)

    suspend fun setStarred(threadId: String, starred: Boolean)

    /** Convenience for the common one-way archive action. */
    suspend fun archive(threadId: String)

    suspend fun delete(threadId: String)
}
