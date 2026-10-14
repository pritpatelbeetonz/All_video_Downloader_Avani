package app.messages.config

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
    const val KEY_ADS_CONFIG = "video_downlader_launcher_v2"

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
    const val KEY_SEND_REVENUE_SPLASH_ADS = "sendRevenueSplashAds"
    const val KEY_SEND_REVENUE_SPLASH_ADS_ALT = "send_revenue_splash_ads"
    const val KEY_SEND_REVENUE_SPLASH_INTER_LEGACY = "sendRevenueSplashInter"
    const val KEY_SEND_REVENUE_SPLASH_APP_OPEN_LEGACY = "sendRevenueSplashAppOpen"
    const val KEY_APP_OPEN_INTRO_SCREEN = "app_open_ad_id_IntroScreen"
    const val KEY_APP_OPEN_INTRO_SCREEN_ALT = "pr_introAppOpen"
    const val KEY_APP_OPEN_INTRO_SCREEN_ALT2 = "app_open_ad_id_intro"
    const val KEY_INTRO_APP_OPEN_ON = "introAppOpenOn"
    const val KEY_INTRO_APP_OPEN_ON_ALT = "intro_app_open_on"
    const val KEY_INTRO_APP_OPEN_PRELOAD = "introAppOpenAdPreload"
    const val KEY_SEND_REVENUE_INTRO_APP_OPEN = "sendRevenueIntroAppOpen"
    private const val KEY_ONESIGNAL_APP_ID = "oneSignalAppId"
    private const val KEY_ONESIGNAL_APP_ID_ALT = "onesignal_app_id"
    private const val KEY_PRIVACY_POLICY_URL = "privacyPolicyUrl"
    private const val KEY_PRIVACY_POLICY_URL_ALT = "privacy_policy_url"
    private const val KEY_PRIVACY_POLICY_URL_ALT2 = "privacy_policy"
    const val KEY_REMOTE_FACEBOOK_APP_ID = "remote_facebook_app_id"
    const val KEY_REMOTE_FACEBOOK_APP_ID_ALT = "facebook_app_id"
    const val KEY_REMOTE_FACEBOOK_CLIENT_TOKEN = "remote_facebook_client_token"
    const val KEY_REMOTE_FACEBOOK_CLIENT_TOKEN_ALT = "facebook_client_token"

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

    fun initialize(context: Context, onComplete: ((success: Boolean) -> Unit)? = null) {
        // MobileAds.initialize does disk and network work; Google's own guidance is
        // to keep it off the main thread. Ad requests issued before it reports ready
        // are dropped, so the first preload hangs off its completion callback (which
        // the SDK delivers on the main thread) rather than running inline below.
        Thread {
            runCatching {
                com.google.android.gms.ads.MobileAds.initialize(context) { /* preloadAllAds(context) */ }
            }.onFailure { }
        }.start()

        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0L)
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
            }
        })

        val application = context.applicationContext as android.app.Application
        application.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                refresh()
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                if (activity is androidx.appcompat.app.AppCompatActivity) {
                    activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                        object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentResumed(fm: androidx.fragment.app.FragmentManager, f: androidx.fragment.app.Fragment) {
                                refresh()
                            }
                        }, true
                    )
                }
            }
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })



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

    private fun applyFrom(remoteConfig: FirebaseRemoteConfig) {
        val newConfig = parse(remoteConfig)
        val interIdChanged = (config.interAdId != newConfig.interAdId)
        val setAsDefaultInterIdChanged = (config.interAdIdSetAsDefault != newConfig.interAdIdSetAsDefault)
        val splashInterIdChanged = (config.interAdIdSplashScreen != newConfig.interAdIdSplashScreen)
        // Also covers the blank-open_ad case, where the effective unit is appOpenAdId.
        val appClickOpenIdChanged = (config.appClickOpenAdId != newConfig.appClickOpenAdId) ||
            (newConfig.appClickOpenAdId.isBlank() && config.appOpenAdId != newConfig.appOpenAdId)
        
        config = newConfig
        _configFlow.value = config

        Log.d(TAG, "==================== FIREBASE CONFIG RECEIVED ====================")
        Log.d(TAG, "Parsed AdsConfig JSON:\n${gson.toJson(newConfig)}")
        val allEntries = remoteConfig.all
        if (allEntries.isNotEmpty()) {
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
                app.messages.ui.videodownloader.VideoDownloaderEngineHelper.syncAdsConfigToFlutter(json)
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

    private fun getRemoteConfigJsonString(remoteConfig: FirebaseRemoteConfig): String {
        var json = remoteConfig.getString(KEY_ADS_CONFIG)
        if (json.isBlank()) {
            json = remoteConfig.getString("messages_chat_sms_text_launcher_app_v2")
        }
        if (json.isBlank()) {
            json = remoteConfig.getString("ads_config")
        }
        return json
    }

    private fun parse(remoteConfig: FirebaseRemoteConfig): AdsConfig {
        val json = getRemoteConfigJsonString(remoteConfig)
        val fromJson = runCatching {
            if (json.isBlank()) AdsConfig.DEFAULT
            else gson.fromJson(json, AdsConfig::class.java) ?: AdsConfig.DEFAULT
        }.getOrElse { error ->
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
            sendRevenueSplashAds = remoteConfig.optionalBoolean(KEY_SEND_REVENUE_SPLASH_ADS)
                ?: remoteConfig.optionalBoolean(KEY_SEND_REVENUE_SPLASH_ADS_ALT)
                ?: remoteConfig.optionalBoolean(KEY_SEND_REVENUE_SPLASH_INTER_LEGACY)
                ?: remoteConfig.optionalBoolean(KEY_SEND_REVENUE_SPLASH_APP_OPEN_LEGACY)
                ?: fromJson.sendRevenueSplashAds,
            appOpenAdIdIntroScreen = remoteConfig.optionalString(KEY_APP_OPEN_INTRO_SCREEN)
                ?: remoteConfig.optionalString(KEY_APP_OPEN_INTRO_SCREEN_ALT)
                ?: remoteConfig.optionalString(KEY_APP_OPEN_INTRO_SCREEN_ALT2)
                ?: fromJson.appOpenAdIdIntroScreen,
            introAppOpenOn = remoteConfig.optionalBoolean(KEY_INTRO_APP_OPEN_ON)
                ?: remoteConfig.optionalBoolean(KEY_INTRO_APP_OPEN_ON_ALT)
                ?: fromJson.introAppOpenOn,
            introAppOpenAdPreload = remoteConfig.optionalBoolean(KEY_INTRO_APP_OPEN_PRELOAD)
                ?: fromJson.introAppOpenAdPreload,
            sendRevenueIntroAppOpen = remoteConfig.optionalBoolean(KEY_SEND_REVENUE_INTRO_APP_OPEN)
                ?: fromJson.sendRevenueIntroAppOpen,
        )
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

    private var interstitialAd: com.google.android.gms.ads.interstitial.InterstitialAd? = null
        private set
    private var setAsDefaultInterstitialAd: com.google.android.gms.ads.interstitial.InterstitialAd? = null
        private set
    var appOpenAd: com.google.android.gms.ads.appopen.AppOpenAd? = null
        private set

    /**
     * App-open ad for the app-tap placement only.
     *
     * Held separately from [appOpenAd] because the two placements can run different
     * ad units (`open_ad` vs `app_open_ad_id`). Sharing one instance would let an ad
     * loaded for one unit be shown — and reported — against the other.
     */
    private var appClickOpenAd: com.google.android.gms.ads.appopen.AppOpenAd? = null
    private var isAppClickOpenAdLoading = false

    private var swipeCount = 0

    // --- Preload Caches ---
    private val preloadedNativeAds = java.util.concurrent.ConcurrentHashMap<String, com.google.android.gms.ads.nativead.NativeAd>()
    private val preloadedBannerAds = java.util.concurrent.ConcurrentHashMap<String, com.google.android.gms.ads.AdView>()

    private var isInterstitialLoading = false
    private var isSetAsDefaultInterLoading = false
    private var isAppOpenLoading = false
    private val loadingNativeAds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val loadingBannerAds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun preloadAllAds(context: Context) {
        val appContext = context.applicationContext
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(appContext)
        if (!prefs.isLanguageSelected()) {
            Log.d(TAG, "[RemoteConfig] Skipping preloadAllAds: Setup flow not completed yet (isLanguageSelected=false)")
            return
        }

        // Interstitial preloading is managed per-screen (e.g. on Language screen onCreate)
        
        if (config.appOpenAdPreload) {
            preloadAppOpenAd(appContext)
        } else {
            }

        // Self-gating: no-ops unless the app-tap path is set to the app-open format.
        preloadAppClickOpenAd(appContext)

        // Preload drawer native ad on launcher startup
        preloadDrawerNativeAd(appContext)
        
        if (config.bannerAdPreload) {
            preloadBannerAds(appContext)
        } else {
            preloadedBannerAds.clear()
        }
    }

    /**
     * Checks whether ad revenue analytics should be logged for a specific placement.
     */
    fun shouldSendRevenueForPlacement(placement: String): Boolean {
        if (!config.sendRevenueToAnalytics) return false
        return when (placement.lowercase()) {
            "drawer_native", "native_drawer" -> config.sendRevenueDrawerNative
            "dashboard_native", "native_dashboard" -> config.sendRevenueDashboardNative
            "google_search_native", "native_google_search" -> config.sendRevenueGoogleSearchNative
            "language_native", "native_language" -> config.sendRevenueLanguageNative
            "language_inter", "inter_language" -> config.sendRevenueLanguageInter
            "native_chat", "chat_native" -> config.sendRevenueChatNative
            "native_menu", "menu_native" -> config.sendRevenueMenuNative
            "native_setting", "setting_native" -> config.sendRevenueSettingNative
            "native_launcher_setting", "launcher_setting_native", "native_launcher_screen" -> config.sendRevenueLauncherSettingNative
            "app_click_inter", "app_click_interstitial" -> config.sendRevenueAppClickInter
            "swipe_inter", "swipe_interstitial" -> config.sendRevenueSwipeInter
            "set_default_inter", "set_default_interstitial" -> config.sendRevenueSetDefaultInter
            "splash_inter", "splash_interstitial", "splash_app_open", "app_open_splash" -> config.sendRevenueSplashAds
            "intro_app_open", "app_open_intro" -> config.sendRevenueIntroAppOpen
            "app_open", "swipe_app_open", "app_click_open", "app_open_language" -> config.sendRevenueAppOpen
            "banner", "banner_splash", "banner_home", "banner_drawer", "banner_language" -> config.sendRevenueBanner
            else -> true
        }
    }

    fun sendRevenue(placement: String, adValue: com.google.android.gms.ads.AdValue) {
        val revenue = adValue.valueMicros / 1000000.0
        app.messages.Controller.getInstance()
            .sendRevenueToAnalytics(placement, revenue, adValue.currencyCode, adValue.precisionType)
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
        loadingNativeAds[id] = true
        com.google.android.gms.ads.AdLoader.Builder(context.applicationContext, id)
            .forNativeAd { nativeAd ->
                Log.d("ADS_LANGUAGE", "✅ [LANGUAGE_PRELOAD] Language Native Ad loaded successfully and stored in RAM cache! (UnitId: $id)")
                nativeAd.setOnPaidEventListener { adValue -> sendRevenue("language_native", adValue) }
                preloadedNativeAds[id] = nativeAd
                loadingNativeAds[id] = false
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.e("ADS_LANGUAGE", "❌ [LANGUAGE_PRELOAD] Failed to preload Language Native Ad: ${error.message} (code=${error.code})")
                    loadingNativeAds[id] = false
                }
            })
            .build()
            .loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
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
        loadingNativeAds[id] = true
        com.google.android.gms.ads.AdLoader.Builder(context.applicationContext, id)
            .forNativeAd { nativeAd ->
                Log.d("ADS_DASHBOARD", "✅ [DASHBOARD_PRELOAD] Background preload SUCCEEDED and cached in RAM! (UnitId: $id)")
                nativeAd.setOnPaidEventListener { adValue -> sendRevenue("dashboard_native", adValue) }
                preloadedNativeAds[id] = nativeAd
                loadingNativeAds[id] = false
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.e("ADS_DASHBOARD", "❌ [DASHBOARD_PRELOAD] Failed to preload dashboard native ad: ${error.message} (code=${error.code})")
                    loadingNativeAds[id] = false
                }
            })
            .build()
            .loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
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
        loadingNativeAds[id] = true
        com.google.android.gms.ads.AdLoader.Builder(context.applicationContext, id)
            .forNativeAd { nativeAd ->
                Log.d("ADS_GOOGLE_SEARCH", "✅ [SEARCH_PRELOAD] Background preload SUCCEEDED and cached in RAM! (UnitId: $id)")
                nativeAd.setOnPaidEventListener { adValue -> sendRevenue("google_search_native", adValue) }
                preloadedNativeAds[id] = nativeAd
                loadingNativeAds[id] = false
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.e("ADS_GOOGLE_SEARCH", "❌ [SEARCH_PRELOAD] Failed to preload Google search native ad: ${error.message} (code=${error.code})")
                    loadingNativeAds[id] = false
                }
            })
            .build()
            .loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
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
        loadingNativeAds[id] = true
        com.google.android.gms.ads.AdLoader.Builder(context.applicationContext, id)
            .forNativeAd { nativeAd ->
                Log.d("ADS_DRAWER", "✅ [DRAWER_PRELOAD] Background preload SUCCEEDED and cached in RAM! (UnitId: $id)")
                nativeAd.setOnPaidEventListener { adValue -> sendRevenue("drawer_native", adValue) }
                preloadedNativeAds[id] = nativeAd
                loadingNativeAds[id] = false
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.e("ADS_DRAWER", "❌ [DRAWER_PRELOAD] Failed to preload drawer native ad: ${error.message} (code=${error.code})")
                    loadingNativeAds[id] = false
                }
            })
            .build()
            .loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
    }

    /**
     * Caches a loaded native ad (e.g. if the user closed the drawer quickly before it could be displayed).
     */
    fun cacheNativeAd(adUnitId: String, nativeAd: com.google.android.gms.ads.nativead.NativeAd) {
        preloadedNativeAds[adUnitId] = nativeAd
    }

    /**
     * Retrieves and removes a cached native ad from RAM cache if available.
     */
    fun getCachedNativeAd(adUnitId: String): com.google.android.gms.ads.nativead.NativeAd? {
        return preloadedNativeAds.remove(adUnitId)
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
            val adView = com.google.android.gms.ads.AdView(context)
            adView.setAdSize(com.google.android.gms.ads.AdSize.BANNER)
            adView.adUnitId = id
            adView.setOnPaidEventListener { adValue -> sendRevenue("banner_splash", adValue) }
            adView.adListener = object : com.google.android.gms.ads.AdListener() {
                override fun onAdLoaded() {
                    preloadedBannerAds[id] = adView
                    loadingBannerAds[id] = false
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    loadingBannerAds[id] = false
                }
            }
            adView.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
        }
    }

    fun getBannerAd(context: Context, adUnitId: String?): com.google.android.gms.ads.AdView? {
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
        val adView = com.google.android.gms.ads.AdView(context)
        adView.setAdSize(com.google.android.gms.ads.AdSize.BANNER)
        adView.adUnitId = targetId
        adView.setOnPaidEventListener { adValue -> sendRevenue("banner_home", adValue) }
        return adView
    }

    fun loadNativeAd(
        context: Context,
        adUnitId: String? = null,
        onLoaded: (com.google.android.gms.ads.nativead.NativeAd) -> Unit,
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
        val cachedAd = preloadedNativeAds.remove(targetAdUnitId)
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
        if (targetAdUnitId == config.nativeAdIdDashboard) {
            Log.d("ADS_DASHBOARD", "🌐 [DASHBOARD_NETWORK_FETCH] Fetching fresh native ad on-demand from AdMob for unit $targetAdUnitId")
        } else if (targetAdUnitId == config.nativeAdIdGoogleSearch) {
            Log.d("ADS_GOOGLE_SEARCH", "🌐 [SEARCH_NETWORK_FETCH] Fetching fresh native ad on-demand from AdMob for unit $targetAdUnitId")
        } else if (targetAdUnitId == config.nativeAdIdLanguage) {
            Log.d("ADS_LANGUAGE", "🌐 [LANGUAGE_NETWORK_FETCH] Fetching fresh Language Native Ad on-demand from AdMob for unit $targetAdUnitId")
        }
        Log.d(TAG, "[RemoteConfig] loadNativeAd: Fetching fresh native ad on-demand for unit $targetAdUnitId")
        val adLoader = com.google.android.gms.ads.AdLoader.Builder(context, targetAdUnitId)
            .forNativeAd { nativeAd ->
                if (targetAdUnitId == config.nativeAdIdLanguage) {
                    Log.d("ADS_LANGUAGE", "📥 [LANGUAGE_NETWORK_SUCCESS] Language Native Ad fetched on-demand successfully! (UnitId: $targetAdUnitId)")
                }
                Log.d(TAG, "[RemoteConfig] loadNativeAd: On-demand native ad successfully loaded for unit $targetAdUnitId")
                val placement = when (targetAdUnitId) {
                    config.nativeAdIdDashboard -> "dashboard_native"
                    config.nativeAdIdGoogleSearch -> "google_search_native"
                    config.nativeAdIdAppDrawer -> "drawer_native"
                    config.nativeAdIdLanguage -> "language_native"
                    config.nativeAdIdSettingScreen -> "native_setting"
                    config.nativeAdIdLauncherScreen -> "native_launcher_setting"
                    config.nativeAdIdMenuScreen -> "native_menu"
                    config.nativeAdIdChatScreen -> "native_chat"
                    else -> "drawer_native"
                }
                nativeAd.setOnPaidEventListener { adValue -> sendRevenue(placement, adValue) }
                onLoaded(nativeAd)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.e(TAG, "[RemoteConfig] loadNativeAd: Failed to load on-demand native ad for unit $targetAdUnitId: ${error.message}")
                    onFailed()
                }
            })
            .build()
        adLoader.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
    }

    fun loadRectangleBannerAd(
        context: Context,
        adUnitId: String? = null,
        onLoaded: (com.google.android.gms.ads.AdView) -> Unit,
        onFailed: () -> Unit
    ) {
        val targetAdUnitId = adUnitId ?: config.bannerAdIdLanguage
        if (!config.isBannerOn || targetAdUnitId.isBlank() || targetAdUnitId == "11") {
            onFailed()
            return
        }

        val adView = com.google.android.gms.ads.AdView(context)
        adView.adUnitId = targetAdUnitId
        adView.setAdSize(com.google.android.gms.ads.AdSize.MEDIUM_RECTANGLE)
        adView.setOnPaidEventListener { adValue -> sendRevenue("banner_language", adValue) }

        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                onLoaded(adView)
            }
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                onFailed()
            }
        }
        adView.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
    }

    fun loadBannerAd(
        context: Context,
        adUnitId: String? = null,
        onLoaded: (com.google.android.gms.ads.AdView) -> Unit,
        onFailed: () -> Unit
    ) {
        val targetAdUnitId = adUnitId ?: config.bannerAdIdLanguage
        if (!config.isBannerOn || targetAdUnitId.isBlank() || targetAdUnitId == "11") {
            onFailed()
            return
        }
        val adView = com.google.android.gms.ads.AdView(context)
        adView.adUnitId = targetAdUnitId
        adView.setAdSize(com.google.android.gms.ads.AdSize.BANNER)
        adView.setOnPaidEventListener { adValue -> sendRevenue("banner_language", adValue) }

        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                onLoaded(adView)
            }
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                onFailed()
            }
        }
        adView.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
    }

    fun preloadInterstitial(context: Context) {
        if (!config.canShowInter || !config.interAdPreload) return
        val targetAdUnitId = config.interAdId.takeIf { it.isNotBlank() && it != "11" } ?: return
        if (interstitialAd != null) {
            return
        }
        if (isInterstitialLoading) {
            return
        }
        
        isInterstitialLoading = true
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            context,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                    ad.setOnPaidEventListener { adValue -> sendRevenue("app_click_inter", adValue) }
                    interstitialAd = ad
                    isInterstitialLoading = false
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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

    private var pendingSetAsDefaultShow: ((com.google.android.gms.ads.interstitial.InterstitialAd?) -> Unit)? = null

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
        isSetAsDefaultInterLoading = true
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            context,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                    Log.d("SetDefaultFlow", "📥 [AdsConfigManager] SetAsDefault interstitial loaded successfully into RAM!")
                    ad.setOnPaidEventListener { adValue -> sendRevenue("set_default_inter", adValue) }
                    isSetAsDefaultInterLoading = false
                    val pending = pendingSetAsDefaultShow
                    if (pending != null) {
                        Log.d("SetDefaultFlow", "⚡ [AdsConfigManager] Invoking pending show callback for SetAsDefault interstitial")
                        pendingSetAsDefaultShow = null
                        setAsDefaultInterstitialAd = null
                        pending.invoke(ad)
                    } else {
                        setAsDefaultInterstitialAd = ad
                    }
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.e("SetDefaultFlow", "❌ [AdsConfigManager] SetAsDefault interstitial failed to load: ${error.message} (code=${error.code})")
                    setAsDefaultInterstitialAd = null
                    isSetAsDefaultInterLoading = false
                    val pending = pendingSetAsDefaultShow
                    if (pending != null) {
                        Log.d("SetDefaultFlow", "⚠️ [AdsConfigManager] Invoking pending show callback with null ad after failure")
                        pendingSetAsDefaultShow = null
                        pending.invoke(null)
                    }
                }
            }
        )
    }

    fun showSetAsDefaultInterstitial(activity: android.app.Activity, onComplete: () -> Unit) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()
        Log.d("SetDefaultFlow", "👉 [AdsConfigManager] showSetAsDefaultInterstitial() called. isPremium=$isPremium, canShowSetAsDefaultInter=${config.canShowSetAsDefaultInter}, setAsDefaultInterOn=${config.setAsDefaultInterOn}, interAdIdSetAsDefault='${config.interAdIdSetAsDefault}'")
        if (isPremium || !config.canShowSetAsDefaultInter) {
            Log.d("SetDefaultFlow", "⏭️ [AdsConfigManager] showSetAsDefaultInterstitial() skipped (isPremium=$isPremium, canShowSetAsDefaultInter=${config.canShowSetAsDefaultInter}) -> calling onComplete immediately")
            onComplete()
            return
        }

        val isAdFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isAdFinished.compareAndSet(false, true)) {
                Log.d("SetDefaultFlow", "🏁 [AdsConfigManager] SetAsDefault ad flow completed -> triggering onComplete callback")
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
            Log.d("SetDefaultFlow", "📺 [AdsConfigManager] Displaying SetAsDefault Interstitial Ad to user now")
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("SetDefaultFlow", "✅ [AdsConfigManager] SetAsDefault Interstitial Ad dismissed by user")
                    setAsDefaultInterstitialAd = null
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    Log.e("SetDefaultFlow", "❌ [AdsConfigManager] SetAsDefault Interstitial Ad failed to show: ${error.message} (code=${error.code})")
                    setAsDefaultInterstitialAd = null
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

            val originalCompleteOnce = ::completeOnce
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.d("SetDefaultFlow", "⏰ [AdsConfigManager] SetAsDefault loader 10s timeout reached -> dismissing loader and proceeding")
                pendingSetAsDefaultShow = null
                originalCompleteOnce()
            }
            timeoutHandler.postDelayed(timeoutRunnable, 10000L)

            fun cancelTimeoutAndComplete() {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                originalCompleteOnce()
            }

            // 2. Show non-blocking loader dialog while waiting/fetching
            val loader = showLoadingDialog(activity, onInterrupted = {
                pendingSetAsDefaultShow = null
                cancelTimeoutAndComplete()
            })

            // 3. If a preload request is currently in-flight, attach listener to show it as soon as it finishes!
            if (isSetAsDefaultInterLoading) {
                Log.d("SetDefaultFlow", "🔄 [AdsConfigManager] SetAsDefault Interstitial is currently in-flight loading -> attaching callback")
                pendingSetAsDefaultShow = { ad ->
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

            // 4. If not preloaded and not loading, fetch on-demand!
            val targetAdUnitId = config.interAdIdSetAsDefault.takeIf { it.isNotBlank() && it != "11" }
                ?: config.interAdId.takeIf { it.isNotBlank() && it != "11" }
                ?: run {
                    Log.w("SetDefaultFlow", "⚠️ [AdsConfigManager] SetAsDefault unit ID is blank or '11' (interAdIdSetAsDefault='${config.interAdIdSetAsDefault}', interAdId='${config.interAdId}') -> skipping show")
                    cancelTimeoutAndComplete()
                    return@runWhenActivityResumed
                }

            Log.d("SetDefaultFlow", "🌐 [AdsConfigManager] Fetching SetAsDefault Interstitial on-demand with unitId: $targetAdUnitId")
            com.google.android.gms.ads.interstitial.InterstitialAd.load(
                activity,
                targetAdUnitId,
                com.google.android.gms.ads.AdRequest.Builder().build(),
                object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        Log.d("SetDefaultFlow", "📥 [AdsConfigManager] On-demand SetAsDefault Interstitial loaded successfully!")
                        ad.setOnPaidEventListener { adValue -> sendRevenue("set_default_inter", adValue) }
                        try { loader?.dismiss() } catch (e: Exception) {}
                        showAd(ad)
                    }
                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        Log.e("SetDefaultFlow", "❌ [AdsConfigManager] On-demand SetAsDefault Interstitial failed to load: ${error.message} (code=${error.code})")
                        try { loader?.dismiss() } catch (e: Exception) {}
                        cancelTimeoutAndComplete()
                    }
                }
            )
        }
    }

    private var splashInterstitialAd: com.google.android.gms.ads.interstitial.InterstitialAd? = null
    @Volatile
    private var isSplashInterLoading = false
    private var pendingSplashShow: ((com.google.android.gms.ads.interstitial.InterstitialAd?) -> Unit)? = null

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
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            context,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash Interstitial loaded successfully!")
                    ad.setOnPaidEventListener { adValue ->
                        sendRevenue("splash_inter", adValue)
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

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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

        fun showAd(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] Activity not RESUMED/STARTED -> skipping Splash ad.show to prevent blackout")
                splashInterstitialAd = null
                completeOnce()
                return
            }
            Log.d("SplashAd", "📺 [AdsConfigManager] Displaying Splash Interstitial Ad to user now")
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash Interstitial Ad dismissed by user")
                    splashInterstitialAd = null
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    Log.e("SplashAd", "❌ [AdsConfigManager] Splash Interstitial Ad failed to show: ${error.message} (code=${error.code})")
                    splashInterstitialAd = null
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

    private var splashAppOpenAd: com.google.android.gms.ads.appopen.AppOpenAd? = null
    @Volatile
    private var isSplashAppOpenLoading = false
    private var pendingSplashAppOpenShow: ((com.google.android.gms.ads.appopen.AppOpenAd?) -> Unit)? = null

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
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.appopen.AppOpenAd.load(
            context,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash App Open Ad loaded successfully!")
                    ad.setOnPaidEventListener { adValue ->
                        sendRevenue("splash_app_open", adValue)
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

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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

        fun showAd(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.w("SplashAd", "⚠️ [AdsConfigManager] Activity not RESUMED/STARTED -> skipping Splash App Open show")
                splashAppOpenAd = null
                completeOnce()
                return
            }
            Log.d("SplashAd", "📺 [AdsConfigManager] Displaying Splash App Open Ad to user now")
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("SplashAd", "✅ [AdsConfigManager] Splash App Open Ad dismissed by user")
                    splashAppOpenAd = null
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    Log.e("SplashAd", "❌ [AdsConfigManager] Splash App Open Ad failed to show: ${error.message}")
                    splashAppOpenAd = null
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

    private var introAppOpenAd: com.google.android.gms.ads.appopen.AppOpenAd? = null
    @Volatile
    private var isIntroAppOpenLoading = false
    private var pendingIntroAppOpenShow: ((com.google.android.gms.ads.appopen.AppOpenAd?) -> Unit)? = null

    fun preloadIntroAppOpen(context: Context, onLoaded: ((Boolean) -> Unit)? = null) {
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(context).isPremium()
        Log.d("IntroAd", "🚀 [AdsConfigManager] preloadIntroAppOpen() called. isPremium=$isPremium, canShowIntroAppOpen=${config.canShowIntroAppOpen}, introAppOpenAdPreload=${config.introAppOpenAdPreload}")
        if (isPremium || !config.canShowIntroAppOpen || !config.introAppOpenAdPreload) {
            Log.d("IntroAd", "⏭️ [AdsConfigManager] preloadIntroAppOpen() skipped (disabled by config/premium)")
            onLoaded?.invoke(false)
            return
        }
        if (introAppOpenAd != null) {
            Log.d("IntroAd", "ℹ️ [AdsConfigManager] preloadIntroAppOpen() skipped (already loaded in RAM)")
            onLoaded?.invoke(true)
            return
        }
        if (isIntroAppOpenLoading) {
            Log.d("IntroAd", "ℹ️ [AdsConfigManager] preloadIntroAppOpen() skipped (already loading)")
            return
        }

        val targetAdUnitId = config.appOpenAdIdIntroScreen.takeIf { it.isNotBlank() && it != "11" }
            ?: config.appOpenAdId.takeIf { it.isNotBlank() && it != "11" }
            ?: run {
                Log.w("IntroAd", "⚠️ [AdsConfigManager] preloadIntroAppOpen() skipped (unit ID is blank or '11')")
                onLoaded?.invoke(false)
                return
            }

        Log.d("IntroAd", "🌐 [AdsConfigManager] Preloading Intro App Open with unitId: $targetAdUnitId")
        isIntroAppOpenLoading = true
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.appopen.AppOpenAd.load(
            context,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
                    Log.d("IntroAd", "✅ [AdsConfigManager] Intro App Open Ad loaded successfully!")
                    ad.setOnPaidEventListener { adValue ->
                        sendRevenue("intro_app_open", adValue)
                    }
                    isIntroAppOpenLoading = false
                    val pending = pendingIntroAppOpenShow
                    if (pending != null) {
                        pendingIntroAppOpenShow = null
                        introAppOpenAd = null
                        pending.invoke(ad)
                    } else {
                        introAppOpenAd = ad
                    }
                    onLoaded?.invoke(true)
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.e("IntroAd", "❌ [AdsConfigManager] Intro App Open Ad failed to load: ${error.message} (code=${error.code})")
                    introAppOpenAd = null
                    isIntroAppOpenLoading = false
                    val pending = pendingIntroAppOpenShow
                    if (pending != null) {
                        pendingIntroAppOpenShow = null
                        pending.invoke(null)
                    }
                    onLoaded?.invoke(false)
                }
            }
        )
    }

    fun showIntroAppOpen(
        activity: android.app.Activity,
        timeoutMs: Long = 5000L,
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

        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true)) {
                onLoadingStateChanged?.invoke(false)
                Log.d("IntroAd", "🏁 [AdsConfigManager] Intro App Open flow completed -> triggering onComplete callback")
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
            onLoadingStateChanged?.invoke(false)
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("IntroAd", "✅ [AdsConfigManager] Intro App Open Ad dismissed by user")
                    introAppOpenAd = null
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    Log.e("IntroAd", "❌ [AdsConfigManager] Intro App Open Ad failed to show: ${error.message}")
                    introAppOpenAd = null
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

            if (!isIntroAppOpenLoading) {
                Log.d("IntroAd", "⚡ [AdsConfigManager] Intro App Open not preloaded yet -> triggering preload now")
                preloadIntroAppOpen(activity)
            }

            if (!isIntroAppOpenLoading) {
                Log.d("IntroAd", "ℹ️ [AdsConfigManager] Intro App Open cannot be loaded (disabled, premium, or invalid ID) -> proceeding immediately")
                completeOnce()
                return@runWhenActivityResumed
            }

            Log.d("IntroAd", "⏳ [AdsConfigManager] Intro App Open is loading in background -> waiting with timeout: ${timeoutMs}ms")
            onLoadingStateChanged?.invoke(true)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.w("IntroAd", "⏰ [AdsConfigManager] Intro App Open loading wait timed out -> proceeding")
                pendingIntroAppOpenShow = null
                completeOnce()
            }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            pendingIntroAppOpenShow = { ad ->
                handler.removeCallbacks(timeoutRunnable)
                if (ad != null && isActivitySafeToShowAd(activity)) {
                    showAd(ad)
                } else {
                    completeOnce()
                }
            }
        }
    }

    private var languageInterstitialAd: com.google.android.gms.ads.interstitial.InterstitialAd? = null
    private var isLanguageInterLoading = false
    private var pendingLanguageShow: ((com.google.android.gms.ads.interstitial.InterstitialAd?) -> Unit)? = null

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
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            context,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                    Log.d("SetDefaultFlow", "[AdsConfigManager] Language Interstitial loaded successfully!")
                    ad.setOnPaidEventListener { adValue -> sendRevenue("language_inter", adValue) }
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
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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

        fun showAd(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d("SetDefaultFlow", "[AdsConfigManager] Activity not RESUMED -> skipping Language ad.show to prevent blackout")
                languageInterstitialAd = null
                completeOnce()
                return
            }
            Log.d("SetDefaultFlow", "[AdsConfigManager] Displaying Language Interstitial Ad to user")
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("SetDefaultFlow", "[AdsConfigManager] Language Interstitial Ad dismissed by user")
                    languageInterstitialAd = null
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
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
            com.google.android.gms.ads.interstitial.InterstitialAd.load(
                activity,
                targetAdUnitId,
                com.google.android.gms.ads.AdRequest.Builder().build(),
                object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        Log.d("SetDefaultFlow", "📥 [AdsConfigManager] On-demand Language Interstitial loaded successfully!")
                        ad.setOnPaidEventListener { adValue -> sendRevenue("language_inter", adValue) }
                        try { loader?.dismiss() } catch (e: Exception) {}
                        showAd(ad)
                    }
                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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
        if (!config.isAppOpenOn || !config.appOpenAdPreload) return
        val targetAdUnitId = config.appOpenAdId.takeIf { it.isNotBlank() && it != "11" } ?: return
        if (appOpenAd != null) {
            return
        }
        if (isAppOpenLoading) {
            return
        }
        
        isAppOpenLoading = true
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.appopen.AppOpenAd.load(
            context,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
                    ad.setOnPaidEventListener { adValue -> sendRevenue("app_open", adValue) }
                    appOpenAd = ad
                    isAppOpenLoading = false
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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

    private fun showLoadingDialog(
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

    private fun showInterstitialWithLoader(activity: android.app.Activity, onComplete: () -> Unit) {
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

        fun showAd(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d(TAG, "[showInterstitialWithLoader] Activity not RESUMED (e.g. phone call active) -> skipping ad.show to prevent blackout")
                interstitialAd = null
                preloadInterstitial(activity)
                completeOnce()
                return
            }

            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitial(activity)
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                    preloadInterstitial(activity)
                    completeOnce()
                }
            }
            ad.show(activity)
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

            com.google.android.gms.ads.interstitial.InterstitialAd.load(
                activity,
                targetAdUnitId,
                com.google.android.gms.ads.AdRequest.Builder().build(),
                object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        ad.setOnPaidEventListener { adValue -> sendRevenue("app_click_inter", adValue) }
                        interstitialAd = ad
                        try { loader?.dismiss() } catch (e: Exception) {}
                        if (isActivitySafeToShowAd(activity)) {
                            showAd(ad)
                        } else {
                            completeOnce()
                        }
                    }
                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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
        config.appClickOpenAdId.takeIf { it.isNotBlank() } ?: config.appOpenAdId

    /**
     * Warms the app-tap app-open ad. No-op unless the app-tap path is actually set
     * to that format, so a config using interstitials never requests one.
     */
    fun preloadAppClickOpenAd(context: Context) {
        if (config.appClickAdFormat != AppClickAdFormat.APP_OPEN) return
        val targetAdUnitId = appClickOpenAdUnitId().takeIf { it.isNotBlank() && it != "11" } ?: return
        if (appClickOpenAd != null || isAppClickOpenAdLoading) return

        isAppClickOpenAdLoading = true
        com.google.android.gms.ads.appopen.AppOpenAd.load(
            context,
            targetAdUnitId,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
                    ad.setOnPaidEventListener { adValue -> sendRevenue("app_click_open", adValue) }
                    appClickOpenAd = ad
                    isAppClickOpenAdLoading = false
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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

        fun showAd(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d(TAG, "[showAppClickOpenAdWithLoader] Activity not RESUMED -> skipping ad.show to prevent blackout")
                appClickOpenAd = null
                preloadAppClickOpenAd(activity)
                completeOnce()
                return
            }

            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appClickOpenAd = null
                    preloadAppClickOpenAd(activity)
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    appClickOpenAd = null
                    preloadAppClickOpenAd(activity)
                    completeOnce()
                }
            }
            ad.show(activity)
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

        com.google.android.gms.ads.appopen.AppOpenAd.load(
            activity,
            targetAdUnitId,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    ad.setOnPaidEventListener { adValue -> sendRevenue("app_click_open", adValue) }
                    appClickOpenAd = ad
                    try { loader?.dismiss() } catch (_: Exception) {}
                    if (isActivitySafeToShowAd(activity)) {
                        showAd(ad)
                    } else {
                        completeOnce()
                    }
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    appClickOpenAd = null
                    try { loader?.dismiss() } catch (_: Exception) {}
                    completeOnce()
                }
            }
        )
    }

    private fun showAppOpenAdWithLoader(activity: android.app.Activity, onComplete: () -> Unit) {
        val targetAdUnitId = config.appOpenAdId.takeIf { it.isNotBlank() && it != "11" } ?: run {
            onComplete()
            return
        }

        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        fun completeOnce() {
            if (isCompleted.compareAndSet(false, true)) {
                onComplete()
            }
        }

        fun showAd(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
            if (!isActivitySafeToShowAd(activity)) {
                Log.d(TAG, "[showAppOpenAdWithLoader] Activity not RESUMED -> skipping ad.show to prevent blackout")
                appOpenAd = null
                preloadAppOpenAd(activity)
                completeOnce()
                return
            }

            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    preloadAppOpenAd(activity)
                    completeOnce()
                }
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
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

            com.google.android.gms.ads.appopen.AppOpenAd.load(
                activity,
                targetAdUnitId,
                com.google.android.gms.ads.AdRequest.Builder().build(),
                object : com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: com.google.android.gms.ads.appopen.AppOpenAd) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        ad.setOnPaidEventListener { adValue -> sendRevenue("app_open", adValue) }
                        appOpenAd = ad
                        try { loader?.dismiss() } catch (_: Exception) {}
                        if (isActivitySafeToShowAd(activity)) {
                            showAd(ad)
                        } else {
                            completeOnce()
                        }
                    }
                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
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
        swipeCount++
        if (swipeCount >= config.swipeInterCount) {
            swipeCount = 0
            val flagValue = config.intertitialoropenads.trim().lowercase().replace("\"", "")
            if (flagValue == "open" || flagValue == "true" || flagValue == "appopen" || flagValue == "yes") {
                showAppOpenAdWithLoader(activity, onComplete)
            } else {
                showInterstitialWithLoader(activity, onComplete)
            }
        } else {
            onComplete()
        }
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

        // The format comes from the app-click switches, not from
        // `intertitialoropenads` — that string still steers swipe / back / plus.
        fun show() {
            when (config.appClickAdFormat) {
                AppClickAdFormat.APP_OPEN -> showAppClickOpenAdWithLoader(activity, onComplete)
                AppClickAdFormat.INTERSTITIAL -> showInterstitialWithLoader(activity, onComplete)
                AppClickAdFormat.NONE -> onComplete()
            }
        }

        // Ad-consuming apps are a small slice of the drawer, so a tap on one is
        // already rare. Applying `inter_count` on top of that made the ad fire
        // almost never — every qualifying tap shows one instead.
        if (scope == AppClickAdScope.AD_CONSUMING_ONLY) {
            Log.d(
                TAG,
                "appClick[$packageName] SHOW scope=AD_CONSUMING_ONLY " +
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
        backClickCount++
        if (backClickCount >= config.swipeInterCount) {
            backClickCount = 0
            val flagValue = config.intertitialoropenads.trim().lowercase().replace("\"", "")
            if (flagValue == "open" || flagValue == "true" || flagValue == "appopen" || flagValue == "yes") {
                showAppOpenAdWithLoader(activity, onComplete)
            } else {
                showInterstitialWithLoader(activity, onComplete)
            }
        } else {
            onComplete()
        }
    }

    fun onPlusClicked(activity: android.app.Activity, onComplete: () -> Unit) {
        onComplete()
    }



    fun populateNativeAdView(
        nativeAd: com.google.android.gms.ads.nativead.NativeAd,
        adView: com.google.android.gms.ads.nativead.NativeAdView
    ) {
        val install = adView.findViewById<android.widget.TextView>(app.messages.R.id.ad_call_to_action)
        adView.mediaView = adView.findViewById<com.google.android.gms.ads.nativead.MediaView>(app.messages.R.id.ad_media)
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

        if (adView.mediaView != null && nativeAd.mediaContent != null) {
            adView.mediaView!!.mediaContent = nativeAd.mediaContent
        }

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

        adView.setNativeAd(nativeAd)

        val mediaContent = nativeAd.mediaContent
        if (adView.mediaView != null && mediaContent != null && mediaContent.hasVideoContent()) {
            val vc = mediaContent.videoController
            vc.videoLifecycleCallbacks = object : com.google.android.gms.ads.VideoController.VideoLifecycleCallbacks() {
                override fun onVideoEnd() {
                    super.onVideoEnd()
                }
            }
        }
    }


}
