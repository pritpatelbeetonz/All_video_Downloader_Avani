package app.messages.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.messages.data.telephony.ConversationQuery
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.concurrent.thread

/** Handles the "Mark as read" notification action — clears unread + dismisses. */
@AndroidEntryPoint
class MarkReadReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: MessageNotifier
    @Inject lateinit var query: ConversationQuery

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra(MessageNotifier.EXTRA_OPEN_THREAD).orEmpty()
        if (threadId.isBlank()) return
        val pending = goAsync()
        thread {
            try {
                runCatching { query.markThreadRead(threadId) }
                notifier.cancel(threadId)
            } finally {
                pending.finish()
            }
        }
    }
}
