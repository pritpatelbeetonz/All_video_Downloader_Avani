package app.messages.ui.onboarding

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.core.prefs.AppPrefs
import app.messages.data.role.RoleStateRepository
import app.messages.data.role.SmsRoleStatus
import app.messages.data.telephony.DefaultSmsRole
import app.messages.preferences.PreferenceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: AppPrefs,
    private val role: DefaultSmsRole,
    roleStateRepository: RoleStateRepository,
) : ViewModel() {

    val roleStatus: StateFlow<SmsRoleStatus> = roleStateRepository.state

    fun requestRole(activity: Activity) {
        role.request(activity)
    }

    fun markOnboardingSeen() {
        viewModelScope.launch {
            // Write to both storage systems so SplashActivity (SharedPreferences) and
            // MainActivity (DataStore) agree: onboarding has been completed.
            prefs.setHasSeenOnboarding(true)
            PreferenceHelper.getInstance(appContext).setOnboardingDone(true)
        }
    }

    fun saveSurvey(priorities: Set<String>) {
        viewModelScope.launch { prefs.setSurveyPriorities(priorities) }
    }
}
