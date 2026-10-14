package app.messages.core.scheduling

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the scheduling offer (§F13): free = 3 active, Premium = unlimited, plus
 * the time-window guard. A regression here changes what we sell.
 */
class ScheduleRulesTest {

    private val now = 1_700_000_000_000L
    private val inOneHour = now + 1.hours.inWholeMilliseconds

    // ── Free cap ───────────────────────────────────────────────────────────────
    @Test fun `free user may schedule below the cap`() {
        assertTrue(ScheduleRules.canScheduleMore(premium = false, activeCount = 0))
        assertTrue(ScheduleRules.canScheduleMore(premium = false, activeCount = 2))
    }

    @Test fun `free user blocked at the cap`() {
        assertFalse(ScheduleRules.canScheduleMore(premium = false, activeCount = 3))
        assertFalse(ScheduleRules.canScheduleMore(premium = false, activeCount = 9))
    }

    @Test fun `premium is unlimited`() {
        assertTrue(ScheduleRules.canScheduleMore(premium = true, activeCount = 3))
        assertTrue(ScheduleRules.canScheduleMore(premium = true, activeCount = 10_000))
    }

    // ── Remaining slots ────────────────────────────────────────────────────────
    @Test fun `remaining slots count down for free`() {
        assertEquals(3, ScheduleRules.remainingSlots(premium = false, activeCount = 0))
        assertEquals(1, ScheduleRules.remainingSlots(premium = false, activeCount = 2))
        assertEquals(0, ScheduleRules.remainingSlots(premium = false, activeCount = 3))
    }

    @Test fun `remaining slots never negative`() {
        assertEquals(0, ScheduleRules.remainingSlots(premium = false, activeCount = 5))
    }

    // ── Validation ─────────────────────────────────────────────────────────────
    @Test fun `valid future send within cap is Ok`() {
        assertEquals(
            ScheduleRules.Check.Ok,
            ScheduleRules.validate(premium = false, activeCount = 0, scheduledAtMs = inOneHour, nowMs = now),
        )
    }

    @Test fun `at-cap free user cannot schedule even a valid time`() {
        assertEquals(
            ScheduleRules.Check.AtCap,
            ScheduleRules.validate(premium = false, activeCount = 3, scheduledAtMs = inOneHour, nowMs = now),
        )
    }

    @Test fun `past or present time is rejected`() {
        assertEquals(
            ScheduleRules.Check.InPast,
            ScheduleRules.validate(false, activeCount = 0, scheduledAtMs = now, nowMs = now),
        )
        assertEquals(
            ScheduleRules.Check.InPast,
            ScheduleRules.validate(false, activeCount = 0, scheduledAtMs = now - 1, nowMs = now),
        )
    }

    @Test fun `beyond the max window is rejected`() {
        val tooFar = now + 366.days.inWholeMilliseconds
        assertEquals(
            ScheduleRules.Check.TooFar,
            ScheduleRules.validate(false, activeCount = 0, scheduledAtMs = tooFar, nowMs = now),
        )
    }

    @Test fun `cap is checked before time window`() {
        // At cap AND in the past → cap wins (we tell the user the actionable thing).
        assertEquals(
            ScheduleRules.Check.AtCap,
            ScheduleRules.validate(false, activeCount = 3, scheduledAtMs = now - 1, nowMs = now),
        )
    }
}
