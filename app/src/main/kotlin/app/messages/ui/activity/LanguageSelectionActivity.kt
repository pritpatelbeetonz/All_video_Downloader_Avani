package app.messages.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import app.messages.Controller
import app.messages.Controller.Companion.adsModel
import app.messages.R
import app.messages.databinding.ActivityLanguageSelectionBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.adapters.LanguageAdapter
import app.messages.ui.base.BaseActivity
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.AnalyticsHelper.logAds
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import app.messages.constant.Constants
import app.messages.data.LanguageData
import app.messages.utility.BackupUtility
import kotlinx.coroutines.launch

class LanguageSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityLanguageSelectionBinding
    private lateinit var adapter: LanguageAdapter
    var prefs: PreferenceHelper? = null
    private var isFromSettings: Boolean = false

    /** Done, the Done button and the back gesture all land in saveLanguage(). */
    private val isNavigatingNext = java.util.concurrent.atomic.AtomicBoolean(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("SetDefaultFlow", "[LanguageSelectionActivity] onCreate() entered")

        prefs = PreferenceHelper.getInstance(this)
        // Onboarding marks itself done before this screen, so that flag can't tell
        // the two apart. A language is only already chosen when coming from
        // Settings; read it before it is set below.
        isFromSettings = intent.getBooleanExtra("from_settings", false) || (prefs?.isLanguageSelected() == true)

        // Mark as selected immediately so pressing Home/Back doesn't cause a loop
        if (prefs?.isLanguageSelected() == false) {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Setting language selected flag = true (default en)")
            prefs?.setLanguageSelected(true)
            prefs?.setSelectedLanguageCode("en")
        }
        
        // Warm it now so the Done tap shows it instantly rather than parking the
        // user on a loader while it fetches on demand.
        Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Preloading language interstitial ad and generic interstitial")
        app.messages.config.AdsConfigManager.preloadLanguageInterstitial(this)
        app.messages.config.AdsConfigManager.preloadInterstitial(this)

        setupUI()
        setupRecyclerView()

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                app.messages.config.AdsConfigManager.configFlow.collect { config ->
                    if (!config.isNativeOn || prefs?.isPremium() == true) {
                        binding.nativeAdCardView.visibility = View.GONE
                    } else if (binding.nativeAdFrameLayout.childCount == 0 || binding.nativeAdCardView.visibility == View.GONE) {
                        loadBigNativeAd()
                    }
                }
            }
        }
    }

    private fun loadBigNativeAd() {
        Log.d("ADS_LANGUAGE", "🚀 [LANGUAGE_UI_REQUEST] LanguageSelectionActivity requested Big Native Ad for screen")
        binding.nativeAdCardView.visibility = View.VISIBLE
        binding.nativeAdShimmerFrameLayout.visibility = View.VISIBLE
        binding.nativeAdFrameLayout.visibility = View.GONE

        app.messages.config.AdsConfigManager.loadNativeAd(
            context = this,
            adUnitId = app.messages.config.AdsConfigManager.config.nativeAdIdLanguage,
            onLoaded = { nativeAd ->
                Log.d("ADS_LANGUAGE", "✨ [LANGUAGE_UI_SUCCESS] Native Ad delivered to UI! (Headline: \"${nativeAd.headline}\")")
                if (isDestroyed || isFinishing) return@loadNativeAd
                val adView = layoutInflater.inflate(R.layout.ads_native_big, null) as com.google.android.gms.ads.nativead.NativeAdView
                app.messages.config.AdsConfigManager.populateNativeAdView(nativeAd, adView)
                binding.nativeAdShimmerFrameLayout.visibility = View.GONE
                binding.nativeAdFrameLayout.visibility = View.VISIBLE
                binding.nativeAdFrameLayout.removeAllViews()
                binding.nativeAdFrameLayout.addView(adView)
                Log.d("ADS_LANGUAGE", "🎉 [LANGUAGE_UI_RENDERED] Big Native Ad inflated & DISPLAYED on screen!")
            },
            onFailed = {
                Log.e("ADS_LANGUAGE", "❌ [LANGUAGE_UI_FAILED] Big Native Ad failed to load / render")
                if (!isDestroyed && !isFinishing) {
                    binding.nativeAdCardView.visibility = View.GONE
                }
            }
        )
    }


    private fun setupUI() {
        binding.btnNext.setOnClickListener {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] User clicked Next button")
            AnalyticsHelper.logEvent("language_done_clicked", EventScreenName.LANGUAGE)
            saveLanguage()
        }
        binding.btnDone.setOnClickListener {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] User clicked Done button")
            AnalyticsHelper.logEvent("language_done_clicked", EventScreenName.LANGUAGE)
            saveLanguage()
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Back button pressed")
                saveLanguage()
            }
        })
    }

    fun saveLanguage() {
        val selectedCode = if (adapter.hasSelection()) {
            adapter.getSelectedLanguage()?.code ?: "en"
        } else {
            prefs?.getSelectedLanguageCode()?.ifBlank { "en" } ?: "en"
        }
        
        Log.d("SetDefaultFlow", "[LanguageSelectionActivity] saveLanguage() called. Selected language: $selectedCode")
        prefs?.setLanguageSelected(true)
        prefs?.setSelectedLanguageCode(selectedCode)
        app.messages.utility.LocaleHelper.setLocale(this, selectedCode)
        app.messages.utility.LocaleHelper.syncLanguageToFlutter(this, selectedCode)
        app.messages.ui.videodownloader.VideoDownloaderEngineHelper.notifyLanguageChanged(selectedCode)

        // Log one-time Firebase Analytics event for language selection
        val languageBundle = android.os.Bundle().apply {
            putString("language_code", selectedCode)
        }
        app.messages.analytics.AnalyticsHelper.logOnce(
            context = this,
            prefKey = app.messages.preferences.PreferenceHelper.KEY_EVENT_LANGUAGE_LOGGED,
            eventName = app.messages.analytics.EventNames.LANGUAGE_SCREEN_DONE,
            params = languageBundle
        )

        if (!isNavigatingNext.compareAndSet(false, true)) {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] saveLanguage() ignored (already navigating)")
            return
        }

        // Only preload launcher session ads if user is onboarding for the first time
        val isFirstLaunchOnboarding = !isFromSettings
        if (isFirstLaunchOnboarding) {
            Log.d("ADS_DASHBOARD", "⚡ [LANGUAGE_DONE] User clicked Done on onboarding -> Preloading Native Dashboard Ad...")
            Log.d("ADS_GOOGLE_SEARCH", "⚡ [LANGUAGE_DONE] User clicked Done on onboarding -> Preloading Google Search Native Ad...")
            Log.d("ADS_DRAWER", "⚡ [LANGUAGE_DONE] User clicked Done on onboarding -> Preloading App Drawer Native Ad...")
            app.messages.config.AdsConfigManager.preloadDashboardNativeAd(this)
            app.messages.config.AdsConfigManager.preloadGoogleSearchNativeAd(this)
            app.messages.config.AdsConfigManager.preloadDrawerNativeAd(this)
        }

        Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Triggering showLanguageInterstitial")
        app.messages.config.AdsConfigManager.showLanguageInterstitial(this) {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] showLanguageInterstitial callback fired")
            if (isFromSettings) {
                // If user opened from Settings, just return back
                finish()
            } else {
                if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
                    // Paid install: Flow A (11 pages) before the Launcher
                    android.util.Log.d("FLOW_CHECK", "🚀 [LANGUAGE_DONE] Paid install -> PaidOnboardingActivity (Flow A)")
                    startActivity(app.messages.ui.paidonboarding.PaidOnboardingActivity.intent(this))
                    finish()
                    return@showLanguageInterstitial
                }
                try {
                    android.util.Log.d("FLOW_CHECK", "🚀 [LANGUAGE_DONE] User finished Language Selection -> Launching LauncherActivity with open_chats_first=true (Targeting Video Downloader)")
                    startActivity(
                        Intent(this, app.messages.ui.launcher.LauncherActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            putExtra("open_chats_first", true)
                        }
                    )
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                } catch (e: Exception) {
                    android.util.Log.e("FLOW_CHECK", "❌ [LANGUAGE_DONE_ERROR] Failed to start LauncherActivity: ${e.message}", e)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = LanguageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val prefs = app.messages.preferences.PreferenceHelper.getInstance(this)
        // Default to "en" on first launch so English is pre-selected
        val selectedCode = prefs.getSelectedLanguageCode().ifBlank { "en" }
        val countries = LanguageData.getCountries().toMutableList()

        // Mark the selected language (pre-selects English on first launch)
        countries.forEach { it.isSelected = it.code == selectedCode }

        adapter.submitList(countries)

        // Scroll to the selected item so it is visible
        val selectedIndex = countries.indexOfFirst { it.isSelected }
        if (selectedIndex >= 0) {
            binding.recyclerView.scrollToPosition(selectedIndex)
        }
    }

    private fun isTaskRootLauncher(): Boolean {
        return app.messages.preferences.PreferenceHelper.getInstance(this).isLauncherModeEnabled()
    }

    /** Already the SMS handler? Then the prompt screen has nothing to ask for. */
    private fun isDefaultSmsApp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null) return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        }
        return android.provider.Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }

    private fun isDefaultHomeApp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == packageName
    }
}
