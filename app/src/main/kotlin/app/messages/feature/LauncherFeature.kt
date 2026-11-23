package app.messages.feature

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * An app that lives in the launcher's page 0 ("the slot"). The launcher never knows which app it
 * is: it only talks to this contract through [LauncherSlot].
 *
 * To plug in another app: implement this interface and point [LauncherSlot.feature] at it.
 * To remove the app: point [LauncherSlot.feature] at [NoFeature].
 * Step-by-step: .claude/skills/add-launcher-feature/SKILL.md
 */
interface LauncherFeature {
    /** Stable id for logs / analytics, e.g. "video_downloader". */
    val id: String

    /** Content description of the left edge tab. */
    @get:StringRes val labelRes: Int

    /** Icon on the left edge tab (a round launcher-style drawable / mipmap). */
    @get:DrawableRes val iconRes: Int

    /** False = the slot is empty: the left edge tab is hidden and page 0 shows a blank placeholder. */
    val isEnabled: Boolean get() = true

    /**
     * True when the app needs its own horizontal / diagonal gestures (Flutter, WebView, reels).
     * The launcher then turns the ViewPager swipe off while page 0 is showing; users leave it
     * with the page-indicator / edge tabs / back button instead.
     */
    val lockPagerSwipeOnPage: Boolean

    /** The fragment shown on page 0. Called each time the pager (re)creates the page. */
    fun createFragment(): Fragment

    /**
     * Start any heavy init (engine boot, SDK init). Idempotent. Flow screens call it as soon as it
     * is safe to block the main thread (after splash / onboarding ad flows), NOT at app start.
     */
    fun warmUp(app: Application) {}

    /** Safety net: guarantee the app is ready right before [createFragment]'s view attaches. */
    fun ensureReady(app: Application) {}

    /** The Remote Config ads JSON was (re)fetched. Forward it if the app shows its own ads. */
    fun onAdsConfig(json: String) {}

    /** The user picked a language in the launcher's language screen. */
    fun onLanguageChanged(code: String) {}
}
