package app.messages.data.telephony

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side queries against the Android Telephony content provider.
 *
 * Schema decisions per build doc §7.3:
 * - Source of truth is the Telephony provider; we never cache here.
 * - Address resolution goes through `content://mms-sms/canonical-addresses`.
 * - Unread count is computed per-thread; M2 will switch this to a single
 *   GROUP BY query if the per-thread loop becomes a hotspot.
 */
@Singleton
class ConversationQuery @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    private val canonicalAddressesUri: Uri =
        Uri.parse("content://mms-sms/canonical-addresses")

    // `Telephony.Threads.CONTENT_URI` resolves to `content://mms-sms/conversations`
    // which by default returns a per-message view that has no `message_count` or
    // `recipient_ids` columns. The MmsSmsProvider switches to the thread-summary
    // view (the one with those columns) only when `?simple=true` is on the query
    // string. Confirmed on OnePlus / Android 16; the bare URI throws
    // "no such column: message_count" mid-query.
    private val threadsSimpleUri: Uri = Telephony.Threads.CONTENT_URI
        .buildUpon()
        .appendQueryParameter("simple", "true")
        .build()

    private data class TempConversationRow(
        val threadId: String,
        val recipientIds: List<Long>,
        val snippet: String,
        val lastActivityEpochMs: Long,
        val messageCount: Int,
        val isArchived: Boolean,
    )

    fun listConversations(): List<ConversationRow> {
        val tempRows = mutableListOf<TempConversationRow>()
        val allRecipientIds = mutableSetOf<Long>()

        val projection = arrayOf(
            "_id", "date", "message_count", "recipient_ids", "snippet", "read", "archived",
        )
        resolver.query(
            threadsSimpleUri,
            projection, null, null, "date DESC",
        )?.use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxDate = c.getColumnIndexOrThrow("date")
            val idxCount = c.getColumnIndexOrThrow("message_count")
            val idxRecipients = c.getColumnIndexOrThrow("recipient_ids")
            val idxSnippet = c.getColumnIndexOrThrow("snippet")
            val idxArchived = c.getColumnIndex("archived")
            while (c.moveToNext()) {
                val threadId = c.getLong(idxId).toString()
                val recipientsStr = c.getString(idxRecipients).orEmpty()
                val archived = if (idxArchived >= 0) c.getInt(idxArchived) == 1 else false
                
                val ids = recipientsStr.trim().split(' ').mapNotNull { it.toLongOrNull() }
                allRecipientIds.addAll(ids)
                
                tempRows.add(
                    TempConversationRow(
                        threadId = threadId,
                        recipientIds = ids,
                        snippet = c.getString(idxSnippet) ?: "",
                        lastActivityEpochMs = c.getLong(idxDate),
                        messageCount = c.getInt(idxCount),
                        isArchived = archived,
                    )
                )
            }
        }

        val addressMap = resolveAllAddresses(allRecipientIds)
        val unreadCounts = queryAllUnreadCounts()

        return tempRows.map { row ->
            ConversationRow(
                threadId = row.threadId,
                addresses = row.recipientIds.mapNotNull { addressMap[it] },
                snippet = row.snippet,
                lastActivityEpochMs = row.lastActivityEpochMs,
                messageCount = row.messageCount,
                unreadCount = unreadCounts[row.threadId] ?: 0,
                isArchived = row.isArchived,
            )
        }
    }

    private fun resolveAllAddresses(ids: Set<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val addressById = HashMap<Long, String>(ids.size)
        val idList = ids.toList()
        val chunkSize = 900
        for (i in idList.indices step chunkSize) {
            val chunk = idList.subList(i, minOf(i + chunkSize, idList.size))
            val placeholders = chunk.joinToString(",") { "?" }
            resolver.query(
                canonicalAddressesUri,
                arrayOf("_id", "address"),
                "_id IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { c ->
                val idxId = c.getColumnIndexOrThrow("_id")
                val idxAddr = c.getColumnIndexOrThrow("address")
                while (c.moveToNext()) {
                    val addr = c.getString(idxAddr)
                    if (!addr.isNullOrBlank()) {
                        addressById[c.getLong(idxId)] = addr
                    }
                }
            }
        }
        return addressById
    }

    private fun queryAllUnreadCounts(): Map<String, Int> {
        val counts = HashMap<String, Int>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("thread_id"),
            "read=0 AND type=?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            null,
        )?.use { c ->
            val idxThread = c.getColumnIndexOrThrow("thread_id")
            while (c.moveToNext()) {
                val tid = c.getLong(idxThread).toString()
                counts[tid] = (counts[tid] ?: 0) + 1
            }
        }
        return counts
    }


    fun listMessages(threadId: String, limit: Int, offset: Int): List<MessageRow> {
        val out = mutableListOf<MessageRow>()
        val projection = arrayOf(
            "_id", "thread_id", "address", "person", "date", "date_sent",
            "type", "status", "read", "body", "sub_id",
        )
        val sortOrder = "date DESC LIMIT $limit OFFSET $offset"
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "thread_id=?", arrayOf(threadId), sortOrder,
        )?.use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxThread = c.getColumnIndexOrThrow("thread_id")
            val idxAddr = c.getColumnIndexOrThrow("address")
            val idxDate = c.getColumnIndexOrThrow("date")
            val idxType = c.getColumnIndexOrThrow("type")
            val idxStatus = c.getColumnIndexOrThrow("status")
            val idxRead = c.getColumnIndexOrThrow("read")
            val idxBody = c.getColumnIndexOrThrow("body")
            val idxSub = c.getColumnIndex("sub_id")
            while (c.moveToNext()) {
                out.add(
                    MessageRow(
                        id = c.getLong(idxId).toString(),
                        threadId = c.getLong(idxThread).toString(),
                        address = c.getString(idxAddr) ?: "",
                        dateEpochMs = c.getLong(idxDate),
                        type = c.getInt(idxType),
                        status = c.getInt(idxStatus),
                        isRead = c.getInt(idxRead) == 1,
                        body = c.getString(idxBody) ?: "",
                        simSlot = if (idxSub >= 0) c.getInt(idxSub) else null,
                    ),
                )
            }
        }
        return out
    }

    /**
     * Resolves the participant addresses for a single thread — used by the
     * thread screen to title its app bar and to know where to send replies.
     * For a 1:1 conversation this is a single number; group threads return all.
     */
    fun getThreadAddresses(threadId: String): List<String> {
        var recipientIds = ""
        resolver.query(
            threadsSimpleUri,
            arrayOf("recipient_ids"),
            "_id=?",
            arrayOf(threadId),
            null,
        )?.use { c ->
            if (c.moveToFirst()) recipientIds = c.getString(0).orEmpty()
        }
        if (recipientIds.isBlank()) {
            // Fallback: derive from the most recent message's address column.
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("address"),
                "thread_id=?",
                arrayOf(threadId),
                "date DESC LIMIT 1",
            )?.use { c ->
                if (c.moveToFirst()) {
                    val addr = c.getString(0)
                    if (!addr.isNullOrBlank()) return listOf(addr)
                }
            }
            return emptyList()
        }
        return resolveAddresses(recipientIds)
    }

    fun markThreadRead(threadId: String): Int {
        val values = ContentValues().apply { put("read", 1) }
        return resolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "thread_id=? AND read=0",
            arrayOf(threadId),
        )
    }

    fun deleteMessage(messageId: String): Int {
        return resolver.delete(
            Telephony.Sms.CONTENT_URI,
            "_id=?",
            arrayOf(messageId),
        )
    }

    /** Full-text-ish search over message bodies (provider LIKE). */
    fun searchMessages(queryText: String, limit: Int = 50): List<MessageSearchResult> {
        val q = queryText.trim()
        if (q.isBlank()) return emptyList()
        val out = mutableListOf<MessageSearchResult>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("_id", "thread_id", "address", "body", "date"),
            "body LIKE ?",
            arrayOf("%$q%"),
            "date DESC LIMIT $limit",
        )?.use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxThread = c.getColumnIndexOrThrow("thread_id")
            val idxAddr = c.getColumnIndexOrThrow("address")
            val idxBody = c.getColumnIndexOrThrow("body")
            val idxDate = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                out.add(
                    MessageSearchResult(
                        messageId = c.getLong(idxId).toString(),
                        threadId = c.getLong(idxThread).toString(),
                        address = c.getString(idxAddr) ?: "",
                        body = c.getString(idxBody) ?: "",
                        dateEpochMs = c.getLong(idxDate),
                    ),
                )
            }
        }
        return out
    }

    /**
     * Returns the thread id for [address], creating one if needed. Used by the
     * compose screen to navigate into the conversation after sending.
     */
    fun getOrCreateThreadId(address: String): String =
        Telephony.Threads.getOrCreateThreadId(context, address).toString()

    fun deleteThread(threadId: String): Int {
        return resolver.delete(
            Uri.parse("content://mms-sms/conversations/$threadId"),
            null, null,
        )
    }

    private fun countUnread(threadId: String): Int {
        var count = 0
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("_id"),
            "thread_id=? AND read=0 AND type=?",
            arrayOf(threadId, Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            null,
        )?.use { c -> count = c.count }
        return count
    }

    private fun resolveAddresses(idsCsv: String): List<String> {
        if (idsCsv.isBlank()) return emptyList()
        val ids = idsCsv.trim().split(' ').mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return emptyList()
        // The per-id URI `content://mms-sms/canonical-addresses/{id}` is not
        // recognized on every OEM build (Motorola/Android 16 throws
        // IllegalStateException "Unrecognized URI"). Querying the table with
        // a standard `_id IN (?, ?, ...)` selection works everywhere.
        val placeholders = ids.joinToString(",") { "?" }
        val addressById = HashMap<Long, String>(ids.size)
        resolver.query(
            canonicalAddressesUri,
            arrayOf("_id", "address"),
            "_id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray(),
            null,
        )?.use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxAddr = c.getColumnIndexOrThrow("address")
            while (c.moveToNext()) {
                val addr = c.getString(idxAddr)
                if (!addr.isNullOrBlank()) addressById[c.getLong(idxId)] = addr
            }
        }
        // Preserve the input order — recipient_ids order is meaningful for groups.
        return ids.mapNotNull { addressById[it] }
    }
}

/** Plain row shape — repository maps these to [app.messages.data.telephony.model.Conversation]. */
data class ConversationRow(
    val threadId: String,
    val addresses: List<String>,
    val snippet: String,
    val lastActivityEpochMs: Long,
    val messageCount: Int,
    val unreadCount: Int,
    val isArchived: Boolean,
)

data class MessageRow(
    val id: String,
    val threadId: String,
    val address: String,
    val dateEpochMs: Long,
    val type: Int,
    val status: Int,
    val isRead: Boolean,
    val body: String,
    val simSlot: Int?,
)

/** A single search hit over message bodies. */
data class MessageSearchResult(
    val messageId: String,
    val threadId: String,
    val address: String,
    val body: String,
    val dateEpochMs: Long,
    val contactName: String? = null,
) {
    val displayName: String get() = contactName ?: address
}
