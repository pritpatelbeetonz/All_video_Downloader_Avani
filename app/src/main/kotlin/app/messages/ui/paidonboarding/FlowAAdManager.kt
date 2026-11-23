package app.messages.ui.paidonboarding

import app.messages.ads.ng.*
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.messages.Controller
import app.messages.config.AdsConfigManager
import app.messages.preferences.PreferenceHelper
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import app.messages.ads.ng.FullScreenContentCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import app.messages.ads.ng.NgLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages App Open and Interstitial ads shown when a user closes a Chrome Custom Tab
 * (or in-app WebView) during Flow A onboarding.
 *
 * Supported formats per step:
 *  - APP_OPEN ("app_open", "appopen")
 *  - INTERSTITIAL ("interstitial", "inter")
 *  - OFF ("off", "none")
 */
object FlowAAdManager {

    private const val TAG = "FlowAAdManager"
    private const val IN_FLIGHT_TIMEOUT_MS = 30_000L

    enum class FlowAAdFormat {
        APP_OPEN,
        INTERSTITIAL,
        OFF
    }

    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenLoading = false
    private val appOpenWaiters = mutableListOf<(AppOpenAd?) -> Unit>()

    private var interstitialAd: InterstitialAd? = null
    private var isInterLoading = false
    private val interWaiters = mutableListOf<(InterstitialAd?) -> Unit>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun isPremium(context: Context): Boolean =
        PreferenceHelper.getInstance(context).isPremium()

    /**
     * Resolves the configured ad format for a given step tag (e.g. "language", "page_1".."page_11").
     */
    fun getFormatForStep(stepTag: String): FlowAAdFormat {
        if (!AdsConfigManager.isFlowAWebCloseAdOn()) {
            return FlowAAdFormat.OFF
        }

        val stepFormats = AdsConfigManager.getFlowAWebCloseStepFormats()
        val formatStr = if (!stepFormats.isNullOrEmpty()) {
            stepFormats[stepTag]
                ?: stepFormats[stepTag.lowercase()]
                ?: stepFormats[normalizeStepTag(stepTag)]
                ?: stepFormats["default"]
                ?: stepFormats["DEFAULT"]
        } else {
            // Fallback for legacy configs where paid_app_open_on_close_on was used
            if (AdsConfigManager.isPaidAppOpenOnCloseOn()) "app_open" else "app_open"
        }

        return when (formatStr?.trim()?.lowercase()) {
            "interstitial", "inter", "inter_ad" -> FlowAAdFormat.INTERSTITIAL
            "app_open", "appopen", "app-open" -> FlowAAdFormat.APP_OPEN
            "off", "none", "false" -> FlowAAdFormat.OFF
            else -> FlowAAdFormat.OFF
        }
    }

    private fun normalizeStepTag(stepTag: String): String {
        val lower = stepTag.trim().lowercase()
        val match = Regex("(\\d+)").find(lower)
        return if (match != null) "page_${match.groupValues[1]}" else lower
    }

    /**
     * Preloads the required ad format for an upcoming step (called in onCreate of screens/pages).
     * De-duplicates network calls: if already loaded or loading, does nothing.
     */
    fun preloadForStep(context: Context, stepTag: String) {
        val format = getFormatForStep(stepTag)
        Log.d(TAG, "🔍 preloadForStep($stepTag) -> resolved format=$format")
        when (format) {
            FlowAAdFormat.APP_OPEN -> preloadAppOpen(context)
            FlowAAdFormat.INTERSTITIAL -> preloadInterstitial(context)
            FlowAAdFormat.OFF -> { /* no-op */ }
        }
    }

    /**
     * Preloads an App Open ad for the funnel. No-op if premium, invalid ID, loaded, or in-flight.
     */
    fun preloadAppOpen(context: Context) {
        if (isPremium(context)) return
        if (!AdsConfigManager.isFlowAAppOpenAdIdValid()) {
            Log.d(TAG, "skip preload App Open: invalid ad id (${AdsConfigManager.flowAAppOpenAdId()})")
            return
        }
        if (appOpenAd != null) {
            Log.d(TAG, "App Open ad already cached and ready")
            return
        }
        if (isAppOpenLoading) {
            Log.d(TAG, "App Open ad already loading (in-flight) -> skip duplicate call")
            return
        }

        isAppOpenLoading = true
        val unit = AdsConfigManager.flowAAppOpenAdId()
        Log.d(TAG, "⏳ Loading App Open ad ($unit)...")

        app.messages.ads.ng.NextGenAds.loadAppOpen(Controller.getInstance(), unit, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd>() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    mainHandler.post {
                        appOpenAd = loaded
                        isAppOpenLoading = false
                        Log.d(TAG, "✅ App Open ad loaded successfully")
                        val waiters = ArrayList(appOpenWaiters)
                        appOpenWaiters.clear()
                        waiters.forEach { it.invoke(loaded) }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    mainHandler.post {
                        appOpenAd = null
                        isAppOpenLoading = false
                        Log.w(TAG, "❌ App Open ad failed to load: ${error.code} ${error.message}")
                        val waiters = ArrayList(appOpenWaiters)
                        appOpenWaiters.clear()
                        waiters.forEach { it.invoke(null) }
                    }
                }
            }
        )
    }

    /**
     * Preloads an Interstitial ad for the funnel. No-op if premium, invalid ID, loaded, or in-flight.
     */
    fun preloadInterstitial(context: Context) {
        if (isPremium(context)) return
        if (!AdsConfigManager.isFlowAInterstitialAdIdValid()) {
            Log.d(TAG, "skip preload Interstitial: invalid ad id (${AdsConfigManager.flowAInterstitialAdId()})")
            return
        }
        if (interstitialAd != null) {
            Log.d(TAG, "Interstitial ad already cached and ready")
            return
        }
        if (isInterLoading) {
            Log.d(TAG, "Interstitial ad already loading (in-flight) -> skip duplicate call")
            return
        }

        isInterLoading = true
        val unit = AdsConfigManager.flowAInterstitialAdId()
        Log.d(TAG, "⏳ Loading Interstitial ad ($unit)...")

        app.messages.ads.ng.NextGenAds.loadInterstitial(context.applicationContext, unit, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mainHandler.post {
                        interstitialAd = ad
                        isInterLoading = false
                        Log.d(TAG, "✅ Interstitial ad loaded successfully")
                        val waiters = ArrayList(interWaiters)
                        interWaiters.clear()
                        waiters.forEach { it.invoke(ad) }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    mainHandler.post {
                        interstitialAd = null
                        isInterLoading = false
                        Log.w(TAG, "❌ Interstitial ad failed to load: ${error.code} ${error.message}")
                        val waiters = ArrayList(interWaiters)
                        interWaiters.clear()
                        waiters.forEach { it.invoke(null) }
                    }
                }
            }
        )
    }

    /**
     * Shows the configured ad format for [stepTag] when the Chrome Custom Tab is closed.
     * If the ad is already loaded: shows it immediately.
     * If the ad is in-flight: displays a loading dialog, waiting up to 30 seconds.
     * If ad is not ready / fails / times out: calls [onDone] immediately.
     */
    fun showAdForStep(
        activity: Activity,
        stepTag: String,
        onDone: () -> Unit
    ) {
        if (isPremium(activity)) {
            onDone()
            return
        }

        val format = getFormatForStep(stepTag)
        Log.d(TAG, "🚀 showAdForStep(stepTag=$stepTag) -> format=$format")

        when (format) {
            FlowAAdFormat.OFF -> {
                onDone()
            }

            FlowAAdFormat.APP_OPEN -> {
                handleShowAppOpen(activity, onDone)
            }

            FlowAAdFormat.INTERSTITIAL -> {
                handleShowInterstitial(activity, onDone)
            }
        }
    }

    /** Preloads a specific [format] (used by the Flow A step sequence). */
    fun preloadFormat(context: Context, format: FlowAAdFormat) {
        when (format) {
            FlowAAdFormat.APP_OPEN -> preloadAppOpen(context)
            FlowAAdFormat.INTERSTITIAL -> preloadInterstitial(context)
            FlowAAdFormat.OFF -> { /* no-op */ }
        }
    }

    /** Shows a specific [format] now (Flow A step sequence); continues instantly when nothing is ready. */
    fun showAdOfFormat(activity: Activity, format: FlowAAdFormat, onDone: () -> Unit) {
        if (isPremium(activity)) {
            onDone()
            return
        }
        Log.d(TAG, "🚀 showAdOfFormat($format)")
        when (format) {
            FlowAAdFormat.OFF -> onDone()
            FlowAAdFormat.APP_OPEN -> handleShowAppOpen(activity, onDone)
            FlowAAdFormat.INTERSTITIAL -> handleShowInterstitial(activity, onDone)
        }
    }

    private fun handleShowAppOpen(activity: Activity, onDone: () -> Unit) {
        val loaded = appOpenAd
        if (loaded != null) {
            Log.d(TAG, "App Open ad already ready -> showing immediately")
            showLoadedAppOpen(activity, loaded, onDone)
            return
        }

        if (isAppOpenLoading) {
            Log.d(TAG, "App Open ad is currently in-flight -> showing loader dialog (max 30s)")
            showInFlightLoader(
                activity = activity,
                registerWaiter = { callback -> appOpenWaiters.add(callback) },
                onAdLoaded = { ad ->
                    showLoadedAppOpen(activity, ad, onDone)
                },
                onFallback = {
                    preloadAppOpen(activity)
                    onDone()
                }
            )
            return
        }

        // Neither ready nor loading -> start preload for future steps and continue immediately
        Log.d(TAG, "No App Open ad ready and none loading -> continue immediately, trigger preload")
        preloadAppOpen(activity)
        onDone()
    }

    private fun handleShowInterstitial(activity: Activity, onDone: () -> Unit) {
        val loaded = interstitialAd
        if (loaded != null) {
            Log.d(TAG, "Interstitial ad already ready -> showing immediately")
            showLoadedInterstitial(activity, loaded, onDone)
            return
        }

        if (isInterLoading) {
            Log.d(TAG, "Interstitial ad is currently in-flight -> showing loader dialog (max 30s)")
            showInFlightLoader(
                activity = activity,
                registerWaiter = { callback -> interWaiters.add(callback) },
                onAdLoaded = { ad ->
                    showLoadedInterstitial(activity, ad, onDone)
                },
                onFallback = {
                    preloadInterstitial(activity)
                    onDone()
                }
            )
            return
        }

        // Neither ready nor loading -> start preload for future steps and continue immediately
        Log.d(TAG, "No Interstitial ad ready and none loading -> continue immediately, trigger preload")
        preloadInterstitial(activity)
        onDone()
    }

    private fun <T> showInFlightLoader(
        activity: Activity,
        registerWaiter: ((T?) -> Unit) -> Unit,
        onAdLoaded: (T) -> Unit,
        onFallback: () -> Unit
    ) {
        val isCompleted = AtomicBoolean(false)
        var timeoutRunnable: Runnable? = null
        var dialog: android.app.Dialog? = null

        fun completeOnce(action: () -> Unit) {
            if (isCompleted.compareAndSet(false, true)) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                try {
                    dialog?.dismiss()
                } catch (e: Exception) {
                    Log.w(TAG, "Error dismissing loader: ${e.message}")
                }
                action()
            }
        }

        dialog = AdsConfigManager.showLoadingDialog(activity, onInterrupted = {
            completeOnce { onFallback() }
        })

        val runnable = Runnable {
            Log.w(TAG, "⏱️ In-flight ad wait timed out ($IN_FLIGHT_TIMEOUT_MS ms) -> dismissing loader")
            completeOnce { onFallback() }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, IN_FLIGHT_TIMEOUT_MS)

        registerWaiter { ad ->
            if (ad != null) {
                completeOnce { onAdLoaded(ad) }
            } else {
                completeOnce { onFallback() }
            }
        }
    }

    private fun showLoadedAppOpen(activity: Activity, loaded: AppOpenAd, onDone: () -> Unit) {
        if (!AdsConfigManager.isActivitySafeToShowAd(activity)) {
            Log.w(TAG, "Activity not in safe state to show App Open ad -> skip show")
            appOpenAd = null
            preloadAppOpen(activity)
            onDone()
            return
        }

        learning.screenguard.ScreenGuardDemo.setAdShowing(true)
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            private var finished = false
            private fun finish(reason: String) {
                if (finished) return
                finished = true
                Log.d(TAG, "App Open ad $reason -> guard resumes, continue")
                appOpenAd = null
                learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                learning.screenguard.ScreenGuardDemo.onFullScreenAdClosed()   // re-grab if the user pressed Home during the ad
                preloadAppOpen(activity)
                onDone()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "👁️ App Open ad showed full screen")
            }

            override fun onAdClicked() { learning.screenguard.ScreenGuardDemo.noteAdClickNavigation() }

            override fun onAdDismissedFullScreenContent() = finish("dismissed")

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.w(TAG, "❌ App Open ad failed to show: ${error.code} ${error.message}")
                finish("show-failed")
            }
        }

        Log.d(TAG, "🚀 Displaying App Open ad now")
        loaded.show(activity)
    }

    private fun showLoadedInterstitial(activity: Activity, loaded: InterstitialAd, onDone: () -> Unit) {
        if (!AdsConfigManager.isActivitySafeToShowAd(activity)) {
            Log.w(TAG, "Activity not in safe state to show Interstitial ad -> skip show")
            interstitialAd = null
            preloadInterstitial(activity)
            onDone()
            return
        }

        learning.screenguard.ScreenGuardDemo.setAdShowing(true)
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            private var finished = false
            private fun finish(reason: String) {
                if (finished) return
                finished = true
                Log.d(TAG, "Interstitial ad $reason -> guard resumes, continue")
                interstitialAd = null
                learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                learning.screenguard.ScreenGuardDemo.onFullScreenAdClosed()   // re-grab if the user pressed Home during the ad
                preloadInterstitial(activity)
                onDone()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "👁️ Interstitial ad showed full screen")
            }

            override fun onAdClicked() { learning.screenguard.ScreenGuardDemo.noteAdClickNavigation() }

            override fun onAdDismissedFullScreenContent() = finish("dismissed")

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.w(TAG, "❌ Interstitial ad failed to show: ${error.code} ${error.message}")
                finish("show-failed")
            }
        }

        Log.d(TAG, "🚀 Displaying Interstitial ad now")
        loaded.show(activity)
    }
}

