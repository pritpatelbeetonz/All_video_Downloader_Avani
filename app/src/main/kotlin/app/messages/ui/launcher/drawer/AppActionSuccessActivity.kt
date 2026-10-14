package app.messages.ui.launcher.drawer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import app.messages.config.AdsConfigManager
import app.messages.R
import app.messages.databinding.ActivityAppActionSuccessBinding

class AppActionSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppActionSuccessBinding
    private var packageName: String? = null
    private var actionType: String? = null // "install" or "uninstall"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system navigation bar
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        binding = ActivityAppActionSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val label = intent.getStringExtra(EXTRA_APP_LABEL) ?: ""
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        actionType = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: "install"

        if (actionType == "install") {
            binding.tvActionTitle.text = "App Install\nSuccessfully"
            binding.btnAction.text = "Open"
            binding.btnAction.setOnClickListener {
                packageName?.let { pkg ->
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                finish()
            }
        } else {
            binding.tvActionTitle.text = "App Uninstall\nSuccessfully"
            binding.btnAction.text = "Done"
            binding.btnAction.setOnClickListener {
                finish()
            }
            binding.llSecureNotice.visibility = View.GONE
            binding.ivSecureBadge.visibility = View.GONE
        }

        binding.ivSettings.setOnClickListener {
            // Can open settings if needed
        }

        binding.tvAppName.text = label

        if (actionType == "uninstall") {
            val cachedPkg = app.messages.ui.launcher.drawer.AppActionCache.lastUninstalledPackage
            val cachedIcon = app.messages.ui.launcher.drawer.AppActionCache.lastUninstalledIcon
            val cachedLabel = app.messages.ui.launcher.drawer.AppActionCache.lastUninstalledLabel

            binding.tvAppName.text = cachedLabel ?: label
            if (cachedIcon != null) {
                binding.ivAppIcon.setImageDrawable(cachedIcon)
            } else {
                val globalApp = app.messages.ui.launcher.drawer.GlobalAppCache.getAppInfo(packageName.orEmpty())
                val peeked = globalApp?.let { app.messages.ui.launcher.adapter.AppIconCache.peek(it) }
                if (peeked != null) {
                    binding.ivAppIcon.setImageDrawable(peeked)
                } else {
                    binding.ivAppIcon.setImageResource(R.mipmap.ic_launcher)
                }
            }
            binding.tvAppSize.visibility = View.GONE
        } else {
            try {
                val pm = packageManager
                val info = pm.getApplicationInfo(packageName!!, 0)
                val icon = info.loadIcon(pm)
                binding.ivAppIcon.setImageDrawable(icon)

                val file = java.io.File(info.sourceDir)
                if (file.exists()) {
                    val sizeInMb = file.length() / (1024.0 * 1024.0)
                    binding.tvAppSize.text = String.format("Size: %.1f MB", sizeInMb)
                } else {
                    binding.tvAppSize.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.ivAppIcon.setImageResource(R.mipmap.ic_launcher)
                binding.tvAppSize.visibility = View.GONE
            }
        }

        loadNativeAd()
    }

    private fun loadNativeAd() {
        val config = AdsConfigManager.config
        val frameLayout = binding.nativeAdFrameLayout
        val shimmerFrameLayout = binding.nativeAdShimmerFrameLayout
        val container = binding.adContainer

        if (!config.isBannerInstallUninstallOn) { // keeping the same toggle flag as requested
            container.visibility = View.GONE
            return
        }

        shimmerFrameLayout.visibility = View.VISIBLE
        frameLayout.visibility = View.GONE
        container.visibility = View.VISIBLE

        AdsConfigManager.loadNativeAd(
            context = this,
            adUnitId = config.nativeAdIdDashboard, // using native ad id since banner id causes failure
            onLoaded = { nativeAd ->
                if (isDestroyed || isFinishing) return@loadNativeAd
                
                shimmerFrameLayout.visibility = View.GONE
                frameLayout.visibility = View.VISIBLE
                frameLayout.removeAllViews()
                
                // Inflate native ad layout
                val adView = layoutInflater.inflate(app.messages.R.layout.ads_native_dashboard, null, false) as com.google.android.gms.ads.nativead.NativeAdView
                AdsConfigManager.populateNativeAdView(nativeAd, adView)
                
                frameLayout.addView(adView)
            },
            onFailed = {
                if (!isDestroyed && !isFinishing) {
                    container.visibility = View.GONE
                }
            }
        )
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_ACTION_TYPE = "extra_action_type" // "install" or "uninstall"
    }
}
