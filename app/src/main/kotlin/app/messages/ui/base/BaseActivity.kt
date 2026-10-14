package app.messages.ui.base

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.WindowMetrics
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.messages.config.AdsConfigManager
import com.google.android.gms.ads.AdSize
import app.messages.R
import app.messages.constant.Constants.PRIVACY_POLICY
import app.messages.preferences.PreferenceHelper
import app.messages.utility.LocaleHelper
import app.messages.utility.isTiramisuPlusNew

abstract class BaseActivity : AppCompatActivity() {

    val GENERIC_PERM_HANDLER = 100
    val FULLSCREEN_INTENT_PERMISSION_REQ = 101
    var actionOnPermission: ((granted: Boolean) -> Unit)? = null
    var isAskingPermissions = false
    private var currentLanguage: String? = null

    companion object {
        const val GENERIC_PERM_HANDLER = 101
        const val OVERLAY_PERMISSION_REQ_CODE = 102
    }

    open fun shouldApplySystemBarInsets(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = PreferenceHelper.getInstance(this).getSelectedLanguageCode()

        if (shouldApplySystemBarInsets()) {
            applySystemBarInsets(R.color.colorBackground, !isNightModeActive())
        }
    }

     fun getNormalAdSize(activity: Activity): AdSize {
        val displayMetrics = activity.resources.displayMetrics
        val adWidthPixels =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
                windowMetrics.bounds.width()
            } else {
                displayMetrics.widthPixels
            }
        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
    override fun attachBaseContext(newBase: Context) {

        val langCode = PreferenceHelper.getInstance(newBase).getSelectedLanguageCode()
        var context = LocaleHelper.setLocale(newBase, langCode)

        val scale =PreferenceHelper.getInstance(newBase).getFontSize()

        val configuration = Configuration(context.resources.configuration)
        configuration.fontScale = scale

        context = context.createConfigurationContext(configuration)

        super.attachBaseContext(context)
    }

    fun isNightModeActive(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    /*

        override fun attachBaseContext(newBase: android.content.Context) {
            val langCode = PreferenceHelper.getInstance(newBase).getSelectedLanguageCode()
            super.attachBaseContext(LocaleHelper.setLocale(newBase, langCode))
        }
    */

    override fun onResume() {
        super.onResume()
        val newLang = PreferenceHelper.getInstance(this).getSelectedLanguageCode()
        if (currentLanguage != null && currentLanguage != newLang) {
            currentLanguage = newLang
            recreate()
            return
        }
    }

    fun applySystemBarInsets(statusBarColorResId: Int, lightStatusBar: Boolean) {
        val window: Window = window

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val decorView: View = window.decorView
        val controller = WindowInsetsControllerCompat(window, decorView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightStatusBar

        val root: FrameLayout = findViewById(android.R.id.content)
        root.setBackgroundColor(
            ContextCompat.getColor(this, statusBarColorResId)
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime: Insets = insets.getInsets(WindowInsetsCompat.Type.ime())

            val isKeyboardVisible = ime.bottom > 0

            // If keyboard is open → don't paint bottom padding area
            if (isKeyboardVisible) {
                v.setPadding(sysBars.left, sysBars.top, sysBars.right, 0)
            } else {
                v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            }

            insets
        }

        if (shouldHideSystemBars()) {
            controller.hide(
                WindowInsetsCompat.Type.navigationBars()
            )
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    open fun shouldHideSystemBars(): Boolean = true


    private fun setupStatusBar() {
        // Set status bar color to black
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorBackground)

        // Ensure status bar icons are light (white) for better visibility on black background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
    }


    public fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    fun openPrivacyPolicy() {
        try {
            val url = AdsConfigManager.config.privacyPolicyUrl.ifBlank {
                PRIVACY_POLICY
            }
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            }
    }

    fun handleNotificationPermission(callback: (granted: Boolean) -> Unit) {
        if (!isTiramisuPlusNew()) {
            callback(true)
        } else {
            handlePermission { granted ->
                callback(granted)
            }
        }
    }

    fun handlePermission(callback: (granted: Boolean) -> Unit) {
        val permissionStr = Manifest.permission.POST_NOTIFICATIONS

        if (ContextCompat.checkSelfPermission(
                this,
                permissionStr
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            callback(true)
        } else {
            isAskingPermissions = true
            actionOnPermission = callback
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permissionStr),
                GENERIC_PERM_HANDLER
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isAskingPermissions = false

        if (requestCode == GENERIC_PERM_HANDLER || requestCode == FULLSCREEN_INTENT_PERMISSION_REQ) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                actionOnPermission?.invoke(true)
            } else {
                // Permission denied
                if (permissions.isNotEmpty() &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])
                ) {
                    // "Don’t ask again" → open settings
                    showSettingsDialog(this@BaseActivity)
                } else {
                    actionOnPermission?.invoke(false)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun showSettingsDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.permission_des))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.go_to_setting)) { dialog, _ ->

                dialog.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)

            }
            .show()
    }

    fun isInternetAvailable(videoConferenceActivity: Activity): Boolean {
        var checkInternetBoolean = false
        val connectivityManager =
            videoConferenceActivity.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkCapabilities =
                    connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                checkInternetBoolean =
                    networkCapabilities != null && networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )
            } else {
                val networkInfo = connectivityManager.activeNetworkInfo
                checkInternetBoolean =
                    networkInfo != null && networkInfo.isConnectedOrConnecting
            }
        } catch (videoConferenceException: Exception) {
            videoConferenceException.message
        }
        return checkInternetBoolean
    }


}