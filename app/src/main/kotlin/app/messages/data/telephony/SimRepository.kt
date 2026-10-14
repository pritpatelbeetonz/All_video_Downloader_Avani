package app.messages.data.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import android.Manifest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A selectable SIM for outgoing messages. */
data class SimInfo(
    val subscriptionId: Int,
    val label: String,
    val slotIndex: Int,
)

/**
 * Enumerates active SIMs for the composer's SIM selector (dual-SIM, build doc
 * §F4). Returns an empty list on single-SIM devices or when the read fails —
 * the composer hides the SIM chip in that case and sends on the system default.
 */
@Singleton
class SimRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun activeSims(): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map { info ->
                val label = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM ${info.simSlotIndex + 1}"
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    label = label,
                    slotIndex = info.simSlotIndex,
                )
            }
        }.getOrDefault(emptyList())
    }
}
