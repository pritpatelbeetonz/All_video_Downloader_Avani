package app.messages.search

import android.content.Context
import android.content.SharedPreferences

class SearchHistoryPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)

    fun getRecent(): List<String> {
        val raw = prefs.getString("recent_searches", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||")
    }

    fun addRecent(query: String) {
        if (query.isBlank()) return
        val current = getRecent().toMutableList()
        current.remove(query)
        current.add(0, query)
        val limited = current.take(10)
        prefs.edit().putString("recent_searches", limited.joinToString("|||")).apply()
    }

    fun clearRecent() {
        prefs.edit().remove("recent_searches").apply()
    }
}
