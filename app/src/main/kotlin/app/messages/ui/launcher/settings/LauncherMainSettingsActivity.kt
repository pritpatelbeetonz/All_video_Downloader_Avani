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
                    AdsConfigManager.sendRevenue(config.nativeAdIdLauncherScreen, "native_launcher_setting", adValue)
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

    override fun onResume() {
        super.onResume()
        setSelectedLanguageText()
        setupThemeModeUI()
        binding.tvCacheSize.text = getCacheSizeFormatted()
    }

    private fun setupUI() {
        binding.switchNavBar.isChecked = prefs.getNavigationBarVisible()
        setSelectedLanguageText()
        setupThemeModeUI()
        binding.tvCacheSize.text = getCacheSizeFormatted()
        setupAppVersion()
    }

    private fun setSelectedLanguageText() {
        val selectedCode = prefs.getSelectedLanguageCode().ifBlank { "en" }
        val langModel = app.messages.data.LanguageData.getLanguageByCode(selectedCode)
        binding.tvCurrentLanguage.text = langModel?.nativeName ?: "English"
    }

    private fun setupThemeModeUI() {
        val themeMode = prefs.getThemeMode()
        binding.tvCurrentTheme.text = app.messages.enumeration.getThemeModeText(themeMode, this)
    }

    private fun setupAppVersion() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvAppVersion.text = "Version ${pInfo.versionName ?: "1.0"}"
        } catch (_: Exception) {
            binding.tvAppVersion.text = "Version 1.0"
        }
    }

    private fun getCacheSizeFormatted(): String {
        return try {
            val cacheSize = getFolderSize(cacheDir) + (externalCacheDir?.let { getFolderSize(it) } ?: 0L)
            android.text.format.Formatter.formatFileSize(this, cacheSize)
        } catch (_: Exception) {
            "0 B"
        }
    }

    private fun getFolderSize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    private fun clearAppCache() {
        try {
            cacheDir.deleteRecursively()
            externalCacheDir?.deleteRecursively()
            binding.tvCacheSize.text = getCacheSizeFormatted()
            android.widget.Toast.makeText(this, getString(R.string.cache_cleared), android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showThemeModeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_mode, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroup)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)
        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val currentTheme = prefs.getThemeMode()
        when (currentTheme) {
            app.messages.enumeration.ThemeMode.SYSTEM_DEFAULT.value -> radioGroup.check(R.id.rbSystemDefault)
            app.messages.enumeration.ThemeMode.LIGHT.value -> radioGroup.check(R.id.rbLightTheme)
            app.messages.enumeration.ThemeMode.DARK.value -> radioGroup.check(R.id.rbDarkTheme)
            else -> radioGroup.check(R.id.rbSystemDefault)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                R.id.rbSystemDefault -> app.messages.enumeration.ThemeMode.SYSTEM_DEFAULT.value
                R.id.rbLightTheme -> app.messages.enumeration.ThemeMode.LIGHT.value
                R.id.rbDarkTheme -> app.messages.enumeration.ThemeMode.DARK.value
                else -> app.messages.enumeration.ThemeMode.SYSTEM_DEFAULT.value
            }

            prefs.setThemMode(selectedTheme)
            setupThemeModeUI()
            app.messages.utility.ThemeUtils.applyTheme(this)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun rateUs() {
        val uri = android.net.Uri.parse("market://details?id=$packageName")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            } catch (_: Exception) {}
        }
    }

    private fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT, "${getString(R.string.msg_share_app)}\n\nhttps://play.google.com/store/apps/details?id=$packageName")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.choose_one)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

        // General & About actions
        binding.cardLanguage.setOnClickListener {
            startActivity(Intent(this, app.messages.ui.activity.LanguageSelectionActivity::class.java))
        }

        binding.cardTheme.setOnClickListener {
            showThemeModeDialog()
        }

        binding.cardClearCache.setOnClickListener {
            clearAppCache()
        }

        binding.cardShareApp.setOnClickListener {
            shareApp()
        }

        binding.cardRateUs.setOnClickListener {
            rateUs()
        }

        binding.cardPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }

        binding.cardAppVersion.setOnClickListener {
            binding.tvCacheSize.text = getCacheSizeFormatted()
        }
    }
}
