package app.messages.ui.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.applock.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Backs the full-screen [LockScreen]. */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val controller: AppLockController,
) : ViewModel() {

    val biometricEnabled: StateFlow<Boolean> = controller.biometricEnabled

    /** Called by the biometric success path. */
    fun unlocked() = controller.onUnlocked()

    /** Verifies a typed PIN; on success the controller flips the gate to Unlocked. */
    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = controller.verifyPin(pin)
            if (ok) controller.onUnlocked()
            onResult(ok)
        }
    }
}
