package app.messages.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import app.messages.data.SyncManager
import app.messages.data.db.AppDatabase
import app.messages.data.db.ArchivedThreadEntity
import app.messages.enumeration.SwipeActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NOTIFICATION_DISMISSED) {
            val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
            app.messages.utility.NotificationHelper.clearSession(threadId)
            return
        }
        if (intent.action != ACTION_NOTIFICATION_EVENT) return

        val actionType = intent.getIntExtra(EXTRA_ACTION_TYPE, SwipeActions.NONE.value)
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, threadId.toInt())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (actionType) {
                    SwipeActions.REPLY.value -> handleReply(context, intent, address, threadId)
                    SwipeActions.MARK_READ.value -> markRead(context, threadId)
                    SwipeActions.ARCHIVE.value -> archiveThread(context, threadId)
                    SwipeActions.DELETE.value -> deleteThread(context, threadId)
                    SwipeActions.BLOCK.value -> blockContact(context, address, threadId)
                    SwipeActions.CALL.value -> {
                        callContact(context, address)
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReply(context: Context, intent: Intent, address: String, threadId: Long) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_QUICK_REPLY)?.toString()?.trim().orEmpty()
        if (replyText.isBlank() || address.isBlank()) return

        val now = System.currentTimeMillis()

        try {
            // 1. Persist to system provider
            val values = android.content.ContentValues().apply {
                put(android.provider.Telephony.Sms.ADDRESS, address)
                put(android.provider.Telephony.Sms.BODY, replyText)
                put(android.provider.Telephony.Sms.DATE, now)
                put(android.provider.Telephony.Sms.READ, 1)
                put(android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.MESSAGE_TYPE_SENT)
                put(android.provider.Telephony.Sms.THREAD_ID, threadId)
            }
            val insertedUri = context.contentResolver.insert(android.provider.Telephony.Sms.Sent.CONTENT_URI, values)
            val systemMsgId = insertedUri?.lastPathSegment?.toLongOrNull() ?: now

            // 2. Insert into local DB
            val db = AppDatabase.getDatabase(context)
            val msg = app.messages.data.db.MessageEntity(
                id = systemMsgId,
                threadId = threadId,
                address = app.messages.utility.AddressUtils.normalizeAddress(address),
                body = replyText,
                date = now,
                type = 2, // Sent
                read = true,
                status = 1 // Sending
            )
            db.messageDao().insert(msg)

            // Update conversation snippet
            val existing = db.conversationDao().getById(threadId)
            val updatedConversation = app.messages.data.db.ConversationEntity(
                threadId = threadId,
                address = address,
                body = replyText,
                date = now,
                read = true,
                messageCount = (existing?.messageCount ?: 0) + 1,
                recipientName = existing?.recipientName,
                photoUri = existing?.photoUri,
                lastMsgType = 2,
                isSaved = existing?.isSaved ?: false,
                isPinned = existing?.isPinned ?: false,
                normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)
            )
            db.conversationDao().insert(updatedConversation)

            // 3. Send the actual SMS
            val sentIntent = PendingIntent.getBroadcast(
                context,
                systemMsgId.toInt(),
                Intent(MessageStatusReceiver.SENT_INTENT_ACTION).apply {
                    putExtra("message_id", systemMsgId)
                    putExtra("system_uri", insertedUri?.toString())
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Determine which SIM to use based on the last message in the thread
            val lastMsg = db.messageDao().getLastMessageForThreadSync(threadId)
            val subId = lastMsg?.subscriptionId?.toInt() ?: SmsManager.getDefaultSmsSubscriptionId()

            val smsManager = if (subId != -1 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                }
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(address, null, replyText, sentIntent, null)

            // 4. Update the notification to show the thread including the reply
            app.messages.utility.NotificationHelper.showNotification(context, address, replyText, threadId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun markRead(context: Context, threadId: Long) {
        if (threadId <= 0) return
        try {
            SyncManager(context).markThreadAsRead(threadId)
            app.messages.utility.NotificationHelper.clearSession(threadId)
            NotificationManagerCompat.from(context).cancel(threadId.toInt())
        } catch (_: Exception) {
        }
    }

    private suspend fun archiveThread(context: Context, threadId: Long) {
        if (threadId <= 0) return
        try {
            val db = AppDatabase.getDatabase(context)
            db.archivedThreadDao().insert(ArchivedThreadEntity(threadId))
            SyncManager(context).updateSystemArchiveStatus(threadId, true)
            app.messages.utility.NotificationHelper.clearSession(threadId)
            NotificationManagerCompat.from(context).cancel(threadId.toInt())
        } catch (_: Exception) {
        }
    }

    private suspend fun deleteThread(context: Context, threadId: Long) {
        if (threadId <= 0) return
        try {
            val uri = Uri.parse("content://mms-sms/conversations/$threadId")
            context.contentResolver.delete(uri, null, null)
            val db = AppDatabase.getDatabase(context)
            db.conversationDao().deleteByThreadId(threadId)
            db.messageDao().deleteByThreadId(threadId)
            app.messages.utility.NotificationHelper.clearSession(threadId)
            NotificationManagerCompat.from(context).cancel(threadId.toInt())
        } catch (_: Exception) {
        }
    }

    private suspend fun blockContact(context: Context, address: String, threadId: Long) {
        if (address.isBlank()) return
        try {
            val db = AppDatabase.getDatabase(context)
            db.blockedContactDao().insert(app.messages.data.db.BlockedContactEntity(address))
            SyncManager(context).updateSystemBlockStatus(address, true)
            if (threadId > 0) {
                db.conversationDao().deleteByThreadId(threadId)
                app.messages.utility.NotificationHelper.clearSession(threadId)
                NotificationManagerCompat.from(context).cancel(threadId.toInt())
            }
        } catch (_: Exception) {
        }
    }

    private fun callContact(context: Context, address: String) {
        if (address.isBlank()) return
        try {
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$address")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_EVENT = "app.messages.NOTIFICATION_EVENT"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val ACTION_NOTIFICATION_DISMISSED = "app.messages.NOTIFICATION_DISMISSED"
        private const val KEY_QUICK_REPLY = "key_quick_reply"
    }
}
