package app.messages.ui.activity

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.messages.databinding.ActivitySetDefaultLauncherBinding
import app.messages.ui.base.BaseActivity
import app.messages.ui.launcher.LauncherActivity

class SetDefaultLauncherActivity : BaseActivity() {

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
        val isDefault = isDefaultLauncherApp()
        android.util.Log.d("SetDefaultFlow", "[SetDefaultLauncherActivity] launcherRoleLauncher result received. isDefaultLauncherApp=$isDefault")
        if (isDefault) {
            goToNext()
        } else {
            android.widget.Toast.makeText(this, getString(app.messages.R.string.please_set_as_default_sms_app_to_proceed).replace("SMS", "Home"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

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
        launcherRoleLauncher.launch(intent)
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

        val prefs = app.messages.preferences.PreferenceHelper.getInstance(this)
        val intent = if (!prefs.isLanguageSelected()) {
            Intent(this, LanguageSelectionActivity::class.java)
        } else if (app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(this)) {
            app.messages.ui.paidonboarding.PaidOnboardingActivity.intent(this)
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

