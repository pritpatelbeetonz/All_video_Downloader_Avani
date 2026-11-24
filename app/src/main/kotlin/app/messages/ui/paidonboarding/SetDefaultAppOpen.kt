package app.messages.ui.paidonboarding

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.messages.Controller
import app.messages.ads.ng.FullScreenContentCallback
import app.messages.ads.ng.NextGenAds
import app.messages.ads.ng.NgLoadCallback
import app.messages.ads.ng.fullScreenContentCallback
import app.messages.config.AdsConfigManager
import app.messages.preferences.PreferenceHelper
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App Open ad for the `app_open` token of the Set-as-Default sequence (`set_default_sequence`).
 * Uses its OWN Remote Config unit ([AdsConfigManager.setDefaultAppOpenAdId], falling back to the
 * Flow A unit) and its own cache, so it never competes with the Flow A App Open.
 *
 * Same behaviour as the Set-as-Default interstitial:
 *  - loaded            -> shown at once;
 *  - still in flight   -> loader, waits up to [IN_FLIGHT_WAIT_MS] on that SAME request (no 2nd request);
 *  - nothing, and on-demand allowed -> requests one on the spot behind a loader ([ON_DEMAND_WAIT_MS]);
 *  - anything else (invalid id, premium, timeout, failure) -> continues, never stuck.
 * Duplicate-safe: preload does nothing while an ad is cached or a request is in flight.
 *
 * Logcat filter: tag:SetDefaultAppOpen
 */
object SetDefaultAppOpen {

    private const val TAG = "SetDefaultAppOpen"
    private const val IN_FLIGHT_WAIT_MS = AdsConfigManager.SET_DEFAULT_IN_FLIGHT_WAIT_MS
    private const val ON_DEMAND_WAIT_MS = 7_000L

    private var ad: AppOpenAd? = null
    private var loading = false
    private val waiters = mutableListOf<(AppOpenAd?) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun preload(context: Context) {
        if (PreferenceHelper.getInstance(context).isPremium()) return
        if (!AdsConfigManager.isSetDefaultAppOpenAdIdValid()) {
            Log.d(TAG, "skip preload: invalid ad id (${AdsConfigManager.setDefaultAppOpenAdId()})")
            return
        }
        if (ad != null) { Log.d(TAG, "already cached -> skip"); return }
        if (loading) { Log.d(TAG, "already loading (in-flight) -> skip duplicate"); return }
        loading = true
        val unit = AdsConfigManager.setDefaultAppOpenAdId()
        Log.d(TAG, "⏳ loading App Open ($unit)")
        NextGenAds.loadAppOpen(Controller.getInstance(), unit, object : NgLoadCallback<AppOpenAd>() {
            override fun onAdLoaded(loaded: AppOpenAd) {
                ad = loaded
                loading = false
                Log.d(TAG, "✅ loaded")
                notifyWaiters(loaded)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                ad = null
                loading = false
                Log.w(TAG, "❌ failed to load: ${error.code} ${error.message}")
                notifyWaiters(null)
            }
        })
    }

    private fun notifyWaiters(result: AppOpenAd?) {
        val pending = waiters.toList()
        waiters.clear()
        pending.forEach { it(result) }
    }

    /**
     * Shows the ad (waiting behind a loader when it is in flight / requested on the spot), then [onDone].
     * [onDone] runs exactly once on every path.
     */
    fun showIfReady(activity: Activity, allowOnDemand: Boolean = true, onDone: () -> Unit) {
        if (PreferenceHelper.getInstance(activity).isPremium() || !AdsConfigManager.isSetDefaultAppOpenAdIdValid()) {
            onDone(); return
        }
        val loaded = ad
        if (loaded != null) {
            showLoaded(activity, loaded, onDone)
            return
        }
        if (loading) {
            Log.d(TAG, "in flight -> loader, waiting up to ${IN_FLIGHT_WAIT_MS}ms (no 2nd request)")
            waitWithLoader(activity, IN_FLIGHT_WAIT_MS, onDone)
            return
        }
        if (!allowOnDemand) {
            Log.d(TAG, "nothing cached / in flight and on-demand off -> continue")
            preload(activity)
            onDone(); return
        }
        Log.d(TAG, "nothing cached / in flight -> request on the spot behind a loader (${ON_DEMAND_WAIT_MS}ms)")
        preload(activity)
        if (!loading) { onDone(); return }   // preload refused (invalid / premium)
        waitWithLoader(activity, ON_DEMAND_WAIT_MS, onDone)
    }

    private fun waitWithLoader(activity: Activity, timeoutMs: Long, onDone: () -> Unit) {
        val done = AtomicBoolean(false)
        var dialog: Dialog? = null
        var timeout: Runnable? = null
        lateinit var waiter: (AppOpenAd?) -> Unit

        fun finish(result: AppOpenAd?) {
            if (!done.compareAndSet(false, true)) return
            timeout?.let { mainHandler.removeCallbacks(it) }
            waiters.remove(waiter)
            try { dialog?.dismiss() } catch (_: Exception) {}
            if (result != null) showLoaded(activity, result, onDone) else onDone()
        }
        waiter = { finish(it) }
        timeout = Runnable {
            Log.w(TAG, "⏱️ wait timed out (${timeoutMs}ms) -> continue without the ad")
            finish(null)
        }
        dialog = AdsConfigManager.showLoadingDialog(activity, onInterrupted = { finish(null) })
        mainHandler.postDelayed(timeout!!, timeoutMs)
        waiters.add(waiter)
    }

    private fun showLoaded(activity: Activity, loaded: AppOpenAd, onDone: () -> Unit) {
        if (!AdsConfigManager.isActivitySafeToShowAd(activity)) {
            Log.w(TAG, "activity not in a safe state to show -> skip, keep the ad cached")
            onDone(); return
        }
        ad = null   // taken out of the cache so nothing else can show the same ad
        learning.screenguard.ScreenGuardDemo.setAdShowing(true)
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            private var finished = false
            private fun finish(reason: String) {
                if (finished) return
                finished = true
                Log.d(TAG, "$reason -> guard resumes, continue")
                learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                learning.screenguard.ScreenGuardDemo.onFullScreenAdClosed()
                // No refill: this screen shows the ad once, so a new request here would never be shown.
                onDone()
            }

            override fun onAdClicked() { learning.screenguard.ScreenGuardDemo.noteAdClickNavigation() }
            override fun onAdDismissedFullScreenContent() = finish("dismissed")
            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.w(TAG, "❌ failed to show: ${error.code} ${error.message}")
                finish("show-failed")
            }
        }
        Log.d(TAG, "🚀 showing App Open")
        loaded.show(activity)
    }
}
