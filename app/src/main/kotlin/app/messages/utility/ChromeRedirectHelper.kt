package app.messages.utility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

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

    // ---- Pre-render ("pool") for the launcher redirect page -------------------------------------------
    // Own Custom Tabs session, separate from Flow A's (whose callback talks to the screen guard).
    private var client: CustomTabsClient? = null
    private var session: CustomTabsSession? = null
    private var boundPkg: String? = null
    /** URL Chrome was asked to pre-render and that no open has consumed yet (duplicate guard). */
    private var prefetchedUrl: String? = null
    private var pendingPrefetchUrl: String? = null

    private val connection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, newClient: CustomTabsClient) {
            client = newClient
            runCatching { newClient.warmup(0L) }
            session = newClient.newSession(CustomTabsCallback())
            Log.d(TAG, "Custom Tabs service connected ($name)")
            pendingPrefetchUrl?.let { pending -> pendingPrefetchUrl = null; session?.let { doPrefetch(it, pending) } }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            client = null; session = null; boundPkg = null; prefetchedUrl = null
        }
    }

    /**
     * Asks Chrome to pre-render [url] so the launcher redirect opens instantly. No-op when the URL is not
     * web, no Custom Tabs browser exists, or the same URL is already pre-rendered and unopened.
     */
    @JvmStatic
    fun prefetch(context: Context, url: String) {
        if (!isWebUrl(url)) return
        val clean = url.trim()
        if (prefetchedUrl == clean) { Log.d(TAG, "prefetch skipped: already pre-rendered ($clean)"); return }
        val pkg = app.messages.ui.paidonboarding.PaidChromeTabManager.resolveBrowserPackage(context) ?: return
        val s = session
        if (s != null && boundPkg == pkg) { doPrefetch(s, clean); return }
        pendingPrefetchUrl = clean
        runCatching {
            if (CustomTabsClient.bindCustomTabsService(context.applicationContext, pkg, connection)) boundPkg = pkg
        }.onFailure { Log.w(TAG, "bindCustomTabsService failed: ${it.message}") }
    }

    private fun doPrefetch(s: CustomTabsSession, url: String) {
        val ok = runCatching { s.mayLaunchUrl(Uri.parse(url), null, null) }.getOrDefault(false)
        if (ok) prefetchedUrl = url
        Log.d(TAG, "prefetch mayLaunchUrl=$ok -> $url")
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

        // Attempt 0: the pre-rendered page, opened WITH the session that pre-rendered it.
        val s = session
        if (s != null && prefetchedUrl == url.trim() && boundPkg != null) {
            try {
                val intent = CustomTabsIntent.Builder(s).setShowTitle(true).build().intent
                intent.setPackage(boundPkg)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.data = uri
                context.startActivity(intent)
                prefetchedUrl = null   // consumed: the pool is refilled by the next prefetch()
                Log.d(TAG, "Launched pre-rendered page: $url")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Pre-rendered launch failed, falling back: ${e.message}")
            }
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
