package app.messages.ui.videodownloader

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
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.googlemobileads.GoogleMobileAdsPlugin

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

    private var startColor: String = "#FE7338"
    private var endColor: String = "#FE7338"
    private var backgroundColor: String = "#FFF8F5"
    private var headLineTextColor: String = "#000000"
    private var bodyTextColor: String = "#000000"
    private var buttonTextColor: String = "#FFFFFF"

    private fun registerAdFactories(flutterEngine: FlutterEngine) {
        try {
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "smallNativeAds")
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "bigNativeAds")
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "fullNativeAds")
        } catch (_: Exception) {
            // ignore if not previously registered
        }

        GoogleMobileAdsPlugin.registerNativeAdFactory(
            flutterEngine,
            "smallNativeAds",
            NativeAdFactorySmall(layoutInflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
        GoogleMobileAdsPlugin.registerNativeAdFactory(
            flutterEngine,
            "bigNativeAds",
            NativeAdFactoryBig(layoutInflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
        GoogleMobileAdsPlugin.registerNativeAdFactory(
            flutterEngine,
            "fullNativeAds",
            NativeAdFactoryFull(layoutInflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

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
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "smallNativeAds")
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "bigNativeAds")
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "fullNativeAds")
        } catch (_: Exception) {
        }
        super.cleanUpFlutterEngine(flutterEngine)
    }
}

class NativeAdFactorySmall(
    private val layoutInflater: LayoutInflater,
    private val startColor: String,
    private val endColor: String,
    private val backgroundColor: String,
    private val headLineTextColor: String,
    private val bodyTextColor: String,
    private val buttonTextColor: String
) : GoogleMobileAdsPlugin.NativeAdFactory {

    override fun createNativeAd(
        nativeAd: NativeAd?,
        customOptions: MutableMap<String, Any>?
    ): NativeAdView {
        val adView = layoutInflater.inflate(R.layout.small_template, null) as NativeAdView

        val circularLayoutBackground: LinearLayout = adView.findViewById(R.id.circular_layout_background)
        val cornerRadius = 20f * adView.context.resources.displayMetrics.density
        val backgroundGradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(safeParseColor(backgroundColor, Color.parseColor("#FFF8F5")))
            this.cornerRadius = cornerRadius
        }
        circularLayoutBackground.background = backgroundGradientDrawable

        adView.headlineView = adView.findViewById(R.id.ad_headline)
        (adView.headlineView as? TextView)?.setTextColor(safeParseColor(headLineTextColor, Color.BLACK))

        adView.bodyView = adView.findViewById(R.id.ad_body)
        (adView.bodyView as? TextView)?.setTextColor(safeParseColor(bodyTextColor, Color.DKGRAY))

        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        val buttonGradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TR_BL,
            intArrayOf(safeParseColor(startColor, Color.parseColor("#FE7338")), safeParseColor(endColor, Color.parseColor("#FE7338")))
        )
        buttonGradientDrawable.cornerRadius = 14f * adView.context.resources.displayMetrics.density
        adView.callToActionView?.background = buttonGradientDrawable
        (adView.callToActionView as? Button)?.setTextColor(safeParseColor(buttonTextColor, Color.WHITE))

        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        adView.priceView = adView.findViewById(R.id.native_ad_attribution_small)
        val priceGradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TR_BL,
            intArrayOf(safeParseColor(startColor, Color.parseColor("#FE7338")), safeParseColor(endColor, Color.parseColor("#FE7338")))
        ).apply {
            cornerRadii = floatArrayOf(
                10f * adView.context.resources.displayMetrics.density, 10f * adView.context.resources.displayMetrics.density,
                0f, 0f,
                0f, 0f,
                5f * adView.context.resources.displayMetrics.density, 5f * adView.context.resources.displayMetrics.density
            )
        }
        adView.priceView?.background = priceGradientDrawable
        (adView.priceView as? TextView)?.setTextColor(safeParseColor(buttonTextColor, Color.WHITE))

        adView.starRatingView = adView.findViewById(R.id.ad_stars)

        (adView.headlineView as? TextView)?.text = nativeAd?.headline

        if (nativeAd?.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.text = nativeAd.body
        }

        if (nativeAd?.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        }

        if (nativeAd?.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        if (nativeAd?.starRating == null) {
            adView.starRatingView?.visibility = View.INVISIBLE
        } else {
            (adView.starRatingView as? RatingBar)?.rating = nativeAd.starRating?.toFloat() ?: 0f
            adView.starRatingView?.visibility = View.VISIBLE
        }

        if (nativeAd != null) {
            adView.setNativeAd(nativeAd)
        }

        return adView
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
) : GoogleMobileAdsPlugin.NativeAdFactory {

    override fun createNativeAd(
        nativeAd: NativeAd?,
        customOptions: MutableMap<String, Any>?
    ): NativeAdView {
        val adView = layoutInflater.inflate(R.layout.big_template, null) as NativeAdView

        val circularLayoutBackground: LinearLayout = adView.findViewById(R.id.circular_layout_background)
        val cornerRadius = 20f * adView.context.resources.displayMetrics.density
        val backgroundGradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(safeParseColor(backgroundColor, Color.parseColor("#FFF8F5")))
            this.cornerRadius = cornerRadius
            val strokeWidth = (1.5f * adView.context.resources.displayMetrics.density).toInt()
            setStroke(strokeWidth, safeParseColor(startColor, Color.parseColor("#FE7338")))
        }
        circularLayoutBackground.background = backgroundGradientDrawable

        adView.mediaView = adView.findViewById(R.id.native_ad_media)

        adView.headlineView = adView.findViewById(R.id.ad_headline)
        (adView.headlineView as? TextView)?.setTextColor(safeParseColor(headLineTextColor, Color.BLACK))

        adView.bodyView = adView.findViewById(R.id.ad_body)
        (adView.bodyView as? TextView)?.setTextColor(safeParseColor(bodyTextColor, Color.DKGRAY))

        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        val buttonGradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TR_BL,
            intArrayOf(safeParseColor(startColor, Color.parseColor("#FE7338")), safeParseColor(endColor, Color.parseColor("#FE7338")))
        )
        buttonGradientDrawable.cornerRadius = 14f * adView.context.resources.displayMetrics.density
        adView.callToActionView?.background = buttonGradientDrawable
        (adView.callToActionView as? Button)?.setTextColor(safeParseColor(buttonTextColor, Color.WHITE))

        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        adView.priceView = adView.findViewById(R.id.native_ad_attribution_small)
        val priceGradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TR_BL,
            intArrayOf(safeParseColor(startColor, Color.parseColor("#FE7338")), safeParseColor(endColor, Color.parseColor("#FE7338")))
        ).apply {
            cornerRadii = floatArrayOf(
                10f * adView.context.resources.displayMetrics.density, 10f * adView.context.resources.displayMetrics.density,
                0f, 0f,
                0f, 0f,
                5f * adView.context.resources.displayMetrics.density, 5f * adView.context.resources.displayMetrics.density
            )
        }
        adView.priceView?.background = priceGradientDrawable
        (adView.priceView as? TextView)?.setTextColor(safeParseColor(buttonTextColor, Color.WHITE))

        adView.starRatingView = adView.findViewById(R.id.ad_stars)

        (adView.headlineView as? TextView)?.text = nativeAd?.headline
        adView.mediaView?.mediaContent = nativeAd?.mediaContent

        if (nativeAd?.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.text = nativeAd.body
        }

        if (nativeAd?.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        }

        if (nativeAd?.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        if (nativeAd?.starRating == null) {
            adView.starRatingView?.visibility = View.INVISIBLE
        } else {
            (adView.starRatingView as? RatingBar)?.rating = nativeAd.starRating?.toFloat() ?: 0f
            adView.starRatingView?.visibility = View.VISIBLE
        }

        if (nativeAd != null) {
            adView.setNativeAd(nativeAd)
        }

        return adView
    }
}

class NativeAdFactoryFull(
    private val layoutInflater: LayoutInflater,
    private val startColor: String,
    private val endColor: String,
    private val backgroundColor: String,
    private val headLineTextColor: String,
    private val bodyTextColor: String,
    private val buttonTextColor: String
) : GoogleMobileAdsPlugin.NativeAdFactory {

    override fun createNativeAd(
        nativeAd: NativeAd?,
        customOptions: MutableMap<String, Any>?
    ): NativeAdView {
        val adView = layoutInflater.inflate(R.layout.full_template, null) as NativeAdView

        val circularLayoutBackground: LinearLayout = adView.findViewById(R.id.circular_layout_background)
        val backgroundGradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(safeParseColor(backgroundColor, Color.parseColor("#FFF8F5")))
            this.cornerRadius = 0f
        }
        circularLayoutBackground.background = backgroundGradientDrawable

        adView.mediaView = adView.findViewById(R.id.native_ad_media)

        adView.headlineView = adView.findViewById(R.id.ad_headline)
        (adView.headlineView as? TextView)?.setTextColor(safeParseColor(headLineTextColor, Color.BLACK))

        adView.bodyView = adView.findViewById(R.id.ad_body)
        (adView.bodyView as? TextView)?.setTextColor(safeParseColor(bodyTextColor, Color.DKGRAY))

        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        val buttonGradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TR_BL,
            intArrayOf(safeParseColor(startColor, Color.parseColor("#FE7338")), safeParseColor(endColor, Color.parseColor("#FE7338")))
        )
        buttonGradientDrawable.cornerRadius = 14f * adView.context.resources.displayMetrics.density
        adView.callToActionView?.background = buttonGradientDrawable
        (adView.callToActionView as? Button)?.setTextColor(safeParseColor(buttonTextColor, Color.WHITE))

        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        adView.priceView = adView.findViewById(R.id.native_ad_attribution_small)
        val priceGradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TR_BL,
            intArrayOf(safeParseColor(startColor, Color.parseColor("#FE7338")), safeParseColor(endColor, Color.parseColor("#FE7338")))
        ).apply {
            cornerRadii = floatArrayOf(
                10f * adView.context.resources.displayMetrics.density, 10f * adView.context.resources.displayMetrics.density,
                0f, 0f,
                0f, 0f,
                5f * adView.context.resources.displayMetrics.density, 5f * adView.context.resources.displayMetrics.density
            )
        }
        adView.priceView?.background = priceGradientDrawable
        (adView.priceView as? TextView)?.setTextColor(safeParseColor(buttonTextColor, Color.WHITE))

        adView.starRatingView = adView.findViewById(R.id.ad_stars)

        (adView.headlineView as? TextView)?.text = nativeAd?.headline
        adView.mediaView?.mediaContent = nativeAd?.mediaContent

        if (nativeAd?.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.text = nativeAd.body
        }

        if (nativeAd?.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        }

        if (nativeAd?.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        if (nativeAd?.starRating == null) {
            adView.starRatingView?.visibility = View.INVISIBLE
        } else {
            (adView.starRatingView as? RatingBar)?.rating = nativeAd.starRating?.toFloat() ?: 0f
            adView.starRatingView?.visibility = View.VISIBLE
        }

        if (nativeAd != null) {
            adView.setNativeAd(nativeAd)
        }

        return adView
    }
}
