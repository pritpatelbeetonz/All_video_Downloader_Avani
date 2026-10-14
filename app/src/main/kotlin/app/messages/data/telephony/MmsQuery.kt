package app.messages.data.telephony

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads MMS messages + parts from the Telephony MMS provider for display
 * (build doc §F5). The provider stores already-parsed parts, so rendering
 * existing MMS needs no PDU library — that's only required to *receive* (parse
 * an incoming PDU) or *send* (compose one).
 *
 * Gotchas baked in here:
 * - MMS `date` is in **seconds**, not milliseconds (unlike SMS). We ×1000.
 * - `msg_box` gives direction (1=inbox, 2=sent), there's no `type` column.
 * - The sender address lives in `content://mms/{id}/addr` with type=137 (FROM).
 * - SMIL (`application/smil`) parts are layout, not content — skipped.
 */
@Singleton
class MmsQuery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    private val partUriBase: Uri = Uri.parse("content://mms/part")

    fun listMmsForThread(threadId: String, limit: Int): List<MmsMessageRow> {
        val out = mutableListOf<MmsMessageRow>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "date", "msg_box", "sub", "read"),
            "thread_id=?",
            arrayOf(threadId),
            "date DESC LIMIT $limit",
        )?.use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxDate = c.getColumnIndexOrThrow("date")
            val idxBox = c.getColumnIndexOrThrow("msg_box")
            val idxRead = c.getColumnIndex("read")
            while (c.moveToNext()) {
                val mmsId = c.getLong(idxId).toString()
                val parts = readParts(mmsId)
                out.add(
                    MmsMessageRow(
                        id = "mms_$mmsId",
                        threadId = threadId,
                        // seconds → millis
                        dateEpochMs = c.getLong(idxDate) * 1000L,
                        msgBox = c.getInt(idxBox),
                        isRead = idxRead < 0 || c.getInt(idxRead) == 1,
                        address = readSender(mmsId),
                        text = parts.text,
                        attachmentUri = parts.attachmentUri,
                        attachmentContentType = parts.attachmentContentType,
                    ),
                )
            }
        }
        return out
    }

    private data class Parts(
        val text: String,
        val attachmentUri: String?,
        val attachmentContentType: String?,
    )

    private fun readParts(mmsId: String): Parts {
        val textBuilder = StringBuilder()
        var attachmentUri: String? = null
        var attachmentCt: String? = null
        resolver.query(
            partUriBase,
            arrayOf("_id", "ct", "text", "_data"),
            "mid=?",
            arrayOf(mmsId),
            null,
        )?.use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxCt = c.getColumnIndexOrThrow("ct")
            val idxText = c.getColumnIndexOrThrow("text")
            while (c.moveToNext()) {
                val ct = c.getString(idxCt).orEmpty()
                when {
                    ct == "application/smil" -> Unit // layout, skip
                    ct == "text/plain" -> {
                        val t = c.getString(idxText)
                        if (!t.isNullOrEmpty()) {
                            if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                            textBuilder.append(t)
                        }
                    }
                    else -> {
                        // First media/vcard part wins as the bubble attachment.
                        if (attachmentUri == null) {
                            val partId = c.getLong(idxId)
                            attachmentUri = Uri.withAppendedPath(
                                partUriBase, partId.toString(),
                            ).toString()
                            attachmentCt = ct
                        }
                    }
                }
            }
        }
        return Parts(textBuilder.toString(), attachmentUri, attachmentCt)
    }

    /**
     * Resolves the thread id, sender, and a snippet for a freshly-received MMS
     * (referenced by its `content://mms/{id}` URI) so we can emit an event and
     * post a notification for it.
     */
    fun notifyInfo(messageUri: Uri): MmsNotifyInfo? {
        val mmsId = messageUri.lastPathSegment ?: return null
        var threadId = ""
        resolver.query(
            messageUri, arrayOf("thread_id"), null, null, null,
        )?.use { c -> if (c.moveToFirst()) threadId = c.getLong(0).toString() }
        if (threadId.isBlank()) return null
        val parts = readParts(mmsId)
        val snippet = parts.text.ifBlank {
            when {
                parts.attachmentContentType?.startsWith("image/") == true -> "Photo"
                parts.attachmentContentType?.startsWith("video/") == true -> "Video"
                parts.attachmentContentType?.startsWith("audio/") == true -> "Audio"
                else -> "Attachment"
            }
        }
        return MmsNotifyInfo(
            threadId = threadId,
            address = readSender(mmsId),
            snippet = snippet,
        )
    }

    private fun readSender(mmsId: String): String {
        var sender = ""
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        resolver.query(
            addrUri,
            arrayOf("address", "type"),
            "type=$ADDR_TYPE_FROM",
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val a = c.getString(0)
                // The MMS "insert-address-token" placeholder marks self-sent.
                if (!a.isNullOrBlank() && a != INSERT_ADDRESS_TOKEN) sender = a
            }
        }
        return sender
    }

    private companion object {
        const val MESSAGE_BOX_INBOX = 1
        const val ADDR_TYPE_FROM = 137 // PduHeaders.FROM
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"
    }
}

/** Minimal info to event + notify a newly-received MMS. */
data class MmsNotifyInfo(
    val threadId: String,
    val address: String,
    val snippet: String,
)

/** A single MMS message flattened for the unified thread list. */
data class MmsMessageRow(
    val id: String,
    val threadId: String,
    val dateEpochMs: Long,
    /** Telephony.Mms.MESSAGE_BOX_*: 1=inbox, 2=sent, 3=drafts, 4=outbox. */
    val msgBox: Int,
    val isRead: Boolean,
    val address: String,
    val text: String,
    val attachmentUri: String?,
    val attachmentContentType: String?,
) {
    val isInbox: Boolean get() = msgBox == 1
}
