package app.messages.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.messages.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessageStatusReceiver : BroadcastReceiver() {
    companion object {
        const val DELIVERY_INTENT_ACTION = "app.messages.SMS_DELIVERED"
        const val SENT_INTENT_ACTION = "app.messages.SMS_SENT"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val action = intent?.action ?: return
        val msgId = intent.getLongExtra("message_id", -1L)
        if (msgId == -1L) return

        val currentResultCode = resultCode
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    DELIVERY_INTENT_ACTION -> {
                        if (currentResultCode == Activity.RESULT_OK) {
                            val db = AppDatabase.getDatabase(ctx)
                            db.messageDao().updateDeliveryStatus(msgId, 6, System.currentTimeMillis())
                            }
                    }
                    SENT_INTENT_ACTION -> {
                        val db = AppDatabase.getDatabase(ctx)
                        val systemUriStr = intent.getStringExtra("system_uri")
                        val systemUri = if (systemUriStr != null) android.net.Uri.parse(systemUriStr) else null
                        
                        if (currentResultCode == Activity.RESULT_OK) {
                            val msg = db.messageDao().getMessageByIdSync(msgId)
                            if (msg != null && msg.status < 2) {
                                db.messageDao().updateStatus(msgId, 2) // 2 = Sent
                            }
                            
                            // Also update the Android System Provider so it moves out of OUTBOX
                            if (systemUri != null) {
                                try {
                                    val values = android.content.ContentValues().apply {
                                        put(android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.MESSAGE_TYPE_SENT)
                                    }
                                    ctx.contentResolver.update(systemUri, values, null, null)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            db.messageDao().updateStatus(msgId, 3) // 3 = Failed
                            
                            if (systemUri != null) {
                                try {
                                    val values = android.content.ContentValues().apply {
                                        put(android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED)
                                    }
                                    ctx.contentResolver.update(systemUri, values, null, null)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
