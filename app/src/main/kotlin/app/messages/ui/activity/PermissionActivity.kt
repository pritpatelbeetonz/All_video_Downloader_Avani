package app.messages.ui.activity

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
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
import app.messages.data.model.PermissionItem
import app.messages.databinding.ActivityPermissionBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.adapters.PermissionListAdapter
import app.messages.ui.base.BaseActivity
import app.messages.utility.PermissionProvider
import app.messages.utility.viewBinding

class PermissionActivity : BaseActivity() {
    private val binding by viewBinding(ActivityPermissionBinding::inflate)
    private var permissionListAdapter: PermissionListAdapter? = null
    private var permissionList: List<PermissionItem> = ArrayList()
    private var permissionCheckHandler: Handler? = null
    private var permissionCheckRunnable: Runnable? = null
    private var isCheckingPermission = false
    private var hasNavigatedToHome = false

    private var askDefaultAppPermission = false

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // When user comes back from overlay settings
            stopPermissionCheck()

            // Prevent duplicate navigation if already navigated
            if (hasNavigatedToHome || isFinishing || isDestroyed) {
                return@registerForActivityResult
            }

            AnalyticsHelper.logEvent(
                EventNames.PERMISSION_CLICK_ALLOW_PERMISSION, EventScreenName.PERMISSION
            )
            if (isOverlayPermissionGranted()) {
                goToMainActivity()
            } else {
                // User didn’t enable overlay → go to DisplayOverlayPermissionActivity
                /* Activity.finish()
                 Activity.startActivity(Intent(this, DisplayOverlayPermissionActivity::class.java))*/
                goToMainActivity()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ If already granted → go straight to MainActivity

        /*
                BannerAdLoader.instance?.showBanner(
                    this@PermissionActivity,
                    binding.bannerViewAdsFrameLayout.bannerAdFrameLayout,
                    binding.bannerViewAdsFrameLayout.bannerAdShimmerFrameLayout
                )*/

        setContentView(binding.root)

        loadBannerAd()

        askDefaultAppPermission = intent.getBooleanExtra("defaultPermission", false)

        /*    if (areAllPermissionsGranted()) {
    //            requestDefaultAppPermission()
                goToMainActivity()
                return
            }*/

        setAdapter()
        privacyText()

        binding.btnAllowPermission.setOnClickListener {
//            requestNextPermissionNew()
            requestDefaultAppPermission()
        }
    }

    private fun loadBannerAd() {
        var isAdRequested = false
        val adRunnable = Runnable {
            if (!isAdRequested) {
                isAdRequested = true
               showBanner(
                    this@PermissionActivity,
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

        // Prevent duplicate navigation
        if (hasNavigatedToHome || isFinishing || isDestroyed) {
            return
        }

        // Check in background if overlay permission was granted
        // This handles the case when user grants permission from settings and returns to the app

        /*if (askDefaultAppPermission) {
            goToMainActivity()
        } else*/ if (areAllPermissionsGranted()) {
            stopPermissionCheck()
            goToMainActivity()
//            requestDefaultAppPermission()
        }
    }


    override fun onPause() {
        super.onPause()
        // Don't stop checking when paused - we want to detect permission grant while user is in settings
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // Prevent duplicate navigation
        if (hasNavigatedToHome || isFinishing || isDestroyed) {
            return
        }

        // When window regains focus (user switches back to app), check permission immediately
        if (hasFocus && isCheckingPermission) {
            if (areAllPermissionsGranted()) {
                stopPermissionCheck()
                goToMainActivity()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPermissionCheck()
    }

    private fun startPermissionCheck() {
        if (isCheckingPermission || hasNavigatedToHome) return

        isCheckingPermission = true
        permissionCheckHandler = Handler(Looper.getMainLooper())
        permissionCheckRunnable = object : Runnable {
            override fun run() {
                // Prevent duplicate navigation
                if (hasNavigatedToHome || isFinishing || isDestroyed) {
                    stopPermissionCheck()
                    return
                }

                if (isNotificationPermissionGranted()) {
                    stopPermissionCheck()

                    goToMainActivity()
                    /*   val intent = Intent(this@PermissionActivity, PermissionActivity::class.java)
                       intent.flags =
                           Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                       intent.putExtra("defaultPermission", true)
                       startActivity(intent)
   */
//                    requestDefaultAppPermission()
                } else {
                    // Continue checking every 500ms
                    permissionCheckHandler?.postDelayed(this, 500)
                }
            }
        }
        permissionCheckHandler?.post(permissionCheckRunnable!!)
    }

    private fun stopPermissionCheck() {
        isCheckingPermission = false
        permissionCheckRunnable?.let {
            permissionCheckHandler?.removeCallbacks(it)
        }
        permissionCheckRunnable = null
        permissionCheckHandler = null
    }

    private fun privacyText() {
        val textView = binding.tvPrivacyPolicy
        val fullText = getString(R.string.by_continuing_you_agree_to_our_privacy_policy)

        val spannable = SpannableString(fullText)
        val primaryColor = ContextCompat.getColor(this, R.color.black)


        // ---- Privacy Policy ----
        val privacyText = getString(R.string.privacy_policy)
        val privacyStart = fullText.indexOf(privacyText)
        if (privacyStart >= 0) {
            val privacyEnd = privacyStart + privacyText.length
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {

                    openPrivacyPolicy()
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = true   // ✅ underline
                    ds.color = primaryColor     // ✅ primary color
                }
            }, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
    }


    private fun setAdapter() {
        permissionList = PermissionProvider.getPermissions(this)
        permissionListAdapter = PermissionListAdapter(permissionList)
        binding.recyclerPermissions.layoutManager = LinearLayoutManager(this)
        binding.recyclerPermissions.adapter = permissionListAdapter
    }


    private fun requestNextPermissionNew() {
        // 1. Notification
        if (!isNotificationPermissionGranted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                actionOnPermission = { granted ->
                    if (granted) requestNextPermissionNew()
                }
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), GENERIC_PERM_HANDLER
                )
            } else {
                val intent = Intent().setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
            return
        }

        // ✅ Done → Go to Main
        goToMainActivity()
    }


    private fun areAllPermissionsGranted(): Boolean {
        return isNotificationPermissionGranted() && isDefaultSmsApp()
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }


    private fun isPhoneStatePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestDefaultAppPermission() {
//        stopPermissionCheck()
        requestDefaultSmsRole()
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    private val smsRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isDefaultSmsApp()) {
                requestNextPermissionNew()
            } else {
                }
        }

    private fun requestDefaultSmsRole() {
        if (!isDefaultSmsApp()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        smsRoleLauncher.launch(intent)
                    } else {
                        requestNextPermissionNew()
                    }
                } else {
                    // Fallback if RoleManager is not available or ROLE_SMS is not available
                    launchLegacyDefaultSmsRequest()
                }
            } else {
                launchLegacyDefaultSmsRequest()
            }
        } else {
            requestNextPermissionNew()
        }
    }

    private fun launchLegacyDefaultSmsRequest() {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        smsRoleLauncher.launch(intent)
    }

    private fun goToMainActivity() {
        // Prevent duplicate navigation
        if (hasNavigatedToHome || isFinishing || isDestroyed) {
            return
        }

        hasNavigatedToHome = true

        PreferenceHelper.getInstance(this).setPermissionDone(true)

        // Clear back stack and start HomeActivity as a new task
        val intent = Intent(this, app.messages.ui.launcher.LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()

    }
}