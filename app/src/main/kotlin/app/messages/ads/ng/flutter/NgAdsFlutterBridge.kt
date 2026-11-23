package app.messages.ads.ng.flutter

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.messages.ads.ng.AdListener
import app.messages.ads.ng.AdsSdkInit
import app.messages.ads.ng.BannerAdView
import app.messages.ads.ng.FullScreenContentCallback
import app.messages.ads.ng.NextGenAds
import app.messages.ads.ng.NgLoadCallback
import app.messages.ads.ng.eventListener
import app.messages.ads.ng.fullScreenContentCallback
import app.messages.ads.ng.setOnPaidEventListener
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Builds the native view for a loaded native ad (one per factory id: small / big / full). */
fun interface NgNativeAdFactory {
    fun createNativeAd(nativeAd: NativeAd?, customOptions: MutableMap<String, Any>?): NativeAdView
}

/**
 * Host side of the downloader's `ng_ads` Dart library (video_downloader/lib/for_ads/ng/ng_ads.dart).
 *
 * The `google_mobile_ads` Flutter plugin needs the classic SDK, which cannot coexist with the
 * Next-Gen SDK, so the Flutter module talks to this bridge instead: it loads / shows ads with the
 * Next-Gen SDK, renders native and banner ads through the `ng_ads/ad_view` platform view, and
 * forwards UMP consent calls. Every ad event is sent back as `onAdEvent {adId, event, ...}`.
 */
object NgAdsFlutterBridge {

    private const val TAG = "NG_ADS_FLUTTER"
    private const val CHANNEL = "ng_ads"
    private const val VIEW_TYPE = "ng_ads/ad_view"

    private class EngineState(val engine: FlutterEngine, val channel: MethodChannel) {
        val factories = HashMap<String, NgNativeAdFactory>()
        val ads = HashMap<Int, Any>()           // NativeAd / InterstitialAd / AppOpenAd / BannerAdView
        val adViews = HashMap<Int, View>()      // rendered NativeAdView / BannerAdView per ad id
        val forms = HashMap<Int, ConsentForm>()
    }

    private val states = WeakHashMap<FlutterEngine, EngineState>()
    private var currentActivity: WeakReference<Activity>? = null
    private var lifecycleRegistered = false

    // ---- registration -----------------------------------------------------------------------

    /** Installs the channel and platform view on [engine] (idempotent). */
    @JvmStatic
    fun attach(context: Context, engine: FlutterEngine) {
        if (states.containsKey(engine)) return
        val appContext = context.applicationContext
        trackActivities(appContext)
        val channel = MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL)
        val state = EngineState(engine, channel)
        states[engine] = state
        channel.setMethodCallHandler { call, result -> handle(appContext, state, call, result) }
        engine.platformViewsController.registry.registerViewFactory(VIEW_TYPE, AdViewFactory(state))
    }

    @JvmStatic
    fun registerNativeAdFactory(engine: FlutterEngine, factoryId: String, factory: NgNativeAdFactory) {
        stateOf(engine).factories[factoryId] = factory
    }

    @JvmStatic
    fun unregisterNativeAdFactory(engine: FlutterEngine, factoryId: String) {
        states[engine]?.factories?.remove(factoryId)
    }

    private fun stateOf(engine: FlutterEngine): EngineState =
        states[engine] ?: error("NgAdsFlutterBridge.attach() was not called for this engine")

    private fun trackActivities(app: Context) {
        if (lifecycleRegistered) return
        lifecycleRegistered = true
        (app as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { currentActivity = WeakReference(activity) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) { currentActivity = WeakReference(activity) }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity?.get() === activity) currentActivity = null
            }
        })
    }

    private fun activity(): Activity? = currentActivity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    // ---- events to Dart -----------------------------------------------------------------------

    private fun send(state: EngineState, adId: Int, event: String, extra: Map<String, Any?> = emptyMap()) {
        val args = HashMap<String, Any?>(extra)
        args["adId"] = adId
        args["event"] = event
        runCatching { state.channel.invokeMethod("onAdEvent", args) }
    }

    private fun paidArgs(v: AdValue) = mapOf(
        "valueMicros" to v.valueMicros,
        "precision" to v.precisionType.ordinal,
        "currencyCode" to v.currencyCode,
    )

    private fun loadErrorArgs(e: LoadAdError) = mapOf(
        "code" to e.code.value,
        "message" to e.message,
        "responseId" to e.responseInfo?.responseId,
    )

    private fun showErrorArgs(e: FullScreenContentError) = mapOf("code" to e.code.value, "message" to e.message)

    private fun fullScreenCallback(state: EngineState, adId: Int) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = send(state, adId, "onAdShowedFullScreenContent")
        override fun onAdImpression() = send(state, adId, "onAdImpression")
        override fun onAdClicked() = send(state, adId, "onAdClicked")
        override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) =
            send(state, adId, "onAdFailedToShowFullScreenContent", showErrorArgs(error))
        override fun onAdDismissedFullScreenContent() = send(state, adId, "onAdDismissedFullScreenContent")
    }

    // ---- method calls -------------------------------------------------------------------------

    private fun handle(ctx: Context, state: EngineState, call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialize" -> AdsSdkInit.init(ctx) { result.success(null) }
            "loadNative" -> { loadNative(ctx, state, call); result.success(null) }
            "loadBanner" -> { loadBanner(ctx, state, call); result.success(null) }
            "loadInterstitial" -> { loadInterstitial(ctx, state, call); result.success(null) }
            "loadAppOpen" -> { loadAppOpen(ctx, state, call); result.success(null) }
            "showAd" -> { showAd(state, call.argument<Int>("adId")!!); result.success(null) }
            "setImmersiveMode" -> {
                val enabled = call.argument<Boolean>("enabled") ?: false
                when (val ad = state.ads[call.argument<Int>("adId")]) {
                    is InterstitialAd -> ad.setImmersiveMode(enabled)
                    is AppOpenAd -> ad.setImmersiveMode(enabled)
                }
                result.success(null)
            }
            "disposeAd" -> { disposeAd(state, call.argument<Int>("adId")!!); result.success(null) }
            else -> if (!handleConsent(ctx, state, call, result)) result.notImplemented()
        }
    }

    private fun loadNative(ctx: Context, state: EngineState, call: MethodCall) {
        val adId = call.argument<Int>("adId")!!
        val unit = call.argument<String>("adUnitId").orEmpty()
        val factoryId = call.argument<String>("factoryId") ?: "bigNativeAds"
        val startMuted = call.argument<Boolean>("startMuted") ?: true
        // values(), not entries: the SDK is built with an older Kotlin, so its enums have no
        // getEntries() at runtime (NoSuchMethodError).
        val ratio = NativeAd.NativeMediaAspectRatio.values().getOrNull(call.argument<Int>("mediaAspectRatio") ?: 1)
            ?: NativeAd.NativeMediaAspectRatio.ANY
        NextGenAds.loadNative(
            ctx, unit,
            onLoaded = loaded@{ ad ->
                if (disposed.remove(adId)) { ad.destroy(); return@loaded }
                ad.setOnPaidEventListener { v -> send(state, adId, "onPaidEvent", paidArgs(v)) }
                ad.eventListener = object : AdListener() {
                    override fun onAdImpression() = send(state, adId, "onAdImpression")
                    override fun onAdClicked() = send(state, adId, "onAdClicked")
                    override fun onAdOpened() = send(state, adId, "onAdOpened")
                    override fun onAdClosed() = send(state, adId, "onAdClosed")
                }
                val factory = state.factories[factoryId]
                if (factory == null) {
                    Log.e(TAG, "No native factory '$factoryId' registered")
                    ad.destroy()
                    send(state, adId, "onAdFailedToLoad", mapOf("code" to 0, "message" to "No factory $factoryId"))
                    return@loaded
                }
                state.ads[adId] = ad
                state.adViews[adId] = factory.createNativeAd(ad, null)
                send(state, adId, "onAdLoaded", mapOf("responseId" to ad.getResponseInfo()?.responseId))
            },
            onFailed = { e -> send(state, adId, "onAdFailedToLoad", loadErrorArgs(e)) },
            startMuted = startMuted,
            mediaAspectRatio = ratio,
        )
    }

    /** Ad ids Dart disposed before their load finished. */
    private val disposed = HashSet<Int>()

    private fun loadBanner(ctx: Context, state: EngineState, call: MethodCall) {
        val adId = call.argument<Int>("adId")!!
        val width = call.argument<Int>("width") ?: 320
        val height = call.argument<Int>("height") ?: 50
        val view = BannerAdView(ctx).apply {
            adUnitId = call.argument<String>("adUnitId").orEmpty()
            setAdSize(AdSize(width, height))
            setOnPaidEventListener { v -> send(state, adId, "onPaidEvent", paidArgs(v)) }
            adListener = object : AdListener() {
                override fun onAdLoaded() = send(state, adId, "onAdLoaded", mapOf("responseId" to responseInfo?.responseId))
                override fun onAdFailedToLoad(error: LoadAdError) = send(state, adId, "onAdFailedToLoad", loadErrorArgs(error))
                override fun onAdImpression() = send(state, adId, "onAdImpression")
                override fun onAdClicked() = send(state, adId, "onAdClicked")
            }
        }
        state.ads[adId] = view
        state.adViews[adId] = view
        view.loadAd()
    }

    private fun loadInterstitial(ctx: Context, state: EngineState, call: MethodCall) {
        val adId = call.argument<Int>("adId")!!
        NextGenAds.loadInterstitial(ctx, call.argument<String>("adUnitId").orEmpty(), object : NgLoadCallback<InterstitialAd>() {
            override fun onAdLoaded(ad: InterstitialAd) {
                if (disposed.remove(adId)) { ad.destroy(); return }
                ad.setOnPaidEventListener { v -> send(state, adId, "onPaidEvent", paidArgs(v)) }
                ad.fullScreenContentCallback = fullScreenCallback(state, adId)
                state.ads[adId] = ad
                send(state, adId, "onAdLoaded", mapOf("responseId" to ad.getResponseInfo()?.responseId))
            }

            override fun onAdFailedToLoad(error: LoadAdError) = send(state, adId, "onAdFailedToLoad", loadErrorArgs(error))
        })
    }

    private fun loadAppOpen(ctx: Context, state: EngineState, call: MethodCall) {
        val adId = call.argument<Int>("adId")!!
        NextGenAds.loadAppOpen(ctx, call.argument<String>("adUnitId").orEmpty(), object : NgLoadCallback<AppOpenAd>() {
            override fun onAdLoaded(ad: AppOpenAd) {
                if (disposed.remove(adId)) { ad.destroy(); return }
                ad.setOnPaidEventListener { v -> send(state, adId, "onPaidEvent", paidArgs(v)) }
                ad.fullScreenContentCallback = fullScreenCallback(state, adId)
                state.ads[adId] = ad
                send(state, adId, "onAdLoaded", mapOf("responseId" to ad.getResponseInfo()?.responseId))
            }

            override fun onAdFailedToLoad(error: LoadAdError) = send(state, adId, "onAdFailedToLoad", loadErrorArgs(error))
        })
    }

    private fun showAd(state: EngineState, adId: Int) {
        val activity = activity()
        val ad = state.ads[adId]
        if (activity == null || ad == null) {
            send(state, adId, "onAdFailedToShowFullScreenContent", mapOf("code" to 0, "message" to "No activity or ad"))
            return
        }
        when (ad) {
            is InterstitialAd -> ad.show(activity)
            is AppOpenAd -> ad.show(activity)
        }
    }

    private fun disposeAd(state: EngineState, adId: Int) {
        val ad = state.ads.remove(adId)
        val view = state.adViews.remove(adId)
        if (ad == null) disposed.add(adId)
        (view?.parent as? ViewGroup)?.removeView(view)
        when (ad) {
            is NativeAd -> { (view as? NativeAdView)?.destroy(); ad.destroy() }
            is InterstitialAd -> ad.destroy()
            is AppOpenAd -> ad.destroy()
            is BannerAdView -> ad.destroy()
        }
    }

    // ---- platform view ------------------------------------------------------------------------

    private class AdViewFactory(private val state: EngineState) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
        override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
            val adId = ((args as? Map<*, *>)?.get("adId") as? Number)?.toInt() ?: -1
            val adView = state.adViews[adId]
            val host = FrameLayout(context)
            if (adView != null) {
                (adView.parent as? ViewGroup)?.removeView(adView)
                host.addView(adView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            } else {
                Log.w(TAG, "AdWidget for unknown/unloaded ad $adId")
            }
            return object : PlatformView {
                override fun getView(): View = host
                override fun dispose() {
                    // Detach only; the ad stays alive until Dart disposes it.
                    if (adView != null && adView.parent === host) host.removeView(adView)
                }
            }
        }
    }

    // ---- UMP consent ----------------------------------------------------------------------------

    private fun formErrorMap(e: FormError?): Map<String, Any?>? =
        e?.let { mapOf("errorCode" to it.errorCode, "message" to it.message) }

    private fun consentInfo(ctx: Context): ConsentInformation = UserMessagingPlatform.getConsentInformation(ctx)

    private fun dismissed(state: EngineState, listenerId: Int?, e: FormError?) {
        runCatching {
            state.channel.invokeMethod("onConsentFormDismissed", mapOf("listenerId" to listenerId, "error" to formErrorMap(e)))
        }
    }

    private fun handleConsent(ctx: Context, state: EngineState, call: MethodCall, result: MethodChannel.Result): Boolean {
        when (call.method) {
            "consentRequestUpdate" -> {
                val activity = activity()
                if (activity == null) {
                    result.success(mapOf("errorCode" to -1, "message" to "No activity"))
                    return true
                }
                val builder = ConsentRequestParameters.Builder()
                call.argument<Boolean>("tagForUnderAgeOfConsent")?.let { builder.setTagForUnderAgeOfConsent(it) }
                val geo = call.argument<Int>("debugGeography")
                val ids = call.argument<List<String>>("testIdentifiers")
                if (geo != null || !ids.isNullOrEmpty()) {
                    val debug = ConsentDebugSettings.Builder(ctx)
                    if (geo != null) debug.setDebugGeography(geo)
                    ids?.forEach { debug.addTestDeviceHashedId(it) }
                    builder.setConsentDebugSettings(debug.build())
                }
                consentInfo(ctx).requestConsentInfoUpdate(
                    activity, builder.build(),
                    { result.success(null) },
                    { e -> result.success(formErrorMap(e)) },
                )
            }
            "consentIsFormAvailable" -> result.success(consentInfo(ctx).isConsentFormAvailable)
            "consentGetStatus" -> result.success(
                when (consentInfo(ctx).consentStatus) {
                    ConsentInformation.ConsentStatus.NOT_REQUIRED -> 0
                    ConsentInformation.ConsentStatus.OBTAINED -> 1
                    ConsentInformation.ConsentStatus.REQUIRED -> 2
                    else -> 3
                }
            )
            "consentCanRequestAds" -> result.success(consentInfo(ctx).canRequestAds())
            "consentPrivacyOptionsStatus" -> result.success(
                when (consentInfo(ctx).privacyOptionsRequirementStatus) {
                    ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED -> 0
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED -> 1
                    else -> 2
                }
            )
            "consentReset" -> { consentInfo(ctx).reset(); result.success(null) }
            "consentLoadForm" -> {
                val formId = call.argument<Int>("formId")!!
                UserMessagingPlatform.loadConsentForm(
                    ctx,
                    { form -> state.forms[formId] = form; result.success(null) },
                    { e -> result.success(formErrorMap(e)) },
                )
            }
            "consentShowForm" -> {
                val form = state.forms[call.argument<Int>("formId")]
                val listenerId = call.argument<Int>("listenerId")
                val activity = activity()
                if (form == null || activity == null) {
                    dismissed(state, listenerId, null)
                } else {
                    form.show(activity) { e -> dismissed(state, listenerId, e) }
                }
                result.success(null)
            }
            "consentShowPrivacyOptionsForm" -> {
                val listenerId = call.argument<Int>("listenerId")
                val activity = activity()
                if (activity == null) dismissed(state, listenerId, null)
                else UserMessagingPlatform.showPrivacyOptionsForm(activity) { e -> dismissed(state, listenerId, e) }
                result.success(null)
            }
            "consentLoadAndShowIfRequired" -> {
                val listenerId = call.argument<Int>("listenerId")
                val activity = activity()
                if (activity == null) dismissed(state, listenerId, null)
                else UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { e -> dismissed(state, listenerId, e) }
                result.success(null)
            }
            "consentDisposeForm" -> { state.forms.remove(call.argument<Int>("formId")); result.success(null) }
            else -> return false
        }
        return true
    }
}
