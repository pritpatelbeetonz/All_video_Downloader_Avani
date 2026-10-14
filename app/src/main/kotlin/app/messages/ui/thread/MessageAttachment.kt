package app.messages.ui.thread

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.messages.data.telephony.model.MessageKind
import app.messages.data.telephony.model.SmsMessage
import app.messages.ui.theme.Spacing
import coil.compose.AsyncImage

/**
 * Renders an MMS attachment inside a bubble (build doc §F5). Images load from
 * the `content://mms/part/{id}` URI via Coil; other media show a tappable chip
 * that opens the system viewer.
 */
@Composable
fun MessageAttachment(message: SmsMessage, tint: androidx.compose.ui.graphics.Color) {
    val uri = message.attachmentUri ?: return
    val context = LocalContext.current

    fun open() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(uri), message.attachmentMime())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        } catch (_: ActivityNotFoundException) {
            // No viewer for this type — ignore.
        }
    }

    when (message.kind) {
        MessageKind.Image -> AsyncImage(
            model = uri,
            contentDescription = "Image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(Spacing.md))
                .clickable { open() },
        )
        MessageKind.Video -> Box(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(Spacing.md))
                .background(tint.copy(alpha = 0.18f))
                .clickable { open() }
                .padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = "Video",
                tint = tint,
                modifier = Modifier.size(48.dp),
            )
        }
        MessageKind.Audio -> AttachmentChip(Icons.Rounded.GraphicEq, "Audio", tint, ::open)
        MessageKind.VCard -> AttachmentChip(Icons.Outlined.Person, "Contact card", tint, ::open)
        else -> AttachmentChip(Icons.AutoMirrored.Outlined.Article, "Attachment", tint, ::open)
    }
}

@Composable
private fun AttachmentChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(Spacing.md))
            .background(tint.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

private fun SmsMessage.attachmentMime(): String = when (kind) {
    MessageKind.Image -> "image/*"
    MessageKind.Video -> "video/*"
    MessageKind.Audio -> "audio/*"
    MessageKind.VCard -> "text/x-vcard"
    else -> "*/*"
}
