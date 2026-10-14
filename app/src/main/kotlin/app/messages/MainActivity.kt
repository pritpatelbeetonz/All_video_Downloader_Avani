package app.messages

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.messages.core.ads.AdEntryPoint
import app.messages.data.applock.AppLockController
import app.messages.data.role.RoleStateRepository
import app.messages.data.telephony.DefaultSmsRole
import app.messages.monetization.ads.AdSdkInitializer
import app.messages.monetization.ads.AdsRemoteConfig
import app.messages.monetization.ads.AppOpenAdManager
import app.messages.monetization.ads.ConsentManager
import app.messages.monetization.ads.InterstitialAdManager
import app.messages.notifications.MessageNotifier
import app.messages.ui.MessagesApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var roleStateRepository: RoleStateRepository
    @Inject lateinit var adsRemoteConfig: AdsRemoteConfig
    @Inject lateinit var consentManager: ConsentManager
    @Inject lateinit var adSdkInitializer: AdSdkInitializer
    @Inject lateinit var appOpenAdManager: AppOpenAdManager
    @Inject lateinit var interstitialAdManager: InterstitialAdManager
    @Inject lateinit var appLockController: AppLockController

    /** Thread id to open from a notification tap; consumed by [MessagesApp]. */
    private val openThread = MutableStateFlow<String?>(null)

    /** Route to push on entry — the launcher dashboard's Settings tile uses this. */
    private val openRoute = MutableStateFlow<String?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        val langCode = app.messages.preferences.PreferenceHelper.getInstance(newBase).getSelectedLanguageCode()
        val context = app.messages.utility.LocaleHelper.setLocale(newBase, langCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readThreadExtra(intent)
        readRouteExtra(intent)
        setContent {
            MessagesApp(
                openThread = openThread,
                onThreadOpened = { openThread.value = null },
                openRoute = openRoute,
                onRouteOpened = { openRoute.value = null },
            )
        }
        bootstrapAdsAfterFirstFrame()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readThreadExtra(intent)
        readRouteExtra(intent)
    }

    override fun onStart() {
        super.onStart()
        // App-lock gate (§F16): re-evaluates whether to lock on every foreground.
        appLockController.onForeground()
    }

    override fun onStop() {
        super.onStop()
        appLockController.onBackground()
    }

    override fun onResume() {
        super.onResume()
        roleStateRepository.refresh()
        maybeShowAppOpenAd()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DefaultSmsRole.ROLE_REQUEST_CODE) {
            roleStateRepository.refresh()
        }
    }

    private fun readThreadExtra(intent: Intent?) {
        intent?.getStringExtra(MessageNotifier.EXTRA_OPEN_THREAD)?.let {
            openThread.value = it
        }
    }

    private fun readRouteExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            openRoute.value = app.messages.ui.nav.Routes.Settings
        }
    }

    /**
     * Defer all ads-SDK work to ≥ first frame + 2s (§7.5), and only when ads are
     * enabled. Gathers UMP consent, then initializes the SDK and preloads the
     * app-open ad. No-op while ads ship OFF.
     */
    private fun bootstrapAdsAfterFirstFrame() {
        lifecycleScope.launch {
            // Defer past the first frame for cold-start budget (§7.5)…
            delay(ADS_BOOTSTRAP_DELAY_MS)
            // …then wait for the persisted ads flag to actually load. The StateFlow
            // starts false and the DataStore value arrives async, so reading `.value`
            // at a fixed delay raced the load and skipped init (no ads ever showed).
            adsRemoteConfig.adsEnabled.first { it }
            if (isFinishing || isDestroyed) return@launch
            // Request UMP consent for compliance, but DON'T gate SDK init on it — init
            // needs no consent (only ad serving does, which the SDK/UMP enforce), and
            // gating it left the SDK un-initialized whenever the consent callback didn't
            // resolve, so no ad ever loaded. Preload the app-open only after init.
            consentManager.ensureConsent(this@MainActivity) {}
            adSdkInitializer.initializeOnce {
                appOpenAdManager.preload()
                // Splash interstitial (cold start). Shares the app-open budget so the
                // two full-screen formats never stack; the app-open then serves only
                // later foreground-returns.
                interstitialAdManager.maybeShowOnColdStart(this@MainActivity)
            }
        }
    }

    private fun maybeShowAppOpenAd() {
        if (!adSdkInitializer.isReady) return
        // Notification-tap entry is a prohibited app-open surface (journey #1);
        // the policy also enforces this, but we pass the accurate entry point.
        val entryPoint = if (intent?.hasExtra(MessageNotifier.EXTRA_OPEN_THREAD) == true) {
            AdEntryPoint.NotificationTap
        } else {
            AdEntryPoint.ColdStart
        }
        lifecycleScope.launch {
            appOpenAdManager.maybeShow(this@MainActivity, entryPoint)
        }
    }

    companion object {
        /** Opens the app's own Settings screen on entry. */
        const val EXTRA_OPEN_SETTINGS = "app.messages.extra.OPEN_SETTINGS"
        private const val ADS_BOOTSTRAP_DELAY_MS = 2_000L
    }
}
