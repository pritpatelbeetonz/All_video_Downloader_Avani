package app.messages.data.scheduling

import app.messages.core.scheduling.ScheduleRules
import app.messages.data.db.ScheduledMessageDao
import app.messages.data.db.entity.ScheduledMessageEntity
import app.messages.monetization.iap.EntitlementController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app-facing surface for scheduled sends (§F13). Enforces the [ScheduleRules]
 * cap (free = 3 active, Premium = unlimited), persists the row, and arms the
 * exact alarm. The Telephony provider is still the eventual store of record once
 * the message actually sends.
 */
@Singleton
class ScheduledMessageRepository @Inject constructor(
    private val dao: ScheduledMessageDao,
    private val scheduler: ScheduledMessageScheduler,
    private val executor: ScheduledSendExecutor,
    private val entitlement: EntitlementController,
) {
    /** Pending queue, soonest first. */
    val pending: Flow<List<ScheduledMessage>> =
        dao.observeByStatus(ScheduledStatus.PENDING.name)
            .map { rows -> rows.map(ScheduledMessageEntity::toModel) }

    val pendingCount: Flow<Int> = dao.countByStatus(ScheduledStatus.PENDING.name)

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun remainingSlots(activeCount: Int): Int =
        ScheduleRules.remainingSlots(entitlement.isPremium.value, activeCount)

    /**
     * Queues a send at [atMs]. Returns the rule outcome so the UI can show the
     * right message (cap reached → upsell, past time → reject).
     */
    suspend fun schedule(
        threadId: String,
        address: String,
        body: String,
        simSlot: Int?,
        atMs: Long,
    ): ScheduleRules.Check {
        val premium = entitlement.isPremium.value
        val active = dao.countByStatusNow(ScheduledStatus.PENDING.name)
        val check = ScheduleRules.validate(premium, active, atMs, System.currentTimeMillis())
        if (check != ScheduleRules.Check.Ok) return check

        val id = dao.insert(
            ScheduledMessageEntity(
                threadId = threadId,
                address = address,
                body = body,
                simSlot = simSlot,
                scheduledAtMs = atMs,
                status = ScheduledStatus.PENDING.name,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
        scheduler.schedule(id, atMs)
        return ScheduleRules.Check.Ok
    }

    /** Cancels a pending message and removes it from the queue. */
    suspend fun cancel(id: Long) {
        scheduler.cancel(id)
        dao.delete(id)
    }

    /** Moves a pending message to a new time. Editing never costs a new slot. */
    suspend fun reschedule(id: Long, newAtMs: Long): ScheduleRules.Check {
        val premium = entitlement.isPremium.value
        val active = dao.countByStatusNow(ScheduledStatus.PENDING.name)
        val check = ScheduleRules.validate(
            premium, (active - 1).coerceAtLeast(0), newAtMs, System.currentTimeMillis(),
        )
        if (check != ScheduleRules.Check.Ok) return check
        scheduler.cancel(id)
        dao.updateTime(id, newAtMs)
        scheduler.schedule(id, newAtMs)
        return ScheduleRules.Check.Ok
    }

    /** Sends a pending message right now, cancelling its alarm first. */
    suspend fun sendNow(id: Long) {
        scheduler.cancel(id)
        executor.execute(id)
    }
}
