package app.messages.data.export

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports a conversation or all messages to a user-picked SAF file (§F15).
 * TXT/CSV are free; PDF is gated to Premium by the caller (the repository will
 * render whatever [Format] it's handed).
 */
@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Format { TXT, CSV, PDF }

    /** @param threadId null = export every message; otherwise just that thread. */
    suspend fun export(
        uri: Uri,
        format: Format,
        threadId: String?,
        title: String,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = readMessages(threadId)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                when (format) {
                    Format.TXT -> os.write(MessageExporter.toPlainText(rows, title).toByteArray(Charsets.UTF_8))
                    Format.CSV -> os.write(MessageExporter.toCsv(rows).toByteArray(Charsets.UTF_8))
                    Format.PDF -> PdfExporter.write(os, rows, title)
                }
            } ?: error("Couldn't open the file for writing")
            rows.size
        }
    }

    private fun readMessages(threadId: String?): List<ExportMessage> {
        val cols = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
        )
        val sel = threadId?.let { "${Telephony.Sms.THREAD_ID} = ?" }
        val args = threadId?.let { arrayOf(it) }
        val out = ArrayList<ExportMessage>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, cols, sel, args, "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val iA = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iB = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iD = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val iT = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (c.moveToNext()) {
                val type = c.getInt(iT)
                out.add(
                    ExportMessage(
                        dateMs = c.getLong(iD),
                        outgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                        address = c.getString(iA) ?: "",
                        body = c.getString(iB) ?: "",
                    ),
                )
            }
        }
        return out
    }
}
