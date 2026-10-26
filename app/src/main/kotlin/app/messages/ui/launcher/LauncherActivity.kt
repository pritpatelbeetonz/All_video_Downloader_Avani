package app.messages.ui.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import app.messages.R
import app.messages.databinding.ActivityLauncherBinding

import android.content.Context
import androidx.activity.viewModels
import app.messages.utility.LocaleHelper
import app.messages.ui.launcher.adapter.LauncherPagerAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LauncherActivity : AppCompatActivity() {

    /**
     * The launcher process outlives every screen, so the cached default-SMS role
     * state goes stale the moment the user flips the role — in our own dialog or in
     * system settings. Re-reading it on every resume is what keeps the "set as
     * default" card from lingering after the role is granted.
     */
    @javax.inject.Inject lateinit var roleStateRepository: app.messages.data.role.RoleStateRepository

    override fun attachBaseContext(newBase: Context) {
        val langCode = app.messages.preferences.PreferenceHelper.getInstance(newBase).getSelectedLanguageCode()
        var context = LocaleHelper.setLocale(newBase, langCode)
        val scale = app.messages.preferences.PreferenceHelper.getInstance(newBase).getFontSize()
        if (scale > 0f) {
            val configuration = android.content.res.Configuration(context.resources.configuration)
            configuration.fontScale = scale
            context = context.createConfigurationContext(configuration)
        }
        super.attachBaseContext(context)
    }

    private lateinit var binding: ActivityLauncherBinding

    // onboardingPreferences removed

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        super.onCreate(savedInstanceState)
        app.messages.utility.ThemeUtils.applyTheme(this)
        app.messages.preferences.PreferenceHelper.getInstance(this).setLauncherModeEnabled(true)
        
        // Onboarding is handled by MessageApp's SplashActivity



        enableEdgeToEdge()
        showSystemWallpaperBehindWindow()
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (isDefaultHomeApp()) {
            app.messages.analytics.AnalyticsHelper.logOnce(
                context = this,
                prefKey = app.messages.preferences.PreferenceHelper.KEY_EVENT_SET_LAUNCHER_LOGGED,
                eventName = app.messages.analytics.EventNames.LAUNCHER_PERMISSION_GRANTED
            )
        }

        // Granting the home role makes Android fire the HOME intent straight at this
        // activity, which skips SetDefaultLauncherActivity.goToNext(). Show the SetAsDefault
        // interstitial here before forwarding the user to LanguageSelectionActivity.
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(this)
        if (!prefs.isLanguageSelected()) {
            android.util.Log.d("SetDefaultFlow", "[LauncherActivity] Android launched LauncherActivity after granting HOME role. isLanguageSelected=false -> Preloading language native ad & Triggering showSetAsDefaultInterstitial")
            app.messages.config.AdsConfigManager.preloadLanguageNativeAd(this)
            app.messages.config.AdsConfigManager.showSetAsDefaultInterstitial(this) {
                android.util.Log.d("SetDefaultFlow", "[LauncherActivity] showSetAsDefaultInterstitial completed -> Forwarding to LanguageSelectionActivity")
                startActivity(
                    Intent(this, app.messages.ui.activity.LanguageSelectionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                finish()
            }
            return
        }

        // Navigation to PermissionActivity and PremiumActivity removed from startup flow.
        // SMS permissions are requested on the user's first swipe in ChatsFragment.

        android.util.Log.d("SetDefaultFlow", "[LauncherActivity] User is on main launcher (isLanguageSelected=true)")
        // app.messages.config.AdsConfigManager.preloadAllAds(this)

        setupNavigationIfNeeded(R.id.launcherHostFragment)
        handleHomeIntent(intent)
        handleLaunchExtras(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as? NavHostFragment
                val navController = navHost?.navController
                if (navController == null || !navController.isGraphSet()) {
                    moveTaskToBack(true)
                    return
                }
                val current = navHost.childFragmentManager.primaryNavigationFragment
                when {
                    current is LauncherHostFragment && current.onLauncherBackPressed() -> Unit
                    navController.previousBackStackEntry != null -> navController.popBackStack()
                    isDefaultHomeApp() -> Unit
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    private var hasPromptedForDefault = false

    private val defaultLauncherResultLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Result handled gracefully
    }

    override fun onResume() {
        super.onResume()
        roleStateRepository.refresh()
        configureTransparentSystemBars()
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as? NavHostFragment
        val destId = navHost?.navController?.takeIf { it.isGraphSet() }?.currentDestination?.id
        if (destId == R.id.launcherHostFragment) {
            val host = navHost?.childFragmentManager?.primaryNavigationFragment as? LauncherHostFragment
            host?.reapplyPageSystemBars()
        }
        // Default-launcher prompt removed: SetDefaultLauncherActivity handles this before we reach here.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)



        setIntent(intent)
        handleHomeIntent(intent)
        handleLaunchExtras(intent)
    }

    /** Lets the system wallpaper composite behind transparent launcher UI. */
    private fun showSystemWallpaperBehindWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    /**
     * Keep status/nav bars fully transparent so wallpaper shows through.
     * Contrast enforcement otherwise draws a light/white scrim behind nav buttons.
     */
    private fun configureTransparentSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }

    private fun handleLaunchExtras(intent: Intent?) {
        // Handled directly by LauncherHostFragment onViewCreated
    }

    private fun handleHomeIntent(intent: Intent?) {
        if (intent == null) return
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) return
        if (!isDefaultHomeApp()) return
        supportFragmentManager.findFragmentById(R.id.navHost)
            ?.childFragmentManager
            ?.primaryNavigationFragment
            ?.let { fragment ->
                if (fragment is LauncherHostFragment) {
                    fragment.onHomePressed()
                }
            }
    }

    /** Sets the nav graph only when it has not been set yet (no XML app:navGraph). */
    private fun setupNavigationIfNeeded(startDestination: Int) {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as? NavHostFragment
            ?: return
        if (navHost.navController.isGraphSet()) return
        setupNavigation(startDestination)
    }

    private fun setupNavigation(startDestination: Int) {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(startDestination)
        navController.graph = graph
        // Graph is set after first destination-observer attempt in onCreate — attach now.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            configureTransparentSystemBars()
            if (destination.id == R.id.launcherHostFragment) {
                binding.root.post {
                    val host = navHost.childFragmentManager.primaryNavigationFragment as? LauncherHostFragment
                    host?.reapplyPageSystemBars()
                }
            }
        }
    }

    private fun androidx.navigation.NavController.isGraphSet(): Boolean {
        return try {
            graph
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    /**
     * Launcher pager:
     * Home (wallpaper) → white system icons;
     * Messages & Dashboard (light/dark theme content) → black system icons on light theme, white on dark theme.
     */
    fun applyLauncherPageSystemBars(page: Int) {
        configureTransparentSystemBars()
        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val controller = WindowInsetsControllerCompat(window, binding.root)
        
        when (page) {
            LauncherPagerAdapter.PAGE_HOME -> {
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
            else -> {
                controller.isAppearanceLightStatusBars = !isNight
                controller.isAppearanceLightNavigationBars = !isNight
            }
        }
    }

    private fun isDefaultHomeApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = packageManager.resolveActivity(homeIntent, 0)
        return resolve?.activityInfo?.packageName == packageName
    }

    /**
     * PremiumActivity is no longer shown during startup flow.
     * Kept here as a no-op to avoid breaking any indirect call sites.
     */
    private fun maybeShowPremiumIntro() {
        // Intentionally empty — removed from startup flow per requirements.
    }

    private val launcherViewModel: LauncherViewModel by viewModels { LauncherViewModel.Factory(this) }
    private var packageReceiver: android.content.BroadcastReceiver? = null

    private var hasPromptedRuntimePermissions = false
    private var hasPromptedDefaultSms = false

    private val defaultSmsRoleLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Default SMS role completed
    }

    private val appPermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
    }

    fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            android.provider.Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                defaultSmsRoleLauncher.launch(intent)
            }
        } else {
            val intent = Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            defaultSmsRoleLauncher.launch(intent)
        }
    }

    fun checkPermissionsAndSetup() {
        if (hasPromptedRuntimePermissions) return
        hasPromptedRuntimePermissions = true

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            appPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()
        registerPackageReceiver()
    }

    override fun onStop() {
        super.onStop()
        // DO NOT unregister package receiver here so we can catch installs when in background
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterPackageReceiver()
    }

    private fun registerPackageReceiver() {
        if (packageReceiver != null) return
        packageReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                val packageName = intent.data?.schemeSpecificPart
                
                if (action == Intent.ACTION_PACKAGE_ADDED ||
                    action == Intent.ACTION_PACKAGE_REMOVED ||
                    action == Intent.ACTION_PACKAGE_CHANGED) {
                    
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    // If it's a replacement and this is ACTION_PACKAGE_REMOVED, skip to avoid double refresh
                    if (action == Intent.ACTION_PACKAGE_REMOVED && isReplacing) return

                    // An install/update can add or drop the AdMob meta-data, so drop the
                    // cached answer and let the refresh below resolve it again.
                    if (packageName != null) {
                        app.messages.core.ads.AdSupportedAppDetector.invalidate(packageName)
                        // An update can change the app's icon too.
                        app.messages.ui.launcher.adapter.AppIconCache.invalidate(packageName)
                    }

                    launcherViewModel.refreshApps()
                    
                    if (packageName != null && !isReplacing && action != Intent.ACTION_PACKAGE_CHANGED) {
                        val pm = context.packageManager
                        var appLabel = packageName
                        if (action == Intent.ACTION_PACKAGE_ADDED) {
                            try {
                                val info = pm.getApplicationInfo(packageName, 0)
                                appLabel = pm.getApplicationLabel(info).toString()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (action == Intent.ACTION_PACKAGE_REMOVED) {
                            val cachedApp = app.messages.ui.launcher.drawer.GlobalAppCache.getAppInfo(packageName)
                            if (cachedApp != null) {
                                appLabel = cachedApp.label
                                val appIcon = app.messages.ui.launcher.adapter.AppIconCache.peek(cachedApp)
                                    ?: app.messages.ui.launcher.drawer.AppActionCache.lastUninstalledIcon
                                app.messages.ui.launcher.drawer.AppActionCache.setPendingUninstall(
                                    packageName,
                                    cachedApp.label,
                                    appIcon
                                )
                            }
                        }
                        
                        val successIntent = Intent(context, app.messages.ui.launcher.drawer.AppActionSuccessActivity::class.java).apply {
                            putExtra(app.messages.ui.launcher.drawer.AppActionSuccessActivity.EXTRA_PACKAGE_NAME, packageName)
                            putExtra(app.messages.ui.launcher.drawer.AppActionSuccessActivity.EXTRA_APP_LABEL, appLabel)
                            putExtra(app.messages.ui.launcher.drawer.AppActionSuccessActivity.EXTRA_ACTION_TYPE, if (action == Intent.ACTION_PACKAGE_ADDED) "install" else "uninstall")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(successIntent)
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            packageReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun unregisterPackageReceiver() {
        packageReceiver?.let {
            runCatching { unregisterReceiver(it) }
            packageReceiver = null
        }
    }
}
