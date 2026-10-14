package app.messages.ui.launcher.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.messages.R
import app.messages.config.AdsConfigManager
import app.messages.databinding.ActivityLauncherMainSettingsBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity
import kotlinx.coroutines.launch

class LauncherMainSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityLauncherMainSettingsBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherMainSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())

        prefs = PreferenceHelper.getInstance(this)

        setupUI()
        setupClicks()

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                AdsConfigManager.configFlow.collect { config ->
                    if (!config.isNativeOn || prefs.isPremium()) {
                        binding.nativeAdCardView.visibility = View.GONE
                    } else if (binding.nativeAdFrameLayout.childCount == 0 || binding.nativeAdCardView.visibility == View.GONE) {
                        loadBigNativeAd()
                    }
                }
            }
        }
    }

    private fun loadBigNativeAd() {
        val config = AdsConfigManager.config
        if (prefs.isPremium() || !config.isNativeOn) {
            binding.nativeAdCardView.visibility = View.GONE
            return
        }

        binding.nativeAdCardView.visibility = View.VISIBLE
        binding.nativeAdShimmerFrameLayout.visibility = View.VISIBLE
        binding.nativeAdFrameLayout.visibility = View.GONE

        AdsConfigManager.loadNativeAd(
            context = this,
            adUnitId = config.nativeAdIdLauncherScreen,
            onLoaded = { nativeAd ->
                if (isDestroyed || isFinishing) return@loadNativeAd
                nativeAd.setOnPaidEventListener { adValue ->
                    AdsConfigManager.logAdRevenue("Native", "Launcher_Setting", adValue)
                }
                val adView = layoutInflater.inflate(R.layout.ads_native_big, null, false) as com.google.android.gms.ads.nativead.NativeAdView
                AdsConfigManager.populateNativeAdView(nativeAd, adView)

                binding.nativeAdShimmerFrameLayout.visibility = View.GONE
                binding.nativeAdFrameLayout.visibility = View.VISIBLE
                binding.nativeAdFrameLayout.removeAllViews()
                binding.nativeAdFrameLayout.addView(adView)
            },
            onFailed = {
                if (!isDestroyed && !isFinishing) {
                    binding.nativeAdCardView.visibility = View.GONE
                }
            }
        )
    }

    private fun setupUI() {
        binding.switchNavBar.isChecked = prefs.getNavigationBarVisible()
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardHomeScreen.setOnClickListener {
            startActivity(Intent(this, HomeScreenSettingsActivity::class.java))
        }

        binding.cardAppDrawer.setOnClickListener {
            startActivity(Intent(this, AppDrawerSettingsActivity::class.java))
        }

        binding.cardSearch.setOnClickListener {
            startActivity(Intent(this, SearchSettingsActivity::class.java))
        }

        binding.cardGestures.setOnClickListener {
            startActivity(Intent(this, GesturesSettingsActivity::class.java))
        }

        binding.cardPackageMonitor.setOnClickListener {
            startActivity(Intent(this, PackageMonitorSettingsActivity::class.java))
        }

        // Tapping anywhere on the row flips the switch, so the whole card is a
        // target instead of just the 40dp control on the right.
        binding.cardNavBar.setOnClickListener {
            binding.switchNavBar.toggle()
        }

        binding.switchNavBar.setOnCheckedChangeListener { _, isChecked ->
            prefs.setNavigationBarVisible(isChecked)
        }
    }
}
