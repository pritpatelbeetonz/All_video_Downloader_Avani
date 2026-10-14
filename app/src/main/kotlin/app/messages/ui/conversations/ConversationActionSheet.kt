package app.messages.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.messages.data.telephony.model.Conversation

/**
 * Per-conversation long-press actions (Figma 76:686 / 76:926). Pin/Mute toggle
 * to Unpin/Unmute and Star fills in when the thread is already pinned/muted/
 * starred. Delete is the only destructive (red) row.
 */
@Composable
fun ConversationActionSheet(
    conversation: Conversation,
    onPinToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onArchive: () -> Unit,
    onStarToggle: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)) {
        ActionRow(Icons.Outlined.PushPin, if (conversation.isPinned) "Unpin" else "Pin", onPinToggle)
        ActionRow(Icons.Outlined.NotificationsOff, if (conversation.isMuted) "Unmute" else "Mute", onMuteToggle)
        ActionRow(Icons.Outlined.Archive, "Archive", onArchive)
        ActionRow(
            if (conversation.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            "Star", onStarToggle,
        )
        ActionRow(Icons.Outlined.Block, "Block & report spam", onBlock)
        ActionRow(Icons.Outlined.Delete, "Delete", onDelete, destructive = true)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (destructive) scheme.error else scheme.onSurface
    val font = MaterialTheme.typography.titleMedium.fontFamily
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, fontFamily = font, fontSize = 17.sp, fontWeight = FontWeight.W500, color = color)
    }
}
