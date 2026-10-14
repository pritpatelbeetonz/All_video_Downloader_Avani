package app.messages.ui.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import app.messages.Controller
import app.messages.Controller.Companion.adsModel
import app.messages.R

import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.AnalyticsHelper.logAds
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import app.messages.constant.Constants
import app.messages.databinding.ActivityHomeBinding
import app.messages.ui.adapters.HomePagerAdapter
import app.messages.ui.base.BaseActivity

class HomeActivity : BaseActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var pagerAdapter: HomePagerAdapter



    private val smsRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Handle result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app.messages.preferences.PreferenceHelper.getInstance(this).setLauncherModeEnabled(false)

        loadBannerAd()

        requestNotificationPermission()
        maybeShowPremiumIntro()
    }

    private fun maybeShowPremiumIntro() {
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(this)
        if (prefs.isPremium() || prefs.isPremiumIntroShown()) return
        prefs.setPremiumIntroShown(true)
        startActivity(Intent(this, PremiumActivity::class.java))
    }

    private fun loadBannerAd() {
        var isAdRequested = false
        val adRunnable = Runnable {
            if (!isAdRequested) {
                isAdRequested = true
                showBanner(
                    this@HomeActivity,
                    binding.bannerViewAdsFrameLayout.bannerAdFrameLayout,
                    binding.bannerViewAdsFrameLayout.bannerAdShimmerFrameLayout
                )
            }
        }
        val adHandler = android.os.Handler(android.os.Looper.getMainLooper())

      Controller.isRemoteConfigLoaded.observe(this) { isLoaded ->
            if (isLoaded == true) {
                adHandler.removeCallbacks(adRunnable)
                adRunnable.run()
            }
        }

        // Safety Fallback: Use the cached config to load the ad if the background fetch
        // takes longer than 3.5 seconds, or if the user bypassed the Splash Screen entirely.
        adHandler.postDelayed(adRunnable, 3500)
    }

    fun showBanner(
        activity: Activity,
        frameLayout: FrameLayout,
        shimmerFrameLayout: FrameLayout
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
            return
        }
        if (adsModel!!.isShowBannerAd.equals("Yes", ignoreCase = true)) {
            loadAndShowBanner(activity,frameLayout,shimmerFrameLayout,Constants.AD_ID_BANNER)
        } else {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
        }
    }

    private fun loadAndShowBanner(activity: Activity, frameLayout: FrameLayout, shimmerFrameLayout: FrameLayout, adUnitID: String) {
        //Set flAd View Height to Same As Place Holder
        val adView = AdView(activity)
        adView.adUnitId = adUnitID
        //            AdSize adSize = getAdSize(activity);
        adView.setAdSize(getNormalAdSize(activity))
        val adRequest = AdRequest.Builder().build()
        logAds(activity, "banner_req")
        adView.loadAd(adRequest)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                logAds(activity, "banner_loaded")
                frameLayout.visibility = View.VISIBLE
                shimmerFrameLayout.visibility = View.GONE

                if (adView.parent != null) {
                    (adView.parent as ViewGroup).removeView(adView) // <- fix
                }
                frameLayout.removeAllViews()
                logAds(activity, "banner_showed")
                frameLayout.addView(adView)
            }

            override fun onAdClicked() {
                super.onAdClicked()
                logAds(activity, "banner_clicked")
//                showBanner(activity, frameLayout, shimmerFrameLayout)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                frameLayout.visibility = View.GONE
                shimmerFrameLayout.visibility = View.GONE
                logAds(activity, "banner_failed_to_load_" + loadAdError.code)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (!isDefaultSmsApp()) {
            promptSetDefaultSmsApp()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // Prevent duplicate navigation
        if (isFinishing || isDestroyed) {
            return
        }


    }



    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            packageName == defaultSmsPackage
        }
    }

    private fun promptSetDefaultSmsApp() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_as_default_sms_app))
            .setMessage(getString(R.string.to_send_sms_messages_this_app_needs_to_be_set_as_your_default_sms_app_would_you_like_to_do_that_now))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                AnalyticsHelper.logEvent(EventNames.PERMISSION_CLICK_ALLOW_PERMISSION, EventScreenName.HOME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = getSystemService(android.app.role.RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                        smsRoleLauncher.launch(intent)
                    } else {
                        launchLegacyDefaultSmsRequest()
                    }
                } else {
                    launchLegacyDefaultSmsRequest()
                }
            }
            .setNegativeButton(getString(R.string.no)) { _, _ ->
                // User declined
            }
            .show()
    }

    private fun launchLegacyDefaultSmsRequest() {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        smsRoleLauncher.launch(intent)
    }
}