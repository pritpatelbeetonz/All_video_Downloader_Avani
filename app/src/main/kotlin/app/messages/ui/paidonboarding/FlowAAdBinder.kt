package app.messages.ui.paidonboarding

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
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

    /** Real native pool keyed by 1-based page number (preloaded ahead of the page). */
    private val nativeCache = HashMap<Int, NativeAd>()

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
            FlowAAdKind.OFF -> hide(container)
            FlowAAdKind.DUMMY -> showDummy(activity, pageNumber, container, dummyUrl!!, launcher, isBanner = true)
            FlowAAdKind.REAL -> showRealBanner(activity, container)
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
        val cached = nativeCache.remove(pageNumber)
        if (cached != null) { inflateNative(activity, container, cached); return }
        loadNative(activity, pageNumber) { ad ->
            if (!activity.isFinishing && !activity.isDestroyed) inflateNative(activity, container, ad)
        }
    }

    private fun inflateNative(activity: Activity, container: FrameLayout, ad: NativeAd) {
        try {
            val adView = activity.layoutInflater.inflate(R.layout.ads_native_big, null) as NativeAdView
            AdsConfigManager.populateNativeAdView(ad, adView)
            container.removeAllViews()
            container.addView(adView)
            container.visibility = View.VISIBLE
            Log.d(TAG, "REAL native shown")
        } catch (t: Throwable) {
            Log.w(TAG, "inflateNative failed: ${t.javaClass.simpleName}: ${t.message}")
            hide(container)
        }
    }

    private fun loadNative(activity: Activity, pageNumber: Int, onLoaded: (NativeAd) -> Unit) {
        val id = AdsConfigManager.flowaNativeRealAdId()
        AdLoader.Builder(activity, id)
            .forNativeAd { ad -> Log.d(TAG, "real native loaded (page $pageNumber)"); onLoaded(ad) }
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    // ---- REAL banner -------------------------------------------------------------------------
    private fun showRealBanner(activity: Activity, container: FrameLayout) {
        if (!AdsConfigManager.isFlowaBannerRealIdValid()) { hide(container); return }
        try {
            val adView = AdView(activity).apply {
                adUnitId = AdsConfigManager.flowaBannerRealAdId()
                setAdSize(AdSize.BANNER)
            }
            container.removeAllViews()
            container.addView(adView)
            container.visibility = View.VISIBLE
            adView.loadAd(AdRequest.Builder().build())
            Log.d(TAG, "REAL banner requested")
        } catch (t: Throwable) {
            Log.w(TAG, "showRealBanner failed: ${t.javaClass.simpleName}: ${t.message}")
            hide(container)
        }
    }

    // ---- Preload (rolling: one page ahead, never all at once) --------------------------------
    /**
     * Warm ONLY the given 1-based [pageNumber]'s two slots (native + banner), whatever Remote Config
     * set them to — both, one, or none. Called one page ahead: from the Language screen for page 1,
     * then from each Flow-A page for the next page. So at most one page's ads are fetched at a time,
     * not all 11 together. Gated by the Remote Config flag `flowa_ad_preload_on` (+ premium = off).
     *
     * Preloads: dummy images (Glide cache) for whichever slot is DUMMY, and the real NativeAd into
     * [nativeCache] when a slot is REAL. The real banner is not pooled — AdView is context-bound and
     * a banner loads fast live when its page shows, so it is requested on show, not ahead.
     */
    fun preloadForPage(activity: Activity, pageNumber: Int) {
        if (!AdsConfigManager.isFlowaAdPreloadOn() || isPremium(activity)) return
        if (pageNumber !in 1..11) return

        val nativeUrl = FlowADummyImages.nativeImage(pageNumber)
        val bannerUrl = FlowADummyImages.bannerImage(pageNumber)

        when (AdsConfigManager.flowaNativeKind(pageNumber, nativeUrl != null)) {
            FlowAAdKind.DUMMY -> nativeUrl?.let { Glide.with(activity).load(it).preload() }
            FlowAAdKind.REAL ->
                if (!nativeCache.containsKey(pageNumber)) {
                    loadNative(activity, pageNumber) { ad -> nativeCache[pageNumber] = ad }
                }
            FlowAAdKind.OFF -> { /* nothing to warm */ }
        }

        when (AdsConfigManager.flowaBannerKind(pageNumber, bannerUrl != null)) {
            FlowAAdKind.DUMMY -> bannerUrl?.let { Glide.with(activity).load(it).preload() }
            // REAL banner is requested live on show (fast, context-bound) — not pooled here.
            else -> { /* nothing to warm ahead */ }
        }
        Log.d(TAG, "preload page $pageNumber (one ahead)")
    }
}
