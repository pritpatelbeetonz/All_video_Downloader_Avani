package app.messages.utility

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import app.messages.data.db.LauncherDatabase
import app.messages.data.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MessageLifecycleHelper {
    
    suspend fun restoreMessage(context: Context, deletedMessageId: Long): Boolean = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.getLauncherDatabase(context)
        val deletedMsg = db.deletedMessageDao().getByIdSync(deletedMessageId) ?: return@withContext false
        
        try {
            // Restore to system content://sms provider to make it globally visible
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, deletedMsg.address)
                put(Telephony.Sms.BODY, deletedMsg.body)
                put(Telephony.Sms.DATE, deletedMsg.date)
                put(Telephony.Sms.READ, if (deletedMsg.read) 1 else 0)
                put(Telephony.Sms.TYPE, deletedMsg.type)
                // Use the exact threadId as it was prior to deletion
                put(Telephony.Sms.THREAD_ID, deletedMsg.threadId)
                if (deletedMsg.status != 0) {
                    put(Telephony.Sms.STATUS, deletedMsg.status)
                }
            }
            
            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            if (uri != null) {
                val newId = uri.lastPathSegment?.toLongOrNull()
                
                if (newId != null) {
                    // Fetch the updated threadId the system might have assigned
                    var finalThreadId = deletedMsg.threadId
                    context.contentResolver.query(
                        uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            finalThreadId = cursor.getLong(0)
                        }
                    }
                    
                    // Android sometimes overrides read=0 to read=1 automatically during insertion.
                    // Force update it back if it's supposed to be unread.
                    if (!deletedMsg.read) {
                        try {
                            val updateValues = ContentValues().apply { put(Telephony.Sms.READ, 0) }
                            context.contentResolver.update(uri, updateValues, null, null)
                        } catch (e: Exception) {}
                    }
                    
                    // Insert into our local MessageEntity
                    val restoredMsg = app.messages.data.db.MessageEntity(
                        id = newId,
                        threadId = finalThreadId,
                        address = deletedMsg.address,
                        body = deletedMsg.body,
                        date = deletedMsg.date,
                        type = deletedMsg.type,
                        read = deletedMsg.read,
                        status = deletedMsg.status,
                        imageUri = deletedMsg.imageUri,
                        simSlot = deletedMsg.simSlot,
                        subscriptionId = deletedMsg.subscriptionId,
                        scheduledTime = deletedMsg.scheduledTime,
                        deliveryTime = deletedMsg.deliveryTime
                    )
                    
                    db.messageDao().insertAll(listOf(restoredMsg))
                    
                    // Restore conversation if needed
                    val deletedConv = db.deletedConversationDao().getByIdSync(deletedMsg.threadId)
                    if (deletedConv != null) {
                        val activeConv = db.conversationDao().getByIdSync(deletedConv.threadId)
                        if (activeConv == null) {
                            val newConv = app.messages.data.db.ConversationEntity(
                                threadId = finalThreadId,
                                address = deletedConv.address,
                                body = deletedMsg.body, // Update to the restored message's body
                                date = deletedMsg.date,
                                read = deletedConv.read,
                                messageCount = deletedConv.messageCount, // Best approximation
                                recipientName = deletedConv.recipientName,
                                photoUri = deletedConv.photoUri,
                                lastMsgType = deletedMsg.type,
                                isSaved = deletedConv.isSaved,
                                isPinned = deletedConv.isPinned,
                                normalizedAddress = deletedConv.normalizedAddress
                            )
                            db.conversationDao().upsertAll(listOf(newConv))
                        }
                    }
                    
                    // Delete from DeletedMessageEntity
                    db.deletedMessageDao().deleteById(deletedMessageId)
                    
                    // Sync system
                    SyncManager(context).syncAll()
                    
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext false
    }
}
