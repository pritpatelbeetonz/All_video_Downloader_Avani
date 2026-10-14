package app.messages.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.messages.core.time.formatClockTime
import app.messages.data.telephony.model.MessageStatus
import app.messages.data.telephony.model.SmsMessage
import app.messages.ui.conversations.ContactAvatar
import app.messages.ui.theme.Spacing

/**
 * A single message bubble (Figma thread frame).
 *
 * - max width 78% of screen
 * - sent: solid brand blue + white text, end-aligned
 * - received: light gray bubble, start-aligned, with the sender's avatar at the
 *   bubble's bottom-left (shown once per group, on the last bubble)
 * - grouped bubbles connect via tail corners; timestamp/status on the last bubble
 */
@Composable
fun MessageBubble(
    message: SmsMessage,
    connectedAbove: Boolean,
    connectedBelow: Boolean,
    showMeta: Boolean,
    onRetry: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    senderName: String? = null,
    senderPhotoUri: String? = null,
) {
    val isOutgoing = message.isOutgoing
    val maxWidth = (LocalConfiguration.current.screenWidthDp *
        Spacing.BUBBLE_MAX_WIDTH_FRACTION).dp

    val round = Spacing.bubbleRadius
    val tail = Spacing.bubbleTailRadius
    val shape = if (isOutgoing) {
        RoundedCornerShape(
            topStart = round,
            bottomStart = round,
            topEnd = if (connectedAbove) tail else round,
            bottomEnd = if (connectedBelow) tail else round,
        )
    } else {
        RoundedCornerShape(
            topEnd = round,
            bottomEnd = round,
            topStart = if (connectedAbove) tail else round,
            bottomStart = if (connectedBelow) tail else round,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (connectedAbove) 2.dp else Spacing.md,
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
            ),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
    ) {
        if (isOutgoing) {
            BubbleBody(message, shape, maxWidth, isOutgoing = true, onLongPress = onLongPress)
            if (showMeta) MetaRow(message, isOutgoing = true, onRetry = onRetry)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                if (!connectedBelow) {
                    ContactAvatar(
                        address = message.address,
                        name = senderName,
                        photoUri = senderPhotoUri,
                        isGroup = false,
                        size = 28.dp,
                    )
                } else {
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.width(8.dp))
                BubbleBody(message, shape, maxWidth, isOutgoing = false, onLongPress = onLongPress)
            }
            if (showMeta) {
                Row(modifier = Modifier.padding(start = 36.dp)) {
                    MetaRow(message, isOutgoing = false, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun BubbleBody(
    message: SmsMessage,
    shape: RoundedCornerShape,
    maxWidth: androidx.compose.ui.unit.Dp,
    isOutgoing: Boolean,
    onLongPress: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bubbleColor = if (isOutgoing) scheme.primary else scheme.surfaceContainerHigh
    val textColor = if (isOutgoing) scheme.onPrimary else scheme.onSurface
    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(bubbleColor)
            .longPressable(onLongPress)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        if (message.attachmentUri != null) {
            MessageAttachment(message = message, tint = textColor)
            if (message.body.isNotBlank()) Spacer(Modifier.height(Spacing.sm))
        }
        if (message.body.isNotBlank()) {
            LinkifiedText(
                text = message.body,
                linkColor = if (isOutgoing) scheme.onPrimary else scheme.primary,
                baseStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            )
        }
    }
}

@Composable
private fun MetaRow(
    message: SmsMessage,
    isOutgoing: Boolean,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val metaColor = scheme.onSurface.copy(alpha = 0.6f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(
            top = 2.dp,
            start = Spacing.xs,
            end = Spacing.xs,
        ),
    ) {
        if (message.isStarred) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = "Starred",
                modifier = Modifier.size(12.dp),
                tint = scheme.primary,
            )
        }
        Text(
            text = formatClockTime(message.date),
            style = MaterialTheme.typography.labelSmall,
            color = metaColor,
        )
        if (isOutgoing) {
            when (message.status) {
                MessageStatus.Sending, MessageStatus.Queued, MessageStatus.Pending ->
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = "Sending",
                        modifier = Modifier.size(14.dp),
                        tint = metaColor,
                    )
                MessageStatus.Sent ->
                    Icon(
                        Icons.Outlined.Done,
                        contentDescription = "Sent",
                        modifier = Modifier.size(14.dp),
                        tint = metaColor,
                    )
                MessageStatus.Delivered ->
                    Icon(
                        Icons.Outlined.DoneAll,
                        contentDescription = "Delivered",
                        modifier = Modifier.size(14.dp),
                        tint = scheme.primary,
                    )
                MessageStatus.Failed -> {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = "Failed",
                        modifier = Modifier.size(14.dp),
                        tint = scheme.error,
                    )
                    Text(
                        text = "Tap to retry",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W600,
                        ),
                        color = scheme.error,
                        modifier = Modifier.retryClickable(onRetry),
                    )
                }
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}
