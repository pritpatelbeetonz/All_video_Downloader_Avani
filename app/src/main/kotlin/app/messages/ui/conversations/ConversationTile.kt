package app.messages.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.messages.core.time.formatRelativeTimestamp
import app.messages.data.telephony.model.Conversation
import app.messages.ui.theme.Spacing

/**
 * Conversation tile, restyled to Figma 17:226: avatar, name + snippet, a
 * trailing date + chevron, and a blue numbered unread badge. A hairline divider
 * (indented past the avatar) separates rows.
 */
@Composable
fun ConversationTile(
    conversation: Conversation,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val isUnread = conversation.isUnread
    val mutedAlpha = 0.6f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Spacing.tileHeight)
                .padding(horizontal = 20.dp, vertical = Spacing.md),
        ) {
            ContactAvatar(
                address = conversation.primaryAddress,
                name = conversation.contactName,
                photoUri = conversation.contactPhotoUri,
                isGroup = conversation.isGroup,
            )
            Spacer(Modifier.width(Spacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isUnread) FontWeight.W700 else FontWeight.W600,
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conversation.isMuted) {
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = scheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                SnippetRow(
                    conversation = conversation,
                    fullColor = scheme.onSurface,
                    mutedColor = scheme.onSurface.copy(alpha = mutedAlpha),
                    draftColor = scheme.primary,
                    isUnread = isUnread,
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = scheme.onSurface.copy(alpha = mutedAlpha),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(
                        text = formatRelativeTimestamp(conversation.lastActivity),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isUnread) FontWeight.W600 else FontWeight.W500,
                        ),
                        color = scheme.onSurface.copy(alpha = mutedAlpha),
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurface.copy(alpha = 0.4f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (isUnread) {
                    UnreadBadge(count = conversation.unreadCount, color = scheme.primary)
                } else {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 84.dp)
                .height(1.dp)
                .background(scheme.onSurface.copy(alpha = 0.07f)),
        )
    }
}

@Composable
private fun SnippetRow(
    conversation: Conversation,
    fullColor: Color,
    mutedColor: Color,
    draftColor: Color,
    isUnread: Boolean,
) {
    val textColor = if (isUnread) fullColor else mutedColor
    val text = buildAnnotatedString {
        if (conversation.hasDraft) {
            withStyle(
                SpanStyle(
                    color = draftColor,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.W500,
                ),
            ) { append("Draft: ") }
        }
        append(conversation.snippet)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (conversation.hasAttachment) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = mutedColor,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isUnread) FontWeight.W500 else FontWeight.W400,
            ),
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnreadBadge(count: Int, color: Color) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else "${count.coerceAtLeast(1)}",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.W700,
        )
    }
}
