package app.messages.data.telephony

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.klinker.android.send_message.MmsSentReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Captures the real result of an outgoing MMS. The klinker library base
 * ([MmsSentReceiver] → StatusUpdatedReceiver) updates the message's box in the
 * provider (sent vs failed) from the sent-PendingIntent result; we additionally
 * log the outcome and nudge the thread so the bubble reflects accurate status
 * instead of an optimistic tick.
 */
class AppMmsSentReceiver : MmsSentReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun eventBus(): TelephonyEventBus
    }

    override fun onMessageStatusUpdated(context: Context, intent: Intent, resultCode: Int) {
        val ok = resultCode == Activity.RESULT_OK
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
                .eventBus()
                .emit(TelephonyEvent.InboxChanged)
        }
    }
}
