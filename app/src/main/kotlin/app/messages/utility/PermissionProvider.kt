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
            PermissionItem(R.drawable.ic_call,
                context.getString(R.string.calls),
                context.getString(R.string.allow_this_app_to_read_write_call_logs), Manifest.permission.CALL_PHONE, PermissionType.RUNTIME),
            PermissionItem(R.drawable.ic_notification,
                context.getString(R.string.notification),
                context.getString(R.string.allow_notifications_for_important_alerts_updates), Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, PermissionType.NOTIFICATION),
//            PermissionItem(R.drawable.ic_permission_overly, context.getString(R.string.overlay), context.getString(R.string.per_overlay_des), Settings.ACTION_MANAGE_OVERLAY_PERMISSION, PermissionType.OVERLAY)
        )
    }
}
