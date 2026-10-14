package app.messages.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.messages.ui.theme.Spacing

/**
 * Overflow menu sheet for the conversation list — per build doc §4.2.
 * Mounted as a modal bottom sheet so destinations are reachable with the
 * thumb regardless of device height.
 */
@Composable
fun ConversationListMenuSheet(
    onArchived: () -> Unit,
    onBlocked: () -> Unit,
    onScheduled: () -> Unit,
    onStarred: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onPremium: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = Spacing.md, bottom = Spacing.md)
                .align(Alignment.CenterHorizontally),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.onSurface.copy(alpha = 0.2f)),
            )
        }
        MenuRow(Icons.Outlined.Archive, "Archived", onArchived)
        MenuRow(Icons.Outlined.Block, "Blocked & spam", onBlocked)
        MenuRow(Icons.Outlined.Schedule, "Scheduled", onScheduled)
        MenuRow(Icons.Outlined.StarBorder, "Starred", onStarred)
        MenuRow(Icons.Outlined.CloudUpload, "Backup", onBackup)
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
        MenuRow(Icons.Outlined.Settings, "Settings", onSettings)
        // MenuRow(
        //     icon = Icons.Outlined.WorkspacePremium,
        //     label = "Go Premium",
        //     onClick = onPremium,
        //     foreground = scheme.primary,
        // )
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = foreground)
        Spacer(Modifier.width(Spacing.lg))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = foreground,
        )
    }
}

