package app.messages.ui.activity

import app.messages.ads.ng.*
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
import app.messages.ads.ng.AdListener
import app.messages.ads.ng.NgNativeLoader as AdLoader
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import app.messages.Controller
import app.messages.Controller.Companion.adsModel
import app.messages.R
import app.messages.databinding.ActivityLanguageSelectionBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.adapters.LanguageAdapter
import app.messages.ui.base.PaidFunnelActivity
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.AnalyticsHelper.logAds
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import app.messages.constant.Constants
import app.messages.data.LanguageData
import kotlinx.coroutines.launch

class LanguageSelectionActivity : PaidFunnelActivity() {

    private companion object {
        /** Extra 8s waits on a still-in-flight language native (2 -> up to ~24s total while on screen). */
        const val MAX_LANGUAGE_AD_EXTRA_WAITS = 2
    }

    private lateinit var binding: ActivityLanguageSelectionBinding
    private lateinit var adapter: LanguageAdapter
    var prefs: PreferenceHelper? = null
    private var isFromSettings: Boolean = false
    private var isPaidOnboarding: Boolean = false
    private var sequenceRunner: app.messages.ui.paidonboarding.FlowASequenceRunner? = null
    private val chromeTabLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        sequenceRunner?.takeIf { it.awaitingChrome }?.let {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Chrome tab closed -> continue step sequence")
            it.onChromeClosed()
            return@registerForActivityResult
        }
        Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Chrome tab closed -> continue to Flow A")
        // Guard resumes, then continue to Flow A.
        app.messages.ui.paidonboarding.PaidChromeTabManager.onWebStepClosed(this, stepTag = "language") {
            startActivity(app.messages.ui.paidonboarding.PaidOnboardingActivity.intent(this))
            finish()
        }
    }

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
        // Paid install still onboarding (first visit, or restarted by Home): Back stays here and
        // Done always continues to Flow A, even though a language was already picked.
        isPaidOnboarding = !intent.getBooleanExtra("from_settings", false) &&
            (intent.getBooleanExtra(app.messages.ui.paidonboarding.PaidOnboardingActivity.EXTRA_PAID_ONBOARDING, false) ||
                app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this))
        isFromSettings = !isPaidOnboarding &&
            (intent.getBooleanExtra("from_settings", false) || (prefs?.isLanguageSelected() == true))
        Log.d("SetDefaultFlow", "[LanguageSelectionActivity] isPaidOnboarding=$isPaidOnboarding isFromSettings=$isFromSettings")
        if (isPaidOnboarding) {
            // Flow A attribution funnel: ${prefix}_user_lang_visited.
            app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelEvery(this, "user_lang_visited")
            app.messages.onboarding.watch.OnboardingAppWatcher.register(this)
            // Register the guard HERE too: the Language screen comes before PaidOnboardingActivity,
            // so without this the guard isn't listening yet while this screen is in front.
            learning.screenguard.ScreenGuardDemo.register(this)
            // Warm the Custom Tabs session now so Chrome's TAB_HIDDEN callback is live by the time
            // the tab opens after Done (permission-free tab-exit detection, like the reference app).
            app.messages.ui.paidonboarding.PaidChromeTabManager.warmup(this)
            // Prefetch the after-language web step so the Custom Tab is ready the instant the user taps
            // Done (fresh, cache-busted URL — no stale cached page).
            app.messages.ui.paidonboarding.PaidChromeTabManager.prefetchStep(this, "after_language")
            // Preload the Flow A ad (App Open or Interstitial) for the after-language web-step close.
            app.messages.ui.paidonboarding.FlowASequenceRunner.preload(this, "language")
            // Fetch the Flow-A dummy ad images now (native.json / banner.json) so they're cached and
            // ready by the time Flow A opens after Done, then warm ONLY the first Flow-A page's ads
            // (rolling preload — page 1 here, each later page warms the next one itself).
            lifecycleScope.launch {
                app.messages.ui.paidonboarding.FlowADummyImages.fetch()
                app.messages.ui.paidonboarding.FlowAAdBinder.preloadForPage(this@LanguageSelectionActivity, 1)
            }
        } else if (!isFromSettings) {
            // Flow B (Organic install onboarding): log org_user_lang_visited once
            app.messages.analytics.AnalyticsHelper.logOnce(
                context = this,
                prefKey = "key_once_org_user_lang_visited",
                eventName = app.messages.analytics.EventNames.ORG_LANG_VISITED
            )
        }

        // Warm it now so the Done tap shows it instantly rather than parking the
        // user on a loader while it fetches on demand.
        Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Preloading language interstitial ad and generic interstitial")
        // Flow A (paid) never shows the language interstitial (it uses the Flow A step ads instead),
        // so preloading it there only creates unshown requests.
        if (!isPaidOnboarding) {
            app.messages.config.AdsConfigManager.preloadLanguageInterstitial(this)
        }
        if (app.messages.config.AdsConfigManager.config.launcherSimple) {
            // Simple launcher config: preload ONLY what the swipe / app-tap / settings patterns use (inter,
            // appopen, chrome). Flow B here; Flow A does the same when its last page finishes.
            if (!isPaidOnboarding) app.messages.config.AdsConfigManager.preloadLauncherByPatterns(this)
        } else {
            app.messages.config.AdsConfigManager.preloadInterstitial(this)
        }

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

    /** How many times the 8s single-flight wait was re-attached because the request was still in flight. */
    private var languageAdExtraWaits = 0

    private fun loadBigNativeAd(isExtraWait: Boolean = false) {
        if (!isExtraWait) languageAdExtraWaits = 0
        Log.d("ADS_LANGUAGE", "🚀 [LANGUAGE_UI_REQUEST] LanguageSelectionActivity requested Big Native Ad for screen (extraWait=$isExtraWait)")
        binding.nativeAdCardView.visibility = View.VISIBLE
        binding.nativeAdShimmerFrameLayout.visibility = View.VISIBLE
        binding.nativeAdFrameLayout.visibility = View.GONE

        app.messages.config.AdsConfigManager.loadNativeAd(
            context = this,
            adUnitId = app.messages.config.AdsConfigManager.config.nativeAdIdLanguage,
            onLoaded = { nativeAd ->
                Log.d("ADS_LANGUAGE", "✨ [LANGUAGE_UI_SUCCESS] Native Ad delivered to UI! (Headline: \"${nativeAd.headline}\")")
                if (isDestroyed || isFinishing) {
                    // Screen is gone before the ad arrived: keep the ad for the next time instead of wasting it.
                    app.messages.config.AdsConfigManager.cacheNativeAd(
                        app.messages.config.AdsConfigManager.config.nativeAdIdLanguage, nativeAd
                    )
                    return@loadNativeAd
                }
                val adView = layoutInflater.inflate(R.layout.ads_native_big, null) as com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
                app.messages.config.AdsConfigManager.populateNativeAdView(nativeAd, adView, "language")
                binding.nativeAdShimmerFrameLayout.visibility = View.GONE
                binding.nativeAdFrameLayout.visibility = View.VISIBLE
                binding.nativeAdFrameLayout.removeAllViews()
                binding.nativeAdFrameLayout.addView(adView)
                Log.d("ADS_LANGUAGE", "🎉 [LANGUAGE_UI_RENDERED] Big Native Ad inflated & DISPLAYED on screen!")
            },
            onFailed = {
                if (isDestroyed || isFinishing) return@loadNativeAd
                val langId = app.messages.config.AdsConfigManager.config.nativeAdIdLanguage
                // The 8s single-flight wait gave up but the request is still in flight: keep the shimmer
                // and wait on the SAME request again (no new request), so a slow ad still shows here
                // instead of landing in the cache unseen.
                if (app.messages.config.AdsConfigManager.isNativeLoading(langId) &&
                    languageAdExtraWaits < MAX_LANGUAGE_AD_EXTRA_WAITS
                ) {
                    languageAdExtraWaits++
                    Log.d("ADS_LANGUAGE", "⏳ [LANGUAGE_UI_WAIT] Wait timed out but request still in flight -> waiting again ($languageAdExtraWaits/$MAX_LANGUAGE_AD_EXTRA_WAITS)")
                    loadBigNativeAd(isExtraWait = true)
                    return@loadNativeAd
                }
                Log.e("ADS_LANGUAGE", "❌ [LANGUAGE_UI_FAILED] Big Native Ad failed to load / render")
                binding.nativeAdCardView.visibility = View.GONE
            }
        )
    }


    private fun setupUI() {
        binding.btnNext.setOnClickListener {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] User clicked Next button")
            AnalyticsHelper.logEvent("language_done_clicked", EventScreenName.LANGUAGE)
            if (isPaidOnboarding) app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelEvery(this, "user_lang_clicked")
            saveLanguage()
        }
        binding.btnDone.setOnClickListener {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] User clicked Done button")
            AnalyticsHelper.logEvent("language_done_clicked", EventScreenName.LANGUAGE)
            if (isPaidOnboarding) app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelEvery(this, "user_lang_clicked")
            saveLanguage()
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Back button pressed")
                if (isPaidOnboarding) {
                    app.messages.ui.paidonboarding.FlowAEscapeLogger.onEscapeAttempt(this@LanguageSelectionActivity, "back")
                    // Onboarding focus: Back doesn't move on or leave; only Done does
                    android.widget.Toast.makeText(
                        this@LanguageSelectionActivity,
                        app.messages.R.string.paid_onb_complete_steps,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                if (!adapter.hasSelection() && !isFromSettings) {
                    android.widget.Toast.makeText(
                        this@LanguageSelectionActivity,
                        app.messages.R.string.paid_onb_complete_steps,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return
                }
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
        // Flow A attribution funnel: ${prefix}_user_lang_success (language applied, moving forward).
        if (isPaidOnboarding) {
            app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelEvery(this, "user_lang_success")
        } else if (!isFromSettings) {
            // Flow B (Organic install onboarding): log org_user_lang_success once
            app.messages.analytics.AnalyticsHelper.logOnce(
                context = this,
                prefKey = "key_once_org_user_lang_success",
                eventName = app.messages.analytics.EventNames.ORG_LANG_SUCCESS,
                params = android.os.Bundle().apply { putString("language_code", selectedCode) }
            )
        }
        app.messages.utility.LocaleHelper.setLocale(this, selectedCode)
        app.messages.utility.LocaleHelper.syncLanguageToFlutter(this, selectedCode)
        app.messages.feature.LauncherSlot.feature.onLanguageChanged(selectedCode)

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
        // Flow A (paid) still has the whole onboarding ahead, so the launcher ads would sit unused (and
        // count as unshown requests) if the user leaves. PaidOnboardingActivity preloads them near the
        // last pages instead.
        val isFlowA = !isFromSettings && app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)
        if (isFirstLaunchOnboarding && !isFlowA) {
            Log.d("ADS_DASHBOARD", "⚡ [LANGUAGE_DONE] User clicked Done on onboarding -> Preloading Native Dashboard Ad...")
            Log.d("ADS_DRAWER", "⚡ [LANGUAGE_DONE] User clicked Done on onboarding -> Preloading App Drawer Native Ad...")
            app.messages.config.AdsConfigManager.preloadDashboardNativeAd(this)
            // Google Search native is intentionally NOT preloaded (low show rate): it loads on demand when search opens.
            app.messages.config.AdsConfigManager.preloadDrawerNativeAd(this)
        }

        // NEW UPDATE (2026-11-02): Flow A (paid onboarding) SKIPS the language
        // interstitial and goes straight to Flow A. Flow B (regular install) and
        // the from-Settings path still show the interstitial, as before.
        if (isFlowA) {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Flow A -> SKIP language interstitial, go straight to Flow A")
            navigateAfterLanguageDone()
        } else {
            Log.d("SetDefaultFlow", "[LanguageSelectionActivity] Flow B/settings -> showLanguageInterstitial, then navigate")
            app.messages.config.AdsConfigManager.showLanguageInterstitial(this) {
                Log.d("SetDefaultFlow", "[LanguageSelectionActivity] showLanguageInterstitial callback fired")
                navigateAfterLanguageDone()
            }
        }
    }

    /**
     * NEW UPDATE (2026-11-02): extracted from saveLanguage() so Flow A can navigate
     * WITHOUT the language interstitial, while Flow B / from-Settings navigate from
     * inside the interstitial callback. The branching itself is unchanged.
     */
    private fun navigateAfterLanguageDone() {
        // Splash skips the Flutter warm-up for this screen; start it now that the user is leaving.
        app.messages.feature.LauncherSlot.warmUp(applicationContext)
        if (isFromSettings) {
            // If user opened from Settings, just return back
            finish()
            return
        }
        if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
            // Remote Config sequence for the Language step (e.g. inter -> chrome -> app_open).
            if (sequenceRunner != null) return
            sequenceRunner = app.messages.ui.paidonboarding.FlowASequenceRunner.forStep(
                activity = this,
                stepTag = "language",
                launchChrome = { onFallback ->
                    app.messages.ui.paidonboarding.PaidChromeTabManager.launch(
                        activity = this,
                        launcher = chromeTabLauncher,
                        stepTag = "after_language",
                        onFallback = onFallback
                    )
                },
                onDone = {
                    sequenceRunner = null
                    startActivity(app.messages.ui.paidonboarding.PaidOnboardingActivity.intent(this))
                    finish()
                }
            )
            sequenceRunner?.let {
                android.util.Log.d("FLOW_CHECK", "[LANGUAGE_DONE] Paid install -> Remote Config step sequence")
                it.start()
                return
            }
            android.util.Log.d("FLOW_CHECK", "🚀 [LANGUAGE_DONE] Paid install -> PaidOnboardingActivity (Flow A)")
            startActivity(app.messages.ui.paidonboarding.PaidOnboardingActivity.intent(this))
            finish()
            return
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

    private fun setupRecyclerView() {
        adapter = LanguageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val prefs = app.messages.preferences.PreferenceHelper.getInstance(this)
        val countries = LanguageData.getCountries().toMutableList()

        if (isFromSettings) {
            // Opened from Settings: keep previously selected language selected and Done button visible
            val selectedCode = prefs.getSelectedLanguageCode().ifBlank { "en" }
            countries.forEach { it.isSelected = it.code == selectedCode }
            binding.btnDone.visibility = View.VISIBLE
            val selectedIndex = countries.indexOfFirst { it.isSelected }
            if (selectedIndex >= 0) {
                binding.recyclerView.scrollToPosition(selectedIndex)
            }
        } else {
            // First-launch onboarding: NO language selected by default, Done button HIDDEN
            countries.forEach { it.isSelected = false }
            binding.btnDone.visibility = View.GONE
        }

        adapter.onLanguageSelected = {
            binding.btnDone.visibility = View.VISIBLE
        }

        adapter.submitList(countries)
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
