package app.messages.data.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One message in an export (§F15). */
data class ExportMessage(
    val dateMs: Long,
    val outgoing: Boolean,
    val address: String,
    val body: String,
)

/**
 * Renders an export to TXT or CSV (§F15 — both free; PDF is the Premium format
 * and is rendered separately on-device via PdfDocument). Pure string building so
 * the CSV escaping is unit-testable.
 */
object MessageExporter {

    private fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

    fun toPlainText(rows: List<ExportMessage>, title: String): String {
        val sb = StringBuilder()
        sb.append("=== ").append(title).append(" ===\n\n")
        for (m in rows) {
            val who = if (m.outgoing) "You" else m.address
            sb.append('[').append(stamp(m.dateMs)).append("] ")
                .append(who).append(": ").append(m.body).append('\n')
        }
        return sb.toString()
    }

    fun toCsv(rows: List<ExportMessage>): String {
        val sb = StringBuilder()
        sb.append("date,direction,address,message\n")
        for (m in rows) {
            sb.append(escape(stamp(m.dateMs))).append(',')
                .append(if (m.outgoing) "Sent" else "Received").append(',')
                .append(escape(m.address)).append(',')
                .append(escape(m.body)).append('\n')
        }
        return sb.toString()
    }

    /** RFC-4180 quoting: wrap in quotes when the value has a comma, quote, CR or LF. */
    private fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
