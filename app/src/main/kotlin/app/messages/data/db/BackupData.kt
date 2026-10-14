package app.messages.data.db

import androidx.annotation.Keep

@Keep
data class BackupData(
    val backupTime: Long,
    val appVersion: Int,
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val archivedThreads: List<ArchivedThreadEntity> = emptyList(),
    val blockedContacts: List<BlockedContactEntity> = emptyList(),
    val scheduledMessages: List<LauncherScheduledMessageEntity> = emptyList(),
    val deletedConversations: List<DeletedConversationEntity> = emptyList(),
    val deletedMessages: List<DeletedMessageEntity> = emptyList()
)
