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
}
