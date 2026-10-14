package app.messages.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.core.prefs.AppPrefs
import app.messages.core.prefs.SwipeAction
import app.messages.core.prefs.ThemeMode
import app.messages.monetization.ads.AdsController
import app.messages.monetization.ads.AdsRemoteConfig
import app.messages.monetization.iap.EntitlementController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPrefs,
    private val adsRemoteConfig: AdsRemoteConfig,
    private val entitlement: EntitlementController,
    adsController: AdsController,
) : ViewModel() {

    // ── Developer toggles (debug only) ──────────────────────────────────────────
    val adsEnabledDebug: StateFlow<Boolean> = adsRemoteConfig.adsEnabled
    val isPremium: StateFlow<Boolean> = entitlement.isPremium

    /** Whether to show the adaptive banner at the bottom of Settings (§6.2 A5). */
    val showBanner: StateFlow<Boolean> = adsController.bannerAdsEnabled

    fun setAdsEnabledDebug(value: Boolean) = adsRemoteConfig.setEnabledForDebug(value)
    fun setPremiumDebug(value: Boolean) {
        viewModelScope.launch { entitlement.setPremium(value) }
    }

    val themeMode: StateFlow<ThemeMode> =
        prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    val swipeAction: StateFlow<SwipeAction> =
        prefs.swipeAction.stateIn(viewModelScope, SharingStarted.Eagerly, SwipeAction.Archive)

    val deliveryReports: StateFlow<Boolean> =
        prefs.deliveryReports.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setSwipeAction(action: SwipeAction) {
        viewModelScope.launch { prefs.setSwipeAction(action) }
    }

    fun setDeliveryReports(enabled: Boolean) {
        viewModelScope.launch { prefs.setDeliveryReports(enabled) }
    }
}
