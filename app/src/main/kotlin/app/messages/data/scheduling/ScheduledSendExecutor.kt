package app.messages.data.scheduling

import app.messages.data.db.ScheduledMessageDao
import app.messages.data.db.entity.ScheduledMessageEntity
import app.messages.data.telephony.SmsSender
import app.messages.notifications.ScheduledNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs a single scheduled send and records the outcome. Shared by the alarm
 * receiver, the boot re-arm path, and the "send now" action so the send + status
 * + result-notification logic lives in exactly one place.
 */
@Singleton
class ScheduledSendExecutor @Inject constructor(
    private val dao: ScheduledMessageDao,
    private val sender: SmsSender,
    private val notifier: ScheduledNotifier,
) {
    /** Sends the row with [id] if it still exists and is PENDING. */
    suspend fun execute(id: Long) {
        val row = dao.byId(id) ?: return
        if (row.status != ScheduledStatus.PENDING.name) return
        send(row)
    }

    /** Sends a known-pending row, marking SENT/FAILED and notifying the user. */
    suspend fun send(row: ScheduledMessageEntity) {
        try {
            sender.send(row.address, row.body, row.simSlot)
            dao.updateStatus(row.id, ScheduledStatus.SENT.name)
            notifier.notifySent(row.id, row.threadId, row.address, row.body)
        } catch (t: Throwable) {
            dao.updateStatus(row.id, ScheduledStatus.FAILED.name)
            notifier.notifyFailed(row.id, row.threadId, row.address, row.body)
        }
    }
}
