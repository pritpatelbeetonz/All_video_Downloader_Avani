package app.messages.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.core.prefs.AppPrefs
import app.messages.core.prefs.ThemeMode
import app.messages.data.applock.AppLockController
import app.messages.data.role.RoleStateRepository
import app.messages.data.role.SmsRoleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Top-level VM — owns the boot-time decisions (start destination, session
 * counting) so individual screen VMs don't have to. Lifecycle of this VM
 * matches the host Activity.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: AppPrefs,
    private val roleStateRepository: RoleStateRepository,
    lockController: AppLockController,
) : ViewModel() {

    val hasSeenOnboarding: StateFlow<Boolean?> = prefs.hasSeenOnboarding
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    val roleStatus: StateFlow<SmsRoleStatus> = roleStateRepository.state

    /** App-lock gate state (§F16): Loading → splash, Locked → lock screen. */
    val lockState: StateFlow<AppLockController.LockUiState> = lockController.state

    /**
     * Re-reads the default-SMS role from the OS. Called on every resume of the
     * messages UI so a role granted elsewhere (our dialog, system settings) is
     * reflected immediately — both by hiding the banner and by re-querying the
     * inbox, which the repository keys off this same state.
     */
    fun refreshRoleState() {
        roleStateRepository.refresh()
    }

    init {
        viewModelScope.launch { prefs.incrementSessionCount() }
    }
}
