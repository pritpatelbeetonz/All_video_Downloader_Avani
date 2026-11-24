package app.messages.ui.paidonboarding

import android.app.Activity
import android.content.Context
import android.util.Log
import app.messages.Controller
import app.messages.ads.ng.FullScreenContentCallback
import app.messages.ads.ng.NextGenAds
import app.messages.ads.ng.NgLoadCallback
import app.messages.config.AdsConfigManager
import app.messages.preferences.PreferenceHelper
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * App Open ad for the `app_open` token of the Set-as-Default sequence (`set_default_sequence`).
 * Uses its OWN Remote Config unit ([AdsConfigManager.setDefaultAppOpenAdId], falling back to the
 * Flow A unit) and its own cache, so it never competes with the Flow A App Open.
 *
 * Same rules as [PaidOnboardingAppOpen]: invalid id -> no-op; only a real, already-loaded ad is shown
 * (otherwise continue instantly, never a spinner); guard stands down while it is on screen.
 * Duplicate-safe: preload does nothing while an ad is cached or a request is in flight.
 *
 * Logcat filter: tag:SetDefaultAppOpen
 */
object SetDefaultAppOpen {

    private const val TAG = "SetDefaultAppOpen"

    private var ad: AppOpenAd? = null
    private var loading = false

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
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                ad = null
                loading = false
                Log.w(TAG, "❌ failed to load: ${error.code} ${error.message}")
            }
        })
    }

    /** Shows the ad if one is loaded, then [onDone]; in every other case [onDone] runs immediately (once). */
    fun showIfReady(activity: Activity, onDone: () -> Unit) {
        if (PreferenceHelper.getInstance(activity).isPremium() || !AdsConfigManager.isSetDefaultAppOpenAdIdValid()) {
            onDone(); return
        }
        val loaded = ad
        if (loaded == null) {
            Log.d(TAG, "no ad ready -> continue instantly, preload for next time")
            preload(activity)
            onDone(); return
        }
        learning.screenguard.ScreenGuardDemo.setAdShowing(true)
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            private var finished = false
            private fun finish(reason: String) {
                if (finished) return
                finished = true
                Log.d(TAG, "$reason -> guard resumes, continue")
                ad = null
                learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                learning.screenguard.ScreenGuardDemo.onFullScreenAdClosed()
                preload(activity)
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
