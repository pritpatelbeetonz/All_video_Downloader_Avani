package app.messages.data.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fires once per SMS part when the radio has acknowledged (or failed) the
 * send. Updates the row's `type` column and forwards the result via
 * [TelephonyEventBus].
 */
@AndroidEntryPoint
class SmsSentReceiver : BroadcastReceiver() {

    @Inject lateinit var eventBus: TelephonyEventBus

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val messageId = intent.getStringExtra(SmsSender.EXTRA_MESSAGE_ID).orEmpty()
        val part = intent.getIntExtra(SmsSender.EXTRA_PART_INDEX, 0)

        val status = when (resultCode) {
            Activity.RESULT_OK -> SendStatusKind.Sent
            SmsManager.RESULT_ERROR_NO_SERVICE,
            SmsManager.RESULT_ERROR_NULL_PDU,
            SmsManager.RESULT_ERROR_RADIO_OFF,
            SmsManager.RESULT_ERROR_GENERIC_FAILURE,
            -> SendStatusKind.Failed
            else -> SendStatusKind.Failed
        }

        updateSmsRowType(context, messageId, status)
        eventBus.emit(TelephonyEvent.SendStatus(messageId, part, status))
    }

    private fun updateSmsRowType(context: Context, messageId: String, status: SendStatusKind) {
        if (messageId.isEmpty()) return
        val type = when (status) {
            SendStatusKind.Sent -> Telephony.Sms.MESSAGE_TYPE_SENT
            SendStatusKind.Failed -> Telephony.Sms.MESSAGE_TYPE_FAILED
            else -> Telephony.Sms.MESSAGE_TYPE_OUTBOX
        }
        val values = ContentValues().apply { put(Telephony.Sms.TYPE, type) }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "_id=?",
            arrayOf(messageId),
        )
    }

    companion object {
        const val ACTION = "app.messages.SMS_SENT"
    }
}
