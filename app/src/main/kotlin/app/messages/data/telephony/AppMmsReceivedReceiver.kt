package app.messages.data.telephony

import android.content.Context
import android.net.Uri
import android.util.Log
import app.messages.notifications.MessageNotifier
import com.klinker.android.send_message.MmsReceivedReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Handles an MMS that the klinker library has downloaded + persisted to the
 * provider (build doc §F5 receive path). The library's [MmsReceivedReceiver]
 * base does the WAP-push parse → `SmsManager.downloadMultimediaMessage` →
 * `PduPersister` work; we just react to the persisted result here.
 *
 * Dependencies are pulled via a Hilt [EntryPoint] from the application context
 * rather than `@AndroidEntryPoint` field injection, because [onMessageReceived]
 * is invoked from the library's async task — fetching from the app graph at
 * call time is lifecycle-safe regardless of the receiver instance.
 */
class AppMmsReceivedReceiver : MmsReceivedReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun eventBus(): TelephonyEventBus
        fun notifier(): MessageNotifier
        fun mmsQuery(): MmsQuery
        fun blocking(): app.messages.data.blocking.BlockingRepository
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val info = runCatching { deps.mmsQuery().notifyInfo(messageUri) }.getOrNull() ?: return

        deps.eventBus().emit(
            TelephonyEvent.MessageReceived(threadId = info.threadId, messageId = messageUri.toString()),
        )
        // Same blocked-sender rule as SMS: persisted but no notification.
        if (info.address.isNotBlank() && !deps.blocking().isBlocked(info.address)) {
            deps.notifier().notifyIncoming(
                threadId = info.threadId,
                address = info.address,
                body = info.snippet,
                timestampMillis = System.currentTimeMillis(),
            )
        }
    }

    override fun onError(context: Context, error: String) {
        }
}
