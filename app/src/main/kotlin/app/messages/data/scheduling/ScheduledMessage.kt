package app.messages.data.scheduling

import app.messages.data.db.entity.ScheduledMessageEntity

/** Lifecycle of a scheduled send. Stored as [name] in the Room row. */
enum class ScheduledStatus { PENDING, SENT, FAILED, CANCELED }

/** UI/domain view of a [ScheduledMessageEntity] with a typed status. */
data class ScheduledMessage(
    val id: Long,
    val threadId: String,
    val address: String,
    val body: String,
    val simSlot: Int?,
    val scheduledAtMs: Long,
    val status: ScheduledStatus,
    val createdAtMs: Long,
)

fun ScheduledMessageEntity.toModel(): ScheduledMessage = ScheduledMessage(
    id = id,
    threadId = threadId,
    address = address,
    body = body,
    simSlot = simSlot,
    scheduledAtMs = scheduledAtMs,
    status = runCatching { ScheduledStatus.valueOf(status) }.getOrDefault(ScheduledStatus.PENDING),
    createdAtMs = createdAtMs,
)
