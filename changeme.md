# Monetization & Ad Optimization Implementation Plan & Architecture Guide (`changeme.md`)

This document provides a technical reference, architectural review, and step-by-step implementation guide for optimizing ad preloading, preventing wasted ad requests, implementing Google UMP GDPR compliance, and handling system launcher role transitions across apps.

---

## 🏛️ Architectural Principles & Best Practices

### 1. Centralized Ad Unit Validation (`isValidAdUnitId`)
* **Problem**: Validation checks (`isNotBlank() && it != "11"`) are easily duplicated and can miss public Google AdMob test IDs (`ca-app-pub-3940256099942544/...`).
* **Architectural Fix**: Use a single extension function in `AdsConfig.kt`:
  ```kotlin
  fun String?.isValidAdUnitId(): Boolean =
      !isNullOrBlank() && this != "11" && !startsWith("ca-app-pub-3940256099942544")
  ```
  Every preloader and loader MUST validate unit IDs using this extension before attempting any AdMob network request.

### 2. Loading Dialog Safety & 10-Second Timeout Handler
* **Problem**: Custom full-screen translucent loader dialogs can hang indefinitely if an AdMob request encounters a silent network timeout or fails without firing callbacks, trapping the user on a loading screen.
* **Architectural Fix**:
  * Attach a `10,000ms` (10s) `Handler` timeout runnable to every full-screen loader dialog (`showSetAsDefaultInterstitial`, `showLanguageInterstitial`, `showInterstitialWithLoader`, `showAppClickOpenAdWithLoader`, `showAppOpenAdWithLoader`).
  * Check `!activity.isFinishing && !activity.isDestroyed` before showing or dismissing dialogs to prevent `WindowManager.BadTokenException` crashes.

### 3. Consolidating Dual Ad Frameworks (Legacy vs. Modern)
* **Problem**: Legacy Java ad utilities (`AdsLoadUtil`, `AdsSplashUtils`, `AdsPreloadUtils`) conflict with modern Kotlin singletons (`AdsConfigManager.kt`).
* **Architectural Fix**: Migrate all screens exclusively to `AdsConfigManager.kt` so state, caching, and Remote Config updates remain in sync across the app.

### 4. Gated Preloading & On-Demand Execution
* **Problem**: Preloading ads on app startup or onboarding setup screens (`SplashActivity`, `SetDefaultLauncherActivity`, `LanguageSelectionActivity`) wastes thousands of requests for users who abandon setup or delay choices.
* **Architectural Fix**:
  * Gate `preloadAllAds()` with `PreferenceHelper.isLanguageSelected()`. If setup is incomplete, `preloadAllAds()` returns early.
  * Trigger `preloadSetAsDefaultInterstitial()` **strictly on-demand** when the user taps the *"Set as Default Launcher"* button.
  * Trigger `preloadAllAds()` **only when `LauncherActivity` opens** and setup is confirmed complete.

---

## 📋 Component-by-Component Implementation Guide

### 1. `AdsConfig.kt` (Configuration Model)

#### Key Additions:
```kotlin
data class AdsConfig(
    // Unit ID mappings with alternates for Remote Config
    @SerializedName(value = "bannerAdIdSplash", alternate = ["banner_ad_id_splash"])
    val bannerAdIdSplash: String = DEFAULT_BANNER_ID,

    @SerializedName(value = "nativeAdIdDashboard", alternate = ["native_ad_id_dashboard", "native_dashboard"])
    val nativeAdIdDashboard: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdLanguage", alternate = ["native_ad_id_language", "native_language"])
    val nativeAdIdLanguage: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "nativeAdIdChatScreen", alternate = ["native_ad_id_chat_screen", "native_chat_screen"])
    val nativeAdIdChatScreen: String = DEFAULT_NATIVE_ID,

    @SerializedName(value = "inter_ad_id_set_as_default", alternate = ["inter_ad_id_set_default", "interAdIdSetAsDefault", "inter_set_as_default"])
    val interAdIdSetAsDefault: String = DEFAULT_INTER_SET_AS_DEFAULT_ID,

    @SerializedName(value = "inter_ad_id_language", alternate = ["inter_language", "interAdIdLanguage"])
    val interAdIdLanguage: String = DEFAULT_INTER_ID,
    
    // ...
) {
    companion object {
        const val DEFAULT_BANNER_ID = "11"
        const val DEFAULT_NATIVE_ID = "11"
        const val DEFAULT_INTER_ID = "11"
        const val DEFAULT_INTER_SET_AS_DEFAULT_ID = "11"
        const val DEFAULT_APP_OPEN_ID = "11"
        
        val DEFAULT = AdsConfig()
    }
}

/** Extension function to validate unit IDs before sending network requests. */
fun String?.isValidAdUnitId(): Boolean =
    !isNullOrBlank() && this != "11" && !startsWith("ca-app-pub-3940256099942544")
```

---

### 2. `AdsConfigManager.kt` (Ad Management Engine)

#### Key Implementation Logic:

```kotlin
// 1. Gate preloadAllAds behind setup completion check
fun preloadAllAds(context: Context) {
    val appContext = context.applicationContext
    val prefs = PreferenceHelper.getInstance(appContext)
    if (!prefs.isLanguageSelected()) {
        Log.d(TAG, "Skipping preloadAllAds: Setup flow not completed yet")
        return
    }

    if (config.interAdPreload) preloadInterstitial(appContext)
    if (config.appOpenAdPreload) preloadAppOpenAd(appContext)
    preloadAppClickOpenAd(appContext)
    if (config.nativeAdPreload) preloadNativeAds(appContext)
    if (config.bannerAdPreload) preloadBannerAds(appContext)
}

// 2. Validate unit IDs in preloading methods using isValidAdUnitId()
fun preloadInterstitial(context: Context) {
    if (!config.canShowInter || !config.interAdPreload) return
    val targetAdUnitId = config.interAdId.takeIf { it.isValidAdUnitId() } ?: return
    if (interstitialAd != null || isInterstitialLoading) return
    
    // Perform AdMob InterstitialAd.load...
}

// 3. Implement 10-Second Timeout in full-screen loaders
fun showSetAsDefaultInterstitial(activity: Activity, onComplete: () -> Unit) {
    val timeoutHandler = Handler(Looper.getMainLooper())
    var isCompleted = false

    fun completeOnce() {
        if (isCompleted) return
        isCompleted = true
        onComplete()
    }

    val timeoutRunnable = Runnable {
        Log.d("SetDefaultFlow", "Loader 10s timeout reached -> dismissing loader and proceeding")
        if (!activity.isDestroyed && !activity.isFinishing) {
            try { loader?.dismiss() } catch (e: Exception) {}
        }
        completeOnce()
    }
    timeoutHandler.postDelayed(timeoutRunnable, 10000L)

    // Load / show interstitial, then cancel timeout handler on finish:
    // timeoutHandler.removeCallbacks(timeoutRunnable)
}
```

---

### 3. `SetDefaultLauncherActivity.kt` (On-Demand Trigger)

#### Key Implementation Logic:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // DO NOT preload SetAsDefault interstitial in onCreate()!

    binding.btnSetDefault.setOnClickListener {
        Log.d("SetDefaultFlow", "User clicked 'Set as Default' button.")
        // Trigger preloading strictly on-demand when button is tapped
        AdsConfigManager.preloadSetAsDefaultInterstitial(this)
        requestDefaultLauncherRole()
    }
}
```

---

### 4. `LauncherActivity.kt` (Role Transition & Launcher Entry)

#### Key Implementation Logic:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val prefs = PreferenceHelper.getInstance(this)
    
    // System RoleManager.ROLE_HOME grant fires HOME intent directly to LauncherActivity,
    // bypassing SetDefaultLauncherActivity.goToNext(). Show SetAsDefault ad here if setup incomplete.
    if (!prefs.isLanguageSelected()) {
        Log.d("SetDefaultFlow", "LauncherActivity launched via HOME role before setup -> Showing SetAsDefault Interstitial")
        AdsConfigManager.showSetAsDefaultInterstitial(this) {
            startActivity(Intent(this, LanguageSelectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
        return
    }

    // Trigger preloadAllAds ONLY when user lands on main launcher interface
    Log.d("SetDefaultFlow", "User is on main launcher (isLanguageSelected=true) -> Preloading all launcher ads")
    AdsConfigManager.preloadAllAds(this)
}
```

---

### 5. `SplashActivity.kt` (Google UMP GDPR Consent Flow)

#### Key Implementation Logic:
```kotlin
private fun adsConfiguration() {
    val googleMobileAds = GoogleMobileAds.getInstance(applicationContext)
    googleMobileAds.gatherConsent(this) { error ->
        if (googleMobileAds.canRequestAds) {
            admobAdsInitialize()
        } else {
            callRemoteConfig()
        }
    }
    if (googleMobileAds.canRequestAds) {
        admobAdsInitialize()
    }
}
```

---

### 6. `NativeAdManager.kt` (SMS Inbox Native Ad)

#### Key Implementation Logic:
```kotlin
private val nativeUnitId: String
    get() = AdsConfigManager.config.nativeAdIdChatScreen
        .takeIf { it.isValidAdUnitId() }
        ?: AdsConfigManager.config.nativeAdIdDashboard
        .takeIf { it.isValidAdUnitId() }
        ?: remoteConfig.nativeUnitId
```

---

### 7. `Controller.kt` (Analytics Revenue Gate)

#### Key Implementation Logic:
```kotlin
fun sendRevenueToAnalytics(revenue: Double, currency: String?, precision: Int) {
    if (!app.messages.config.AdsConfigManager.config.sendRevenueToAnalytics) {
        Log.d("AdRevenue", "sendRevenueToAnalytics skipped (sendRevenueToAnalytics = false)")
        return
    }
    val analytics = FirebaseAnalytics.getInstance(instance)
    val bundle = Bundle().apply {
        putString(FirebaseAnalytics.Param.AD_PLATFORM, "Google AdMob Manager")
        putDouble(FirebaseAnalytics.Param.VALUE, revenue)
        putString(FirebaseAnalytics.Param.CURRENCY, currency ?: "USD")
        putInt("precision_type", precision)
    }
    analytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
}
```

---

## 🧪 Verification & QA Testing Plan

### Automated Verification
* Run Gradle compile to ensure clean build:
  ```bash
  ./gradlew assembleDebug
  ```

### Manual Verification Steps
1. **Placeholder Safety Test**: Set Remote Config unit ID to `"11"` or blank. Confirm in Logcat that no AdMob network requests fire.
2. **On-Demand Set-As-Default Test**: Open the Set-As-Default screen. Verify 0 ad requests are sent until the user taps the blue "Set as Default" button.
3. **10-Second Loader Timeout Test**: Simulate slow network during interstitial load. Verify loader dialog automatically dismisses after 10 seconds without trapping the user.
4. **Role Assignment Bypass Test**: Grant launcher role in Android System Settings without completing setup flow. Open home screen and verify redirection to Language Selection with the Set-As-Default interstitial ad.
5. **GDPR Consent Test**: Test in EEA geography mode (`DEBUG_GEOGRAPHY_EEA`). Confirm UMP consent form appears on first launch before MobileAds SDK initializes.
