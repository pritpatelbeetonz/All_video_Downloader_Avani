package app.messages.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import app.messages.data.db.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class CacheManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("message_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val memoryCache = ConcurrentHashMap<String, Any>()
    
    companion object {
        @Volatile
        private var INSTANCE: CacheManager? = null
        
        fun getInstance(context: Context): CacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Cache keys
    private object CacheKeys {
        const val CONVERSATIONS = "conversations_cache"
        const val CONTACTS_MAP = "contacts_map_cache"
        const val LAST_SYNC_TIME = "last_sync_time"
        const val QUICK_CONVERSATIONS = "quick_conversations_cache"
    }
    
    // Conversation caching
    suspend fun cacheConversations(conversations: List<ConversationEntity>) = withContext(Dispatchers.IO) {
        memoryCache[CacheKeys.CONVERSATIONS] = conversations
    }
    
    @Suppress("UNCHECKED_CAST")
    suspend fun getCachedConversations(): List<ConversationEntity> = withContext(Dispatchers.IO) {
        return@withContext (memoryCache[CacheKeys.CONVERSATIONS] as? List<ConversationEntity>) ?: emptyList()
    }
    
    // Quick conversations cache (for instant loading)
    suspend fun cacheQuickConversations(conversations: List<ConversationEntity>) = withContext(Dispatchers.IO) {
        memoryCache[CacheKeys.QUICK_CONVERSATIONS] = conversations
    }
    
    @Suppress("UNCHECKED_CAST")
    suspend fun getCachedQuickConversations(): List<ConversationEntity> = withContext(Dispatchers.IO) {
        return@withContext (memoryCache[CacheKeys.QUICK_CONVERSATIONS] as? List<ConversationEntity>) ?: emptyList()
    }
    
    // Contact mapping cache
    suspend fun cacheContactsMap(contactsMap: Map<String, app.messages.data.model.CachedContactInfo>) = withContext(Dispatchers.IO) {
        memoryCache[CacheKeys.CONTACTS_MAP] = contactsMap
    }
    
    @Suppress("UNCHECKED_CAST")
    suspend fun getCachedContactsMap(): Map<String, app.messages.data.model.CachedContactInfo> = withContext(Dispatchers.IO) {
        return@withContext (memoryCache[CacheKeys.CONTACTS_MAP] as? Map<String, app.messages.data.model.CachedContactInfo>) ?: emptyMap()
    }
    
    // Sync timestamp management
    fun setLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(CacheKeys.LAST_SYNC_TIME, timestamp).apply()
    }
    
    fun getLastSyncTime(): Long {
        return prefs.getLong(CacheKeys.LAST_SYNC_TIME, 0)
    }
    
    fun shouldPerformFullSync(): Boolean {
        val lastSync = getLastSyncTime()
        val currentTime = System.currentTimeMillis()
        // Perform full sync if more than 5 minutes have passed
        return (currentTime - lastSync) > (5 * 60 * 1000)
    }
    
    // Cache clearing
    fun clearCache() {
        prefs.edit().clear().apply()
        memoryCache.clear()
    }
    
    fun clearConversationsCache() {
        memoryCache.remove(CacheKeys.CONVERSATIONS)
        memoryCache.remove(CacheKeys.QUICK_CONVERSATIONS)
    }

    suspend fun removeFromCache(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            // Update full cache
            val conversations = getCachedConversations().toMutableList()
            if (conversations.removeAll { it.threadId == threadId }) {
                cacheConversations(conversations)
            }
            
            // Update quick cache
            val quickConversations = getCachedQuickConversations().toMutableList()
            if (quickConversations.removeAll { it.threadId == threadId }) {
                cacheQuickConversations(quickConversations)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getMemoryCachedContactsMap(): Map<String, app.messages.data.model.CachedContactInfo> {
        return memoryCache[CacheKeys.CONTACTS_MAP] as? Map<String, app.messages.data.model.CachedContactInfo> ?: emptyMap()
    }
}