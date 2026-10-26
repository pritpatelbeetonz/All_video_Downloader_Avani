package app.messages.config

import com.google.gson.annotations.SerializedName

data class AdsConfig(
    @SerializedName(value = "bannerAdIdSplash", alternate = ["banner_ad_id_splash"])
    val bannerAdIdSplash: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "bannerAdIdContactHome", alternate = ["banner_ad_id_contact_home"])
    val bannerAdIdContactHome: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "bannerAdIdAppDrawer", alternate = ["banner_ad_id_app_drawer"])
    val bannerAdIdAppDrawer: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "bannerAdIdLanguage", alternate = ["banner_ad_id_language"])
    val bannerAdIdLanguage: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "bannerAdIdOnboarding", alternate = ["banner_ad_id_onboarding"])
    val bannerAdIdOnboarding: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "bannerAdIdChatScreen", alternate = ["banner_ad_id_chat_screen"])
    val bannerAdIdChatScreen: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "bannerAdIdInstallUninstall", alternate = ["banner_ad_id_install_uninstall"])
    val bannerAdIdInstallUninstall: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "nativeAdIdDashboard", alternate = ["native_ad_id_dashboard", "native_dashboard"])
    val nativeAdIdDashboard: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdGoogleSearch", alternate = ["native_ad_id_google_search", "native_google_search"])
    val nativeAdIdGoogleSearch: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdLanguage", alternate = ["native_ad_id_language", "native_language"])
    val nativeAdIdLanguage: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdSettingScreen", alternate = ["native_ad_id_setting_screen", "native_setting_screen"])
    val nativeAdIdSettingScreen: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdLauncherScreen", alternate = ["native_launcher_screen", "native_ad_id_launcher_screen", "native_ad_id_launcher_setting", "native_launcher_setting_screen"])
    val nativeAdIdLauncherScreen: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdMenuScreen", alternate = ["native_ad_id_menu_screen", "native_menu_screen", "menuNativeid"])
    val nativeAdIdMenuScreen: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdChatScreen", alternate = ["native_ad_id_chat_screen", "native_chat_screen"])
    val nativeAdIdChatScreen: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdAppDrawer", alternate = ["native_ad_id_app_drawer", "native_app_drawer"])
    val nativeAdIdAppDrawer: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "interAdId", alternate = ["inter_ad_id"])
    val interAdId: String = DEFAULT_INTER_ID,

    @SerializedName(value = "appOpenAdId", alternate = ["app_open_ad_id"])
    val appOpenAdId: String = DEFAULT_APP_OPEN_ID,

    @SerializedName(value = "appClickInterCount", alternate = ["inter_count", "interstitialForwardAdCount", "interCount"])
    val appClickInterCount: Int = 2,

    @SerializedName(value = "swipeInterCount", alternate = ["inter_back_count", "interstitialBackwardAdCount", "interBackCount"])
    val swipeInterCount: Int = 4,

    @SerializedName("isAppClickInterOn")
    val isAppClickInterOn: Boolean = true,

    /**
     * Show an app-open ad instead of an interstitial on an app tap. Deliberately
     * NOT gated by [isAppOpenOn] -- it is the app-click path's own switch -- and it
     * beats [isAppClickInterOn] when both are on. See [appClickAdFormat].
     */
    @SerializedName(value = "IsAppClickOpenAdOn", alternate = ["isAppClickOpenAdOn", "is_app_click_open_ad_on"])
    val isAppClickOpenAdOn: Boolean = false,

    /**
     * Ad unit for the app-tap app-open ad. Kept separate from [appOpenAdId] so the
     * two placements report independently; falls back to [appOpenAdId] when blank.
     */
    @SerializedName(value = "open_ad", alternate = ["openAd", "app_click_open_ad_id", "appClickOpenAdId"])
    val appClickOpenAdId: String = "",

    /**
     * Show the app-tap ad on every app. Only consulted once [appClickAdFormat] has
     * settled on a format, and loses to [isAdShowOnlyAdConsumeApps] when both are
     * set — see [appClickAdScope].
     */
    @SerializedName(value = "isAdShowOnAllApps", alternate = ["is_ad_show_on_all_apps"])
    val isAdShowOnAllApps: Boolean = false,

    /**
     * Show the app-tap ad only on apps that themselves monetize with AdMob.
     * Defaults on, so a build that ships before the Remote Config keys exist keeps
     * the behaviour it already has.
     */
    @SerializedName(value = "isAdShowOnlyAdConsumeApps", alternate = ["is_ad_show_only_ad_consume_apps"])
    val isAdShowOnlyAdConsumeApps: Boolean = true,

    /**
     * When true, essential/utility apps (Phone, WhatsApp, Maps, UPI, Banking, Settings, etc.)
     * are completely bypass ad-free and do not advance the click counter.
     */
    @SerializedName(value = "isEssentialAppsAdFree", alternate = ["is_essential_apps_ad_free", "isEssentialAppsFilterOn"])
    val isEssentialAppsAdFree: Boolean = true,

    @SerializedName("isRightLeftSwipeInterOn")
    val isRightLeftSwipeInterOn: Boolean = true,

    @SerializedName("isBannerOn")
    val isBannerOn: Boolean = false,

    @SerializedName("isBannerSplashOn")
    val isBannerSplashOn: Boolean = false,

    @SerializedName("isBannerLauncherOn")
    val isBannerLauncherOn: Boolean = false,

    @SerializedName("isBannerInstallUninstallOn")
    val isBannerInstallUninstallOn: Boolean = false,

    @SerializedName(value = "isGoogleSearchNativeOn", alternate = ["isBannerGoogleSearchOn"])
    val isGoogleSearchNativeOn: Boolean = true,

    @SerializedName(value = "isChatNativeOn", alternate = ["nativeAdforChat", "is_chat_native_on"])
    val isChatNativeOn: Boolean = true,

    @SerializedName("intertitialoropenads")
    val intertitialoropenads: String = "interstitial",

    @SerializedName("isNativeOn")
    val isNativeOn: Boolean = true,

    @SerializedName("isInterOn")
    val isInterOn: Boolean = true,

    @SerializedName("isAppOpenOn")
    val isAppOpenOn: Boolean = false,

    @SerializedName("bannerAdPreload")
    val bannerAdPreload: Boolean = false,

    @SerializedName("interAdPreload")
    val interAdPreload: Boolean = true,

    @SerializedName("appOpenAdPreload")
    val appOpenAdPreload: Boolean = false,

    @SerializedName("sendRevenueToAnalytics")
    val sendRevenueToAnalytics: Boolean = true,

    /** Only ads whose unit ID is in this list fire ad_impression; empty = none. */
    @SerializedName(value = "adImpressionEventAllowed", alternate = ["ad_impression_event_allowed"])
    val adImpressionEventAllowed: List<String>? = emptyList(),

    @SerializedName("shareAppLink")
    val shareAppLink: String = "https://play.google.com/store/apps/details?id=app.john.all.video.downloader.com",

    @SerializedName(value = "privacyPolicyUrl", alternate = ["privacy_policy_url", "privacyPolicy", "privacy_policy"])
    val privacyPolicyUrl: String = "https://johnrileyblogs.blogspot.com/2022/06/privacy-policy.html",

    @SerializedName("termsAndConditionsUrl")
    val termsAndConditionsUrl: String = "https://policies.google.com/terms",

    @SerializedName(value = "oneSignalAppId", alternate = ["onesignal_app_id", "one_signal_app_id", "onesignalAppId"])
    val oneSignalAppId: String = "",

    @SerializedName(value = "inter_ad_id_SplashScreen", alternate = ["inter_ad_id_splash_screen", "interAdIdSplashScreen", "inter_splash_screen"])
    val interAdIdSplashScreen: String = DEFAULT_INTER_ID,

    @SerializedName(value = "splashInterOn", alternate = ["isSplashInterOn", "splash_inter_on"])
    val splashInterOn: Boolean = true,

    @SerializedName("splashInterAdPreload")
    val splashInterAdPreload: Boolean = true,

    @SerializedName(value = "app_open_ad_id_SplashScreen", alternate = ["app_open_ad_id_splash_screen", "appOpenAdIdSplashScreen"])
    val appOpenAdIdSplashScreen: String = DEFAULT_APP_OPEN_ID,

    @SerializedName(value = "splashAppOpenOn", alternate = ["isSplashAppOpenOn", "splash_app_open_on"])
    val splashAppOpenOn: Boolean = false,

    @SerializedName("splashAppOpenAdPreload")
    val splashAppOpenAdPreload: Boolean = true,

    @SerializedName(value = "inter_ad_id_set_as_default", alternate = ["inter_ad_id_set_default", "interAdIdSetAsDefault", "inter_set_as_default"])
    val interAdIdSetAsDefault: String = DEFAULT_INTER_SET_AS_DEFAULT_ID,

    @SerializedName("setAsDefaultInterAdPreload")
    val setAsDefaultInterAdPreload: Boolean = true,

    @SerializedName("setAsDefaultInterOn")
    val setAsDefaultInterOn: Boolean = true,

    @SerializedName(value = "inter_ad_id_language", alternate = ["inter_language", "interAdIdLanguage"])
    val interAdIdLanguage: String = DEFAULT_INTER_ID,

    @SerializedName("languageInterAdPreload")
    val languageInterAdPreload: Boolean = false,

    @SerializedName("languageInterOn")
    val languageInterOn: Boolean = true,

    @SerializedName(value = "remote_facebook_app_id", alternate = ["facebook_app_id", "facebookAppId", "remoteFacebookAppId"])
    val remoteFacebookAppId: String = "",

    @SerializedName(value = "remote_facebook_client_token", alternate = ["facebook_client_token", "facebookClientToken", "remoteFacebookClientToken"])
    val remoteFacebookClientToken: String = "",

    @SerializedName(value = "app_open_ad_id_IntroScreen", alternate = ["pr_introAppOpen", "app_open_ad_id_intro", "introAppOpenAdId"])
    val appOpenAdIdIntroScreen: String = DEFAULT_APP_OPEN_ID,

    @SerializedName(value = "introAppOpenOn", alternate = ["isIntroAppOpenOn", "intro_app_open_on"])
    val introAppOpenOn: Boolean = true,

    @SerializedName("introAppOpenAdPreload")
    val introAppOpenAdPreload: Boolean = true,
) {
    val canShowBanner: Boolean get() = isBannerOn
    val canShowBannerSplash: Boolean get() = isBannerOn && isBannerSplashOn
    val canShowBannerLauncher: Boolean get() =  isBannerLauncherOn
    val canShowNative: Boolean get() = isNativeOn
    val canShowInter: Boolean get() = isInterOn
    val canShowSplashInter: Boolean get() = splashInterOn
    val canShowSplashAppOpen: Boolean get() = splashAppOpenOn
    val canShowIntroAppOpen: Boolean get() = introAppOpenOn
    val canShowSetAsDefaultInter: Boolean get() = setAsDefaultInterOn
    val canShowLanguageInter: Boolean get() = isInterOn && languageInterOn
    val canShowAppOpen: Boolean get() = isAppOpenOn
    val canShowAppClickInter: Boolean get() = (isInterOn || isAppOpenOn) && isAppClickInterOn

    /**
     * Determines which ad to show after splash:
     * - If both splashInterOn and splashAppOpenOn are true -> Interstitial wins.
     * - If only splashAppOpenOn is true -> App Open is chosen.
     * - If only splashInterOn is true -> Interstitial is chosen.
     * - If both are false -> NONE is chosen.
     */
    enum class SplashAdChoice { INTERSTITIAL, APP_OPEN, NONE }

    val splashAdChoice: SplashAdChoice
        get() = when {
            splashInterOn -> SplashAdChoice.INTERSTITIAL
            splashAppOpenOn -> SplashAdChoice.APP_OPEN
            else -> SplashAdChoice.NONE
        }

    /**
     * Step 1 of the app-tap decision: which ad format, if any.
     *
     * | isAppClickOpenAdOn | isInterOn && isAppClickInterOn | format         |
     * |--------------------|-------------------------------|----------------|
     * | true               | either                        | [AppClickAdFormat.APP_OPEN]     |
     * | false              | true                          | [AppClickAdFormat.INTERSTITIAL] |
     * | false              | false                         | [AppClickAdFormat.NONE]         |
     *
     * App-open wins outright when both switches are on, so `intertitialoropenads`
     * is not consulted here — it still decides for the swipe / back / plus paths.
     * The app-open branch answers to [isAppClickOpenAdOn] alone; only the
     * interstitial branch is additionally gated by the global [isInterOn].
     */
    val appClickAdFormat: AppClickAdFormat
        get() = when {
            isAppClickOpenAdOn -> AppClickAdFormat.APP_OPEN
            isInterOn && isAppClickInterOn -> AppClickAdFormat.INTERSTITIAL
            else -> AppClickAdFormat.NONE
        }

    /**
     * Step 2 of the app-tap decision: which apps the chosen format applies to.
     *
     * | isAdShowOnAllApps | isAdShowOnlyAdConsumeApps | scope                  |
     * |-------------------|---------------------------|------------------------|
     * | true              | false                     | [AppClickAdScope.ALL_APPS]           |
     * | false             | true                      | [AppClickAdScope.AD_CONSUMING_ONLY]  |
     * | true              | true                      | [AppClickAdScope.AD_CONSUMING_ONLY]  |
     * | false             | false                     | [AppClickAdScope.NONE]               |
     *
     * The restrictive flag wins when both are set, and no flag set means no app-tap
     * ad — the two flags are the two available modes, so selecting none selects off.
     * A format of [AppClickAdFormat.NONE] short-circuits the whole thing.
     */
    val appClickAdScope: AppClickAdScope
        get() = when {
            appClickAdFormat == AppClickAdFormat.NONE -> AppClickAdScope.NONE
            isAdShowOnlyAdConsumeApps -> AppClickAdScope.AD_CONSUMING_ONLY
            isAdShowOnAllApps -> AppClickAdScope.ALL_APPS
            else -> AppClickAdScope.NONE
        }
    val canShowSwipeInter: Boolean get() = (isInterOn || isAppOpenOn) && isRightLeftSwipeInterOn
    val interCount: Int get() = appClickInterCount
    val interBackCount: Int get() = swipeInterCount

    companion object {
        const val DEFAULT_BANNER_ID = "11"
        const val DEFAULT_NATIVE_ID = "11"
        const val DEFAULT_INTER_ID = "11"
        const val DEFAULT_INTER_SET_AS_DEFAULT_ID = "11"
        const val DEFAULT_APP_OPEN_ID = "11"

        val DEFAULT = AdsConfig()
    }
}

/** Which apps a launcher tap may carry an ad for. See [AdsConfig.appClickAdScope]. */
enum class AppClickAdScope {
    /** No app-tap ad, whatever the app is. */
    NONE,

    /** Every app is eligible. */
    ALL_APPS,

    /** Only apps whose manifest declares the AdMob app id are eligible. */
    AD_CONSUMING_ONLY,
}

/** Which ad format a launcher tap shows. See [AdsConfig.appClickAdFormat]. */
enum class AppClickAdFormat {
    /** No app-tap ad. */
    NONE,

    /** Interstitial, using `interAdId`. */
    INTERSTITIAL,

    /** App-open ad, using `open_ad` (falling back to `appOpenAdId`). */
    APP_OPEN,
}
