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
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import app.messages.ads.listeners.AppOpenAdsListener
import app.messages.ads.model.AdsModel
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.data.CacheManager
import app.messages.data.db.AppDatabase
import app.messages.preferences.PreferenceHelper
import app.messages.ui.activity.LanguageSelectionActivity
import app.messages.ui.activity.OnboardingActivity
import app.messages.ui.activity.PermissionActivity
import app.messages.ui.activity.SetDefaultSmsActivity
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
        
        // Initialize BillingHelper to check premium status early
        app.messages.utility.BillingHelper.getInstance(this)

//        AppTimerManager.initialize(this)

        AnalyticsHelper.init(this)
        AnalyticsHelper.logEvent(EventNames.APP_OPEN)

        processLifecycleOwner()
        registerActivityLifecycle()

        adsConfiguration()

        // Pre-warm database and cache in background
        ioScope.launch {
            try {
                // Initialize database connection
                AppDatabase.getDatabase(this@Controller)

                // Initialize cache manager
                CacheManager.getInstance(this@Controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun adsConfiguration() {
        val testDeviceIdAds: List<String> = mutableListOf(
            "A60DCA743B4F66FE88947C10E6F2A6FE",
        )
        val requestConfiguration =
            RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIdAds)
                .build()

        MobileAds.setRequestConfiguration(requestConfiguration)

        MobileAds.initialize(this) { initializationStatus: InitializationStatus? -> }
    }


    private fun processLifecycleOwner() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        isAppInForeground = true
                        if (currentActivity != null && isActivityAllowedForAppOpenAds(activity = currentActivity)) {
                            if (canDisplayAppOpenAds && !isShowingAppOpenAd && !isLoadingAppOpenAd) {
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
                && activity !is SetDefaultSmsActivity
                && activity !is app.messages.ui.activity.SetDefaultLauncherActivity
                && activity !is PermissionActivity
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
        placement: String = "general",
        revenue: Double,
        currency: String?,
        precision: Int
    ) {
        if (!app.messages.config.AdsConfigManager.shouldSendRevenueForPlacement(placement)) {
            Log.d("AdRevenue", "sendRevenueToAnalytics skipped for placement: $placement")
            return
        }
        Log.d("AdRevenue", "sendRevenueToAnalytics [$placement] -> revenue: $revenue, currency: $currency, precision: $precision")
        val analytics = FirebaseAnalytics.getInstance(instance)
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
            putString(FirebaseAnalytics.Param.AD_SOURCE, "AdMob")
            putString(FirebaseAnalytics.Param.AD_FORMAT, placement)
            putDouble(FirebaseAnalytics.Param.VALUE, revenue)
            putString(FirebaseAnalytics.Param.CURRENCY, currency?.takeIf { it.isNotBlank() } ?: "USD")
            putInt("precision_type", precision)
        }
        analytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
    }

    fun sendRevenueToAnalytics(revenue: Double, currency: String?, precision: Int) {
        sendRevenueToAnalytics("general", revenue, currency, precision)
    }

    fun showAppOpenAds(
        activity: Activity,
        onShowAdCompleteListener: AppOpenAdsListener
    ) {
        // App open ads logic has been moved to AdsConfigManager (Remote Config)
        onShowAdCompleteListener.appOpenAdsShow()
    }

}
