package app.messages.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.core.prefs.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [NotificationSettingsScreen] with the DataStore-held notification
 * preferences. Both default ON; writes flow straight back into [AppPrefs], which
 * MessageNotifier observes — so toggles take effect on the next incoming message
 * without any restart.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val prefs: AppPrefs,
) : ViewModel() {
    val notificationsEnabled: StateFlow<Boolean> =
        prefs.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val messagePreview: StateFlow<Boolean> =
        prefs.messagePreview.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    }

    fun setMessagePreview(enabled: Boolean) {
        viewModelScope.launch { prefs.setMessagePreview(enabled) }
    }
}
