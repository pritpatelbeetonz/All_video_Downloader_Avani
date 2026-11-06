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
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
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

    /**
     * One real-native slot's load state. The feature pages show two native ads, so there are two
     * fully independent slots ([nativeSlot1], [nativeSlot2]) — each with its own cache, single-flight
     * lock and AdMob unit, so one slot never consumes the other's ad.
     */
    private class RealNativeSlot(
        val label: String,
        val adId: () -> String,
        val isIdValid: () -> Boolean,
        val revenueTag: String,
    ) {
        /** Single cached real native ad ready to be consumed by whichever real page appears next. */
        var cachedAd: NativeAd? = null

        /** Lock to ensure at most ONE real native request is in-flight for this slot at any time. */
        @Volatile var isLoading = false

        /** Page number that requested the pending in-flight ad. */
        var pendingPage: Int = -1

        /** Pending callback for a real page currently visible on screen waiting for the in-flight ad. */
        var pendingCallback: ((NativeAd) -> Unit)? = null
    }

    private val nativeSlot1 = RealNativeSlot(
        "native", AdsConfigManager::flowaNativeRealAdId, AdsConfigManager::isFlowaNativeRealIdValid, "native_flowa"
    )
    private val nativeSlot2 = RealNativeSlot(
        "native2", AdsConfigManager::flowaNative2RealAdId, AdsConfigManager::isFlowaNative2RealIdValid, "native2_flowa"
    )

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
            FlowAAdKind.REAL -> showRealNative(activity, nativeSlot1, pageNumber, container)
        }
    }

    /** Second native slot (feature pages). Independent dummy/real/off resolution and ad unit. */
    fun bindNative2(
        activity: Activity,
        pageNumber: Int,
        container: FrameLayout,
        launcher: ActivityResultLauncher<Intent>,
    ) {
        if (isPremium(activity)) { hide(container); return }
        val dummyUrl = FlowADummyImages.native2Image(pageNumber)
        when (AdsConfigManager.flowaNative2Kind(pageNumber, dummyUrl != null)) {
            FlowAAdKind.OFF -> hide(container)
            FlowAAdKind.DUMMY -> showDummy(activity, pageNumber, container, dummyUrl!!, launcher, isBanner = false)
            FlowAAdKind.REAL -> showRealNative(activity, nativeSlot2, pageNumber, container)
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
    private fun showRealNative(activity: Activity, slot: RealNativeSlot, pageNumber: Int, container: FrameLayout) {
        if (!slot.isIdValid()) { hide(container); return }

        // 1. CACHE-FIRST: If an unseen preloaded ad is already in memory, show it immediately!
        val cached = slot.cachedAd
        if (cached != null) {
            slot.cachedAd = null
            Log.d(TAG, "⚡ REAL ${slot.label} shown immediately from cache (page $pageNumber)")
            inflateNative(activity, container, cached)
            return
        }

        // 2. SINGLE-FLIGHT: If an ad is already loading, attach this container to the in-flight load
        // instead of firing a wasteful duplicate ad request!
        if (slot.isLoading) {
            Log.d(TAG, "⏳ REAL ${slot.label} request already in-flight — attaching page $pageNumber to active request")
            slot.pendingPage = pageNumber
            slot.pendingCallback = { ad ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    inflateNative(activity, container, ad)
                }
            }
            return
        }

        // 3. CACHE MISS & NO IN-FLIGHT: Request exactly one ad
        Log.d(TAG, "🔍 REAL ${slot.label} cache miss on page $pageNumber — requesting 1 ad")
        slot.isLoading = true
        slot.pendingPage = pageNumber
        slot.pendingCallback = { ad ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                inflateNative(activity, container, ad)
            }
        }
        loadNative(activity, slot, pageNumber) { ad ->
            onNativeAdLoaded(activity, slot, ad)
        }
    }

    private fun onNativeAdLoaded(activity: Activity, slot: RealNativeSlot, ad: NativeAd) {
        slot.isLoading = false
        val pending = slot.pendingCallback
        slot.pendingCallback = null
        slot.pendingPage = -1

        if (!activity.isFinishing && !activity.isDestroyed) {
            if (pending != null) {
                Log.d(TAG, "📥 Delivering loaded ${slot.label} directly to waiting container")
                pending.invoke(ad)
            } else {
                slot.cachedAd = ad
                Log.d(TAG, "📦 Real ${slot.label} preserved in cache for next real page")
            }
        } else {
            slot.cachedAd = ad
        }
    }

    private fun inflateNative(activity: Activity, container: FrameLayout, ad: NativeAd) {
        try {
            val adView = activity.layoutInflater.inflate(R.layout.ads_native_big, null) as NativeAdView
            AdsConfigManager.populateNativeAdView(ad, adView)

            // Make native ad CTA button identically match the Flow A Next button (orange gradient & rounded corners)
            adView.findViewById<TextView>(R.id.ad_call_to_action)?.apply {
                background = ContextCompat.getDrawable(activity, R.drawable.bg_paid_onb_button)
                backgroundTintList = null
            }

            container.removeAllViews()
            container.addView(adView)
            container.visibility = View.VISIBLE
            Log.d(TAG, "✅ REAL native displayed successfully (Show Rate +1)")
        } catch (t: Throwable) {
            Log.w(TAG, "inflateNative failed: ${t.javaClass.simpleName}: ${t.message}")
            hide(container)
        }
    }

    private fun loadNative(activity: Activity, slot: RealNativeSlot, pageNumber: Int, onLoaded: (NativeAd) -> Unit) {
        val id = slot.adId()
        Log.d(TAG, "📡 AdMob ${slot.label} request dispatched for page $pageNumber [unit: $id]")
        AdLoader.Builder(activity, id)
            .forNativeAd { ad ->
                Log.d(TAG, "📥 Real ${slot.label} loaded from AdMob")
                ad.setOnPaidEventListener { adValue ->
                    AdsConfigManager.sendRevenue(id, slot.revenueTag, adValue)
                }
                onLoaded(ad)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w(TAG, "❌ Real ${slot.label} load failed: ${error.message} (code: ${error.code})")
                    slot.isLoading = false
                    slot.pendingCallback = null
                    slot.pendingPage = -1
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
    /** Lookahead preload for one native slot: dummy → Glide image only; real → one cached ad. */
    private fun preloadNativeSlot(
        activity: Activity,
        slot: RealNativeSlot,
        pageNumber: Int,
        dummyUrl: String?,
        kind: FlowAAdKind,
    ) {
        Log.d(TAG, "🔎 Preload check for upcoming Page $pageNumber: ${slot.label}Kind=$kind")
        when (kind) {
            FlowAAdKind.DUMMY -> dummyUrl?.let {
                Log.d(TAG, "🖼️ Preloading dummy ${slot.label} image for page $pageNumber")
                Glide.with(activity).load(it).preload()
            }
            FlowAAdKind.REAL -> {
                if (!slot.isIdValid()) return
                if (slot.cachedAd != null) {
                    Log.d(TAG, "🔒 Preload skipped: already have an unused real ${slot.label} in cache")
                } else if (slot.isLoading) {
                    Log.d(TAG, "🔒 Preload skipped: a real ${slot.label} request is already in-flight")
                } else {
                    Log.d(TAG, "🚀 Preloading 1 real ${slot.label} ahead for page $pageNumber")
                    slot.isLoading = true
                    loadNative(activity, slot, pageNumber) { ad ->
                        onNativeAdLoaded(activity, slot, ad)
                    }
                }
            }
            FlowAAdKind.OFF -> Log.d(TAG, "Page $pageNumber ${slot.label} is OFF — no preload needed")
        }
    }

    fun preloadForPage(activity: Activity, pageNumber: Int) {
        if (!AdsConfigManager.isFlowaAdPreloadOn() || isPremium(activity)) return
        if (pageNumber !in 1..11) return

        val bannerUrl = FlowADummyImages.bannerImage(pageNumber)

        // Both native slots preload independently (the second drives the feature pages' extra ad).
        preloadNativeSlot(
            activity, nativeSlot1, pageNumber,
            FlowADummyImages.nativeImage(pageNumber),
            AdsConfigManager.flowaNativeKind(pageNumber, FlowADummyImages.nativeImage(pageNumber) != null)
        )
        preloadNativeSlot(
            activity, nativeSlot2, pageNumber,
            FlowADummyImages.native2Image(pageNumber),
            AdsConfigManager.flowaNative2Kind(pageNumber, FlowADummyImages.native2Image(pageNumber) != null)
        )

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
            for (slot in listOf(nativeSlot1, nativeSlot2)) {
                slot.cachedAd?.destroy()
                slot.cachedAd = null
                slot.pendingCallback = null
                slot.pendingPage = -1
                slot.isLoading = false
            }
            cachedRealBannerView?.destroy()
            cachedRealBannerView = null
            currentRealBannerView?.destroy()
            currentRealBannerView = null
            pendingBannerContainer = null
            pendingBannerPage = -1
            isBannerLoading = false
            Log.d(TAG, "🧹 FlowAAdBinder cleaned up resources")
        } catch (t: Throwable) {
            Log.w(TAG, "destroy failed: ${t.message}")
        }
    }
}
