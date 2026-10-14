package app.messages.data.telephony

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide push channel for telephony events. Replaces what the Flutter
 * version did with `EventChannel` — receivers and the ContentObserver emit
 * into [emit], repositories subscribe to [events].
 *
 * Buffer is generous so a flurry of provider notifications never drops an
 * event silently; if the consumer is slow we'd rather accept extra requeries
 * than miss a "message_received".
 */
@Singleton
class TelephonyEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<TelephonyEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    val events: SharedFlow<TelephonyEvent> = _events.asSharedFlow()

    fun emit(event: TelephonyEvent) {
        _events.tryEmit(event)
    }
}

sealed interface TelephonyEvent {
    /** ContentObserver fired — caller should requery. */
    data object InboxChanged : TelephonyEvent

    /** New SMS/MMS arrived. Carries identifiers for targeted updates. */
    data class MessageReceived(val threadId: String, val messageId: String) : TelephonyEvent

    /** Send pipeline status update for a single SMS part. */
    data class SendStatus(
        val messageId: String,
        val part: Int,
        val status: SendStatusKind,
    ) : TelephonyEvent
}

enum class SendStatusKind { Pending, Sent, Delivered, Failed }
