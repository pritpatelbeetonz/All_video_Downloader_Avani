package app.messages.utility

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object DraftManager {
    private const val PREFS_NAME = "drafts_prefs"
    private val memoryCache = ConcurrentHashMap<String, String>()
    @Volatile private var isCacheLoaded = false

    private fun ensureCacheLoaded(context: Context) {
        if (!isCacheLoaded) {
            synchronized(this) {
                if (!isCacheLoaded) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val all = prefs.all
                    memoryCache.clear()
                    for ((k, v) in all) {
                        if (v is String && v.isNotBlank()) {
                            memoryCache[k] = v
                        }
                    }
                    isCacheLoaded = true
                }
            }
        }
    }
    
    fun saveDraft(context: Context, key: String, draftText: String) {
        ensureCacheLoaded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trimmed = draftText.trim()
        if (trimmed.isBlank()) {
            memoryCache.remove(key)
            prefs.edit().remove(key).apply()
        } else {
            memoryCache[key] = trimmed
            prefs.edit().putString(key, trimmed).apply()
        }
    }
    
    fun getDraft(context: Context, key: String): String? {
        ensureCacheLoaded(context)
        return memoryCache[key]
    }
    
    fun hasDraft(context: Context, key: String): Boolean {
        ensureCacheLoaded(context)
        return memoryCache.containsKey(key)
    }
    
    fun getAllDrafts(context: Context): Map<String, String> {
        ensureCacheLoaded(context)
        return HashMap(memoryCache)
    }
}

