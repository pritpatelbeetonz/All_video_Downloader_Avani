package app.messages.ui.videodownloader

import android.content.Context
import android.view.LayoutInflater
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.appevents.AppEventsLogger
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import app.messages.ads.ng.flutter.NgAdsFlutterBridge
import app.messages.ads.ng.flutter.NgNativeAdFactory

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
            NgAdsFlutterBridge.unregisterNativeAdFactory(flutterEngine, "smallNativeAds")
            NgAdsFlutterBridge.unregisterNativeAdFactory(flutterEngine, "bigNativeAds")
            NgAdsFlutterBridge.unregisterNativeAdFactory(flutterEngine, "fullNativeAds")
        } catch (_: Exception) {
        }

        NgAdsFlutterBridge.registerNativeAdFactory(
            flutterEngine,
            "smallNativeAds",
            NativeAdFactorySmall(inflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
        NgAdsFlutterBridge.registerNativeAdFactory(
            flutterEngine,
            "bigNativeAds",
            NativeAdFactoryBig(inflater, startColor, endColor, backgroundColor, headLineTextColor, bodyTextColor, buttonTextColor)
        )
        NgAdsFlutterBridge.registerNativeAdFactory(
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
                    "isDownloaderVisible" -> {
                        result.success(isDownloaderVisible)
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
                    "saveToDownloads" -> {
                        val sourcePath = call.argument<String>("sourcePath") ?: ""
                        val fileName = call.argument<String>("fileName")
                            ?: "download_${System.currentTimeMillis()}"
                        val mime = call.argument<String>("mime") ?: "video/mp4"
                        val subDir = call.argument<String>("subDir") ?: ""
                        val appContext = context.applicationContext
                        Thread {
                            val saved: String? = try {
                                saveFileToDownloads(appContext, sourcePath, fileName, mime, subDir)
                            } catch (e: Exception) {
                                android.util.Log.e("MEDIASTORE_SAVE", "Failed: ${e.message}", e)
                                null
                            }
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (saved != null) result.success(saved)
                                else result.error("SAVE_FAILED", "Could not save to Downloads", null)
                            }
                        }.start()
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

    /**
     * Copies a finished temp file into the public Download/<subDir> folder so the
     * user sees it directly in Files -> Downloads (and video galleries).
     * Android 10+ (API 29): MediaStore.Downloads — no storage permission needed.
     * Android 9 and below: legacy public-dir write + media scan.
     * Returns the saved path/relative-path on success, null on failure.
     */
    private fun saveFileToDownloads(
        context: Context,
        sourcePath: String,
        fileName: String,
        mime: String,
        subDir: String
    ): String? {
        val source = java.io.File(sourcePath)
        if (!source.exists()) {
            android.util.Log.e("MEDIASTORE_SAVE", "Source temp file missing: $sourcePath")
            return null
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = if (subDir.isBlank()) {
                android.os.Environment.DIRECTORY_DOWNLOADS
            } else {
                "${android.os.Environment.DIRECTORY_DOWNLOADS}/$subDir"
            }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = android.provider.MediaStore.Downloads.getContentUri(
                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
            val uri = resolver.insert(collection, values) ?: run {
                android.util.Log.e("MEDIASTORE_SAVE", "resolver.insert returned null")
                return null
            }
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                } ?: run {
                    android.util.Log.e("MEDIASTORE_SAVE", "openOutputStream null")
                    resolver.delete(uri, null, null)
                    return null
                }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                android.util.Log.d("MEDIASTORE_SAVE", "Saved via MediaStore: $relativePath/$fileName")
                return "$relativePath/$fileName"
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }

        // Android 9 and below: legacy public Download write.
        @Suppress("DEPRECATION")
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val dir = if (subDir.isBlank()) downloads else java.io.File(downloads, subDir)
        if (!dir.exists()) dir.mkdirs()
        val dest = java.io.File(dir, fileName)
        source.copyTo(dest, overwrite = true)
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(dest.absolutePath), arrayOf(mime), null
        )
        android.util.Log.d("MEDIASTORE_SAVE", "Saved legacy: ${dest.absolutePath}")
        return dest.absolutePath
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

    /**
     * Whether the downloader page is actually on screen. Flutter's own lifecycle
     * reports `resumed` for the pre-warmed engine even with no view attached, so
     * ads in the Flutter module gate on this instead.
     */
    @Volatile
    var isDownloaderVisible: Boolean = false
        private set

    fun setDownloaderVisible(visible: Boolean) {
        if (isDownloaderVisible == visible) return
        isDownloaderVisible = visible
        try {
            val flutterEngine = io.flutter.embedding.engine.FlutterEngineCache.getInstance().get(ENGINE_ID)
            if (flutterEngine != null) {
                MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
                    .invokeMethod("setDownloaderVisible", mapOf("visible" to visible))
                android.util.Log.d("VideoDownloaderEngine", "Dispatched setDownloaderVisible($visible) to Flutter")
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
        // Next-Gen ads bridge for the Flutter module (replaces the google_mobile_ads plugin).
        NgAdsFlutterBridge.attach(context, flutterEngine)
        registerAdFactories(context, flutterEngine)
        initMethodChannel(context, flutterEngine)
        if (app.messages.config.AdsConfigManager.rawConfigJson.isNotBlank()) {
            syncAdsConfigToFlutter(app.messages.config.AdsConfigManager.rawConfigJson)
        }
    }
}
