package app.messages.data.model

enum class PermissionType {
    RUNTIME,
    OVERLAY,
    NOTIFICATION
}

data class PermissionItem(
    val iconRes: Int,
    val title: String,
    val description: String,
    val permission: String,
    val type: PermissionType
)
