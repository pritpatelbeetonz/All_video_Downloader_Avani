package app.messages.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Index
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations", indices = [Index(value = ["date"]), Index(value = ["threadId"]), Index(value = ["normalizedAddress"])])
data class ConversationEntity(
    @PrimaryKey val threadId: Long,
    val address: String, 
    val body: String,
    val date: Long,
    val read: Boolean,
    val messageCount: Int,
    val recipientName: String? = null,
    val photoUri: String? = null,
    val lastMsgType: Int = 1, // 1=Inbox, 2=Sent
    val isSaved: Boolean = false,
    val isPinned: Boolean = false,
    val normalizedAddress: String? = null
)

@Entity(tableName = "archived_threads")
data class ArchivedThreadEntity(
    @PrimaryKey val threadId: Long,
    val archivedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_contacts")
data class BlockedContactEntity(
    @PrimaryKey val address: String,
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages", indices = [Index(value = ["threadId"]), Index(value = ["date"]), Index(value = ["threadId", "date"])])
data class MessageEntity(
    @PrimaryKey val id: Long, // System ID
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1=Inbox, 2=Sent
    val read: Boolean,
    val status: Int = 0, // 0=None, 1=Sending, 2=Sent, 3=Failed, 4=Scheduled, 5=Delaying
    val imageUri: String? = null,
    val simSlot: Int? = null, // 0=SIM1, 1=SIM2, null=unknown
    val subscriptionId: Long? = null, // Subscription ID for multi-SIM
    val scheduledTime: Long? = null,
    val deliveryTime: Long? = null
)

@Entity(tableName = "launcher_scheduled_messages")
data class LauncherScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val address: String,
    val body: String,
    val scheduledTime: Long,
    val status: Int = 0, // 0=Pending, 1=Sent, 2=Failed
    val subscriptionId: Long? = null,
    val simSlot: Int? = null,
    val localMessageId: Long? = null,
    val imageUri: String? = null
)


data class Contact(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE threadId NOT IN (SELECT threadId FROM archived_threads) AND NOT EXISTS (SELECT 1 FROM blocked_contacts WHERE ',' || conversations.address || ',' LIKE '%,' || blocked_contacts.address || ',%') AND TRIM(IFNULL(body, '')) != '' AND LOWER(TRIM(IFNULL(body, ''))) != 'no content' ORDER BY isPinned DESC, date DESC")
    fun getUnarchivedFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE threadId IN (SELECT threadId FROM archived_threads) AND NOT EXISTS (SELECT 1 FROM blocked_contacts WHERE ',' || conversations.address || ',' LIKE '%,' || blocked_contacts.address || ',%') ORDER BY isPinned DESC, date DESC")
    fun getArchivedFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE EXISTS (SELECT 1 FROM blocked_contacts WHERE ',' || conversations.address || ',' LIKE '%,' || blocked_contacts.address || ',%') ORDER BY isPinned DESC, date DESC")
    fun getBlockedFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, date DESC")
    fun getAllFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT threadId FROM conversations")
    suspend fun getAllThreadIds(): List<Long>

    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    suspend fun getById(threadId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    fun getByIdSync(threadId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations")
    fun getAllSync(): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE normalizedAddress = :normalizedAddress LIMIT 1")
    suspend fun getByNormalizedAddress(normalizedAddress: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE normalizedAddress = :normalizedAddress ORDER BY date DESC LIMIT 1")
    fun getFlowByNormalizedAddress(normalizedAddress: String): kotlinx.coroutines.flow.Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE normalizedAddress = :normalizedAddress")
    suspend fun getAllByNormalizedAddress(normalizedAddress: String): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE normalizedAddress = :normalizedAddress")
    fun getAllByNormalizedAddressSync(normalizedAddress: String): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)
    
    @Query("DELETE FROM conversations WHERE threadId >= 0")
    suspend fun clearSystemConversations()

    @Query("DELETE FROM conversations WHERE threadId = :threadId")
    suspend fun deleteByThreadId(threadId: Long)

    @Query("DELETE FROM conversations WHERE (TRIM(IFNULL(body, '')) = '' OR LOWER(TRIM(IFNULL(body, ''))) = 'no content') AND NOT (address LIKE '%,%' OR recipientName IS NOT NULL)")
    suspend fun deleteBlankOrPlaceholderConversations()

    @Query("UPDATE conversations SET read = 1 WHERE threadId = :threadId")
    suspend fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = 0 WHERE threadId = :threadId")
    suspend fun markUnread(threadId: Long)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE threadId = :threadId")
    suspend fun updatePinStatus(threadId: Long, isPinned: Boolean)

    @Transaction
    suspend fun upsertAll(conversations: List<ConversationEntity>) {
        val existingMap = getAllSync().associateBy { it.threadId }
        val toSave = ArrayList<ConversationEntity>(conversations.size)
        for (conv in conversations) {
            val existing = existingMap[conv.threadId]
            if (existing == null) {
                toSave.add(conv)
            } else if (conv.date >= existing.date) {
                val finalPinned = existing.isPinned
                val finalName = if (conv.address.contains(",")) {
                    existing.recipientName ?: conv.recipientName
                } else {
                    conv.recipientName
                }
                toSave.add(conv.copy(isPinned = finalPinned, recipientName = finalName))
            }
        }
        if (toSave.isNotEmpty()) {
            insertAll(toSave)
        }
    }

    @Transaction
    suspend fun syncAllWithDeletions(toInsert: List<ConversationEntity>, toDelete: List<Long>) {
        for (id in toDelete) {
            deleteByThreadId(id)
        }
        upsertAll(toInsert)
    }

    @Transaction
    suspend fun syncRecentWithDeletions(toInsert: List<ConversationEntity>, toDelete: List<Long>) {
        for (id in toDelete) {
            deleteByThreadId(id)
        }
        upsertAll(toInsert)
    }
}

@Dao
interface BlockedContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockedContact: BlockedContactEntity)

    @Query("DELETE FROM blocked_contacts WHERE address = :address")
    suspend fun unblock(address: String)

    @Query("SELECT address FROM blocked_contacts")
    suspend fun getAllBlockedAddresses(): List<String>
    
    @Query("SELECT address FROM blocked_contacts")
    fun getBlockedAddressesFlow(): Flow<List<String>>
}

@Entity(tableName = "blocked_messages")
data class BlockedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val body: String,
    val date: Long
)

@Dao
interface BlockedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockedMessage: BlockedMessageEntity)

    @Query("SELECT * FROM blocked_messages ORDER BY date DESC")
    fun getAllBlockedMessagesFlow(): Flow<List<BlockedMessageEntity>>

    @Query("DELETE FROM blocked_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM blocked_messages")
    suspend fun deleteAll()
}

@Dao
interface LauncherScheduledMessageDao {
    @Query("SELECT * FROM launcher_scheduled_messages WHERE status = 0 ORDER BY scheduledTime ASC")
    fun getPendingFlow(): Flow<List<LauncherScheduledMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scheduledMessage: LauncherScheduledMessageEntity): Long

    @Query("SELECT * FROM launcher_scheduled_messages WHERE localMessageId = :localId")
    suspend fun getByLocalIdSync(localId: Long): LauncherScheduledMessageEntity?

    @Query("DELETE FROM launcher_scheduled_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE launcher_scheduled_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    @Query("SELECT * FROM launcher_scheduled_messages WHERE id = :id")
    suspend fun getByIdSync(id: Long): LauncherScheduledMessageEntity?

    @Query("SELECT * FROM launcher_scheduled_messages WHERE localMessageId = :localId")
    suspend fun getByLocalMessageIdSync(localId: Long): LauncherScheduledMessageEntity?

    @Query("SELECT * FROM launcher_scheduled_messages WHERE status = 0 AND scheduledTime <= :currentTime")
    suspend fun getPendingToSendMessage(currentTime: Long): List<LauncherScheduledMessageEntity>
}



@Entity(tableName = "deleted_conversations", indices = [Index(value = ["date"]), Index(value = ["threadId"])])
data class DeletedConversationEntity(
    @PrimaryKey val threadId: Long,
    val address: String, 
    val body: String,
    val date: Long,
    val read: Boolean,
    val messageCount: Int,
    val recipientName: String? = null,
    val photoUri: String? = null,
    val lastMsgType: Int = 1,
    val isSaved: Boolean = false,
    val isPinned: Boolean = false,
    val normalizedAddress: String? = null,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "deleted_messages", indices = [Index(value = ["threadId"]), Index(value = ["date"]), Index(value = ["threadId", "date"])])
data class DeletedMessageEntity(
    @PrimaryKey val id: Long, // System ID
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1=Inbox, 2=Sent
    val read: Boolean,
    val status: Int = 0,
    val imageUri: String? = null,
    val simSlot: Int? = null,
    val subscriptionId: Long? = null,
    val scheduledTime: Long? = null,
    val deliveryTime: Long? = null,
    val deletedAt: Long = System.currentTimeMillis()
)

@Dao
interface DeletedConversationDao {
    @Query("SELECT * FROM deleted_conversations ORDER BY deletedAt DESC")
    fun getAllFlow(): Flow<List<DeletedConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: DeletedConversationEntity)

    @Query("SELECT * FROM deleted_conversations WHERE threadId = :threadId")
    suspend fun getByIdSync(threadId: Long): DeletedConversationEntity?

    @Query("DELETE FROM deleted_conversations WHERE threadId = :threadId")
    suspend fun deleteById(threadId: Long)
    
    @Query("SELECT * FROM deleted_conversations")
    fun getAllSync(): List<DeletedConversationEntity>
}

@Dao
interface DeletedMessageDao {
    @Query("SELECT * FROM deleted_messages ORDER BY deletedAt DESC")
    fun getAllFlow(): Flow<List<DeletedMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: DeletedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<DeletedMessageEntity>)

    @Query("SELECT * FROM deleted_messages WHERE threadId = :threadId ORDER BY date ASC")
    fun getMessagesForThreadFlow(threadId: Long): Flow<List<DeletedMessageEntity>>

    @Query("SELECT * FROM deleted_messages WHERE threadId = :threadId ORDER BY date ASC")
    suspend fun getMessagesForThreadSync(threadId: Long): List<DeletedMessageEntity>

    @Query("SELECT * FROM deleted_messages WHERE id = :id")
    suspend fun getByIdSync(id: Long): DeletedMessageEntity?

    @Query("DELETE FROM deleted_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT * FROM deleted_messages")
    fun getAllSync(): List<DeletedMessageEntity>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE address = :address OR threadId = :threadId ORDER BY date ASC")
    fun getMessagesByAddressOrThreadFlow(address: String, threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages")
    suspend fun getAllSync(): List<MessageEntity>

    @Query("SELECT DISTINCT threadId FROM messages")
    fun getThreadIdsWithMessagesSync(): List<Long>

    @Query("UPDATE messages SET threadId = :newId WHERE threadId = :oldId")
    suspend fun mergeMessages(oldId: Long, newId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
    
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    @Query("UPDATE messages SET status = :status, date = :date WHERE id = :id")
    suspend fun updateDateAndStatus(id: Long, date: Long, status: Int)

    @Query("UPDATE messages SET status = :status, deliveryTime = :deliveryTime WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Long, status: Int, deliveryTime: Long)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageByIdSync(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE threadId = :threadId")
    suspend fun getMessagesForThreadSync(threadId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date DESC LIMIT 1")
    suspend fun getLastMessageForThreadSync(threadId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteByThreadId(threadId: Long)

    @Query("UPDATE messages SET read = 1 WHERE threadId = :threadId")
    suspend fun markRead(threadId: Long)

    @Query("UPDATE messages SET read = 0 WHERE threadId = :threadId")
    suspend fun markUnread(threadId: Long)
}

@Dao
interface ArchivedThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(archivedThread: ArchivedThreadEntity)

    @Query("DELETE FROM archived_threads WHERE threadId = :threadId")
    suspend fun unarchive(threadId: Long)

    @Query("SELECT threadId FROM archived_threads")
    suspend fun getAllArchivedThreadIds(): List<Long>
}
