package app.messages.utility

import android.Manifest
import android.content.Context
import android.provider.Settings
import app.messages.R
import app.messages.data.model.PermissionItem
import app.messages.data.model.PermissionType


object PermissionProvider {

    fun getPermissions(context: Context): List<PermissionItem> {
        return listOf(
            PermissionItem(
                R.drawable.ic_notification,
                context.getString(R.string.notification),
                context.getString(R.string.allow_notifications_for_important_alerts_updates),
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                PermissionType.NOTIFICATION
            )
        )
    }
}
