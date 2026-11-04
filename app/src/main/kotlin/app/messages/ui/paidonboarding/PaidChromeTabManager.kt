package app.messages.ui.paidonboarding

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import app.messages.attribution.InstallSourceManager
import app.messages.config.AdsConfigManager
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Manages the in-app Chrome Custom Tab experience for paid Google users during onboarding.
 *
 * Flow:
 *  1. Language Screen Done -> if enabled, opens Chrome Custom Tab; on close, opens Flow A (11 onboarding pages).
 *  2. Between Onboarding Pages -> on Next, if enabled, opens Chrome Custom Tab; on close, advances to next page.
 *  3. Remote Config flag `chrome_tab_enabled`: when false (or URL blank), directly navigates without opening Chrome.
 */
object PaidChromeTabManager {

    private const val TAG = "PaidChromeTab"

    /**
     * When true, every Chrome tab open loads the page FRESH (no cached page reused).
     * We do this by appending a unique cache-buster query param on each launch so the URL
     * never matches a previously cached document. We do NOT warm up or prefetch anything.
     */
    private const val FORCE_FRESH_OPEN = true
    private const val CACHE_BUSTER_PARAM = "nocache"

    /**
     * Appends a unique query param so the URL is new on every open -> forces a fresh load.
     * Preserves any existing query params. No-op when [FORCE_FRESH_OPEN] is false.
     */
    private fun freshUrl(url: String): String {
        if (!FORCE_FRESH_OPEN) return url
        val stamp = System.currentTimeMillis()
        val sep = if (url.contains("?")) "&" else "?"
        val fresh = "$url$sep$CACHE_BUSTER_PARAM=$stamp"
        Log.d(TAG, "🆕 Fresh-open cache-buster applied -> $fresh")
        return fresh
    }

    /**
     * Checks whether the current install is attributed to Google Ads (paid Google user).
     */
    fun isGooglePaidUser(): Boolean {
        val isAd = InstallSourceManager.isAdInstall()
        val source = InstallSourceManager.currentSource()
        return isAd && source == InstallSourceManager.GOOGLE
    }

    /**
     * Returns true if Chrome Custom Tab should be shown after the Language screen.
     */
    fun shouldShowAfterLanguage(context: Context): Boolean {
        if (!isGooglePaidUser()) {
            Log.d(TAG, "Install is not a Google paid install (source=${InstallSourceManager.currentSource()}) -> skip Chrome")
            return false
        }
        if (!AdsConfigManager.isChromeTabEnabled()) {
            Log.d(TAG, "Chrome tab disabled via Remote Config (chrome_tab_enabled=false) -> skip Chrome")
            return false
        }
        if (!AdsConfigManager.config.chromeTabAfterLanguage) {
            Log.d(TAG, "chromeTabAfterLanguage is disabled -> skip Chrome")
            return false
        }
        val url = AdsConfigManager.getChromeTabUrl()
        if (url.isBlank()) {
            Log.d(TAG, "Chrome tab URL is empty in Remote Config -> skip Chrome")
            return false
        }
        return true
    }

    /**
     * Returns true if Chrome Custom Tab should be shown when clicking Next between onboarding pages.
     */
    fun shouldShowBetweenPages(context: Context, currentPosition: Int): Boolean {
        if (!isGooglePaidUser()) return false
        if (!AdsConfigManager.isChromeTabEnabled()) return false
        if (!AdsConfigManager.config.chromeTabBetweenPages) return false

        val interval = AdsConfigManager.config.chromeTabPageInterval.coerceAtLeast(1)
        if (interval > 1 && (currentPosition + 1) % interval != 0) {
            return false
        }

        val url = AdsConfigManager.getChromeTabUrl()
        if (url.isBlank()) return false
        return true
    }

    /**
     * Builds the Chrome Custom Tabs intent configured with app styles.
     */
    fun createCustomTabsIntent(context: Context): CustomTabsIntent {
        warmup(context)   // ensure the session + TAB_HIDDEN callback is being prepared
        // Builder(session): launching WITH the session is what makes Chrome deliver TAB_HIDDEN to us.
        return CustomTabsIntent.Builder(session)
            .setShowTitle(true)
            .build()
    }

    /**
     * Resolves a SPECIFIC browser package to host the Custom Tab, so Android opens it directly
     * instead of showing an "Open with" chooser when several browsers are installed and none is set
     * as default. Prefers Chrome-family providers, then any Custom-Tabs-capable browser, then the
     * device default. Returns null if nothing suitable is found (then we launch without a package).
     */
    private val PREFERRED_BROWSERS = listOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
        "com.microsoft.emmx", "com.brave.browser", "com.sec.android.app.sbrowser",
    )

    fun resolveBrowserPackage(context: Context): String? {
        return try {
            CustomTabsClient.getPackageName(context, PREFERRED_BROWSERS)
                ?.takeIf { it.isNotBlank() }
                ?: CustomTabsClient.getPackageName(context, null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "resolveBrowserPackage failed: ${e.message}")
            null
        }
    }

    // ========================================================================
    //  WARMED SESSION + TAB_HIDDEN callback — exactly how the reference app
    //  (WebAds) detects the user leaving the tab WITHOUT Usage Access:
    //  Chrome itself fires onNavigationEvent(TAB_HIDDEN) through the session.
    // ========================================================================
    private var client: CustomTabsClient? = null
    private var session: CustomTabsSession? = null
    private var boundPkg: String? = null

    /** Chrome reports navigation events here; TAB_HIDDEN (6) = the user left our tab. */
    private val tabCallback = object : CustomTabsCallback() {
        override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
            if (navigationEvent == TAB_HIDDEN) {
                Log.d(TAG, "🌐 [tabCallback] TAB_HIDDEN (6) — Chrome says user left the tab → notify guard")
                learning.screenguard.ScreenGuardDemo.onCustomTabHidden()
            }
        }
    }

    private val connection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, newClient: CustomTabsClient) {
            client = newClient
            try { newClient.warmup(0L) } catch (_: Exception) {}
            session = newClient.newSession(tabCallback)
            Log.d(TAG, "🔥 Custom Tabs service connected ($name) — warmed + session created (callback live)")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            client = null; session = null; boundPkg = null
            Log.d(TAG, "🔌 Custom Tabs service disconnected")
        }
    }

    /** Bind + warm up the browser so the TAB_HIDDEN callback is live by the time the tab opens. */
    fun warmup(context: Context) {
        val pkg = resolveBrowserPackage(context) ?: return
        if (pkg == boundPkg && session != null) return   // already bound to this browser
        try {
            if (CustomTabsClient.bindCustomTabsService(context.applicationContext, pkg, connection)) {
                boundPkg = pkg
                Log.d(TAG, "🔗 Binding Custom Tabs service to $pkg (for TAB_HIDDEN callback)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "bindCustomTabsService failed: ${e.message}")
        }
    }

    /**
     * Launches Chrome Custom Tab via the given [launcher].
     *
     * If launching fails or URL is invalid, calls [onFallback] immediately so the user is never stuck.
     */
    fun launch(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>,
        stepTag: String = "step",
        onFallback: () -> Unit
    ): Boolean {
        // Our own screen transition (Language/Flow A → web step). Tell the guard so a Home press right
        // after Done isn't mistaken for the user escaping (reference noteInAppAdvance).
        learning.screenguard.ScreenGuardDemo.noteOwnNavigation()

        val url = AdsConfigManager.getChromeTabUrl()
        if (url.isBlank()) {
            Log.w(TAG, "Cannot launch Chrome tab: URL is blank")
            onFallback()
            return false
        }

        // Flag 3 (independent): App-Open-only mode — the step shows ONLY our App Open ad and no
        // web/custom tab opens at all. There is no "close" to react to, so on dismiss we just
        // continue (onFallback = advance to the next step). If the ad id is invalid or nothing is
        // loaded, showIfReady continues instantly — the user is never stuck.
        if (AdsConfigManager.isPaidAppOpenOnlyMode()) {
            Log.d(TAG, "🅰️ App-Open-only mode ($stepTag) → App Open ad instead of web/custom tab")
            PaidOnboardingAppOpen.showIfReady(activity, onDone = onFallback)
            return true
        }

        val freshUrl = freshUrl(url)
        // Remote Config decides the surface via two booleans:
        //  - isWebViewOn wins → force the in-app WebView.
        //  - else isCustomTabOn → Custom Tab (WebView only as fallback if no browser supports it).
        //  - else (both off) → in-app WebView.
        val forceWebView = AdsConfigManager.isWebViewOn() || !AdsConfigManager.isCustomTabOn()
        val pkg = if (forceWebView) null else resolveBrowserPackage(activity)

        return try {
            if (pkg == null) {
                // WebView path — either forced by Remote Config, or no Custom-Tabs browser is installed
                // (the fallback, same role as the reference app's WebLinkActivity). We do NOT call
                // onChromeSessionOpen(): the WebView is OUR OWN activity, so the guard stays active and
                // treats it like any onboarding screen (full permission-free reorder/trap on leave).
                Log.w(TAG, "🌐 WebView path (${if (forceWebView) "forced by Remote Config" else "no Custom Tabs browser"}) → PaidWebLinkActivity for $stepTag")
                launcher.launch(PaidWebLinkActivity.intent(activity, freshUrl))
                true
            } else {
                val customTabsIntent = createCustomTabsIntent(activity)
                val intent = customTabsIntent.intent.apply {
                    data = Uri.parse(freshUrl)
                    setPackage(pkg)   // pin the browser so Android opens it directly (no "Open with" chooser)
                }
                Log.d(TAG, "📌 Pinned Custom Tab to browser: $pkg (no chooser)")
                Log.d(TAG, "🚀 Opening Chrome Custom Tab ($stepTag) for URL: $url")
                try {
                    FirebaseAnalytics.getInstance(activity).logEvent("paid_chrome_tab_opened", null)
                } catch (_: Exception) {}

                launcher.launch(intent)
                // The Custom Tab is CHROME's activity, not ours → tell the guard to stand down for it
                // (TAB_HIDDEN / webGuard handle the tab). Centralised here so both call sites stay in sync.
                learning.screenguard.ScreenGuardDemo.onChromeSessionOpen()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch web step ($stepTag): ${e.message}", e)
            onFallback()
            false
        }
    }

    /**
     * Call from the web-step close hook (the chromeTabLauncher ActivityResult callback, which fires
     * for BOTH the close button and the OS back gesture, on the Custom Tab and the WebView alike).
     *
     * Resumes the guard, then — if the master switch is on (flag 1) — shows an App Open ad on every
     * [AdsConfigManager.paidAppOpenCloseInterval]-th close (flag 2). Either way [onContinue] runs
     * exactly once to advance the flow; an invalid ad id or no loaded ad continues instantly.
     */
    fun onWebStepClosed(activity: Activity, onContinue: () -> Unit) {
        learning.screenguard.ScreenGuardDemo.onChromeSessionClosed()   // tab closed → guard resumes normal behaviour

        if (!AdsConfigManager.isPaidAppOpenOnCloseOn()) {
            onContinue(); return
        }
        val count = PaidOnboardingPrefs.incrementWebCloseCount(activity)
        val interval = AdsConfigManager.paidAppOpenCloseInterval()
        if (count % interval != 0) {
            Log.d(TAG, "App Open on close: skip (close #$count, interval $interval)")
            onContinue(); return
        }
        Log.d(TAG, "App Open on close: show (close #$count, interval $interval)")
        PaidOnboardingAppOpen.showIfReady(activity, onDone = onContinue)
    }
}

