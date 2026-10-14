package app.messages.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import app.messages.data.db.LauncherDatabase
import app.messages.data.db.LauncherScheduledMessageEntity
import app.messages.data.db.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.klinker.android.send_message.Message
import android.graphics.BitmapFactory
import android.app.PendingIntent
import app.messages.receiver.MessageStatusReceiver
import android.util.Log

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduledId = intent.getLongExtra("scheduled_id", -1L)
        if (scheduledId == -1L) return

        val pendingResult = goAsync()
        val db = LauncherDatabase.getLauncherDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduledMessage = db.launcherScheduledMessageDao().getByIdSync(scheduledId)
                if (scheduledMessage != null && scheduledMessage.status == 0) {
                    try {
                        if (!scheduledMessage.imageUri.isNullOrEmpty()) {
                            sendScheduledMms(context, scheduledMessage)
                        } else {
                            sendScheduledSms(context, scheduledMessage)
                        }
                        db.launcherScheduledMessageDao().updateStatus(scheduledId, 1) // Sent in schedule table
                    } catch (e: Exception) {
                        e.printStackTrace()
                        db.launcherScheduledMessageDao().updateStatus(scheduledId, 2) // Failed in schedule table
                        val localId = scheduledMessage.localMessageId
                        if (localId != null) {
                            db.messageDao().updateStatus(localId, 3) // Failed in chat bubble
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun sendScheduledSms(context: Context, message: LauncherScheduledMessageEntity) {
        val smsManager = if (message.subscriptionId != null && message.subscriptionId > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(message.subscriptionId.toInt())
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(message.subscriptionId.toInt())
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }

        if (smsManager == null) {
            return
        }

        val now = System.currentTimeMillis()
        val localId = message.localMessageId ?: now

        val sentIntent = Intent(MessageStatusReceiver.SENT_INTENT_ACTION).apply {
            putExtra("message_id", localId)
            setPackage(context.packageName)
        }
        val sentPI = PendingIntent.getBroadcast(
            context,
            localId.toInt(),
            sentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val deliveryPI = if (app.messages.preferences.PreferenceHelper.getInstance(context).getDeliveryReportsEnabled()) {
            val deliveryIntent = Intent(MessageStatusReceiver.DELIVERY_INTENT_ACTION).apply {
                putExtra("message_id", localId)
                setPackage(context.packageName)
            }
            PendingIntent.getBroadcast(
                context,
                localId.toInt() + 1,
                deliveryIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val parts = smsManager.divideMessage(message.body)
        try {
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>().apply { repeat(parts.size) { add(sentPI) } }
                val deliveryIntents = deliveryPI?.let { pi -> ArrayList<PendingIntent>().apply { repeat(parts.size) { add(pi) } } }
                smsManager.sendMultipartTextMessage(message.address, null, parts, sentIntents, deliveryIntents)
            } else {
                smsManager.sendTextMessage(message.address, null, message.body, sentPI, deliveryPI)
            }
            } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

        val db = LauncherDatabase.getLauncherDatabase(context)

        // Set status to Sending and update date to now for deduplication in SyncManager
        val localMsgId = message.localMessageId
        if (localMsgId != null) {
            db.messageDao().updateDateAndStatus(localMsgId, now, 1) // 1 = Sending
        }

        // If for some reason it wasn't in MessageEntity yet, we insert it (fallback)
        if (message.localMessageId == null) {
            val localMsg = MessageEntity(
                id = now,
                threadId = message.threadId,
                address = message.address,
                body = message.body,
                date = now,
                type = 2, // Sent
                read = true,
                status = 2, // Sent
                imageUri = null,
                subscriptionId = message.subscriptionId,
                simSlot = message.simSlot
            )
            db.messageDao().insert(localMsg)
        }
        
        // Write to system provider as SENT
        writeToSystemProvider(context, message.address, message.body, message.threadId, now, Telephony.Sms.MESSAGE_TYPE_SENT)
    }

    private suspend fun sendScheduledMms(context: Context, message: LauncherScheduledMessageEntity) {
        val settings = Settings()
        settings.setUseSystemSending(true)
        if (message.subscriptionId != null) {
            settings.setSubscriptionId(message.subscriptionId.toInt())
        }

        val mmsMessage = Message(message.body, message.address)
        
        try {
            val uri = Uri.parse(message.imageUri)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                mmsMessage.addImage(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val now = System.currentTimeMillis()
        val transaction = Transaction(context, settings)
        // Library uses long msgId for callback tracking
        val msgId = message.localMessageId ?: now
        transaction.sendNewMessage(mmsMessage, msgId)

        val db = LauncherDatabase.getLauncherDatabase(context)
        val localMsgId = message.localMessageId
        if (localMsgId != null) {
            db.messageDao().updateDateAndStatus(localMsgId, now, 1) // 1 = Sending
        }
    }

    private fun writeToSystemProvider(context: Context, address: String, body: String, threadId: Long, date: Long, type: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, type)
                put(Telephony.Sms.THREAD_ID, threadId)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
