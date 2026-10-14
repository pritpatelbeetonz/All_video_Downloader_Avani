package app.messages.core.applock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The re-lock decision table (§F16). */
class AppLockPolicyTest {

    private val now = 1_700_000_000_000L
    private val grace = AppLockPolicy.graceMs

    @Test fun `lock disabled never prompts`() {
        assertFalse(
            AppLockPolicy.requiresUnlock(
                lockEnabled = false, authedThisProcess = false,
                backgroundedAtMs = null, nowMs = now,
            ),
        )
    }

    @Test fun `fresh process with lock on must unlock`() {
        assertTrue(
            AppLockPolicy.requiresUnlock(
                lockEnabled = true, authedThisProcess = false,
                backgroundedAtMs = null, nowMs = now,
            ),
        )
    }

    @Test fun `authed and still foreground does not re-prompt`() {
        assertFalse(
            AppLockPolicy.requiresUnlock(
                lockEnabled = true, authedThisProcess = true,
                backgroundedAtMs = null, nowMs = now,
            ),
        )
    }

    @Test fun `brief background within grace does not re-prompt`() {
        assertFalse(
            AppLockPolicy.requiresUnlock(
                lockEnabled = true, authedThisProcess = true,
                backgroundedAtMs = now - (grace - 1), nowMs = now,
            ),
        )
    }

    @Test fun `background beyond grace re-prompts`() {
        assertTrue(
            AppLockPolicy.requiresUnlock(
                lockEnabled = true, authedThisProcess = true,
                backgroundedAtMs = now - (grace + 1), nowMs = now,
            ),
        )
    }
}
