package app.messages.ui.paidonboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
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
        return CustomTabsIntent.Builder()
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
        val url = AdsConfigManager.getChromeTabUrl()
        if (url.isBlank()) {
            Log.w(TAG, "Cannot launch Chrome tab: URL is blank")
            onFallback()
            return false
        }

        return try {
            val uri = Uri.parse(freshUrl(url))
            val customTabsIntent = createCustomTabsIntent(activity)
            val intent = customTabsIntent.intent.apply {
                data = uri
                // Pin a specific browser so Android opens it directly (no "Open with" chooser).
                val pkg = resolveBrowserPackage(activity)
                if (pkg != null) {
                    setPackage(pkg)
                    Log.d(TAG, "📌 Pinned Custom Tab to browser: $pkg (no chooser)")
                } else {
                    Log.w(TAG, "⚠️ No Custom Tabs browser resolved — system may show an 'Open with' chooser")
                }
            }
            Log.d(TAG, "🚀 Opening Chrome Custom Tab ($stepTag) for URL: $url")
            try {
                FirebaseAnalytics.getInstance(activity).logEvent("paid_chrome_tab_opened", null)
            } catch (_: Exception) {}

            launcher.launch(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Chrome Custom Tab: ${e.message}", e)
            onFallback()
            false
        }
    }
}

