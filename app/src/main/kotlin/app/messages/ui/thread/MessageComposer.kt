package app.messages.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.messages.data.telephony.SimInfo
import app.messages.ui.theme.Spacing
import app.messages.ui.util.rememberHaptics

/**
 * Thread composer per build doc §5.3.
 *
 * - 1–5 line auto-grow field in a pill container
 * - send button stays plain/disabled until text is present, then morphs to a
 *   filled primary FAB-style button
 * - attachment "+" opens the attachment sheet (targets land in M3)
 * - SIM chip appears only on dual-SIM devices
 */
@Composable
fun MessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    sims: List<SimInfo>,
    selectedSim: SimInfo?,
    onCycleSim: () -> Unit,
    modifier: Modifier = Modifier,
    attachment: PendingAttachment? = null,
    onClearAttachment: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val canSend = text.isNotBlank() || attachment != null
    val haptics = rememberHaptics()

    Surface(
        color = scheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
      androidx.compose.foundation.layout.Column {
        if (attachment != null) {
            AttachmentPreview(attachment = attachment, onClear = onClearAttachment)
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(
                horizontal = Spacing.sm,
                vertical = Spacing.sm,
            ),
        ) {
            IconButtonBox(onClick = onAttach) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Attach",
                    tint = scheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Spacing.pillRadius))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = Spacing.md, vertical = 10.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Type Message",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = scheme.onSurface,
                        ),
                        cursorBrush = SolidColor(scheme.primary),
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 24.dp),
                    )
                }
                if (sims.size > 1 && selectedSim != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    SimChip(label = selectedSim.label, onClick = onCycleSim)
                }
            }

            if (canSend) {
                Spacer(Modifier.width(Spacing.sm))
                SendButton(enabled = true, onClick = { haptics.tick(); onSend() })
            }
        }
      }
    }
}

@Composable
private fun AttachmentPreview(attachment: PendingAttachment, onClear: () -> Unit) {
    when (attachment.kind) {
        AttachmentKind.Audio -> ChipPreview(Icons.Outlined.Mic, attachment.label ?: "Voice clip", onClear)
        AttachmentKind.Contact -> ChipPreview(Icons.Outlined.Person, attachment.label ?: "Contact card", onClear)
        else -> ImagePreview(attachment.uri, onClear) // image + gif (Coil renders gifs)
    }
}

@Composable
private fun ImagePreview(uri: android.net.Uri, onClear: () -> Unit) {
    Box(modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm)) {
        coil.compose.AsyncImage(
            model = uri,
            contentDescription = "Attachment",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(Spacing.md)),
        )
        ClearBadge(onClear, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun ChipPreview(icon: ImageVector, label: String, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(Spacing.md))
                .background(scheme.secondaryContainer)
                .padding(start = Spacing.md, end = Spacing.lg, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSecondaryContainer,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        ClearBadge(onClear, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun ClearBadge(onClear: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .padding(2.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(scheme.scrim.copy(alpha = 0.6f))
            .retryClickable(onClear),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Remove attachment",
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (enabled) scheme.primary else scheme.surfaceContainerHigh
    val fg = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (enabled) Modifier.retryClickable(onClick) else Modifier),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Send",
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SimChip(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(Spacing.pillRadius))
            .background(scheme.secondaryContainer)
            .retryClickable(onClick)
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
    ) {
        Icon(
            Icons.Outlined.SimCard,
            contentDescription = "SIM",
            tint = scheme.onSecondaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun IconButtonBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.5.dp, scheme.outline.copy(alpha = 0.35f), CircleShape)
            .retryClickable(onClick),
    ) {
        content()
    }
}
