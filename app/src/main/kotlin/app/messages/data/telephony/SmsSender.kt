package app.messages.data.telephony

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS send pipeline. Owns the [SmsManager] call + the two [PendingIntent]s
 * that fire SENT and DELIVERED status updates, which [SmsSentReceiver] /
 * [SmsDeliveredReceiver] forward back to the UI via [TelephonyEventBus].
 *
 * Multipart support: messages over the 160 GSM-7 / 70 UCS-2 limit are split
 * with [SmsManager.divideMessage]. Per build doc §F4, the character/segment
 * counter the composer shows is derived from the part count.
 */
@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Persists the outgoing row, then dispatches it. Returns the new row's
     * `_id` so the UI can track delivery status across [SendStatusEvent]s.
     */
    fun send(
        address: String,
        body: String,
        simSlot: Int? = null,
        requestDeliveryReport: Boolean = false,
    ): String {
        val uri = persistOutgoing(address, body, simSlot)
        val messageId = uri?.lastPathSegment ?: ""

        val smsManager: SmsManager = if (simSlot != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(simSlot)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(body)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        // Only request delivery reports when the user opted in: many carriers
        // never return one, which would leave a sent message looking unconfirmed.
        val deliveredIntents =
            if (requestDeliveryReport) ArrayList<PendingIntent>(parts.size) else null
        for (i in parts.indices) {
            sentIntents.add(buildIntent(SmsSentReceiver::class.java, SmsSentReceiver.ACTION, messageId, i))
            deliveredIntents?.add(
                buildIntent(SmsDeliveredReceiver::class.java, SmsDeliveredReceiver.ACTION, messageId, i),
            )
        }

        if (parts.size == 1) {
            smsManager.sendTextMessage(
                address, null, parts[0], sentIntents[0], deliveredIntents?.get(0),
            )
        } else {
            smsManager.sendMultipartTextMessage(
                address, null, parts, sentIntents, deliveredIntents,
            )
        }
        return messageId
    }

    private fun persistOutgoing(address: String, body: String, simSlot: Int?): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            if (simSlot != null) put("sub_id", simSlot)
        }
        return context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    private fun buildIntent(
        cls: Class<out BroadcastReceiver>,
        action: String,
        messageId: String,
        part: Int,
    ): PendingIntent {
        // Target the receiver class explicitly. The SENT/DELIVERED receivers carry
        // no intent-filter, so an action-only broadcast (even package-scoped)
        // resolves to no receiver — the status callback is silently dropped and the
        // message stays stuck "sending". An explicit component always delivers.
        val intent = Intent(context, cls).apply {
            setAction(action)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_PART_INDEX, part)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = (messageId.hashCode() xor part).and(0x7fffffff)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PART_INDEX = "part_index"
    }
}
