package app.messages.data.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by [ScheduledMessageScheduler]'s exact alarm when a scheduled message is
 * due. Runs even if no UI process is alive (the same way [app.messages.notifications.ReplyReceiver]
 * sends from a notification). [goAsync] keeps the receiver alive for the off-main
 * send work.
 */
@AndroidEntryPoint
class ScheduledSendReceiver : BroadcastReceiver() {

    @Inject lateinit var executor: ScheduledSendExecutor

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                executor.execute(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEND = "app.messages.action.SCHEDULED_SEND"
        const val EXTRA_ID = "scheduled_id"
    }
}
