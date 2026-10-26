package app.messages.ui.videodownloader

import android.content.Context
import android.view.LayoutInflater
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.appevents.AppEventsLogger
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.googlemobileads.GoogleMobileAdsPlugin

object VideoDownloaderEngineHelper {
    const val ENGINE_ID = "video_downloader_engine"
    const val CHANNEL = "nativeChannel"

    private var startColor: String = "#FE7338"
    private var endColor: String = "#FE7338"
    private var backgroundColor: String = "#FFF8F5"
    private var headLineTextColor: String = "#000000"
    private var bodyTextColor: String = "#000000"
    private var buttonTextColor: String = "#FFFFFF"

    fun registerAdFactories(context: Context, flutterEngine: FlutterEngine) {
        val inflater = LayoutInflater.from(context)
        try {
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "smallNativeAds")
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "bigNativeAds")
            GoogleMobileAdsPlugin.unregisterNativeAdFactory(flutterEngine, "fullNativeAds")
        } catch (_: Exception) {
        }

        GoogleMobileAdsPlugin.registerNativeAdFactory(
            flutterEngine,
            "smallNativeAds",
            NativeAdFactorySmall(inflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
        GoogleMobileAdsPlugin.registerNativeAdFactory(
            flutterEngine,
            "bigNativeAds",
            NativeAdFactoryBig(inflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
        GoogleMobileAdsPlugin.registerNativeAdFactory(
            flutterEngine,
            "fullNativeAds",
            NativeAdFactoryFull(inflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
    }

    fun initMethodChannel(context: Context, flutterEngine: FlutterEngine) {
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
                                FacebookSdk.sdkInitialize(context)
                                FacebookSdk.setAutoInitEnabled(true)
                                FacebookSdk.fullyInitialize()
                                FacebookSdk.setAutoLogAppEventsEnabled(true)
                                FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
                                AppEventsLogger.newLogger(context).applicationId
                            }

                            registerAdFactories(context, flutterEngine)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        result.success(true)
                    }
                    "openNativeLanguageScreen" -> {
                        try {
                            val intent = android.content.Intent(
                                context,
                                app.messages.ui.activity.LanguageSelectionActivity::class.java
                            ).apply {
                                putExtra("from_settings", true)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        result.success(true)
                    }
                    "getAdsConfig" -> {
                        result.success(app.messages.config.AdsConfigManager.rawConfigJson)
                    }
                    "acquireWakeLock" -> {
                        acquireWakeLock(context)
                        result.success(true)
                    }
                    "releaseWakeLock" -> {
                        releaseWakeLock()
                        result.success(true)
                    }
                    "logNative" -> {
                        try {
                            val tag = call.argument<String>("tag") ?: "FLUTTER_LOG"
                            val msg = call.argument<String>("message") ?: ""
                            android.util.Log.d(tag, msg)
                        } catch (_: Exception) {}
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    fun acquireWakeLock(context: Context) {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "VideoDownloader:DownloadWakeLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(30 * 60 * 1000L) // 30 mins max timeout safety
                android.util.Log.d("WAKELOCK", "⚡ [WAKELOCK] Acquired wake lock for background download")
            }
        } catch (e: Exception) {
            android.util.Log.e("WAKELOCK", "Failed to acquire wake lock: ${e.message}")
        }
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                android.util.Log.d("WAKELOCK", "💤 [WAKELOCK] Released wake lock")
            }
        } catch (e: Exception) {
            android.util.Log.e("WAKELOCK", "Failed to release wake lock: ${e.message}")
        }
    }

    fun syncAdsConfigToFlutter(rawJson: String? = null) {
        try {
            val json = rawJson ?: app.messages.config.AdsConfigManager.rawConfigJson
            if (json.isBlank()) return
            val flutterEngine = io.flutter.embedding.engine.FlutterEngineCache.getInstance().get(ENGINE_ID)
            if (flutterEngine != null) {
                MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
                    .invokeMethod("syncAdsConfig", mapOf("rawConfig" to json))
                android.util.Log.d("VideoDownloaderEngine", "Dispatched syncAdsConfig to Flutter (${json.length} chars)")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyLanguageChanged(languageCode: String) {
        try {
            val flutterEngine = io.flutter.embedding.engine.FlutterEngineCache.getInstance().get(ENGINE_ID)
            if (flutterEngine != null) {
                val normalized = if (languageCode == "in") "id" else languageCode
                MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
                    .invokeMethod("changeLanguage", mapOf("languageCode" to normalized))
                android.util.Log.d("VideoDownloaderEngine", "Dispatched changeLanguage ($normalized) to Flutter")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initEngine(context: Context, flutterEngine: FlutterEngine) {
        registerAdFactories(context, flutterEngine)
        initMethodChannel(context, flutterEngine)
        if (app.messages.config.AdsConfigManager.rawConfigJson.isNotBlank()) {
            syncAdsConfigToFlutter(app.messages.config.AdsConfigManager.rawConfigJson)
        }
    }
}
