package app.messages.data.telephony

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default-SMS-handler role helper.
 *
 * On API 29+ we use [RoleManager.ROLE_SMS] — this is the canonical path on
 * Android 10–15 per build doc §1.2.
 *
 * On 24–28 (which we still floor at minSdk for graceful read-only mode) the
 * legacy [Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT] intent is used.
 *
 * Role grant is *user-initiated* — we never silently change the default app.
 */
@Singleton
class DefaultSmsRole @Inject constructor() {

    fun isDefault(context: Context): Boolean {
        // On API 29+ the role manager is the source of truth. On Android 14+
        // (and Android 16 in particular) the legacy
        // `Settings.Secure.default_sms_application` setting reads null even
        // when our role IS held — so checking `Telephony.Sms.getDefaultSmsPackage`
        // is a false negative on modern devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (rm != null) return rm.isRoleHeld(RoleManager.ROLE_SMS)
        }
        val pkg = Telephony.Sms.getDefaultSmsPackage(context)
        return pkg == context.packageName
    }

    fun defaultPackage(context: Context): String? =
        Telephony.Sms.getDefaultSmsPackage(context)

    fun canRequest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return rm?.isRoleAvailable(RoleManager.ROLE_SMS) == true
        }
        return true
    }

    /**
     * Launches the role request UI. The caller is responsible for refreshing
     * role state on activity resume — the system grant dialog returns control
     * here with the new state already reflected in [isDefault].
     */
    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            val available = rm.isRoleAvailable(RoleManager.ROLE_SMS)
            val held = rm.isRoleHeld(RoleManager.ROLE_SMS)
            if (available && !held) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
                activity.startActivityForResult(intent, ROLE_REQUEST_CODE)
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(
                    Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                    activity.packageName,
                )
            activity.startActivityForResult(intent, ROLE_REQUEST_CODE)
        }
    }

    companion object {
        const val ROLE_REQUEST_CODE = 4242
    }
}
