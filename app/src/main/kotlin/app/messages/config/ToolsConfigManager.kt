package app.messages.config

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import app.messages.data.model.ToolModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

object ToolsConfigManager {
    private const val TAG = "ToolsConfigManager"
    private const val KEY_TOOLS_ENABLED = "tools_enabled"
    private const val KEY_TOOLS = "tools"

    private val _isToolsEnabledFlow = MutableStateFlow(FirebaseRemoteConfig.getInstance().getBoolean(KEY_TOOLS_ENABLED))
    val isToolsEnabledFlow: StateFlow<Boolean> = _isToolsEnabledFlow

    private val _toolsFlow = MutableStateFlow<List<ToolModel>>(emptyList())
    val toolsFlow: StateFlow<List<ToolModel>> = _toolsFlow

    init {
        parseToolsJson(FirebaseRemoteConfig.getInstance().getString(KEY_TOOLS))
    }

    fun fetchTools() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            // Always update the standalone boolean flag from Remote Config
            _isToolsEnabledFlow.value = remoteConfig.getBoolean(KEY_TOOLS_ENABLED)
            // Parse the JSON array
            parseToolsJson(remoteConfig.getString(KEY_TOOLS))
        }
    }

    private fun parseToolsJson(toolsJsonStr: String) {
        if (toolsJsonStr.isNotEmpty()) {
            try {
                val jsonStr = toolsJsonStr.trim()
                if (jsonStr.startsWith("{")) {
                    val jsonObject = JSONObject(jsonStr)
                    
                    // Extract tools_enabled from inside the JSON payload if it exists
                    if (jsonObject.has("tools_enabled")) {
                        _isToolsEnabledFlow.value = jsonObject.getBoolean("tools_enabled")
                    }
                    
                    val toolsArrayStr = if (jsonObject.has("tools")) {
                        jsonObject.getJSONArray("tools").toString()
                    } else {
                        "[]"
                    }
                    
                    val type = object : TypeToken<List<ToolModel>>() {}.type
                    val tools = Gson().fromJson<List<ToolModel>>(toolsArrayStr, type)
                    _toolsFlow.value = tools ?: emptyList()
                    
                } else if (jsonStr.startsWith("[")) {
                    val type = object : TypeToken<List<ToolModel>>() {}.type
                    val tools = Gson().fromJson<List<ToolModel>>(jsonStr, type)
                    _toolsFlow.value = tools ?: emptyList()
                } else {
                    _toolsFlow.value = emptyList()
                }
            } catch (e: Exception) {
                _toolsFlow.value = emptyList()
            }
        } else {
            _toolsFlow.value = emptyList()
        }
    }
}
