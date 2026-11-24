package app.messages.ui.videodownloader

import app.messages.ads.ng.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import app.messages.R
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.appevents.AppEventsLogger
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import app.messages.ads.ng.flutter.NgAdsFlutterBridge
import app.messages.ads.ng.flutter.NgNativeAdFactory

fun safeParseColor(colorStr: String?, defaultColor: Int): Int {
    if (colorStr.isNullOrBlank()) return defaultColor
    val trimmed = colorStr.trim()
    val formatted = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return try {
        Color.parseColor(formatted)
    } catch (_: Exception) {
        defaultColor
    }
}

class VideoDownloaderFlutterActivity : FlutterActivity() {

    companion object {
        private const val CHANNEL = "nativeChannel"

        fun start(context: Context) {
            val intent = Intent(context, VideoDownloaderFlutterActivity::class.java)
            context.startActivity(intent)
        }
    }

    private var startColor: String = "#FB7829"
    private var endColor: String = "#FF0048"
    private var backgroundColor: String = "#FFF8F5"
    private var headLineTextColor: String = "#000000"
    private var bodyTextColor: String = "#000000"
    private var buttonTextColor: String = "#FFFFFF"

    private fun registerAdFactories(flutterEngine: FlutterEngine) {
        try {
            NgAdsFlutterBridge.unregisterNativeAdFactory(flutterEngine, "bigNativeAds")
        } catch (_: Exception) {
            // ignore if not previously registered
        }

        NgAdsFlutterBridge.registerNativeAdFactory(
            flutterEngine,
            "bigNativeAds",
            NativeAdFactoryBig(layoutInflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        NgAdsFlutterBridge.attach(this, flutterEngine)

        // Always register NativeAd factories immediately so Flutter screens have them ready
        registerAdFactories(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call: MethodCall, result ->
                when (call.method) {
                    "setToast" -> {
                        try {
                            val fbAppId = call.argument<String>("fb_appid") ?: ""
                            val fbToken = call.argument<String>("fb_token") ?: ""
                            startColor = call.argument<String>("btnBgColorG1") ?: startColor
                            endColor = call.argument<String>("btnBgColorG2") ?: endColor
                            backgroundColor = call.argument<String>("nativeBGColor") ?: backgroundColor
                            headLineTextColor = call.argument<String>("headerTextColor") ?: headLineTextColor
                            bodyTextColor = call.argument<String>("bodyTextColor") ?: bodyTextColor
                            buttonTextColor = call.argument<String>("btnTextColor") ?: buttonTextColor

                            if (fbAppId.isNotEmpty() && fbAppId != "null" && fbToken.isNotEmpty() && fbToken != "null") {
                                FacebookSdk.setApplicationId(fbAppId)
                                FacebookSdk.setClientToken(fbToken)
                                FacebookSdk.sdkInitialize(this@VideoDownloaderFlutterActivity)
                                FacebookSdk.setAutoInitEnabled(true)
                                FacebookSdk.fullyInitialize()
                                FacebookSdk.setAutoLogAppEventsEnabled(true)
                                FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
                                AppEventsLogger.newLogger(this@VideoDownloaderFlutterActivity).applicationId
                            }

                            // Re-register ad factories with remote config styling
                            registerAdFactories(flutterEngine)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        try {
            NgAdsFlutterBridge.unregisterNativeAdFactory(flutterEngine, "bigNativeAds")
        } catch (_: Exception) {
        }
        super.cleanUpFlutterEngine(flutterEngine)
    }
}

class NativeAdFactoryBig(
    private val layoutInflater: LayoutInflater,
    private val startColor: String,
    private val endColor: String,
    private val backgroundColor: String,
    private val headLineTextColor: String,
    private val bodyTextColor: String,
    private val buttonTextColor: String
) : NgNativeAdFactory {

    /**
     * Same card as the Language / Flow A / Dashboard natives (ads_native_big), so every wide native in
     * the app looks alike. Only the CTA keeps the downloader's own orange gradient (Remote Config colors).
     */
    override fun createNativeAd(
        nativeAd: NativeAd?,
        customOptions: MutableMap<String, Any>?
    ): NativeAdView {
        val adView = layoutInflater.inflate(R.layout.ads_native_big, null) as NativeAdView
        val density = adView.context.resources.displayMetrics.density

        adView.findViewById<TextView>(R.id.ad_call_to_action)?.apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TR_BL,
                intArrayOf(
                    safeParseColor(startColor, Color.parseColor("#FB7829")),
                    safeParseColor(endColor, Color.parseColor("#FF0048"))
                )
            ).apply { cornerRadius = 100f * density }
            backgroundTintList = null
            setTextColor(safeParseColor(buttonTextColor, Color.WHITE))
        }

        if (nativeAd != null) {
            app.messages.config.AdsConfigManager.populateNativeAdView(nativeAd, adView, "downloader_big")
        }
        return adView
    }
}
