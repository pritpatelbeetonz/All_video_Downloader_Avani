package app.messages.attribution

import android.content.Context
import android.telephony.TelephonyManager

/**
 * The reference app's SIM and country checks (steps 3 and 4), run after the Firestore lookup and
 * before the Google / Facebook / Unity checks. Neither needs a permission.
 */
internal object DeviceChecks {

    /** true / false, or null when the check itself failed (then the other checks decide). */
    fun hasSimCard(context: Context): Boolean? = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        when (tm.simState) {
            TelephonyManager.SIM_STATE_ABSENT, TelephonyManager.SIM_STATE_UNKNOWN -> false
            else -> true
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Upper-case country code, or null when unknown. networkCountryIso is "" without a network,
     * so an empty value falls through to the SIM country.
     */
    fun countryCode(context: Context): String? = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.networkCountryIso?.takeIf { it.isNotEmpty() }?.uppercase()
            ?: tm.simCountryIso?.takeIf { it.isNotEmpty() }?.uppercase()
    } catch (e: Exception) {
        null
    }
}
