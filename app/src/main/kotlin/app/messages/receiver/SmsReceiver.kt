package app.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

import android.provider.Telephony
import android.content.ContentValues
import android.util.Log
import app.messages.data.db.AppDatabase
import app.messages.data.db.ConversationEntity
import app.messages.data.db.MessageEntity
import kotlinx.coroutines.runBlocking
import app.messages.utility.AddressUtils

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val inIntent = intent ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIncomingSms(ctx, inIntent, expectedDefaultApp = true)
            } finally {
                pendingResult.finish()
            }
        }
        }
}

/**
 * Separate receiver for non-default SMS flows.
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val inIntent = intent ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIncomingSms(ctx, inIntent, expectedDefaultApp = false)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun handleIncomingSms(context: Context, intent: Intent, expectedDefaultApp: Boolean) {
    val action = intent.action ?: return
    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
    if (messages.isNullOrEmpty()) return
    val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
    val isDefaultSmsApp = defaultSmsPackage == context.packageName
    if (expectedDefaultApp != isDefaultSmsApp) return

    val groupedMessages = messages.groupBy { it.displayOriginatingAddress ?: "" }
    for ((address, parts) in groupedMessages) {
        if (!AddressUtils.isValidAddress(address)) continue
        val rawBody = parts.joinToString("") { it.displayMessageBody ?: "" }
        val body = AddressUtils.cleanMessageBody(rawBody)
        val now = parts.firstOrNull()?.timestampMillis?.takeIf { it > 0 } ?: System.currentTimeMillis()

        // ThreadId that the system associates with this address
        val systemThreadId = Telephony.Threads.getOrCreateThreadId(context, address)

        // ThreadId that ChatActivity is currently observing (-1 if no chat is open)
        val activeThreadId = app.messages.Controller.activeThreadId
        val normalizedIncoming = AddressUtils.normalizeAddress(address)

        // If a chat is open, prefer using its exact threadId so the new message appears
        // in the Flow that ChatActivity is already observing (zero-latency display).
        val localThreadId: Long = if (activeThreadId != -1L) {
            val db = AppDatabase.getDatabase(context)
            val activeConv = runBlocking { db.conversationDao().getById(activeThreadId) }
            if (activeConv != null &&
                AddressUtils.normalizeAddress(activeConv.address) == normalizedIncoming) {
                activeThreadId
            } else {
                systemThreadId
            }
        } else {
            systemThreadId
        }

        val isInActiveThread = (localThreadId == activeThreadId && activeThreadId != -1L)

        // ── Blocking Check ──────────────────────────────────────────
        val db = AppDatabase.getDatabase(context)
        val blockedList = runBlocking { db.blockedContactDao().getAllBlockedAddresses() }
        val isBlocked = blockedList.any { AddressUtils.normalizeAddress(it) == normalizedIncoming }
        
        val prefHelper = app.messages.preferences.PreferenceHelper.getInstance(context)
        val isDropEnabled = prefHelper.isDropMessagesEnabled()
        
        if (isBlocked && isDropEnabled) {
            // Drop message completely
            CoroutineScope(Dispatchers.IO).launch {
                db.blockedMessageDao().insert(
                    app.messages.data.db.BlockedMessageEntity(
                        address = address,
                        body = body,
                        date = now
                    )
                )
            }
            continue // Skip to next message in the intent
        }
        // ────────────────────────────────────────────────────────────


        // ── Step 1: Persist to system provider (default SMS app only) ────────────────
        var systemMsgId: Long? = null
        if (isDefaultSmsApp && action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.READ, if (isInActiveThread) 1 else 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            try {
                val insertedUri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                systemMsgId = insertedUri?.lastPathSegment?.toLongOrNull()
            } catch (e: Exception) {
                }
        }

        // ── Step 2: Insert message into LOCAL DB immediately ───────────────────────
        // Always insert - use system ID if available, otherwise a negative temp ID.
        // syncMessagesForThread will later replace temp ID with the real one.
        val localMsgId: Long = systemMsgId ?: -now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)

                // Insert MessageEntity using localThreadId (what ChatActivity is observing)
                val msg = MessageEntity(
                    id = localMsgId,
                    threadId = localThreadId,
                    address = app.messages.utility.AddressUtils.normalizeAddress(address),
                    body = body,
                    date = now,
                    type = 1, // Inbox
                    read = isInActiveThread,
                    status = 0,
                    simSlot = null,
                    subscriptionId = null
                )
                db.messageDao().insert(msg)

                // Update local conversation so the conversation list shows the new message
                val existing = db.conversationDao().getById(localThreadId)
                val updatedConversation = ConversationEntity(
                    threadId = localThreadId,
                    address = address,
                    body = body.trim(),
                    date = now,
                    read = isInActiveThread,
                    messageCount = (existing?.messageCount ?: 0) + 1,
                    recipientName = existing?.recipientName,
                    photoUri = existing?.photoUri,
                    lastMsgType = 1,
                    isSaved = existing?.isSaved ?: false,
                    isPinned = existing?.isPinned ?: false,
                    normalizedAddress = normalizedIncoming
                )
                db.conversationDao().insert(updatedConversation)

                // ── Step 3: Full sync with BOTH threadIds so the system provider is
                // queried correctly even when localThreadId != systemThreadId
                val syncManager = app.messages.data.SyncManager(context)
                syncManager.syncMessagesForThread(localThreadId, systemThreadId)
                syncManager.quickSyncRecentConversations(20)
                } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val shouldSuppressNotification =
            isBlocked || (isInActiveThread && app.messages.Controller.getInstance().isAppInForeground)
        if (!shouldSuppressNotification) {
            CoroutineScope(Dispatchers.IO).launch {
                app.messages.utility.NotificationHelper.showNotification(context, address, body, localThreadId)
            }
        }
        }
}
