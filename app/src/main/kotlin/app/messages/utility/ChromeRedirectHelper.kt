package app.messages.utility

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Resilient utility for opening external URLs in Chrome or Chrome Custom Tabs.
 * Falls back through:
 * 1. Dedicated Chrome Custom Tab (com.android.chrome)
 * 2. Generic System Custom Tab
 * 3. Standard ACTION_VIEW Browser Intent
 */
object ChromeRedirectHelper {
    private const val TAG = "ChromeRedirect"
    private const val CHROME_PACKAGE = "com.android.chrome"

    /** Only http(s) URLs with a host are opened. */
    @JvmStatic
    fun isWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    /** True when some installed app (Chrome or any browser) can open [url]. */
    @JvmStatic
    fun canOpen(context: Context, url: String): Boolean {
        if (!isWebUrl(url)) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
        return runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)
    }

    @JvmStatic
    fun openUrl(context: Context, url: String): Boolean {
        if (!isWebUrl(url)) {
            Log.w(TAG, "Cannot launch browser: not an http(s) URL: '$url'")
            return false
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null) {
            Log.e(TAG, "Cannot parse URL: $url")
            return false
        }

        // Attempt 1: Dedicated Chrome Custom Tab
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.setPackage(CHROME_PACKAGE)
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, uri)
            Log.d(TAG, "Launched via Chrome Custom Tabs: $url")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Chrome package failed, attempting system Custom Tabs: ${e.message}")
        }

        // Attempt 2: Generic System Custom Tabs
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, uri)
            Log.d(TAG, "Launched via Generic Custom Tabs: $url")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Generic Custom Tabs failed, attempting ACTION_VIEW intent: ${e.message}")
        }

        // Attempt 3: ACTION_VIEW fallback
        return try {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            Log.d(TAG, "Launched via ACTION_VIEW: $url")
            true
        } catch (e: Exception) {
            Log.e(TAG, "All browser launch attempts failed: ${e.message}")
            false
        }
    }
}
