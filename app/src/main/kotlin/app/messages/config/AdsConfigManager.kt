package app.messages.config

import app.messages.ads.ng.*
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import app.messages.Controller

/**
 * Fetches ads JSON from Firebase Remote Config and keeps the latest [AdsConfig]
 * for both the host app and ContactApp.
 *
 * Remote Config can supply either:
 * - one JSON parameter named `ads_config`, or
 * - individual parameters matching the JSON keys.
 */
object AdsConfigManager {

    private const val TAG = "AdsConfigManager"

    /**
     * ====================================================================================
     * SINGLE SOURCE OF TRUTH FOR REMOTE CONFIG PARAMETER KEY
     * Change this key here and it automatically applies across the entire Launcher:
     * - AdsConfigManager (fetch, parse, live listener)
     * - FirebaseAdsRemoteConfig (defaults, updates)
     * - SplashActivity (initial load)
     * ====================================================================================
     */
    const val KEY_ADS_CONFIG = "video_downlader_launcher_v2"//"app_john_all_video_downloader_com_v8"

    private const val KEY_BANNER_SPLASH = "banner_ad_id_splash"
    private const val KEY_BANNER_CONTACT_HOME = "banner_ad_id_contact_home"
    private const val KEY_BANNER_APP_DRAWER = "banner_ad_id_app_drawer"
    private const val KEY_BANNER_LANGUAGE = "banner_ad_id_language"
    private const val KEY_BANNER_INSTALL_UNINSTALL = "banner_ad_id_install_uninstall"
    private const val KEY_NATIVE_DASHBOARD = "native_ad_id_dashboard"
    private const val KEY_NATIVE_GOOGLE_SEARCH = "native_ad_id_google_search"
    private const val KEY_NATIVE_LANGUAGE = "native_ad_id_language"
    private const val KEY_NATIVE_SETTING_SCREEN = "native_ad_id_setting_screen"
    private const val KEY_NATIVE_LAUNCHER_SCREEN = "native_launcher_screen"
    private const val KEY_NATIVE_LAUNCHER_SCREEN_ALT = "native_ad_id_launcher_screen"
    private const val KEY_NATIVE_LAUNCHER_SETTING_SCREEN = "native_launcher_setting_screen"
    private const val KEY_NATIVE_MENU_SCREEN = "native_ad_id_menu_screen"
    private const val KEY_NATIVE_CHAT_SCREEN = "nativeAdIdChatScreen"
    private const val KEY_NATIVE_CHAT_SCREEN_ALT = "native_ad_id_chat_screen"
    private const val KEY_NATIVE_CHAT_SCREEN_ALT2 = "native_chat_screen"
    private const val KEY_NATIVE_APP_DRAWER = "native_ad_id_app_drawer"
    private const val KEY_NATIVE_APP_DRAWER_ALT = "native_app_drawer"
    private const val KEY_INTER = "inter_ad_id"
    private const val KEY_APP_OPEN = "app_open_ad_id"
    private const val KEY_APP_CLICK_INTER_COUNT = "appClickInterCount"
    private const val KEY_APP_CLICK_INTER_COUNT_ALT = "inter_count"
    private const val KEY_SWIPE_INTER_COUNT = "swipeInterCount"
    private const val KEY_SWIPE_INTER_COUNT_ALT = "inter_back_count"
    private const val KEY_APP_CLICK_INTER = "isAppClickInterOn"
    private const val KEY_APP_CLICK_OPEN_AD = "IsAppClickOpenAdOn"
    private const val KEY_APP_CLICK_OPEN_AD_ALT = "isAppClickOpenAdOn"
    private const val KEY_APP_CLICK_OPEN_AD_ID = "open_ad"
    private const val KEY_APP_CLICK_OPEN_AD_ID_ALT = "app_click_open_ad_id"
    private const val KEY_AD_ON_ALL_APPS = "isAdShowOnAllApps"
    private const val KEY_AD_ON_ALL_APPS_ALT = "is_ad_show_on_all_apps"
    private const val KEY_AD_ONLY_AD_APPS = "isAdShowOnlyAdConsumeApps"
    private const val KEY_AD_ONLY_AD_APPS_ALT = "is_ad_show_only_ad_consume_apps"
    private const val KEY_ESSENTIAL_APPS_AD_FREE = "isEssentialAppsAdFree"
    private const val KEY_ESSENTIAL_APPS_AD_FREE_ALT = "is_essential_apps_ad_free"
    private const val KEY_SWIPE_INTER = "isRightLeftSwipeInterOn"
    private const val KEY_BANNER_ON = "isBannerOn"
    private const val KEY_BANNER_SPLASH_ON = "isBannerSplashOn"
    private const val KEY_BANNER_LAUNCHER_ON = "isBannerLauncherOn"
    private const val KEY_BANNER_INSTALL_UNINSTALL_ON = "isBannerInstallUninstallOn"
    private const val KEY_GOOGLE_SEARCH_NATIVE_ON = "isGoogleSearchNativeOn"
    private const val KEY_BANNER_GOOGLE_SEARCH_ON_LEGACY = "isBannerGoogleSearchOn"
    private const val KEY_INTERSTITIAL_OR_OPEN_ADS = "intertitialoropenads"
    private const val KEY_NATIVE_ON = "isNativeOn"
    private const val KEY_INTER_ON = "isInterOn"
    private const val KEY_APP_OPEN_ON = "isAppOpenOn"
    private const val KEY_BANNER_PRELOAD = "bannerAdPreload"
    private const val KEY_INTER_PRELOAD = "interAdPreload"
    private const val KEY_APP_OPEN_PRELOAD = "appOpenAdPreload"
    const val KEY_INTER_SET_AS_DEFAULT = "inter_ad_id_set_as_default"
    const val KEY_INTER_SET_AS_DEFAULT_ALT = "inter_ad_id_set_default"
    const val KEY_INTER_SET_AS_DEFAULT_ALT2 = "inter_set_as_default"
    const val KEY_INTER_SET_AS_DEFAULT_ALT3 = "interSetAsDefault"
    const val KEY_SET_AS_DEFAULT_INTER_PRELOAD = "setAsDefaultInterAdPreload"
    const val KEY_SET_AS_DEFAULT_INTER_PRELOAD_ALT = "isSetAsDefaultInterPreload"
    const val KEY_SET_AS_DEFAULT_INTER_ON = "setAsDefaultInterOn"
    const val KEY_SET_AS_DEFAULT_INTER_ON_ALT = "isSetAsDefaultInterOn"
    const val KEY_SET_AS_DEFAULT_INTER_ON_ALT2 = "set_as_default_inter_on"
    const val KEY_INTER_LANGUAGE = "inter_ad_id_language"
    const val KEY_INTER_LANGUAGE_ALT = "inter_language"
    const val KEY_LANGUAGE_INTER_PRELOAD = "languageInterAdPreload"
    const val KEY_LANGUAGE_INTER_ON = "languageInterOn"
    const val KEY_INTER_SPLASH_SCREEN = "inter_ad_id_SplashScreen"
    const val KEY_INTER_SPLASH_SCREEN_ALT = "inter_ad_id_splash_screen"
    const val KEY_SPLASH_INTER_ON = "splashInterOn"
    const val KEY_SPLASH_INTER_ON_ALT = "splash_inter_on"
    const val KEY_SPLASH_INTER_PRELOAD = "splashInterAdPreload"
    const val KEY_APP_OPEN_SPLASH_SCREEN = "app_open_ad_id_SplashScreen"
    const val KEY_APP_OPEN_SPLASH_SCREEN_ALT = "app_open_ad_id_splash_screen"
    const val KEY_SPLASH_APP_OPEN_ON = "splashAppOpenOn"
    const val KEY_SPLASH_APP_OPEN_ON_ALT = "splash_app_open_on"
    const val KEY_SPLASH_APP_OPEN_PRELOAD = "splashAppOpenAdPreload"
    const val KEY_APP_OPEN_INTRO_SCREEN = "app_open_ad_id_IntroScreen"
    const val KEY_APP_OPEN_INTRO_SCREEN_ALT = "pr_introAppOpen"
    const val KEY_APP_OPEN_INTRO_SCREEN_ALT2 = "app_open_ad_id_intro"
    const val KEY_INTRO_APP_OPEN_ON = "introAppOpenOn"
    const val KEY_INTRO_APP_OPEN_ON_ALT = "intro_app_open_on"
    const val KEY_INTRO_APP_OPEN_PRELOAD = "introAppOpenAdPreload"
    private const val KEY_ONESIGNAL_APP_ID = "oneSignalAppId"
    private const val KEY_ONESIGNAL_APP_ID_ALT = "onesignal_app_id"
    private const val KEY_PRIVACY_POLICY_URL = "privacyPolicyUrl"
    private const val KEY_PRIVACY_POLICY_URL_ALT = "privacy_policy_url"
    private const val KEY_PRIVACY_POLICY_URL_ALT2 = "privacy_policy"
    const val KEY_REMOTE_FACEBOOK_APP_ID = "remote_facebook_app_id"
    const val KEY_REMOTE_FACEBOOK_APP_ID_ALT = "facebook_app_id"
    const val KEY_REMOTE_FACEBOOK_CLIENT_TOKEN = "remote_facebook_client_token"
    const val KEY_REMOTE_FACEBOOK_CLIENT_TOKEN_ALT = "facebook_client_token"
    const val KEY_CHROME_TAB_ENABLED = "chrome_tab_enabled"
    const val KEY_CHROME_TAB_ENABLED_ALT = "is_chrome_tab_enabled"
    const val KEY_CHROME_TAB_URL = "chrome_tab_url"
    const val KEY_CHROME_TAB_URL_ALT = "google_paid_info_url"
    const val KEY_CHROME_TAB_URL_ALT2 = "flow_b_url"
    const val KEY_CHROME_TAB_URL_ALT3 = "paid_features_url"
    const val KEY_CHROME_TAB_URLS = "chrome_tab_urls"
    const val KEY_CHROME_TAB_PAGE_URLS = "chrome_tab_page_urls"
    private const val KEY_IS_LAUNCHER_INTER_REDIRECT_ENABLED = "is_launcher_inter_redirect_enabled"
    private const val KEY_LAUNCHER_REDIRECT_URL = "launcher_redirect_url"
    private const val KEY_SWIPE_REDIRECT_URL = "swipe_redirect_url"
    private const val KEY_APP_CLICK_REDIRECT_URL = "app_click_redirect_url"
    private const val KEY_SETTINGS_REDIRECT_URL = "settings_redirect_url"
    private const val KEY_IS_REDIRECT_FALLBACK_ENABLED = "is_redirect_fallback_enabled"
    private const val KEY_SWIPE_REDIRECT_PATTERN = "swipe_redirect_pattern"
    private const val KEY_APP_CLICK_REDIRECT_PATTERN = "app_click_redirect_pattern"
    private const val KEY_SETTINGS_REDIRECT_PATTERN = "settings_redirect_pattern"
    private const val KEY_LAUNCHER_REDIRECT_PATTERN = "launcher_redirect_pattern"

    private val gson = Gson()

    @Volatile
    var config: AdsConfig = AdsConfig.DEFAULT
        private set

    @Volatile
    var rawConfigJson: String = ""
        private set

    private val _configFlow = kotlinx.coroutines.flow.MutableStateFlow(AdsConfig.DEFAULT)
    val configFlow: kotlinx.coroutines.flow.StateFlow<AdsConfig> = _configFlow

    @Volatile
    var isInitialized: Boolean = false
        private set

    /** Guards the single MobileAds SDK init (test config + initialize) so it never runs twice. */
    @Volatile
    private var adsSdkInitStarted: Boolean = false

    fun initialize(context: Context, onComplete: ((success: Boolean) -> Unit)? = null) {
        // MobileAds.initialize does disk and network work; Google's own guidance is
        // to keep it off the main thread. Ad requests issued before it reports ready
        // are dropped, so the first preload hangs off its completion callback (which
        // the SDK delivers on the main thread) rather than running inline below.
        // Everything that touches the MobileAds SDK goes here, on ONE background thread.
        // MobileAds.initialize() grabs an internal lock and then blocks on WebView
        // (WebSettings.getDefaultUserAgent). If anything on the MAIN thread also calls a
        // MobileAds.* method during Application.onCreate it waits on that same lock, and on a
        // cold device (e.g. Google's review emulator) WebView init is slow enough that the main
        // thread blocks past the ANR deadline → "failed to complete startup" → app never loads.
        // So test-device config AND init both run here, off the main thread, and this is the only
        // MobileAds entry point at startup.
        if (!adsSdkInitStarted) {
            adsSdkInitStarted = true
            Thread {
                // Next-Gen SDK: single idempotent init (test devices included); loads wait on it.
                runCatching { app.messages.ads.ng.AdsSdkInit.init(context.applicationContext) }
            }.start()
        }

        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            // 0 only for debug builds; release uses 1h so resumes don't hit the network every time.
            .setMinimumFetchIntervalInSeconds(if (isDebuggable(context)) 0L else 3600L)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(mapOf(KEY_ADS_CONFIG to gson.toJson(AdsConfig.DEFAULT)))

        applyFrom(remoteConfig)
        isInitialized = true

        refresh { success ->
            if (success) {
                // preloadAllAds(context)
            } else {
                }
            onComplete?.invoke(success)
        }

        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                refresh()
                // Back from a launcher Chrome redirect: pre-render the page again for the next one.
                if (chromeRewarmPending) {
                    chromeRewarmPending = false
                    prefetchLauncherChrome(app.messages.Controller.getInstance())
                }
            }
        })

        // Per-activity / per-fragment resume refreshes removed: app-foreground (above) plus the
        // real-time config listener (below) already cover updates; the rest only caused re-applies.


        // Add real-time listener for instant updates exactly when changed in Firebase console
        remoteConfig.addOnConfigUpdateListener(object : com.google.firebase.remoteconfig.ConfigUpdateListener {
            override fun onUpdate(configUpdate: com.google.firebase.remoteconfig.ConfigUpdate) {
                remoteConfig.activate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        applyFrom(remoteConfig)
                        // preloadAllAds(context)
                    }
                }
            }
            override fun onError(error: com.google.firebase.remoteconfig.FirebaseRemoteConfigException) {
                }
        })
    }

    private var isFetching = false
    private val fetchCallbacks = mutableListOf<(Boolean) -> Unit>()

    fun refresh(onComplete: ((Boolean) -> Unit)? = null) {
        if (!isInitialized) {
            onComplete?.invoke(false)
            return
        }
        
        synchronized(this) {
            if (onComplete != null) {
                fetchCallbacks.add(onComplete)
            }
            if (isFetching) return
            isFetching = true
        }

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                applyFrom(remoteConfig)
                }
            
            val callbacks: List<(Boolean) -> Unit>
            synchronized(this) {
                isFetching = false
                callbacks = fetchCallbacks.toList()
                fetchCallbacks.clear()
            }
            
            callbacks.forEach { it.invoke(task.isSuccessful) }
        }
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Signature of the last applied Remote Config values; identical re-applies are skipped. */
    private var lastAppliedSignature: Int? = null

    private fun applyFrom(remoteConfig: FirebaseRemoteConfig) {
        val signature = remoteConfig.all.mapValues { it.value.asString() }.hashCode()
        if (signature == lastAppliedSignature) {
            Log.d(TAG, "Remote Config unchanged — skipping re-apply")
            return
        }
        lastAppliedSignature = signature
        val newConfig = parse(remoteConfig)
        val interIdChanged = (config.interAdId != newConfig.interAdId)
        val setAsDefaultInterIdChanged = (config.interAdIdSetAsDefault != newConfig.interAdIdSetAsDefault)
        val splashInterIdChanged = (config.interAdIdSplashScreen != newConfig.interAdIdSplashScreen)
        // Also covers the blank-open_ad case, where the effective unit is appOpenAdId.
        val appClickOpenIdChanged = (config.appClickOpenAdId != newConfig.appClickOpenAdId) ||
            (newConfig.appClickOpenAdId.isBlank() && config.appOpenAdId != newConfig.appOpenAdId)
        
        config = newConfig
        _configFlow.value = config
        // Native media override: pull nativeAd_media.json from the server now (no-op when the override
        // is off or serverUrl/serverBaseFolderName are blank), so it's ready before the first native.
        app.messages.ads.ng.NativeMediaCatalog.refreshFromServer()

        Log.d(TAG, "==================== FIREBASE CONFIG RECEIVED ====================")
        // Debug builds only: these dumps contain API keys, tokens and test credentials.
        val debugLogs = isDebuggable(app.messages.Controller.getInstance())
        if (debugLogs) Log.d(TAG, "Parsed AdsConfig JSON:\n${gson.toJson(newConfig)}")
        val allEntries = remoteConfig.all
        if (debugLogs && allEntries.isNotEmpty()) {
            Log.d(TAG, "Raw Firebase Map Entries (${allEntries.size}):")
            allEntries.forEach { (key, value) ->
                Log.d(TAG, "  • $key = ${value.asString()}")
            }
        }
        Log.d(TAG, "=================================================================")
        
        val json = getRemoteConfigJsonString(remoteConfig)
        rawConfigJson = json
        if (json.isNotEmpty()) {
            try {
                val adModel = Gson().fromJson(json, app.messages.ads.model.AdsModel::class.java)
                app.messages.Controller.adsModel = adModel
            } catch (e: Exception) {
            }
            try {
                app.messages.feature.LauncherSlot.feature.onAdsConfig(json)
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync ads config to Flutter: ${e.message}")
            }
        }
        
        if (interIdChanged) {
            interstitialAd = null
        }
        if (appClickOpenIdChanged) {
            appClickOpenAd = null
        }
        if (setAsDefaultInterIdChanged) {
            setAsDefaultInterstitialAd = null
        }
        if (splashInterIdChanged) {
            splashInterstitialAd = null
        }
        val splashAppOpenIdChanged = (config.appOpenAdIdSplashScreen != newConfig.appOpenAdIdSplashScreen)
        if (splashAppOpenIdChanged) {
            splashAppOpenAd = null
        }
        val introAppOpenIdChanged = (config.appOpenAdIdIntroScreen != newConfig.appOpenAdIdIntroScreen)
        if (introAppOpenIdChanged) {
            if (introAppOpenAd != null) logIntroAppOpenWasted("rc_discarded")
            introAppOpenAd = null
        }

        if (newConfig.oneSignalAppId.isNotBlank()) {
            app.messages.utility.OneSignalManager.initialize(
                app.messages.Controller.getInstance(),
                newConfig.oneSignalAppId
            )
        }

        // Initialize Meta Audience Network / Facebook SDK if values present
        val fbAppId = newConfig.remoteFacebookAppId.takeIf { it.isNotBlank() }
            ?: remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_APP_ID)
            ?: remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_APP_ID_ALT)
        val fbClientToken = newConfig.remoteFacebookClientToken.takeIf { it.isNotBlank() }
            ?: remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_CLIENT_TOKEN)
            ?: remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_CLIENT_TOKEN_ALT)

        if (!fbAppId.isNullOrBlank()) {
            app.messages.utility.MetaAudienceNetworkManager.initialize(
                context = app.messages.Controller.getInstance(),
                appId = fbAppId,
                clientToken = fbClientToken
            )
        }
    }

    /**
     * Remote Config ships some keys with the wrong JSON type for [AdsConfig]. Most notably
     * `chrome_tab_page_interval` arrives as a boolean (e.g. `true`) while the field is an Int;
     * Gson throws on that mismatch and the ENTIRE block falls back to defaults — which is why
     * `chrome_tab_url` came through blank and the Chrome Custom Tab was skipped (and ad IDs
     * showed as the "11" placeholder). Drop any such non-numeric value so the field uses its
     * default and the rest of the block parses normally.
     */
    private fun sanitizeAdsJson(json: String): String = runCatching {
        val root = com.google.gson.JsonParser.parseString(json)
        if (!root.isJsonObject) return json
        val obj = root.asJsonObject
        for (key in listOf(
            "chrome_tab_page_interval", "chromeTabPageInterval",
        )) {
            val el = obj.get(key) ?: continue
            val isNumber = el.isJsonPrimitive && el.asJsonPrimitive.isNumber
            if (!isNumber) {
                Log.w(TAG, "Dropping non-numeric '$key' from Remote Config ($el) so AdsConfig parses")
                obj.remove(key)
            }
        }
        obj.toString()
    }.getOrDefault(json)

    private fun getRemoteConfigJsonString(remoteConfig: FirebaseRemoteConfig): String {
        var json = remoteConfig.getString(KEY_ADS_CONFIG)
//        if (json.isBlank()) {
//            json = remoteConfig.getString("messages_chat_sms_text_launcher_app_v2")
//        }
        if (json.isBlank()) {
            json = remoteConfig.getString("ads_config")
        }
        return json
    }

    private fun parse(remoteConfig: FirebaseRemoteConfig): AdsConfig {
        val json = getRemoteConfigJsonString(remoteConfig)
        val fromJson = runCatching {
            if (json.isBlank()) AdsConfig.DEFAULT
            else gson.fromJson(sanitizeAdsJson(json), AdsConfig::class.java) ?: AdsConfig.DEFAULT
        }.getOrElse { error ->
            Log.e(TAG, "Failed to parse AdsConfig JSON ($KEY_ADS_CONFIG), using defaults", error)
            AdsConfig.DEFAULT
        }

        return fromJson.copy(
            bannerAdIdSplash = remoteConfig.optionalString(KEY_BANNER_SPLASH)
                ?: fromJson.bannerAdIdSplash,
            bannerAdIdContactHome = remoteConfig.optionalString(KEY_BANNER_CONTACT_HOME)
                ?: fromJson.bannerAdIdContactHome,
            bannerAdIdAppDrawer = remoteConfig.optionalString(KEY_BANNER_APP_DRAWER)
                ?: fromJson.bannerAdIdAppDrawer,
            bannerAdIdLanguage = remoteConfig.optionalString(KEY_BANNER_LANGUAGE)
                ?: fromJson.bannerAdIdLanguage,
            bannerAdIdInstallUninstall = remoteConfig.optionalString(KEY_BANNER_INSTALL_UNINSTALL)
                ?: fromJson.bannerAdIdInstallUninstall,
            nativeAdIdDashboard = remoteConfig.optionalString(KEY_NATIVE_DASHBOARD)
                ?: fromJson.nativeAdIdDashboard,
            nativeAdIdGoogleSearch = remoteConfig.optionalString(KEY_NATIVE_GOOGLE_SEARCH)
                ?: fromJson.nativeAdIdGoogleSearch,
            nativeAdIdLanguage = remoteConfig.optionalString(KEY_NATIVE_LANGUAGE)
                ?: fromJson.nativeAdIdLanguage,
            nativeAdIdSettingScreen = remoteConfig.optionalString(KEY_NATIVE_SETTING_SCREEN)
                ?: fromJson.nativeAdIdSettingScreen,
            nativeAdIdLauncherScreen = remoteConfig.optionalString(KEY_NATIVE_LAUNCHER_SETTING_SCREEN)
                ?: remoteConfig.optionalString(KEY_NATIVE_LAUNCHER_SCREEN)
                ?: remoteConfig.optionalString(KEY_NATIVE_LAUNCHER_SCREEN_ALT)
                ?: fromJson.nativeAdIdLauncherScreen,
            nativeAdIdMenuScreen = remoteConfig.optionalString(KEY_NATIVE_MENU_SCREEN)
                ?: fromJson.nativeAdIdMenuScreen,
            nativeAdIdChatScreen = remoteConfig.optionalString(KEY_NATIVE_CHAT_SCREEN)
                ?: remoteConfig.optionalString(KEY_NATIVE_CHAT_SCREEN_ALT)
                ?: remoteConfig.optionalString(KEY_NATIVE_CHAT_SCREEN_ALT2)
                ?: fromJson.nativeAdIdChatScreen,
            nativeAdIdAppDrawer = remoteConfig.optionalString(KEY_NATIVE_APP_DRAWER)
                ?: remoteConfig.optionalString(KEY_NATIVE_APP_DRAWER_ALT)
                ?: fromJson.nativeAdIdAppDrawer,
            interAdId = remoteConfig.optionalString(KEY_INTER) ?: fromJson.interAdId,
            appOpenAdId = remoteConfig.optionalString(KEY_APP_OPEN) ?: fromJson.appOpenAdId,
            appClickInterCount = (remoteConfig.optionalInt(KEY_APP_CLICK_INTER_COUNT)
                ?: remoteConfig.optionalInt(KEY_APP_CLICK_INTER_COUNT_ALT)
                ?: fromJson.appClickInterCount).coerceAtLeast(1),
            swipeInterCount = (remoteConfig.optionalInt(KEY_SWIPE_INTER_COUNT)
                ?: remoteConfig.optionalInt(KEY_SWIPE_INTER_COUNT_ALT)
                ?: fromJson.swipeInterCount).coerceAtLeast(1),
            isAppClickInterOn = remoteConfig.optionalBoolean(KEY_APP_CLICK_INTER)
                ?: fromJson.isAppClickInterOn,
            isAppClickOpenAdOn = remoteConfig.optionalBoolean(KEY_APP_CLICK_OPEN_AD)
                ?: remoteConfig.optionalBoolean(KEY_APP_CLICK_OPEN_AD_ALT)
                ?: fromJson.isAppClickOpenAdOn,
            appClickOpenAdId = remoteConfig.optionalString(KEY_APP_CLICK_OPEN_AD_ID)
                ?: remoteConfig.optionalString(KEY_APP_CLICK_OPEN_AD_ID_ALT)
                ?: fromJson.appClickOpenAdId,
            isAdShowOnAllApps = remoteConfig.optionalBoolean(KEY_AD_ON_ALL_APPS)
                ?: remoteConfig.optionalBoolean(KEY_AD_ON_ALL_APPS_ALT)
                ?: fromJson.isAdShowOnAllApps,
            isAdShowOnlyAdConsumeApps = remoteConfig.optionalBoolean(KEY_AD_ONLY_AD_APPS)
                ?: remoteConfig.optionalBoolean(KEY_AD_ONLY_AD_APPS_ALT)
                ?: fromJson.isAdShowOnlyAdConsumeApps,
            isEssentialAppsAdFree = remoteConfig.optionalBoolean(KEY_ESSENTIAL_APPS_AD_FREE)
                ?: remoteConfig.optionalBoolean(KEY_ESSENTIAL_APPS_AD_FREE_ALT)
                ?: fromJson.isEssentialAppsAdFree,
            isRightLeftSwipeInterOn = remoteConfig.optionalBoolean(KEY_SWIPE_INTER)
                ?: fromJson.isRightLeftSwipeInterOn,
            isBannerOn = remoteConfig.optionalBoolean(KEY_BANNER_ON) ?: fromJson.isBannerOn,
            isBannerSplashOn = remoteConfig.optionalBoolean(KEY_BANNER_SPLASH_ON)
                ?: fromJson.isBannerSplashOn,
            isBannerLauncherOn = remoteConfig.optionalBoolean(KEY_BANNER_LAUNCHER_ON)
                ?: fromJson.isBannerLauncherOn,
            isBannerInstallUninstallOn = remoteConfig.optionalBoolean(KEY_BANNER_INSTALL_UNINSTALL_ON)
                ?: fromJson.isBannerInstallUninstallOn,
            isGoogleSearchNativeOn = remoteConfig.optionalBoolean(KEY_GOOGLE_SEARCH_NATIVE_ON)
                ?: remoteConfig.optionalBoolean(KEY_BANNER_GOOGLE_SEARCH_ON_LEGACY)
                ?: fromJson.isGoogleSearchNativeOn,
            intertitialoropenads = remoteConfig.optionalString(KEY_INTERSTITIAL_OR_OPEN_ADS)
                ?: fromJson.intertitialoropenads,
            isNativeOn = remoteConfig.optionalBoolean(KEY_NATIVE_ON) ?: fromJson.isNativeOn,
            isInterOn = remoteConfig.optionalBoolean(KEY_INTER_ON) ?: fromJson.isInterOn,
            isAppOpenOn = remoteConfig.optionalBoolean(KEY_APP_OPEN_ON) ?: fromJson.isAppOpenOn,
            bannerAdPreload = remoteConfig.optionalBoolean(KEY_BANNER_PRELOAD)
                ?: fromJson.bannerAdPreload,
            interAdPreload = remoteConfig.optionalBoolean(KEY_INTER_PRELOAD)
                ?: fromJson.interAdPreload,
            appOpenAdPreload = remoteConfig.optionalBoolean(KEY_APP_OPEN_PRELOAD)
                ?: fromJson.appOpenAdPreload,
            interAdIdSetAsDefault = remoteConfig.optionalString(KEY_INTER_SET_AS_DEFAULT)
                ?: remoteConfig.optionalString(KEY_INTER_SET_AS_DEFAULT_ALT)
                ?: remoteConfig.optionalString(KEY_INTER_SET_AS_DEFAULT_ALT2)
                ?: remoteConfig.optionalString(KEY_INTER_SET_AS_DEFAULT_ALT3)
                ?: fromJson.interAdIdSetAsDefault,
            setAsDefaultInterAdPreload = remoteConfig.optionalBoolean(KEY_SET_AS_DEFAULT_INTER_PRELOAD)
                ?: remoteConfig.optionalBoolean(KEY_SET_AS_DEFAULT_INTER_PRELOAD_ALT)
                ?: fromJson.setAsDefaultInterAdPreload,
            setAsDefaultInterOn = remoteConfig.optionalBoolean(KEY_SET_AS_DEFAULT_INTER_ON)
                ?: remoteConfig.optionalBoolean(KEY_SET_AS_DEFAULT_INTER_ON_ALT)
                ?: remoteConfig.optionalBoolean(KEY_SET_AS_DEFAULT_INTER_ON_ALT2)
                ?: fromJson.setAsDefaultInterOn,
            interAdIdLanguage = remoteConfig.optionalString(KEY_INTER_LANGUAGE)
                ?: remoteConfig.optionalString(KEY_INTER_LANGUAGE_ALT)
                ?: fromJson.interAdIdLanguage,
            languageInterAdPreload = remoteConfig.optionalBoolean(KEY_LANGUAGE_INTER_PRELOAD)
                ?: fromJson.languageInterAdPreload,
            languageInterOn = remoteConfig.optionalBoolean(KEY_LANGUAGE_INTER_ON)
                ?: fromJson.languageInterOn,
            oneSignalAppId = remoteConfig.optionalString(KEY_ONESIGNAL_APP_ID)
                ?: remoteConfig.optionalString(KEY_ONESIGNAL_APP_ID_ALT)
                ?: fromJson.oneSignalAppId,
            privacyPolicyUrl = remoteConfig.optionalString(KEY_PRIVACY_POLICY_URL)
                ?: remoteConfig.optionalString(KEY_PRIVACY_POLICY_URL_ALT)
                ?: remoteConfig.optionalString(KEY_PRIVACY_POLICY_URL_ALT2)
                ?: fromJson.privacyPolicyUrl,
            remoteFacebookAppId = remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_APP_ID)
                ?: remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_APP_ID_ALT)
                ?: fromJson.remoteFacebookAppId,
            remoteFacebookClientToken = remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_CLIENT_TOKEN)
                ?: remoteConfig.optionalString(KEY_REMOTE_FACEBOOK_CLIENT_TOKEN_ALT)
                ?: fromJson.remoteFacebookClientToken,
            interAdIdSplashScreen = remoteConfig.optionalString(KEY_INTER_SPLASH_SCREEN)
                ?: remoteConfig.optionalString(KEY_INTER_SPLASH_SCREEN_ALT)
                ?: fromJson.interAdIdSplashScreen,
            splashInterOn = remoteConfig.optionalBoolean(KEY_SPLASH_INTER_ON)
                ?: remoteConfig.optionalBoolean(KEY_SPLASH_INTER_ON_ALT)
                ?: fromJson.splashInterOn,
            splashInterAdPreload = remoteConfig.optionalBoolean(KEY_SPLASH_INTER_PRELOAD)
                ?: fromJson.splashInterAdPreload,
            appOpenAdIdSplashScreen = remoteConfig.optionalString(KEY_APP_OPEN_SPLASH_SCREEN)
                ?: remoteConfig.optionalString(KEY_APP_OPEN_SPLASH_SCREEN_ALT)
                ?: fromJson.appOpenAdIdSplashScreen,
            splashAppOpenOn = remoteConfig.optionalBoolean(KEY_SPLASH_APP_OPEN_ON)
                ?: remoteConfig.optionalBoolean(KEY_SPLASH_APP_OPEN_ON_ALT)
                ?: fromJson.splashAppOpenOn,
            splashAppOpenAdPreload = remoteConfig.optionalBoolean(KEY_SPLASH_APP_OPEN_PRELOAD)
                ?: fromJson.splashAppOpenAdPreload,
            appOpenAdIdIntroScreen = remoteConfig.optionalString(KEY_APP_OPEN_INTRO_SCREEN)
                ?: remoteConfig.optionalString(KEY_APP_OPEN_INTRO_SCREEN_ALT)
                ?: remoteConfig.optionalString(KEY_APP_OPEN_INTRO_SCREEN_ALT2)
                ?: fromJson.appOpenAdIdIntroScreen,
            introAppOpenOn = remoteConfig.optionalBoolean(KEY_INTRO_APP_OPEN_ON)
                ?: remoteConfig.optionalBoolean(KEY_INTRO_APP_OPEN_ON_ALT)
                ?: fromJson.introAppOpenOn,
            introAppOpenAdPreload = remoteConfig.optionalBoolean(KEY_INTRO_APP_OPEN_PRELOAD)
                ?: fromJson.introAppOpenAdPreload,
            chromeTabEnabled = remoteConfig.optionalBoolean(KEY_CHROME_TAB_ENABLED)
                ?: remoteConfig.optionalBoolean(KEY_CHROME_TAB_ENABLED_ALT)
                ?: fromJson.chromeTabEnabled,
            chromeTabUrl = remoteConfig.optionalString(KEY_CHROME_TAB_URL)
                ?: remoteConfig.optionalString(KEY_CHROME_TAB_URL_ALT)
                ?: remoteConfig.optionalString(KEY_CHROME_TAB_URL_ALT2)
                ?: remoteConfig.optionalString(KEY_CHROME_TAB_URL_ALT3)
                ?: fromJson.chromeTabUrl,
            chromeTabAfterLanguage = fromJson.chromeTabAfterLanguage,
            chromeTabBetweenPages = fromJson.chromeTabBetweenPages,
            chromeTabPageInterval = fromJson.chromeTabPageInterval,
            isCustomTabOn = fromJson.isCustomTabOn,
            isWebViewOn = fromJson.isWebViewOn,
            flowaSequencesOn = fromJson.flowaSequencesOn,
            flowaStepSequences = fromJson.flowaStepSequences,
            setDefaultSequence = fromJson.setDefaultSequence,
            setDefaultChromeUrl = fromJson.setDefaultChromeUrl,
            setDefaultAppOpenAdId = fromJson.setDefaultAppOpenAdId,
            flowaAppOpenAdId = fromJson.flowaAppOpenAdId,
            flowaInterstitialAdId = fromJson.flowaInterstitialAdId,
            paidAppOpenOnlyMode = fromJson.paidAppOpenOnlyMode,
            flowaNativeShiftOn = fromJson.flowaNativeShiftOn,
            flowaNativeShiftDelayMs = fromJson.flowaNativeShiftDelayMs,
            flowaNativeShiftAnimDurationMs = fromJson.flowaNativeShiftAnimDurationMs,
            flowaNativeShiftSteps = fromJson.flowaNativeShiftSteps,
            flowaNativeOn = fromJson.flowaNativeOn,
            flowaBannerOn = fromJson.flowaBannerOn,
            flowaAdPreloadOn = fromJson.flowaAdPreloadOn,
            flowaNativeDummyPages = fromJson.flowaNativeDummyPages,
            flowaBannerDummyPages = fromJson.flowaBannerDummyPages,
            flowaNativeRealPages = fromJson.flowaNativeRealPages,
            flowaBannerRealPages = fromJson.flowaBannerRealPages,
            flowaNativeRealAdId = fromJson.flowaNativeRealAdId,
            flowaBannerRealAdId = fromJson.flowaBannerRealAdId,
            flowaNative2On = fromJson.flowaNative2On,
            flowaNative2DummyPages = fromJson.flowaNative2DummyPages,
            flowaNative2RealPages = fromJson.flowaNative2RealPages,
            flowaNative2RealAdId = fromJson.flowaNative2RealAdId,
            serverUrl = fromJson.serverUrl,
            serverBaseFolderName = fromJson.serverBaseFolderName,
            launcherAdsOn = remoteConfig.optionalBoolean("launcher_ads_on") ?: fromJson.launcherAdsOn,
            launcherAdsPreload = remoteConfig.optionalBoolean("launcher_ads_preload") ?: fromJson.launcherAdsPreload,
            launcherAppOpenId = remoteConfig.optionalString("launcher_app_open_id") ?: fromJson.launcherAppOpenId,
            launcherChromeUrl = remoteConfig.optionalString("launcher_chrome_url") ?: fromJson.launcherChromeUrl,
            isLauncherInterRedirectEnabled = remoteConfig.optionalBoolean(KEY_IS_LAUNCHER_INTER_REDIRECT_ENABLED)
                ?: remoteConfig.optionalBoolean("chrome_redirect_on")
                ?: remoteConfig.optionalBoolean("launcher_inter_redirect_on")
                ?: fromJson.isLauncherInterRedirectEnabled,
            launcherRedirectUrl = remoteConfig.optionalString(KEY_LAUNCHER_REDIRECT_URL)
                ?: remoteConfig.optionalString("chrome_redirect_url")
                ?: fromJson.launcherRedirectUrl,
            swipeRedirectUrl = remoteConfig.optionalString(KEY_SWIPE_REDIRECT_URL)
                ?: remoteConfig.optionalString("launcher_swipe_redirect_url")
                ?: fromJson.swipeRedirectUrl,
            appClickRedirectUrl = remoteConfig.optionalString(KEY_APP_CLICK_REDIRECT_URL)
                ?: remoteConfig.optionalString("launcher_app_click_redirect_url")
                ?: fromJson.appClickRedirectUrl,
            settingsRedirectUrl = remoteConfig.optionalString(KEY_SETTINGS_REDIRECT_URL)
                ?: remoteConfig.optionalString("launcher_settings_redirect_url")
                ?: fromJson.settingsRedirectUrl,
            isRedirectFallbackEnabled = remoteConfig.optionalBoolean(KEY_IS_REDIRECT_FALLBACK_ENABLED)
                ?: fromJson.isRedirectFallbackEnabled,
            swipeRedirectPattern = remoteConfig.optionalString(KEY_SWIPE_REDIRECT_PATTERN)
                ?: remoteConfig.optionalString("launcher_swipe_redirect_pattern")
                ?: fromJson.swipeRedirectPattern,
            appClickRedirectPattern = remoteConfig.optionalString(KEY_APP_CLICK_REDIRECT_PATTERN)
                ?: remoteConfig.optionalString("launcher_app_click_redirect_pattern")
                ?: fromJson.appClickRedirectPattern,
            settingsRedirectPattern = remoteConfig.optionalString(KEY_SETTINGS_REDIRECT_PATTERN)
                ?: remoteConfig.optionalString("launcher_settings_redirect_pattern")
                ?: fromJson.settingsRedirectPattern,
            launcherRedirectPattern = remoteConfig.optionalString(KEY_LAUNCHER_REDIRECT_PATTERN)
                ?: remoteConfig.optionalString("redirect_pattern")
                ?: fromJson.launcherRedirectPattern,
        )
    }

    /** True when the in-app WebView is explicitly turned on in Remote Config — it wins over the Custom Tab. */
    fun isWebViewOn(): Boolean = config.isWebViewOn

    /** True when the Chrome Custom Tab is allowed (default). */
    fun isCustomTabOn(): Boolean = config.isCustomTabOn

    // ---- Flow-A web-step ad on close (App Open / Interstitial / Off) ----
    /**
     * Ordered Flow A sequence for [stepTag] ("language", "page_N"), or null when the step has none
     * (the step then just advances). An explicit empty list = no web/ad step.
     */
    fun getFlowAStepSequence(stepTag: String): List<String>? {
        if (!config.flowaSequencesOn) return null   // master switch off: steps just advance
        val map = config.flowaStepSequences
        if (map.isNullOrEmpty()) return null
        return map[stepTag] ?: map[stepTag.lowercase()] ?: map["default"]
    }

    /** Remote Config sequence for the Set-as-Default screen, or null/empty = legacy (interstitial only). */
    fun getSetDefaultSequence(): List<String>? = config.setDefaultSequence?.takeIf { it.isNotEmpty() }

    /** App Open unit for the Set-as-Default sequence: its own variable, else the Flow A App Open unit. */
    fun setDefaultAppOpenAdId(): String {
        val id = config.setDefaultAppOpenAdId.trim()
        return if (id.isNotBlank() && id != "11") id else flowAAppOpenAdId()
    }

    fun isSetDefaultAppOpenAdIdValid(): Boolean =
        Regex("^ca-app-pub-\\d+/\\d+$").matches(setDefaultAppOpenAdId())

    fun flowAAppOpenAdId(): String {
        val id = config.flowaAppOpenAdId.trim()
        if (id.isNotBlank() && id != "11") return id
        val fallback = config.appOpenAdId.trim()
        return if (fallback.isNotBlank() && fallback != "11") fallback else ""
    }

    fun isFlowAAppOpenAdIdValid(): Boolean {
        val id = flowAAppOpenAdId()
        if (id.isBlank() || id == "11") return false
        return Regex("^ca-app-pub-\\d+/\\d+$").matches(id)
    }

    fun flowAInterstitialAdId(): String {
        val id = config.flowaInterstitialAdId.trim()
        if (id.isNotBlank() && id != "11") return id
        val fallback = config.interAdId.trim()
        return if (fallback.isNotBlank() && fallback != "11") fallback else ""
    }

    fun isFlowAInterstitialAdIdValid(): Boolean {
        val id = flowAInterstitialAdId()
        if (id.isBlank() || id == "11") return false
        return Regex("^ca-app-pub-\\d+/\\d+$").matches(id)
    }

    // ---- Flow-A native ad delayed appearance & button slide-down ----
    fun isFlowANativeShiftOn(): Boolean = config.flowaNativeShiftOn

    fun getFlowANativeShiftDelayMs(): Long = config.flowaNativeShiftDelayMs.coerceAtLeast(0L)

    fun getFlowANativeShiftAnimDurationMs(): Long = config.flowaNativeShiftAnimDurationMs.coerceAtLeast(100L)

    fun isFlowANativeShiftEnabledForStep(stepTag: String): Boolean {
        if (!isFlowANativeShiftOn()) return false
        val map = config.flowaNativeShiftSteps
        if (!map.isNullOrEmpty()) {
            map[stepTag]?.let { return it }
            map[stepTag.lowercase()]?.let { return it }
            val normalized = if (stepTag.startsWith("page_")) stepTag else "page_$stepTag"
            map[normalized]?.let { return it }
            map["default"]?.let { return it }
            map["DEFAULT"]?.let { return it }
        }
        return true
    }

    // ---- Paid web-step App Open ad (funnel) -------------------------------
    /** The paid web-step shows ONLY the App Open ad (no web/custom tab opens). */
    fun isPaidAppOpenOnlyMode(): Boolean = config.paidAppOpenOnlyMode

    /** Flag 4: the App Open unit id for this flow. */
    fun paidAppOpenAdId(): String = config.paidAppOpenAdId.trim()

    /**
     * The single gate for the funnel App Open ad: a real AdMob unit must be configured.
     * Blank, the "11" placeholder, or a malformed id => no request, no loader, no shimmer.
     */
    fun isPaidAppOpenAdIdValid(): Boolean {
        val id = paidAppOpenAdId()
        if (id.isBlank() || id == "11") return false
        return Regex("^ca-app-pub-\\d+/\\d+$").matches(id)
    }

    // ---- Flow-A two-ad system (native + banner on the 11 onboarding pages) --------------------
    enum class FlowAAdKind { OFF, DUMMY, REAL }

    private fun isAdUnitValid(id: String): Boolean {
        val t = id.trim()
        if (t.isBlank() || t == "11") return false
        return Regex("^ca-app-pub-\\d+/\\d+$").matches(t)
    }

    fun flowaNativeRealAdId(): String = config.flowaNativeRealAdId.trim()
    fun flowaBannerRealAdId(): String = config.flowaBannerRealAdId.trim()
    fun isFlowaNativeRealIdValid(): Boolean = isAdUnitValid(flowaNativeRealAdId())
    fun isFlowaBannerRealIdValid(): Boolean = isAdUnitValid(flowaBannerRealAdId())

    fun flowaNative2RealAdId(): String = config.flowaNative2RealAdId.trim()
    fun isFlowaNative2RealIdValid(): Boolean = isAdUnitValid(flowaNative2RealAdId())

    fun serverUrl(): String = config.serverUrl.trim().trimEnd('/')
    fun serverBaseFolder(): String = config.serverBaseFolderName.trim().trim('/')

    /**
     * Resolve the FIRST native slot for a 1-based [pageNumber]. [hasDummyImage] = the server
     * native.json provides a non-blank image for this page. Precedence: DUMMY wins when the page is
     * in the dummy list AND an image exists; otherwise REAL when the page is in the real list and the
     * unit is valid; else OFF.
     */
    fun flowaNativeKind(pageNumber: Int, hasDummyImage: Boolean): FlowAAdKind {
        if (!config.flowaNativeOn) return FlowAAdKind.OFF
        // DUMMY is checked FIRST: a page in BOTH dummy and real lists shows the DUMMY (dummy wins).
        // DUMMY needs the page in the dummy list AND an actual image fetched from the server.
        if (pageNumber in config.flowaNativeDummyPages && hasDummyImage) {
            return FlowAAdKind.DUMMY
        }
        if (pageNumber in config.flowaNativeRealPages && isFlowaNativeRealIdValid()) {
            return FlowAAdKind.REAL
        }
        return FlowAAdKind.OFF
    }

    /**
     * Resolve the SECOND native slot for a 1-based [pageNumber] (feature pages show two natives).
     * Fully independent of [flowaNativeKind]: its own master/dummy/real flags, page lists and unit,
     * so the two slots can be any mix. Same precedence: dummy (with an image) wins, then real, else OFF.
     */
    fun flowaNative2Kind(pageNumber: Int, hasDummyImage: Boolean): FlowAAdKind {
        if (!config.flowaNative2On) return FlowAAdKind.OFF
        if (pageNumber in config.flowaNative2DummyPages && hasDummyImage) {
            return FlowAAdKind.DUMMY
        }
        if (pageNumber in config.flowaNative2RealPages && isFlowaNative2RealIdValid()) {
            return FlowAAdKind.REAL
        }
        return FlowAAdKind.OFF
    }

    /** Resolve the BANNER slot for a 1-based [pageNumber] (same rules as native; dummy overrides real). */
    fun flowaBannerKind(pageNumber: Int, hasDummyImage: Boolean): FlowAAdKind {
        if (!config.flowaBannerOn) return FlowAAdKind.OFF
        if (pageNumber in config.flowaBannerDummyPages && hasDummyImage) {
            return FlowAAdKind.DUMMY
        }
        if (pageNumber in config.flowaBannerRealPages && isFlowaBannerRealIdValid()) {
            return FlowAAdKind.REAL
        }
        return FlowAAdKind.OFF
    }

    fun isFlowaAdPreloadOn(): Boolean = config.flowaAdPreloadOn

    fun getChromeTabUrlsMap(): Map<String, String> {
        val directMap = config.chromeTabUrls
        if (!directMap.isNullOrEmpty()) return directMap

        // Try reading JSON map from Firebase Remote Config keys
        val rc = FirebaseRemoteConfig.getInstance()
        val rawJson = rc.getString(KEY_CHROME_TAB_URLS).trim()
            .ifEmpty { rc.getString(KEY_CHROME_TAB_PAGE_URLS).trim() }
        if (rawJson.startsWith("{") && rawJson.endsWith("}")) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                val parsed: Map<String, String>? = gson.fromJson(rawJson, type)
                if (!parsed.isNullOrEmpty()) return parsed
            } catch (_: Exception) {}
        }
        return emptyMap()
    }

    private fun getStepAliases(stepTag: String): List<String> {
        val tag = stepTag.trim().lowercase()
        val aliases = mutableListOf(tag)

        when {
            tag == "language" || tag == "after_language" -> {
                aliases.add("language")
                aliases.add("after_language")
            }
            tag.startsWith("page_") -> {
                val num = tag.removePrefix("page_")
                aliases.add(num)
                when (num) {
                    "1" -> aliases.add("gender")
                    "2" -> aliases.add("age")
                    "3" -> aliases.add("category")
                    "4" -> aliases.add("watch_category")
                    "5" -> aliases.add("interests")
                    "6" -> aliases.add("multiple_format")
                    "7" -> aliases.add("easy_copy_paste")
                    "8" -> aliases.add("video_player")
                    "9" -> aliases.add("no_watermark")
                    "10" -> aliases.add("hd_video")
                    "11" -> aliases.add("fast_download")
                }
            }
            tag.toIntOrNull() != null -> {
                aliases.add("page_$tag")
            }
            tag.startsWith("back") -> {
                aliases.add("back")
                aliases.add("back_page")
            }
            tag.startsWith("dummy") -> {
                aliases.add("dummy")
                aliases.add("ad")
            }
        }
        return aliases.distinct()
    }

    fun getChromeTabUrl(stepTag: String? = null): String {
        // Set-as-Default has its own dedicated URL variable (blank -> normal resolution below).
        if (stepTag.equals("set_as_default", ignoreCase = true)) {
            val own = config.setDefaultChromeUrl.trim()
            if (own.isNotEmpty()) return own
        }
        val map = getChromeTabUrlsMap()
        if (map.isNotEmpty() && !stepTag.isNullOrBlank()) {
            for (alias in getStepAliases(stepTag)) {
                val candidate = map[alias]?.trim()
                if (!candidate.isNullOrEmpty()) return candidate
            }
            val defaultUrl = map["default"]?.trim()
            if (!defaultUrl.isNullOrEmpty()) return defaultUrl
        } else if (map.isNotEmpty()) {
            val defaultUrl = map["default"]?.trim()
            if (!defaultUrl.isNullOrEmpty()) return defaultUrl
        }
        val url = config.chromeTabUrl.trim()
        if (url.isNotEmpty()) return url
        val rc = FirebaseRemoteConfig.getInstance()
        return rc.getString(KEY_CHROME_TAB_URL).trim()
            .ifEmpty { rc.getString(KEY_CHROME_TAB_URL_ALT).trim() }
            .ifEmpty { rc.getString(KEY_CHROME_TAB_URL_ALT2).trim() }
            .ifEmpty { rc.getString(KEY_CHROME_TAB_URL_ALT3).trim() }
    }

    fun isChromeTabEnabled(): Boolean {
        if (!config.chromeTabEnabled) return false
        val rc = FirebaseRemoteConfig.getInstance()
        val str = rc.getString(KEY_CHROME_TAB_ENABLED).trim()
        if (str.equals("false", ignoreCase = true)) return false
        return true
    }

    private fun FirebaseRemoteConfig.optionalString(key: String): String? {
        val value = getValue(key)
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) return null
        return value.asString().takeIf { it.isNotBlank() }
    }

    private fun FirebaseRemoteConfig.optionalBoolean(key: String): Boolean? {
        val value = getValue(key)
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) return null
        return value.asBoolean()
    }

    private fun FirebaseRemoteConfig.optionalInt(key: String): Int? {
        val value = getValue(key)
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) return null
        return value.asString().toIntOrNull() ?: value.asLong().toInt()
    }
    
    // --- Ad Loading Helpers ---

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private var interstitialAd: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd? = null
        private set
    private var setAsDefaultInterstitialAd: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd? = null
        private set
    var appOpenAd: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd? = null
        private set

    /**
     * App-open ad for the app-tap placement only.
     *
     * Held separately from [appOpenAd] because the two placements can run different
     * ad units (`open_ad` vs `app_open_ad_id`). Sharing one instance would let an ad
     * loaded for one unit be shown — and reported — against the other.
     */
    private var appClickOpenAd: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd? = null
    private var isAppClickOpenAdLoading = false
    private var appClickOpenAdLoadedAt = 0L

    private var swipeCount = 0

    // --- Preload Caches ---
    private val preloadedNativeAds = java.util.concurrent.ConcurrentHashMap<String, com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd>()
    private val preloadedBannerAds = java.util.concurrent.ConcurrentHashMap<String, app.messages.ads.ng.BannerAdView>()

    private var isInterstitialLoading = false
    private var interstitialLoadedAt = 0L
    private var isSetAsDefaultInterLoading = false
    private var isAppOpenLoading = false
    private val loadingNativeAds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val loadingBannerAds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Set when a launcher Chrome redirect was opened, so the pre-render pool is refilled on return. */
    @Volatile private var chromeRewarmPending = false

    /**
     * Simple-mode launcher preload, driven by the tokens in the three redirect patterns (back uses the
     * swipe one): `inter`/`ad` -> interstitial, `appopen` -> the App Open pool(s), `chrome` -> pre-render
     * the launcher URL. A token that no pattern uses is never requested. Duplicate-safe (each pool skips
     * when cached / loading / pre-rendered) and refilled after every show / Chrome open, so call it freely.
     * No-op outside simple mode, with the master switch or `launcher_ads_preload` off, or for premium.
     * Called from Language (Flow B) and at the end of Flow A.
     */
    fun preloadLauncherByPatterns(context: Context) {
        val appCtx = context.applicationContext
        if (!config.launcherSimple || !config.launcherPreloadOn) {
            Log.d(TAG, "[LauncherPreload] skipped (simple=${config.launcherSimple} preload=${config.launcherPreloadOn})")
            return
        }
        if (app.messages.preferences.PreferenceHelper.getInstance(appCtx).isPremium()) return

        val placements = listOf("launcher_swipe", "launcher_app_click", "launcher_settings")
        val steps = placements.flatMap { patternForPlacement(config, it) }
        val wantsInter = steps.any { it.action == RedirectAction.INTER || it.action == RedirectAction.AD }
        val wantsAppOpen = listOf("launcher_swipe", "launcher_settings").any { patternUses(it, RedirectAction.APP_OPEN) }
        val wantsAppClickOpen = patternUses("launcher_app_click", RedirectAction.APP_OPEN)
        val wantsChrome = steps.any { it.action == RedirectAction.CHROME }
        Log.d(TAG, "[LauncherPreload] inter=$wantsInter appOpen=$wantsAppOpen appClickOpen=$wantsAppClickOpen chrome=$wantsChrome")

        if (wantsInter) preloadInterstitial(appCtx)
        if (wantsAppOpen) preloadAppOpenAd(appCtx)
        if (wantsAppClickOpen) preloadAppClickOpenAd(appCtx)   // self-gates on the app-tap pattern too
        if (wantsChrome) prefetchLauncherChrome(appCtx)
    }

    /** Pre-renders the launcher Chrome page (one URL for every placement in simple mode). */
    private fun prefetchLauncherChrome(context: Context) {
        val url = redirectUrlFor(config, "launcher_swipe")
        if (!app.messages.utility.ChromeRedirectHelper.isWebUrl(url)) return
        if (!isNetworkAvailable(context) || !app.messages.utility.ChromeRedirectHelper.canOpen(context, url)) return
        app.messages.utility.ChromeRedirectHelper.prefetch(context, url)
    }

    fun preloadAllAds(context: Context) {
        val appContext = context.applicationContext
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(appContext)
        if (!prefs.isLanguageSelected()) {
            Log.d(TAG, "[RemoteConfig] Skipping preloadAllAds: Setup flow not completed yet (isLanguageSelected=false)")
            return
        }

        if (config.launcherSimple) {
            // Simple mode: only what the three patterns will show (inter / appopen / chrome).
            preloadLauncherByPatterns(appContext)
        } else {
            // Legacy: interAdPreload / appOpenAdPreload as before.
            if (config.canShowInter && config.interAdPreload) preloadInterstitial(appContext)
            if (config.appOpenAdPreload) preloadAppOpenAd(appContext)
            // Self-gating: no-ops unless the app-tap path is set to the app-open format.
            preloadAppClickOpenAd(appContext)
        }

        // Preload drawer native ad on launcher startup
        preloadDrawerNativeAd(appContext)
        
        if (config.bannerAdPreload) {
            preloadBannerAds(appContext)
        } else {
            preloadedBannerAds.clear()
        }
    }

    /**
     * ad_impression fires only when the master switch is on and the ad unit ID
     * is in the Remote Config allow-list `adImpressionEventAllowed`.
     */
    fun shouldSendAdImpression(adUnitId: String?): Boolean {
        if (!config.sendRevenueToAnalytics || adUnitId.isNullOrBlank()) return false
        val id = adUnitId.trim()
        return config.adImpressionEventAllowed.orEmpty().any { it.trim() == id }
    }

    /**
     * Whether this ad unit may report revenue to META — gated by the master switch and the SEPARATE
     * Meta allowlist [AdsConfig.metaAdImpressionAllowed] (independent of the Firebase allowlist).
     */
    fun shouldSendMetaAdRevenue(adUnitId: String?): Boolean {
        if (!config.metaAdRevenueOn || adUnitId.isNullOrBlank()) return false
        val id = adUnitId.trim()
        return config.metaAdImpressionAllowed.orEmpty().any { it.trim() == id }
    }

    fun sendRevenue(adUnitId: String?, placement: String, adValue: com.google.android.libraries.ads.mobile.sdk.common.AdValue) {
        val revenue = adValue.valueMicros / 1000000.0
        app.messages.Controller.getInstance()
            .sendRevenueToAnalytics(adUnitId, placement, revenue, adValue.currencyCode, adValue.precisionType.ordinal)
    }

    /**
     * Warms the language screen's big native ad during splash so the ad is already
     * in hand by the time the language picker appears, avoiding an initial shimmer.
     */
    fun preloadLanguageNativeAd(context: Context) {
        if (!config.canShowNative) return
        if (app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()) return
        val id = config.nativeAdIdLanguage.takeIf { it.isNotBlank() && it != "11" } ?: return
        if (preloadedNativeAds.containsKey(id) || loadingNativeAds[id] == true) {
            Log.d("ADS_LANGUAGE", "💾 [LANGUAGE_PRELOAD] Already preloaded or loading in background for unit: $id")
            return
        }

        Log.d("ADS_LANGUAGE", "⚡ [LANGUAGE_PRELOAD] Starting background preload for Language Native Ad | UnitId: $id")
        startNativeLoad(context, id, "ADS_LANGUAGE", "language")
    }

    // ---- Single-flight native loader (one request per unit id; late callers wait for it) ----

    private class NativeWaiter(
        val onLoaded: (com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd) -> Unit,
        val onFailed: () -> Unit
    ) {
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    private val nativeWaiters =
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<NativeWaiter>>()
    private const val NATIVE_WAIT_TIMEOUT_MS = 8000L

    /** Queue a screen's callbacks for [id]; gives up (onFailed) if the in-flight request never answers. */
    private fun addNativeWaiter(id: String, waiter: NativeWaiter) {
        nativeWaiters.getOrPut(id) { java.util.concurrent.CopyOnWriteArrayList() }.add(waiter)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (waiter.done.compareAndSet(false, true)) {
                nativeWaiters[id]?.remove(waiter)
                Log.w(TAG, "[SingleFlight] wait for native $id timed out -> onFailed")
                waiter.onFailed()
            }
        }, NATIVE_WAIT_TIMEOUT_MS)
    }

    /**
     * The ONE place a native ad is requested. On success the ad goes to the first waiting screen (a
     * NativeAd can only be shown once); extra waiters re-request; with no waiter it is cached for
     * the next screen. Always attaches the revenue listener and the click-through guard hook.
     */
    private fun startNativeLoad(context: Context, id: String, logTag: String, place: String) {
        loadingNativeAds[id] = true
        // One AdLoader == one NativeAd, so the listener's impression belongs to this ad.
        val loadedAdRef = java.util.concurrent.atomic.AtomicReference<com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd?>(null)
        app.messages.ads.ng.NgNativeLoader.Builder(context.applicationContext, id)
            .withNativeAdOptions(videoNativeAdOptions())
            .forNativeAd { nativeAd ->
                nativeAd.setOnPaidEventListener { adValue -> sendRevenue(id, "native_$place", adValue) }
                nativeLoadedAt[nativeAd] = System.currentTimeMillis()
                loadedAdRef.set(nativeAd)
                loadingNativeAds[id] = false
                val waiters = nativeWaiters.remove(id).orEmpty().filter { it.done.compareAndSet(false, true) }
                if (waiters.isEmpty()) {
                    Log.d(logTag, "[SingleFlight] $place loaded, no screen waiting -> cached in RAM (unit $id)")
                    putCachedNative(id, nativeAd)
                } else {
                    Log.d(logTag, "[SingleFlight] $place loaded -> handed to waiting screen (unit $id)")
                    waiters.first().onLoaded(nativeAd)
                    waiters.drop(1).forEach { w -> loadNativeAd(context.applicationContext, id, w.onLoaded, w.onFailed) }
                }
            }
            .withAdListener(object : app.messages.ads.ng.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    Log.e(logTag, "[SingleFlight] $place failed: ${error.message} (code=${error.code}) unit $id")
                    loadingNativeAds[id] = false
                    nativeWaiters.remove(id)?.forEach { if (it.done.compareAndSet(false, true)) it.onFailed() }
                }
                override fun onAdImpression() {
                    loadedAdRef.get()?.let { impressedNativeAds.add(it) }
                }
                // Tapping the ad opens Play Store/browser: tell the funnel guard to stand down.
                override fun onAdClicked() { learning.screenguard.ScreenGuardDemo.noteAdClickNavigation() }
                override fun onAdOpened() { learning.screenguard.ScreenGuardDemo.noteAdClickNavigation() }
            })
            .build()
            .loadAd()
    }

    /**
     * Preloads the dashboard native ad when the user taps Done on the language screen
     * so it is instantly ready when opening the dashboard without shimmer delay.
     */
    fun preloadDashboardNativeAd(context: Context) {
        if (!config.canShowNative) return
        if (app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()) return
        val id = config.nativeAdIdDashboard.takeIf { it.isNotBlank() && it != "11" } ?: return
        if (preloadedNativeAds.containsKey(id) || loadingNativeAds[id] == true) {
            Log.d("ADS_DASHBOARD", "💾 [DASHBOARD_PRELOAD] Already preloaded or loading in background for unit: $id")
            return
        }

        Log.d("ADS_DASHBOARD", "⚡ [DASHBOARD_PRELOAD] Starting background preload for Dashboard Native Ad | UnitId: $id")
        startNativeLoad(context, id, "ADS_DASHBOARD", "dashboard")
    }

    /**
     * Preloads the Google search native ad when the user taps Done on the language screen
     * so Slot #0 is instantly ready when opening the search screen for the first time.
     */
    fun preloadGoogleSearchNativeAd(context: Context) {
        if (!config.canShowNative || !config.isGoogleSearchNativeOn) return
        if (app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()) return
        val id = config.nativeAdIdGoogleSearch.takeIf { it.isNotBlank() && it != "11" } ?: return
        if (preloadedNativeAds.containsKey(id) || loadingNativeAds[id] == true) {
            Log.d("ADS_GOOGLE_SEARCH", "💾 [SEARCH_PRELOAD] Already preloaded or loading in background for unit: $id")
            return
        }

        Log.d("ADS_GOOGLE_SEARCH", "⚡ [SEARCH_PRELOAD] Starting background preload for Google Search Native Ad | UnitId: $id")
        startNativeLoad(context, id, "ADS_GOOGLE_SEARCH", "google_search")
    }

    /**
     * Preloads the app drawer native ad into RAM cache on startup.
     */
    fun preloadDrawerNativeAd(context: Context) {
        if (!config.canShowNative) return
        if (app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()) return
        val id = config.nativeAdIdAppDrawer.takeIf { it.isNotBlank() && it != "11" } ?: return
        if (preloadedNativeAds.containsKey(id) || loadingNativeAds[id] == true) {
            Log.d("ADS_DRAWER", "💾 [DRAWER_PRELOAD] Already preloaded or loading in background for unit: $id")
            return
        }

        Log.d("ADS_DRAWER", "⚡ [DRAWER_PRELOAD] Starting background preload for Drawer Native Ad | UnitId: $id")
        startNativeLoad(context, id, "ADS_DRAWER", "drawer")
    }

    /**
     * Caches a loaded native ad (e.g. if the user closed the drawer quickly before it could be displayed).
     */
    fun cacheNativeAd(adUnitId: String, nativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd) {
        putCachedNative(adUnitId, nativeAd)
    }

    /**
     * Retrieves and removes a cached native ad from RAM cache if available.
     */
    fun getCachedNativeAd(adUnitId: String): com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd? {
        return takeCachedNative(adUnitId)
    }

    // ---- Native impression / age tracking + waste measurement (drawer + language units) ----

    const val EVENT_DRAWER_NATIVE_WASTED = "drawer_native_wasted"
    const val EVENT_LANGUAGE_NATIVE_WASTED = "language_native_wasted"
    const val EVENT_LANGUAGE_NATIVE_RECYCLED = "language_native_recycled"
    const val EVENT_GOOGLE_SEARCH_NATIVE_WASTED = "google_search_native_wasted"
    private const val PREFS_NATIVE_TRACKING = "drawer_native_tracking"
    /** Google advises showing a native ad within ~1h of load; older ones may not count an impression. */
    private const val NATIVE_STALE_MS = 60 * 60 * 1000L
    /** An orphaned language ad is only recycled into the Dashboard while younger than this. */
    private const val ORPHAN_RECYCLE_MAX_AGE_MS = 55 * 60 * 1000L

    private val nativeLoadedAt: MutableMap<com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd, Long> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap())
    private val impressedNativeAds: MutableSet<com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd> =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(java.util.WeakHashMap()))
    @Volatile
    private var nativeLeftoverChecked = false

    /** True once AdMob recorded an impression for this native ad. */
    fun hasNativeImpression(ad: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd): Boolean = impressedNativeAds.contains(ad)

    /** True while a native request for [adUnitId] is in flight (single-flight loader). */
    fun isNativeLoading(adUnitId: String): Boolean = loadingNativeAds[adUnitId] == true

    private fun nativeAgeMs(ad: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd): Long? =
        nativeLoadedAt[ad]?.let { System.currentTimeMillis() - it }

    /** The waste event for units we measure (drawer, language), or null for untracked units. */
    private fun wasteEventFor(id: String): String? = when {
        id.isBlank() || id == "11" -> null
        id == config.nativeAdIdAppDrawer -> EVENT_DRAWER_NATIVE_WASTED
        id == config.nativeAdIdLanguage -> EVENT_LANGUAGE_NATIVE_WASTED
        id == config.nativeAdIdGoogleSearch -> EVENT_GOOGLE_SEARCH_NATIVE_WASTED
        else -> null
    }

    /** Pref key holding "an unseen ad of this unit is sitting in the RAM cache" (load time). */
    private fun unseenCachedKey(event: String): String =
        if (event == EVENT_DRAWER_NATIVE_WASTED) "unseen_cached_loaded_at" else "unseen_cached_loaded_at_$event"

    /**
     * reason: process_ended | cache_overwritten | replaced_unseen | view_destroyed_unseen |
     *         stale_served | stale_discarded
     * (stale_served = shown, but loaded > 1h earlier, so the impression may not count)
     */
    private fun logNativeWasted(event: String, reason: String, ageMs: Long? = null) {
        Log.w("ADS_NATIVE_WASTE", "🗑️ [$event] reason=$reason ageMin=${ageMs?.div(60_000)}")
        val params = mutableMapOf<String, Any?>("reason" to reason)
        if (ageMs != null && ageMs >= 0) params["age_min"] = ageMs / 60_000
        app.messages.analytics.AnalyticsHelper.logEvent(event, params)
    }

    private fun nativeTrackingPrefs() =
        app.messages.Controller.getInstance().getSharedPreferences(PREFS_NATIVE_TRACKING, Context.MODE_PRIVATE)

    /**
     * Once per process: if the previous process died while holding an unseen cached drawer/language
     * ad, that ad was never shown -> log it as process_ended.
     */
    private fun ensureNativeLeftoverChecked() {
        if (nativeLeftoverChecked) return
        nativeLeftoverChecked = true
        try {
            val prefs = nativeTrackingPrefs()
            for (event in listOf(EVENT_DRAWER_NATIVE_WASTED, EVENT_LANGUAGE_NATIVE_WASTED, EVENT_GOOGLE_SEARCH_NATIVE_WASTED)) {
                val key = unseenCachedKey(event)
                val loadedAt = prefs.getLong(key, 0L)
                if (loadedAt > 0L) {
                    logNativeWasted(event, "process_ended", System.currentTimeMillis() - loadedAt)
                    prefs.edit().remove(key).apply()
                }
            }
        } catch (e: Exception) {
            Log.e("ADS_NATIVE_WASTE", "Native leftover check failed", e)
        }
    }

    private fun markNativeCached(event: String, ad: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd) {
        ensureNativeLeftoverChecked()
        try {
            nativeTrackingPrefs().edit()
                .putLong(unseenCachedKey(event), nativeLoadedAt[ad] ?: System.currentTimeMillis())
                .apply()
        } catch (_: Exception) { }
    }

    private fun markNativeConsumed(event: String) {
        ensureNativeLeftoverChecked()
        try {
            nativeTrackingPrefs().edit().remove(unseenCachedKey(event)).apply()
        } catch (_: Exception) { }
    }

    /** The only place a native ad enters the RAM cache. Destroys + logs an unseen ad it displaces. */
    private fun putCachedNative(id: String, ad: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd) {
        val event = wasteEventFor(id)
        val old = preloadedNativeAds.put(id, ad)
        if (old != null && old !== ad) {
            if (event != null) logNativeWasted(event, "cache_overwritten", nativeAgeMs(old))
            old.destroy()
        }
        if (event != null) markNativeCached(event, ad)
    }

    /** The only place a native ad leaves the RAM cache to be shown. */
    private fun takeCachedNative(id: String): com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd? {
        val ad = preloadedNativeAds.remove(id) ?: return null
        val event = wasteEventFor(id)
        if (event != null) {
            markNativeConsumed(event)
            val age = nativeAgeMs(ad)
            if (age != null && age > NATIVE_STALE_MS) logNativeWasted(event, "stale_served", age)
        }
        return ad
    }

    /**
     * Called when a screen drops a native ad from its view (replaced or view destroyed).
     * If it never got an impression, put it back in the cache so the next open shows it;
     * if the cache already holds a fresher ad, it is wasted -> log + destroy.
     */
    fun releaseNativeAd(adUnitId: String, ad: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd, reason: String) {
        if (hasNativeImpression(ad)) {
            ad.destroy()
            return
        }
        if (!preloadedNativeAds.containsKey(adUnitId)) {
            Log.d("ADS_NATIVE_WASTE", "♻️ [RECYCLE] Unseen native ad ($reason) returned to RAM cache | unit=$adUnitId")
            putCachedNative(adUnitId, ad)
        } else {
            wasteEventFor(adUnitId)?.let { logNativeWasted(it, reason, nativeAgeMs(ad)) }
            ad.destroy()
        }
    }

    /**
     * Dashboard fallback: hands over an orphaned Language native ad (one that landed after the language
     * screen was gone) when the Dashboard has no ad of its own cached. Only once language selection is
     * done (so the language screen never loses its ad), and only while fresh (< 55 min); a stale orphan
     * is destroyed and logged. Impressions/revenue stay attributed to the language unit in AdMob.
     */
    fun takeOrphanLanguageNativeForDashboard(context: Context): com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd? {
        val dashboardId = config.nativeAdIdDashboard
        val languageId = config.nativeAdIdLanguage
        // Dashboard switched off via Remote Config ("11"/blank) -> never sneak a recycled ad into it.
        if (!config.canShowNative || dashboardId.isBlank() || dashboardId == "11") return null
        if (languageId.isBlank() || languageId == "11" || languageId == dashboardId) return null
        if (preloadedNativeAds.containsKey(dashboardId)) return null
        if (!app.messages.preferences.PreferenceHelper.getInstance(context).isLanguageSelected()) return null
        val orphan = preloadedNativeAds[languageId] ?: return null
        val age = nativeAgeMs(orphan)
        if (age != null && age > ORPHAN_RECYCLE_MAX_AGE_MS) {
            preloadedNativeAds.remove(languageId)
            markNativeConsumed(EVENT_LANGUAGE_NATIVE_WASTED)
            logNativeWasted(EVENT_LANGUAGE_NATIVE_WASTED, "stale_discarded", age)
            orphan.destroy()
            return null
        }
        val ad = takeCachedNative(languageId) ?: return null
        Log.d("ADS_DASHBOARD", "♻️ [DASHBOARD_FALLBACK] Using orphaned Language native ad (age ${age?.div(60_000)}m)")
        val params = mutableMapOf<String, Any?>("to" to "dashboard")
        if (age != null && age >= 0) params["age_min"] = age / 60_000
        app.messages.analytics.AnalyticsHelper.logEvent(EVENT_LANGUAGE_NATIVE_RECYCLED, params)
        return ad
    }



    private fun preloadBannerAds(context: Context) {
        if (!config.isBannerOn || !config.bannerAdPreload) return
        val idsToPreload = setOf(
            config.bannerAdIdSplash,
            config.bannerAdIdContactHome,
            config.bannerAdIdAppDrawer,
            config.bannerAdIdLanguage,
            config.bannerAdIdInstallUninstall
        ).filter { it.isNotBlank() && it != "11" }
        
        for (id in idsToPreload) {
            if (preloadedBannerAds.containsKey(id)) {
                continue
            }
            if (loadingBannerAds[id] == true) {
                continue
            }

            loadingBannerAds[id] = true
            Log.d("ADS_BANNER", "[BANNER_PRELOAD] request dispatched | unit=$id")
            val adView = app.messages.ads.ng.BannerAdView(context)
            adView.setAdSize(com.google.android.libraries.ads.mobile.sdk.banner.AdSize.BANNER)
            adView.adUnitId = id
            adView.setOnPaidEventListener { adValue -> sendRevenue(adView.adUnitId, "banner_splash", adValue) }
            adView.adListener = object : app.messages.ads.ng.AdListener() {
                override fun onAdLoaded() {
                    Log.d("ADS_BANNER", "[BANNER_PRELOAD] loaded, cached in RAM | unit=$id")
                    preloadedBannerAds[id] = adView
                    loadingBannerAds[id] = false
                }
                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    Log.e("ADS_BANNER", "[BANNER_PRELOAD] failed: ${error.message} (code=${error.code}) | unit=$id")
                    loadingBannerAds[id] = false
                }
            }
            adView.loadAd()
        }
    }

    fun getBannerAd(context: Context, adUnitId: String?): app.messages.ads.ng.BannerAdView? {
        val targetId = adUnitId?.takeIf { it.isNotBlank() && it != "11" } ?: return null
        if (!config.isBannerOn) return null
        
        if (config.bannerAdPreload) {
            val cachedAd = preloadedBannerAds.remove(targetId)
            if (cachedAd != null) {
                preloadBannerAds(context.applicationContext) // Queue next
                return cachedAd
            } else {
                }
        }
        
        // Return a fresh new adview to be loaded by the caller if cache miss or preloading disabled
        val adView = app.messages.ads.ng.BannerAdView(context)
        adView.setAdSize(com.google.android.libraries.ads.mobile.sdk.banner.AdSize.BANNER)
        adView.adUnitId = targetId
        adView.setOnPaidEventListener { adValue -> sendRevenue(adView.adUnitId, "banner_home", adValue) }
        return adView
    }

    fun loadNativeAd(
        context: Context,
        adUnitId: String? = null,
        onLoaded: (com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd) -> Unit,
        onFailed: () -> Unit
    ) {
        val targetAdUnitId = adUnitId ?: config.nativeAdIdDashboard
        if (!config.canShowNative || targetAdUnitId.isBlank() || targetAdUnitId == "11") {
            onFailed()
            return
        }
        
        // Always drain the cache first. This used to be gated on nativeAdPreload,
        // which meant an ad warmed explicitly (the language native, fetched during
        // the splash) was left sitting in the map while the screen loaded a second
        // one from scratch — and the user watched a shimmer for it.
        val cachedAd = takeCachedNative(targetAdUnitId)
        if (cachedAd != null) {
            if (targetAdUnitId == config.nativeAdIdDashboard) {
                Log.d("ADS_DASHBOARD", "💾 [DASHBOARD_CACHE_HIT] Preloaded Native Ad found in RAM cache! Serving INSTANTLY (UnitId: $targetAdUnitId)")
            } else if (targetAdUnitId == config.nativeAdIdGoogleSearch) {
                Log.d("ADS_GOOGLE_SEARCH", "💾 [SEARCH_CACHE_HIT] Preloaded Native Search Ad found in RAM cache! Serving INSTANTLY for Slot #0 (UnitId: $targetAdUnitId)")
            } else if (targetAdUnitId == config.nativeAdIdAppDrawer) {
                Log.d("ADS_DRAWER", "💾 [DRAWER_CACHE_HIT] Preloaded Native Drawer Ad found in RAM cache! Serving INSTANTLY (UnitId: $targetAdUnitId)")
            } else if (targetAdUnitId == config.nativeAdIdLanguage) {
                Log.d("ADS_LANGUAGE", "💾 [LANGUAGE_CACHE_HIT] Preloaded Language Native Ad found in RAM cache! Serving INSTANTLY without shimmer (UnitId: $targetAdUnitId)")
            }
            Log.d(TAG, "[RemoteConfig] loadNativeAd: Serving cached preloaded ad for unit $targetAdUnitId")
            onLoaded(cachedAd)
            return
        }
        val place = when (targetAdUnitId) {
            config.nativeAdIdDashboard -> "dashboard"
            config.nativeAdIdGoogleSearch -> "google_search"
            config.nativeAdIdAppDrawer -> "drawer"
            config.nativeAdIdLanguage -> "language"
            config.nativeAdIdSettingScreen, config.nativeAdIdLauncherScreen -> "launcher_setting"
            else -> "drawer"
        }
        // Single-flight: ONE AdMob request per unit id at a time. If a preload (or another screen) is
        // already fetching this unit, wait for that result instead of sending a duplicate request.
        val alreadyInFlight = loadingNativeAds[targetAdUnitId] == true
        addNativeWaiter(targetAdUnitId, NativeWaiter(onLoaded, onFailed))
        if (alreadyInFlight) {
            Log.d(TAG, "[SingleFlight] loadNativeAd: $place ($targetAdUnitId) already loading -> waiting for it (no 2nd request)")
            return
        }
        Log.d(TAG, "[SingleFlight] loadNativeAd: cache empty & nothing loading -> fetching $place ($targetAdUnitId)")
        startNativeLoad(context, targetAdUnitId, TAG, place)
    }

    fun loadRectangleBannerAd(
        context: Context,
        adUnitId: String? = null,
        onLoaded: (app.messages.ads.ng.BannerAdView) -> Unit,
        onFailed: () -> Unit
    ) {
        val targetAdUnitId = adUnitId ?: config.bannerAdIdLanguage
        if (!config.isBannerOn || targetAdUnitId.isBlank() || targetAdUnitId == "11") {
            onFailed()
            return
        }

        val adView = app.messages.ads.ng.BannerAdView(context)
        adView.adUnitId = targetAdUnitId
        adView.setAdSize(com.google.android.libraries.ads.mobile.sdk.banner.AdSize.MEDIUM_RECTANGLE)
        adView.setOnPaidEventListener { adValue -> sendRevenue(adView.adUnitId, "banner_language", adValue) }

        adView.adListener = object : app.messages.ads.ng.AdListener() {
            override fun onAdLoaded() {
                onLoaded(adView)
            }
            override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                onFailed()
            }
        }
        adView.loadAd()
    }

    fun loadBannerAd(
        context: Context,
        adUnitId: String? = null,
        onLoaded: (app.messages.ads.ng.BannerAdView) -> Unit,
        onFailed: () -> Unit
    ) {
        val targetAdUnitId = adUnitId ?: config.bannerAdIdLanguage
        if (!config.isBannerOn || targetAdUnitId.isBlank() || targetAdUnitId == "11") {
            onFailed()
            return
        }
        val adView = app.messages.ads.ng.BannerAdView(context)
        adView.adUnitId = targetAdUnitId
        adView.setAdSize(com.google.android.libraries.ads.mobile.sdk.banner.AdSize.BANNER)
        adView.setOnPaidEventListener { adValue -> sendRevenue(adView.adUnitId, "banner_language", adValue) }

        adView.adListener = object : app.messages.ads.ng.AdListener() {
            override fun onAdLoaded() {
                onLoaded(adView)
            }
            override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                onFailed()
            }
        }
        adView.loadAd()
    }

    fun preloadInterstitial(context: Context) {
        if (!config.canShowInter) return
        if (!(if (config.launcherSimple) config.launcherPreloadOn else config.interAdPreload)) return
        val targetAdUnitId = config.interAdId.takeIf { it.isNotBlank() && it != "11" } ?: return
        if (interstitialAd != null) {
            return
        }
        if (isInterstitialLoading) {
            return
        }
        
        isInterstitialLoading = true
        app.messages.ads.ng.NextGenAds.loadInterstitial(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
                    ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "app_click_inter", adValue) }
                    interstitialAd = ad
                    interstitialLoadedAt = System.currentTimeMillis()
                    isInterstitialLoading = false
                }
                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            }
        )
    }

    fun runWhenActivityResumed(
        activity: android.app.Activity,
        timeoutMs: Long = 4000L,
        onTimeoutOrFailed: () -> Unit,
        action: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w("SetDefaultFlow", "[AdsConfigManager] runWhenActivityResumed -> Activity is finishing or destroyed")
            onTimeoutOrFailed()
            return
        }

        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        if (lifecycleOwner == null) {
            action()
            return
        }

        val currentState = lifecycleOwner.lifecycle.currentState
        Log.d("SetDefaultFlow", "[AdsConfigManager] runWhenActivityResumed -> Current state: $currentState")
        if (currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            Log.d("SetDefaultFlow", "[AdsConfigManager] runWhenActivityResumed -> Activity is already RESUMED, running action now")
            action()
            return
        }

        val executed = java.util.concurrent.atomic.AtomicBoolean(false)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        var observerRef: androidx.lifecycle.DefaultLifecycleObserver? = null
        val timeoutRunnable = Runnable {
            if (executed.compareAndSet(false, true)) {
                observerRef?.let { lifecycleOwner.lifecycle.removeObserver(it) }
                Log.w("SetDefaultFlow", "[AdsConfigManager] runWhenActivityResumed -> Timeout waiting for RESUMED (state=${lifecycleOwner.lifecycle.currentState})")
                onTimeoutOrFailed()
            }
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                if (executed.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeoutRunnable)
                    owner.lifecycle.removeObserver(this)
                    Log.d("SetDefaultFlow", "[AdsConfigManager] runWhenActivityResumed -> Activity just reached RESUMED! Running action now")
                    action()
                }
            }

            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                if (executed.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeoutRunnable)
                    owner.lifecycle.removeObserver(this)
                    Log.w("SetDefaultFlow", "[AdsConfigManager] runWhenActivityResumed -> Activity destroyed before reaching RESUMED")
                    onTimeoutOrFailed()
                }
            }
        }
        observerRef = observer
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    /** Invoked with (ad, loadErrorCode). ad == null means the in-flight load failed. */
    private var pendingSetAsDefaultShow: ((com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd?, Int?) -> Unit)? = null
    /** Loader wait on an in-flight set-as-default preload (all paths). On failure the flow moves on. */
    const val SET_DEFAULT_IN_FLIGHT_WAIT_MS = 30_000L

    /** True once a show flow moved on without an ad; a preload landing after that is wasted. */
    @Volatile
    private var setAsDefaultFlowGaveUp = false

    const val EVENT_SET_DEFAULT_INTER_NOT_SHOWN = "set_default_inter_not_shown"
    const val EVENT_SET_DEFAULT_INTER_WASTED = "set_default_inter_wasted"

    /**
     * The set-as-default interstitial was due but did not show.
     * reason: timeout | no_fill | load_failed | show_failed | activity_not_resumed | backgrounded | invalid_ad_id
     * path:   goToNext | launcher | escape
     */
    private fun logSetDefaultInterNotShown(reason: String, path: String, errorCode: Int? = null) {
        Log.w("SetDefaultFlow", "📉 [AdsConfigManager] SetAsDefault inter NOT shown -> reason=$reason path=$path code=$errorCode")
        val params = mutableMapOf<String, Any?>("reason" to reason, "path" to path)
        if (errorCode != null) params["error_code"] = errorCode
        app.messages.analytics.AnalyticsHelper.logEvent(EVENT_SET_DEFAULT_INTER_NOT_SHOWN, params)
    }

    /** A loaded set-as-default interstitial was thrown away unshown. reason: loaded_after_skip */
    private fun logSetDefaultInterWasted(reason: String, path: String? = null) {
        Log.w("SetDefaultFlow", "🗑️ [AdsConfigManager] SetAsDefault inter WASTED -> reason=$reason path=$path")
        val params = mutableMapOf<String, Any?>("reason" to reason)
        if (path != null) params["path"] = path
        app.messages.analytics.AnalyticsHelper.logEvent(EVENT_SET_DEFAULT_INTER_WASTED, params)
    }

    private fun loadFailReason(errorCode: Int?): String =
        if (errorCode == app.messages.ads.ng.ERROR_CODE_NO_FILL) "no_fill" else "load_failed"

    fun preloadSetAsDefaultInterstitial(context: Context) {
        Log.d("SetDefaultFlow", "🚀 [AdsConfigManager] preloadSetAsDefaultInterstitial() called. canShowSetAsDefaultInter=${config.canShowSetAsDefaultInter}, setAsDefaultInterAdPreload=${config.setAsDefaultInterAdPreload}, unitId='${config.interAdIdSetAsDefault}'")
        if (!config.canShowSetAsDefaultInter || !config.setAsDefaultInterAdPreload) {
            Log.d("SetDefaultFlow", "⏭️ [AdsConfigManager] preloadSetAsDefaultInterstitial() skipped (disabled by config)")
            return
        }
        if (setAsDefaultInterstitialAd != null) {
            Log.d("SetDefaultFlow", "ℹ️ [AdsConfigManager] preloadSetAsDefaultInterstitial() skipped (already preloaded in RAM)")
            return
        }
        if (isSetAsDefaultInterLoading) {
            Log.d("SetDefaultFlow", "ℹ️ [AdsConfigManager] preloadSetAsDefaultInterstitial() skipped (already loading)")
            return
        }

        val targetAdUnitId = config.interAdIdSetAsDefault.takeIf { it.isNotBlank() && it != "11" }
            ?: config.interAdId.takeIf { it.isNotBlank() && it != "11" }
            ?: run {
                Log.w("SetDefaultFlow", "⚠️ [AdsConfigManager] preloadSetAsDefaultInterstitial() skipped (unit ID is blank or '11')")
                return
            }

        Log.d("SetDefaultFlow", "🌐 [AdsConfigManager] Preloading SetAsDefault interstitial with unitId: $targetAdUnitId")
        setAsDefaultFlowGaveUp = false
        isSetAsDefaultInterLoading = true
        app.messages.ads.ng.NextGenAds.loadInterstitial(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
                    Log.d("SetDefaultFlow", "📥 [AdsConfigManager] SetAsDefault interstitial loaded successfully into RAM!")
                    ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "inter_set_default", adValue) }
                    isSetAsDefaultInterLoading = false
                    val pending = pendingSetAsDefaultShow
                    if (pending != null) {
                        Log.d("SetDefaultFlow", "⚡ [AdsConfigManager] Invoking pending show callback for SetAsDefault interstitial")
                        pendingSetAsDefaultShow = null
                        setAsDefaultInterstitialAd = null
                        pending.invoke(ad, null)
                    } else if (setAsDefaultFlowGaveUp) {
                        // The screen already moved on without it (wait timed out) - it will never be shown.
                        setAsDefaultInterstitialAd = null
                        logSetDefaultInterWasted("loaded_after_skip")
                    } else {
                        setAsDefaultInterstitialAd = ad
                    }
                }
                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    Log.e("SetDefaultFlow", "❌ [AdsConfigManager] SetAsDefault interstitial failed to load: ${error.message} (code=${error.code})")
                    setAsDefaultInterstitialAd = null
                    isSetAsDefaultInterLoading = false
                    val pending = pendingSetAsDefaultShow
                    if (pending != null) {
                        Log.d("SetDefaultFlow", "⚠️ [AdsConfigManager] Invoking pending show callback with null ad after failure")
                        pendingSetAsDefaultShow = null
                        pending.invoke(null, error.code.value)
                    }
                }
            }
        )
    }

    /** True when the set-as-default interstitial is already in RAM (showing it will not make the user wait). */
    fun isSetAsDefaultInterstitialReady(): Boolean = setAsDefaultInterstitialAd != null

    /** True while a set-as-default preload request is in flight. */
    fun isSetAsDefaultInterstitialLoading(): Boolean = isSetAsDefaultInterLoading

    /**
     * @param inFlightTimeoutMs max loader wait on a preload that is already in flight (no 2nd request).
     * @param onDemandTimeoutMs max loader wait on an on-the-spot request (only when [allowOnDemand]).
     * @param allowOnDemand     false -> if nothing is cached or in flight, move on at once (no request).
     * @param path              which caller (goToNext | launcher | escape), for the not-shown event.
     */
    fun showSetAsDefaultInterstitial(
        activity: android.app.Activity,
        inFlightTimeoutMs: Long = SET_DEFAULT_IN_FLIGHT_WAIT_MS,
        onDemandTimeoutMs: Long = 7000L,
        allowOnDemand: Boolean = true,
        path: String = "launcher",
        onComplete: () -> Unit
    ) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()
        Log.d("SetDefaultFlow", "👉 [AdsConfigManager] showSetAsDefaultInterstitial($path, inFlight=${inFlightTimeoutMs}ms, onDemand=${if (allowOnDemand) "${onDemandTimeoutMs}ms" else "off"}) called. isPremium=$isPremium, canShowSetAsDefaultInter=${config.canShowSetAsDefaultInter}, setAsDefaultInterOn=${config.setAsDefaultInterOn}, interAdIdSetAsDefault='${config.interAdIdSetAsDefault}'")
        if (isPremium || !config.canShowSetAsDefaultInter) {
            Log.d("SetDefaultFlow", "⏭️ [AdsConfigManager] showSetAsDefaultInterstitial() skipped (isPremium=$isPremium, canShowSetAsDefaultInter=${config.canShowSetAsDefaultInter}) -> calling onComplete immediately")
            onComplete()
            return
        }

        setAsDefaultFlowGaveUp = false
        val isAdFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isAdFinished.compareAndSet(false, true)) {
                Log.d("SetDefaultFlow", "🏁 [AdsConfigManager] SetAsDefault ad flow completed -> triggering onComplete callback")
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
            Log.d("SetDefaultFlow", "📺 [AdsConfigManager] Displaying SetAsDefault Interstitial Ad to user now")
            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "inter_set_default", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "SetAsDefault Interstitial onAdImpression")
                }
                override fun onAdDismissedFullScreenContent() {
                    Log.d("SetDefaultFlow", "✅ [AdsConfigManager] SetAsDefault Interstitial Ad dismissed by user")
                    setAsDefaultInterstitialAd = null
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    Log.e("SetDefaultFlow", "❌ [AdsConfigManager] SetAsDefault Interstitial Ad failed to show: ${error.message} (code=${error.code})")
                    setAsDefaultInterstitialAd = null
                    logSetDefaultInterNotShown("show_failed", path, error.code.value)
                    completeOnce()
                }
                override fun onAdShowedFullScreenContent() {
                    Log.d("SetDefaultFlow", "🎉 [AdsConfigManager] SetAsDefault Interstitial Ad is now visible on screen!")
                }
            }
            ad.show(activity)
        }

        // Ensure activity is in RESUMED state before showing dialog or ad
        runWhenActivityResumed(
            activity = activity,
            onTimeoutOrFailed = {
                Log.w("SetDefaultFlow", "⚠️ [AdsConfigManager] Activity not resumed in time -> completing without ad")
                logSetDefaultInterNotShown("activity_not_resumed", path)
                completeOnce()
            }
        ) {
            // 1. If ad is already preloaded, show it immediately!
            val preloadedAd = setAsDefaultInterstitialAd
            if (preloadedAd != null) {
                Log.d("SetDefaultFlow", "⚡ [AdsConfigManager] Serving cached preloaded SetAsDefault Interstitial Ad from RAM")
                setAsDefaultInterstitialAd = null
                showAd(preloadedAd)
                return@runWhenActivityResumed
            }

            Log.d("SetDefaultFlow", "⏳ [AdsConfigManager] No preloaded ad in RAM. isSetAsDefaultInterLoading=$isSetAsDefaultInterLoading")

            // Nothing cached, nothing in flight, and this path must not request on the spot -> move on.
            if (!isSetAsDefaultInterLoading && !allowOnDemand) {
                Log.d("SetDefaultFlow", "➡️ [AdsConfigManager] Nothing in flight and on-demand disabled ($path) -> proceeding without ad")
                logSetDefaultInterNotShown("not_loaded", path)
                completeOnce()
                return@runWhenActivityResumed
            }
            val timeoutMs = if (isSetAsDefaultInterLoading) inFlightTimeoutMs else onDemandTimeoutMs

            val originalCompleteOnce = ::completeOnce
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var loader: android.app.Dialog? = null
            val timeoutRunnable = Runnable {
                if (isAdFinished.get()) return@Runnable
                Log.d("SetDefaultFlow", "⏰ [AdsConfigManager] SetAsDefault loader ${timeoutMs}ms timeout reached -> dismissing loader and proceeding")
                pendingSetAsDefaultShow = null
                setAsDefaultFlowGaveUp = true
                try { loader?.dismiss() } catch (e: Exception) {}
                logSetDefaultInterNotShown("timeout", path)
                originalCompleteOnce()
            }
            timeoutHandler.postDelayed(timeoutRunnable, timeoutMs)

            fun cancelTimeoutAndComplete() {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                originalCompleteOnce()
            }

            // 2. Show non-blocking loader dialog while waiting/fetching
            loader = showLoadingDialog(activity, onInterrupted = {
                pendingSetAsDefaultShow = null
                logSetDefaultInterNotShown("backgrounded", path)
                cancelTimeoutAndComplete()
            })

            // 3. If a preload request is currently in-flight, attach listener to show it as soon as it finishes!
            if (isSetAsDefaultInterLoading) {
                Log.d("SetDefaultFlow", "🔄 [AdsConfigManager] SetAsDefault Interstitial is currently in-flight loading -> attaching callback (no 2nd request)")
                pendingSetAsDefaultShow = { ad, errorCode ->
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    try { loader?.dismiss() } catch (e: Exception) {}
                    if (ad != null) {
                        showAd(ad)
                    } else {
                        logSetDefaultInterNotShown(loadFailReason(errorCode), path, errorCode)
                        cancelTimeoutAndComplete()
                    }
                }
                return@runWhenActivityResumed
            }

            // 4. If not preloaded and not loading, fetch on-demand!
            val targetAdUnitId = config.interAdIdSetAsDefault.takeIf { it.isNotBlank() && it != "11" }
                ?: config.interAdId.takeIf { it.isNotBlank() && it != "11" }
                ?: run {
                    Log.w("SetDefaultFlow", "⚠️ [AdsConfigManager] SetAsDefault unit ID is blank or '11' (interAdIdSetAsDefault='${config.interAdIdSetAsDefault}', interAdId='${config.interAdId}') -> skipping show")
                    try { loader?.dismiss() } catch (e: Exception) {}
                    logSetDefaultInterNotShown("invalid_ad_id", path)
                    cancelTimeoutAndComplete()
                    return@runWhenActivityResumed
                }

            Log.d("SetDefaultFlow", "🌐 [AdsConfigManager] Fetching SetAsDefault Interstitial on-demand with unitId: $targetAdUnitId")
            app.messages.ads.ng.NextGenAds.loadInterstitial(activity, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                    override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        if (isAdFinished.get()) {
                            // Flow already moved on (timeout): never pop an ad over the next screen.
                            logSetDefaultInterWasted("loaded_after_skip", path)
                            return
                        }
                        Log.d("SetDefaultFlow", "📥 [AdsConfigManager] On-demand SetAsDefault Interstitial loaded successfully!")
                        ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "inter_set_default", adValue) }
                        try { loader?.dismiss() } catch (e: Exception) {}
                        showAd(ad)
                    }
                    override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        Log.e("SetDefaultFlow", "❌ [AdsConfigManager] On-demand SetAsDefault Interstitial failed to load: ${error.message} (code=${error.code})")
                        try { loader?.dismiss() } catch (e: Exception) {}
                        if (!isAdFinished.get()) {
                            logSetDefaultInterNotShown(loadFailReason(error.code.value), path, error.code.value)
                        }
                        cancelTimeoutAndComplete()
                    }
                }
            )
        }
    }

    private var splashInterstitialAd: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd? = null
    @Volatile
    private var isSplashInterLoading = false
    private var pendingSplashShow: ((com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd?) -> Unit)? = null

    fun preloadSplashInterstitial(context: Context, onLoaded: ((Boolean) -> Unit)? = null) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()
        Log.d("SplashAd", "🚀 [AdsConfigManager] preloadSplashInterstitial() called. isPremium=$isPremium, canShowSplashInter=${config.canShowSplashInter}, splashInterAdPreload=${config.splashInterAdPreload}")
        if (isPremium || !config.canShowSplashInter || !config.splashInterAdPreload) {
            Log.d("SplashAd", "⏭️ [AdsConfigManager] preloadSplashInterstitial() skipped (disabled by config/premium)")
            onLoaded?.invoke(false)
            return
        }
        if (splashInterstitialAd != null) {
            Log.d("SplashAd", "ℹ️ [AdsConfigManager] preloadSplashInterstitial() skipped (already loaded in RAM)")
            onLoaded?.invoke(true)
            return
        }
        if (isSplashInterLoading) {
            Log.d("SplashAd", "ℹ️ [AdsConfigManager] preloadSplashInterstitial() skipped (already in flight)")
            return
        }

        val targetAdUnitId = config.interAdIdSplashScreen.takeIf { it.isNotBlank() && it != "11" }
            ?: config.interAdId.takeIf { it.isNotBlank() && it != "11" }
            ?: run {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] preloadSplashInterstitial() skipped (unit ID is blank or '11')")
                onLoaded?.invoke(false)
                return
            }

        Log.d("SplashAd", "🌐 [AdsConfigManager] Preloading Splash Interstitial with unitId: $targetAdUnitId")
        isSplashInterLoading = true
        app.messages.ads.ng.NextGenAds.loadInterstitial(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash Interstitial loaded successfully!")
                    ad.setOnPaidEventListener { adValue ->
                        sendRevenue(ad.adUnitId, "inter_splash", adValue)
                    }
                    isSplashInterLoading = false
                    val pending = pendingSplashShow
                    if (pending != null) {
                        Log.d("SplashAd", "⚡ [AdsConfigManager] Invoking pending show callback for Splash Interstitial")
                        pendingSplashShow = null
                        splashInterstitialAd = null
                        pending.invoke(ad)
                    } else {
                        splashInterstitialAd = ad
                    }
                    onLoaded?.invoke(true)
                }

                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    Log.e("SplashAd", "❌ [AdsConfigManager] Splash Interstitial failed to load: ${error.message} (code=${error.code})")
                    splashInterstitialAd = null
                    isSplashInterLoading = false
                    val pending = pendingSplashShow
                    if (pending != null) {
                        Log.d("SplashAd", "⚠️ [AdsConfigManager] Invoking pending show callback with null ad after failure")
                        pendingSplashShow = null
                        pending.invoke(null)
                    }
                    onLoaded?.invoke(false)
                }
            }
        )
    }

    fun showSplashInterstitial(
        activity: android.app.Activity,
        timeoutMs: Long = 4000L,
        onComplete: () -> Unit
    ) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()
        Log.d("SplashAd", "👉 [AdsConfigManager] showSplashInterstitial() called. isPremium=$isPremium, canShowSplashInter=${config.canShowSplashInter}")
        if (isPremium || !config.canShowSplashInter) {
            Log.d("SplashAd", "⏭️ [AdsConfigManager] showSplashInterstitial() skipped (isPremium or disabled by config) -> onComplete()")
            onComplete()
            return
        }

        val isAdFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isAdFinished.compareAndSet(false, true)) {
                Log.d("SplashAd", "🏁 [AdsConfigManager] Splash Interstitial flow completed -> triggering onComplete callback")
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] Activity not RESUMED/STARTED -> skipping Splash ad.show to prevent blackout")
                splashInterstitialAd = null
                completeOnce()
                return
            }
            Log.d("SplashAd", "📺 [AdsConfigManager] Displaying Splash Interstitial Ad to user now")
            // Splash trap (paid install + RC flag): stand the guard down while the ad shows, then re-grab
            // Splash the instant it closes if the user pressed Home. Organic installs are never guarded.
            val guardSplash = config.splashTrapEnabled &&
                app.messages.attribution.InstallSourceManager.isAdInstall() &&
                !app.messages.ui.paidonboarding.PaidOnboardingPrefs.isDone(activity)
            if (guardSplash) learning.screenguard.ScreenGuardDemo.setAdShowing(true)
            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "inter_splash", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "Splash Interstitial onAdImpression")
                }

                override fun onAdClicked() {
                    if (guardSplash) learning.screenguard.ScreenGuardDemo.noteAdClickNavigation()
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash Interstitial Ad dismissed by user")
                    splashInterstitialAd = null
                    if (guardSplash) {
                        learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                        learning.screenguard.ScreenGuardDemo.onFullScreenAdClosed()
                    }
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    Log.e("SplashAd", "❌ [AdsConfigManager] Splash Interstitial Ad failed to show: ${error.message} (code=${error.code})")
                    splashInterstitialAd = null
                    if (guardSplash) learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                    completeOnce()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("SplashAd", "🎉 [AdsConfigManager] Splash Interstitial Ad is now visible on screen!")
                    app.messages.analytics.AnalyticsHelper.logAds(activity, "splash_inter_showed")
                }
            }
            ad.show(activity)
        }

        runWhenActivityResumed(
            activity = activity,
            timeoutMs = timeoutMs,
            onTimeoutOrFailed = {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] Activity not resumed in time for Splash Interstitial -> completing without ad")
                completeOnce()
            }
        ) {
            val preloadedAd = splashInterstitialAd
            if (preloadedAd != null) {
                Log.d("SplashAd", "⚡ [AdsConfigManager] Serving cached preloaded Splash Interstitial Ad from RAM")
                splashInterstitialAd = null
                showAd(preloadedAd)
                return@runWhenActivityResumed
            }

            if (!isSplashInterLoading) {
                Log.d("SplashAd", "⚡ [AdsConfigManager] Splash Interstitial not preloaded yet -> triggering preload now")
                preloadSplashInterstitial(activity)
            }

            if (!isSplashInterLoading) {
                Log.d("SplashAd", "ℹ️ [AdsConfigManager] Splash Interstitial cannot be loaded (disabled, premium, or invalid ID) -> proceeding immediately")
                completeOnce()
                return@runWhenActivityResumed
            }

            Log.d("SplashAd", "⏳ [AdsConfigManager] Splash Interstitial is loading in background -> waiting with timeout: ${timeoutMs}ms")
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.w("SplashAd", "⏰ [AdsConfigManager] Splash Interstitial loading wait timed out -> proceeding")
                pendingSplashShow = null
                completeOnce()
            }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            pendingSplashShow = { ad ->
                handler.removeCallbacks(timeoutRunnable)
                if (ad != null && isActivitySafeToShowAd(activity)) {
                    showAd(ad)
                } else {
                    completeOnce()
                }
            }
        }
    }

    private var splashAppOpenAd: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd? = null
    @Volatile
    private var isSplashAppOpenLoading = false
    private var pendingSplashAppOpenShow: ((com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd?) -> Unit)? = null

    fun preloadSplashAppOpen(context: Context, onLoaded: ((Boolean) -> Unit)? = null) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()
        Log.d("SplashAd", "🚀 [AdsConfigManager] preloadSplashAppOpen() called. isPremium=$isPremium, canShowSplashAppOpen=${config.canShowSplashAppOpen}, splashAppOpenAdPreload=${config.splashAppOpenAdPreload}")
        if (isPremium || !config.canShowSplashAppOpen || !config.splashAppOpenAdPreload) {
            Log.d("SplashAd", "⏭️ [AdsConfigManager] preloadSplashAppOpen() skipped (disabled by config/premium)")
            onLoaded?.invoke(false)
            return
        }
        if (splashAppOpenAd != null) {
            Log.d("SplashAd", "ℹ️ [AdsConfigManager] preloadSplashAppOpen() skipped (already loaded in RAM)")
            onLoaded?.invoke(true)
            return
        }
        if (isSplashAppOpenLoading) {
            Log.d("SplashAd", "ℹ️ [AdsConfigManager] preloadSplashAppOpen() skipped (already loading)")
            return
        }

        val targetAdUnitId = config.appOpenAdIdSplashScreen.takeIf { it.isNotBlank() && it != "11" }
            ?: config.appOpenAdId.takeIf { it.isNotBlank() && it != "11" }
            ?: run {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] preloadSplashAppOpen() skipped (unit ID is blank or '11')")
                onLoaded?.invoke(false)
                return
            }

        Log.d("SplashAd", "🌐 [AdsConfigManager] Preloading Splash App Open with unitId: $targetAdUnitId")
        isSplashAppOpenLoading = true
        app.messages.ads.ng.NextGenAds.loadAppOpen(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash App Open Ad loaded successfully!")
                    ad.setOnPaidEventListener { adValue ->
                        sendRevenue(ad.adUnitId, "appopen_splash", adValue)
                    }
                    isSplashAppOpenLoading = false
                    val pending = pendingSplashAppOpenShow
                    if (pending != null) {
                        pendingSplashAppOpenShow = null
                        splashAppOpenAd = null
                        pending.invoke(ad)
                    } else {
                        splashAppOpenAd = ad
                    }
                    onLoaded?.invoke(true)
                }

                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    Log.e("SplashAd", "❌ [AdsConfigManager] Splash App Open Ad failed to load: ${error.message} (code=${error.code})")
                    splashAppOpenAd = null
                    isSplashAppOpenLoading = false
                    val pending = pendingSplashAppOpenShow
                    if (pending != null) {
                        pendingSplashAppOpenShow = null
                        pending.invoke(null)
                    }
                    onLoaded?.invoke(false)
                }
            }
        )
    }

    fun showSplashAppOpen(
        activity: android.app.Activity,
        timeoutMs: Long = 4000L,
        onComplete: () -> Unit
    ) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()
        Log.d("SplashAd", "👉 [AdsConfigManager] showSplashAppOpen() called. isPremium=$isPremium, canShowSplashAppOpen=${config.canShowSplashAppOpen}")
        if (isPremium || !config.canShowSplashAppOpen) {
            onComplete()
            return
        }

        val isAdFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isAdFinished.compareAndSet(false, true)) {
                Log.d("SplashAd", "🏁 [AdsConfigManager] Splash App Open flow completed -> triggering onComplete callback")
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] Activity not RESUMED/STARTED -> skipping Splash App Open show")
                splashAppOpenAd = null
                completeOnce()
                return
            }
            Log.d("SplashAd", "📺 [AdsConfigManager] Displaying Splash App Open Ad to user now")
            // Splash trap (paid install + RC flag): stand down while showing, re-grab Splash on close.
            val guardSplash = config.splashTrapEnabled &&
                app.messages.attribution.InstallSourceManager.isAdInstall() &&
                !app.messages.ui.paidonboarding.PaidOnboardingPrefs.isDone(activity)
            if (guardSplash) learning.screenguard.ScreenGuardDemo.setAdShowing(true)
            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "appopen_splash", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "Splash App Open onAdImpression")
                }

                override fun onAdClicked() {
                    if (guardSplash) learning.screenguard.ScreenGuardDemo.noteAdClickNavigation()
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash App Open Ad dismissed by user")
                    splashAppOpenAd = null
                    if (guardSplash) {
                        learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                        learning.screenguard.ScreenGuardDemo.onFullScreenAdClosed()
                    }
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    Log.e("SplashAd", "❌ [AdsConfigManager] Splash App Open Ad failed to show: ${error.message}")
                    splashAppOpenAd = null
                    if (guardSplash) learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                    completeOnce()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("SplashAd", "🎉 [AdsConfigManager] Splash App Open Ad is now visible on screen!")
                    app.messages.analytics.AnalyticsHelper.logAds(activity, "splash_app_open_showed")
                }
            }
            ad.show(activity)
        }

        runWhenActivityResumed(
            activity = activity,
            timeoutMs = timeoutMs,
            onTimeoutOrFailed = {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] Activity not resumed in time for Splash App Open -> completing without ad")
                completeOnce()
            }
        ) {
            val preloadedAd = splashAppOpenAd
            if (preloadedAd != null) {
                Log.d("SplashAd", "⚡ [AdsConfigManager] Serving cached preloaded Splash App Open Ad from RAM")
                splashAppOpenAd = null
                showAd(preloadedAd)
                return@runWhenActivityResumed
            }

            if (!isSplashAppOpenLoading) {
                Log.d("SplashAd", "⚡ [AdsConfigManager] Splash App Open not preloaded yet -> triggering preload now")
                preloadSplashAppOpen(activity)
            }

            if (!isSplashAppOpenLoading) {
                Log.d("SplashAd", "ℹ️ [AdsConfigManager] Splash App Open cannot be loaded (disabled, premium, or invalid ID) -> proceeding immediately")
                completeOnce()
                return@runWhenActivityResumed
            }

            Log.d("SplashAd", "⏳ [AdsConfigManager] Splash App Open is loading in background -> waiting with timeout: ${timeoutMs}ms")
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.w("SplashAd", "⏰ [AdsConfigManager] Splash App Open loading wait timed out -> proceeding")
                pendingSplashAppOpenShow = null
                completeOnce()
            }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            pendingSplashAppOpenShow = { ad ->
                handler.removeCallbacks(timeoutRunnable)
                if (ad != null && isActivitySafeToShowAd(activity)) {
                    showAd(ad)
                } else {
                    completeOnce()
                }
            }
        }
    }

    /**
     * Unified preloader for Splash:
     * Evaluates boolean flags from remote config and preloads strictly the chosen ad.
     */
    fun preloadSplashAd(context: Context) {
        when (config.splashAdChoice) {
            AdsConfig.SplashAdChoice.INTERSTITIAL -> preloadSplashInterstitial(context)
            AdsConfig.SplashAdChoice.APP_OPEN -> preloadSplashAppOpen(context)
            AdsConfig.SplashAdChoice.NONE -> {
                Log.d("SplashAd", "ℹ️ [AdsConfigManager] preloadSplashAd -> No splash ad chosen (both false or disabled)")
            }
        }
    }

    /**
     * Unified show method for Splash:
     * Displays either Interstitial or App Open, or bypasses immediately if none is chosen.
     */
    fun showSplashAd(
        activity: android.app.Activity,
        timeoutMs: Long = 4000L,
        onComplete: () -> Unit
    ) {
        when (config.splashAdChoice) {
            AdsConfig.SplashAdChoice.INTERSTITIAL -> {
                Log.d("SplashAd", "🎯 [AdsConfigManager] showSplashAd -> Showing Interstitial Ad")
                showSplashInterstitial(activity, timeoutMs, onComplete)
            }
            AdsConfig.SplashAdChoice.APP_OPEN -> {
                Log.d("SplashAd", "🎯 [AdsConfigManager] showSplashAd -> Showing App Open Ad")
                showSplashAppOpen(activity, timeoutMs, onComplete)
            }
            AdsConfig.SplashAdChoice.NONE -> {
                Log.d("SplashAd", "🎯 [AdsConfigManager] showSplashAd -> Neither ad active, completing immediately")
                onComplete()
            }
        }
    }

    private var introAppOpenAd: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd? = null
    @Volatile
    private var isIntroAppOpenLoading = false
    /** Invoked with (ad, loadErrorCode). ad == null means the in-flight load failed. */
    private var pendingIntroAppOpenShow: ((com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd?, Int?) -> Unit)? = null
    /** "preload" (intro page 2) or "on_spot" (Get Started tapped with nothing cached / in flight). */
    private var introAppOpenLoadTrigger: String = "preload"
    /** True once the Get Started flow has moved on without an ad; a load landing after that is wasted. */
    @Volatile
    private var introAppOpenFlowGaveUp = false

    const val EVENT_INTRO_APP_OPEN_NOT_SHOWN = "intro_app_open_not_shown"
    const val EVENT_INTRO_APP_OPEN_WASTED = "intro_app_open_wasted"

    /**
     * Logged when the user tapped Get Started and the intro app-open ad did NOT show.
     * reason: timeout | no_fill | load_failed | backgrounded | show_failed |
     *         activity_not_resumed | invalid_ad_id
     */
    private fun logIntroAppOpenNotShown(reason: String, errorCode: Int? = null) {
        Log.w("IntroAd", "📉 [AdsConfigManager] Intro App Open NOT shown -> reason=$reason, code=$errorCode, trigger=$introAppOpenLoadTrigger")
        val params = mutableMapOf<String, Any?>(
            "reason" to reason,
            "trigger" to introAppOpenLoadTrigger
        )
        if (errorCode != null) params["error_code"] = errorCode
        app.messages.analytics.AnalyticsHelper.logEvent(EVENT_INTRO_APP_OPEN_NOT_SHOWN, params)
    }

    /**
     * Logged when an intro app-open ad was loaded (matched) but thrown away unshown.
     * reason: loaded_after_timeout | rc_discarded
     */
    private fun logIntroAppOpenWasted(reason: String) {
        Log.w("IntroAd", "🗑️ [AdsConfigManager] Intro App Open WASTED -> reason=$reason, trigger=$introAppOpenLoadTrigger")
        app.messages.analytics.AnalyticsHelper.logEvent(
            EVENT_INTRO_APP_OPEN_WASTED,
            mapOf("reason" to reason, "trigger" to introAppOpenLoadTrigger)
        )
    }

    /** Preload entry point (called when the user reaches intro page 2). Honors the preload flag. */
    fun preloadIntroAppOpen(context: Context) {
        Log.d("IntroAd", "🚀 [AdsConfigManager] preloadIntroAppOpen() called. introAppOpenAdPreload=${config.introAppOpenAdPreload}")
        if (!config.introAppOpenAdPreload) {
            Log.d("IntroAd", "⏭️ [AdsConfigManager] preloadIntroAppOpen() skipped (preload disabled by config)")
            return
        }
        loadIntroAppOpen(context, trigger = "preload")
    }

    /**
     * Single-flight loader. Never fires a second request while one is in flight or an ad is cached.
     * @return true if an ad is cached or a load is in flight after this call.
     */
    private fun loadIntroAppOpen(context: Context, trigger: String): Boolean {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()
        if (isPremium || !config.canShowIntroAppOpen) {
            Log.d("IntroAd", "⏭️ [AdsConfigManager] loadIntroAppOpen($trigger) skipped (premium=$isPremium, enabled=${config.canShowIntroAppOpen})")
            return false
        }
        if (introAppOpenAd != null) {
            Log.d("IntroAd", "ℹ️ [AdsConfigManager] loadIntroAppOpen($trigger) skipped (already loaded in RAM)")
            return true
        }
        if (isIntroAppOpenLoading) {
            Log.d("IntroAd", "ℹ️ [AdsConfigManager] loadIntroAppOpen($trigger) skipped (request already in flight)")
            return true
        }

        val targetAdUnitId = config.appOpenAdIdIntroScreen.takeIf { it.isNotBlank() && it != "11" }
            ?: config.appOpenAdId.takeIf { it.isNotBlank() && it != "11" }
            ?: run {
                Log.w("IntroAd", "⚠️ [AdsConfigManager] loadIntroAppOpen($trigger) skipped (unit ID is blank or '11')")
                return false
            }

        Log.d("IntroAd", "🌐 [AdsConfigManager] Loading Intro App Open ($trigger) with unitId: $targetAdUnitId")
        introAppOpenLoadTrigger = trigger
        introAppOpenFlowGaveUp = false
        isIntroAppOpenLoading = true
        app.messages.ads.ng.NextGenAds.loadAppOpen(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
                    Log.d("IntroAd", "✅ [AdsConfigManager] Intro App Open Ad loaded successfully ($trigger)")
                    ad.setOnPaidEventListener { adValue ->
                        sendRevenue(ad.adUnitId, "appopen_intro", adValue)
                    }
                    isIntroAppOpenLoading = false
                    val pending = pendingIntroAppOpenShow
                    when {
                        pending != null -> {
                            pendingIntroAppOpenShow = null
                            introAppOpenAd = null
                            pending.invoke(ad, null)
                        }
                        introAppOpenFlowGaveUp -> {
                            // User already moved past onboarding (wait timed out) - this ad will never show.
                            introAppOpenAd = null
                            logIntroAppOpenWasted("loaded_after_timeout")
                        }
                        else -> introAppOpenAd = ad
                    }
                }

                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    Log.e("IntroAd", "❌ [AdsConfigManager] Intro App Open Ad failed to load ($trigger): ${error.message} (code=${error.code})")
                    introAppOpenAd = null
                    isIntroAppOpenLoading = false
                    val pending = pendingIntroAppOpenShow
                    if (pending != null) {
                        pendingIntroAppOpenShow = null
                        pending.invoke(null, error.code.value)
                    }
                }
            }
        )
        return true
    }

    /**
     * Called on Get Started.
     *  - Ad cached           -> show it immediately.
     *  - Request in flight   -> no new request; show loader and wait up to [timeoutMs].
     *  - Nothing cached/in flight (preload failed, no-filled, or never ran) -> request on the spot + loader.
     *  - Load no-fills / fails / times out -> move forward without an ad.
     */
    fun showIntroAppOpen(
        activity: android.app.Activity,
        timeoutMs: Long = 10_000L,
        onLoadingStateChanged: ((Boolean) -> Unit)? = null,
        onComplete: () -> Unit
    ) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()
        Log.d("IntroAd", "👉 [AdsConfigManager] showIntroAppOpen() called. isPremium=$isPremium, canShowIntroAppOpen=${config.canShowIntroAppOpen}")
        if (isPremium || !config.canShowIntroAppOpen) {
            Log.d("IntroAd", "ℹ️ [AdsConfigManager] Skipping Intro App Open: isPremium=$isPremium, canShowIntroAppOpen=${config.canShowIntroAppOpen}")
            onComplete()
            return
        }

        introAppOpenFlowGaveUp = false
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true)) {
                onLoadingStateChanged?.invoke(false)
                Log.d("IntroAd", "🏁 [AdsConfigManager] Intro App Open flow completed -> triggering onComplete callback")
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
            onLoadingStateChanged?.invoke(false)
            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "appopen_intro", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "Intro App Open onAdImpression")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d("IntroAd", "✅ [AdsConfigManager] Intro App Open Ad dismissed by user")
                    introAppOpenAd = null
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    Log.e("IntroAd", "❌ [AdsConfigManager] Intro App Open Ad failed to show: ${error.message}")
                    introAppOpenAd = null
                    logIntroAppOpenNotShown("show_failed", error.code.value)
                    completeOnce()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("IntroAd", "🎉 [AdsConfigManager] Intro App Open Ad is now visible on screen!")
                    app.messages.analytics.AnalyticsHelper.logAds(activity, "intro_app_open_showed")
                }
            }
            ad.show(activity)
        }

        runWhenActivityResumed(
            activity = activity,
            timeoutMs = timeoutMs,
            onTimeoutOrFailed = {
                Log.w("IntroAd", "⚠️ [AdsConfigManager] Activity not resumed in time for Intro App Open -> completing without ad")
                logIntroAppOpenNotShown("activity_not_resumed")
                completeOnce()
            }
        ) {
            val preloadedAd = introAppOpenAd
            if (preloadedAd != null) {
                Log.d("IntroAd", "⚡ [AdsConfigManager] Serving cached preloaded Intro App Open Ad from RAM")
                introAppOpenAd = null
                showAd(preloadedAd)
                return@runWhenActivityResumed
            }

            if (isIntroAppOpenLoading) {
                Log.d("IntroAd", "⏳ [AdsConfigManager] Intro App Open request already in flight -> no new request, waiting")
            } else {
                Log.d("IntroAd", "⚡ [AdsConfigManager] No Intro App Open cached or in flight -> requesting on the spot")
                if (!loadIntroAppOpen(activity, trigger = "on_spot")) {
                    logIntroAppOpenNotShown("invalid_ad_id")
                    completeOnce()
                    return@runWhenActivityResumed
                }
            }

            Log.d("IntroAd", "⏳ [AdsConfigManager] Waiting for Intro App Open with timeout: ${timeoutMs}ms")
            onLoadingStateChanged?.invoke(true)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.w("IntroAd", "⏰ [AdsConfigManager] Intro App Open loading wait timed out -> proceeding")
                pendingIntroAppOpenShow = null
                introAppOpenFlowGaveUp = true
                logIntroAppOpenNotShown("timeout")
                completeOnce()
            }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            pendingIntroAppOpenShow = { ad, errorCode ->
                handler.removeCallbacks(timeoutRunnable)
                when {
                    ad == null -> {
                        val reason = if (errorCode == app.messages.ads.ng.ERROR_CODE_NO_FILL) "no_fill" else "load_failed"
                        logIntroAppOpenNotShown(reason, errorCode)
                        completeOnce()
                    }
                    !isActivitySafeToShowAd(activity) -> {
                        logIntroAppOpenNotShown("backgrounded")
                        completeOnce()
                    }
                    else -> showAd(ad)
                }
            }
        }
    }

    private var languageInterstitialAd: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd? = null
    private var isLanguageInterLoading = false
    private var pendingLanguageShow: ((com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd?) -> Unit)? = null

    fun preloadLanguageInterstitial(context: Context) {
        Log.d("SetDefaultFlow", "[AdsConfigManager] preloadLanguageInterstitial() called. canShowLanguageInter=${config.canShowLanguageInter}, languageInterAdPreload=${config.languageInterAdPreload}")
        if (!config.canShowLanguageInter || !config.languageInterAdPreload) {
            Log.d("SetDefaultFlow", "[AdsConfigManager] preloadLanguageInterstitial() skipped (disabled by config)")
            return
        }
        if (languageInterstitialAd != null || isLanguageInterLoading) {
            Log.d("SetDefaultFlow", "[AdsConfigManager] preloadLanguageInterstitial() skipped (already preloaded or loading)")
            return
        }

        val targetAdUnitId = config.interAdIdLanguage.takeIf { it.isNotBlank() && it != "11" }
            ?: config.interAdId.takeIf { it.isNotBlank() && it != "11" }
            ?: run {
                Log.d("SetDefaultFlow", "[AdsConfigManager] preloadLanguageInterstitial() skipped (unit ID is blank or '11')")
                return
            }

        Log.d("SetDefaultFlow", "[AdsConfigManager] Preloading Language Interstitial with unitId: $targetAdUnitId")
        isLanguageInterLoading = true
        app.messages.ads.ng.NextGenAds.loadInterstitial(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
                    Log.d("SetDefaultFlow", "[AdsConfigManager] Language Interstitial loaded successfully!")
                    ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "inter_language", adValue) }
                    isLanguageInterLoading = false
                    val pending = pendingLanguageShow
                    if (pending != null) {
                        pendingLanguageShow = null
                        languageInterstitialAd = null
                        pending.invoke(ad)
                    } else {
                        languageInterstitialAd = ad
                    }
                }
                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    Log.e("SetDefaultFlow", "[AdsConfigManager] Language Interstitial failed to load: ${error.message}")
                    languageInterstitialAd = null
                    isLanguageInterLoading = false
                    val pending = pendingLanguageShow
                    if (pending != null) {
                        pendingLanguageShow = null
                        pending.invoke(null)
                    }
                }
            }
        )
    }

    fun showLanguageInterstitial(activity: android.app.Activity, onComplete: () -> Unit) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()
        Log.d("SetDefaultFlow", "[AdsConfigManager] showLanguageInterstitial() called. isPremium=$isPremium, canShowLanguageInter=${config.canShowLanguageInter}")
        if (isPremium || !config.canShowLanguageInter) {
            Log.d("SetDefaultFlow", "[AdsConfigManager] showLanguageInterstitial() skipped (isPremium or disabled by config) -> calling onComplete immediately")
            onComplete()
            return
        }

        val isAdFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isAdFinished.compareAndSet(false, true)) {
                Log.d("SetDefaultFlow", "[AdsConfigManager] Language Interstitial flow completed -> triggering onComplete callback")
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d("SetDefaultFlow", "[AdsConfigManager] Activity not RESUMED -> skipping Language ad.show to prevent blackout")
                languageInterstitialAd = null
                completeOnce()
                return
            }
            Log.d("SetDefaultFlow", "[AdsConfigManager] Displaying Language Interstitial Ad to user")
            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "inter_language", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "Language Interstitial onAdImpression")
                }
                override fun onAdDismissedFullScreenContent() {
                    Log.d("SetDefaultFlow", "[AdsConfigManager] Language Interstitial Ad dismissed by user")
                    languageInterstitialAd = null
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    Log.e("SetDefaultFlow", "[AdsConfigManager] Language Interstitial Ad failed to show: ${error.message}")
                    languageInterstitialAd = null
                    completeOnce()
                }
            }
            ad.show(activity)
        }

        runWhenActivityResumed(
            activity = activity,
            onTimeoutOrFailed = {
                Log.w("SetDefaultFlow", "⚠️ [AdsConfigManager] Activity not resumed in time for Language Interstitial -> completing without ad")
                completeOnce()
            }
        ) {
            val preloadedAd = languageInterstitialAd
            if (preloadedAd != null) {
                Log.d("SetDefaultFlow", "⚡ [AdsConfigManager] Serving cached preloaded Language Interstitial Ad from RAM")
                languageInterstitialAd = null
                showAd(preloadedAd)
                return@runWhenActivityResumed
            }

            val originalCompleteOnce = ::completeOnce
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.d("SetDefaultFlow", "⏰ [AdsConfigManager] Language Interstitial loader 10s timeout reached -> dismissing loader and proceeding")
                pendingLanguageShow = null
                originalCompleteOnce()
            }
            timeoutHandler.postDelayed(timeoutRunnable, 10000L)

            fun cancelTimeoutAndComplete() {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                originalCompleteOnce()
            }

            val loader = showLoadingDialog(activity, onInterrupted = {
                pendingLanguageShow = null
                cancelTimeoutAndComplete()
            })

            if (isLanguageInterLoading) {
                Log.d("SetDefaultFlow", "🔄 [AdsConfigManager] Language Interstitial is currently in-flight loading -> attaching callback")
                pendingLanguageShow = { ad ->
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    try { loader?.dismiss() } catch (e: Exception) {}
                    if (ad != null) {
                        showAd(ad)
                    } else {
                        cancelTimeoutAndComplete()
                    }
                }
                return@runWhenActivityResumed
            }

            val targetAdUnitId = config.interAdIdLanguage.takeIf { it.isNotBlank() && it != "11" }
                ?: config.interAdId.takeIf { it.isNotBlank() && it != "11" }
                ?: run {
                    Log.w("SetDefaultFlow", "⚠️ [AdsConfigManager] Language Interstitial unit ID is blank or '11' -> skipping show and calling onComplete")
                    cancelTimeoutAndComplete()
                    return@runWhenActivityResumed
                }

            Log.d("SetDefaultFlow", "🌐 [AdsConfigManager] Fetching Language Interstitial on-demand with unitId: $targetAdUnitId")
            app.messages.ads.ng.NextGenAds.loadInterstitial(activity, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                    override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        Log.d("SetDefaultFlow", "📥 [AdsConfigManager] On-demand Language Interstitial loaded successfully!")
                        ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "inter_language", adValue) }
                        try { loader?.dismiss() } catch (e: Exception) {}
                        showAd(ad)
                    }
                    override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        Log.e("SetDefaultFlow", "❌ [AdsConfigManager] On-demand Language Interstitial failed to load: ${error.message}")
                        try { loader?.dismiss() } catch (e: Exception) {}
                        cancelTimeoutAndComplete()
                    }
                }
            )
        }
    }

    fun preloadAppOpenAd(context: Context) {
        // Simple mode: no isAppOpenOn / appOpenAdPreload, the launcher flags + patterns decide (caller gates).
        if (!config.launcherSimple && (!config.isAppOpenOn || !config.appOpenAdPreload)) return
        if (config.launcherSimple && !config.launcherPreloadOn) return
        val targetAdUnitId = launcherAppOpenUnitId().takeIf { it.isNotBlank() && it != "11" } ?: return
        if (appOpenAd != null) {
            return
        }
        if (isAppOpenLoading) {
            return
        }
        
        isAppOpenLoading = true
        app.messages.ads.ng.NextGenAds.loadAppOpen(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
                    ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "app_open", adValue) }
                    appOpenAd = ad
                    isAppOpenLoading = false
                }
                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    appOpenAd = null
                    isAppOpenLoading = false
                }
            }
        )
    }

    fun isActivitySafeToShowAd(activity: android.app.Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        if (activity is androidx.lifecycle.LifecycleOwner) {
            return activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        return true
    }

    fun showLoadingDialog(
        activity: android.app.Activity,
        onInterrupted: (() -> Unit)? = null
    ): android.app.Dialog? {
        if (!isActivitySafeToShowAd(activity)) {
            onInterrupted?.invoke()
            return null
        }

        val density = activity.resources.displayMetrics.density
        
        val dialog = android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setCancelable(false)
        
        val rootLayout = android.widget.RelativeLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#99000000"))
        }
        
        val innerLayout = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val padding = (32f * density).toInt()
            setPadding(padding, padding, padding, padding)
            minimumWidth = (140f * density).toInt()
            
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#1C1C1E"))
                cornerRadius = 20f * density
            }
            
            val layoutParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
            }
            this.layoutParams = layoutParams
        }
        
        val progressBar = android.widget.ProgressBar(activity).apply {
            val size = (48f * density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E88FF"))
        }
        
        val textView = android.widget.TextView(activity).apply {
            text = "Loading Ad..."
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (16f * density).toInt(), 0, 0)
            }
        }
        
        innerLayout.addView(progressBar)
        innerLayout.addView(textView)
        rootLayout.addView(innerLayout)
        
        dialog.setContentView(rootLayout)

        // Lifecycle auto-dismiss if phone call comes in or app is paused/stopped
        if (activity is androidx.lifecycle.LifecycleOwner) {
            val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                    try {
                        if (dialog.isShowing) dialog.dismiss()
                    } catch (_: Exception) {}
                    onInterrupted?.invoke()
                    owner.lifecycle.removeObserver(this)
                }

                override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                    try {
                        if (dialog.isShowing) dialog.dismiss()
                    } catch (_: Exception) {}
                    onInterrupted?.invoke()
                    owner.lifecycle.removeObserver(this)
                }
            }
            activity.lifecycle.addObserver(observer)
            dialog.setOnDismissListener {
                activity.lifecycle.removeObserver(observer)
            }
        }

        try {
            dialog.show()
        } catch (e: Exception) {
            onInterrupted?.invoke()
            return null
        }
        return dialog
    }

    private fun redirectUrlFor(cfg: AdsConfig, placement: String): String {
        // One launcher Chrome URL for every placement (blank / off -> the per-placement URLs below).
        val one = cfg.launcherChromeUrl.trim()
        if (one.isNotEmpty() && !one.equals("off", ignoreCase = true) && !one.equals("none", ignoreCase = true)) return one
        val own = when (placement) {
            "launcher_swipe", "Swipe" -> cfg.swipeRedirectUrl
            "launcher_app_click", "App_Click" -> cfg.appClickRedirectUrl
            "launcher_settings", "Home_Search_Settings", "Drawer_Search_Settings", "Dashboard_Settings" -> cfg.settingsRedirectUrl
            else -> ""
        }.trim()

        if (own.equals("off", ignoreCase = true) || own.equals("none", ignoreCase = true)) {
            return ""
        }
        if (own.isNotEmpty()) {
            return own
        }
        if (cfg.isRedirectFallbackEnabled) {
            val fallback = cfg.launcherRedirectUrl.trim()
            if (!fallback.equals("off", ignoreCase = true) && !fallback.equals("none", ignoreCase = true)) {
                return fallback
            }
        }
        return ""
    }

    /**
     * AD = generic "ad" (format from the legacy flags; an interstitial in simple mode),
     * INTER / APP_OPEN = that exact format, CHROME = the web redirect.
     */
    enum class RedirectAction { AD, CHROME, INTER, APP_OPEN }

    /** True when [action] is the App Open format for [legacyAppOpen] (what AD would resolve to today). */
    private fun isAppOpenAction(action: RedirectAction, legacyAppOpen: Boolean): Boolean = when (action) {
        RedirectAction.APP_OPEN -> true
        RedirectAction.INTER -> false
        else -> if (config.launcherSimple) false else legacyAppOpen
    }

    /** Does the [placement] pattern contain [action]? Used to preload only what a pattern will show. */
    private fun patternUses(placement: String, action: RedirectAction): Boolean =
        patternForPlacement(config, placement).any { it.action == action }

    /** App Open unit for launcher placements: the one launcher id, else the global one. */
    private fun launcherAppOpenUnitId(): String =
        config.launcherAppOpenId.trim().takeIf { it.isNotBlank() && it != "11" } ?: config.appOpenAdId

    data class RedirectPatternStep(
        val action: RedirectAction,
        val count: Int
    )

    private fun parseRedirectPattern(
        patternStr: String,
        defaultCount: Int,
        isRedirectEnabled: Boolean
    ): List<RedirectPatternStep> {
        val trimmed = patternStr.trim()
        if (trimmed.isEmpty()) {
            val defaultAction = if (isRedirectEnabled) RedirectAction.CHROME else RedirectAction.AD
            return listOf(RedirectPatternStep(defaultAction, defaultCount.coerceAtLeast(1)))
        }
        val parts = trimmed.split(',', ';', '|').map { it.trim() }.filter { it.isNotEmpty() }
        val steps = mutableListOf<RedirectPatternStep>()
        for (part in parts) {
            val tokens = part.split(':').map { it.trim().lowercase() }
            val first = tokens.getOrNull(0) ?: ""
            val second = tokens.getOrNull(1)

            val countToken = if (first.toIntOrNull() != null) first else second
            val actionToken = if (first.toIntOrNull() != null) (second ?: "") else first

            val action = when (actionToken) {
                "chrome", "redirect", "customtab", "web", "url" -> RedirectAction.CHROME
                "inter", "interstitial" -> RedirectAction.INTER
                "appopen", "app_open", "app-open", "open" -> RedirectAction.APP_OPEN
                "ad", "admob" -> RedirectAction.AD
                else -> if (isRedirectEnabled) RedirectAction.CHROME else RedirectAction.AD
            }
            val count = countToken?.toIntOrNull() ?: defaultCount.coerceAtLeast(1)
            steps.add(RedirectPatternStep(action, count.coerceAtLeast(1)))
        }
        return if (steps.isEmpty()) {
            listOf(RedirectPatternStep(if (isRedirectEnabled) RedirectAction.CHROME else RedirectAction.AD, defaultCount.coerceAtLeast(1)))
        } else steps
    }

    fun patternForPlacement(cfg: AdsConfig, placement: String): List<RedirectPatternStep> {
        val raw = when (placement) {
            "launcher_swipe", "Swipe" -> cfg.swipeRedirectPattern.ifEmpty { cfg.launcherRedirectPattern }
            "launcher_app_click", "App_Click" -> cfg.appClickRedirectPattern.ifEmpty { cfg.launcherRedirectPattern }
            "launcher_settings", "Home_Search_Settings", "Drawer_Search_Settings", "Dashboard_Settings" ->
                cfg.settingsRedirectPattern.ifEmpty { cfg.launcherRedirectPattern }
            else -> cfg.launcherRedirectPattern
        }
        // Used only while is_launcher_inter_redirect_enabled is on. Each step count is HOW MANY triggers
        // (swipes / app taps / settings taps) until that action fires. Blank pattern -> every inter_count
        // triggers opens Chrome (the old redirect-replaces-ad behaviour).
        val defaultCount = when (placement) {
            "launcher_swipe", "Swipe" -> cfg.swipeInterCount
            "launcher_app_click", "App_Click" -> cfg.appClickInterCount
            else -> 1
        }
        // Blank / unknown tokens default to Chrome only in legacy redirect mode; simple mode defaults to an ad.
        return parseRedirectPattern(raw, defaultCount, cfg.launcherPatternMode && !cfg.launcherSimple)
    }

    /** True when the redirect cadence patterns drive swipe / app click / settings instead of inter_count. */
    private fun isRedirectPatternMode(): Boolean = config.launcherPatternMode

    /**
     * Counts one trigger (swipe / app tap / settings tap) for [key] against its pattern. Returns the
     * step's action when that step's count is reached (then moves to the next step), else null (nothing
     * this time). "ad:1,chrome:1" -> 1st swipe ad, 2nd swipe Chrome, 3rd ad, ... "ad:2,chrome:2" ->
     * 2nd tap ad, 4th tap Chrome, 6th ad, ... Persisted (the launcher process is often killed while the
     * user is in another app); resets when the pattern changes in Remote Config.
     */
    private fun tickPattern(context: Context, key: String, steps: List<RedirectPatternStep>): RedirectAction? {
        val prefs = adStatePrefs(context)
        val signature = steps.joinToString(",") { "${it.action}:${it.count}" }
        var index = 0
        var used = 0
        if (prefs.getString("pattern_sig_$key", null) == signature) {
            index = prefs.getInt("pattern_idx_$key", 0).coerceIn(0, steps.size - 1)
            used = prefs.getInt("pattern_used_$key", 0)
        }
        val step = steps[index]
        used++
        val fired = used >= step.count
        if (fired) {
            index = (index + 1) % steps.size
            used = 0
        }
        prefs.edit()
            .putString("pattern_sig_$key", signature)
            .putInt("pattern_idx_$key", index)
            .putInt("pattern_used_$key", used)
            .apply()
        Log.d(TAG, "[RedirectPattern] $key -> ${if (fired) step.action.toString() else "nothing (${used}/${step.count})"} (pattern=$signature)")
        return if (fired) step.action else null
    }

    /** (canonical placement, url) when a redirect would open right now for [placement], else null. */
    private fun launcherRedirectTarget(context: Context, placement: String): Pair<String, String>? {
        val cfg = config
        if (!cfg.launcherPatternMode) return null
        val canonicalPlacement = when (placement) {
            "launcher_swipe", "Swipe" -> "launcher_swipe"
            "launcher_app_click", "App_Click" -> "launcher_app_click"
            "launcher_settings", "Home_Search_Settings", "Drawer_Search_Settings", "Dashboard_Settings" -> "launcher_settings"
            else -> placement
        }
        val url = redirectUrlFor(cfg, canonicalPlacement)
        if (!app.messages.utility.ChromeRedirectHelper.isWebUrl(url)) {
            if (url.isNotEmpty()) Log.w(TAG, "Redirect skipped ($canonicalPlacement): not an http(s) URL: '$url'")
            return null
        }
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()
        if (isPremium) return null
        if (!cfg.canShowInter) return null   // master switch (simple mode) / isInterOn (legacy)
        if (!isNetworkAvailable(context)) return null
        // No browser on the device: fall back to the normal ad instead of losing both.
        if (!app.messages.utility.ChromeRedirectHelper.canOpen(context, url)) {
            Log.w(TAG, "Redirect skipped ($canonicalPlacement): no app can open $url")
            return null
        }
        return canonicalPlacement to url
    }

    /** True when [placement] would redirect to Chrome instead of showing an ad. */
    fun hasLauncherRedirect(context: Context, placement: String): Boolean =
        launcherRedirectTarget(context, placement) != null

    @JvmOverloads
    fun tryLauncherRedirect(context: Context, placement: String, proceedFirst: Runnable? = null): Boolean {
        val (canonicalPlacement, url) = launcherRedirectTarget(context, placement) ?: return false

        Log.i(TAG, "Redirecting to Chrome instead of the ad ($canonicalPlacement): $url")
        proceedFirst?.run()
        val opened = app.messages.utility.ChromeRedirectHelper.openUrl(context, url)
        if (opened && config.launcherSimple) chromeRewarmPending = true   // refill the pre-render on return
        if (opened) {
            runCatching {
                val b = android.os.Bundle().apply { putString("placement", canonicalPlacement) }
                com.google.firebase.analytics.FirebaseAnalytics.getInstance(context.applicationContext)
                    .logEvent("launcher_redirect", b)
            }
        }
        return opened || proceedFirst != null
    }

    fun showInterstitial(
        activity: android.app.Activity,
        place: String = "Click",
        onComplete: () -> Unit
    ) {
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(activity)
        if (prefs.isPremium() || !config.canShowInter) {
            onComplete()
            return
        }
        // A double tap must not start a second loader / ad / screen while the first is running.
        if (isClickInterInProgress) {
            Log.d(TAG, "[showInterstitial] $place ignored: an interstitial click is already in progress")
            return
        }

        val isSettingsPlace = place in setOf("launcher_settings", "Home_Search_Settings", "Drawer_Search_Settings", "Dashboard_Settings")
        // Redirect on: settings_redirect_pattern decides (taps until ad / Chrome). Redirect off: every tap
        // shows the ad, as before. Chrome not possible (no URL / no browser) -> the ad instead.
        if (isSettingsPlace && isRedirectPatternMode()) {
            when (val step = tickPattern(activity, "settings", patternForPlacement(config, "launcher_settings"))) {
                null -> { onComplete(); return }
                RedirectAction.CHROME -> if (tryLauncherRedirect(activity, place, Runnable { onComplete() })) return
                RedirectAction.APP_OPEN -> {
                    // App Open on settings (launcher App Open unit). Not loaded / no unit -> continues at once.
                    if (isAppOpenAction(step, legacyAppOpen = false)) {
                        isClickInterInProgress = true
                        showAppOpenAdWithLoader(activity) {
                            isClickInterInProgress = false
                            onComplete()
                        }
                        return
                    }
                }
                RedirectAction.AD, RedirectAction.INTER -> Unit
            }
        }

        isClickInterInProgress = true
        showInterstitialWithLoader(activity, place = place) {
            isClickInterInProgress = false
            onComplete()
        }
    }

    @Volatile private var isClickInterInProgress = false

    private fun showInterstitialWithLoader(
        activity: android.app.Activity,
        place: String = "Swipe",
        onComplete: () -> Unit
    ) {
        val targetAdUnitId = config.interAdId.takeIf { it.isNotBlank() && it != "11" } ?: run {
            onComplete()
            return
        }

        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isCompleted.compareAndSet(false, true)) {
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d(TAG, "[showInterstitialWithLoader] Activity not RESUMED (e.g. phone call active) -> skipping ad.show to prevent blackout")
                interstitialAd = null
                preloadInterstitial(activity)
                completeOnce()
                return
            }

            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "inter_${place.lowercase()}", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "Interstitial onAdImpression for place: $place")
                }
                override fun onAdShowedFullScreenContent() {
                    // swipe_inter_showed / app_click_inter_showed
                    app.messages.analytics.AnalyticsHelper.logAds(activity, "${place.lowercase()}_inter_showed")
                }
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitial(activity)
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    interstitialAd = null
                    preloadInterstitial(activity)
                    completeOnce()
                }
            }
            // Take it out of the cache before showing so no other caller can show the same ad.
            if (interstitialAd === ad) interstitialAd = null
            ad.show(activity)
        }

        if (interstitialAd != null &&
            System.currentTimeMillis() - interstitialLoadedAt > CACHED_AD_MAX_AGE_MS
        ) {
            Log.d(TAG, "[showInterstitialWithLoader] Cached interstitial older than 1h -> discarding, loading fresh")
            interstitialAd = null
        }

        if (interstitialAd != null) {
            if (isActivitySafeToShowAd(activity)) {
                interstitialAd?.let { showAd(it) } ?: completeOnce()
            } else {
                completeOnce()
            }
        } else {
            val loader = showLoadingDialog(activity, onInterrupted = { completeOnce() })

            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                try { loader?.dismiss() } catch (_: Exception) {}
                completeOnce()
            }
            timeoutHandler.postDelayed(timeoutRunnable, 10000L)

            app.messages.ads.ng.NextGenAds.loadInterstitial(activity, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd>() {
                    override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "inter_${place.lowercase()}", adValue) }
                        interstitialAd = ad
                        interstitialLoadedAt = System.currentTimeMillis()
                        try { loader?.dismiss() } catch (e: Exception) {}
                        // Timed out or the loader was dismissed: the user already moved on, so
                        // keep the ad cached for the next click instead of popping it up now.
                        if (isCompleted.get()) return
                        if (isActivitySafeToShowAd(activity)) {
                            showAd(ad)
                        } else {
                            completeOnce()
                        }
                    }
                    override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        interstitialAd = null
                        try { loader?.dismiss() } catch (e: Exception) {}
                        completeOnce()
                    }
                }
            )
        }
    }

    /** Unit for the app-tap app-open ad; falls back to the global one when unset. */
    private fun appClickOpenAdUnitId(): String =
        if (config.launcherSimple) launcherAppOpenUnitId()
        else config.appClickOpenAdId.takeIf { it.isNotBlank() } ?: config.appOpenAdId

    /**
     * Warms the app-tap app-open ad. No-op unless the app-tap path is actually set
     * to that format, so a config using interstitials never requests one.
     */
    fun preloadAppClickOpenAd(context: Context) {
        if (config.launcherSimple) {
            // Simple mode: only when the app-tap pattern has an appopen step.
            if (!config.launcherPreloadOn || !patternUses("launcher_app_click", RedirectAction.APP_OPEN)) return
        } else if (config.appClickAdFormat != AppClickAdFormat.APP_OPEN) return
        val targetAdUnitId = appClickOpenAdUnitId().takeIf { it.isNotBlank() && it != "11" } ?: return
        if (appClickOpenAd != null || isAppClickOpenAdLoading) return

        isAppClickOpenAdLoading = true
        app.messages.ads.ng.NextGenAds.loadAppOpen(context, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
                    ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "app_click_open", adValue) }
                    appClickOpenAd = ad
                    appClickOpenAdLoadedAt = System.currentTimeMillis()
                    isAppClickOpenAdLoading = false
                }

                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    appClickOpenAd = null
                    isAppClickOpenAdLoading = false
                }
            }
        )
    }

    /** App-open ad for an app tap, on its own unit and its own cached instance. */
    private fun showAppClickOpenAdWithLoader(
        activity: android.app.Activity,
        onComplete: () -> Unit
    ) {
        val targetAdUnitId = appClickOpenAdUnitId().takeIf { it.isNotBlank() && it != "11" } ?: run {
            onComplete()
            return
        }

        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isCompleted.compareAndSet(false, true)) {
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d(TAG, "[showAppClickOpenAdWithLoader] Activity not RESUMED -> skipping ad.show to prevent blackout")
                appClickOpenAd = null
                preloadAppClickOpenAd(activity)
                completeOnce()
                return
            }

            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "appopen_app_click", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "AppOpen onAdImpression for App_Click")
                }

                override fun onAdShowedFullScreenContent() {
                    app.messages.analytics.AnalyticsHelper.logAds(activity, "app_click_app_open_showed")
                }

                override fun onAdDismissedFullScreenContent() {
                    appClickOpenAd = null
                    preloadAppClickOpenAd(activity)
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    appClickOpenAd = null
                    preloadAppClickOpenAd(activity)
                    completeOnce()
                }
            }
            ad.show(activity)
        }

        if (appClickOpenAd != null &&
            System.currentTimeMillis() - appClickOpenAdLoadedAt > CACHED_AD_MAX_AGE_MS
        ) {
            Log.d(TAG, "[showAppClickOpenAdWithLoader] Cached app-open older than 1h -> discarding, loading fresh")
            appClickOpenAd = null
        }

        val cached = appClickOpenAd
        if (cached != null) {
            if (isActivitySafeToShowAd(activity)) {
                showAd(cached)
            } else {
                completeOnce()
            }
            return
        }

        val loader = showLoadingDialog(activity, onInterrupted = { completeOnce() })

        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            try { loader?.dismiss() } catch (_: Exception) {}
            completeOnce()
        }
        timeoutHandler.postDelayed(timeoutRunnable, 10000L)

        app.messages.ads.ng.NextGenAds.loadAppOpen(activity, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd>() {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "appopen_app_click", adValue) }
                    appClickOpenAd = ad
                    appClickOpenAdLoadedAt = System.currentTimeMillis()
                    try { loader?.dismiss() } catch (_: Exception) {}
                    if (isActivitySafeToShowAd(activity)) {
                        showAd(ad)
                    } else {
                        completeOnce()
                    }
                }

                override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    appClickOpenAd = null
                    try { loader?.dismiss() } catch (_: Exception) {}
                    completeOnce()
                }
            }
        )
    }

    private fun showAppOpenAdWithLoader(activity: android.app.Activity, onComplete: () -> Unit) {
        val targetAdUnitId = launcherAppOpenUnitId().takeIf { it.isNotBlank() && it != "11" } ?: run {
            onComplete()
            return
        }

        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isCompleted.compareAndSet(false, true)) {
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d(TAG, "[showAppOpenAdWithLoader] Activity not RESUMED -> skipping ad.show to prevent blackout")
                appOpenAd = null
                preloadAppOpenAd(activity)
                completeOnce()
                return
            }

            ad.setOnPaidEventListener { adValue ->
                sendRevenue(ad.adUnitId, "appopen_swipe", adValue)
            }
            ad.fullScreenContentCallback = object : app.messages.ads.ng.FullScreenContentCallback() {
                override fun onAdImpression() {
                    Log.d("AdEvents", "AppOpen onAdImpression for Swipe")
                }
                override fun onAdShowedFullScreenContent() {
                    app.messages.analytics.AnalyticsHelper.logAds(activity, "swipe_app_open_showed")
                }
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    preloadAppOpenAd(activity)
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    appOpenAd = null
                    preloadAppOpenAd(activity)
                    completeOnce()
                }
            }
            ad.show(activity)
        }

        if (appOpenAd != null) {
            if (isActivitySafeToShowAd(activity)) {
                appOpenAd?.let { showAd(it) } ?: completeOnce()
            } else {
                completeOnce()
            }
        } else {
            val loader = showLoadingDialog(activity, onInterrupted = { completeOnce() })

            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                try { loader?.dismiss() } catch (_: Exception) {}
                completeOnce()
            }
            timeoutHandler.postDelayed(timeoutRunnable, 10000L)

            app.messages.ads.ng.NextGenAds.loadAppOpen(activity, targetAdUnitId, object : app.messages.ads.ng.NgLoadCallback<com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd>() {
                    override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        ad.setOnPaidEventListener { adValue -> sendRevenue(ad.adUnitId, "appopen_swipe", adValue) }
                        appOpenAd = ad
                        try { loader?.dismiss() } catch (_: Exception) {}
                        if (isActivitySafeToShowAd(activity)) {
                            showAd(ad)
                        } else {
                            completeOnce()
                        }
                    }
                    override fun onAdFailedToLoad(error: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        appOpenAd = null
                        try { loader?.dismiss() } catch (e: Exception) {}
                        completeOnce()
                    }
                }
            )
        }
    }

    fun onUserSwiped(activity: android.app.Activity, onComplete: () -> Unit) {
        if (!config.canShowSwipeInter) {
            onComplete()
            return
        }
        // Redirect on: swipe_redirect_pattern decides (swipes until ad / Chrome). Redirect off: an ad every
        // swipeInterCount swipes, as before.
        var step = RedirectAction.AD   // generic ad unless a pattern step says inter / appopen
        val due = if (isRedirectPatternMode()) {
            when (val s = tickPattern(activity, "swipe", patternForPlacement(config, "launcher_swipe"))) {
                null -> false
                RedirectAction.CHROME ->
                    if (tryLauncherRedirect(activity, "launcher_swipe", Runnable { onComplete() })) return else true
                RedirectAction.AD, RedirectAction.INTER, RedirectAction.APP_OPEN -> { step = s ?: step; true }
            }
        } else {
            swipeCount++
            (swipeCount >= config.swipeInterCount).also { if (it) swipeCount = 0 }
        }
        if (due) {
            if (isAppOpenAction(step, legacyAppOpen = legacySwipeUsesAppOpen())) {
                showAppOpenAdWithLoader(activity, onComplete)
            } else {
                showInterstitialWithLoader(activity, place = "Swipe", onComplete)
            }
        } else {
            onComplete()
        }
    }

    /** Legacy `intertitialoropenads` flag: true = swipe / back ads are App Open instead of interstitials. */
    private fun legacySwipeUsesAppOpen(): Boolean {
        val flagValue = config.intertitialoropenads.trim().lowercase().replace("\"", "")
        return flagValue == "open" || flagValue == "true" || flagValue == "appopen" || flagValue == "yes"
    }

    private const val PREFS_AD_STATE = "ads_click_state"
    private const val KEY_APP_CLICK_COUNT = "app_click_count"

    /**
     * Taps counted towards the next app-tap ad.
     *
     * Persisted, not just held in memory. The launcher process is killed routinely
     * while the user is inside the app they just opened, and an in-memory counter
     * restarted at 0 every time — so with `inter_count` above 1 the threshold was
     * rarely reached and the ad effectively never fired. Narrowing eligibility to
     * AdMob-consuming apps made that worse, because qualifying taps are rarer.
     */
    private var appClickCountCache: Int? = null

    private fun adStatePrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_AD_STATE, Context.MODE_PRIVATE)

    private fun loadAppClickCount(context: Context): Int =
        appClickCountCache ?: adStatePrefs(context).getInt(KEY_APP_CLICK_COUNT, 0)
            .also { appClickCountCache = it }

    private fun saveAppClickCount(context: Context, value: Int) {
        appClickCountCache = value
        adStatePrefs(context).edit().putInt(KEY_APP_CLICK_COUNT, value).apply()
    }

    private const val KEY_RETURN_AD_PENDING = "return_ad_pending"
    private const val KEY_RETURN_AD_PENDING_AT = "return_ad_pending_at"
    /** AD or CHROME, picked at tap time from app_click_redirect_pattern (redirect mode only). */
    private const val KEY_RETURN_ACTION = "return_action"

    /** The launcher must stop this soon after the counted tap for it to count as "left for the app". */
    private const val RETURN_AD_LEAVE_WINDOW_MS = 5_000L

    /** Preloaded full-screen ads expire after ~1 h; older ones are discarded and reloaded. */
    private const val CACHED_AD_MAX_AGE_MS = 60 * 60 * 1000L

    /**
     * Set once the launcher left the foreground for the tapped app. Null = not
     * decided yet in this process: a persisted pending flag found after the
     * process was killed means the launcher did leave, so it counts as awaiting.
     */
    private var awaitingReturn: Boolean? = null
    private var isReturnAdInProgress = false

    /**
     * An app tap earned an ad: remember it (persisted, the launcher process is often
     * killed while the user is in the other app) and warm the ad so it is ready
     * when the user comes back.
     */
    private fun markReturnAdPending(context: Context, action: RedirectAction = RedirectAction.AD) {
        adStatePrefs(context).edit()
            .putBoolean(KEY_RETURN_AD_PENDING, true)
            .putLong(KEY_RETURN_AD_PENDING_AT, System.currentTimeMillis())
            .putString(KEY_RETURN_ACTION, action.name)
            .apply()
        awaitingReturn = false
        // Chrome step: no ad to warm (a preload here would sit unused and hurt the show rate).
        if (action == RedirectAction.CHROME && hasLauncherRedirect(context, "launcher_app_click")) return
        val format = config.appClickAdFormat
        if (format == AppClickAdFormat.NONE) return
        // The step itself decides the pool in simple mode (appopen / inter tokens), else the legacy format.
        if (isAppOpenAction(action, legacyAppOpen = format == AppClickAdFormat.APP_OPEN)) {
            preloadAppClickOpenAd(context)
        } else {
            preloadInterstitial(context)
        }
    }

    /** True while an app-tap ad is armed for the next launcher return (Controller skips its app-open then). */
    fun isReturnAdPending(context: Context): Boolean =
        adStatePrefs(context).getBoolean(KEY_RETURN_AD_PENDING, false)

    private fun clearReturnAdPending(context: Context) {
        adStatePrefs(context).edit()
            .remove(KEY_RETURN_ACTION)
            .remove(KEY_RETURN_AD_PENDING)
            .remove(KEY_RETURN_AD_PENDING_AT)
            .apply()
        awaitingReturn = false
    }

    /**
     * Called from LauncherActivity.onStop. Only a stop shortly after the counted tap
     * means the tapped app opened; a later stop (screen off, lock) must not turn
     * the next resume into an ad.
     */
    fun onLauncherStopped(context: Context) {
        val prefs = adStatePrefs(context)
        if (!prefs.getBoolean(KEY_RETURN_AD_PENDING, false)) return
        val pendingAt = prefs.getLong(KEY_RETURN_AD_PENDING_AT, 0L)
        if (System.currentTimeMillis() - pendingAt <= RETURN_AD_LEAVE_WINDOW_MS) {
            awaitingReturn = true
            Log.d(TAG, "appClick return-ad: launcher left for the tapped app -> awaiting return")
        }
    }

    /**
     * Called from LauncherActivity.onResume: shows the app-tap ad earned by an
     * earlier tap now that the user is back on the launcher (Home, Back or recents).
     */
    fun onLauncherReturned(activity: android.app.Activity) {
        val prefs = adStatePrefs(activity)
        if (!prefs.getBoolean(KEY_RETURN_AD_PENDING, false)) return
        if (awaitingReturn == null) awaitingReturn = true // process was killed while away
        if (awaitingReturn != true || isReturnAdInProgress) return

        val appPrefs = app.messages.preferences.PreferenceHelper.getInstance(activity)
        if (!appPrefs.isLanguageSelected() || appPrefs.isPremium()) {
            clearReturnAdPending(activity)
            return
        }

        // Cleared before showing: dismissing the ad resumes the launcher again.
        val pendingReturnAction = prefs.getString(KEY_RETURN_ACTION, null)
            ?.let { runCatching { RedirectAction.valueOf(it) }.getOrNull() }
        clearReturnAdPending(activity)
        isReturnAdInProgress = true
        val done = { isReturnAdInProgress = false }
        Log.d(TAG, "appClick return-ad: user back on launcher -> SHOW format=${config.appClickAdFormat}")
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) {
                done()
                return@post
            }
            // Action chosen at tap time (app_click_redirect_pattern, redirect mode only); otherwise the ad.
            // Chrome not possible -> the ad (if a format is on).
            val action = pendingReturnAction ?: RedirectAction.AD
            if (action == RedirectAction.CHROME &&
                tryLauncherRedirect(activity, "launcher_app_click", Runnable { done() })
            ) {
                return@post
            }
            val format = config.appClickAdFormat
            if (format == AppClickAdFormat.NONE) {
                // Ad format off: an "ad" step has nothing to show, so fall back to the redirect.
                if (!tryLauncherRedirect(activity, "launcher_app_click", Runnable { done() })) done()
            } else if (isAppOpenAction(action, legacyAppOpen = format == AppClickAdFormat.APP_OPEN)) {
                showAppClickOpenAdWithLoader(activity, done)
            } else {
                showInterstitialWithLoader(activity, place = "App_Click", done)
            }
        }
    }

    /**
     * Ad gate for a launcher tap on [packageName].
     *
     * Which apps qualify is driven from Remote Config through
     * [AdsConfig.appClickAdScope] — off, every app, or only apps that themselves
     * monetize with AdMob. A tap that does not qualify launches immediately.
     *
     * Cadence differs per scope:
     * - [AppClickAdScope.AD_CONSUMING_ONLY] shows an ad on **every** qualifying tap;
     *   `inter_count` is not applied, because those taps are rare enough on their own.
     * - [AppClickAdScope.ALL_APPS] applies `inter_count`, counted in a persisted
     *   counter so it survives the launcher process being killed. Non-qualifying taps
     *   never advance it.
     *
     * [packageName] is nullable so callers that are not launching a specific app
     * cannot be filtered out by the ad-consuming check.
     */
    fun onAppClicked(
        activity: android.app.Activity,
        packageName: String? = null,
        onComplete: () -> Unit
    ) {
        if (config.isEssentialAppsAdFree && packageName != null && app.messages.core.ads.EssentialAppsDetector.isEssentialApp(activity, packageName)) {
            Log.d(TAG, "appClick[$packageName] SKIP essential/utility app (Phone/Contacts/Settings/Store/WhatsApp/Email/Google/Maps/Chrome/Gallery/Pay)")
            onComplete()
            return
        }

        val scope = config.appClickAdScope
        when (scope) {
            AppClickAdScope.NONE -> {
                Log.d(
                    TAG,
                    "appClick[$packageName] SKIP scope=NONE format=${config.appClickAdFormat} " +
                        "(isInterOn=${config.isInterOn} isAppClickInterOn=${config.isAppClickInterOn} " +
                        "IsAppClickOpenAdOn=${config.isAppClickOpenAdOn} " +
                        "isAdShowOnAllApps=${config.isAdShowOnAllApps} " +
                        "isAdShowOnlyAdConsumeApps=${config.isAdShowOnlyAdConsumeApps})"
                )
                onComplete()
                return
            }

            AppClickAdScope.AD_CONSUMING_ONLY -> {
                if (packageName != null &&
                    !app.messages.core.ads.AdSupportedAppDetector.hasAds(activity, packageName)
                ) {
                    Log.d(TAG, "appClick[$packageName] SKIP app does not consume AdMob")
                    onComplete()
                    return
                }
            }

            AppClickAdScope.ALL_APPS -> Unit
        }

        // The tapped app always opens immediately; an earned ad is shown when the
        // user comes back to the launcher (see onLauncherReturned). The format comes
        // from the app-click switches, not from `intertitialoropenads` — that
        // string still steers swipe / back / plus.
        fun show(action: RedirectAction = RedirectAction.AD) {
            // isAppClickAdOnClick: an ad step shows right now, and the app opens when it is dismissed
            // (onComplete launches it). Chrome steps still wait for the return below.
            if (config.isAppClickAdOnClick && action != RedirectAction.CHROME &&
                config.appClickAdFormat != AppClickAdFormat.NONE
            ) {
                if (isReturnAdInProgress) return   // double tap while the ad / loader is up
                isReturnAdInProgress = true
                val done = { isReturnAdInProgress = false; onComplete() }
                Log.d(TAG, "appClick[$packageName] SHOW ON CLICK action=$action format=${config.appClickAdFormat}")
                if (isAppOpenAction(action, legacyAppOpen = config.appClickAdFormat == AppClickAdFormat.APP_OPEN)) {
                    showAppClickOpenAdWithLoader(activity, done)
                } else {
                    showInterstitialWithLoader(activity, place = "App_Click", done)
                }
                return
            }
            // The redirect is deferred like the ad: opening Chrome now would cover the app the
            // user just tapped. It opens when they come back to the launcher (onLauncherReturned).
            if (config.appClickAdFormat != AppClickAdFormat.NONE ||
                hasLauncherRedirect(activity, "launcher_app_click")
            ) {
                markReturnAdPending(activity, action)
                Log.d(TAG, "appClick[$packageName] DEFERRED to launcher return action=$action format=${config.appClickAdFormat}")
            }
            onComplete()
        }

        // Redirect on: app_click_redirect_pattern alone decides (taps until ad / Chrome), for both
        // scopes; appClickInterCount is not used. Redirect off: the inter_count logic below, as before.
        if (isRedirectPatternMode()) {
            val action = tickPattern(activity, "app_click", patternForPlacement(config, "launcher_app_click"))
            if (action == null) onComplete() else show(action)
            return
        }

        // Ad-consuming apps are a small slice of the drawer, so a tap on one is
        // already rare. Applying `inter_count` on top of that made the ad fire
        // almost never — every qualifying tap shows one instead.
        if (scope == AppClickAdScope.AD_CONSUMING_ONLY) {
            Log.d(
                TAG,
                "appClick[$packageName] DUE scope=AD_CONSUMING_ONLY " +
                    "format=${config.appClickAdFormat} (every tap, inter_count ignored)"
            )
            show()
            return
        }

        val count = loadAppClickCount(activity) + 1
        Log.d(
            TAG,
            "appClick[$packageName] ELIGIBLE scope=$scope format=${config.appClickAdFormat} " +
                "count=$count/${config.appClickInterCount}"
        )
        if (count >= config.appClickInterCount) {
            saveAppClickCount(activity, 0)
            show()
        } else {
            saveAppClickCount(activity, count)
            onComplete()
        }
    }

    private var backClickCount = 0

    fun onBackClicked(activity: android.app.Activity, onComplete: () -> Unit) {
        if (!config.canShowAppClickInter) {
            onComplete()
            return
        }
        // Back follows the swipe pattern (own counter) in pattern mode; otherwise the swipe count as before.
        var step = RedirectAction.AD
        val due = if (isRedirectPatternMode()) {
            when (val s = tickPattern(activity, "back", patternForPlacement(config, "launcher_swipe"))) {
                null -> false
                RedirectAction.CHROME ->
                    if (tryLauncherRedirect(activity, "launcher_swipe", Runnable { onComplete() })) return else true
                RedirectAction.AD, RedirectAction.INTER, RedirectAction.APP_OPEN -> { step = s ?: step; true }
            }
        } else {
            backClickCount++
            (backClickCount >= config.swipeInterCount).also { if (it) backClickCount = 0 }
        }
        if (due) {
            if (isAppOpenAction(step, legacyAppOpen = legacySwipeUsesAppOpen())) {
                showAppOpenAdWithLoader(activity, onComplete)
            } else {
                showInterstitialWithLoader(activity, place = "Swipe", onComplete)
            }
        } else {
            onComplete()
        }
    }

    fun onPlusClicked(activity: android.app.Activity, onComplete: () -> Unit) {
        onComplete()
    }



    /**
     * Options for every native request: video creatives are allowed (start muted, user can
     * unmute), and any media aspect ratio is accepted so video demand is not filtered out.
     */
    fun videoNativeAdOptions(): app.messages.ads.ng.NgNativeOptions =
        app.messages.ads.ng.NgNativeOptions(startMuted = true)

    fun populateNativeAdView(
        nativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd,
        adView: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView,
        surface: String = ""
    ) {
        val install = adView.findViewById<android.widget.TextView>(app.messages.R.id.ad_call_to_action)
        // Media area: real AdMob MediaView, or the override image when Remote Config asks for it on this
        // surface (returns null then, so no MediaView is registered). Rest of the ad stays real.
        val mediaView = app.messages.ads.ng.NativeMediaOverride.resolveMediaView(adView, app.messages.R.id.ad_media, surface)
        adView.headlineView = adView.findViewById<android.view.View>(app.messages.R.id.ad_headline)
        adView.bodyView = adView.findViewById<android.view.View>(app.messages.R.id.ad_body)
        adView.callToActionView = install
        adView.iconView = adView.findViewById<android.view.View>(app.messages.R.id.ad_app_icon)
        adView.priceView = adView.findViewById<android.view.View>(app.messages.R.id.ad_price)
        adView.starRatingView = adView.findViewById<android.view.View>(app.messages.R.id.ad_stars)
        adView.storeView = adView.findViewById<android.view.View>(app.messages.R.id.ad_store)
        adView.advertiserView = adView.findViewById<android.view.View>(app.messages.R.id.ad_advertiser)

        if (install != null) install.text = nativeAd.callToAction
        adView.callToActionView = install

        if (adView.headlineView != null) (adView.headlineView as android.widget.TextView).text = nativeAd.headline

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = android.view.View.GONE
        } else {
            adView.bodyView?.visibility = android.view.View.VISIBLE
            (adView.bodyView as? android.widget.TextView)?.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = android.view.View.GONE
        } else {
            adView.callToActionView?.visibility = android.view.View.VISIBLE
            (adView.callToActionView as? android.widget.TextView)?.text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = android.view.View.GONE
        } else {
            (adView.iconView as? android.widget.ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = android.view.View.VISIBLE
        }

        if (nativeAd.price == null) {
            adView.priceView?.visibility = android.view.View.GONE
        } else {
            adView.priceView?.visibility = android.view.View.VISIBLE
            (adView.priceView as? android.widget.TextView)?.text = nativeAd.price
        }

        if (nativeAd.store == null) {
            adView.storeView?.visibility = android.view.View.GONE
        } else {
            adView.storeView?.visibility = android.view.View.VISIBLE
            (adView.storeView as? android.widget.TextView)?.text = nativeAd.store
        }

        if (nativeAd.starRating == null) {
            adView.starRatingView?.visibility = android.view.View.GONE
        } else {
            adView.starRatingView?.visibility = android.view.View.VISIBLE
            (adView.starRatingView as? android.widget.RatingBar)?.rating = nativeAd.starRating!!.toFloat()
        }

        if (nativeAd.advertiser == null) {
            adView.advertiserView?.visibility = android.view.View.GONE
        } else {
            adView.advertiserView?.visibility = android.view.View.VISIBLE
            (adView.advertiserView as? android.widget.TextView)?.text = nativeAd.advertiser
        }

        // Next-Gen: the media view is registered with the ad (it renders image or video itself).
        app.messages.ads.ng.NextGenAds.bindNative(adView, nativeAd, mediaView)
    }


}
