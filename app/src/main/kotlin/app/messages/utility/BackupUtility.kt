package app.messages.utility

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import android.os.Environment
import android.provider.Telephony
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import app.messages.data.SyncManager
import app.messages.data.db.AppDatabase
import app.messages.data.db.BackupData
import app.messages.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupUtility {

    private const val BACKUP_DIR_NAME = "Messages/Backups"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun createBackup(context: Context, backupFolderUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            
            // Backup EXACTLY what the app sees locally (including MMS as text, placeholders, etc.)
            val messagesList = db.messageDao().getAllSync()
            val activeThreadIds = messagesList.map { it.threadId }.toSet()
            val allConversations = db.conversationDao().getAllFlow().firstOrNull() ?: emptyList()
            val filteredConversations = allConversations.filter { it.threadId in activeThreadIds }

            // Collect all data
            val backupData = BackupData(
                backupTime = System.currentTimeMillis(),
                appVersion = getAppVersion(context),
                conversations = filteredConversations,
                messages = messagesList,
                archivedThreads = db.archivedThreadDao().getAllArchivedThreadIds().map { app.messages.data.db.ArchivedThreadEntity(it) },
                blockedContacts = db.blockedContactDao().getAllBlockedAddresses().map { app.messages.data.db.BlockedContactEntity(it) },
                scheduledMessages = db.scheduledMessageDao().getPendingToSendMessage(Long.MAX_VALUE),
                deletedConversations = db.deletedConversationDao().getAllSync(),
                deletedMessages = db.deletedMessageDao().getAllSync()
            )
            
            val folder = DocumentFile.fromTreeUri(context, backupFolderUri)
                ?: return@withContext Result.failure(Exception("Cannot access backup folder"))

            if (!folder.canWrite()) {
                return@withContext Result.failure(Exception("Cannot write to backup folder"))
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "backup_$timestamp.json"
            
            val file = folder.createFile("application/json", fileName)
                ?: return@withContext Result.failure(Exception("Could not create backup file"))
            
            val jsonString = gson.toJson(backupData)
            val maxLogSize = 4000
            android.util.Log.e("BackupUtility", "BACKUP_JSON_DATA START (length=${jsonString.length})")
            for (i in 0..jsonString.length / maxLogSize) {
                val start = i * maxLogSize
                var end = (i + 1) * maxLogSize
                end = if (end > jsonString.length) jsonString.length else end
                if (start < end) {
                    android.util.Log.e("BackupUtility", "BACKUP_JSON_CHUNK: " + jsonString.substring(start, end))
                }
            }
            android.util.Log.e("BackupUtility", "BACKUP_JSON_DATA END")
            
            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            } ?: return@withContext Result.failure(Exception("Could not write to backup file"))
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun getAllSystemMessagesAsEntities(context: Context): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.SUBSCRIPTION_ID
            )
            context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val subIdIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)

                while (cursor.moveToNext()) {
                    val type = cursor.getInt(typeIdx)
                    val subId = if (cursor.isNull(subIdIdx)) null else cursor.getLong(subIdIdx)
                    messages.add(
                        MessageEntity(
                            id = cursor.getLong(idIdx),
                            threadId = cursor.getLong(threadIdIdx),
                            address = app.messages.utility.AddressUtils.normalizeAddress(cursor.getString(addressIdx) ?: ""),
                            body = cursor.getString(bodyIdx) ?: "",
                            date = cursor.getLong(dateIdx),
                            type = type,
                            read = cursor.getInt(readIdx) == 1,
                            status = if (type == 2) 2 else 0,
                            subscriptionId = subId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return messages
    }

    suspend fun restoreBackup(context: Context, backupUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val inputStream = context.contentResolver.openInputStream(backupUri)
                ?: return@withContext Result.failure(Exception("Cannot read backup file"))

            val backupData = inputStream.use { stream ->
                gson.fromJson(InputStreamReader(stream), BackupData::class.java)
            }
            
            // The user explicitly requested that all restored messages be marked as READ
            // regardless of their state during the backup creation.
            // Additionally, deleted messages/conversations from the backup should be fully restored 
            // to the active inbox, not kept hidden in the deleted tables.
            val rawMessages = backupData.messages + backupData.deletedMessages.map {
                MessageEntity(
                    id = it.id, threadId = it.threadId, address = it.address, body = it.body,
                    date = it.date, type = it.type, read = true, status = it.status,
                    imageUri = it.imageUri, simSlot = it.simSlot, subscriptionId = it.subscriptionId,
                    scheduledTime = it.scheduledTime, deliveryTime = it.deliveryTime
                )
            }
            // Deduplicate messages in backup by normalized address, body, approximate time, and type
            val uniqueMessages = rawMessages.distinctBy { 
                "${app.messages.utility.AddressUtils.normalizeAddress(it.address)}_${app.messages.utility.AddressUtils.cleanMessageBody(it.body)}_${it.date / 3000}_${it.type}"
            }.map { it.copy(read = true) }

            val rawConversations = backupData.conversations + backupData.deletedConversations.map {
                app.messages.data.db.ConversationEntity(
                    threadId = it.threadId, address = it.address, body = it.body,
                    date = it.date, read = true, messageCount = it.messageCount,
                    recipientName = it.recipientName, photoUri = it.photoUri,
                    lastMsgType = it.lastMsgType, isSaved = it.isSaved,
                    isPinned = it.isPinned, normalizedAddress = it.normalizedAddress
                )
            }
            // Deduplicate conversations by normalized address
            val uniqueConversations = rawConversations.distinctBy { 
                app.messages.utility.AddressUtils.normalizeAddress(it.address) 
            }.map { it.copy(read = true) }
            
            var finalMessages = uniqueMessages
            var finalConversations = uniqueConversations
            
            // 1. Synchronization with system FIRST (if app is default SMS)
            if (isDefaultSmsApp(context)) {
                val syncResult = syncToSystemProvider(context, finalMessages, finalConversations)
                finalMessages = syncResult.first
                finalConversations = syncResult.second
            } else {
                val oldToNewThreadMap = mutableMapOf<Long, Long>()
                finalConversations = finalConversations.map { conv ->
                    val liveThreadId = try {
                        if (conv.address.contains(",")) {
                            Telephony.Threads.getOrCreateThreadId(context, conv.address.split(",").toSet())
                        } else {
                            Telephony.Threads.getOrCreateThreadId(context, conv.address)
                        }
                    } catch (e: Exception) {
                        conv.threadId
                    }
                    if (liveThreadId > 0 && liveThreadId != conv.threadId) {
                        oldToNewThreadMap[conv.threadId] = liveThreadId
                    }
                    conv.copy(threadId = if (liveThreadId > 0) liveThreadId else conv.threadId)
                }
                finalMessages = finalMessages.map { msg ->
                    val newThreadId = oldToNewThreadMap[msg.threadId]
                    if (newThreadId != null) msg.copy(threadId = newThreadId) else msg
                }
            }
            
            // 2. Restore local database
            db.conversationDao().upsertAll(finalConversations)
            db.messageDao().insertAll(finalMessages)
            
            for (archive in backupData.archivedThreads) {
                db.archivedThreadDao().insert(archive)
            }
            
            for (block in backupData.blockedContacts) {
                db.blockedContactDao().insert(block)
            }
            
            for (scheduled in backupData.scheduledMessages) {
                db.scheduledMessageDao().insert(scheduled)
            }

            // 3. Trigger a full sync via SyncManager to reconcile with system
            SyncManager(context).syncAll()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun syncToSystemProvider(
        context: Context, 
        messages: List<MessageEntity>, 
        conversations: List<app.messages.data.db.ConversationEntity>
    ): Pair<List<MessageEntity>, List<app.messages.data.db.ConversationEntity>> {
        val resolver = context.contentResolver
        val (exactSignatures, fuzzySignatures) = getSystemMessageSignatures(resolver)
        val finalMessages = mutableListOf<MessageEntity>()
        
        val threadIdCache = mutableMapOf<String, Long>()
        val oldToNewThreadIdMap = mutableMapOf<Long, Long>()
        val threadIdToRawAddress = conversations.associate { it.threadId to it.address }

        for (msg in messages) {
            var updatedMsg = msg
            val rawAddress = threadIdToRawAddress[msg.threadId] ?: msg.address
            val normAddr = app.messages.utility.AddressUtils.normalizeAddress(rawAddress)
            val cleanBody = app.messages.utility.AddressUtils.cleanMessageBody(msg.body)
            
            val exactKey = "${normAddr}_${cleanBody}_${msg.date}"
            val fuzzyKey = "${normAddr}_${cleanBody}_${msg.date / 3000}"
            
            val actualSystemId = exactSignatures[exactKey] ?: fuzzySignatures[fuzzyKey]
            
            val liveThreadId = threadIdCache.getOrPut(rawAddress) {
                try {
                    if (rawAddress.contains(",")) {
                        Telephony.Threads.getOrCreateThreadId(context, rawAddress.split(",").toSet())
                    } else {
                        Telephony.Threads.getOrCreateThreadId(context, rawAddress)
                    }
                } catch (e: Exception) {
                    msg.threadId
                }
            }
            if (liveThreadId > 0 && liveThreadId != msg.threadId) {
                oldToNewThreadIdMap[msg.threadId] = liveThreadId
            }

            if (actualSystemId == null) {
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, rawAddress)
                        put(Telephony.Sms.BODY, msg.body)
                        put(Telephony.Sms.DATE, msg.date)
                        put(Telephony.Sms.READ, if (msg.read) 1 else 0)
                        put(Telephony.Sms.TYPE, msg.type)
                        if (liveThreadId > 0) {
                            put(Telephony.Sms.THREAD_ID, liveThreadId)
                        }
                        putExtraData(this, msg)
                    }
                    val targetUri = when (msg.type) {
                        1 -> android.provider.Telephony.Sms.Inbox.CONTENT_URI
                        2 -> android.provider.Telephony.Sms.Sent.CONTENT_URI
                        3 -> android.provider.Telephony.Sms.Draft.CONTENT_URI
                        4 -> android.provider.Telephony.Sms.Outbox.CONTENT_URI
                        5 -> android.net.Uri.parse("content://sms/failed")
                        6 -> android.net.Uri.parse("content://sms/queued")
                        else -> android.provider.Telephony.Sms.CONTENT_URI
                    }
                    val uri = resolver.insert(targetUri, values)
                    val newId = uri?.lastPathSegment?.toLongOrNull()
                    if (newId != null) {
                        updatedMsg = msg.copy(id = newId, threadId = if (liveThreadId > 0) liveThreadId else msg.threadId)
                        exactSignatures[exactKey] = newId
                        fuzzySignatures[fuzzyKey] = newId
                    }
                } catch (e: Exception) {
                    // Skip if fails
                }
            } else {
                updatedMsg = msg.copy(id = actualSystemId, threadId = if (liveThreadId > 0) liveThreadId else msg.threadId)
            }
            finalMessages.add(updatedMsg)
        }
        
        // Update the original conversations with any new live thread IDs
        val finalConversations = conversations.map { conv ->
            val newThreadId = oldToNewThreadIdMap[conv.threadId]
            if (newThreadId != null) {
                conv.copy(threadId = newThreadId)
            } else {
                conv
            }
        }
        
        return Pair(finalMessages, finalConversations)
    }

    private fun getSystemMessageSignatures(resolver: ContentResolver): Pair<MutableMap<String, Long>, MutableMap<String, Long>> {
        val exactSignatures = mutableMapOf<String, Long>()
        val fuzzySignatures = mutableMapOf<String, Long>()
        try {
            val uri = Uri.parse("content://sms")
            val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val address = cursor.getString(addressIdx) ?: ""
                    val body = cursor.getString(bodyIdx) ?: ""
                    val date = cursor.getLong(dateIdx)
                    
                    val normAddr = app.messages.utility.AddressUtils.normalizeAddress(address)
                    val cleanBody = app.messages.utility.AddressUtils.cleanMessageBody(body)
                    
                    exactSignatures["${normAddr}_${cleanBody}_${date}"] = id
                    fuzzySignatures["${normAddr}_${cleanBody}_${date / 3000}"] = id
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(exactSignatures, fuzzySignatures)
    }
    
    private fun putExtraData(values: ContentValues, msg: MessageEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && msg.subscriptionId != null) {
            values.put(Telephony.Sms.SUBSCRIPTION_ID, msg.subscriptionId)
        }
    }

    private fun getSystemMessageIds(resolver: ContentResolver): Set<Long> {
        val ids = mutableSetOf<Long>()
        val uri = Uri.parse("content://sms")
        resolver.query(uri, arrayOf("_id"), null, null, null)?.use {
            val idx = it.getColumnIndex("_id")
            while (it.moveToNext()) {
                ids.add(it.getLong(idx))
            }
        }
        return ids
    }

    // Kept for backward compatibility or future use, but not actively used for new SAF flow
    fun getBackupFolder(): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(root, "Messages/Backups")
    }

    fun isDefaultSmsApp(context: Context): Boolean {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
        return defaultSmsPackage == context.packageName
    }

    private fun getAppVersion(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                pInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }
}

