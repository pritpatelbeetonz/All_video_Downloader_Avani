package app.messages.ui.scheduling

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.core.scheduling.ScheduleRules
import app.messages.data.scheduling.ScheduledMessage
import app.messages.ui.components.EmptyState
import app.messages.ui.components.ScreenScaffold
import app.messages.ui.conversations.ContactAvatar
import app.messages.ui.theme.Spacing

/**
 * The scheduled-send queue (§F13). Lists pending messages with per-row Send now
 * / Reschedule / Cancel, and nudges free users toward Premium once they near the
 * 3-message cap.
 */
@Composable
fun ScheduledScreen(
    onBack: () -> Unit,
    onPremium: () -> Unit,
    vm: ScheduledViewModel = hiltViewModel(),
) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val isPremium by vm.isPremium.collectAsStateWithLifecycle()
    var rescheduleTarget by remember { mutableStateOf<ScheduledMessage?>(null) }

    ScreenScaffold(title = "Scheduled", onBack = onBack) {
        if (pending.isEmpty()) {
            EmptyScheduled(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(pending, key = { it.id }) { msg ->
                    ScheduledRow(
                        name = vm.displayName(msg.address),
                        msg = msg,
                        onSendNow = { vm.sendNow(msg.id) },
                        onReschedule = { rescheduleTarget = msg },
                        onCancel = { vm.cancel(msg.id) },
                    )
                }
            }
        }
    }

    rescheduleTarget?.let { msg ->
        SchedulePickerDialog(
            initialMs = msg.scheduledAtMs,
            onDismiss = { rescheduleTarget = null },
            onConfirm = { atMs ->
                vm.reschedule(msg.id, atMs)
                rescheduleTarget = null
            },
        )
    }
}

@Composable
private fun ScheduledRow(
    name: String,
    msg: ScheduledMessage,
    onSendNow: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.md),
    ) {
        ContactAvatar(
            address = msg.address,
            name = name.takeIf { it != msg.address },
            photoUri = null,
            isGroup = false,
            size = 44.dp,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = formatScheduledAt(msg.scheduledAtMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600),
                    color = scheme.primary,
                )
            }
            Spacer(Modifier.size(2.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = msg.body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Send now") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null) },
                    onClick = { menuOpen = false; onSendNow() },
                )
                DropdownMenuItem(
                    text = { Text("Reschedule") },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    onClick = { menuOpen = false; onReschedule() },
                )
                DropdownMenuItem(
                    text = { Text("Cancel send") },
                    leadingIcon = { Icon(Icons.Outlined.Cancel, contentDescription = null) },
                    onClick = { menuOpen = false; onCancel() },
                )
            }
        }
    }
}

@Composable
private fun UpsellFooter(used: Int, onPremium: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.screenEdge)
            .clip(RoundedCornerShape(Spacing.sheetRadius))
            .background(scheme.primaryContainer)
            .clickable(onClick = onPremium)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Using $used of ${ScheduleRules.FREE_MAX_ACTIVE} free slots",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    "Go Premium for unlimited scheduled messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun EmptyScheduled(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Schedule,
        text = "Nothing scheduled",
        hint = "Tap + in a chat, then Schedule, to send later",
        modifier = modifier,
    )
}
