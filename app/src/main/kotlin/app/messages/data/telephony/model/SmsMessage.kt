package app.messages.data.telephony.model

import java.util.Date

enum class MessageDirection { Incoming, Outgoing }

/** Lifecycle status of an outgoing message. Mirrors `Telephony.Sms.STATUS_*`. */
enum class MessageStatus { Pending, Sending, Sent, Delivered, Failed, Queued }

/** MIME-style content kind for a message — drives bubble rendering. */
enum class MessageKind { Text, Image, Video, Audio, VCard, Location, Unknown }

/**
 * A single SMS / MMS message. The Telephony content provider is the source
 * of truth; this is the canonical Kotlin model the UI consumes.
 */
data class SmsMessage(
    val id: String,
    val threadId: String,
    val address: String,
    val body: String,
    val date: Date,
    val direction: MessageDirection,
    val status: MessageStatus,
    val isRead: Boolean,
    val kind: MessageKind = MessageKind.Text,
    val isStarred: Boolean = false,
    val simSlot: Int? = null,
    val attachmentUri: String? = null,
) {
    val isIncoming: Boolean get() = direction == MessageDirection.Incoming
    val isOutgoing: Boolean get() = direction == MessageDirection.Outgoing
    val hasFailed: Boolean get() = status == MessageStatus.Failed
}
