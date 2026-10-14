package app.messages.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/** Long-press handler for a bubble (no ripple, no click action). */
fun Modifier.longPressable(onLongPress: () -> Unit): Modifier = composed {
    combinedClickable(onClick = {}, onLongClick = onLongPress)
}

/**
 * Simple tap handler used by the composer buttons, action rows, and the
 * "Tap to retry" affordance. Non-`@Composable` so it can be used conditionally
 * inside `Modifier.then(...)`.
 */
fun Modifier.retryClickable(onClick: () -> Unit): Modifier = composed {
    clickable(onClick = onClick)
}
