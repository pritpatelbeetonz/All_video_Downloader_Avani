package app.messages

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import app.messages.ads.listeners.AppOpenAdsListener
import app.messages.ads.model.AdsModel
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.preferences.PreferenceHelper
import app.messages.ui.activity.LanguageSelectionActivity
import app.messages.ui.activity.OnboardingActivity
import app.messages.ui.activity.SplashActivity
import app.messages.utility.LocaleHelper
import app.messages.utility.ThemeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


open class Controller : Application() {
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var currentActivity: Activity? = null
    private var startedActivityCount = 0
    var isAppInForeground = false
        private set

    /** True while an app-open ad is on screen — blocks loading/showing another (policy violation). */
    private var isShowingAppOpenAd = false
    /** True while an app-open ad request is in flight. */
    private var isLoadingAppOpenAd = false

    companion object {

        var activeThreadId: Long = -1L
        private lateinit var instance: Controller
        fun getInstance(): Controller = instance

        var canDisplayAppOpenAds = true
        val isRemoteConfigLoaded = MutableLiveData<Boolean>()

        @JvmStatic
        var adsModel: AdsModel?
            get() {
                val data = PreferenceHelper.getInstance(instance.applicationContext).getConfigJSON()
                return if (data == null) AdsModel()
                else Gson().fromJson(data, AdsModel::class.java)
            }
            set(value) {
                PreferenceHelper.getInstance(instance.applicationContext)
                    .setConfigJSON(Gson().toJson(value))
            }
    }

    override fun attachBaseContext(base: Context) {
        val langCode = PreferenceHelper.getInstance(base).getSelectedLanguageCode()
        super.attachBaseContext(LocaleHelper.setLocale(base, langCode))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val langCode = PreferenceHelper.getInstance(this).getSelectedLanguageCode()
        LocaleHelper.setLocale(this, langCode)
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        ThemeUtils.applyTheme(this)
        
        // Initialize OneSignal from cached App ID if available
        app.messages.utility.OneSignalManager.initFromCache(this)

        // Initialize Meta Audience Network / Facebook SDK from cached App ID if available
        app.messages.utility.MetaAudienceNetworkManager.initFromCache(this)

        app.messages.config.AdsConfigManager.initialize(this)

        initializeFirebase()
        
//        AppTimerManager.initialize(this)

        AnalyticsHelper.init(this)
        AnalyticsHelper.logEvent(EventNames.APP_OPEN)

        processLifecycleOwner()
        registerActivityLifecycle()

        // NOTE: MobileAds init (and its test-device RequestConfiguration) is handled entirely by
        // AdsConfigManager.initialize() above, on a background thread. We must NOT call any
        // MobileAds.* method here on the main thread during Application.onCreate — doing so blocks
        // on the MobileAds lock held by that background init (which itself waits on WebView) and
        // ANRs the app on cold devices ("failed to complete startup"). The old adsConfiguration()
        // did exactly that and has been removed.
    }

    private fun processLifecycleOwner() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        isAppInForeground = true
                        if (currentActivity != null && isActivityAllowedForAppOpenAds(activity = currentActivity)) {
                            // The app-tap ad shows on this return instead; one ad per return.
                            if (canDisplayAppOpenAds && !isShowingAppOpenAd && !isLoadingAppOpenAd &&
                                !app.messages.config.AdsConfigManager.isReturnAdPending(this@Controller)
                            ) {
                                appOpenAdsShow(
                                    currentActivity!!,
                                    object : AppOpenAdsListener {
                                        override fun appOpenAdsShow() {

                                        }
                                    })
                            }
                            canDisplayAppOpenAds = true
                        }
                        Log.d("processLifecycleOwner", "APP MAXIMIZED (FOREGROUND)")
                    }

                    Lifecycle.Event.ON_STOP -> {
                        isAppInForeground = false
                        Log.d("processLifecycleOwner", "APP MINIMIZED (BACKGROUND)")
                    }

                    else -> Unit
                }
            }
        )
    }

    private fun isActivityAllowedForAppOpenAds(activity: Activity?): Boolean {
//        val isLanguageSelected =  languageScreen == "Yes"
        return (activity !is SplashActivity
                && activity !is app.messages.ui.activity.SetDefaultLauncherActivity
                && activity !is OnboardingActivity
                && (activity !is LanguageSelectionActivity || PreferenceHelper.getInstance(activity)
            .isLanguageSelected()))
    }

    private fun registerActivityLifecycle() {

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {

                override fun onActivityStarted(activity: Activity) {
                    currentActivity = activity
                    if (startedActivityCount == 0) {
                        // App moved to foreground
//                        isAppInForeground = true
                        Log.d("AppState", "APP FOREGROUND")
                    }
                    startedActivityCount++
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivityCount--
                    if (startedActivityCount == 0) {
                        // App moved to background
//                        isAppInForeground = false
                        Log.d("AppState", "APP BACKGROUND")
                    }
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) {
                }

                override fun onActivityResumed(activity: Activity) {
//                    isAppInForeground = true
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle
                ) {
                }

                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
    }

    private fun initializeFirebase() {

        /*if (isInternetAvailable()) {
            CoroutineScope(Dispatchers.IO).launch {
                MobileAds.initialize(applicationContext) { }
                withContext(Dispatchers.Main) {
                }
            }

            FirebaseApp.initializeApp(this)
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings =
                FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0).build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        }*/
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    fun appOpenAdsShow(
        activity: Activity,
        appOpenAdsInterface: AppOpenAdsListener
    ) {
        showAppOpenAds(
            activity,
            appOpenAdsInterface
        )

    }

    fun sendRevenueToAnalytics(
        adUnitId: String?,
        placement: String,
        revenue: Double,
        currency: String?,
        precision: Int
    ) {
        // Report to Meta (Facebook) first — its own allowlist, independent of the Firebase one below.
        app.messages.monetization.ads.MetaRevenueReporter.logAdRevenue(instance, adUnitId, revenue, currency, placement)

        val allowed = app.messages.config.AdsConfigManager.shouldSendAdImpression(adUnitId)
        Log.d("AdRevenue", "ad_impression [$placement] unit=$adUnitId allowed=$allowed revenue=$revenue $currency")
        if (!allowed) return
        val analytics = FirebaseAnalytics.getInstance(instance)
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
            putString(FirebaseAnalytics.Param.AD_SOURCE, "AdMob")
            putString(FirebaseAnalytics.Param.AD_FORMAT, adFormatOf(placement))
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString("ad_placement", placement)
            putDouble(FirebaseAnalytics.Param.VALUE, revenue)
            putString(FirebaseAnalytics.Param.CURRENCY, currency?.takeIf { it.isNotBlank() } ?: "USD")
            putInt("precision_type", precision)
        }
        analytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
        Log.d("AdRevenue", "ad_impression SENT to Firebase [$placement] format=${adFormatOf(placement)} unit=$adUnitId value=$revenue ${currency ?: "USD"}")
    }

    /** Maps a placement key like "inter_splash" or "app_click_open" to its ad format. */
    private fun adFormatOf(placement: String): String {
        val p = placement.lowercase()
        return when {
            "native" in p -> "native"
            "banner" in p -> "banner"
            "appopen" in p || "app_open" in p || p.endsWith("_open") -> "app_open"
            "inter" in p -> "interstitial"
            else -> "unknown"
        }
    }

    fun showAppOpenAds(
        activity: Activity,
        onShowAdCompleteListener: AppOpenAdsListener
    ) {
        // App open ads logic has been moved to AdsConfigManager (Remote Config)
        onShowAdCompleteListener.appOpenAdsShow()
    }

}
