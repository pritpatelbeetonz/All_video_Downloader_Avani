package app.messages.ui.launcher.drawer

import android.graphics.drawable.Drawable

object AppActionCache {
    var lastUninstalledPackage: String? = null
    var lastUninstalledLabel: String? = null
    var lastUninstalledIcon: Drawable? = null

    fun setPendingUninstall(packageName: String, label: String, icon: Drawable?) {
        lastUninstalledPackage = packageName
        lastUninstalledLabel = label
        if (icon != null || lastUninstalledIcon == null || lastUninstalledPackage != packageName) {
            lastUninstalledIcon = icon ?: lastUninstalledIcon
        }
    }
    
    fun clear() {
        lastUninstalledPackage = null
        lastUninstalledLabel = null
        lastUninstalledIcon = null
    }
}
