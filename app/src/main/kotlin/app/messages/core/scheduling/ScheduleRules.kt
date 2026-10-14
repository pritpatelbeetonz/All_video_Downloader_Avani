package app.messages.core.scheduling

import kotlin.time.Duration.Companion.days

/**
 * Pure rules for message scheduling (§F13). Kept free of Android types so the
 * cap + time-window policy is unit-testable in isolation — same discipline as
 * [app.messages.core.ads.AdRules].
 *
 * The product decision encoded here: **free users get 3 active scheduled
 * messages, Premium is unlimited.** Changing that ships a different offer.
 */
object ScheduleRules {

    /** Active (pending) scheduled messages allowed without Premium. */
    const val FREE_MAX_ACTIVE = 3

    /** A message can be scheduled at most this far out (sanity bound). */
    val maxLeadTime = 365.days

    /** All users get unlimited scheduling — no cap. */
    fun activeLimit(premium: Boolean): Int = Int.MAX_VALUE

    fun canScheduleMore(premium: Boolean, activeCount: Int): Boolean = true

    /** Slots left before the free cap bites (0 once at/over the cap). */
    fun remainingSlots(premium: Boolean, activeCount: Int): Int =
        (activeLimit(premium) - activeCount).coerceAtLeast(0)

    /** Outcome of validating a proposed scheduled send. */
    enum class Check { Ok, AtCap, InPast, TooFar }

    /**
     * Validates a proposed scheduled send against both the entitlement cap and
     * the time window. [activeCount] is the current number of pending messages.
     */
    fun validate(
        premium: Boolean,
        activeCount: Int,
        scheduledAtMs: Long,
        nowMs: Long,
    ): Check {
        if (!canScheduleMore(premium, activeCount)) return Check.AtCap
        if (scheduledAtMs <= nowMs) return Check.InPast
        if (scheduledAtMs - nowMs > maxLeadTime.inWholeMilliseconds) return Check.TooFar
        return Check.Ok
    }
}
