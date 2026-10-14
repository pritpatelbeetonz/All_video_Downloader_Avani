package app.messages.ui.launcher.drawer

import app.messages.data.model.AppInfo

object GlobalAppCache {
    var lastApps: List<AppInfo> = emptyList()

    fun getAppInfo(packageName: String): AppInfo? {
        return lastApps.find { it.packageName == packageName }
    }
}
