package app.messages.ui.thread

import app.messages.data.telephony.model.MessageDirection
import app.messages.data.telephony.model.MessageStatus
import app.messages.data.telephony.model.SmsMessage
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadItemsTest {

    private val now: Date = dateAt(2026, Calendar.JUNE, 12, 18, 0)

    private fun dateAt(y: Int, m: Int, d: Int, h: Int, min: Int): Date =
        Calendar.getInstance().apply {
            set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0)
        }.time

    private fun msg(
        id: String,
        date: Date,
        outgoing: Boolean,
    ) = SmsMessage(
        id = id,
        threadId = "t1",
        address = "+100",
        body = "msg $id",
        date = date,
        direction = if (outgoing) MessageDirection.Outgoing else MessageDirection.Incoming,
        status = MessageStatus.Delivered,
        isRead = true,
    )

    @Test fun `empty input yields no items`() {
        assertTrue(buildThreadItems(emptyList(), now).isEmpty())
    }

    @Test fun `single message gets a day separator above it`() {
        val items = buildThreadItems(listOf(msg("1", now, outgoing = false)), now)
        assertEquals(2, items.size)
        assertTrue(items[0] is ThreadItem.DaySeparator)
        assertEquals("Today", (items[0] as ThreadItem.DaySeparator).label)
        assertTrue(items[1] is ThreadItem.Message)
    }

    @Test fun `consecutive same-sender messages within window are grouped`() {
        // newest-first input (repository order)
        val newer = msg("2", dateAt(2026, Calendar.JUNE, 12, 10, 2), outgoing = true)
        val older = msg("1", dateAt(2026, Calendar.JUNE, 12, 10, 0), outgoing = true)
        val items = buildThreadItems(listOf(newer, older), now)

        val messages = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(2, messages.size)
        // older is visually above; it connects below to newer
        assertTrue(messages[0].connectedBelow)
        assertFalse(messages[0].connectedAbove)
        // newer is last in group → shows meta, connects above
        assertTrue(messages[1].connectedAbove)
        assertTrue(messages[1].showMeta)
        assertFalse(messages[0].showMeta)
    }

    @Test fun `different senders are not grouped`() {
        val incoming = msg("2", dateAt(2026, Calendar.JUNE, 12, 10, 1), outgoing = false)
        val outgoing = msg("1", dateAt(2026, Calendar.JUNE, 12, 10, 0), outgoing = true)
        val items = buildThreadItems(listOf(incoming, outgoing), now)
        val messages = items.filterIsInstance<ThreadItem.Message>()
        assertFalse(messages[0].connectedBelow)
        assertFalse(messages[1].connectedAbove)
        assertTrue(messages[0].showMeta)
        assertTrue(messages[1].showMeta)
    }

    @Test fun `messages on different days get separate separators`() {
        val today = msg("2", dateAt(2026, Calendar.JUNE, 12, 9, 0), outgoing = false)
        val yesterday = msg("1", dateAt(2026, Calendar.JUNE, 11, 9, 0), outgoing = false)
        val items = buildThreadItems(listOf(today, yesterday), now)
        val separators = items.filterIsInstance<ThreadItem.DaySeparator>()
        assertEquals(2, separators.size)
        assertEquals("Yesterday", separators[0].label)
        assertEquals("Today", separators[1].label)
    }

    @Test fun `same sender across a day boundary is not grouped`() {
        val today = msg("2", dateAt(2026, Calendar.JUNE, 12, 0, 1), outgoing = true)
        val yesterday = msg("1", dateAt(2026, Calendar.JUNE, 11, 23, 59), outgoing = true)
        val items = buildThreadItems(listOf(today, yesterday), now)
        val messages = items.filterIsInstance<ThreadItem.Message>()
        assertFalse(messages[0].connectedBelow)
        assertFalse(messages[1].connectedAbove)
    }
}
