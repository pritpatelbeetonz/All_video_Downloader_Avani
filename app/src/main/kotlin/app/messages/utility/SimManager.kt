package app.messages.utility

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.messages.R

data class SimInfo(
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String?,
    val subscriptionId: Long,
    val isActive: Boolean
)

class SimManager(private val context: Context) {
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun getAvailableSims(): List<SimInfo> {
        val sims = mutableListOf<SimInfo>()
        
        try {
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            activeSubscriptions?.forEach { subscriptionInfo ->
                val simInfo = SimInfo(
                    slotIndex = subscriptionInfo.simSlotIndex,
                    displayName = subscriptionInfo.displayName?.toString() ?: "SIM ${subscriptionInfo.simSlotIndex + 1}",
                    carrierName = subscriptionInfo.carrierName?.toString() ?: "Unknown Carrier",
                    number = subscriptionInfo.number?.toString(),
                    subscriptionId = subscriptionInfo.subscriptionId.toLong(),
                    isActive = getIsActiveStatus(subscriptionInfo)
                )
                sims.add(simInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return sims
    }
    
    fun getSimForSubscriptionId(subscriptionId: Long?): SimInfo? {
        return getAvailableSims().find { it.subscriptionId == subscriptionId }
    }
    
    fun getSimForSlotIndex(slotIndex: Int): SimInfo? {
        return getAvailableSims().find { it.slotIndex == slotIndex }
    }
    
    fun getDefaultSim(): SimInfo? {
        val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return getSimForSubscriptionId(defaultSubId.toLong())
        }
        return null
    }
    
    fun getSimDisplayText(slotIndex: Int?): String {
        return when (slotIndex) {
            0 -> "SIM 1"
            1 -> "SIM 2"
            2 -> "SIM 3"
            else -> "Unknown SIM"
        }
    }
    
    fun getSimDisplayTextWithCarrier(slotIndex: Int?, carrierName: String?): String {
        val simText = getSimDisplayText(slotIndex)
        return if (carrierName != null && carrierName.isNotEmpty()) {
            "$simText ($carrierName)"
        } else {
            simText
        }
    }
    
    fun hasMultipleSims(): Boolean {
        return getAvailableSims().size > 1
    }
    
    fun openSimSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getSimColor(slotIndex: Int): Int {
        return when (slotIndex) {
            0 -> ContextCompat.getColor(context, R.color.sim1_color)
            1 -> ContextCompat.getColor(context, R.color.sim2_color)
            2 -> ContextCompat.getColor(context, R.color.sim3_color)
            else -> ContextCompat.getColor(context, R.color.sim_default_color)
        }
    }
    
    private fun getIsActiveStatus(subscriptionInfo: SubscriptionInfo): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Use reflection to access isActive property on Android 12+
                val isActiveMethod = SubscriptionInfo::class.java.getMethod("isActive")
                isActiveMethod.invoke(subscriptionInfo) as Boolean
            } else {
                // For older Android versions, assume SIM is active if it has a valid subscription ID
                subscriptionInfo.subscriptionId > 0
            }
        } catch (e: Exception) {
            // Fallback: assume active if subscription ID is valid
            subscriptionInfo.subscriptionId > 0
        }
    }
}