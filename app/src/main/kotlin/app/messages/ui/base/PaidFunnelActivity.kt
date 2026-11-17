package app.messages.ui.base

import android.view.KeyEvent

/**
 * Shared base for the paid onboarding "funnel" screens — the equivalent of the reference app's
 * base `FunnelScreenActivity`. It intercepts the two silent exits on EVERY funnel screen (not just
 * set-default) and hands them to the central guard:
 *
 *  - HOME (Home button / gesture) → [ScreenGuardDemo.onFunnelHome] → routes forward to Language.
 *  - RECENTS (□ key / app-switch)  → [ScreenGuardDemo.onFunnelRecents] → pulls the same screen back.
 *
 * Both are gated on [isFunnelTrapActive] (paid/non-organic install still in onboarding); organic
 * installs fall through to normal behaviour. Back is intentionally NOT handled here so each screen
 * keeps its own back logic (previous page, save-language, stay, etc.).
 *
 * Learning/testing only — the whole trap is behind `ScreenGuardDemo.ENABLE_FUNNEL_TRAP`.
 */
abstract class PaidFunnelActivity : BaseActivity() {

    /** Is this screen currently a trapped (non-organic, mid-onboarding) funnel screen? */
    protected open fun isFunnelTrapActive(): Boolean =
        app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)

    override fun onUserLeaveHint() {
        if (isFunnelTrapActive()) {
            learning.screenguard.ScreenGuardDemo.onFunnelHome(this)
        }
        super.onUserLeaveHint()
    }

    /**
     * Called by the guard's CLOSE_SYSTEM_DIALOGS receiver when a RECENTS or (gesture-nav) HOME/RECENTS
     * SWIPE happens on this screen — the one exit `onUserLeaveHint` + `dispatchKeyEvent` miss, because
     * a swipe is not a key event and the leave hint fires too late. Gated by this screen's own
     * [isFunnelTrapActive] (so it never fires while our picker/ad/next-navigation is in progress).
     */
    fun onSystemGestureLeave() {
        if (isFunnelTrapActive()) {
            learning.screenguard.ScreenGuardDemo.onFunnelRecents(this)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH &&
            event.action == KeyEvent.ACTION_DOWN &&
            isFunnelTrapActive()
        ) {
            learning.screenguard.ScreenGuardDemo.onFunnelRecents(this)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && isFunnelTrapActive() && !isFinishing && !isDestroyed) {
            // Focus loss alone is ambiguous: Home / Recents / gesture swipes ALSO drop focus. Wait a
            // moment; if a specific escape (home/recent/back/...) was logged meanwhile, this was not
            // the shade, so don't log (or re-tag the esc_suc as) "shade".
            val focusLostAt = android.os.SystemClock.elapsedRealtime()
            window.decorView.postDelayed({
                val logger = app.messages.ui.paidonboarding.FlowAEscapeLogger
                val specificEscapeSeen = logger.lastAttemptAtMs >= focusLostAt - 1500
                // Opening our own Custom Tab / ad / role picker also drops this screen's focus — that is
                // our flow, not a shade pull.
                val ownFlow = learning.screenguard.ScreenGuardDemo.isOwnFlowActive()
                if (!specificEscapeSeen && !ownFlow && !hasWindowFocus() && !isFinishing && !isDestroyed) {
                    logger.onEscapeAttempt(this, "shade")
                }
            }, 400)
        }
    }
}
