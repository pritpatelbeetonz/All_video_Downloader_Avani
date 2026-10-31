package app.messages.ui.activity

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import app.messages.BuildConfig
import app.messages.Controller
import app.messages.Controller.Companion.adsModel


import app.messages.ads.model.AdsModel
import app.messages.analytics.AnalyticsHelper.logAds
import app.messages.constant.Constants
import app.messages.databinding.ActivitySplashBinding
import app.messages.preferences.PreferenceHelper
import app.messages.attribution.InstallSourceManager
import app.messages.ui.base.BaseActivity
import android.graphics.Color
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Boolean
import kotlin.jvm.java

class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding

    lateinit var remoteConfig: FirebaseRemoteConfig
    private val atomicBoolean = AtomicBoolean(false)
    private val isNextActivityStarted = AtomicBoolean(false)
    /** Longest the splash waits for the install source after Remote Config; past it the flow is regular. */
    private val INSTALL_SOURCE_WAIT_MS = 6000L
    var appOpenCount = 0

    override fun shouldApplySystemBarInsets(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        findViewById<FrameLayout>(android.R.id.content)?.let {
            it.setPadding(0, 0, 0, 0)
            it.setBackgroundColor(Color.TRANSPARENT)
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appOpenCount = PreferenceHelper.getInstance(this).getAppOpenCount()

        val isPremium = PreferenceHelper.getInstance(this).isPremium()
        
        // Splash Banner is completely disabled
        binding.bannerViewAdsFrameLayout.bannerAdShimmerFrameLayout.visibility = View.GONE
        binding.bannerViewAdsFrameLayout.bannerAdFrameLayout.visibility = View.GONE

        incrementAppOpenCount(this)

        if (isInternetAvailable(this@SplashActivity)) {
            adsConfiguration()
//            preloadAdsAndGoToNextActivity()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                nextActivity()
            }, 0)
        }

        /*Handler(Looper.getMainLooper()).postDelayed({
            nextActivity()
        }, 300)*/
    }

    fun incrementAppOpenCount(context: Context) {
        val prefs = PreferenceHelper.getInstance(context)
        val count = prefs.getAppOpenCount()
        prefs.setAppOpenCount(count + 1)

    }



    private fun adsConfiguration() {
        // 1. Immediately preload the Splash Ad with cached / default Remote Config
        app.messages.config.AdsConfigManager.preloadSplashAd(this@SplashActivity)

        // 2. Gather UMP Consent asynchronously in background (non-blocking)
        val googleMobileAds = app.messages.ads.GoogleMobileAds.getInstance(applicationContext)
        googleMobileAds.gatherConsent(this) { error ->
            if (googleMobileAds.canRequestAds) {
                app.messages.config.AdsConfigManager.preloadSplashAd(this@SplashActivity)
            }
        }

        // 3. Fetch Remote Config and coordinate Splash duration
        callRemoteConfigAndProceed()
    }

    private fun callRemoteConfigAndProceed() {
        if (atomicBoolean.getAndSet(true)) {
            return
        }
        val splashStartTime = System.currentTimeMillis()
        val minSplashDurationMs = 1500L
        val maxRemoteConfigWaitMs = 30000L

        val isConfigDone = AtomicBoolean(false)

        fun proceedToAd() {
            if (isConfigDone.compareAndSet(false, true)) {
                // Install source (google / regular ...) from the values just fetched: it picks the
                // onboarding flow in nextActivity(). Saved on an earlier launch = answers at once.
                InstallSourceManager.checkFromSplash(INSTALL_SOURCE_WAIT_MS) { source ->
                    // TODO: whether to load / show the splash interstitial for this source (rule to come)
                    Log.d("SplashAd", "Install source: $source")
                    val elapsed = System.currentTimeMillis() - splashStartTime
                    val remainingDelay = (minSplashDurationMs - elapsed).coerceAtLeast(0L)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isFinishing && !isDestroyed && !isNextActivityStarted.get()) {
                            showSplashAdAndNext()
                        }
                    }, remainingDelay)
                }
            }
        }

        // Safety fallback: if Remote Config takes more than 30 seconds, proceed with cached config
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            Log.w("SplashAd", "⏰ Remote config fetch timed out after ${maxRemoteConfigWaitMs}ms -> proceeding with cached config")
            proceedToAd()
        }
        handler.postDelayed(timeoutRunnable, maxRemoteConfigWaitMs)

        app.messages.config.AdsConfigManager.refresh { success ->
            handler.removeCallbacks(timeoutRunnable)

            // Sync with old AdsModel so any old code doesn't crash
            var json = FirebaseRemoteConfig.getInstance().getString(app.messages.config.AdsConfigManager.KEY_ADS_CONFIG)
            if (json.isEmpty()) {
                json = FirebaseRemoteConfig.getInstance().getString("ads_config")
            }
            if (json.isNotEmpty()) {
                try {
                    val adModel = Gson().fromJson(json, AdsModel::class.java)
                    Controller.adsModel = adModel
                } catch (e: Exception) {
                }
            }

            Controller.isRemoteConfigLoaded.postValue(true)

            // Preload with any newly fetched ad unit ID or switch
            app.messages.config.AdsConfigManager.preloadSplashAd(this@SplashActivity)

            proceedToAd()
        }
    }

    private fun showSplashAdAndNext() {
        app.messages.config.AdsConfigManager.showSplashAd(
            activity = this@SplashActivity,
            timeoutMs = 30000L
        ) {
            nextActivity()
        }
    }

    /**
     * Robust check for default SMS app status.
     * Uses RoleManager on Android Q+ (more reliable on OEM devices like Samsung/Xiaomi/MIUI).
     * Falls back to Telephony.Sms.getDefaultSmsPackage() on older devices.
     */
    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    private fun isDefaultLauncherApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        }
    }

    private fun nextActivity() {
        if (isNextActivityStarted.getAndSet(true)) return
        if (isFinishing || isDestroyed) return
        val prefs = PreferenceHelper.getInstance(this)

        if (prefs.isFirstAppLaunch()) {
            prefs.setFirstAppLaunch(false)
        }

        val intent = if (!prefs.isOnboardingDone() && InstallSourceManager.isAdInstall()) {
            // Paid install (google / facebook / unity): skips the regular onboarding.
            // Set as default -> Language -> (11 intro screens, to be added) -> Launcher
            Log.d("FLOW_CHECK", "Paid install (${InstallSourceManager.currentSource()}) -> paid onboarding flow")
            prefs.setOnboardingDone(true)
            when {
                !isDefaultLauncherApp() -> Intent(this, SetDefaultLauncherActivity::class.java)
                !prefs.isLanguageSelected() -> Intent(this, LanguageSelectionActivity::class.java)
                else -> Intent(this, app.messages.ui.launcher.LauncherActivity::class.java).apply {
                    putExtra("open_chats_first", true)
                }
            }
        } else if (!prefs.isOnboardingDone()) {
            Intent(this, OnboardingActivity::class.java)
        } else {
            Intent(this, app.messages.ui.launcher.LauncherActivity::class.java)
        }

        startActivity(intent)
        finish()
    }

}