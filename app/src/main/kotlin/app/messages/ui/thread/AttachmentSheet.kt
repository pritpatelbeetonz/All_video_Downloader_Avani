package app.messages.ui.thread

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.messages.ui.theme.Spacing

/**
 * Attachment targets per build doc §5.3 — Gallery, Camera, GIF, Contact,
 * Schedule, Audio. Gallery/Camera/GIF drive the MMS image path; Contact attaches
 * a vCard; Schedule opens the scheduled-send picker; Audio records a voice clip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onGif: () -> Unit,
    onContact: () -> Unit,
    onSchedule: () -> Unit,
    onAudio: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AttachTarget(Icons.Outlined.Image, "Gallery") { onDismiss(); onGallery() }
                AttachTarget(Icons.Outlined.PhotoCamera, "Camera") { onDismiss(); onCamera() }
                AttachTarget(Icons.Outlined.Gif, "GIF") { onDismiss(); onGif() }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg,
                ),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AttachTarget(Icons.Outlined.Person, "Contact") { onDismiss(); onContact() }
                AttachTarget(Icons.Outlined.CalendarMonth, "Schedule") { onDismiss(); onSchedule() }
                AttachTarget(Icons.Outlined.Mic, "Audio") { onDismiss(); onAudio() }
            }
        }
    }
}

@Composable
private fun AttachTarget(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(scheme.secondaryContainer),
        ) {
            Icon(icon, contentDescription = label, tint = scheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
