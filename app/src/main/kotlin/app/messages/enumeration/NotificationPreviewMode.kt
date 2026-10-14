package app.messages.enumeration

import android.content.Context
import app.messages.R

enum class NotificationPreviewMode(val value: Int) {
    SHOW_NAME_AND_MESSAGES(0),
    SHOW_NAME(1),
    HIDE_CONTENTS(2);

    companion object {
        fun fromValue(value: Int): NotificationPreviewMode {
            return entries.find { it.value == value } ?: SHOW_NAME_AND_MESSAGES
        }
    }
}


fun getNotificationPreviewModeText(value: Int, context: Context): String {
    return when (value) {
        NotificationPreviewMode.SHOW_NAME_AND_MESSAGES.value -> context.getString(R.string.show_name_and_message)
        NotificationPreviewMode.SHOW_NAME.value -> context.getString(R.string.show_name)
        NotificationPreviewMode.HIDE_CONTENTS.value -> context.getString(R.string.hide_contents)
        else -> {
            context.getString(R.string.show_name_and_message)
        }
    }
}


