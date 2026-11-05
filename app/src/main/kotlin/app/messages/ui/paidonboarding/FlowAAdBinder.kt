package app.messages.ui.paidonboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowMetrics
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import app.messages.R
import app.messages.config.AdsConfigManager
import app.messages.config.AdsConfigManager.FlowAAdKind
import app.messages.preferences.PreferenceHelper
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Renders the two Flow-A ad slots (native per page, banner shared on the activity). Each slot is
 * DUMMY (a server image, shown as-is; click → web/Custom Tab → same page), REAL (AdMob), or OFF —
 * resolved by [AdsConfigManager.flowaNativeKind]/[flowaBannerKind]. Dummy images come from
 * [FlowADummyImages]; real native/banner use the configured funnel units.
 *
 * Logcat: tag:FlowAAds
 */
object FlowAAdBinder {

    private const val TAG = "FlowAAds"

    /** Currently visible page number (1-based). */
    @Volatile
    private var currentPage: Int = 1

    /** Single cached real native ad ready to be consumed by whichever real page appears next. */
    private var cachedNativeAd: NativeAd? = null

    /** Lock to ensure at most ONE real native ad request is in-flight at any time (protects show-rate). */
    @Volatile
    private var isNativeLoading = false

    /** Page number that requested the pending in-flight ad. */
    private var pendingPage: Int = -1

    /** Pending callback for a real page currently visible on screen waiting for the in-flight ad. */
    private var pendingNativeCallback: ((NativeAd) -> Unit)? = null

    /** Currently active real AdView attached to the shared activity container. */
    private var currentRealBannerView: AdView? = null

    /** Preloaded real AdView waiting to be attached to the activity banner container. */
    private var cachedRealBannerView: AdView? = null

    /** Lock to prevent duplicate in-flight banner requests. */
    @Volatile
    private var isBannerLoading = false

    /** Page number that requested the pending in-flight banner. */
    private var pendingBannerPage: Int = -1

    /** Pending container waiting for the in-flight banner to finish loading. */
    private var pendingBannerContainer: FrameLayout? = null

    /** Notify the binder of the currently selected onboarding page. */
    fun onPageSelected(pageNumber: Int) {
        currentPage = pageNumber
    }

    private fun isPremium(activity: Activity) = PreferenceHelper.getInstance(activity).isPremium()

    private fun hide(container: FrameLayout) {
        container.removeAllViews()
        container.visibility = View.GONE
    }

    // ---- NATIVE (per page) -------------------------------------------------------------------
    fun bindNative(
        activity: Activity,
        pageNumber: Int,
        container: FrameLayout,
        launcher: ActivityResultLauncher<Intent>,
    ) {
        if (isPremium(activity)) { hide(container); return }
        val dummyUrl = FlowADummyImages.nativeImage(pageNumber)
        when (AdsConfigManager.flowaNativeKind(pageNumber, dummyUrl != null)) {
            FlowAAdKind.OFF -> hide(container)
            FlowAAdKind.DUMMY -> showDummy(activity, pageNumber, container, dummyUrl!!, launcher, isBanner = false)
            FlowAAdKind.REAL -> showRealNative(activity, pageNumber, container)
        }
    }

    // ---- BANNER (shared container, re-bound per page) ----------------------------------------
    fun bindBanner(
        activity: Activity,
        pageNumber: Int,
        container: FrameLayout,
        launcher: ActivityResultLauncher<Intent>,
    ) {
        if (isPremium(activity)) { hide(container); return }
        val dummyUrl = FlowADummyImages.bannerImage(pageNumber)
        when (AdsConfigManager.flowaBannerKind(pageNumber, dummyUrl != null)) {
            FlowAAdKind.OFF -> {
                hide(container)
                currentRealBannerView?.destroy()
                currentRealBannerView = null
            }
            FlowAAdKind.DUMMY -> {
                currentRealBannerView?.destroy()
                currentRealBannerView = null
                showDummy(activity, pageNumber, container, dummyUrl!!, launcher, isBanner = true)
            }
            FlowAAdKind.REAL -> showRealBanner(activity, container, pageNumber)
        }
    }

    // ---- DUMMY -------------------------------------------------------------------------------
    private fun showDummy(
        activity: Activity,
        pageNumber: Int,
        container: FrameLayout,
        url: String,
        launcher: ActivityResultLauncher<Intent>,
        isBanner: Boolean,
    ) {
        container.removeAllViews()
        val iv = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true   // scale to full width, height from the image ratio
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
            setOnClickListener { openDummyWeb(activity, launcher, if (isBanner) "banner_$pageNumber" else "native_$pageNumber") }
        }
        container.addView(iv)
        container.visibility = View.VISIBLE
        Glide.with(activity).load(url).into(iv)
        Log.d(TAG, "DUMMY ${if (isBanner) "banner" else "native"} page=$pageNumber url=$url")
    }

    /** Dummy click → open the existing web step (chrome_tab_url + decided surface); close returns to the same page. */
    private fun openDummyWeb(activity: Activity, launcher: ActivityResultLauncher<Intent>, tag: String) {
        Log.d(TAG, "dummy click ($tag) → web step")
        PaidChromeTabManager.launch(activity = activity, launcher = launcher, stepTag = "dummy_$tag") {
            // onFallback (URL blank / launch failed): nothing to do — stay on the same page.
        }
    }

    // ---- REAL native -------------------------------------------------------------------------
    private fun showRealNative(activity: Activity, pageNumber: Int, container: FrameLayout) {
        if (!AdsConfigManager.isFlowaNativeRealIdValid()) { hide(container); return }

        // 1. CACHE-FIRST: If an unseen preloaded ad is already in memory, show it immediately!
        val cached = cachedNativeAd
        if (cached != null) {
            cachedNativeAd = null
            Log.d(TAG, "⚡ REAL native shown immediately from cache (page $pageNumber)")
            inflateNative(activity, container, cached)
            return
        }

        // 2. SINGLE-FLIGHT: If an ad is already loading, attach this container to the in-flight load
        // instead of firing a wasteful duplicate ad request!
        if (isNativeLoading) {
            Log.d(TAG, "⏳ REAL native request already in-flight — attaching page $pageNumber to active request")
            pendingPage = pageNumber
            pendingNativeCallback = { ad ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    inflateNative(activity, container, ad)
                }
            }
            return
        }

        // 3. CACHE MISS & NO IN-FLIGHT: Request exactly one ad
        Log.d(TAG, "🔍 REAL native cache miss on page $pageNumber — requesting 1 ad")
        isNativeLoading = true
        pendingPage = pageNumber
        pendingNativeCallback = { ad ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                inflateNative(activity, container, ad)
            }
        }
        loadNative(activity, pageNumber) { ad ->
            onNativeAdLoaded(activity, ad)
        }
    }

    private fun onNativeAdLoaded(activity: Activity, ad: NativeAd) {
        isNativeLoading = false
        val pending = pendingNativeCallback
        pendingNativeCallback = null
        pendingPage = -1

        if (!activity.isFinishing && !activity.isDestroyed) {
            if (pending != null) {
                Log.d(TAG, "📥 Delivering loaded NativeAd directly to waiting container")
                pending.invoke(ad)
            } else {
                cachedNativeAd = ad
                Log.d(TAG, "📦 Real NativeAd preserved in cache for next real page")
            }
        } else {
            cachedNativeAd = ad
        }
    }

    private fun inflateNative(activity: Activity, container: FrameLayout, ad: NativeAd) {
        try {
            val adView = activity.layoutInflater.inflate(R.layout.ads_native_big, null) as NativeAdView
            AdsConfigManager.populateNativeAdView(ad, adView)
            container.removeAllViews()
            container.addView(adView)
            container.visibility = View.VISIBLE
            Log.d(TAG, "✅ REAL native displayed successfully (Show Rate +1)")
        } catch (t: Throwable) {
            Log.w(TAG, "inflateNative failed: ${t.javaClass.simpleName}: ${t.message}")
            hide(container)
        }
    }

    private fun loadNative(activity: Activity, pageNumber: Int, onLoaded: (NativeAd) -> Unit) {
        val id = AdsConfigManager.flowaNativeRealAdId()
        Log.d(TAG, "📡 AdMob NativeAd request dispatched for page $pageNumber [unit: $id]")
        AdLoader.Builder(activity, id)
            .forNativeAd { ad ->
                Log.d(TAG, "📥 Real native loaded from AdMob")
                ad.setOnPaidEventListener { adValue ->
                    AdsConfigManager.sendRevenue(id, "native_flowa", adValue)
                }
                onLoaded(ad)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w(TAG, "❌ Real native load failed: ${error.message} (code: ${error.code})")
                    isNativeLoading = false
                    pendingNativeCallback = null
                    pendingPage = -1
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    // ---- REAL banner -------------------------------------------------------------------------
    private fun showRealBanner(activity: Activity, container: FrameLayout, pageNumber: Int) {
        if (!AdsConfigManager.isFlowaBannerRealIdValid()) { hide(container); return }

        // 1. If the real banner is already attached and showing in this container, reuse it!
        val existing = currentRealBannerView
        if (existing != null && container.indexOfChild(existing) >= 0) {
            Log.d(TAG, "♻️ REAL banner reused existing AdView (page $pageNumber)")
            container.visibility = View.VISIBLE
            return
        }

        // 2. CACHE-FIRST: If preloaded banner is ready, attach it immediately!
        val cached = cachedRealBannerView
        if (cached != null) {
            cachedRealBannerView = null
            (cached.parent as? ViewGroup)?.removeView(cached)
            currentRealBannerView?.destroy()
            currentRealBannerView = cached
            container.removeAllViews()
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            container.addView(cached, lp)
            container.visibility = View.VISIBLE
            Log.d(TAG, "⚡ REAL banner shown immediately from preloaded cache (page $pageNumber)")
            return
        }

        // 3. SINGLE-FLIGHT: If a banner request is already in-flight, attach this container to it!
        if (isBannerLoading) {
            Log.d(TAG, "⏳ REAL banner request already in-flight — attaching page $pageNumber to active request")
            pendingBannerPage = pageNumber
            pendingBannerContainer = container
            return
        }

        // 4. CACHE MISS & NO IN-FLIGHT: Request a fresh banner
        Log.d(TAG, "🔍 REAL banner cache miss on page $pageNumber — requesting 1 banner")
        isBannerLoading = true
        pendingBannerPage = pageNumber
        pendingBannerContainer = container
        loadRealBanner(activity, pageNumber)
    }

    /** Calculates adaptive full-width banner size matching the device screen width. */
    private fun getAdaptiveBannerSize(activity: Activity): AdSize {
        val displayMetrics = activity.resources.displayMetrics
        val adWidthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            windowMetrics.bounds.width()
        } else {
            displayMetrics.widthPixels
        }
        val density = displayMetrics.density
        val adWidthDp = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp)
    }

    private fun loadRealBanner(activity: Activity, pageNumber: Int) {
        val id = AdsConfigManager.flowaBannerRealAdId()
        Log.d(TAG, "📡 AdMob BannerAd request dispatched for page $pageNumber [unit: $id]")
        val adView = AdView(activity).apply {
            adUnitId = id
            setAdSize(getAdaptiveBannerSize(activity))
            setOnPaidEventListener { adValue ->
                AdsConfigManager.sendRevenue(id, "banner_flowa", adValue)
            }
        }
        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                onBannerAdLoaded(activity, adView)
            }
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                Log.w(TAG, "❌ Real banner load failed: ${error.message} (code: ${error.code})")
                isBannerLoading = false
                pendingBannerPage = -1
                pendingBannerContainer = null
            }
        }
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun onBannerAdLoaded(activity: Activity, adView: AdView) {
        isBannerLoading = false
        val pendingCont = pendingBannerContainer
        pendingBannerPage = -1
        pendingBannerContainer = null

        if (!activity.isFinishing && !activity.isDestroyed) {
            if (pendingCont != null) {
                (adView.parent as? ViewGroup)?.removeView(adView)
                currentRealBannerView?.destroy()
                currentRealBannerView = adView
                pendingCont.removeAllViews()
                val lp = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER }
                pendingCont.addView(adView, lp)
                pendingCont.visibility = View.VISIBLE
                Log.d(TAG, "✅ REAL banner displayed successfully (Show Rate +1)")
            } else {
                cachedRealBannerView = adView
                Log.d(TAG, "📦 Real banner preserved in cache for next real page")
            }
        } else {
            cachedRealBannerView = adView
        }
    }

    // ---- Smart Lookahead Preload -------------------------------------------------------------
    /**
     * Smart lookahead preloading:
     * 1. Inspects the upcoming [pageNumber] configuration in Remote Config.
     * 2. If upcoming page is DUMMY: only preloads the dummy image with Glide. Zero AdMob requests.
     * 3. If upcoming page is REAL:
     *    - If an unused preloaded ad is already in cache -> skips request (reuses cached ad!).
     *    - If an ad request is already in-flight -> skips duplicate request!
     *    - Only dispatches a single real ad request when cache is empty and nothing is loading.
     */
    fun preloadForPage(activity: Activity, pageNumber: Int) {
        if (!AdsConfigManager.isFlowaAdPreloadOn() || isPremium(activity)) return
        if (pageNumber !in 1..11) return

        val nativeUrl = FlowADummyImages.nativeImage(pageNumber)
        val bannerUrl = FlowADummyImages.bannerImage(pageNumber)

        val nativeKind = AdsConfigManager.flowaNativeKind(pageNumber, nativeUrl != null)
        Log.d(TAG, "🔎 Preload check for upcoming Page $pageNumber: NativeKind=$nativeKind")

        when (nativeKind) {
            FlowAAdKind.DUMMY -> {
                // Preload ONLY the dummy image via Glide (zero AdMob network calls)
                nativeUrl?.let {
                    Log.d(TAG, "🖼️ Preloading dummy native image for page $pageNumber")
                    Glide.with(activity).load(it).preload()
                }
            }
            FlowAAdKind.REAL -> {
                if (cachedNativeAd != null) {
                    Log.d(TAG, "🔒 Preload skipped: already have an unused real NativeAd in cache")
                } else if (isNativeLoading) {
                    Log.d(TAG, "🔒 Preload skipped: a real native request is already in-flight")
                } else {
                    Log.d(TAG, "🚀 Preloading 1 real NativeAd ahead for page $pageNumber")
                    isNativeLoading = true
                    loadNative(activity, pageNumber) { ad ->
                        onNativeAdLoaded(activity, ad)
                    }
                }
            }
            FlowAAdKind.OFF -> {
                Log.d(TAG, "Page $pageNumber native is OFF — no preload needed")
            }
        }

        // Banner preload (Dummy image or Real AdMob banner)
        val bannerKind = AdsConfigManager.flowaBannerKind(pageNumber, bannerUrl != null)
        Log.d(TAG, "🔎 Preload check for upcoming Page $pageNumber: BannerKind=$bannerKind")

        when (bannerKind) {
            FlowAAdKind.DUMMY -> bannerUrl?.let {
                Log.d(TAG, "🖼️ Preloading dummy banner image for page $pageNumber")
                Glide.with(activity).load(it).preload()
            }
            FlowAAdKind.REAL -> {
                if (!AdsConfigManager.isFlowaBannerRealIdValid()) return
                if (currentRealBannerView != null) {
                    Log.d(TAG, "🔒 Real banner preload skipped: active banner view already exists")
                } else if (cachedRealBannerView != null) {
                    Log.d(TAG, "🔒 Real banner preload skipped: already have preloaded AdView in cache")
                } else if (isBannerLoading) {
                    Log.d(TAG, "🔒 Real banner preload skipped: banner request already in-flight")
                } else {
                    Log.d(TAG, "🚀 Preloading 1 real BannerAd ahead for page $pageNumber")
                    isBannerLoading = true
                    loadRealBanner(activity, pageNumber)
                }
            }
            FlowAAdKind.OFF -> {
                Log.d(TAG, "Page $pageNumber banner is OFF — no preload needed")
            }
        }
    }

    /** Clean up any unused cached ads and views when exiting onboarding. */
    fun destroy() {
        try {
            cachedNativeAd?.destroy()
            cachedNativeAd = null
            pendingNativeCallback = null
            pendingPage = -1
            cachedRealBannerView?.destroy()
            cachedRealBannerView = null
            currentRealBannerView?.destroy()
            currentRealBannerView = null
            pendingBannerContainer = null
            pendingBannerPage = -1
            isNativeLoading = false
            isBannerLoading = false
            Log.d(TAG, "🧹 FlowAAdBinder cleaned up resources")
        } catch (t: Throwable) {
            Log.w(TAG, "destroy failed: ${t.message}")
        }
    }
}
