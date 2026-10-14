package app.messages.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import app.messages.R
import app.messages.data.model.OnboardingModel
import app.messages.ui.base.BaseActivity
import app.messages.databinding.ActivityOnboardingBinding
import app.messages.ui.adapters.OnboardingPagerAdapter
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.constraintlayout.widget.ConstraintLayout

class OnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingPages: List<OnboardingModel>
    private lateinit var indicators: Array<View>

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

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.llNext) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val baseMargin = dpToPx(28)
            val lp = v.layoutParams as? ConstraintLayout.LayoutParams
            if (lp != null) {
                lp.bottomMargin = baseMargin + navInsets.bottom
                v.layoutParams = lp
            }
            insets
        }

        setupIndicators(2)
        setCurrentIndicator(0)

        // Preload Intro App Open Ad immediately on step 1
        app.messages.config.AdsConfigManager.preloadIntroAppOpen(this)

        onboardingPages = listOf(
            OnboardingModel(
                imageRes = R.drawable.img_intro_1,
                title = "Video Downloader",
                message = "Download videos instantly using any link."
            ),
            OnboardingModel(
                imageRes = 0,
                title = "High Speed Downloader",
                message = "Download videos from links easily.\nWatch offline anytime.",
                videoRes = R.raw.intro2_video
            )
        )

        binding.onboardingViewPager.adapter = OnboardingPagerAdapter(this, onboardingPages)

        binding.btnNext.setOnClickListener {
            if (binding.onboardingViewPager.currentItem < onboardingPages.lastIndex) {
                AnalyticsHelper.logEvent(EventNames.ONBOARDING_CLICK_NEXT, EventScreenName.ONBOARDING)
                binding.onboardingViewPager.currentItem += 1
            } else {
                AnalyticsHelper.logEvent(EventNames.ONBOARDING_CLICK_GET_STARTED, EventScreenName.ONBOARDING)
                navigateToNextScreenWithAd()
            }
        }

        binding.onboardingViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentIndicator(position)
            }
        })
    }

    private var loadingDialog: androidx.appcompat.app.AlertDialog? = null
    private val isNavigating = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun showLoadingDialog(show: Boolean) {
        if (show) {
            if (loadingDialog == null && !isFinishing && !isDestroyed) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
                dialogView.findViewById<android.widget.TextView>(R.id.tvProgressMessage)?.text = "Loading..."
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

    private fun proceedToNextScreen() {
        if (isNavigating.compareAndSet(false, true)) {
            showLoadingDialog(false)
            val prefs = app.messages.preferences.PreferenceHelper.getInstance(this)
            prefs.setOnboardingDone(true)

            val intent = if (!isDefaultLauncherApp()) {
                Intent(this, SetDefaultLauncherActivity::class.java)
            } else if (!prefs.isLanguageSelected()) {
                Intent(this, LanguageSelectionActivity::class.java)
            } else {
                Intent(this, app.messages.ui.launcher.LauncherActivity::class.java).apply {
                    putExtra("open_chats_first", true)
                }
            }
            startActivity(intent)
            finish()
        }
    }

    private fun navigateToNextScreenWithAd() {
        app.messages.config.AdsConfigManager.showIntroAppOpen(
            activity = this,
            timeoutMs = 5000L,
            onLoadingStateChanged = { isLoading ->
                runOnUiThread {
                    showLoadingDialog(isLoading)
                }
            },
            onComplete = {
                proceedToNextScreen()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        showLoadingDialog(false)
    }

    private fun setupIndicators(count: Int) {
        indicators = Array(count) { View(this) }
        binding.indicatorLayout.removeAllViews()

        for (i in indicators.indices) {
            val view = View(this)
            val params = LinearLayout.LayoutParams(
                dpToPx(8),
                dpToPx(8)
            )
            params.marginEnd = dpToPx(6)
            view.layoutParams = params
            view.setBackgroundResource(R.drawable.dot_intro_inactive)
            indicators[i] = view
            binding.indicatorLayout.addView(view)
        }
    }

    private fun setCurrentIndicator(position: Int) {
        for (i in indicators.indices) {
            if (i == position) {
                indicators[i].setBackgroundResource(R.drawable.dot_intro_active)
            } else {
                indicators[i].setBackgroundResource(R.drawable.dot_intro_inactive)
            }
        }
    }

    private fun isDefaultLauncherApp(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        }
    }
}