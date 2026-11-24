package app.messages.ui.activity

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.messages.databinding.ActivitySetDefaultLauncherBinding
import app.messages.ui.base.PaidFunnelActivity
import app.messages.ui.launcher.LauncherActivity

class SetDefaultLauncherActivity : PaidFunnelActivity() {

    private lateinit var binding: ActivitySetDefaultLauncherBinding

    override fun shouldApplySystemBarInsets(): Boolean = false

    private fun isDefaultLauncherApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            return resolveInfo?.activityInfo?.packageName == packageName
        }
    }

    /** Remote Config `set_default_sequence` runner (inter / chrome / app_open in the configured order). */
    private var sequenceRunner: app.messages.ui.paidonboarding.FlowASequenceRunner? = null
    private val chromeTabLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        sequenceRunner?.takeIf { it.awaitingChrome }?.let {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Chrome tab closed -> continue set-default sequence")
            it.onChromeClosed()
        }
    }

    /**
     * If Remote Config defines `set_default_sequence`, runs it (replacing the plain interstitial) and
     * returns true; [onDone] runs after the last token. Returns false when none is configured, so the
     * caller keeps the legacy interstitial-only behaviour.
     */
    private fun runSetDefaultSequence(path: String, allowOnDemand: Boolean, onDone: () -> Unit): Boolean {
        val raw = app.messages.config.AdsConfigManager.getSetDefaultSequence() ?: return false
        if (sequenceRunner != null) return true   // already running; the first run's onDone moves on
        val runner = app.messages.ui.paidonboarding.FlowASequenceRunner.forTokens(
            activity = this,
            stepTag = "set_as_default",
            rawTokens = raw,
            launchChrome = { onFallback ->
                app.messages.ui.paidonboarding.PaidChromeTabManager.launch(
                    activity = this,
                    launcher = chromeTabLauncher,
                    stepTag = "set_as_default",
                    onFallback = onFallback
                )
            },
            interHandler = { next ->
                learning.screenguard.ScreenGuardDemo.setAdShowing(true)
                learning.screenguard.ScreenGuardDemo.noteOwnNavigation(3000L)
                app.messages.config.AdsConfigManager.showSetAsDefaultInterstitial(
                    this, allowOnDemand = allowOnDemand, path = path
                ) {
                    learning.screenguard.ScreenGuardDemo.noteOwnNavigation(1500L)
                    learning.screenguard.ScreenGuardDemo.setAdShowing(false)
                    next()
                }
            },
            appOpenHandler = { next ->
                app.messages.ui.paidonboarding.SetDefaultAppOpen.showIfReady(this, allowOnDemand = allowOnDemand) { next() }
            },
            onDone = {
                sequenceRunner = null
                onDone()
            }
        )
        sequenceRunner = runner
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] set_default_sequence=$raw ($path)")
        runner.start()
        return true
    }

    private val launcherRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The system picker returned → let the guard enforce normally again.
        pickerOpen = false
        learning.screenguard.ScreenGuardDemo.setSystemPickerOpen(false)
        val isDefault = isDefaultLauncherApp()
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] launcherRoleLauncher result received. isDefaultLauncherApp=$isDefault")
        if (isDefault) {
            if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
                // Flow A attribution funnel: ${prefix}_user_set_as_def_success (role granted).
                app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelOnce(this, "user_set_as_def_success")
            } else {
                // Flow B (Organic install): log org_user_set_as_def_success once
                app.messages.analytics.AnalyticsHelper.logOnce(
                    context = this,
                    prefKey = "key_once_org_user_set_as_def_success",
                    eventName = app.messages.analytics.EventNames.ORG_SET_AS_DEF_SUCCESS
                )
            }
            goToNext()
        } else {
            android.widget.Toast.makeText(this, getString(app.messages.R.string.please_set_as_default_sms_app_to_proceed).replace("SMS", "Home"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** True while WE launched the system ROLE_HOME / home-settings picker (so leaving to it isn't "the user escaping"). */
    private var pickerOpen = false

    private val isNavigatingToNext = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivitySetDefaultLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyBottomInsets()

        // Preload everything set_default_sequence will show (inter / own App Open / chrome page), once per
        // fresh launch (not on rotation). Duplicate-safe, see FlowASequenceRunner.preloadSetDefault.
        if (savedInstanceState == null) {
            app.messages.config.AdsConfigManager.getSetDefaultSequence()?.let {
                app.messages.ui.paidonboarding.FlowASequenceRunner.preloadSetDefault(this, it)
            }
        }

        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] onCreate() entered. isDefaultLauncherApp=${isDefaultLauncherApp()}")

        if (isDefaultLauncherApp()) {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] App is already default launcher -> calling goToNext()")
            goToNext()
            return
        }

        // FUNNEL-GUARD (learning): arm the trap on THIS screen for paid (non-organic) installs still in
        // onboarding. Registering here (not just after granting) lets the guard watch the set-default
        // screen, so Home/Recents leaves are pulled back and routed to Language — same as the reference
        // app's FunnelScreenActivity. Organic installs never reach here (Splash routes them away), and
        // shouldShow() == isAdInstall && !onboardingDone gates it regardless.
        if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Paid install in onboarding -> arming funnel guard on set-default screen")
            app.messages.onboarding.watch.OnboardingAppWatcher.register(this)
            learning.screenguard.ScreenGuardDemo.register(this)
            // No interstitial preload on screen open any more: every visit fired a request, and users who
            // left/killed the app here (or came back to this screen after already seeing the ad) never
            // showed it -> ~9k matched-but-unshown per day (43% show rate). The request now starts only
            // when it can be used: on the "Set as default" tap, or on an escape (forceAdvanceToLanguage
            // preloads + waits, and setAdShowing(true) already keeps the guard off during that wait).
            // Flow A attribution funnel: ${prefix}_user_set_as_def_visited.
            app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelOnce(this, "user_set_as_def_visited")
        } else {
            // Flow B (Organic install): log org_user_set_as_def_visited once
            app.messages.analytics.AnalyticsHelper.logOnce(
                context = this,
                prefKey = "key_once_org_user_set_as_def_visited",
                eventName = app.messages.analytics.EventNames.ORG_SET_AS_DEF_VISITED
            )
        }

        // Back on the set-default screen (trapped): don't exit — force-advance into the funnel with the
        // interstitial, exactly like the reference (onIntroBack → onSystemButton reorders → onNewIntent →
        // validateDefaultRoleAndAdvance(force) → showInterstitialAfterDefault → Language).
        onBackPressedDispatcher.addCallback(this) {
            if (isFunnelTrapActive()) {
                android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] ⛔ Back (trap armed) -> force-advance to Language")
                app.messages.ui.paidonboarding.FlowAEscapeLogger.onEscapeAttempt(this@SetDefaultLauncherActivity, "back")
                forceAdvanceToLanguage("back")
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        val titleHtml = "<font color='#1E1E1E'><b>Set </b></font><font color='#FF5733'><b>All Video Downloader</b></font><br/><font color='#1E1E1E'><b>as Default Home App</b></font>"
        binding.tvTitle.text = androidx.core.text.HtmlCompat.fromHtml(titleHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)

        binding.btnSetDefault.setOnClickListener {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] User clicked 'Set as Default' button -> preloading SetAsDefault Interstitial in background")
            // Flow A attribution funnel: ${prefix}_user_set_as_def_clicked.
            app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelOnce(this, "user_set_as_def_clicked")
            app.messages.config.AdsConfigManager.preloadSetAsDefaultInterstitial(this)
            // In parallel with the system role dialog, start whatever set_default_sequence will show
            // (inter / own App Open / chrome page). Duplicate-safe: skips what onCreate already warmed.
            app.messages.config.AdsConfigManager.getSetDefaultSequence()?.let {
                app.messages.ui.paidonboarding.FlowASequenceRunner.preloadSetDefault(this, it)
            }
            requestDefaultLauncherRole()
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // Light background -> Crisp dark status bar & navigation bar icons
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        // Clear BaseActivity's white background and top padding on android.R.id.content
        val root: FrameLayout = findViewById(android.R.id.content)
        root.setBackgroundColor(Color.TRANSPARENT)
        ViewCompat.setOnApplyWindowInsetsListener(root, null)
        root.setPadding(0, 0, 0, 0)
    }

    private fun applyBottomInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val baseMargin = (16 * resources.displayMetrics.density).toInt()
            (binding.llBottomContainer.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = baseMargin + navBars.bottom
                binding.llBottomContainer.layoutParams = lp
            }
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        val isDefault = isDefaultLauncherApp()
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] onResume() -> isDefaultLauncherApp=$isDefault")
        if (isDefault) {
            goToNext()
        }
    }

    private fun requestDefaultLauncherRole() {
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] requestDefaultLauncherRole() called")
        if (!isDefaultLauncherApp()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Launching RoleManager intent for ROLE_HOME")
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        notePickerOpening()
                        launcherRoleLauncher.launch(intent)
                    } else {
                        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Role is already held -> calling goToNext()")
                        goToNext()
                    }
                } else {
                    android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] RoleManager unavailable -> launching legacy request")
                    launchLegacyDefaultLauncherRequest()
                }
            } else {
                android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Pre-Q device -> launching legacy request")
                launchLegacyDefaultLauncherRequest()
            }
        } else {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Already default launcher -> calling goToNext()")
            goToNext()
        }
    }

    private fun launchLegacyDefaultLauncherRequest() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        notePickerOpening()
        launcherRoleLauncher.launch(intent)
    }

    /** Mark that WE are opening the system picker, so the guard stands down (doesn't yank over it). */
    private fun notePickerOpening() {
        pickerOpen = true
        learning.screenguard.ScreenGuardDemo.setSystemPickerOpen(true)
    }

    /**
     * Set-default adds extra gates on top of the shared funnel trap (PaidFunnelActivity):
     * never while OUR system role picker is up, while we're navigating onward, or once already
     * default. Home → Language and Recents → pull-back are handled by the base + ScreenGuardDemo.
     */
    override fun isFunnelTrapActive(): Boolean =
        !pickerOpen &&
            !isNavigatingToNext.get() &&
            !isDefaultLauncherApp() &&
            super.isFunnelTrapActive()

    /** Fired once, so repeated guard re-entries / escape attempts don't stack navigations. */
    private val hasAdvancedToLanguage = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * IDENTICAL TO REFERENCE (SetDefaultActivity.onNewIntent → validateDefaultRoleAndAdvance(force) →
     * showInterstitialAfterDefault): when the guard's REORDER_TO_FRONT pull-back re-delivers the intent
     * to this screen (the user pressed Home/Recents to escape), force-advance into the funnel — show the
     * Set-As-Default interstitial, then open Language (Flow A from page 1) and finish this screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] onNewIntent (guard re-entry) -> maybe force-advance")
        if (isFunnelTrapActive()) {
            forceAdvanceToLanguage("onNewIntent")
        }
    }

    /** Interstitial (if any) then Language + Flow-A-from-page-1, finishing this screen. Runs once. */
    private fun forceAdvanceToLanguage(reason: String) {
        if (!isFunnelTrapActive()) return
        if (!hasAdvancedToLanguage.compareAndSet(false, true)) return
        // Flow A attribution funnel: advanced WITHOUT granting default -> ${prefix}_user_not_set_as_def.
        if (!isDefaultLauncherApp()) {
            app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelOnce(this, "user_not_set_as_def")
        }
        isNavigatingToNext.set(true)   // disarm the trap while we navigate
        // Stand the guard down while the interstitial + navigation run, so it doesn't pull back over
        // the ad (reference showInterstitialAfterDefault → FunnelGuard.noteInAppAdvance(3000)).
        // The interstitial can take a second or two to load and the user may watch it for a while, so a
        // fixed timer is NOT enough (that's the bug in the log: the 3s window expired ~0.1s before the
        // guard fired, hid the ad and toasted). Mark an ad is showing — unbounded, like the App Open
        // path; the guard's leaveCheck + startEnforcing both bail on adShowing. Cleared when it dismisses.
        learning.screenguard.ScreenGuardDemo.setAdShowing(true)
        learning.screenguard.ScreenGuardDemo.noteOwnNavigation(3000L)
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] 🚧 escape ($reason) -> SetAsDefault interstitial -> Language (reference force-advance)")
        app.messages.config.AdsConfigManager.preloadSetAsDefaultInterstitial(this)
        fun moveOn() {
            warmFlutterAfterSetDefault()
            startActivity(
                app.messages.ui.paidonboarding.PaidOnboardingActivity.restartFromLanguage(this).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
            )
            overridePendingTransition(0, 0)
            finish()
        }
        // Remote Config sequence (inter / chrome / app_open) if configured; it manages the ad flag per step.
        val ranSequence = runSetDefaultSequence("escape", allowOnDemand = true) {
            learning.screenguard.ScreenGuardDemo.noteOwnNavigation(1500L)
            learning.screenguard.ScreenGuardDemo.setAdShowing(false)
            moveOn()
        }
        if (ranSequence) return
        app.messages.config.AdsConfigManager.showSetAsDefaultInterstitial(this, path = "escape") {
            // Ad dismissed (or none to show): cover the SetDefault → Language handoff with a fresh own-nav
            // window, THEN clear the ad flag (order matters), then move.
            learning.screenguard.ScreenGuardDemo.noteOwnNavigation(1500L)
            learning.screenguard.ScreenGuardDemo.setAdShowing(false)
            moveOn()
        }
    }

    private fun goToNext() {
        if (!isNavigatingToNext.compareAndSet(false, true)) {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] goToNext() ignored (already navigating)")
            return
        }

        if (isDefaultLauncherApp()) {
            if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
                app.messages.ui.paidonboarding.FlowAEscapeLogger.logFunnelOnce(this, "user_set_as_def_success")
            } else {
                app.messages.analytics.AnalyticsHelper.logOnce(
                    context = this,
                    prefKey = "key_once_org_user_set_as_def_success",
                    eventName = app.messages.analytics.EventNames.ORG_SET_AS_DEF_SUCCESS
                )
            }
        }

        // Log one-time Firebase Analytics event for setting default launcher
        app.messages.analytics.AnalyticsHelper.logOnce(
            context = this,
            prefKey = app.messages.preferences.PreferenceHelper.KEY_EVENT_SET_LAUNCHER_LOGGED,
            eventName = app.messages.analytics.EventNames.LAUNCHER_PERMISSION_GRANTED
        )

        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] goToNext() -> Preloading language native ad and transitioning to LauncherActivity")
        app.messages.config.AdsConfigManager.preloadLanguageNativeAd(this)

        // Default launcher granted: start the onboarding watcher for paid installs still in Flow A.
        if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
            app.messages.onboarding.watch.OnboardingAppWatcher.register(this)
        }

        val prefs = app.messages.preferences.PreferenceHelper.getInstance(this)
        val intent = if (!prefs.isLanguageSelected()) {
            Intent(this, LanguageSelectionActivity::class.java)
        } else if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
            app.messages.ui.paidonboarding.PaidOnboardingActivity.restartFromLanguage(this)
        } else {
            Intent(this, LauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_chats_first", true)
            }
        }
        // The role dialog returned here (Samsung / Xiaomi / older Android) instead of going through
        // LauncherActivity, which is the only other place this preloaded interstitial is shown. Show it
        // now so the request is not wasted. The guard must stand down: the ad takes window focus, which
        // the funnel guard would otherwise read as an escape (same handling as forceAdvanceToLanguage).
        // Loaded -> show it. Still loading -> loader, wait up to 8s (SET_DEFAULT_IN_FLIGHT_WAIT_MS) on that
        // same request; if it fails, move on. Neither -> move on straight away (no on-demand request).
        // Flow B: after the interstitial (or none), wait with loader for the Language native requested
        // above, so Language opens with its ad in hand. Flow A keeps its own hand-off unchanged.
        fun proceed() {
            if (!isFlowA()) {
                openAfterLanguageNative(intent)
                return
            }
            warmFlutterAfterSetDefault()
            startActivity(intent)
            finish()
        }
        // Remote Config sequence (inter / chrome / app_open) if configured; otherwise the legacy interstitial below.
        // Same behaviour as the escape path: in-flight -> loader (30s), nothing -> request on the spot.
        if (runSetDefaultSequence("goToNext", allowOnDemand = true) { proceed() }) return
        val ready = app.messages.config.AdsConfigManager.isSetAsDefaultInterstitialReady()
        val inFlight = app.messages.config.AdsConfigManager.isSetAsDefaultInterstitialLoading()
        if (!ready && !inFlight) {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] goToNext() -> interstitial not ready or loading, going straight to next screen")
            proceed()
            return
        }
        learning.screenguard.ScreenGuardDemo.setAdShowing(true)
        learning.screenguard.ScreenGuardDemo.noteOwnNavigation(3000L)
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] goToNext() -> SetAsDefault interstitial (ready=$ready, inFlight=$inFlight) -> next screen")
        app.messages.config.AdsConfigManager.showSetAsDefaultInterstitial(
            this,
            allowOnDemand = false,   // in flight -> wait (8s); failed / nothing -> forward, never a new request
            path = "goToNext"
        ) {
            learning.screenguard.ScreenGuardDemo.noteOwnNavigation(1500L)
            learning.screenguard.ScreenGuardDemo.setAdShowing(false)
            proceed()
        }
    }

    private fun isFlowA(): Boolean = app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)

    private var loadingDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showLoadingDialog(show: Boolean) {
        if (show) {
            if (loadingDialog == null && !isFinishing && !isDestroyed) {
                val dialogView = layoutInflater.inflate(app.messages.R.layout.dialog_progress, null)
                dialogView.findViewById<android.widget.TextView>(app.messages.R.id.tvProgressMessage)?.text = "Loading..."
                loadingDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create().apply {
                        window?.setBackgroundDrawableResource(android.R.color.transparent)
                        show()
                    }
            }
        } else {
            loadingDialog?.dismiss()
            loadingDialog = null
        }
    }

    /**
     * Flow B hand-off. Only when the next screen is Language and its native request (started in goToNext)
     * is still in flight: loader, wait on that SAME request (no new one, 8s single-flight cap), put the
     * ad back in the RAM cache, then open Language, which takes it from the cache with no shimmer.
     * Cached / not loading / other next screen -> open it straight away.
     */
    private fun openAfterLanguageNative(intent: Intent) {
        val ads = app.messages.config.AdsConfigManager
        val langId = ads.config.nativeAdIdLanguage
        val waitForAd = intent.component?.className == LanguageSelectionActivity::class.java.name &&
            ads.isNativeLoading(langId)
        val opened = java.util.concurrent.atomic.AtomicBoolean(false)
        fun open() {
            if (!opened.compareAndSet(false, true)) return
            showLoadingDialog(false)
            if (isFinishing || isDestroyed) return
            // Flutter boots on the main thread and froze Language / starved its ad, so only warm it here when
            // going straight to the launcher. Language warms it itself when the user taps Done.
            if (intent.component?.className == LauncherActivity::class.java.name) warmFlutterAfterSetDefault()
            startActivity(intent)
            finish()
        }
        if (!waitForAd) {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Flow B -> next screen now (language native not in flight)")
            open()
            return
        }
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] Flow B -> language native in flight, loader until it lands")
        showLoadingDialog(true)
        ads.loadNativeAd(
            context = this,
            adUnitId = langId,
            onLoaded = { nativeAd ->
                android.util.Log.d("ADS_LANGUAGE", "[SET_DEFAULT_WAIT] Language native landed -> cached for Language screen")
                ads.cacheNativeAd(langId, nativeAd)
                runOnUiThread { open() }
            },
            onFailed = {
                android.util.Log.d("ADS_LANGUAGE", "[SET_DEFAULT_WAIT] Language native failed / timed out -> open Language anyway")
                runOnUiThread { open() }
            }
        )
    }

    override fun onDestroy() {
        showLoadingDialog(false)
        super.onDestroy()
    }

    /** Splash skips the Flutter warm-up for this screen; start it now that the set-default ad flow is over. */
    private fun warmFlutterAfterSetDefault() {
        app.messages.feature.LauncherSlot.warmUp(applicationContext)
    }
}

