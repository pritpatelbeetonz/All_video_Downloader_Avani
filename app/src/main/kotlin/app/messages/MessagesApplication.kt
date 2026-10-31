package app.messages

import android.app.Application
import android.os.Build
import app.messages.core.prefs.AppPrefs
import app.messages.monitoring.CrashReporter
import app.messages.notifications.NotificationChannels
import app.messages.utility.ThemeUtils
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.android.material.color.DynamicColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import app.messages.ui.videodownloader.VideoDownloaderEngineHelper

/**
 * App entry. Wires Hilt and opts into Material You dynamic color where the
 * device supports it (Android 12+, Pixel/OEM-dependent).
 *
 * Compose reads dynamic color directly via `dynamicLight/DarkColorScheme(ctx)`,
 * but [DynamicColors.applyToActivitiesIfAvailable] ensures any non-Compose
 * Activity we ever add (e.g. WebView wrapper, Play billing UI surfaces) also
 * harmonises with the user's wallpaper palette.
 */
@HiltAndroidApp
class MessagesApplication : Controller(), ImageLoaderFactory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MonitoringEntryPoint {
        fun crashReporter(): CrashReporter
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrefsEntryPoint {
        fun appPrefs(): AppPrefs
    }

    /** App-wide Coil loader with GIF decoding so Tenor + MMS gifs animate. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Install crash capture first so it covers everything after Hilt is ready.
        EntryPointAccessors.fromApplication(this, MonitoringEntryPoint::class.java)
            .crashReporter()
            .install()
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Create the notification channels up front so they're visible (and
        // user-configurable) in system settings before the first message.
        NotificationChannels.ensure(this)
        observeThemeMode()
        initFlutterEngine()
        // Loads the install source saved on an earlier launch; the first-launch check runs from SplashActivity.
        app.messages.attribution.InstallSourceManager.init(this)
    }

    private fun initFlutterEngine() {
        try {
            io.flutter.FlutterInjector.instance().flutterLoader().startInitialization(this)
            io.flutter.FlutterInjector.instance().flutterLoader().ensureInitializationComplete(this, null)

            // FlutterEngine(context) auto-registers plugins (GeneratedPluginRegistrant).
            val flutterEngine = FlutterEngine(this)
            flutterEngine.dartExecutor.executeDartEntrypoint(
                DartExecutor.DartEntrypoint.createDefault()
            )
            VideoDownloaderEngineHelper.initEngine(this, flutterEngine)
            FlutterEngineCache.getInstance().put(VideoDownloaderEngineHelper.ENGINE_ID, flutterEngine)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * Keeps the View-based half of the app (launcher home, app drawer, dashboard
     * widgets, chat) on the same appearance the user picked in Settings. The
     * choice is stored in DataStore and read reactively by Compose; this mirrors
     * every change into AppCompat's night mode so both halves stay in step —
     * light stays light and dark stays dark everywhere, and "System" follows the
     * device.
     */
    private fun observeThemeMode() {
        val appPrefs = EntryPointAccessors
            .fromApplication(this, PrefsEntryPoint::class.java)
            .appPrefs()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            appPrefs.themeMode
                .distinctUntilChanged()
                .collect { mode -> ThemeUtils.applyThemeMode(this@MessagesApplication, mode) }
        }
    }
}
