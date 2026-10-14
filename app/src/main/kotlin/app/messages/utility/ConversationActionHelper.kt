package app.messages.utility

import android.content.Context
import android.net.Uri
import android.widget.Toast
import app.messages.data.CacheManager
import app.messages.data.SyncManager
import app.messages.data.db.LauncherDatabase
import app.messages.data.db.ArchivedThreadEntity
import app.messages.data.db.BlockedContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.messages.R

object ConversationActionHelper {
    suspend fun deleteConversations(
        context: Context,
        threadIds: List<Long>,
        showToast: Boolean = true,
        onSuccess: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.getLauncherDatabase(context)
        val cacheManager = CacheManager.getInstance(context)

        threadIds.forEach { id ->
            // Move conversation to deleted table
            val conv = db.conversationDao().getByIdSync(id)
            if (conv != null) {
                db.deletedConversationDao().insert(
                    app.messages.data.db.DeletedConversationEntity(
                        threadId = conv.threadId,
                        address = conv.address,
                        body = conv.body,
                        date = conv.date,
                        read = conv.read,
                        messageCount = conv.messageCount,
                        recipientName = conv.recipientName,
                        photoUri = conv.photoUri,
                        lastMsgType = conv.lastMsgType,
                        isSaved = conv.isSaved,
                        isPinned = conv.isPinned,
                        normalizedAddress = conv.normalizedAddress,
                        deletedAt = System.currentTimeMillis()
                    )
                )
            }
            
            // Move messages to deleted table
            val msgs = db.messageDao().getMessagesForThreadSync(id)
            val deletedMsgs = msgs.map { msg ->
                app.messages.data.db.DeletedMessageEntity(
                    id = msg.id,
                    threadId = msg.threadId,
                    address = msg.address,
                    body = msg.body,
                    date = msg.date,
                    type = msg.type,
                    read = msg.read,
                    status = msg.status,
                    imageUri = msg.imageUri,
                    simSlot = msg.simSlot,
                    subscriptionId = msg.subscriptionId,
                    scheduledTime = msg.scheduledTime,
                    deliveryTime = msg.deliveryTime,
                    deletedAt = System.currentTimeMillis()
                )
            }
            if (deletedMsgs.isNotEmpty()) {
                db.deletedMessageDao().insertAll(deletedMsgs)
            }

            try {
                val uri = Uri.parse("content://mms-sms/conversations/$id")
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            db.conversationDao().deleteByThreadId(id)
            db.messageDao().deleteByThreadId(id)
            cacheManager.removeFromCache(id)
        }

        withContext(Dispatchers.Main) {
            if (showToast) {
                }
            onSuccess()
        }
    }

    suspend fun archiveConversations(
        context: Context,
        threadIds: List<Long>,
        showToast: Boolean = true,
        onSuccess: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.getLauncherDatabase(context)
        val syncManager = SyncManager(context)
        val cacheManager = CacheManager.getInstance(context)

        threadIds.forEach { id ->
            db.archivedThreadDao().insert(ArchivedThreadEntity(id))
            syncManager.updateSystemArchiveStatus(id, true)
            cacheManager.removeFromCache(id)
        }

        withContext(Dispatchers.Main) {
            if (showToast) {
                }
            onSuccess()
        }
    }

    suspend fun unarchiveConversations(
        context: Context,
        threadIds: List<Long>,
        showToast: Boolean = true,
        onSuccess: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.getLauncherDatabase(context)
        val syncManager = SyncManager(context)

        threadIds.forEach { id ->
            db.archivedThreadDao().unarchive(id)
            syncManager.updateSystemArchiveStatus(id, false)
        }

        withContext(Dispatchers.Main) {
            if (showToast) {
                }
            onSuccess()
        }
    }

    suspend fun blockConversations(
        context: Context,
        threadIds: List<Long>,
        addresses: List<String>,
        showToast: Boolean = true,
        onSuccess: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.getLauncherDatabase(context)
        val syncManager = SyncManager(context)
        val cacheManager = CacheManager.getInstance(context)

        // Block all given addresses (comma separated implies multiple users)
        addresses.forEach { addressStr ->
            val splitAddresses = addressStr.split(Regex("[,]+")).map { it.trim() }.filter { it.isNotBlank() }
            splitAddresses.forEach { singleAddress ->
                db.blockedContactDao().insert(BlockedContactEntity(singleAddress))
                syncManager.updateSystemBlockStatus(singleAddress, true)
            }
        }
        
        threadIds.forEach { id ->
            // Copy existing messages to Blocked Messages
            val messages = db.messageDao().getMessagesForThreadSync(id)
            messages.forEach { msg ->
                db.blockedMessageDao().insert(
                    app.messages.data.db.BlockedMessageEntity(
                        address = msg.address,
                        body = msg.body,
                        date = msg.date
                    )
                )
            }
            
            // Delete from system DB completely
            try {
                val uri = android.net.Uri.parse("content://mms-sms/conversations/$id")
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            db.conversationDao().deleteByThreadId(id)
            db.messageDao().deleteByThreadId(id)

            cacheManager.removeFromCache(id)
        }

        withContext(Dispatchers.Main) {
            if (showToast) {
                }
            onSuccess()
        }
    }

    suspend fun unblockConversations(
        context: Context,
        addresses: List<String>,
        showToast: Boolean = true,
        onSuccess: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.getLauncherDatabase(context)
        val syncManager = SyncManager(context)

        addresses.forEach { addressStr ->
            // Safeguard: some legacy bugs might have stored the whole comma-separated string as one entry.
            // We ensure that's cleared to avoid it "sticking" in the DAO's new LIKE query.
            if (addressStr.contains(",")) {
                db.blockedContactDao().unblock(addressStr.trim())
            }

            val splitAddresses = addressStr.split(Regex("[,]+")).map { it.trim() }.filter { it.isNotBlank() }
            splitAddresses.forEach { singleAddress ->
                db.blockedContactDao().unblock(singleAddress)
                syncManager.updateSystemBlockStatus(singleAddress, false)
            }
        }

        withContext(Dispatchers.Main) {
            if (showToast) {
                }
            onSuccess()
        }
    }
}
