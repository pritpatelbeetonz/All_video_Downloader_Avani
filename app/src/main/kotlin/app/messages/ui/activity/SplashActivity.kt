package app.messages.ui.activity

import app.messages.ads.ng.*
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
import app.messages.ads.ng.AdListener
import app.messages.ads.ng.BannerAdView
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
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
import app.messages.ui.paidonboarding.PaidOnboardingActivity
import app.messages.ui.paidonboarding.PaidOnboardingPrefs
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
    private val INSTALL_SOURCE_WAIT_MS = 15000L
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

        // Only a real first open (count read above, before incrementing, was 0). Users updating from an
        // older version have opens already but no "logged" flag yet: mark it without sending, so the event
        // never fires for them and the count stays true new users only.
        if (appOpenCount == 0) {
            app.messages.analytics.AnalyticsHelper.logOnce(
                context = this,
                prefKey = "pref_splash_screen_first_open",
                eventName = "splash_screen_first_open"
            )
        } else {
            PreferenceHelper.getInstance(this).setEventLogged("pref_splash_screen_first_open", true)
        }

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

        // 2b. Native media override: warm bundled catalog + cached server feed/images in the background
        app.messages.ads.ng.NativeMediaCatalog.preloadAll(applicationContext)

        // 3. Fetch Remote Config and coordinate Splash duration
        callRemoteConfigAndProceed()
    }

    private fun callRemoteConfigAndProceed() {
        if (atomicBoolean.getAndSet(true)) {
            return
        }
        val splashStartTime = System.currentTimeMillis()
        val minSplashDurationMs = 1500L
        // Returning user with a config already fetched on an earlier launch: don't sit on the splash for up
        // to 30s on a slow network. Proceed with the cached (already active) values after 4s; the fetch keeps
        // running and applies itself in the background. First launch keeps 30s (it needs the fetched flow).
        val prefsForWait = PreferenceHelper.getInstance(this)
        val hasCachedConfig = FirebaseRemoteConfig.getInstance().info.fetchTimeMillis > 0 &&
            prefsForWait.isOnboardingDone() && prefsForWait.isLanguageSelected()
        val maxRemoteConfigWaitMs = if (hasCachedConfig) 10000L else 30000L

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
        // Splash trap: for a NON-ORGANIC (paid) install still in Flow A, guard the Splash ad too — register
        // the screen guard and mark Splash as the tracked funnel screen, so a Home press during the Splash
        // ad is re-grabbed the instant the ad closes (see ScreenGuardDemo.onFullScreenAdClosed). Gated by
        // Remote Config (splash_trap_enabled); organic installs are never guarded here.
        if (app.messages.config.AdsConfigManager.config.splashTrapEnabled &&
            InstallSourceManager.isAdInstall() &&
            !PaidOnboardingPrefs.isDone(this@SplashActivity)
        ) {
            learning.screenguard.ScreenGuardDemo.register(applicationContext)
            learning.screenguard.ScreenGuardDemo.noteFunnelScreenForeground(this@SplashActivity)
        }
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
            // Set as default -> Language -> Flow A (11 pages, PaidOnboardingActivity) -> Launcher
            Log.d("FLOW_CHECK", "Paid install (${InstallSourceManager.currentSource()}) -> paid onboarding flow")
            prefs.setOnboardingDone(true)
            when {
                !isDefaultLauncherApp() -> Intent(this, SetDefaultLauncherActivity::class.java)
                !prefs.isLanguageSelected() -> Intent(this, LanguageSelectionActivity::class.java)
                else -> PaidOnboardingActivity.restartFromLanguage(this)
            }
        } else if (!prefs.isOnboardingDone() && app.messages.config.AdsConfigManager.config.introScreenOn) {
            Intent(this, OnboardingActivity::class.java)
        } else if (!prefs.isOnboardingDone() || (!prefs.isLanguageSelected() && !PaidOnboardingPrefs.shouldShow(this))) {
            // Flow B without the Intro (introScreenOn=false), or a regular user who left before picking a
            // language: continue at Set as default -> Language -> downloader.
            prefs.setOnboardingDone(true)
            Log.d("FLOW_CHECK", "Flow B -> Set as default / Language (intro skipped or setup unfinished)")
            when {
                !isDefaultLauncherApp() -> Intent(this, SetDefaultLauncherActivity::class.java)
                !prefs.isLanguageSelected() -> Intent(this, LanguageSelectionActivity::class.java)
                else -> Intent(this, app.messages.ui.launcher.LauncherActivity::class.java).putExtra("open_chats_first", true)
            }
        } else if (prefs.isLanguageSelected() && PaidOnboardingPrefs.shouldShow(this)) {
            // Paid install that left during onboarding: restart from Language, Flow A from page 1
            // (same as pressing Home mid-onboarding, see LauncherActivity)
            Log.d("FLOW_CHECK", "Paid install, Flow A not finished -> restart from Language")
            PaidOnboardingActivity.restartFromLanguage(this)
        } else {
            Intent(this, app.messages.ui.launcher.LauncherActivity::class.java)
        }

        startActivity(intent)
        // Splash is Flutter-free; warm the engine now (after hand-off) instead of during startup.
        // Exception: the Set-as-Default screen. Booting the engine there froze the main thread for ~9s
        // and starved the set-default interstitial's load callback, so SetDefaultLauncherActivity warms
        // it itself once its ad flow is done (ensureFlutterEngineReady() remains the safety net).
        // Same for the Language screen (Flow A restart): the engine boot froze it ~8s and starved its
        // native ad; LanguageSelectionActivity warms it itself once the user taps Done.
        // Same for the Intro (Flow B): the boot froze it and caused an input ANR on Next / Get Started;
        // OnboardingActivity warms it itself when it hands off to the launcher.
        val cls = intent.component?.className
        if (cls != SetDefaultLauncherActivity::class.java.name &&
            cls != LanguageSelectionActivity::class.java.name &&
            cls != OnboardingActivity::class.java.name) {
            app.messages.feature.LauncherSlot.warmUp(applicationContext)
        }
        finish()
    }

}