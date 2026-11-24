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

/**
 * Manages the in-app Chrome Custom Tab experience for paid Google users during onboarding.
 *
 * Flow:
 * Opened only as the `chrome` token of a Remote Config step sequence ([FlowASequenceRunner]).
 * Remote Config flag `chrome_tab_enabled`: when false (or URL blank), the token is skipped.
 */
object PaidChromeTabManager {

    private const val TAG = "PaidChromeTab"

    /**
     * When true, every Chrome tab open loads the page FRESH (no cached page reused).
     * We do this by appending a unique cache-buster query param on each launch so the URL
     * never matches a previously cached document. Prefetch ([prefetchStep]) pre-renders the SAME
     * fresh URL ahead of the open so the tab is ready instantly — it is still a fresh, unique URL,
     * never a stale cached page, and the prefetch itself does NOT fire ad impressions (Chrome's
     * prefetch warms the page without running it); the impression fires when the user opens the tab.
     */
    private const val FORCE_FRESH_OPEN = true
    private const val CACHE_BUSTER_PARAM = "nocache"

    /**
     * Fresh (cache-busted) URLs we told Chrome to pre-render, keyed by step tag. [launch] reuses the
     * SAME string so the open matches the prerender (a different cache-buster would miss it). Consumed
     * on open; re-filled by the next [prefetchStep].
     */
    private val prefetchedFreshUrls = HashMap<String, String>()

    /** A prefetch requested before the Custom Tabs session was ready; flushed on connect. */
    private var pendingPrefetchStep: String? = null

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
     * Checks whether the current install is attributed to a paid ad campaign (Google, Facebook, Unity).
     */
    fun isPaidUser(): Boolean {
        return InstallSourceManager.isAdInstall()
    }

    fun isGooglePaidUser(): Boolean = isPaidUser()

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
            // A prefetch asked for before the session existed (user reached the screen faster than the
            // service bound) — run it now that we can.
            pendingPrefetchStep?.let { pending ->
                pendingPrefetchStep = null
                session?.let { doPrefetch(it, pending) }
            }
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
     * Pre-renders the given step's web page in the background via Custom Tabs `mayLaunchUrl`, so the tab
     * is already loaded when the user opens it. Computes a fresh (cache-busted) URL ONCE and remembers it
     * so [launch] opens the exact same URL (otherwise the prerender wouldn't match). Call it one step
     * ahead — e.g. while the user is on page N, prefetch `page_${N+1}` (the web step shown on Next).
     *
     * No-op unless this is a paid install with the Chrome tab enabled, a URL configured, and the Custom
     * Tab (not the WebView) path active. Does NOT fire ad impressions — Chrome prefetch warms the page
     * without executing it; the impression fires when the user actually opens the tab.
     */
    fun prefetchStep(context: Context, stepTag: String) {
        if (!isPaidUser()) return
        if (!AdsConfigManager.isChromeTabEnabled()) return
        if (AdsConfigManager.isPaidAppOpenOnlyMode()) return
        // Prefetch is a Custom-Tabs feature; the WebView path can't use it.
        if (AdsConfigManager.isWebViewOn() || !AdsConfigManager.isCustomTabOn()) return
        val url = AdsConfigManager.getChromeTabUrl(stepTag)
        if (url.isBlank()) return

        warmup(context)
        val s = session
        if (s == null) {
            // Service still binding — remember this step and prefetch it the moment we connect.
            pendingPrefetchStep = stepTag
            Log.d(TAG, "🔮 prefetch($stepTag): session not ready yet (warming) — will prefetch on connect")
            return
        }
        doPrefetch(s, stepTag)
    }

    /** Computes the fresh URL once, remembers it for [launch] to reuse, and asks Chrome to pre-render it. */
    private fun doPrefetch(s: CustomTabsSession, stepTag: String) {
        // Already pre-rendered and not yet opened: don't build a new URL / ask Chrome again (a new
        // cache-buster would also make the pre-rendered page miss).
        if (prefetchedFreshUrls.containsKey(stepTag)) {
            Log.d(TAG, "🔮 prefetch($stepTag) already pre-rendered -> skip duplicate")
            return
        }
        val url = AdsConfigManager.getChromeTabUrl(stepTag)
        if (url.isBlank()) return
        val fresh = freshUrl(url)
        prefetchedFreshUrls[stepTag] = fresh
        try {
            val ok = s.mayLaunchUrl(Uri.parse(fresh), null, null)
            Log.d(TAG, "🔮 prefetch($stepTag) mayLaunchUrl=$ok -> $fresh")
        } catch (e: Exception) {
            Log.w(TAG, "🔮 prefetch($stepTag) failed: ${e.message}")
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

        val url = AdsConfigManager.getChromeTabUrl(stepTag)
        if (url.isBlank()) {
            Log.w(TAG, "Cannot launch Chrome tab for $stepTag: URL is blank")
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

        // Reuse the exact URL we pre-rendered for this step (so the prerender is used); otherwise make a
        // fresh one now. Either way it is a unique cache-busted URL — never a stale cached page.
        val freshUrl = prefetchedFreshUrls.remove(stepTag) ?: freshUrl(url)
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
     * Resumes the guard, then continues. Ads around web steps come only from the Remote Config
     * step sequence ([FlowASequenceRunner]); nothing is shown here.
     */
    fun onWebStepClosed(
        activity: Activity,
        stepTag: String = "default",
        onContinue: () -> Unit
    ) {
        learning.screenguard.ScreenGuardDemo.onChromeSessionClosed()   // tab closed → guard resumes normal behaviour
        // The continue below navigates to the next funnel screen (e.g. Language → Flow A) and finishes
        // this one. Mark it as our own navigation so a Home press / leave signal during that brief
        // handoff doesn't make the guard yank the finishing screen back instead of advancing.
        learning.screenguard.ScreenGuardDemo.noteOwnNavigation(900L)

        onContinue()
    }
}

