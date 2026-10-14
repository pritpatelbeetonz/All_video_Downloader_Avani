package app.messages.data.telephony

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import app.messages.data.contacts.ContactsResolver
import app.messages.data.db.ThreadFlagsDao
import app.messages.data.role.RoleStateRepository
import app.messages.data.telephony.model.Conversation
import app.messages.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real [ConversationRepository] backed by the Telephony content provider, with
 * app-only flags (pin / archive / mute) merged in from Room (§7.3).
 *
 * Activation: the repo is a singleton and always reads from the provider.
 * If the app is not the default SMS handler, the `READ_SMS` permission isn't
 * granted, queries throw `SecurityException`, and we return an empty list —
 * the UI shows the "Set as default to send messages" banner.
 *
 * Refresh sources:
 *  - [ContentObserver] on SMS + MMS URIs, debounced 120ms.
 *  - [TelephonyEventBus] events from receivers (incoming SMS, role flips).
 *  - [ThreadFlagsDao] flow — a pin/archive change re-emits the merged list.
 */
@Singleton
class TelephonyConversationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val query: ConversationQuery,
    private val contacts: ContactsResolver,
    private val eventBus: TelephonyEventBus,
    private val roleStateRepository: RoleStateRepository,
    private val flagsDao: ThreadFlagsDao,
    @ApplicationScope private val appScope: CoroutineScope,
) : ConversationRepository {

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    private val observer: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        private var pending: Job? = null
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            pending?.cancel()
            pending = appScope.launch {
                delay(120)
                changes.tryEmit(Unit)
            }
        }
    }

    init {
        runCatching {
            context.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI, true, observer,
            )
            context.contentResolver.registerContentObserver(
                Telephony.Mms.CONTENT_URI, true, observer,
            )
        }
    }

    override fun watchConversations(): Flow<List<Conversation>> {
        val base: Flow<List<Conversation>> = flow {
            // Warm the contact index before the first query so names land on the
            // first emission rather than triggering a second pass.
            contacts.ensureDirectoryLoaded()
            emit(safeQuery())
            merge(
                changes,
                contacts.updates,
                eventBus.events.let { upstream ->
                    flow {
                        upstream.collect { event ->
                            if (event is TelephonyEvent.InboxChanged ||
                                event is TelephonyEvent.MessageReceived
                            ) emit(Unit)
                        }
                    }
                },
                roleStateRepository.state.map { },
            )
                // A burst of provider notifications (an incoming batch, a contacts
                // refresh) must not turn into one full re-query each.
                .debounce(REQUERY_DEBOUNCE_MS)
                .collect { emit(safeQuery()) }
        }
        // Merge the provider-backed list with app-only flags. A pin or archive
        // toggle changes the Room flow, which re-emits the combined list.
        return combine(
            base,
            flagsDao.observeAll()
        ) { convos, flags ->
            val flagMap = flags.associateBy { it.threadId }
            convos.map { c ->
                val f = flagMap[c.threadId]
                c.copy(
                    isPinned = f?.pinned ?: false,
                    isArchived = f?.archived ?: false,
                    isMuted = f?.muted ?: false,
                    isStarred = f?.starred ?: false,
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun safeQuery(): List<Conversation> = withContext(Dispatchers.IO) {
        runCatching { query.listConversations().map(::toConversation) }
            .onFailure { }
            .getOrDefault(emptyList())
    }

    private fun toConversation(row: ConversationRow): Conversation {
        // Resolve the primary address to a contact name + photo. ContactsResolver
        // caches, so repeated list refreshes don't re-hit the provider. Flags
        // (pin/archive/mute) are applied later from Room, not here.
        val primary = row.addresses.firstOrNull()
        val contact = primary?.let { contacts.resolve(it) }
        return Conversation(
            threadId = row.threadId,
            addresses = row.addresses,
            snippet = row.snippet,
            lastActivity = Date(row.lastActivityEpochMs),
            unreadCount = row.unreadCount,
            contactName = if (row.addresses.size <= 1) contact?.displayName else null,
            contactPhotoUri = if (row.addresses.size <= 1) contact?.photoUri else null,
        )
    }

    override suspend fun markRead(threadId: String) {
        withContext(Dispatchers.IO) { query.markThreadRead(threadId) }
        changes.tryEmit(Unit)
    }

    override suspend fun setPinned(threadId: String, pinned: Boolean) {
        withContext(Dispatchers.IO) {
            flagsDao.ensure(threadId)
            flagsDao.updatePinned(threadId, pinned)
        }
    }

    override suspend fun setArchived(threadId: String, archived: Boolean) {
        withContext(Dispatchers.IO) {
            flagsDao.ensure(threadId)
            flagsDao.updateArchived(threadId, archived)
        }
    }

    override suspend fun setMuted(threadId: String, muted: Boolean) {
        withContext(Dispatchers.IO) {
            flagsDao.ensure(threadId)
            flagsDao.updateMuted(threadId, muted)
        }
    }

    override suspend fun setStarred(threadId: String, starred: Boolean) {
        withContext(Dispatchers.IO) {
            flagsDao.ensure(threadId)
            flagsDao.updateStarred(threadId, starred)
        }
    }

    override suspend fun archive(threadId: String) = setArchived(threadId, true)

    override suspend fun delete(threadId: String) {
        withContext(Dispatchers.IO) {
            query.deleteThread(threadId)
            flagsDao.clear(threadId)
        }
        changes.tryEmit(Unit)
    }

    private companion object {
        const val TAG = "TelephonyConvRepo"

        /** Collapses refresh bursts into a single provider re-query. */
        const val REQUERY_DEBOUNCE_MS = 250L
    }
}
