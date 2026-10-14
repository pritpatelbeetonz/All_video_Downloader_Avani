package app.messages.ui.thread

import app.messages.core.time.formatDaySeparator
import app.messages.core.time.isDifferentDay
import app.messages.data.telephony.model.SmsMessage
import java.util.Date

/** A row in the thread list — either a day separator or a message bubble. */
sealed interface ThreadItem {
    data class DaySeparator(val label: String) : ThreadItem

    data class Message(
        val message: SmsMessage,
        val connectedAbove: Boolean,
        val connectedBelow: Boolean,
        val showMeta: Boolean,
    ) : ThreadItem
}

/** Two adjacent same-sender messages group when sent within this window. */
private const val GROUP_GAP_MILLIS = 5 * 60 * 1000L

/**
 * Builds the visual top-to-bottom thread list from messages.
 *
 * [newestFirst] is the repository order (date DESC). The result is oldest →
 * newest (so it renders top → bottom), with a [ThreadItem.DaySeparator] before
 * the first message of each calendar day, and per-message grouping flags:
 *
 * - `connectedAbove` / `connectedBelow`: same sender, same day, within the
 *   group window — drives the 4dp tail-corner connection.
 * - `showMeta`: last bubble in a group — shows timestamp + delivery status.
 *
 * Pure and deterministic so it can be unit-tested without Android.
 */
fun buildThreadItems(newestFirst: List<SmsMessage>, now: Date = Date()): List<ThreadItem> {
    if (newestFirst.isEmpty()) return emptyList()
    val ordered = newestFirst.asReversed() // oldest → newest

    fun grouped(a: SmsMessage, b: SmsMessage): Boolean =
        a.direction == b.direction &&
            !isDifferentDay(a.date, b.date) &&
            kotlin.math.abs(a.date.time - b.date.time) <= GROUP_GAP_MILLIS

    val items = mutableListOf<ThreadItem>()
    for (i in ordered.indices) {
        val msg = ordered[i]
        val prev = ordered.getOrNull(i - 1) // visually above (older)
        val next = ordered.getOrNull(i + 1) // visually below (newer)

        val dayBreak = prev == null || isDifferentDay(prev.date, msg.date)
        if (dayBreak) {
            items += ThreadItem.DaySeparator(formatDaySeparator(msg.date, now))
        }

        val connectedAbove = prev != null && !dayBreak && grouped(prev, msg)
        val connectedBelow = next != null &&
            !isDifferentDay(msg.date, next.date) && grouped(msg, next)

        items += ThreadItem.Message(
            message = msg,
            connectedAbove = connectedAbove,
            connectedBelow = connectedBelow,
            showMeta = !connectedBelow,
        )
    }
    return items
}
