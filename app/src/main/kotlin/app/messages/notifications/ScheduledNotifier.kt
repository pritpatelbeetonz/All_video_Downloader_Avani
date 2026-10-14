package app.messages.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.messages.MainActivity
import app.messages.R
import app.messages.data.contacts.ContactsResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the result notification after a scheduled send fires (§7.3 "posts result
 * notification"). Lives on its own default-importance channel so confirmations
 * don't compete with incoming-message heads-up alerts. Tapping opens the thread.
 */
@Singleton
class ScheduledNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactsResolver,
) {
    fun notifySent(id: Long, threadId: String, address: String, body: String) =
        post(id, threadId, address, body, title = "Scheduled message sent")

    fun notifyFailed(id: Long, threadId: String, address: String, body: String) =
        post(id, threadId, address, body, title = "Couldn't send scheduled message")

    private fun post(id: Long, threadId: String, address: String, body: String, title: String) {
        NotificationChannels.ensure(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val name = contacts.resolve(address).displayName ?: address
        val line = "To $name: $body"
        val builder = NotificationCompat.Builder(context, NotificationChannels.SCHEDULED)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(threadId))

        try {
            nm.notify(NOTIFICATION_BASE + id.toInt(), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the notify.
        }
    }

    private fun contentIntent(threadId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MessageNotifier.EXTRA_OPEN_THREAD, threadId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, "sched$threadId".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val NOTIFICATION_BASE = 900_000
    }
}
