package app.messages.utility

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.os.PowerManager
import app.messages.R
import app.messages.preferences.PreferenceHelper
import app.messages.receiver.NotificationActionReceiver
import app.messages.ui.activity.ChatActivity
import androidx.core.app.RemoteInput
import app.messages.enumeration.NotificationPreviewMode
import app.messages.enumeration.SwipeActions
import androidx.core.app.Person
import app.messages.data.db.LauncherDatabase
import app.messages.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationHelper {
    private const val CHANNEL_ID = "sms_notifications"
    private const val CHANNEL_NAME = "SMS Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications for incoming SMS messages"
    private const val REMOTE_INPUT_KEY_REPLY = "key_quick_reply"
    private val firstShownMap = mutableMapOf<Long, Long>()

    suspend fun showNotification(context: Context, address: String, body: String, threadId: Long) = withContext(Dispatchers.IO) {
        val prefs = PreferenceHelper.getInstance(context)
        if (!prefs.getNotificationsEnabled()) return@withContext

        createNotificationChannel(context)

        val db = LauncherDatabase.getLauncherDatabase(context)
        val conversation = db.conversationDao().getById(threadId)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications.any { it.id == threadId.toInt() }
        } else {
            false
        }

        if (!isVisible) {
            // Start a new session: only show messages from now onwards
            firstShownMap[threadId] = System.currentTimeMillis() - 1000 // Small buffer
        }

        val startTime = firstShownMap[threadId] ?: System.currentTimeMillis()

        val messages = db.messageDao().getMessagesForThreadSync(threadId)
            .filter { it.date >= startTime }
            .sortedBy { it.date }
            .takeLast(10)

        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("thread_id", threadId)
            putExtra("address", address)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val user = Person.Builder()
            .setName(context.getString(R.string.you))
            .build()

        val previewMode = prefs.getNotificationPreviewMode()

        val finalSenderName = when (previewMode) {
            NotificationPreviewMode.HIDE_CONTENTS.value -> context.getString(R.string.app_name)
            else -> conversation?.recipientName ?: address
        }

        val sender = Person.Builder()
            .setName(finalSenderName)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(user)
            .setConversationTitle(null) // SMS is usually 1-on-1

        if (previewMode == NotificationPreviewMode.SHOW_NAME_AND_MESSAGES.value) {
            for (msg in messages) {
                val person = if (msg.type == 2) user else sender
                messagingStyle.addMessage(msg.body, msg.date, person)
            }

            // Fallback: if somehow no messages are found in DB for the session, add the current one
            if (messages.isEmpty()) {
                messagingStyle.addMessage(body, System.currentTimeMillis(), sender)
            }
        } else {
            // For SHOW_NAME or HIDE_CONTENTS, just show one "New message" entry
            messagingStyle.addMessage(context.getString(R.string.new_message), System.currentTimeMillis(), sender)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_notification)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(threadId.toString())
            .setDeleteIntent(dismissPendingIntent(context, threadId))

        addConfiguredActions(context, builder, threadId, address)

        if (prefs.getNotificationWakeScreen()) {
            wakeScreen(context)
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(threadId.toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun wakeScreen(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "MessageApp:NotificationWakeLock"
            )
            wakeLock.acquire(3000) // 3 seconds
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addConfiguredActions(
        context: Context,
        builder: NotificationCompat.Builder,
        threadId: Long,
        address: String
    ) {
        val prefs = PreferenceHelper.getInstance(context)
        val actions = listOf(
            prefs.getNotificationActionButton(1),
            prefs.getNotificationActionButton(2),
            prefs.getNotificationActionButton(3)
        ).distinct().filter { it != SwipeActions.NONE.value }

        for (action in actions) {
            when (action) {
                SwipeActions.ARCHIVE.value -> builder.addAction(
                    0,
                    context.getString(R.string.archived),
                    actionPendingIntent(context, action, threadId, address)
                )
                SwipeActions.DELETE.value -> builder.addAction(
                    0,
                    context.getString(R.string.delete),
                    actionPendingIntent(context, action, threadId, address)
                )
                SwipeActions.BLOCK.value -> builder.addAction(
                    0,
                    context.getString(R.string.block),
                    actionPendingIntent(context, action, threadId, address)
                )
                SwipeActions.CALL.value -> builder.addAction(
                    0,
                    context.getString(R.string.call),
                    actionPendingIntent(context, action, threadId, address)
                )
                SwipeActions.MARK_READ.value -> builder.addAction(
                    0,
                    context.getString(R.string.mark_read),
                    actionPendingIntent(context, action, threadId, address)
                )
                SwipeActions.REPLY.value -> {
                    if (prefs.getNotificationQuickReplyEnabled()) {
                        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY_REPLY)
                            .setLabel(context.getString(R.string.reply))
                            .build()
                        val replyAction = NotificationCompat.Action.Builder(
                            0,
                            context.getString(R.string.reply),
                            actionPendingIntent(context, action, threadId, address)
                        ).addRemoteInput(remoteInput)
                            .setAllowGeneratedReplies(true)
                            .build()
                        builder.addAction(replyAction)
                    } else {
                        builder.addAction(
                            0,
                            context.getString(R.string.reply),
                            actionPendingIntent(context, action, threadId, address)
                        )
                    }
                }
            }
        }
    }

    fun clearSession(threadId: Long) {
        firstShownMap.remove(threadId)
    }

    private fun dismissPendingIntent(context: Context, threadId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTIFICATION_DISMISSED
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, threadId.toInt() + 1000, intent, flags)
    }

    private fun actionPendingIntent(
        context: Context,
        actionType: Int,
        threadId: Long,
        address: String
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTIFICATION_EVENT
            putExtra(NotificationActionReceiver.EXTRA_ACTION_TYPE, actionType)
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, threadId.toInt())
        }

        val requestCode = (threadId.toInt() * 10) + actionType
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
