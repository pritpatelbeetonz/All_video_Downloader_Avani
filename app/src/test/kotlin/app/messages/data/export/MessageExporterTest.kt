package app.messages.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageExporterTest {

    private val rows = listOf(
        ExportMessage(dateMs = 0L, outgoing = false, address = "+15551234567", body = "Hi there"),
        ExportMessage(dateMs = 0L, outgoing = true, address = "+15551234567", body = "Hello, \"world\"\nsecond line"),
    )

    @Test fun `csv has a header row`() {
        assertTrue(MessageExporter.toCsv(rows).lineSequence().first() == "date,direction,address,message")
    }

    @Test fun `csv quotes and escapes tricky bodies`() {
        val csv = MessageExporter.toCsv(rows)
        // The comma, embedded quotes, and newline force quoting; inner quotes double.
        assertTrue(csv.contains("\"Hello, \"\"world\"\"\nsecond line\""))
    }

    @Test fun `csv leaves simple fields unquoted`() {
        val csv = MessageExporter.toCsv(rows)
        assertTrue(csv.contains(",Received,+15551234567,Hi there"))
    }

    @Test fun `plain text labels direction and includes body`() {
        val txt = MessageExporter.toPlainText(rows, "Chat with Sam")
        assertTrue(txt.startsWith("=== Chat with Sam ==="))
        assertTrue(txt.contains("+15551234567: Hi there"))
        assertTrue(txt.contains("You: Hello, \"world\""))
    }

    @Test fun `empty export still has header`() {
        assertEquals("date,direction,address,message\n", MessageExporter.toCsv(emptyList()))
    }
}
