package app.messages.ui.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.core.prefs.AppPrefs
import app.messages.data.applock.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the App-lock settings screen: set/change PIN, toggle biometrics, turn off. */
@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(
    private val controller: AppLockController,
    prefs: AppPrefs,
) : ViewModel() {

    val lockEnabled: StateFlow<Boolean> =
        prefs.appLockEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricEnabled: StateFlow<Boolean> = controller.biometricEnabled

    fun setPin(pin: String) {
        viewModelScope.launch { controller.setPin(pin) }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { controller.setBiometricEnabled(enabled) }
    }

    fun disable() {
        viewModelScope.launch { controller.disable() }
    }
}
