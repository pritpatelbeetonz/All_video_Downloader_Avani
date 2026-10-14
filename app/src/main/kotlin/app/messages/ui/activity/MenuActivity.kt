package app.messages.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import app.messages.config.AdsConfigManager
import app.messages.R
import app.messages.databinding.ActivityMenuBinding
import app.messages.ui.base.BaseActivity

class MenuActivity : BaseActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(app.messages.R.color.bg_surface, !isNightModeActive())

        setupClicks()
        loadNativeAd()
    }

    private fun loadNativeAd() {
        val config = app.messages.config.AdsConfigManager.config

        if (app.messages.preferences.PreferenceHelper.getInstance(this).isPremium() || !config.isNativeOn) {
            binding.nativeAdContainer.visibility = android.view.View.GONE
            return
        }

        binding.nativeAdContainer.visibility = android.view.View.VISIBLE
        binding.nativeAdShimmer.root.visibility = android.view.View.VISIBLE
        binding.nativeAdView.visibility = android.view.View.GONE

        app.messages.config.AdsConfigManager.loadNativeAd(
            context = this,
            adUnitId = config.nativeAdIdMenuScreen,
            onLoaded = { nativeAd ->
                if (isDestroyed || isFinishing) return@loadNativeAd
                binding.nativeAdShimmer.root.visibility = android.view.View.GONE
                binding.nativeAdView.visibility = android.view.View.VISIBLE
                binding.nativeAdView.removeAllViews()

                val adView = layoutInflater.inflate(R.layout.ads_native, null, false) as com.google.android.gms.ads.nativead.NativeAdView
                app.messages.config.AdsConfigManager.populateNativeAdView(nativeAd, adView)

                binding.nativeAdView.addView(adView)
            },
            onFailed = {
                if (!isDestroyed && !isFinishing) {
                    binding.nativeAdContainer.visibility = android.view.View.GONE
                }
            }
        )
    }

    private fun setupClicks() {
        binding.incToolbar.tvTitle.text = getString(R.string.extracted_menu)
        binding.incToolbar.ivBack.setOnClickListener {
            finish()
        }

        binding.rlInbox.setOnClickListener {
            // Usually Inbox just finishes the menu to go back to HomeActivity
            finish()
        }

        binding.rlArchived.setOnClickListener {
            startActivity(Intent(this, ArchiveActivity::class.java))
        }

        binding.rlScheduled.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }

        binding.rlBackup.setOnClickListener {
            startActivity(Intent(this, BackupRestoreActivity::class.java))
        }

        binding.rlBlocking.setOnClickListener {
            startActivity(Intent(this, BlockListActivity::class.java))
        }

        binding.rlSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.rlLanguage.setOnClickListener {
            val intent = Intent(this, LanguageSelectionActivity::class.java).apply {
                putExtra("from_settings", true)
            }
            startActivity(intent)
        }

        binding.rlPrivacy.setOnClickListener {
            val url = AdsConfigManager.config.privacyPolicyUrl.ifBlank {
                app.messages.constant.Constants.PRIVACY_POLICY
            }
//            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()

                customTabsIntent.launchUrl(this, Uri.parse(url))
//                startActivity(intent)
            } catch (e: Exception) {
                // Ignore if browser not found
            }
        }
    }
}
