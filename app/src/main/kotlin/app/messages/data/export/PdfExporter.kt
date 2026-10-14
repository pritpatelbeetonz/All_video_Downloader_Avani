package app.messages.data.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders an export to a formatted A4 PDF (§F15 — Premium format). Uses the
 * platform [PdfDocument]; no third-party PDF lib needed. Handles word wrapping
 * and page breaks so long threads paginate cleanly.
 */
object PdfExporter {
    // A4 at 72dpi.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val LINE = 16f

    fun write(out: OutputStream, rows: List<ExportMessage>, title: String) {
        val doc = PdfDocument()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val body = Paint().apply { textSize = 11f; color = Color.BLACK }
        val header = Paint().apply { textSize = 11f; color = Color.DKGRAY; isFakeBoldText = true }
        val titlePaint = Paint().apply { textSize = 18f; color = Color.BLACK; isFakeBoldText = true }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        canvas.drawText(title, MARGIN, y, titlePaint)
        y += LINE * 2

        val maxWidth = PAGE_W - MARGIN * 2
        for (m in rows) {
            val who = if (m.outgoing) "You" else m.address
            val headerLine = "[${stamp.format(Date(m.dateMs))}] $who:"
            val wrapped = wrap(m.body, body, maxWidth - 12f)
            val needed = LINE * (wrapped.size + 1) + LINE / 2

            if (y + needed > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }

            canvas.drawText(headerLine, MARGIN, y, header)
            y += LINE
            for (line in wrapped) {
                canvas.drawText(line, MARGIN + 12f, y, body)
                y += LINE
            }
            y += LINE / 2
        }

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    /** Greedy word wrap measured with the paint, so it fits the page width. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = ArrayList<String>()
        for (paragraph in text.split("\n")) {
            var current = StringBuilder()
            for (word in paragraph.split(" ")) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            lines.add(current.toString())
        }
        return lines
    }
}
