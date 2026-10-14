package app.messages.data.telephony

import app.messages.core.prefs.AppPrefs
import app.messages.data.contacts.ContactsResolver
import app.messages.data.telephony.model.SmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge

/**
 * Real [MessageRepository] backed by the Telephony provider.
 *
 * Like [TelephonyConversationRepository], it merges a ContentObserver-ish
 * trigger (here, the shared [TelephonyEventBus]) with the initial query so the
 * thread updates the instant a message arrives or a send status changes.
 */
@Singleton
class TelephonyMessageRepository @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
    private val query: ConversationQuery,
    private val mmsQuery: MmsQuery,
    private val sender: SmsSender,
    private val mmsSender: MmsSender,
    private val contacts: ContactsResolver,
    private val eventBus: TelephonyEventBus,
    private val prefs: AppPrefs,
) : MessageRepository {

    override fun watchMessages(threadId: String, limit: Int): Flow<List<SmsMessage>> = flow {
        emit(load(threadId, limit))
        merge(
            eventBus.events,
        ).collect { event ->
            val affectsThread = when (event) {
                is TelephonyEvent.MessageReceived -> event.threadId == threadId
                is TelephonyEvent.SendStatus -> true
                TelephonyEvent.InboxChanged -> true
            }
            if (affectsThread) emit(load(threadId, limit))
        }
    }.flowOn(Dispatchers.IO)

    private fun load(threadId: String, limit: Int): List<SmsMessage> =
        runCatching {
            val now = System.currentTimeMillis()
            val sms = query.listMessages(threadId, limit = limit, offset = 0)
                .map { it.toSmsMessage(now) }
            // MMS lives in a separate provider table; merge both into one
            // date-sorted stream so a thread with photos reads naturally.
            val mms = runCatching { mmsQuery.listMmsForThread(threadId, limit) }
                .getOrDefault(emptyList())
                .map { it.toSmsMessage() }
            (sms + mms)
                .sortedByDescending { it.date.time }
                .take(limit)
        }.getOrDefault(emptyList())

    override suspend fun getHeader(threadId: String): ThreadHeader {
        val addresses = runCatching { query.getThreadAddresses(threadId) }
            .getOrDefault(emptyList())
        val displayName = when {
            addresses.isEmpty() -> "(unknown)"
            addresses.size == 1 -> {
                val c = contacts.resolve(addresses.first())
                c.displayName ?: addresses.first()
            }
            else -> addresses.joinToString(", ") { addr ->
                contacts.resolve(addr).displayName ?: addr
            }
        }
        val photo = addresses.firstOrNull()?.let { contacts.resolve(it).photoUri }
        return ThreadHeader(
            threadId = threadId,
            addresses = addresses,
            displayName = displayName,
            photoUri = if (addresses.size == 1) photo else null,
        )
    }

    override suspend fun send(address: String, body: String, subscriptionId: Int?): String {
        val id = sender.send(address, body, subscriptionId, prefs.deliveryReports.first())
        // The provider insert won't fire our event bus on its own; nudge the
        // thread to re-query so the outgoing bubble appears immediately rather
        // than waiting for the first SENT delivery callback.
        eventBus.emit(TelephonyEvent.InboxChanged)
        return id
    }

    override suspend fun sendMms(address: String, caption: String, mediaUri: String, mimeType: String) {
        runCatching {
            mmsSender.send(address, caption, android.net.Uri.parse(mediaUri), mimeType)
        }
        // The library writes the outgoing MMS to the provider asynchronously;
        // nudge the thread so the sent bubble appears once it lands.
        eventBus.emit(TelephonyEvent.InboxChanged)
    }

    override suspend fun resolveThreadId(address: String): String =
        runCatching { query.getOrCreateThreadId(address) }.getOrDefault("")

    override suspend fun markThreadRead(threadId: String) {
        runCatching { query.markThreadRead(threadId) }
    }

    override suspend fun deleteMessage(messageId: String) {
        runCatching { query.deleteMessage(messageId) }
    }
}
