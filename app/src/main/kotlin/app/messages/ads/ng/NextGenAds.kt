package app.messages.ads.ng

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import java.util.Collections
import java.util.WeakHashMap

/*
 * Google Mobile Ads Next-Gen SDK helpers.
 *
 * The Next-Gen SDK differs from the classic play-services-ads SDK in three ways that matter here:
 *  1. Loads take the ad unit id inside the request and need the SDK initialized first
 *     (every load below waits on [AdsSdkInit.whenReady]).
 *  2. Load and event callbacks arrive on a background thread; everything here is re-posted to
 *     the main thread because callers touch views.
 *  3. An ad has ONE event callback (paid + show/dismiss + impression + click) instead of a
 *     separate paid listener and FullScreenContentCallback. The extensions at the bottom keep the
 *     classic two-step shape (`setOnPaidEventListener` at load, `fullScreenContentCallback` at
 *     show) and merge both into that one callback.
 */

private val mainHandler = Handler(Looper.getMainLooper())

internal fun runMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
}

/** Classic-style load callback (onAdLoaded / onAdFailedToLoad), delivered on the main thread. */
abstract class NgLoadCallback<T> {
    open fun onAdLoaded(ad: T) {}
    open fun onAdFailedToLoad(error: LoadAdError) {}
}

/** Classic-style full-screen callback, delivered on the main thread. */
abstract class FullScreenContentCallback {
    open fun onAdShowedFullScreenContent() {}
    open fun onAdDismissedFullScreenContent() {}
    open fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {}
    open fun onAdImpression() {}
    open fun onAdClicked() {}
}

/** Classic-style ad listener used by natives and banners, delivered on the main thread. */
abstract class AdListener {
    open fun onAdLoaded() {}
    open fun onAdFailedToLoad(error: LoadAdError) {}
    open fun onAdImpression() {}
    open fun onAdClicked() {}
    open fun onAdOpened() {}
    open fun onAdClosed() {}
}

/** Classic `LoadAdError.code` was an Int; Next-Gen uses an enum carrying the same value. */
val LoadAdError.codeInt: Int get() = code.value

/** Classic `AdRequest.ERROR_CODE_NO_FILL`. */
const val ERROR_CODE_NO_FILL = 3

object NextGenAds {

    private fun <T> wrap(cb: NgLoadCallback<T>): AdLoadCallback<T> = object : AdLoadCallback<T> {
        override fun onAdLoaded(ad: T) { runMain { cb.onAdLoaded(ad) } }
        override fun onAdFailedToLoad(adError: LoadAdError) { runMain { cb.onAdFailedToLoad(adError) } }
    }

    @JvmStatic
    fun loadInterstitial(context: Context, adUnitId: String, callback: NgLoadCallback<InterstitialAd>) {
        AdsSdkInit.whenReady(context) {
            InterstitialAd.load(AdRequest.Builder(adUnitId).build(), wrap(callback))
        }
    }

    @JvmStatic
    fun loadAppOpen(context: Context, adUnitId: String, callback: NgLoadCallback<AppOpenAd>) {
        AdsSdkInit.whenReady(context) {
            AppOpenAd.load(AdRequest.Builder(adUnitId).build(), wrap(callback))
        }
    }

    /** Native request with video enabled (muted by default). */
    @JvmStatic
    @JvmOverloads
    fun loadNative(
        context: Context,
        adUnitId: String,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (LoadAdError) -> Unit = {},
        startMuted: Boolean = true,
        mediaAspectRatio: NativeAd.NativeMediaAspectRatio? = NativeAd.NativeMediaAspectRatio.ANY,
    ) {
        AdsSdkInit.whenReady(context) {
            val builder = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))
                .setVideoOptions(VideoOptions.Builder().setStartMuted(startMuted).build())
            mediaAspectRatio?.let { builder.setMediaAspectRatio(it) }
            NativeAdLoader.load(builder.build(), object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) { runMain { onLoaded(nativeAd) } }
                override fun onAdFailedToLoad(adError: LoadAdError) { runMain { onFailed(adError) } }
            })
        }
    }

    /**
     * Registers the native ad on its view. Next-Gen renders AdChoices into an [AdChoicesView] the
     * app must provide, so one is added at the top-end corner when the layout has none.
     */
    @JvmStatic
    fun bindNative(adView: NativeAdView, nativeAd: NativeAd, mediaView: MediaView?) {
        if (adView.adChoicesView == null) {
            val choices = AdChoicesView(adView.context)
            adView.addView(
                choices,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                )
            )
            adView.adChoicesView = choices
        }
        adView.registerNativeAd(nativeAd, mediaView)
    }
}

// ---------------------------------------------------------------------------------------------
// Merged event callbacks (classic two-step shape on top of the single Next-Gen callback)
// ---------------------------------------------------------------------------------------------

private class EventSlots {
    var paid: ((AdValue) -> Unit)? = null
    var fullScreen: FullScreenContentCallback? = null
    var listener: AdListener? = null
}

private val slots: MutableMap<Any, EventSlots> = Collections.synchronizedMap(WeakHashMap())
private fun slotsOf(ad: Any): EventSlots = synchronized(slots) { slots.getOrPut(ad) { EventSlots() } }

private fun dispatchPaid(s: EventSlots, v: AdValue) { s.paid?.let { p -> runMain { p(v) } } }

private fun fullScreenEvents(s: EventSlots) = object {
    fun showed() = runMain { s.fullScreen?.onAdShowedFullScreenContent() }
    fun dismissed() = runMain { s.fullScreen?.onAdDismissedFullScreenContent() }
    fun failed(e: FullScreenContentError) = runMain { s.fullScreen?.onAdFailedToShowFullScreenContent(e) }
    fun impression() = runMain { s.fullScreen?.onAdImpression() }
    fun clicked() = runMain { s.fullScreen?.onAdClicked() }
}

private fun InterstitialAd.install() {
    val s = slotsOf(this)
    val ev = fullScreenEvents(s)
    adEventCallback = object : InterstitialAdEventCallback {
        override fun onAdPaid(value: AdValue) = dispatchPaid(s, value)
        override fun onAdShowedFullScreenContent() = ev.showed()
        override fun onAdDismissedFullScreenContent() = ev.dismissed()
        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = ev.failed(fullScreenContentError)
        override fun onAdImpression() = ev.impression()
        override fun onAdClicked() = ev.clicked()
    }
}

private fun AppOpenAd.install() {
    val s = slotsOf(this)
    val ev = fullScreenEvents(s)
    adEventCallback = object : AppOpenAdEventCallback {
        override fun onAdPaid(value: AdValue) = dispatchPaid(s, value)
        override fun onAdShowedFullScreenContent() = ev.showed()
        override fun onAdDismissedFullScreenContent() = ev.dismissed()
        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = ev.failed(fullScreenContentError)
        override fun onAdImpression() = ev.impression()
        override fun onAdClicked() = ev.clicked()
    }
}

private fun NativeAd.install() {
    val s = slotsOf(this)
    adEventCallback = object : NativeAdEventCallback {
        override fun onAdPaid(value: AdValue) = dispatchPaid(s, value)
        override fun onAdImpression() = runMain { s.listener?.onAdImpression() }
        override fun onAdClicked() = runMain { s.listener?.onAdClicked() }
        override fun onAdShowedFullScreenContent() = runMain { s.listener?.onAdOpened() }
        override fun onAdDismissedFullScreenContent() = runMain { s.listener?.onAdClosed() }
    }
}

fun InterstitialAd.setOnPaidEventListener(listener: (AdValue) -> Unit) {
    slotsOf(this).paid = listener; install()
}

var InterstitialAd.fullScreenContentCallback: FullScreenContentCallback?
    get() = slotsOf(this).fullScreen
    set(value) { slotsOf(this).fullScreen = value; install() }

fun AppOpenAd.setOnPaidEventListener(listener: (AdValue) -> Unit) {
    slotsOf(this).paid = listener; install()
}

var AppOpenAd.fullScreenContentCallback: FullScreenContentCallback?
    get() = slotsOf(this).fullScreen
    set(value) { slotsOf(this).fullScreen = value; install() }

fun NativeAd.setOnPaidEventListener(listener: (AdValue) -> Unit) {
    slotsOf(this).paid = listener; install()
}

/** Impression / click events of a native ad (the classic SDK reported them on the loader). */
var NativeAd.eventListener: AdListener?
    get() = slotsOf(this).listener
    set(value) { slotsOf(this).listener = value; install() }

// ---------------------------------------------------------------------------------------------
// Native loader with the classic fluent shape
// ---------------------------------------------------------------------------------------------

/** Native request options (replaces the classic `NativeAdOptions`). */
class NgNativeOptions(
    val startMuted: Boolean = true,
    val mediaAspectRatio: NativeAd.NativeMediaAspectRatio? = NativeAd.NativeMediaAspectRatio.ANY,
)

/**
 * Fluent native loader keeping the classic shape
 * (`Builder(context, unitId).forNativeAd {}.withAdListener().build().loadAd()`), backed by the
 * Next-Gen [NativeAdLoader]. The listener also receives the loaded ad's impression and click
 * events, as the classic loader-level AdListener did.
 */
class NgNativeLoader private constructor(
    private val context: Context,
    private val adUnitId: String,
    private val listener: AdListener?,
    private val options: NgNativeOptions,
    private val onLoaded: ((NativeAd) -> Unit)?,
) {
    @Volatile private var loading = false
    fun isLoading(): Boolean = loading

    fun loadAd() {
        loading = true
        NextGenAds.loadNative(
            context, adUnitId,
            onLoaded = { ad ->
                loading = false
                if (listener != null) ad.eventListener = listener
                onLoaded?.invoke(ad)
                listener?.onAdLoaded()
            },
            onFailed = { e ->
                loading = false
                listener?.onAdFailedToLoad(e)
            },
            startMuted = options.startMuted,
            mediaAspectRatio = options.mediaAspectRatio,
        )
    }

    class Builder(private val context: Context, private val adUnitId: String) {
        private var listener: AdListener? = null
        private var options = NgNativeOptions()
        private var onLoaded: ((NativeAd) -> Unit)? = null

        fun withAdListener(l: AdListener) = apply { listener = l }
        fun withNativeAdOptions(o: NgNativeOptions) = apply { options = o }
        fun forNativeAd(l: (NativeAd) -> Unit) = apply { onLoaded = l }
        fun build() = NgNativeLoader(context.applicationContext ?: context, adUnitId, listener, options, onLoaded)
    }
}

// ---------------------------------------------------------------------------------------------
// Banner view with the classic shape
// ---------------------------------------------------------------------------------------------

/**
 * Banner container keeping the classic `AdView` shape (`adUnitId`, `setAdSize`, `adListener`,
 * `setOnPaidEventListener`, `loadAd()`, `destroy()`), backed by the Next-Gen [AdView].
 */
class BannerAdView(context: Context) : FrameLayout(context) {
    var adUnitId: String = ""
    var adListener: AdListener? = null
    private var adSize: AdSize = AdSize.BANNER
    private var paid: ((AdValue) -> Unit)? = null
    private var inner: AdView? = null
    var bannerAd: BannerAd? = null
        private set

    val responseInfo get() = bannerAd?.getResponseInfo()

    fun setAdSize(size: AdSize) { adSize = size }
    fun getAdSize(): AdSize = adSize
    fun setOnPaidEventListener(listener: (AdValue) -> Unit) { paid = listener }

    fun loadAd() {
        val id = adUnitId
        AdsSdkInit.whenReady(context) {
            val view = inner ?: AdView(context).also {
                inner = it
                addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
            view.loadAd(BannerAdRequest.Builder(id, adSize).build(), object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    ad.adEventCallback = object : BannerAdEventCallback {
                        override fun onAdPaid(value: AdValue) { paid?.let { p -> runMain { p(value) } } }
                        override fun onAdImpression() = runMain { adListener?.onAdImpression() }
                        override fun onAdClicked() = runMain { adListener?.onAdClicked() }
                    }
                    runMain {
                        bannerAd = ad
                        adListener?.onAdLoaded()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    runMain { adListener?.onAdFailedToLoad(adError) }
                }
            })
        }
    }

    fun pause() {}
    fun resume() {}

    fun destroy() {
        runCatching { inner?.destroy() }
        inner = null
        bannerAd = null
        removeAllViews()
    }
}



