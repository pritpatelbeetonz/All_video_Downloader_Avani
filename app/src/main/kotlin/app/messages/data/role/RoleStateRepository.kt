package app.messages.data.role

import android.app.Activity
import android.content.Context
import app.messages.data.telephony.DefaultSmsRole
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Status of our app's relationship to the default-SMS-handler role. */
enum class SmsRoleStatus {
    /** We haven't asked the OS yet (cold start). */
    Unknown,

    /** The user has granted us ROLE_SMS — full read/send/receive. */
    Granted,

    /** Another app is the default. We are in limited / read-only mode. */
    Denied,

    /** Pre-API-29 path: role API isn't available on this device. */
    Unavailable,
}

/**
 * Single source of truth for role state. [refresh] is called on every resume of
 * a host that shows role-dependent UI — MainActivity, LauncherActivity, and the
 * messages tree itself — so a flip the user made in our dialog or in system
 * Settings is reflected as soon as they are looking at the app again. The
 * launcher process is long-lived, so without those re-reads this value can stay
 * stale for days.
 */
@Singleton
class RoleStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val role: DefaultSmsRole,
) {
    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<SmsRoleStatus> = _state.asStateFlow()

    fun refresh() {
        _state.value = currentState()
    }

    fun request(activity: Activity) {
        role.request(activity)
    }

    private fun currentState(): SmsRoleStatus {
        if (!role.canRequest(context)) return SmsRoleStatus.Unavailable
        return if (role.isDefault(context)) SmsRoleStatus.Granted else SmsRoleStatus.Denied
    }
}
