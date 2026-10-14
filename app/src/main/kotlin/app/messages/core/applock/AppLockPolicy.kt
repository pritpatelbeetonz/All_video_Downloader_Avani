package app.messages.core.applock

import kotlin.time.Duration.Companion.seconds

/**
 * Pure decision for "should we show the lock screen right now?" (§F16). Kept
 * Android-free so the re-lock policy is unit-testable.
 */
object AppLockPolicy {

    /**
     * Grace period after the app is backgrounded before we re-prompt. This lets
     * the system photo picker / SAF document picker round-trip (which briefly
     * backgrounds us) return without nagging the user for the PIN again.
     */
    val graceWindow = 10.seconds
    val graceMs: Long = graceWindow.inWholeMilliseconds

    /**
     * @param lockEnabled        user turned App lock on
     * @param authedThisProcess  user has unlocked since this process started
     * @param backgroundedAtMs   when the app last went to background since auth (or null)
     * @param nowMs              current time
     */
    fun requiresUnlock(
        lockEnabled: Boolean,
        authedThisProcess: Boolean,
        backgroundedAtMs: Long?,
        nowMs: Long,
        graceMillis: Long = graceMs,
    ): Boolean {
        if (!lockEnabled) return false
        if (!authedThisProcess) return true // fresh/cold process always locks
        val backgrounded = backgroundedAtMs ?: return false
        return nowMs - backgrounded > graceMillis
    }
}
