package app.messages.data.telephony.model

import java.util.Date

/**
 * A single conversation thread. Maps to a `Telephony.Threads` row.
 *
 * The UI list reads these directly; pinned/archived/blocked flags live in
 * the app's own Room table (the telephony provider has no notion of them).
 * For M1 those flags are always false from the telephony source — Room
 * mirroring lands in M2.
 */
data class Conversation(
    val threadId: String,
    val addresses: List<String>,
    val snippet: String,
    val lastActivity: Date,
    val unreadCount: Int,
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val isStarred: Boolean = false,
    val hasDraft: Boolean = false,
    val hasAttachment: Boolean = false,
) {
    val isGroup: Boolean get() = addresses.size > 1
    val isUnread: Boolean get() = unreadCount > 0

    /** Address used to derive avatar color hash — first address for groups. */
    val primaryAddress: String get() = addresses.firstOrNull().orEmpty()

    /** Display label — contact name when known, otherwise the address. */
    val displayName: String get() = contactName ?: primaryAddress
}
