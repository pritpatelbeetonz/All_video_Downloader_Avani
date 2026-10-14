package app.messages.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import app.messages.MainActivity
import app.messages.R
import app.messages.core.prefs.AppPrefs
import app.messages.data.contacts.ContactsResolver
import app.messages.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Builds and posts MessagingStyle notifications for incoming SMS, plus the
 * inline-reply and mark-as-read actions — build doc §F7.
 *
 * Keeps a small per-thread message history in memory so the shade shows a
 * proper conversation (incoming + your sent replies) rather than a single
 * line. History is bounded; it's a display convenience, not a store of record
 * (the Telephony provider remains the source of truth).
 *
 * Honors the in-app notification preferences ([AppPrefs.notificationsEnabled],
 * [AppPrefs.messagePreview]). They're collected once into volatile fields so the
 * non-suspend post path never blocks; both default ON so the first notification
 * before DataStore emits behaves correctly.
 *
 * This is journey #1 (notification → reply). It is ad-free by construction and
 * `AdRules` separately forbids an app-open ad on the notification-tap entry.
 */
@Singleton
class MessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactsResolver,
    prefs: AppPrefs,
    @ApplicationScope scope: CoroutineScope,
) {
    private data class Entry(val text: String, val time: Long, val fromSelf: Boolean)

    private val histories = HashMap<String, ArrayDeque<Entry>>()
    private val selfPerson = Person.Builder().setName("You").build()

    @Volatile private var notificationsEnabled = true
    @Volatile private var messagePreview = true

    init {
        scope.launch { prefs.notificationsEnabled.collect { notificationsEnabled = it } }
        scope.launch { prefs.messagePreview.collect { messagePreview = it } }
    }

    fun notifyIncoming(threadId: String, address: String, body: String, timestampMillis: Long) {
        append(threadId, Entry(body, timestampMillis, fromSelf = false))
        post(threadId, address)
    }

    /** Called after an inline reply is sent so the shade reflects it. */
    fun notifyOwnReply(threadId: String, address: String, body: String) {
        append(threadId, Entry(body, System.currentTimeMillis(), fromSelf = true))
        post(threadId, address)
    }

    fun cancel(threadId: String) {
        histories.remove(threadId)
        NotificationManagerCompat.from(context).cancel(notificationId(threadId))
    }

    private fun append(threadId: String, entry: Entry) {
        val deque = histories.getOrPut(threadId) { ArrayDeque() }
        deque.addLast(entry)
        while (deque.size > MAX_HISTORY) deque.removeFirst()
    }

    private fun post(threadId: String, address: String) {
        NotificationChannels.ensure(context)
        val nm = NotificationManagerCompat.from(context)
        // In-app master switch + the OS-level toggle both gate posting.
        if (!notificationsEnabled || !nm.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(threadId))
            .addAction(replyAction(threadId, address))
            .addAction(markReadAction(threadId))
            .setOnlyAlertOnce(false)

        if (messagePreview) {
            val senderName = contacts.resolve(address).displayName ?: address
            val sender = Person.Builder().setName(senderName).setKey(address).build()
            val style = NotificationCompat.MessagingStyle(selfPerson)
                .setConversationTitle(if (address != senderName) senderName else null)
            histories[threadId]?.forEach { e ->
                style.addMessage(e.text, e.time, if (e.fromSelf) selfPerson else sender)
            }
            builder.setStyle(style)
        } else {
            // Preview off: no sender or body, on the lock screen or in the shade.
            builder
                .setContentTitle("New message")
                .setContentText("Tap to view")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        }

        try {
            nm.notify(notificationId(threadId), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the notify.
        }
    }

    private fun contentIntent(threadId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_THREAD, threadId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, threadId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun replyAction(threadId: String, address: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
        val intent = Intent(context, ReplyReceiver::class.java).apply {
            putExtra(EXTRA_OPEN_THREAD, threadId)
            putExtra(EXTRA_ADDRESS, address)
        }
        // MUTABLE so the system can inject the typed reply into the intent.
        val pending = PendingIntent.getBroadcast(
            context, threadId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_message, "Reply", pending,
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()
    }

    private fun markReadAction(threadId: String): NotificationCompat.Action {
        val intent = Intent(context, MarkReadReceiver::class.java).apply {
            putExtra(EXTRA_OPEN_THREAD, threadId)
        }
        val pending = PendingIntent.getBroadcast(
            context, threadId.hashCode() + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_message, "Mark as read", pending,
        ).build()
    }

    private fun notificationId(threadId: String): Int = threadId.hashCode()

    companion object {
        const val EXTRA_OPEN_THREAD = "open_thread_id"
        const val EXTRA_ADDRESS = "address"
        const val KEY_REPLY = "key_reply"
        private const val MAX_HISTORY = 20
    }
}
