package app.messages.core.time

import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now: Date = Calendar.getInstance().apply {
        // 2026-06-12 14:30 — pinned so tests are stable.
        set(2026, Calendar.JUNE, 12, 14, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private val locale = Locale.US

    @Test fun `today renders as HH-mm`() {
        val t = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 12, 9, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals("09:05", formatRelativeTimestamp(t, now = now, locale = locale))
    }

    @Test fun `yesterday renders as Yesterday`() {
        val t = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 11, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals("Yesterday", formatRelativeTimestamp(t, now = now, locale = locale))
    }

    @Test fun `this week renders as weekday abbrev`() {
        val t = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 8, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals("Mon", formatRelativeTimestamp(t, now = now, locale = locale))
    }

    @Test fun `this year older than a week renders as MMM d`() {
        val t = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 14, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals("Mar 14", formatRelativeTimestamp(t, now = now, locale = locale))
    }

    @Test fun `previous year renders as d-M-yy`() {
        val t = Calendar.getInstance().apply {
            set(2024, Calendar.DECEMBER, 31, 23, 59, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals("31/12/24", formatRelativeTimestamp(t, now = now, locale = locale))
    }
}
