package app.messages.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.messages.data.telephony.ConversationQuery
import app.messages.data.telephony.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Handles the inline "Reply" action from a message notification — journey #1.
 *
 * Extracts the typed reply via [RemoteInput], sends it through [SmsSender],
 * marks the thread read, and re-posts the notification so the shade shows the
 * sent reply. No UI is shown; the reply path never surfaces an ad.
 */
@AndroidEntryPoint
class ReplyReceiver : BroadcastReceiver() {

    @Inject lateinit var sender: SmsSender
    @Inject lateinit var notifier: MessageNotifier
    @Inject lateinit var query: ConversationQuery

    override fun onReceive(context: Context, intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MessageNotifier.KEY_REPLY)?.toString()?.trim()
        val threadId = intent.getStringExtra(MessageNotifier.EXTRA_OPEN_THREAD).orEmpty()
        val address = intent.getStringExtra(MessageNotifier.EXTRA_ADDRESS).orEmpty()
        if (reply.isNullOrBlank() || address.isBlank()) return

        val pending = goAsync()
        thread {
            try {
                sender.send(address, reply)
                runCatching { query.markThreadRead(threadId) }
                notifier.notifyOwnReply(threadId, address, reply)
            } finally {
                pending.finish()
            }
        }
    }
}
