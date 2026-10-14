package app.messages.data.telephony

import app.messages.data.telephony.model.MessageDirection
import app.messages.data.telephony.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Outgoing-SMS status mapping — especially the stale-OUTBOX reconciliation that
 * stops a missed [android.telephony.SmsManager] sent-callback from showing a
 * "sending" clock forever (the [STUCK_SENDING_MS] grace window).
 *
 * Telephony int codes are used directly (INBOX=1, SENT=2, OUTBOX=4, FAILED=5,
 * QUEUED=6; status NONE=-1, COMPLETE=0, PENDING=32, FAILED=64) so the test stays
 * a pure JVM test with no Android runtime.
 */
class MessageMapperTest {

    private val now = 1_700_000_000_000L

    private fun row(type: Int, status: Int = -1, ageMs: Long = 0L) = MessageRow(
        id = "1",
        threadId = "7",
        address = "+15551234567",
        dateEpochMs = now - ageMs,
        type = type,
        status = status,
        isRead = true,
        body = "hi",
        simSlot = null,
    )

    @Test fun `fresh outbox shows sending`() {
        val m = row(type = 4, ageMs = 5_000L).toSmsMessage(now)
        assertEquals(MessageDirection.Outgoing, m.direction)
        assertEquals(MessageStatus.Sending, m.status)
    }

    @Test fun `outbox past the grace window reconciles to sent`() {
        val m = row(type = 4, ageMs = STUCK_SENDING_MS + 1).toSmsMessage(now)
        assertEquals(MessageStatus.Sent, m.status)
    }

    @Test fun `outbox exactly at the threshold is still sending`() {
        val m = row(type = 4, ageMs = STUCK_SENDING_MS).toSmsMessage(now)
        assertEquals(MessageStatus.Sending, m.status)
    }

    @Test fun `sent with no delivery report is sent`() {
        assertEquals(MessageStatus.Sent, row(type = 2, status = -1).toSmsMessage(now).status)
    }

    @Test fun `sent with pending report is sent`() {
        assertEquals(MessageStatus.Sent, row(type = 2, status = 32).toSmsMessage(now).status)
    }

    @Test fun `sent with complete report is delivered`() {
        assertEquals(MessageStatus.Delivered, row(type = 2, status = 0).toSmsMessage(now).status)
    }

    @Test fun `sent with failed report is failed`() {
        assertEquals(MessageStatus.Failed, row(type = 2, status = 64).toSmsMessage(now).status)
    }

    @Test fun `inbox is incoming and delivered`() {
        val m = row(type = 1).toSmsMessage(now)
        assertEquals(MessageDirection.Incoming, m.direction)
        assertEquals(MessageStatus.Delivered, m.status)
    }

    @Test fun `failed type maps to failed`() {
        assertEquals(MessageStatus.Failed, row(type = 5).toSmsMessage(now).status)
    }

    @Test fun `queued maps to queued`() {
        assertEquals(MessageStatus.Queued, row(type = 6).toSmsMessage(now).status)
    }
}
