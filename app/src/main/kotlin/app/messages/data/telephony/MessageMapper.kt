package app.messages.data.telephony

import android.provider.Telephony
import app.messages.data.telephony.model.MessageDirection
import app.messages.data.telephony.model.MessageKind
import app.messages.data.telephony.model.MessageStatus
import app.messages.data.telephony.model.SmsMessage
import java.util.Date

/**
 * Grace window after which an OUTBOX row is treated as **sent** rather than a
 * perpetual "sending" clock — see [toSmsMessage].
 */
const val STUCK_SENDING_MS = 60_000L

/**
 * Maps a raw [MessageRow] (Telephony provider `type` + `status` columns) to the
 * canonical [SmsMessage] the UI consumes.
 *
 * `type` (Telephony.Sms.MESSAGE_TYPE_*): INBOX=1, SENT=2, DRAFT=3, OUTBOX=4,
 * FAILED=5, QUEUED=6.
 *
 * `status` (Telephony.Sms.STATUS_*): NONE=-1, COMPLETE=0, PENDING=32, FAILED=64.
 * Only meaningful for SENT rows — it carries the carrier delivery report.
 *
 * An outgoing row stuck in the OUTBOX past [STUCK_SENDING_MS] is surfaced as
 * **Sent** rather than a forever "sending" clock: the [android.telephony
 * .SmsManager] sent-callback can be missed (the process was killed before the
 * broadcast landed, or the carrier never returns one for an unreachable
 * number), and a message handed to the radio a minute ago is far likelier sent
 * than genuinely stuck. [now] is injectable for testing.
 */
fun MessageRow.toSmsMessage(now: Long = System.currentTimeMillis()): SmsMessage {
    val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
        MessageDirection.Incoming
    } else {
        MessageDirection.Outgoing
    }

    val mappedStatus = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.Delivered
        Telephony.Sms.MESSAGE_TYPE_SENT -> when (status) {
            Telephony.Sms.STATUS_COMPLETE -> MessageStatus.Delivered
            Telephony.Sms.STATUS_FAILED -> MessageStatus.Failed
            Telephony.Sms.STATUS_PENDING -> MessageStatus.Sent
            else -> MessageStatus.Sent // STATUS_NONE — sent, carrier gave no report
        }
        Telephony.Sms.MESSAGE_TYPE_OUTBOX ->
            // Resolve a missed sent-callback so it doesn't show a clock forever.
            if (now - dateEpochMs > STUCK_SENDING_MS) MessageStatus.Sent else MessageStatus.Sending
        Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.Queued
        Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.Failed
        else -> MessageStatus.Pending // DRAFT and anything unexpected
    }

    return SmsMessage(
        id = id,
        threadId = threadId,
        address = address,
        body = body,
        date = Date(dateEpochMs),
        direction = direction,
        status = mappedStatus,
        isRead = isRead,
        simSlot = simSlot,
    )
}

/** Maps an MMS row to the unified [SmsMessage] used by the thread list. */
fun MmsMessageRow.toSmsMessage(): SmsMessage {
    // Status reflects the real provider box, not an optimistic tick:
    // 1=inbox (delivered to us), 2=sent, 3=draft, 4=outbox (still sending).
    val mappedStatus = when (msgBox) {
        Telephony.Mms.MESSAGE_BOX_INBOX -> MessageStatus.Delivered
        Telephony.Mms.MESSAGE_BOX_SENT -> MessageStatus.Sent
        Telephony.Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.Sending
        Telephony.Mms.MESSAGE_BOX_DRAFTS -> MessageStatus.Pending
        else -> MessageStatus.Sending
    }
    return SmsMessage(
        id = id,
        threadId = threadId,
        address = address,
        body = text,
        date = Date(dateEpochMs),
        direction = if (isInbox) MessageDirection.Incoming else MessageDirection.Outgoing,
        status = mappedStatus,
        isRead = isRead,
        kind = kindForContentType(attachmentContentType),
        attachmentUri = attachmentUri,
    )
}

private fun kindForContentType(ct: String?): MessageKind = when {
    ct == null -> MessageKind.Text
    ct.startsWith("image/") -> MessageKind.Image
    ct.startsWith("video/") -> MessageKind.Video
    ct.startsWith("audio/") -> MessageKind.Audio
    ct.equals("text/x-vCard", ignoreCase = true) ||
        ct.equals("text/vcard", ignoreCase = true) -> MessageKind.VCard
    else -> MessageKind.Unknown
}
