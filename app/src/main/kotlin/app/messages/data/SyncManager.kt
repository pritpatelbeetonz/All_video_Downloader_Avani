package app.messages.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.provider.BlockedNumberContract
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import app.messages.data.db.LauncherDatabase
import app.messages.data.db.Contact
import app.messages.data.db.ConversationEntity
import app.messages.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull

private const val MAX_CACHED_CONVERSATIONS = 100

class SyncManager(private val context: Context) {

    private val db = LauncherDatabase.getLauncherDatabase(context)
    private val cacheManager = CacheManager.getInstance(context)

    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val isSyncRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext
        if (!isSyncRunning.compareAndSet(false, true)) return@withContext
        try {
            val contactsMap = getSystemContactsMap() 
            cacheManager.cacheContactsMap(contactsMap)
            syncConversations(contactsMap)
            syncBlockedNumbers()
            syncArchiveStatus()
            
            // Update last sync time
            cacheManager.setLastSyncTime(System.currentTimeMillis())
        } finally {
            isSyncRunning.set(false)
        }
    }

    private suspend fun syncBlockedNumbers() = withContext(Dispatchers.IO) {
        try {
            val systemBlockedSet = mutableSetOf<String>()
            val uri = BlockedNumberContract.BlockedNumbers.CONTENT_URI
            context.contentResolver.query(uri, arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)?.use {
                val idx = it.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                while (it.moveToNext()) {
                    it.getString(idx)?.let { num -> systemBlockedSet.add(num) }
                }
            }

            // 1. Add new blocks from system to local DB
            for (number in systemBlockedSet) {
                db.blockedContactDao().insert(app.messages.data.db.BlockedContactEntity(number))
            }
            
            // 2. Remove local blocks that are no longer in the system
            val localBlockedList = db.blockedContactDao().getAllBlockedAddresses()
            for (localAddr in localBlockedList) {
                if (localAddr !in systemBlockedSet) {
                    db.blockedContactDao().unblock(localAddr)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncArchiveStatus() = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("content://mms-sms/conversations?simple=true")
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val archivedIdx = cursor.getColumnIndex("archived")
                val threadIdIdx = cursor.getColumnIndex("_id")
                
                if (archivedIdx != -1 && threadIdIdx != -1) {
                    while (cursor.moveToNext()) {
                        val threadId = cursor.getLong(threadIdIdx)
                        val isArchived = cursor.getInt(archivedIdx) == 1
                        
                        if (isArchived) {
                            db.archivedThreadDao().insert(app.messages.data.db.ArchivedThreadEntity(threadId))
                        }
                        // We avoid calling unarchive(threadId) here because many systems 
                        // don't reliably report or persist the archived state, 
                        // and we don't want to overwrite local archive actions.
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateSystemArchiveStatus(threadId: Long, archived: Boolean) = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put("archived", if (archived) 1 else 0)
        }
        
        val uris = arrayOf(
            Uri.parse("content://mms-sms/conversations/$threadId"),
            Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, threadId.toString()),
            Uri.parse("content://sms/conversations/$threadId"),
            Uri.withAppendedPath(Uri.parse("content://sms/conversations"), threadId.toString())
        )
        
        for (uri in uris) {
            try {
                val rows = context.contentResolver.update(uri, values, null, null)
                if (rows > 0) return@withContext // Success
            } catch (e: Exception) {
                // Try next URI
            }
        }
        
        // If "archived" column update failed, it might be named differently on some ROMs, 
        // but "archived" is the most common for those that support it.
    }

    suspend fun updateSystemBlockStatus(address: String, blocked: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (blocked) {
                val values = android.content.ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, address)
                }
                context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
            } else {
                val uri = BlockedNumberContract.BlockedNumbers.CONTENT_URI
                val selection = "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?"
                val selectionArgs = arrayOf(address)
                context.contentResolver.delete(uri, selection, selectionArgs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Quick sync to get just the latest conversations for immediate display
     */
    suspend fun quickSyncRecentConversations(limit: Int = 20) = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext
        val contactsMap = cacheManager.getMemoryCachedContactsMap()
        syncRecentConversations(contactsMap, limit, null)
    }

    private fun getSystemThreadIds(): Set<Long>? {
        val ids = HashSet<Long>()
        try {
            val uri = Uri.parse("content://mms-sms/conversations?simple=true")
            val cursor = context.contentResolver.query(uri, arrayOf("_id"), null, null, null)
            if (cursor == null) return null
            
            cursor.use {
                val idIdx = it.getColumnIndex("_id")
                while (it.moveToNext()) {
                    if (idIdx >= 0) {
                        ids.add(it.getLong(idIdx))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return ids
    }

    suspend fun syncMessagesForThread(threadId: Long, systemThreadId: Long = threadId) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext

        val messages = ArrayList<MessageEntity>()
        val uri = Uri.parse("content://sms")
        
        val threadIdsToSync = mutableSetOf<Long>()
        threadIdsToSync.add(threadId)
        // Always include the system-assigned threadId (may differ from local DB threadId)
        // so we always find the new message in the content provider.
        threadIdsToSync.add(systemThreadId)
        
        try {
            val address = db.conversationDao().getByIdSync(threadId)?.address
            if (!address.isNullOrBlank()) {
                val normalized = app.messages.utility.AddressUtils.normalizeAddress(address)
                val allThreads = db.conversationDao().getAllByNormalizedAddressSync(normalized)
                allThreads.forEach { threadIdsToSync.add(it.threadId) }
                // Also get the live system threadId for this address
                val liveSystemThreadId = Telephony.Threads.getOrCreateThreadId(context, address)
                threadIdsToSync.add(liveSystemThreadId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val addressStr = db.conversationDao().getByIdSync(threadId)?.address
            val fuzzyAddress = if (addressStr != null && !addressStr.contains(",")) "%" + addressStr.trim() else null
            
            val selection = if (fuzzyAddress != null) {
                "${Telephony.Sms.THREAD_ID} IN (${threadIdsToSync.joinToString(",")}) OR ${Telephony.Sms.ADDRESS} LIKE ?"
            } else {
                "${Telephony.Sms.THREAD_ID} IN (${threadIdsToSync.joinToString(",")})"
            }
            
            val selectionArgs = if (fuzzyAddress != null) arrayOf(fuzzyAddress) else null

            val smsProjection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.STATUS,
                Telephony.Sms.SUBSCRIPTION_ID
            )
            val cursor = context.contentResolver.query(uri, smsProjection, selection, selectionArgs, "date ASC")
            cursor?.use {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)
                val statusIdx = it.getColumnIndex(Telephony.Sms.STATUS)
                val subIdIdx = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (it.moveToNext()) {
                    val subscriptionId = if (subIdIdx >= 0) {
                        it.getLong(subIdIdx)
                    } else {
                        null
                    }
                    
                    val simSlot = subscriptionId?.let { subId ->
                        getSimSlotFromSubscriptionId(subId)
                    }

                    messages.add(
                        MessageEntity(
                            id = it.getLong(idIdx),
                            threadId = threadId,
                            address = app.messages.utility.AddressUtils.normalizeAddress(it.getString(addressIdx) ?: ""),
                            body = app.messages.utility.AddressUtils.cleanMessageBody(it.getString(bodyIdx) ?: ""),
                            date = it.getLong(dateIdx).let { t ->
                                if (t < 10_000_000_000L) t * 1000L else t
                            },
                            type = it.getInt(typeIdx),
                            read = it.getInt(readIdx) == 1,
                            status = when (it.getInt(typeIdx)) {
                                5 -> 3 // Failed
                                4, 6 -> 1 // Outbox/Queued -> Sending
                                2 -> {
                                    val sysStatus = if (statusIdx >= 0) it.getInt(statusIdx) else -1
                                    if (sysStatus == Telephony.Sms.STATUS_COMPLETE) 6 else 2
                                }
                                else -> 0
                            },
                            simSlot = simSlot,
                            subscriptionId = subscriptionId
                        )
                    )
                }
            }
            
            // Fetch MMS
            val mmsUri = Uri.parse("content://mms")
            val mmsSelection = "${Telephony.Mms.THREAD_ID} IN (${threadIdsToSync.joinToString(",")})"
            val mmsProjection = arrayOf(
                Telephony.BaseMmsColumns._ID,
                Telephony.BaseMmsColumns.DATE,
                Telephony.BaseMmsColumns.MESSAGE_BOX,
                Telephony.Mms.SUBSCRIPTION_ID
            )
            val mmsCursor = context.contentResolver.query(mmsUri, mmsProjection, mmsSelection, null, "date ASC")
            mmsCursor?.use {
                val idIdx = it.getColumnIndex(Telephony.BaseMmsColumns._ID)
                val dateIdx = it.getColumnIndex(Telephony.BaseMmsColumns.DATE)
                val msgBoxIdx = it.getColumnIndex(Telephony.BaseMmsColumns.MESSAGE_BOX)
                val subIdIdx = it.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)

                while (it.moveToNext()) {
                    try {
                        val mmsId = it.getLong(idIdx)
                        // MMS date is in seconds, convert to ms if needed
                        val rawDate = it.getLong(dateIdx)
                        val date = if (rawDate < 10_000_000_000L) rawDate * 1000L else rawDate
                        val msgBox = it.getInt(msgBoxIdx)
                        val type = when (msgBox) {
                            1 -> 1 // Inbox
                            2, 4 -> 2 // Sent or Outbox
                            else -> 1
                        }
                        
                        val subscriptionId = if (subIdIdx >= 0) {
                            it.getLong(subIdIdx)
                        } else {
                            null
                        }
                        
                        val simSlot = subscriptionId?.let { subId ->
                            getSimSlotFromSubscriptionId(subId)
                        }
                        
                        val imageUri = getMmsImagePart(mmsId)
                        val mmsAddress = getAddressForMms(mmsId)
                        val mmsText = getMmsText(mmsId)

                        messages.add(
                            MessageEntity(
                                id = mmsId,
                                threadId = threadId,
                                address = mmsAddress,
                                body = when {
                                    mmsText.isNotBlank() -> mmsText
                                    else -> ""
                                },
                                date = date,
                                type = type,
                                read = true,
                                status = if (type == 2) 2 else 0,
                                imageUri = imageUri,
                                simSlot = simSlot,
                                subscriptionId = subscriptionId
                            )
                        )
                    } catch (e: Exception) {
                        // Skip a single corrupt/oversized MMS rather than aborting the thread sync
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            return@withContext
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext
        }
        
        // Fetch existing messages to avoid duplicates
        val existingMessages = db.messageDao().getMessagesForThreadSync(threadId)
        val toDelete = mutableListOf<Long>()
        val filteredMessages = mutableListOf<MessageEntity>()
        
        for (incoming in messages) {
            var finalMsg = incoming
            var isDuplicate = false
            
            for (existing in existingMessages) {
                // 1. Exact ID match
                if (existing.id == incoming.id) {
                    isDuplicate = true
                    // Sync status updates from system (e.g. message became delivered)
                    if (existing.status != incoming.status) {
                        if (incoming.status == 6) {
                            val finalDeliveryTime = existing.deliveryTime ?: System.currentTimeMillis()
                            db.messageDao().updateDeliveryStatus(existing.id, incoming.status, finalDeliveryTime)
                        } else {
                            db.messageDao().updateStatus(existing.id, incoming.status)
                        }
                    }
                    break
                }
                
                // 2. Body + Address + proximate Date match (matches local placeholders)
                val bodyMatch = existing.body == incoming.body
                val addrMatch = app.messages.utility.AddressUtils.normalizeAddress(existing.address) == app.messages.utility.AddressUtils.normalizeAddress(incoming.address)
                val dateDiff = Math.abs(existing.date - incoming.date)
                
                val isPlaceholder = existing.id > 1000000000000L || existing.id < 0L
                val isImagePlaceholderMatch = isPlaceholder && existing.body == "" && (incoming.body == "" || incoming.imageUri != null)
                
                if (isPlaceholder && (bodyMatch || isImagePlaceholderMatch) && addrMatch && dateDiff < 120000) {
                    // Transfer local-only metadata (like precise deliveryTime) to the incoming system record
                    if (existing.deliveryTime != null) {
                        finalMsg = incoming.copy(deliveryTime = existing.deliveryTime)
                    }
                    // Mark placeholder for deletion
                    toDelete.add(existing.id)
                    break
                }
            }
            
            if (!isDuplicate) {
                filteredMessages.add(finalMsg)
            }
        }
        
        if (toDelete.isNotEmpty()) {
            db.messageDao().deleteByIds(toDelete)
        }
        
        if (filteredMessages.isNotEmpty()) {
            val sortedMessages = filteredMessages.sortedBy { it.date }
            db.messageDao().insertAll(sortedMessages)
        }
    }
    
    /**
     * Load messages for a thread with pagination
     */
    suspend fun syncMessagesForThreadPaginated(threadId: Long, offset: Int = 0, limit: Int = 50) = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext 0

        val messages = ArrayList<MessageEntity>()
        val uri = Uri.parse("content://sms")
        
        try {
            val addressStr = db.conversationDao().getByIdSync(threadId)?.address
            val fuzzyAddress = if (addressStr != null && !addressStr.contains(",")) "%" + addressStr.trim() else null
            
            val liveSystemThreadId = try {
                if (addressStr != null) Telephony.Threads.getOrCreateThreadId(context, addressStr) else threadId
            } catch (e: Exception) { threadId }
            
            val threadIdsToSync = setOf(threadId, liveSystemThreadId)
            
            val selection = if (fuzzyAddress != null) {
                "${Telephony.Sms.THREAD_ID} IN (${threadIdsToSync.joinToString(",")}) OR ${Telephony.Sms.ADDRESS} LIKE ?"
            } else {
                "${Telephony.Sms.THREAD_ID} IN (${threadIdsToSync.joinToString(",")})"
            }
            val selectionArgs = if (fuzzyAddress != null) arrayOf(fuzzyAddress) else null

            val smsProjection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.STATUS
            )
            val cursor = context.contentResolver.query(uri, smsProjection, selection, selectionArgs, "date DESC")
            cursor?.use {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)
                val statusIdx = it.getColumnIndex(Telephony.Sms.STATUS)

                var currentIndex = 0
                while (it.moveToNext()) {
                    if (currentIndex < offset) {
                        currentIndex++
                        continue
                    }
                    if (currentIndex >= offset + limit) {
                        break
                    }
                    
                    messages.add(
                        MessageEntity(
                            id = it.getLong(idIdx),
                            threadId = threadId,
                            address = app.messages.utility.AddressUtils.normalizeAddress(it.getString(addressIdx) ?: ""),
                            body = app.messages.utility.AddressUtils.cleanMessageBody(it.getString(bodyIdx) ?: ""),
                            date = it.getLong(dateIdx).let { t ->
                                if (t < 10_000_000_000L) t * 1000L else t
                            },
                            type = it.getInt(typeIdx),
                            read = it.getInt(readIdx) == 1,
                            status = when (it.getInt(typeIdx)) {
                                5 -> 3 // Failed
                                4, 6 -> 1 // Outbox/Queued -> Sending
                                2 -> {
                                    val sysStatus = if (statusIdx >= 0) it.getInt(statusIdx) else -1
                                    if (sysStatus == Telephony.Sms.STATUS_COMPLETE) 6 else 2
                                }
                                else -> 0
                            }
                        )
                    )
                    currentIndex++
                }
            }
            
            // Fetch MMS with pagination
            val mmsUri = Uri.parse("content://mms")
            val mmsProjection = arrayOf(
                Telephony.BaseMmsColumns._ID,
                Telephony.BaseMmsColumns.DATE,
                Telephony.BaseMmsColumns.MESSAGE_BOX
            )
            
            val mmsSelection = "${Telephony.Mms.THREAD_ID} IN (${threadIdsToSync.joinToString(",")})"
            val mmsCursor = context.contentResolver.query(mmsUri, mmsProjection, mmsSelection, null, "date DESC")
            mmsCursor?.use {
                val idIdx = it.getColumnIndex(Telephony.BaseMmsColumns._ID)
                val dateIdx = it.getColumnIndex(Telephony.BaseMmsColumns.DATE)
                val msgBoxIdx = it.getColumnIndex(Telephony.BaseMmsColumns.MESSAGE_BOX)

                var currentMmsIndex = 0
                while (it.moveToNext()) {
                    if (currentMmsIndex < offset) {
                        currentMmsIndex++
                        continue
                    }
                    if (currentMmsIndex >= offset + limit) {
                        break
                    }
                    
                    try {
                        val mmsId = it.getLong(idIdx)
                        // MMS date is in seconds, convert to ms if needed
                        val rawDate = it.getLong(dateIdx)
                        val date = if (rawDate < 10_000_000_000L) rawDate * 1000L else rawDate
                        val msgBox = it.getInt(msgBoxIdx)
                        val type = when (msgBox) {
                            1 -> 1 // Inbox
                            2, 4 -> 2 // Sent or Outbox
                            else -> 1
                        }
                        
                        val imageUri = getMmsImagePart(mmsId)
                        val mmsAddress = getAddressForMms(mmsId)
                        val mmsText = getMmsText(mmsId)

                        messages.add(
                            MessageEntity(
                                id = mmsId,
                                threadId = threadId,
                                address = mmsAddress,
                                body = when {
                                    mmsText.isNotBlank() -> mmsText
                                    else -> ""
                                },
                                date = date,
                                type = type,
                                read = true,
                                status = if (type == 2) 2 else 0,
                                imageUri = imageUri
                            )
                        )
                        currentMmsIndex++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            return@withContext 0
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext 0
        }
        
        // Fetch existing messages to avoid duplicates
        val existingMessages = db.messageDao().getMessagesForThreadSync(threadId)
        val toDelete = mutableListOf<Long>()
        val filteredMessages = mutableListOf<MessageEntity>()
        val seenSentKeys = mutableSetOf<String>()
        
        for (incoming in messages) {
            if (incoming.type == 2) {
                val sentKey = "${incoming.body}_${incoming.date / 5000}"
                if (seenSentKeys.contains(sentKey)) continue
                seenSentKeys.add(sentKey)
            }

            var finalMsg = incoming
            var isDuplicate = false
            
            for (existing in existingMessages) {
                if (existing.id == incoming.id) {
                    isDuplicate = true
                    if (existing.status != incoming.status) {
                        db.messageDao().updateStatus(existing.id, incoming.status)
                    }
                    break
                }
                
                val bodyMatch = existing.body == incoming.body
                val addrMatch = app.messages.utility.AddressUtils.normalizeAddress(existing.address) == app.messages.utility.AddressUtils.normalizeAddress(incoming.address)
                val dateDiff = Math.abs(existing.date - incoming.date)
                
                val isPlaceholder = existing.id > 1000000000000L || existing.id < 0L
                val isImagePlaceholderMatch = isPlaceholder && existing.body == "" && (incoming.body == "" || incoming.imageUri != null)
                val isSentGroupMatch = incoming.type == 2 && existing.type == 2 && existing.body == incoming.body && dateDiff < 10000
                
                if ((bodyMatch || isImagePlaceholderMatch || isSentGroupMatch) && (addrMatch || isSentGroupMatch) && dateDiff < 120000) {
                    toDelete.add(existing.id)
                    break
                }
            }
            
            if (!isDuplicate) {
                filteredMessages.add(finalMsg)
            }
        }
        
        if (toDelete.isNotEmpty()) {
            db.messageDao().deleteByIds(toDelete)
        }
        
        if (filteredMessages.isNotEmpty()) {
            db.messageDao().insertAll(filteredMessages)
        }
        
        filteredMessages.size
    }

    private fun getAddrKey(addr: String): String {
        return app.messages.utility.AddressUtils.normalizeAddress(addr)
    }

    private fun getMmsData(mmsId: Long): Pair<String, String?> {
        var text = ""
        var imageUri: String? = null
        val uri = Uri.parse("content://mms/part")
        val projection = arrayOf("_id", "ct", "text")
        try {
            context.contentResolver.query(
                uri,
                projection,
                "mid = ?",
                arrayOf(mmsId.toString()),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val ctIdx = cursor.getColumnIndex("ct")
                val textIdx = cursor.getColumnIndex("text")
                
                while (cursor.moveToNext()) {
                    val ct = if (ctIdx >= 0) cursor.getString(ctIdx) ?: "" else ""
                    if (ct == "text/plain" && text.isBlank()) {
                        text = if (textIdx >= 0) cursor.getString(textIdx) ?: "" else ""
                    } else if (ct.startsWith("image/") && imageUri == null) {
                        val partId = if (idIdx >= 0) cursor.getString(idIdx) else null
                        if (partId != null) {
                            imageUri = "content://mms/part/$partId"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(text, imageUri)
    }

    private fun getMmsText(mmsId: Long): String {
        return getMmsData(mmsId).first
    }

    private fun getMmsImagePart(mmsId: Long): String? {
        return getMmsData(mmsId).second
    }

    private fun getAddressForMms(mmsId: Long): String {
        val projection = arrayOf("address", "type")
        // Prefer path-based URI; fall back to table query — some OEMs parcel oversized
        // addr rows and throw BadParcelableException on one form but not the other.
        val queryAttempts = listOf(
            Triple(Uri.parse("content://mms/$mmsId/addr"), null as String?, null as Array<String>?),
            Triple(Uri.parse("content://mms/addr"), "msg_id = ?", arrayOf(mmsId.toString()))
        )
        for ((uri, selection, selectionArgs) in queryAttempts) {
            try {
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use {
                    val addrIdx = it.getColumnIndex("address")
                    val typeIdx = it.getColumnIndex("type")
                    if (addrIdx < 0 || typeIdx < 0) return@use
                    while (it.moveToNext()) {
                        val type = it.getInt(typeIdx)
                        if (type == 137 || type == 151) { // From or To
                            val addr = it.getString(addrIdx)
                            if (!addr.isNullOrBlank()) {
                                return app.messages.utility.AddressUtils.normalizeAddress(addr)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = ArrayList<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val sort = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        
        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val number = it.getString(numberIdx) ?: ""
                    val photo = it.getString(photoIdx)
                    
                    contacts.add(Contact(id, name, number, photo))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        contacts.distinctBy { it.phoneNumber }
    }

    private suspend fun getSystemContactsMap(): Map<String, app.messages.data.model.CachedContactInfo> {
        // First check if we have cached contacts map
        val cachedContactsMap = cacheManager.getCachedContactsMap()
        if (cachedContactsMap.isNotEmpty()) {
            return cachedContactsMap
        }
        
        val startMap = HashMap<String, app.messages.data.model.CachedContactInfo>()
        val contacts = getContacts()
        for (contact in contacts) {
            val normalizedNumber = normalizeNumber(contact.phoneNumber)
            if (normalizedNumber.isNotEmpty()) {
                val info = app.messages.data.model.CachedContactInfo(contact.name, contact.photoUri)
                // Store by normalized number
                startMap[normalizedNumber] = info
                // Store by last 10 digits for better matching (handles +91 etc)
                if (normalizedNumber.length >= 10) {
                    val last10 = normalizedNumber.takeLast(10)
                    startMap[last10] = info
                }
            }
        }
        cacheManager.cacheContactsMap(startMap)
        return startMap
    }

    private suspend fun syncConversations(contactsMap: Map<String, app.messages.data.model.CachedContactInfo>) {
        val conversations = ArrayList<ConversationEntity>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        
        // Fetch canonical addresses and pre-query DB states ONCE to eliminate N+1 loop queries
        val canonicalMap = getCanonicalAddressesMap()
        val archivedIds = db.archivedThreadDao().getAllArchivedThreadIds().toSet()
        val blockedAddresses = db.blockedContactDao().getAllBlockedAddresses().toSet()
        val existingConversationsMap = db.conversationDao().getAllSync().associateBy { it.threadId }
        val threadsWithMessages = db.messageDao().getThreadIdsWithMessagesSync().toSet()

        try {
            val cursor = context.contentResolver.query(uri, null, null, null, "date DESC")
            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val recipientIdsIdx = it.getColumnIndex("recipient_ids")
                val dateIdx = it.getColumnIndex("date")
                val snippetIdx = it.getColumnIndex("snippet")
                val readIdx = it.getColumnIndex("read")
                val typeIdx = it.getColumnIndex("type")

                while (it.moveToNext()) {
                    try {
                        val threadId = if (idIdx >= 0) it.getLong(idIdx) else -1L
                    if (threadId == -1L) continue

                    // Check if archived
                    if (archivedIds.contains(threadId)) continue

                    val recipientIds = if (recipientIdsIdx >= 0) it.getString(recipientIdsIdx) ?: "" else ""
                    val address = getAddressFromRecipientIds(recipientIds, canonicalMap)
                    
                    // Check if blocked
                    if (blockedAddresses.contains(address)) continue

                    val snippet = if (snippetIdx >= 0) it.getString(snippetIdx) ?: "" else ""
                    var date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    
                    var finalSnippet: String
                    if (snippet.isBlank() || date <= 0L) {
                        val fallbackResult = getThreadSnippetAndDateFallback(threadId, address)
                        finalSnippet = if (snippet.isNotBlank()) snippet else {
                            if (fallbackResult.first.isNotBlank()) fallbackResult.first else "📷 Media message"
                        }
                        if (fallbackResult.second > 0L) { // Always trust the actual message's date if fetched
                            date = fallbackResult.second
                        }
                    } else {
                        finalSnippet = snippet
                    }
                    
                    var read = if (readIdx >= 0) it.getInt(readIdx) == 1 else true

                    // Override with local DB if it has a newer or equal message (e.g. just restored) 
                    // that the system provider's conversation view hasn't indexed yet.
                    val existing = existingConversationsMap[threadId]
                    if (existing != null && existing.date >= date) {
                        date = existing.date
                        finalSnippet = existing.body.ifBlank { finalSnippet }
                        if (!existing.read) {
                            read = false
                        }
                    }

                    if (!app.messages.utility.AddressUtils.isValidAddress(address)) continue // Filter unknown/blank/placeholder senders
                    
                    // Default to INBOX type to avoid making 10,000 ContentResolver queries inside this loop.
                    val lastMsgType = if (typeIdx >= 0) it.getInt(typeIdx) else 1

                    val addresses = address.split(",")
                    val resolvedNames = mutableListOf<String>()
                    val resolvedPhotos = mutableListOf<String>()
                    var allSaved = true

                    for (addr in addresses) {
                        if (addr.isEmpty()) continue
                        val normalized = normalizeNumber(addr)
                        val last10 = if (normalized.length >= 10) normalized.takeLast(10) else normalized
                        
                        val info = contactsMap[normalized] ?: contactsMap[last10] ?: contactsMap[addr]
                        
                        if (info != null) {
                            resolvedNames.add(info.name)
                            info.photoUri?.let { resolvedPhotos.add(it) }
                        } else {
                            resolvedNames.add(addr)
                            allSaved = false
                        }
                    }

                    val finalName = if (resolvedNames.size > 1) {
                        resolvedNames.joinToString(", ")
                    } else {
                        resolvedNames.firstOrNull()
                    }

                    val resolvedGroupName = if (address.contains(",")) {
                        existing?.recipientName ?: finalName
                    } else {
                        finalName
                    }

                    val finalPhoto = resolvedPhotos.firstOrNull() ?: existing?.photoUri

                    conversations.add(
                        ConversationEntity(
                            threadId = threadId,
                            address = address,
                            body = app.messages.utility.AddressUtils.cleanMessageBody(finalSnippet),
                            date = date,
                            read = read,
                            messageCount = 0,
                            recipientName = resolvedGroupName,
                            photoUri = finalPhoto,
                            lastMsgType = lastMsgType,
                            isSaved = allSaved,
                            normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Continue to next thread if one fails
                }
            }
        }
        val deduped = conversations
            .groupBy { getAddrKey(it.address) }
            .map { (_, group) -> 
                group.maxByOrNull { conv ->
                    val hasMsg = if (threadsWithMessages.contains(conv.threadId) || existingConversationsMap.containsKey(conv.threadId)) 1L else 0L
                    hasMsg * 1000000000000000L + conv.date
                }!!
            }
            .sortedByDescending { it.date }

        // Full purge: identify every DB row whose threadId is NOT in the list currently
        // returned by Android. This removes ALL stale/duplicate rows.
        val currentThreadIds = conversations.map { it.threadId }.toSet()
        val dbThreadIds = db.conversationDao().getAllThreadIds()
        
        // CRITICAL FIX: Don't delete threads that are archived or blocked locally,
        // as the system provider might be filtering them out of the main query.
        val orphansToDelete = dbThreadIds.filter { id -> 
            if (id in currentThreadIds || id in archivedIds) return@filter false
            val conv = existingConversationsMap[id]
            if (conv != null && (conv.address.contains(",") || conv.recipientName != null)) {
                return@filter false
            }
            if (id in threadsWithMessages) {
                return@filter false
            }
            true
        }.toMutableList()

        // Also delete threads present in Android but dropped by dedup (address duplicates)
        val keptThreadIds = deduped.map { it.threadId }.toSet()
        for (conv in conversations) {
            if (conv.threadId !in keptThreadIds && conv.threadId !in archivedIds) {
                orphansToDelete.add(conv.threadId)
            }
        }

        // Perform all deletions and insertions in a single atomic transaction 
        // to prevent UI flicker from multiple Room emissions.
        db.conversationDao().syncAllWithDeletions(deduped, orphansToDelete)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncRecentConversations(
        contactsMap: Map<String, app.messages.data.model.CachedContactInfo>, 
        limit: Int, 
        systemThreadIds: Set<Long>?
    ) = withContext(Dispatchers.IO) {
        val conversations = ArrayList<ConversationEntity>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val canonicalMap = getCanonicalAddressesMap()
        val archivedIds = db.archivedThreadDao().getAllArchivedThreadIds().toSet()
        val blockedAddresses = db.blockedContactDao().getAllBlockedAddresses().toSet()
        val existingConversationsMap = db.conversationDao().getAllSync().associateBy { it.threadId }

        try {
            val cursor = context.contentResolver.query(uri, null, null, null, "date DESC")
            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val recipientIdsIdx = it.getColumnIndex("recipient_ids")
                val dateIdx = it.getColumnIndex("date")
                val snippetIdx = it.getColumnIndex("snippet")
                val readIdx = it.getColumnIndex("read")
                val typeIdx = it.getColumnIndex("type")

                var count = 0
                while (it.moveToNext() && count < limit) {
                    try {
                        val threadId = if (idIdx >= 0) it.getLong(idIdx) else -1L
                        if (threadId == -1L || archivedIds.contains(threadId)) continue

                        val recipientIds = if (recipientIdsIdx >= 0) it.getString(recipientIdsIdx) ?: "" else ""
                        val address = getAddressFromRecipientIds(recipientIds, canonicalMap)
                        if (blockedAddresses.contains(address) || !app.messages.utility.AddressUtils.isValidAddress(address)) continue

                        val snippet = if (snippetIdx >= 0) it.getString(snippetIdx) ?: "" else ""
                        var date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                        
                        var finalSnippet: String = snippet.ifBlank { "📷 Media message" }
                        val read = if (readIdx >= 0) it.getInt(readIdx) == 1 else true

                        val existing = existingConversationsMap[threadId]
                        if (existing != null && existing.date >= date) {
                            date = existing.date
                            finalSnippet = existing.body.ifBlank { finalSnippet }
                        }

                        val lastMsgType = if (typeIdx >= 0) it.getInt(typeIdx) else 1

                        val addresses = address.split(",")
                        val resolvedNames = mutableListOf<String>()
                        var allSaved = true

                        for (addr in addresses) {
                            if (addr.isEmpty()) continue
                            val normalized = normalizeNumber(addr)
                            val last10 = if (normalized.length >= 10) normalized.takeLast(10) else normalized
                            val info = contactsMap[normalized] ?: contactsMap[last10] ?: contactsMap[addr]
                            if (info != null) {
                                resolvedNames.add(info.name)
                            } else {
                                resolvedNames.add(addr)
                                allSaved = false
                            }
                        }

                        val finalName = if (resolvedNames.size > 1) resolvedNames.joinToString(", ") else resolvedNames.firstOrNull()
                        val resolvedGroupName = if (address.contains(",")) existing?.recipientName ?: finalName else finalName

                        conversations.add(
                            ConversationEntity(
                                threadId = threadId,
                                address = address,
                                body = app.messages.utility.AddressUtils.cleanMessageBody(finalSnippet),
                                date = date,
                                read = read,
                                messageCount = 0,
                                recipientName = resolvedGroupName,
                                photoUri = existing?.photoUri,
                                lastMsgType = lastMsgType,
                                isSaved = allSaved,
                                normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)
                            )
                        )
                        count++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if (conversations.isNotEmpty()) {
                db.conversationDao().upsertAll(conversations)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCanonicalAddressesMap(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val uri = Uri.parse("content://mms-sms/canonical-addresses")
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val addressIdx = cursor.getColumnIndex("address")
                while (cursor.moveToNext()) {
                    if (idIdx >= 0 && addressIdx >= 0) {
                        val id = cursor.getString(idIdx)
                        val address = cursor.getString(addressIdx)
                        if (id != null && address != null) {
                            map[id] = address
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun getAddressFromRecipientIds(recipientIds: String, canonicalMap: Map<String, String>? = null): String {
        if (recipientIds.isBlank()) return ""
        val ids = recipientIds.split(Regex("[, ]+")).filter { it.isNotBlank() }
        val addresses = mutableListOf<String>()
        for (id in ids) {
            var resolvedAddr: String? = null
            if (canonicalMap != null) {
                resolvedAddr = canonicalMap[id]
            } else {
                try {
                    val uri = Uri.parse("content://mms-sms/canonical-address/$id")
                    context.contentResolver.query(uri, null, null, null, null)?.use {
                        if (it.moveToFirst()) {
                            resolvedAddr = it.getString(0)
                        }
                    }
                } catch (e2: Exception) {}
            }
            resolvedAddr?.let { addresses.add(it) }
        }
        return addresses.joinToString(",")
    }

    private fun getLastMessageType(threadId: Long): Int {
        try {
            var lastSmsDate = -1L
            var lastSmsType = 1
            
            val smsUri = Telephony.Sms.CONTENT_URI
            val smsSelection = "${Telephony.Sms.THREAD_ID} = ?"
            context.contentResolver.query(smsUri, arrayOf(Telephony.Sms.DATE, Telephony.Sms.TYPE), smsSelection, arrayOf(threadId.toString()), "date DESC")?.use {
                if (it.moveToFirst()) {
                    lastSmsDate = it.getLong(0)
                    lastSmsType = it.getInt(1)
                }
            }
            
            return lastSmsType
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 1
    }

    private fun normalizeNumber(phone: String): String {
        return phone.filter { it.isDigit() }
    }

    private fun getSimSlotFromSubscriptionId(subscriptionId: Long): Int? {
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId.toInt())
            return subscriptionInfo?.simSlotIndex
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun getOrCreateThreadId(address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            -1L
        }
    }

    suspend fun markThreadAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            
            val smsUri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0"
            context.contentResolver.update(smsUri, values, selection, arrayOf(threadId.toString()))
            
            val mmsValues = android.content.ContentValues().apply {
                put(Telephony.Mms.READ, 1)
            }
            val mmsUri = Telephony.Mms.CONTENT_URI
            val mmsSelection = "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0"
            context.contentResolver.update(mmsUri, mmsValues, mmsSelection, arrayOf(threadId.toString()))
            
            db.conversationDao().markRead(threadId)
            db.messageDao().markRead(threadId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markThreadAsUnread(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 0)
            }

            val smsUri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            context.contentResolver.update(smsUri, values, selection, arrayOf(threadId.toString()))

            val mmsValues = android.content.ContentValues().apply {
                put(Telephony.Mms.READ, 0)
            }
            val mmsUri = Telephony.Mms.CONTENT_URI
            val mmsSelection = "${Telephony.Mms.THREAD_ID} = ?"
            context.contentResolver.update(mmsUri, mmsValues, mmsSelection, arrayOf(threadId.toString()))

            db.conversationDao().markUnread(threadId)
            db.messageDao().markUnread(threadId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun getThreadSnippetAndDateFallback(threadId: Long, address: String): Pair<String, Long> {
        var latestDate = 0L
        var latestSnippet = ""
        
        try {
            // 1. Check SMS by thread_id with date DESC LIMIT 1 for instant single-row lookup
            if (threadId > 0) {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                    "date DESC LIMIT 1"
                )?.use {
                    if (it.moveToFirst()) {
                        latestDate = it.getLong(1)
                        latestSnippet = it.getString(0) ?: ""
                    }
                }
            }

            // 2. If no SMS by thread_id, try by fuzzy address as a fallback
            if (latestDate == 0L && address.isNotBlank()) {
                val addresses = address.split(",") 
                if (addresses.size == 1) {
                    val searchAddr = addresses[0].trim()
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                        "${Telephony.Sms.ADDRESS} LIKE ?",
                        arrayOf("%$searchAddr"),
                        "date DESC LIMIT 1"
                    )?.use {
                        if (it.moveToFirst()) {
                            latestDate = it.getLong(1)
                            latestSnippet = it.getString(0) ?: ""
                        }
                    }
                }
            }

            // 3. Check MMS by thread_id with date DESC LIMIT 1
            if (threadId > 0) {
                context.contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
                    "${Telephony.Mms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                    "date DESC LIMIT 1"
                )?.use {
                    if (it.moveToFirst()) {
                        val rawMmsDate = it.getLong(1)
                        val mmsDate = if (rawMmsDate < 10_000_000_000L) rawMmsDate * 1000L else rawMmsDate
                        if (mmsDate > latestDate) {
                            latestDate = mmsDate
                            val mmsId = it.getLong(0)
                            val mmsText = getMmsText(mmsId)
                            latestSnippet = if (mmsText.isNotBlank()) mmsText else "📷 Media message"
                        }
                    }
                }
            }
            
            // 4. Local DB check as absolute last resort
            if (latestDate == 0L || latestSnippet.isBlank()) {
                val localLatestMsg = db.messageDao().getLastMessageForThreadSync(threadId)
                if (localLatestMsg != null) {
                    if (latestDate == 0L) latestDate = localLatestMsg.date
                    if (localLatestMsg.body.isNotBlank()) latestSnippet = localLatestMsg.body
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return latestSnippet to latestDate
    }
}



