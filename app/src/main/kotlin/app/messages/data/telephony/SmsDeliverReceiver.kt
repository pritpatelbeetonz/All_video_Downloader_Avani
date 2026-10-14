package app.messages.data.telephony

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import app.messages.data.blocking.BlockingRepository
import app.messages.notifications.MessageNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Delivered to the default SMS handler only. Writes incoming SMS rows to the
 * Telephony provider (Android does NOT do this for us — that's the contract
 * with the default-handler role) and emits a [TelephonyEvent.MessageReceived]
 * so any active UI can refresh.
 *
 * MessagingStyle notifications + heads-up + inline reply wire in M2 once the
 * thread screen exists. For M1 the goal is "rows reach the inbox cleanly".
 */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject lateinit var eventBus: TelephonyEventBus
    @Inject lateinit var notifier: MessageNotifier
    @Inject lateinit var blocking: BlockingRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Group multipart segments from a single delivery into one logical row.
        val address = messages.first().displayOriginatingAddress.orEmpty()
        val body = buildString { for (m in messages) append(m.displayMessageBody) }
        val timestamp = messages.first().timestampMillis
        val subId = intent.getIntExtra("subscription", -1).takeIf { it != -1 }

        val inserted = writeIncoming(context, messages.first(), address, body, timestamp, subId)
        eventBus.emit(
            TelephonyEvent.MessageReceived(
                threadId = inserted.threadId,
                messageId = inserted.messageId,
            ),
        )
        // Blocked senders: the message is still written (so it appears in the
        // Blocked & spam folder), but we never raise a notification for it.
        if (inserted.threadId.isNotEmpty() && !blocking.isBlocked(address)) {
            notifier.notifyIncoming(inserted.threadId, address, body, timestamp)
        }
    }

    private fun writeIncoming(
        context: Context,
        first: SmsMessage,
        address: String,
        body: String,
        timestamp: Long,
        subId: Int?,
    ): InsertedMessage {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, first.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            if (subId != null) put("sub_id", subId)
        }
        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        val messageId = uri?.lastPathSegment.orEmpty()
        val threadId = lookupThreadId(context, messageId)
        return InsertedMessage(threadId = threadId, messageId = messageId)
    }

    private fun lookupThreadId(context: Context, messageId: String): String {
        if (messageId.isEmpty()) return ""
        var out = ""
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("thread_id"),
            "_id=?",
            arrayOf(messageId),
            null,
        )?.use { c -> if (c.moveToFirst()) out = c.getLong(0).toString() }
        return out
    }

    private data class InsertedMessage(val threadId: String, val messageId: String)
}
