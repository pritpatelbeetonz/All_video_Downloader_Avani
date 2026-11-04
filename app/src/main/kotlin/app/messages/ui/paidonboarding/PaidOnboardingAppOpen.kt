package app.messages.ui.paidonboarding

import android.app.Activity
import android.content.Context
import android.util.Log
import app.messages.Controller
import app.messages.config.AdsConfigManager
import app.messages.preferences.PreferenceHelper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * Dedicated App Open ad for the paid onboarding funnel (Flow A web-steps).
 *
 * Kept SEPARATE from the clean monetization AppOpenAdManager / AdsController on purpose: that path
 * treats onboarding as a prohibited surface, while this is the competitor-funnel replica gated only
 * by its own Remote Config flags (see AdsConfigManager.isPaidAppOpen*).
 *
 * Hard rules:
 *  - The unit id comes from Remote Config (flag 4). If it is blank / "11" / malformed
 *    (isPaidAppOpenAdIdValid == false) this is a pure NO-OP: no load, no state, no loader/shimmer.
 *  - We only ever show a REAL, already-loaded ad. If nothing is loaded we continue INSTANTLY —
 *    never a spinner.
 *  - While the ad is on screen the screen guard stands down (setAdShowing) so the Engine can never
 *    close, dismiss, or steal focus from it.
 *
 * Logcat filter: tag:PaidAppOpen
 */
object PaidOnboardingAppOpen {

    private const val TAG = "PaidAppOpen"

    private var ad: AppOpenAd? = null
    private var loading = false

    private fun isPremium(context: Context): Boolean =
        PreferenceHelper.getInstance(context).isPremium()

    /** Preload the ad so it's ready by the time the user closes the web step. No-op if the id is invalid. */
    fun preload(context: Context) {
        if (isPremium(context)) return
        if (!AdsConfigManager.isPaidAppOpenAdIdValid()) {
            Log.d(TAG, "skip preload: invalid ad id (${AdsConfigManager.paidAppOpenAdId()}) — no request")
            return
        }
        if (ad != null || loading) return
        loading = true
        val unit = AdsConfigManager.paidAppOpenAdId()
        Log.d(TAG, "⏳ loading App Open ($unit)")
        AppOpenAd.load(
            Controller.getInstance(),
            unit,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    ad = loaded
                    loading = false
                    Log.d(TAG, "✅ App Open loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                    Log.w(TAG, "❌ App Open failed to load: ${error.code} ${error.message}")
                }
            },
        )
    }

    /**
     * Show the ad if (and only if) a real one is already loaded, then run [onDone]. In every other
     * case ([isPremium], invalid id, or nothing loaded) [onDone] runs IMMEDIATELY so the user is
     * never stuck and never sees a loader. [onDone] runs exactly once on all paths.
     */
    fun showIfReady(activity: Activity, onDone: () -> Unit) {
        if (isPremium(activity)) {
            onDone(); return
        }
        if (!AdsConfigManager.isPaidAppOpenAdIdValid()) {
            Log.d(TAG, "skip show: invalid ad id — continue instantly")
            onDone(); return
        }
        val loaded = ad
        if (loaded == null) {
            Log.d(TAG, "no ad ready — continue instantly, preload for next time")
            preload(activity)
            onDone(); return
        }

        // Guard stands down BEFORE show, so the Engine can't race / close the ad.
        learning.screenguard.ScreenGuardDemo.setAdShowing(true)
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            private var finished = false
            private fun finish(reason: String) {
                if (finished) return
                finished = true
                Log.d(TAG, "App Open $reason — guard resumes, continue")
                ad = null
                learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                preload(activity)
                onDone()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "👁️ App Open showed")
            }

            override fun onAdDismissedFullScreenContent() = finish("dismissed")

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "❌ App Open failed to show: ${error.code} ${error.message}")
                finish("show-failed")
            }
        }
        Log.d(TAG, "🚀 showing App Open")
        loaded.show(activity)
    }
}
