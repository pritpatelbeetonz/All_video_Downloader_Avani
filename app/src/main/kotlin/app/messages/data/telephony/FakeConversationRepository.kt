package app.messages.data.telephony

import app.messages.data.telephony.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake — used by `@Preview` composables. Not Hilt-bound; tests
 * inject it directly through the [ConversationRepository] interface.
 */
class FakeConversationRepository : ConversationRepository {

    private val state = MutableStateFlow(FakeConversations.list())

    override fun watchConversations(): Flow<List<Conversation>> = state.asStateFlow()

    override suspend fun markRead(threadId: String) {
        state.value = state.value.map { c ->
            if (c.threadId == threadId) c.copy(unreadCount = 0) else c
        }
    }

    override suspend fun setPinned(threadId: String, pinned: Boolean) {
        state.value = state.value.map { c ->
            if (c.threadId == threadId) c.copy(isPinned = pinned) else c
        }
    }

    override suspend fun setArchived(threadId: String, archived: Boolean) {
        state.value = state.value.map { c ->
            if (c.threadId == threadId) c.copy(isArchived = archived) else c
        }
    }

    override suspend fun setMuted(threadId: String, muted: Boolean) {
        state.value = state.value.map { c ->
            if (c.threadId == threadId) c.copy(isMuted = muted) else c
        }
    }

    override suspend fun setStarred(threadId: String, starred: Boolean) {
        state.value = state.value.map { c ->
            if (c.threadId == threadId) c.copy(isStarred = starred) else c
        }
    }

    override suspend fun archive(threadId: String) = setArchived(threadId, true)

    override suspend fun delete(threadId: String) {
        state.value = state.value.filterNot { it.threadId == threadId }
    }
}
