package app.messages.data.applock

import app.messages.core.applock.AppLockPolicy
import app.messages.core.applock.PinCredential
import app.messages.core.applock.PinHasher
import app.messages.core.prefs.AppPrefs
import app.messages.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-lifetime owner of the app-lock state (§F16). The "unlocked this
 * process" flag is intentionally **not** persisted — every cold start re-locks.
 * Re-lock on resume is governed by [AppLockPolicy] (with a short grace window so
 * the system photo/document pickers don't trigger a re-prompt).
 *
 * Activity lifecycle drives [onForeground]/[onBackground]; the lock screen calls
 * [onUnlocked] on success.
 */
@Singleton
class AppLockController @Inject constructor(
    private val prefs: AppPrefs,
    @ApplicationScope private val scope: CoroutineScope,
) {
    enum class LockUiState { Loading, Locked, Unlocked }

    @Volatile private var enabledCache = false
    @Volatile private var authedThisProcess = false
    @Volatile private var backgroundedAt: Long? = null

    private val _state = MutableStateFlow(LockUiState.Loading)
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    val biometricEnabled: StateFlow<Boolean> =
        prefs.appLockBiometric.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            prefs.appLockEnabled.collect { enabled ->
                enabledCache = enabled
                when {
                    _state.value == LockUiState.Loading ->
                        _state.value = if (enabled && !authedThisProcess) {
                            LockUiState.Locked
                        } else {
                            LockUiState.Unlocked
                        }
                    // Lock switched off in Settings while running → drop the gate.
                    !enabled -> _state.value = LockUiState.Unlocked
                }
            }
        }
    }

    /** App became visible (activity onStart). Re-locks if policy says so. */
    fun onForeground(nowMs: Long = System.currentTimeMillis()) {
        if (_state.value == LockUiState.Loading) return
        if (AppLockPolicy.requiresUnlock(enabledCache, authedThisProcess, backgroundedAt, nowMs)) {
            _state.value = LockUiState.Locked
        }
    }

    /** App went to background (activity onStop). Starts the grace clock. */
    fun onBackground(nowMs: Long = System.currentTimeMillis()) {
        backgroundedAt = nowMs
    }

    /** Successful PIN/biometric unlock. */
    fun onUnlocked() {
        authedThisProcess = true
        backgroundedAt = null
        _state.value = LockUiState.Unlocked
    }

    suspend fun verifyPin(pin: String): Boolean {
        val encoded = prefs.appLockPinNow() ?: return false
        val cred = PinCredential.decode(encoded) ?: return false
        return PinHasher.verify(pin, cred)
    }

    suspend fun setPin(pin: String) {
        prefs.setAppLockPin(PinHasher.create(pin).encode())
        authedThisProcess = true // setting a PIN counts as being unlocked now
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = prefs.setAppLockBiometric(enabled)

    suspend fun disable() {
        prefs.clearAppLock()
        _state.value = LockUiState.Unlocked
    }
}
