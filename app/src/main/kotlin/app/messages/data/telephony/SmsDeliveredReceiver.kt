package app.messages.data.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fires once per SMS part when the carrier confirms delivery to the
 * recipient handset. Updates the row's `status` column and forwards to the
 * UI via [TelephonyEventBus].
 */
@AndroidEntryPoint
class SmsDeliveredReceiver : BroadcastReceiver() {

    @Inject lateinit var eventBus: TelephonyEventBus

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val messageId = intent.getStringExtra(SmsSender.EXTRA_MESSAGE_ID).orEmpty()
        val part = intent.getIntExtra(SmsSender.EXTRA_PART_INDEX, 0)
        val status = if (resultCode == Activity.RESULT_OK) SendStatusKind.Delivered else SendStatusKind.Failed

        if (messageId.isNotEmpty()) {
            val values = ContentValues().apply {
                put(
                    Telephony.Sms.STATUS,
                    if (status == SendStatusKind.Delivered) Telephony.Sms.STATUS_COMPLETE
                    else Telephony.Sms.STATUS_FAILED,
                )
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "_id=?",
                arrayOf(messageId),
            )
        }
        eventBus.emit(TelephonyEvent.SendStatus(messageId, part, status))
    }

    companion object {
        const val ACTION = "app.messages.SMS_DELIVERED"
    }
}
