package app.messages.enumeration

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import app.messages.R

enum class SwipeActions(val value: Int) {
    NONE(0),
    ARCHIVE(1),
    DELETE(2),
    BLOCK(3),
    CALL(4),
    MARK_READ(5),
    MARK_UNREAD(6),
    REPLY(7);

    companion object {
        fun fromValue(value: Int): SwipeActions {
            return entries.find { it.value == value } ?: NONE
        }
    }
}

fun getSwipeActionIcon(value: Int): Int {
    return when (value) {
        SwipeActions.NONE.value -> 0
        SwipeActions.ARCHIVE.value -> R.drawable.ic_archive
        SwipeActions.DELETE.value -> R.drawable.ic_delete
        SwipeActions.BLOCK.value -> R.drawable.ic_block
        SwipeActions.CALL.value -> R.drawable.ic_call
        SwipeActions.MARK_READ.value -> R.drawable.ic_mark_read
        SwipeActions.MARK_UNREAD.value -> R.drawable.ic_mark_unread
        else -> {
            0
        }
    }
}

fun getSwipeActionColor(value: Int, context: Context): Int {
    return when (value) {
        SwipeActions.NONE.value -> Color.TRANSPARENT
        SwipeActions.ARCHIVE.value -> ContextCompat.getColor(context, R.color.colorPrimary)
        SwipeActions.DELETE.value -> ContextCompat.getColor(context, R.color.red)
        SwipeActions.BLOCK.value -> ContextCompat.getColor(context, R.color.red)
        SwipeActions.CALL.value -> ContextCompat.getColor(context, R.color.green)
        SwipeActions.MARK_READ.value -> ContextCompat.getColor(context, R.color.colorPrimary)
        SwipeActions.MARK_UNREAD.value -> ContextCompat.getColor(context, R.color.colorPrimary)
        else -> {
            Color.TRANSPARENT
        }
    }
}

fun getSwipeActionText(value: Int, context: Context): String {
    return when (value) {
        SwipeActions.NONE.value -> context.getString(R.string.none)
        SwipeActions.ARCHIVE.value -> context.getString(R.string.archive)
        SwipeActions.DELETE.value -> context.getString(R.string.delete)
        SwipeActions.BLOCK.value -> context.getString(R.string.block)
        SwipeActions.CALL.value -> context.getString(R.string.call)
        SwipeActions.MARK_READ.value -> context.getString(R.string.mark_read)
        SwipeActions.MARK_UNREAD.value -> context.getString(R.string.mark_unread)
        SwipeActions.REPLY.value -> context.getString(R.string.reply)
        else -> {
            context.getString(R.string.none)
        }
    }
}